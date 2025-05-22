package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import org.chipsalliance.t1.rtl._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl.lsu.DataToZVMA

object ZVMAParameter {
  implicit def rw: upickle.default.ReadWriter[ZVMAParameter] = upickle.default.macroRW
}

case class ZVMAParameter(
  vlen: Int,
  dlen: Int,
  elen: Int,
  TE:   Int)
    extends SerializableModuleParameter {
  val tmWidth: Int = log2Ceil(TE + 1)
  val tnWidth: Int = log2Ceil(vlen + 1)

  // The minimum execution unit is a 2 * 2 square matrix
  // todo: param from config
  val aluRowSize = 2
  val aluColSize = 2

  val dataIndexBit: Int = log2Ceil(vlen * 8 / dlen + 1)

  // source buffer param calculate
  val aluSizeVec: Seq[Int] = Seq(aluRowSize, aluColSize)

  val subArrayBufferDepth = 8
  // Should be a constant
  // If an instruction col index is fixed (such as mv), a higher bank may be required.
  val subArrayRamBank     = 2

  val ramDepth: Int = TE * TE * 32 * 4 / (elen * 4 * aluRowSize * aluColSize * subArrayRamBank)
}

class ZVMCsrInterface(parameter: ZVMAParameter) extends Bundle {
  val sew: UInt = UInt(2.W)
  // TEW = SEW * TWIDEN
  val tew = UInt(3.W)
  // tk can hold values from 0-4, inclusive.
  val tk  = UInt(3.W)
  // tm can hold values from 0-TE, inclusive.
  val tm  = UInt(parameter.tmWidth.W)
  val tn  = UInt(parameter.tnWidth.W)
}

class ZVMAInstRequest(parameter: ZVMAParameter) extends Bundle {
  val instruction: UInt = UInt(32.W)
  // mv use rs1, load/store use rs2
  val scalaSource: UInt = UInt(32.W)
  val csr = new ZVMCsrInterface(parameter)
}

class ZVMADecodeResult extends Bundle {
  // todo: form decode
  val writeTile: Bool = Bool()
  val readTile:  Bool = Bool()
  val aluType:   Bool = Bool()

  // from inst
  val eew:         UInt = UInt(2.W)
  val col:         Bool = Bool()
  val accessTile:  UInt = UInt(4.W)
  val accessIndex: UInt = UInt(24.W)
}

class ZVMAExecute(parameter: ZVMAParameter) extends Bundle {
  val colData     = Vec(2, UInt(parameter.elen.W))
  val rowData     = Vec(2, UInt(parameter.elen.W))
  val execute     = Bool()
  val writeTile   = Bool()
  val readTile    = Bool()
  val accessIndex = Bool()
  val col         = Bool()
  val index:      UInt = UInt(
    (
      log2Ceil(parameter.TE / parameter.aluColSize / 2) +
        log2Ceil(parameter.TE / parameter.aluRowSize / 2)
    ).W
  )
  val accessTile: UInt = UInt(4.W)
  val tk:         UInt = UInt(3.W)
}

class ZVMAInterface(parameter: ZVMAParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())
  val request:     ValidIO[ZVMAInstRequest] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: DecoupledIO[DataToZVMA]  = Flipped(Decoupled(new DataToZVMA(parameter.dlen)))
  val dataToLSU:   DecoupledIO[UInt]        = Decoupled(UInt(parameter.dlen.W))
  val idle:        Bool                     = Output(Bool())
}

class ZVMA(val parameter: ZVMAParameter)
    extends FixedIORawModule(new ZVMAInterface(parameter))
    with SerializableModule[ZVMAParameter]
    with ImplicitClock
    with ImplicitReset {
  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val csrReg: ZVMCsrInterface = RegEnable(io.request.bits.csr, 0.U.asTypeOf(io.request.bits.csr), io.request.fire)

  val decodeResult: ZVMADecodeResult = WireDefault(0.U.asTypeOf(new ZVMADecodeResult))
  val opcode:       UInt             = io.request.bits.instruction(6, 0)
  val fun6:         UInt             = io.request.bits.instruction(31, 26)
  val ls:           Bool             = opcode === BitPat("b0?00111")
  decodeResult.writeTile   := opcode === BitPat("b0000111") || (opcode === BitPat("b1010111") && fun6 === BitPat(
    "b010111"
  ))
  decodeResult.readTile    := opcode === BitPat("b0100111") || (opcode === BitPat("b1010111") && fun6 === BitPat(
    "b010000"
  ))
  decodeResult.aluType     := opcode === BitPat("b1110111")
  decodeResult.eew         := Mux(ls, io.request.bits.instruction(30, 29), io.request.bits.csr.sew)
  decodeResult.col         := io.request.bits.scalaSource(24)
  decodeResult.accessIndex := io.request.bits.scalaSource
  decodeResult.accessTile  := Mux(
    decodeResult.aluType,
    io.request.bits.instruction(11, 10),
    io.request.bits.scalaSource(30, 27)
  )
  val contorlReg: ZVMADecodeResult = RegEnable(decodeResult, 0.U.asTypeOf(decodeResult), io.request.fire)

  // [tk]*[sewB] => [1, 2, 3, 4] * [1, 2, 4] = [1, 2, 3, 4, 6, 8, 12, 16]
  val groupWidth = (csrReg.tk << csrReg.sew)(4, 0)

  val updateSize = parameter.dlen / parameter.elen
  // col buffer 0
  // col always update buffer 0
  // rs1 update buffer 0
  val update1    = !Mux(contorlReg.aluType, io.dataFromLSU.bits.vs1, contorlReg.col)
  io.dataFromLSU.ready := true.B
  val updateFire: Bool = io.dataFromLSU.fire

  // source buffer
  val sourceSew1H: UInt = UIntToOH(csrReg.sew)(2, 0)
  val Seq(colBuffer, rowBuffer) = parameter.aluSizeVec.zipWithIndex.map { case (_, index) =>
    val buffer: Seq[ValidIO[UInt]] = Seq.tabulate(parameter.TE) { _ =>
      RegInit(0.U.asTypeOf(Valid(UInt(parameter.elen.W))))
    }

    val enqFire = updateFire && (update1 === index.U)

    // update entry index
    val updateIndex = RegInit(0.U(log2Ceil(parameter.TE / updateSize).W))
    when(enqFire || io.request.valid) {
      updateIndex := Mux(enqFire, updateIndex + 1.U, 0.U)
    }

    val dataVec = cutUInt(io.dataFromLSU.bits.data, parameter.elen)

    buffer.zipWithIndex.foreach { case (data, i) =>
      val groupIndex = i / updateSize
      val dataIndex  = i % updateSize
      when(enqFire && updateIndex === groupIndex.U) {
        data.valid := true.B
        data.bits  := dataVec(dataIndex)
      }
      when(io.request.valid) { data.valid := false.B }
    }
    buffer
  }

  val colElementSize = parameter.aluColSize * 2
  val rowElementSize = parameter.aluRowSize * 2

  val colExecuteIndex: UInt = RegInit(0.U(log2Ceil(parameter.TE / colElementSize).W))
  val rowExecuteIndex: UInt = RegInit(0.U(log2Ceil(parameter.TE / rowElementSize).W))

  val colBufferValid = Seq
    .tabulate(colElementSize) { ei =>
      val accessIndex: UInt = colExecuteIndex ## ei.U(log2Ceil(colElementSize).W)
      VecInit(colBuffer.map(_.valid)).asUInt(accessIndex)
    }
    .reduce(_ && _)

  val rowBufferValid = Seq
    .tabulate(rowElementSize) { ei =>
      val accessIndex: UInt = rowExecuteIndex ## ei.U(log2Ceil(rowElementSize).W)
      VecInit(rowBuffer.map(_.valid)).asUInt(accessIndex)
    }
    .reduce(_ && _)

  val colDataVec: Seq[UInt] = Seq.tabulate(colElementSize) { ei =>
    val accessIndex: UInt = colExecuteIndex ## ei.U(log2Ceil(colElementSize).W)
    VecInit(colBuffer.map(_.bits))(accessIndex)
  }

  val rowDataVec: Seq[UInt] = Seq.tabulate(rowElementSize) { ei =>
    val accessIndex: UInt = rowExecuteIndex ## ei.U(log2Ceil(rowElementSize).W)
    VecInit(rowBuffer.map(_.bits))(accessIndex)
  }

  val aluReadyVec: Vec[Vec[Bool]] = Wire(Vec(parameter.aluColSize, Vec(parameter.aluRowSize, Bool())))
  val aluReady:    Bool           = aluReadyVec.asUInt.andR

  val nextColIndex: UInt = colExecuteIndex +& 1.U
  val nextRowIndex: UInt = rowExecuteIndex +& 1.U

  // Fixed Row index if always access same col
  val fixedCol = !contorlReg.aluType && !contorlReg.col
  val fixedRow = !contorlReg.aluType && contorlReg.col

  val isLastCol = ((nextColIndex ## 0.U(log2Ceil(colElementSize).W)) >= csrReg.tm) || fixedCol
  val isLastRow = ((nextRowIndex ## 0.U(log2Ceil(rowElementSize).W)) >= csrReg.tn) || fixedRow

  val noSource    = contorlReg.readTile
  val onlyColData = contorlReg.writeTile && contorlReg.col
  val onlyRowData = contorlReg.writeTile && !contorlReg.col
  val dataValid   = ((colBufferValid || onlyRowData) && (rowBufferValid || onlyColData)) || noSource
  val issueIdle   = RegInit(true.B)
  val dataIssue   = aluReady && dataValid && !issueIdle

  val initAccessIndex: UInt = io.request.bits.scalaSource(23, log2Ceil(colElementSize))
  val initCol = Mux(fixedCol, initAccessIndex, 0.U)
  when(dataIssue) {
    colExecuteIndex := Mux(isLastCol, initCol, nextColIndex)
    when(isLastCol) {
      rowExecuteIndex := nextRowIndex
    }
    when(isLastCol && isLastRow) {
      issueIdle := true.B
    }
  }

  when(io.request.valid) {
    issueIdle       := false.B
    colExecuteIndex := initCol
    rowExecuteIndex := Mux(fixedRow, initAccessIndex, 0.U)
  }

  val queueDeq:            Bool                    = Wire(Bool())
  val subArrReadDataQueue: Seq[Seq[QueueIO[UInt]]] = Seq.tabulate(parameter.aluColSize) { colIndex =>
    Seq.tabulate(parameter.aluRowSize) { rowIndex =>
      val queue = Queue.io(UInt((parameter.elen * 2).W), 8)
      queue.deq.ready := queueDeq
      queue
    }
  }

  //  execute Unit & Storage
  val subArrayVec = Seq.tabulate(parameter.aluColSize) { colIndex =>
    Seq.tabulate(parameter.aluRowSize) { rowIndex =>
      val matrixPE: Instance[ZVMAProcessingElement] = Instantiate(new ZVMAProcessingElement(parameter))

      matrixPE.io.clock := implicitClock
      matrixPE.io.reset := implicitReset

      val reqToken = pipeToken(parameter.subArrayBufferDepth)(matrixPE.io.request.fire, matrixPE.io.release)
      aluReadyVec(colIndex)(rowIndex) := reqToken

      // for access single col
      val accessSubIndex = contorlReg.accessIndex(1)
      val indexMath      = Mux(contorlReg.col, rowIndex.asUInt === accessSubIndex, colIndex.asUInt === accessSubIndex)
      // todo: alu boundary correction
      val issueCorrect   = (indexMath || contorlReg.aluType) && dataIssue
      // data enq queue
      matrixPE.io.request.valid            := issueCorrect
      matrixPE.io.request.bits.colData(0)  := colDataVec(2 * colIndex)
      matrixPE.io.request.bits.colData(1)  := colDataVec(2 * colIndex + 1)
      matrixPE.io.request.bits.rowData(0)  := rowDataVec(2 * rowIndex)
      matrixPE.io.request.bits.rowData(1)  := rowDataVec(2 * rowIndex + 1)
      matrixPE.io.request.bits.execute     := contorlReg.aluType
      matrixPE.io.request.bits.writeTile   := contorlReg.writeTile
      matrixPE.io.request.bits.readTile    := contorlReg.readTile
      matrixPE.io.request.bits.accessIndex := contorlReg.accessIndex(0)
      matrixPE.io.request.bits.col         := contorlReg.col
      matrixPE.io.request.bits.index       := rowExecuteIndex ## colExecuteIndex
      matrixPE.io.request.bits.accessTile  := contorlReg.accessTile
      matrixPE.io.request.bits.tk          := csrReg.tk

      subArrReadDataQueue(colIndex)(rowIndex).enq.valid := matrixPE.io.response.valid
      subArrReadDataQueue(colIndex)(rowIndex).enq.bits  := matrixPE.io.response.bits
      matrixPE.io.response.ready                        := queueDeq
    }
  }

  val mvSubIndex = contorlReg.accessIndex(1)
  val mvData     = Mux(
    contorlReg.col,
    Mux(
      mvSubIndex,
      subArrReadDataQueue(1)(1).deq.bits ## subArrReadDataQueue(0)(1).deq.bits,
      subArrReadDataQueue(1)(0).deq.bits ## subArrReadDataQueue(0)(0).deq.bits
    ),
    Mux(
      mvSubIndex,
      subArrReadDataQueue(1)(1).deq.bits ## subArrReadDataQueue(1)(0).deq.bits,
      subArrReadDataQueue(0)(1).deq.bits ## subArrReadDataQueue(0)(0).deq.bits
    )
  )
  val mvValid    = Mux(
    contorlReg.col,
    Mux(
      mvSubIndex,
      subArrReadDataQueue(1)(1).deq.valid ## subArrReadDataQueue(0)(1).deq.valid,
      subArrReadDataQueue(1)(0).deq.valid ## subArrReadDataQueue(0)(0).deq.valid
    ),
    Mux(
      mvSubIndex,
      subArrReadDataQueue(1)(1).deq.valid ## subArrReadDataQueue(1)(0).deq.valid,
      subArrReadDataQueue(0)(1).deq.valid ## subArrReadDataQueue(0)(0).deq.valid
    )
  )

  // data buffer to LSU Pipe
  val dataBufferValid: Bool = RegInit(false.B)
  val dataBuffer:      UInt = RegInit(0.U((parameter.dlen / 2).W))
  val queueDeqReady:   Bool = !dataBufferValid || io.dataToLSU.ready
  val queueDeqFire:    Bool = queueDeqReady && mvValid.andR
  queueDeq := queueDeqFire
  when(queueDeqFire) {
    dataBufferValid := !dataBufferValid
    dataBuffer      := mvData
  }

  io.dataToLSU.valid := dataBufferValid && mvValid.andR
  io.dataToLSU.bits  := mvData ## dataBuffer
  io.idle            := issueIdle
}
