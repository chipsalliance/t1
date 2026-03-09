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

case class ZVMAParameter(
  vlen:             Int,
  dlen:             Int,
  elen:             Int,
  TE:               Int,
  matrixAluRowSize: Int,
  matrixAluColSize: Int)
    extends Parameter:
  val tmWidth: Int = log2Ceil(TE + 1)
  val tnWidth: Int = log2Ceil(vlen + 1)

  // The minimum execution unit is a 2 * 2 square matrix
  val aluRowSize: Int = matrixAluRowSize
  val aluColSize: Int = matrixAluColSize

  val dataIndexBit: Int = log2Ceil(vlen * 8 / dlen + 1)

  // source buffer param calculate
  val aluSizeVec: Seq[Int] = Seq(aluRowSize, aluColSize)

  val subArrayBufferDepth: Int = 8
  // Should be a constant
  // If an instruction col index is fixed (such as mv), a higher bank may be required.
  val subArrayRamBank:     Int = 2

  // The minimum unit is 32 * 2 * 2, so ram width is 32 * 4
  val ramDepth: Int = TE * TE * 32 * 4 / (32 * 4 * aluRowSize * aluColSize * subArrayRamBank)

  val indexWidth:    Int = log2Ceil(TE / aluColSize / 2) + log2Ceil(TE / aluRowSize / 2)
  val executeWidth:  Int = 2 * elen + 2 * elen + 5 + indexWidth + 4 + 3
  val sramAddrWidth: Int = log2Ceil(ramDepth)

given upickle.default.ReadWriter[ZVMAParameter] = upickle.default.macroRW

class ZVMAExecute(parameter: ZVMAParameter) extends HWBundle(parameter):
  val colData:     BundleField[Vec[UInt]] = Aligned(Vec(2, UInt(parameter.elen.W)))
  val rowData:     BundleField[Vec[UInt]] = Aligned(Vec(2, UInt(parameter.elen.W)))
  val execute:     BundleField[Bool]      = Aligned(Bool())
  val writeTile:   BundleField[Bool]      = Aligned(Bool())
  val readTile:    BundleField[Bool]      = Aligned(Bool())
  val accessIndex: BundleField[Bool]      = Aligned(Bool())
  val col:         BundleField[Bool]      = Aligned(Bool())
  val index:       BundleField[UInt]      = Aligned(UInt(parameter.indexWidth.W))
  val accessTile:  BundleField[UInt]      = Aligned(UInt(4.W))
  val tk:          BundleField[UInt]      = Aligned(UInt(3.W))

class ZVMAProcessingElementLayers(parameter: ZVMAParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class ProcessInterface(parameter: ZVMAParameter) extends HWBundle(parameter):
  val clock:    BundleField[Clock]                = Flipped(Clock())
  val reset:    BundleField[Reset]                = Flipped(Reset())
  val request:  BundleField[ValidIO[ZVMAExecute]] = Flipped(Valid(new ZVMAExecute(parameter)))
  val response: BundleField[DecoupledIO[UInt]]    = Aligned(Decoupled(UInt((parameter.elen * 2).W)))
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
    val p  = parameter
    val io = summon[Interface[ProcessInterface]]

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
    val dataPipe0 = RegInit(0.U(p.executeWidth.W).asBits.asType(new ZVMAExecute(p)))
    when(reqQueue.deq.fire):
      dataPipe0 := reqQueue.deq.bits

    val pipeValid0 = RegInit(false.B)
    pipeValid0 := reqQueue.deq.fire

    // execute stage 1
    val dataPipe1 = RegInit(0.U(p.executeWidth.W).asBits.asType(new ZVMAExecute(p)))

    val pipeValid1 = RegInit(false.B)
    pipeValid1 := pipeValid0

    val readData = RegInit(0.U((4 * p.elen).W))

    // execute stage 2
    val pipeValid2 = RegInit(false.B)
    pipeValid2 := pipeValid1

    val index2      = RegInit(0.U(p.indexWidth.W))
    val result      = RegInit(0.U((4 * p.elen).W))
    val resultR2    = RegInit(0.U((2 * p.elen).W))
    val accessTile  = RegInit(0.U(4.W))
    val writeState2 = RegInit(false.B)

    // alu
    val readDataVec = cutUInt(readData, p.elen)
    val aluResult   = Wire(Vec(4, UInt(p.elen.W)))
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
        .bits(p.sramAddrWidth - 1, 0)
        .asUInt
      ram.io.isWrite   := write
      ram.io.writeData := result.asBits
    }

    val deqQueue = Queue(QueueParameter(UInt((p.elen * 2).W), 8))
    deqQueue.clock := io.clock
    deqQueue.reset := io.reset

    deqQueue.enq.valid := pipeValid2 & !writeState2
    deqQueue.enq.bits  := resultR2

    io.response.valid  := deqQueue.deq.valid
    io.response.bits   := deqQueue.deq.bits
    deqQueue.deq.ready := io.response.ready
