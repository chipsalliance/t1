// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl.zvma

import me.jiuyang.decoder.BitSet
import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import me.jiuyang.stdlib.dwbb.{*, given}
import org.chipsalliance.t1.rtl.*
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

given upickle.default.ReadWriter[ZVMAParameter] = upickle.default.macroRW

class ZVMAExecute(parameter: ZVMAParameter) extends HWBundle(parameter):
  val colData:     BundleField[Vec[UInt]] = Aligned(Vec(2, UInt(parameter.elen)))
  val rowData:     BundleField[Vec[UInt]] = Aligned(Vec(2, UInt(parameter.elen)))
  val execute:     BundleField[Bool]      = Aligned(Bool())
  val writeTile:   BundleField[Bool]      = Aligned(Bool())
  val readTile:    BundleField[Bool]      = Aligned(Bool())
  val accessIndex: BundleField[Bool]      = Aligned(Bool())
  val col:         BundleField[Bool]      = Aligned(Bool())
  val index:       BundleField[UInt]      =
    Aligned(UInt(log2Ceil(parameter.TE / parameter.aluColSize / 2) + log2Ceil(parameter.TE / parameter.aluRowSize / 2)))
  val accessTile:  BundleField[UInt]      = Aligned(UInt(4))
  val tk:          BundleField[UInt]      = Aligned(UInt(3))

class ZVMCsrInterface(parameter: ZVMAParameter) extends HWBundle(parameter):
  val sew: BundleField[UInt] = Aligned(UInt(2))
  val tew: BundleField[UInt] = Aligned(UInt(3))
  val tk:  BundleField[UInt] = Aligned(UInt(3))
  val tm:  BundleField[UInt] = Aligned(UInt(parameter.tmWidth))
  val tn:  BundleField[UInt] = Aligned(UInt(parameter.tnWidth))

class ZVMAInstRequest(parameter: ZVMAParameter) extends HWBundle(parameter):
  val instruction: BundleField[UInt]            = Aligned(UInt(32))
  val scalaSource: BundleField[UInt]            = Aligned(UInt(32))
  val csr:         BundleField[ZVMCsrInterface] = Aligned(new ZVMCsrInterface(parameter))

class ZVMADecodeResult(parameter: ZVMAParameter) extends HWBundle(parameter):
  val writeTile:   BundleField[Bool] = Aligned(Bool())
  val readTile:    BundleField[Bool] = Aligned(Bool())
  val aluType:     BundleField[Bool] = Aligned(Bool())
  val eew:         BundleField[UInt] = Aligned(UInt(2))
  val col:         BundleField[Bool] = Aligned(Bool())
  val accessTile:  BundleField[UInt] = Aligned(UInt(4))
  val accessIndex: BundleField[UInt] = Aligned(UInt(24))

class DataToZVMA(parameter: ZVMAParameter) extends HWBundle(parameter):
  val data: BundleField[UInt] = Aligned(UInt(parameter.dlen))
  val vs1:  BundleField[Bool] = Aligned(Bool())

class ZVMAInterface(parameter: ZVMAParameter) extends HWBundle(parameter):
  val clock:       BundleField[Clock]                    = Flipped(Clock())
  val reset:       BundleField[Reset]                    = Flipped(Reset())
  val request:     BundleField[ValidIO[ZVMAInstRequest]] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: BundleField[DecoupledIO[DataToZVMA]]  = Flipped(Decoupled(new DataToZVMA(parameter)))
  val dataToLSU:   BundleField[DecoupledIO[UInt]]        = Aligned(Decoupled(UInt(parameter.dlen)))
  val idle:        BundleField[Bool]                     = Aligned(Bool())

class ZVMALayers(parameter: ZVMAParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class ZVMAProbe(parameter: ZVMAParameter) extends DVBundle[ZVMAParameter, ZVMALayers](parameter)

@generator
object ZVMA extends Generator[ZVMAParameter, ZVMALayers, ZVMAInterface, ZVMAProbe]:
  override def moduleName(parameter: ZVMAParameter): String = "ZVMA"

  def architecture(parameter: ZVMAParameter) =
    val p  = parameter
    val io = summon[Interface[ZVMAInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    val csrType = new ZVMCsrInterface(p)
    val csrReg  = RegInit(0.B(csrType.width).asType(csrType))
    when(io.request.valid):
      csrReg := io.request.bits.csr

    val decodeResult = Wire(new ZVMADecodeResult(p))
    decodeResult := 0.B(decodeResult.getType.width).asType(decodeResult.getType)
    val opcode = io.request.bits.instruction.asBits.bits(6, 0).asUInt
    val fun6   = io.request.bits.instruction.asBits.bits(31, 26).asUInt

    val ls = BitSet.bitpat("0?00111").cover(opcode.asBits)
    decodeResult.writeTile   := BitSet.bitpat("0000111").cover(opcode.asBits) | (
      BitSet.bitpat("1010111").cover(opcode.asBits) & BitSet.bitpat("010111").cover(fun6.asBits)
    )
    decodeResult.readTile    := BitSet.bitpat("0100111").cover(opcode.asBits) | (
      BitSet.bitpat("1010111").cover(opcode.asBits) & BitSet.bitpat("010000").cover(fun6.asBits)
    )
    decodeResult.aluType     := BitSet.bitpat("1110111").cover(opcode.asBits)
    decodeResult.eew         := ls ? (
      io.request.bits.instruction.asBits.bits(30, 29).asUInt,
      io.request.bits.csr.sew
    )
    decodeResult.col         := io.request.bits.scalaSource.asBits.bit(24)
    decodeResult.accessIndex := io.request.bits.scalaSource.asBits.bits(23, 0).asUInt
    decodeResult.accessTile  := decodeResult.aluType ? (
      io.request.bits.instruction.asBits.bits(11, 10).asUInt,
      io.request.bits.scalaSource.asBits.bits(30, 27).asUInt
    )

    val contorlReg = RegInit(0.B(decodeResult.getType.width).asType(decodeResult.getType))
    when(io.request.valid):
      contorlReg := decodeResult

    val groupWidth = (csrReg.tk << csrReg.sew).asBits.bits(4, 0).asUInt
    val updateSize = p.dlen / p.elen
    // col buffer 0, col always update buffer 0, rs1 update buffer 0
    // update1 = !Mux(contorlReg.aluType, io.dataFromLSU.bits.vs1, contorlReg.col)
    val update1    = !(contorlReg.aluType ? (io.dataFromLSU.bits.vs1, contorlReg.col))
    io.dataFromLSU.ready := true.B
    val updateFire = io.dataFromLSU.fire

    val sourceSew1H    = UIntToOH(csrReg.sew).asBits.bits(2, 0).asUInt
    val colElementSize = p.aluColSize * 2
    val rowElementSize = p.aluRowSize * 2

    val dataVec                   = cutUInt(io.dataFromLSU.bits.data, p.elen)
    val Seq(colBuffer, rowBuffer) = p.aluSizeVec.zipWithIndex.map { case (_, index) =>
      val bufferType = Valid(UInt(p.elen))
      val buffer     = Seq.tabulate(p.TE) { _ =>
        RegInit(0.B(bufferType.width).asType(bufferType))
      }

      val updateIndex = RegInit(0.U(log2Ceil(p.TE / updateSize)))
      val enqFire     = updateFire & (update1 === index.U(1).asBits.asBool)

      when(enqFire | io.request.valid):
        updateIndex := enqFire ? (
          (updateIndex + 1.U).asBits.bits(updateIndex.width - 1, 0).asUInt,
          0.U(updateIndex.width)
        )

      buffer.zipWithIndex.foreach { case (data, i) =>
        val groupIndex = i / updateSize
        val dataIndex  = i % updateSize
        when(enqFire & (updateIndex === groupIndex.U)):
          data.valid := true.B
          data.bits  := dataVec(dataIndex)
        when(io.request.valid):
          data.valid := false.B
      }
      buffer
    }

    val colExecuteIndex = RegInit(0.U(log2Ceil(p.TE / colElementSize)))
    val rowExecuteIndex = RegInit(0.U(log2Ceil(p.TE / rowElementSize)))

    val colBufferValid = Seq
      .tabulate(colElementSize) { ei =>
        val accessIndex = (colExecuteIndex.asBits ## ei.U(log2Ceil(colElementSize)).asBits).asUInt
        Node((colBuffer.map(_.valid).toVec)(accessIndex))
      }
      .reduce(_ & _)

    val rowBufferValid = Seq
      .tabulate(rowElementSize) { ei =>
        val accessIndex = (rowExecuteIndex.asBits ## ei.U(log2Ceil(rowElementSize)).asBits).asUInt
        Node((rowBuffer.map(_.valid).toVec)(accessIndex))
      }
      .reduce(_ & _)

    val colDataVec = Seq.tabulate(colElementSize) { ei =>
      val accessIndex = (colExecuteIndex.asBits ## ei.U(log2Ceil(colElementSize)).asBits).asUInt
      (colBuffer.map(_.bits).toVec)(accessIndex)
    }

    val rowDataVec = Seq.tabulate(rowElementSize) { ei =>
      val accessIndex = (rowExecuteIndex.asBits ## ei.U(log2Ceil(rowElementSize)).asBits).asUInt
      (rowBuffer.map(_.bits).toVec)(accessIndex)
    }

    val aluReadyVec = Wire(Vec(p.aluColSize, Vec(p.aluRowSize, Bool())))
    val aluReady    = aluReadyVec.asBits.andR

    val nextColIndex = ((0.B(1) ## colExecuteIndex.asBits).asUInt + 1.U)
    val nextRowIndex = ((0.B(1) ## rowExecuteIndex.asBits).asUInt + 1.U)

    // Fixed Row index if always access same col
    val fixedCol = !contorlReg.aluType & !contorlReg.col
    val fixedRow = !contorlReg.aluType & contorlReg.col

    val isLastCol = ((nextColIndex.asBits ## 0.U(log2Ceil(colElementSize)).asBits).asUInt >= csrReg.tm) | fixedCol
    val isLastRow = ((nextRowIndex.asBits ## 0.U(log2Ceil(rowElementSize)).asBits).asUInt >= csrReg.tn) | fixedRow

    val noSource    = contorlReg.readTile
    val onlyColData = contorlReg.writeTile & contorlReg.col
    val onlyRowData = contorlReg.writeTile & !contorlReg.col
    val dataValid   = ((colBufferValid | onlyRowData) & (rowBufferValid | onlyColData)) | noSource
    val issueIdle   = RegInit(true.B)
    val dataIssue   = aluReady & dataValid & !issueIdle

    val colIdxWidth     = log2Ceil(p.TE / colElementSize)
    val rowIdxWidth     = log2Ceil(p.TE / rowElementSize)
    val initAccessIndex = io.request.bits.scalaSource.asBits.bits(23, log2Ceil(colElementSize)).asUInt
    val initColTrunc    = (fixedCol ? (initAccessIndex, 0.U(colIdxWidth))).asBits.bits(colIdxWidth - 1, 0).asUInt
    val initRowTrunc    = (fixedRow ? (initAccessIndex, 0.U(rowIdxWidth))).asBits.bits(rowIdxWidth - 1, 0).asUInt
    when(dataIssue):
      colExecuteIndex := isLastCol ? (initColTrunc, nextColIndex.asBits.bits(colIdxWidth - 1, 0).asUInt)
      when(isLastCol):
        rowExecuteIndex := nextRowIndex.asBits.bits(rowIdxWidth - 1, 0).asUInt
      when(isLastCol & isLastRow):
        issueIdle := true.B

    when(io.request.valid):
      issueIdle       := false.B
      colExecuteIndex := initColTrunc
      rowExecuteIndex := initRowTrunc

    val queueDeq = Wire(Bool())

    val subArrReadDataQueue = Seq.tabulate(p.aluColSize) { colIndex =>
      Seq.tabulate(p.aluRowSize) { rowIndex =>
        val queue = Queue(QueueParameter(UInt(2 * p.elen), 8))
        queue.clock     := io.clock
        queue.reset     := io.reset
        queue.deq.ready := queueDeq
        queue
      }
    }

    Seq.tabulate(p.aluColSize) { colIndex =>
      Seq.tabulate(p.aluRowSize) { rowIndex =>
        val matrixPE = ZVMAProcessingElement.instantiate(p)
        matrixPE.io.clock := io.clock
        matrixPE.io.reset := io.reset

        val accessSubIndex = contorlReg.accessIndex.asBits.bit(1)
        val indexMath      = contorlReg.col ? (
          rowIndex.U === accessSubIndex.asBits.asUInt,
          colIndex.U === accessSubIndex.asBits.asUInt
        )
        val issueCorrect   = (indexMath | contorlReg.aluType) & dataIssue

        matrixPE.io.request.valid := issueCorrect
        val reqToken = pipeToken(p.subArrayBufferDepth)(
          matrixPE.io.request.valid,
          matrixPE.io.release
        )
        aluReadyVec(colIndex)(rowIndex)      := reqToken
        matrixPE.io.request.bits.colData(0)  := colDataVec(2 * colIndex)
        matrixPE.io.request.bits.colData(1)  := colDataVec(2 * colIndex + 1)
        matrixPE.io.request.bits.rowData(0)  := rowDataVec(2 * rowIndex)
        matrixPE.io.request.bits.rowData(1)  := rowDataVec(2 * rowIndex + 1)
        matrixPE.io.request.bits.execute     := contorlReg.aluType
        matrixPE.io.request.bits.writeTile   := contorlReg.writeTile
        matrixPE.io.request.bits.readTile    := contorlReg.readTile
        matrixPE.io.request.bits.accessIndex := contorlReg.accessIndex.asBits.bit(0)
        matrixPE.io.request.bits.col         := contorlReg.col
        matrixPE.io.request.bits.index       := (rowExecuteIndex.asBits ## colExecuteIndex.asBits).asUInt
        matrixPE.io.request.bits.accessTile  := contorlReg.accessTile
        matrixPE.io.request.bits.tk          := csrReg.tk

        subArrReadDataQueue(colIndex)(rowIndex).enq.valid := matrixPE.io.response.valid
        subArrReadDataQueue(colIndex)(rowIndex).enq.bits  := matrixPE.io.response.bits
        matrixPE.io.response.ready                        := queueDeq
      }
    }

    val mvSubIndex = contorlReg.accessIndex.asBits.bit(1)
    val mvData     = contorlReg.col ? (
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.bits.asBits ## subArrReadDataQueue(0)(1).deq.bits.asBits,
        subArrReadDataQueue(1)(0).deq.bits.asBits ## subArrReadDataQueue(0)(0).deq.bits.asBits
      ),
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.bits.asBits ## subArrReadDataQueue(1)(0).deq.bits.asBits,
        subArrReadDataQueue(0)(1).deq.bits.asBits ## subArrReadDataQueue(0)(0).deq.bits.asBits
      )
    )
    val mvValid    = contorlReg.col ? (
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.valid.asBits ## subArrReadDataQueue(0)(1).deq.valid.asBits,
        subArrReadDataQueue(1)(0).deq.valid.asBits ## subArrReadDataQueue(0)(0).deq.valid.asBits
      ),
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.valid.asBits ## subArrReadDataQueue(1)(0).deq.valid.asBits,
        subArrReadDataQueue(0)(1).deq.valid.asBits ## subArrReadDataQueue(0)(0).deq.valid.asBits
      )
    )

    val dataBufferValid = RegInit(false.B)
    val dataBuffer      = RegInit(0.U(p.dlen / 2))
    val queueDeqReady   = !dataBufferValid | io.dataToLSU.ready
    val queueDeqFire    = queueDeqReady & mvValid.andR
    queueDeq := queueDeqFire
    when(queueDeqFire):
      dataBufferValid := !dataBufferValid
      dataBuffer      := mvData.asUInt

    io.dataToLSU.valid := dataBufferValid & mvValid.andR
    io.dataToLSU.bits  := (mvData ## dataBuffer.asBits).asUInt
    io.idle            := issueIdle
