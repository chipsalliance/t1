// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl.zvma

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import me.jiuyang.stdlib.dwbb.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

class ZVMAProcessingElementLayers(parameter: ZVMAParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class ProcessInterface(parameter: ZVMAParameter) extends HWBundle(parameter):
  val clock:    BundleField[Clock]                = Flipped(Clock())
  val reset:    BundleField[Reset]                = Flipped(Reset())
  val request:  BundleField[ValidIO[ZVMAExecute]] = Flipped(Valid(new ZVMAExecute(parameter)))
  val response: BundleField[DecoupledIO[UInt]]    = Aligned(Decoupled(UInt(2 * parameter.elen)))
  val release:  BundleField[Bool]                 = Aligned(Bool())

class ZVMAProcessingElementProbe(parameter: ZVMAParameter)
    extends DVBundle[ZVMAParameter, ZVMAProcessingElementLayers](parameter)

@generator
object ZVMAProcessingElement
    extends Generator[
      ZVMAParameter,
      ZVMAProcessingElementLayers,
      ProcessInterface,
      ZVMAProcessingElementProbe
    ]:
  override def moduleName(parameter: ZVMAParameter): String = "ZVMAProcessingElement"

  def architecture(parameter: ZVMAParameter) =
    val p           = parameter
    val io          = summon[Interface[ProcessInterface]]
    val executeType = new ZVMAExecute(p)

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    val reqQueue = Queue(
      QueueParameter(
        new ZVMAExecute(p),
        p.subArrayBufferDepth
      )
    )
    reqQueue.clock := io.clock
    reqQueue.reset := io.reset

    // data enq queue
    reqQueue.enq.valid := io.request.valid
    reqQueue.enq.bits  := io.request.bits

    // state ram
    val stateVec = Seq.tabulate(p.subArrayRamBank) { i =>
      SRAM.instantiate(SRAMParameter(depth = p.ramDepth, width = 32 * 4))
    }
    stateVec.foreach { sram =>
      sram.io.clock := io.clock
    }

    // pipe reg
    // execute stage 0
    val dataPipe0 = RegInit(0.B(executeType.width).asType(executeType))
    when(reqQueue.deq.fire):
      dataPipe0 := reqQueue.deq.bits

    val pipeValid0 = RegInit(false.B)
    pipeValid0 := reqQueue.deq.fire

    // execute stage 1
    val dataPipe1 = RegInit(0.B(executeType.width).asType(executeType))

    val pipeValid1 = RegInit(false.B)
    pipeValid1 := pipeValid0

    val readData = RegInit(0.U(4 * p.elen))

    // execute stage 2
    val pipeValid2 = RegInit(false.B)
    pipeValid2 := pipeValid1

    val index2      = RegInit(0.U(dataPipe0.index.width))
    val result      = RegInit(0.U(4 * p.elen))
    val resultR2    = RegInit(0.U(2 * p.elen))
    val accessTile  = RegInit(0.U(4))
    val writeState2 = RegInit(false.B)

    // alu
    val readDataVec = cutUInt(readData, p.elen)
    val aluResult   = Wire(Vec(4, UInt(p.elen)))
    dataPipe1.rowData.toSeq.zipWithIndex.foreach { case (rd, ri) =>
      dataPipe1.colData.toSeq.zipWithIndex.foreach { case (cd, ci) =>
        val di: Int = (ri << 1) + ci
        val base           = readDataVec(di)
        // TODO: Temporarily there is int8
        val rdVec          = cutUInt(rd, 8)
        val cdVec          = cutUInt(cd, 8)
        val adderRes       = (base + rdVec.toSeq.zipWithIndex.map { case (d, i) =>
          (dataPipe1.tk > i.U) ? (d * cdVec(i), 0.U)
        }.reduce(_ + _)).asBits.bits(p.elen - 1, 0).asUInt
        val loadDataSelect = dataPipe1.col ? (cd, rd)
        val useLoadData    = dataPipe1.col ? (
          ri.U === dataPipe1.accessIndex.asBits.asUInt,
          ci.U === dataPipe1.accessIndex.asBits.asUInt
        )
        aluResult(di) := dataPipe1.execute ? (
          adderRes,
          useLoadData ? (loadDataSelect, base)
        )
      }
    }

    val mvData = dataPipe1.col ? (
      dataPipe1.accessIndex ? (
        readDataVec(3).asBits ## readDataVec(2).asBits,
        readDataVec(1).asBits ## readDataVec(0).asBits
      ),
      dataPipe1.accessIndex ? (
        readDataVec(3).asBits ## readDataVec(1).asBits,
        readDataVec(2).asBits ## readDataVec(0).asBits
      )
    )

    // control
    val readReady = !(pipeValid2 & writeState2) | (index2.asBits.bit(0) ^ reqQueue.deq.bits.index.asBits.bit(0))
    reqQueue.deq.ready := readReady

    when(pipeValid0):
      dataPipe1 := dataPipe0
      readData  := dataPipe0.index.asBits.bit(0) ? (
        stateVec.last.io.readData.asUInt,
        stateVec.head.io.readData.asUInt
      )

    when(pipeValid1):
      index2      := dataPipe1.index
      result      := aluResult.asBits.asUInt
      writeState2 := !dataPipe1.readTile
      resultR2    := mvData.asUInt
      accessTile  := dataPipe1.accessTile

    io.release := pipeValid2

    // rf read write
    stateVec.zipWithIndex.foreach { case (ram, idx) =>
      val write     = pipeValid2 & (index2.asBits.bit(0) === (if idx == 0 then false.B else true.B)) & writeState2
      val tryToRead =
        reqQueue.deq.valid & (reqQueue.deq.bits.index.asBits.bit(0) === (if idx == 0 then false.B else true.B))
      ram.io.enable    := write | tryToRead
      ram.io.address   := ((accessTile.asBits ## (write ? (index2, reqQueue.deq.bits.index)).asBits).asUInt >> 1).asBits
        .bits(log2Ceil(p.ramDepth) - 1, 0)
        .asUInt
      ram.io.isWrite   := write
      ram.io.writeData := result.asBits
    }

    val deqQueue = Queue(QueueParameter(UInt(2 * p.elen), 8))
    deqQueue.clock := io.clock
    deqQueue.reset := io.reset

    deqQueue.enq.valid := pipeValid2 & !writeState2
    deqQueue.enq.bits  := resultR2

    io.response.valid  := deqQueue.deq.valid
    io.response.bits   := deqQueue.deq.bits
    deqQueue.deq.ready := io.response.ready
