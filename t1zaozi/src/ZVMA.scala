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

// --- HWBundle types ---

class ZVMCsrInterface(parameter: ZVMAParameter) extends HWBundle(parameter):
  val sew: BundleField[UInt] = Aligned(UInt(2.W))
  val tew: BundleField[UInt] = Aligned(UInt(3.W))
  val tk:  BundleField[UInt] = Aligned(UInt(3.W))
  val tm:  BundleField[UInt] = Aligned(UInt(parameter.tmWidth.W))
  val tn:  BundleField[UInt] = Aligned(UInt(parameter.tnWidth.W))

class ZVMAInstRequest(parameter: ZVMAParameter) extends HWBundle(parameter):
  val instruction: BundleField[UInt]            = Aligned(UInt(32.W))
  val scalaSource: BundleField[UInt]            = Aligned(UInt(32.W))
  val csr:         BundleField[ZVMCsrInterface] = Aligned(new ZVMCsrInterface(parameter))

class ZVMADecodeResult(parameter: ZVMAParameter) extends HWBundle(parameter):
  val writeTile:   BundleField[Bool] = Aligned(Bool())
  val readTile:    BundleField[Bool] = Aligned(Bool())
  val aluType:     BundleField[Bool] = Aligned(Bool())
  val eew:         BundleField[UInt] = Aligned(UInt(2.W))
  val col:         BundleField[Bool] = Aligned(Bool())
  val accessTile:  BundleField[UInt] = Aligned(UInt(4.W))
  val accessIndex: BundleField[UInt] = Aligned(UInt(24.W))

class DataToZVMA(parameter: ZVMAParameter) extends HWBundle(parameter):
  val data: BundleField[UInt] = Aligned(UInt(parameter.dlen.W))
  val vs1:  BundleField[Bool] = Aligned(Bool())

class ZVMAInterface(parameter: ZVMAParameter) extends HWBundle(parameter):
  val clock:       BundleField[Clock]                    = Flipped(Clock())
  val reset:       BundleField[Reset]                    = Flipped(Reset())
  val request:     BundleField[ValidIO[ZVMAInstRequest]] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: BundleField[DecoupledIO[DataToZVMA]]  = Flipped(Decoupled(new DataToZVMA(parameter)))
  val dataToLSU:   BundleField[DecoupledIO[UInt]]        = Aligned(Decoupled(UInt(parameter.dlen.W)))
  val idle:        BundleField[Bool]                     = Aligned(Bool())

class ZVMALayers(parameter: ZVMAParameter) extends LayerInterface(parameter):
  def layers = Seq.empty

class ZVMAProbe(parameter: ZVMAParameter) extends DVBundle[ZVMAParameter, ZVMALayers](parameter)

// --- Generator ---

@generator
object ZVMA extends Generator[ZVMAParameter, ZVMALayers, ZVMAInterface, ZVMAProbe]:
  override def moduleName(parameter: ZVMAParameter): String = "ZVMA"

  def architecture(parameter: ZVMAParameter) =
    val p  = parameter
    val io = summon[Interface[ZVMAInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    // --- CSR register ---
    val csrReg = RegInit(0.U(p.csrWidth.W).asBits.asType(new ZVMCsrInterface(p)))
    when(io.request.valid):
      csrReg := io.request.bits.csr

    // --- Instruction decode ---
    val decodeResult = Wire(new ZVMADecodeResult(p))
    val opcode       = io.request.bits.instruction.asBits.bits(6, 0).asUInt
    val fun6         = io.request.bits.instruction.asBits.bits(31, 26).asUInt

    // ls = opcode === BitPat("b0?00111") => (opcode & 0x5F) === 0x07
    val ls = (opcode.asBits & 0x5f.U(7.W).asBits).asUInt === 0x07.U(7.W)

    // writeTile: opcode === 0x07 || (opcode === 0x57 && fun6 === 0x17)
    decodeResult.writeTile   := (opcode === 0x07.U(7.W)) | ((opcode === 0x57.U(7.W)) & (fun6 === 0x17.U(6.W)))
    // readTile: opcode === 0x27 || (opcode === 0x57 && fun6 === 0x10)
    decodeResult.readTile    := (opcode === 0x27.U(7.W)) | ((opcode === 0x57.U(7.W)) & (fun6 === 0x10.U(6.W)))
    // aluType: opcode === 0x77
    decodeResult.aluType     := opcode === 0x77.U(7.W)
    // eew: ls ? instruction(30,29) : csr.sew
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

    // --- Control register ---
    val contorlReg = RegInit(0.U(p.decodeResultWidth.W).asBits.asType(new ZVMADecodeResult(p)))
    when(io.request.valid):
      contorlReg := decodeResult

    val updateSize = p.dlen / p.elen
    // col buffer 0, col always update buffer 0, rs1 update buffer 0
    // update1 = !Mux(contorlReg.aluType, io.dataFromLSU.bits.vs1, contorlReg.col)
    val update1    = !(contorlReg.aluType ? (io.dataFromLSU.bits.vs1, contorlReg.col))
    io.dataFromLSU.ready := true.B
    val updateFire = io.dataFromLSU.fire

    // --- Source buffers ---
    // Each buffer is TE entries of valid + elen-wide data
    val colElementSize = p.aluColSize * 2
    val rowElementSize = p.aluRowSize * 2

    // Buffer valid registers and data registers
    val colBufferValid = Seq.tabulate(p.TE)(_ => RegInit(false.B))
    val colBufferData  = Seq.tabulate(p.TE)(_ => RegInit(0.U(p.elen.W)))
    val rowBufferValid = Seq.tabulate(p.TE)(_ => RegInit(false.B))
    val rowBufferData  = Seq.tabulate(p.TE)(_ => RegInit(0.U(p.elen.W)))

    val colUpdateIndex = RegInit(0.U(log2Ceil(p.TE / updateSize).W))
    val rowUpdateIndex = RegInit(0.U(log2Ceil(p.TE / updateSize).W))

    // update1 is Bool: false => col (index 0), true => row (index 1)
    val colEnqFire = updateFire & !update1
    val rowEnqFire = updateFire & update1

    val dataVec = cutUInt(io.dataFromLSU.bits.data, p.elen)

    val updateIdxWidth = log2Ceil(p.TE / updateSize)
    when(colEnqFire | io.request.valid):
      colUpdateIndex := colEnqFire ? (
        (colUpdateIndex + 1.U).asBits.bits(updateIdxWidth - 1, 0).asUInt,
        0.U(updateIdxWidth.W)
      )

    when(rowEnqFire | io.request.valid):
      rowUpdateIndex := rowEnqFire ? (
        (rowUpdateIndex + 1.U).asBits.bits(updateIdxWidth - 1, 0).asUInt,
        0.U(updateIdxWidth.W)
      )

    colBufferValid.zipWithIndex.foreach { case (valid, i) =>
      val groupIndex = i / updateSize
      val dataIndex  = i % updateSize
      when(colEnqFire & (colUpdateIndex === groupIndex.U)):
        valid            := true.B
        colBufferData(i) := dataVec(dataIndex)
      when(io.request.valid):
        valid := false.B
    }

    rowBufferValid.zipWithIndex.foreach { case (valid, i) =>
      val groupIndex = i / updateSize
      val dataIndex  = i % updateSize
      when(rowEnqFire & (rowUpdateIndex === groupIndex.U)):
        valid            := true.B
        rowBufferData(i) := dataVec(dataIndex)
      when(io.request.valid):
        valid := false.B
    }

    // --- Buffer validity (dynamic index via Wire Vec) ---
    val colExecuteIndex = RegInit(0.U(log2Ceil(p.TE / colElementSize).W))
    val rowExecuteIndex = RegInit(0.U(log2Ceil(p.TE / rowElementSize).W))

    val colBufferValidVec    = Wire(Vec(colElementSize, Bool()))
    Seq.tabulate(colElementSize) { ei =>
      val accessIdx = colExecuteIndex.asBits ## ei.U(log2Ceil(colElementSize).W).asBits
      val validVec  = Wire(Vec(p.TE, Bool()))
      colBufferValid.zipWithIndex.foreach { case (v, idx) => validVec(idx) := v }
      colBufferValidVec(ei) := validVec(accessIdx.asUInt)
    }
    val colBufferValidResult = colBufferValidVec.asBits.andR

    val rowBufferValidVec    = Wire(Vec(rowElementSize, Bool()))
    Seq.tabulate(rowElementSize) { ei =>
      val accessIdx = rowExecuteIndex.asBits ## ei.U(log2Ceil(rowElementSize).W).asBits
      val validVec  = Wire(Vec(p.TE, Bool()))
      rowBufferValid.zipWithIndex.foreach { case (v, idx) => validVec(idx) := v }
      rowBufferValidVec(ei) := validVec(accessIdx.asUInt)
    }
    val rowBufferValidResult = rowBufferValidVec.asBits.andR

    // --- Data selection (dynamic index via Wire Vec) ---
    val colDataWire = Wire(Vec(colElementSize, UInt(p.elen.W)))
    Seq.tabulate(colElementSize) { ei =>
      val accessIdx = colExecuteIndex.asBits ## ei.U(log2Ceil(colElementSize).W).asBits
      val bitsVec   = Wire(Vec(p.TE, UInt(p.elen.W)))
      colBufferData.zipWithIndex.foreach { case (d, idx) => bitsVec(idx) := d }
      colDataWire(ei) := bitsVec(accessIdx.asUInt)
    }

    val rowDataWire = Wire(Vec(rowElementSize, UInt(p.elen.W)))
    Seq.tabulate(rowElementSize) { ei =>
      val accessIdx = rowExecuteIndex.asBits ## ei.U(log2Ceil(rowElementSize).W).asBits
      val bitsVec   = Wire(Vec(p.TE, UInt(p.elen.W)))
      rowBufferData.zipWithIndex.foreach { case (d, idx) => bitsVec(idx) := d }
      rowDataWire(ei) := bitsVec(accessIdx.asUInt)
    }

    // --- ALU ready vec ---
    val aluReadyVec = Wire(Vec(p.aluColSize, Vec(p.aluRowSize, Bool())))
    val aluReady    = aluReadyVec.asBits.andR

    // Simulate +& (width-expanding add) with explicit zero-extension
    val nextColIndex = ((0.U(1.W).asBits ## colExecuteIndex.asBits).asUInt + 1.U)
    val nextRowIndex = ((0.U(1.W).asBits ## rowExecuteIndex.asBits).asUInt + 1.U)

    // Fixed Row index if always access same col
    val fixedCol = !contorlReg.aluType & !contorlReg.col
    val fixedRow = !contorlReg.aluType & contorlReg.col

    val isLastCol = ((nextColIndex.asBits ## 0.U(log2Ceil(colElementSize).W).asBits).asUInt >= csrReg.tm) | fixedCol
    val isLastRow = ((nextRowIndex.asBits ## 0.U(log2Ceil(rowElementSize).W).asBits).asUInt >= csrReg.tn) | fixedRow

    val noSource    = contorlReg.readTile
    val onlyColData = contorlReg.writeTile & contorlReg.col
    val onlyRowData = contorlReg.writeTile & !contorlReg.col
    val dataValid   = ((colBufferValidResult | onlyRowData) & (rowBufferValidResult | onlyColData)) | noSource
    val issueIdle   = RegInit(true.B)
    val dataIssue   = aluReady & dataValid & !issueIdle

    val colIdxWidth     = log2Ceil(p.TE / colElementSize)
    val rowIdxWidth     = log2Ceil(p.TE / rowElementSize)
    val initAccessIndex = io.request.bits.scalaSource.asBits.bits(23, log2Ceil(colElementSize)).asUInt
    // Truncate initCol to colExecuteIndex width
    val initColTrunc    = (fixedCol ? (initAccessIndex, 0.U(colIdxWidth.W))).asBits.bits(colIdxWidth - 1, 0).asUInt
    val initRowTrunc    = (fixedRow ? (initAccessIndex, 0.U(rowIdxWidth.W))).asBits.bits(rowIdxWidth - 1, 0).asUInt
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

    // --- Queue deq signal ---
    val queueDeq = Wire(Bool())

    // --- Subarray output queues ---
    val subArrReadDataQueue = Seq.tabulate(p.aluColSize) { colIndex =>
      Seq.tabulate(p.aluRowSize) { rowIndex =>
        val queue = Queue(QueueParameter(UInt((p.elen * 2).W), 8))
        queue.clock     := io.clock
        queue.reset     := io.reset
        queue.deq.ready := queueDeq
        queue
      }
    }

    // --- PE array ---
    Seq.tabulate(p.aluColSize) { colIndex =>
      Seq.tabulate(p.aluRowSize) { rowIndex =>
        val matrixPE = ZVMAProcessingElement.instantiate(p)
        matrixPE.io.clock := io.clock
        matrixPE.io.reset := io.reset

        // for access single col
        val accessSubIndex = contorlReg.accessIndex.asBits.bit(1)
        val indexMath      = contorlReg.col ? (
          rowIndex.U === accessSubIndex.asBits.asUInt,
          colIndex.U === accessSubIndex.asBits.asUInt
        )
        val issueCorrect   = (indexMath | contorlReg.aluType) & dataIssue

        // ValidIO.fire in Chisel is just .valid; use issueCorrect directly
        val reqToken = pipeToken(p.subArrayBufferDepth)(
          issueCorrect,
          matrixPE.io.release
        )
        aluReadyVec(colIndex)(rowIndex) := reqToken

        // data enq queue
        matrixPE.io.request.valid            := issueCorrect
        matrixPE.io.request.bits.colData(0)  := colDataWire(2 * colIndex)
        matrixPE.io.request.bits.colData(1)  := colDataWire(2 * colIndex + 1)
        matrixPE.io.request.bits.rowData(0)  := rowDataWire(2 * rowIndex)
        matrixPE.io.request.bits.rowData(1)  := rowDataWire(2 * rowIndex + 1)
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

    // --- Output mux ---
    val mvSubIndex  = contorlReg.accessIndex.asBits.bit(1)
    val mvData      = contorlReg.col ? (
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.bits.asBits ## subArrReadDataQueue(0)(1).deq.bits.asBits,
        subArrReadDataQueue(1)(0).deq.bits.asBits ## subArrReadDataQueue(0)(0).deq.bits.asBits
      ),
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.bits.asBits ## subArrReadDataQueue(1)(0).deq.bits.asBits,
        subArrReadDataQueue(0)(1).deq.bits.asBits ## subArrReadDataQueue(0)(0).deq.bits.asBits
      )
    )
    // Compute mvValid as direct AND of the two relevant valid signals (equivalent to Chisel's mvValid.andR)
    val mvValidAndR = contorlReg.col ? (
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.valid & subArrReadDataQueue(0)(1).deq.valid,
        subArrReadDataQueue(1)(0).deq.valid & subArrReadDataQueue(0)(0).deq.valid
      ),
      mvSubIndex ? (
        subArrReadDataQueue(1)(1).deq.valid & subArrReadDataQueue(1)(0).deq.valid,
        subArrReadDataQueue(0)(1).deq.valid & subArrReadDataQueue(0)(0).deq.valid
      )
    )

    // --- Data buffer to LSU pipe ---
    val dataBufferValid = RegInit(false.B)
    val dataBuffer      = RegInit(0.U((p.dlen / 2).W))
    val queueDeqReady   = !dataBufferValid | io.dataToLSU.ready
    val queueDeqFire    = queueDeqReady & mvValidAndR
    queueDeq := queueDeqFire
    when(queueDeqFire):
      dataBufferValid := !dataBufferValid
      dataBuffer      := mvData.asUInt

    io.dataToLSU.valid := dataBufferValid & mvValidAndR
    io.dataToLSU.bits  := (mvData ## dataBuffer.asBits).asUInt
    io.idle            := issueIdle
