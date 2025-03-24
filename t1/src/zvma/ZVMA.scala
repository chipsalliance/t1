package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3._
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
                          TE: Int
                        ) extends SerializableModuleParameter {
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
  val subArrayRamBank = 2
  // todo: calculate
  val ramDepth = 1024
}

class ZVMCsrInterface(parameter: ZVMAParameter) extends Bundle {
  val sew: UInt = UInt(2.W)
  // TEW = SEW * TWIDEN
  val tew = UInt(3.W)
  // tk can hold values from 0-4, inclusive.
  val tk = UInt(3.W)
  // tm can hold values from 0-TE, inclusive.
  val tm = UInt(parameter.tmWidth.W)
  val tn = UInt(parameter.tnWidth.W)
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
  val readTile: Bool = Bool()
  val aluType: Bool = Bool()

  // from inst
  val eew: UInt = UInt(2.W)
  val col: Bool = Bool()
  val accessTile: UInt = UInt(4.W)
  val accessIndex: UInt = UInt(24.W)
}

class ZVMAExecute(parameter: ZVMAParameter) extends Bundle {
  val colData = Vec(2, UInt(parameter.elen.W))
  val rowData = Vec(2, UInt(parameter.elen.W))
  val execute = Bool()
  val writeTile = Bool()
  val readTile = Bool()
  val accessIndex = Bool()
  val col = Bool()
  val index: UInt = UInt((
    log2Ceil(parameter.TE / parameter.aluColSize / 2) +
      log2Ceil(parameter.TE / parameter.aluRowSize / 2)
    ).W)
}

class ZVMAInterface(parameter: ZVMAParameter) extends Bundle {
  val clock          = Input(Clock())
  val reset          = Input(Reset())
  val request: ValidIO[ZVMAInstRequest] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: DecoupledIO[DataToZVMA] = Flipped(Decoupled(new DataToZVMA(parameter.dlen)))
  val dataToLSU: DecoupledIO[UInt] = Decoupled(UInt(parameter.dlen.W))
  val idle: Bool = Output(Bool())
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
  val opcode: UInt = io.request.bits.instruction(6, 0)
  val fun6: UInt = io.request.bits.instruction(31, 26)
  val ls: Bool = opcode === BitPat("b0?00111")
  decodeResult.writeTile := opcode === BitPat("b0000111") || (opcode === BitPat("b1010111") && fun6 === BitPat("b010111"))
  decodeResult.readTile := opcode === BitPat("b0100111") || (opcode === BitPat("b1010111") && fun6 === BitPat("b010000"))
  decodeResult.aluType := opcode === BitPat("b1110111")
  decodeResult.eew := Mux(ls, io.request.bits.instruction(30, 29), io.request.bits.csr.sew)
  decodeResult.col := io.request.bits.scalaSource(24)
  decodeResult.accessIndex := io.request.bits.scalaSource
  decodeResult.accessTile := Mux(decodeResult.aluType, io.request.bits.instruction(11, 10), io.request.bits.scalaSource(30, 27))
  val contorlReg: ZVMADecodeResult = RegEnable(decodeResult, 0.U.asTypeOf(decodeResult), io.request.fire)

  // [tk]*[sewB] => [1, 2, 3, 4] * [1, 2, 4] = [1, 2, 3, 4, 6, 8, 12, 16]
  val groupWidth = (csrReg.tk << csrReg.sew)(4, 0)

  val updateSize = parameter.dlen / parameter.elen
  // col buffer 0
  // col always update buffer 0
  // rs1 update buffer 0
  val update1 = !Mux(contorlReg.aluType, io.dataFromLSU.bits.vs1, contorlReg.col)
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

    buffer.zipWithIndex.foreach {case (data, i) =>
      val groupIndex = i / updateSize
      val dataIndex = i % updateSize
      when(enqFire && updateIndex === groupIndex.U){
        data.valid := true.B
        data.bits := dataVec(dataIndex)
      }
      when(io.request.valid) { data.valid := false.B }
    }
    buffer
  }

  val colElementSize = parameter.aluColSize * 2
  val rowElementSize = parameter.aluRowSize * 2

  val colExecuteIndex: UInt = RegInit(0.U(log2Ceil(parameter.TE / colElementSize).W))
  val rowExecuteIndex: UInt = RegInit(0.U(log2Ceil(parameter.TE / rowElementSize).W))

  val colBufferValid = Seq.tabulate(colElementSize) {ei =>
    val accessIndex: UInt = colExecuteIndex ## ei.U(log2Ceil(colElementSize).W)
    VecInit(colBuffer.map(_.valid)).asUInt(accessIndex)
  }.reduce(_ && _)

  val rowBufferValid = Seq.tabulate(rowElementSize) {ei =>
    val accessIndex: UInt = rowExecuteIndex ## ei.U(log2Ceil(rowElementSize).W)
    VecInit(rowBuffer.map(_.valid)).asUInt(accessIndex)
  }.reduce(_ && _)

  val colDataVec: Seq[UInt] = Seq.tabulate(colElementSize) { ei =>
    val accessIndex: UInt = colExecuteIndex ## ei.U(log2Ceil(colElementSize).W)
    VecInit(colBuffer.map(_.bits))(accessIndex)
  }

  val rowDataVec: Seq[UInt] = Seq.tabulate(rowElementSize) { ei =>
    val accessIndex: UInt = rowExecuteIndex ## ei.U(log2Ceil(rowElementSize).W)
    VecInit(rowBuffer.map(_.bits))(accessIndex)
  }

  val aluReadyVec: Vec[Vec[Bool]] = Wire(Vec(parameter.aluColSize, Vec(parameter.aluRowSize, Bool())))
  val aluReady: Bool = aluReadyVec.asUInt.andR

  val nextColIndex: UInt = colExecuteIndex + 1.U
  val nextRowIndex: UInt = rowExecuteIndex + 1.U

  // Fixed Row index if always access same col
  val fixedCol = !contorlReg.aluType && !contorlReg.col
  val fixedRow = !contorlReg.aluType && contorlReg.col

  val isLastCol = ((nextColIndex ## 0.U(log2Ceil(colElementSize).W)) >= csrReg.tm) || fixedCol
  val isLastRow = ((nextRowIndex ## 0.U(log2Ceil(rowElementSize).W)) >= csrReg.tn) || fixedRow

  val noSource = contorlReg.readTile
  val onlyColData = contorlReg.writeTile && contorlReg.col
  val onlyRowData = contorlReg.writeTile && !contorlReg.col
  val dataValid = ((colBufferValid || onlyRowData) && (rowBufferValid || onlyColData)) || noSource
  val issueIdle = RegInit(true.B)
  val dataIssue = aluReady && dataValid && !issueIdle

  val initAccessIndex: UInt = io.request.bits.scalaSource(23, log2Ceil(colElementSize))
  val initCol = Mux(fixedCol, initAccessIndex,  0.U)
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
    issueIdle := false.B
    colExecuteIndex := initCol
    rowExecuteIndex := Mux(fixedRow, initAccessIndex,  0.U)
  }

  val queueDeq: Bool = Wire(Bool())
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
      val reqQueue = Queue.io(new ZVMAExecute(parameter), parameter.subArrayBufferDepth)
      val requestRelease = Wire(Bool())
      val reqToken = pipeToken(parameter.subArrayBufferDepth)(reqQueue.enq.fire, requestRelease)
      aluReadyVec(colIndex)(rowIndex) := reqToken

      // for access single col
      val accessSubIndex = contorlReg.accessIndex(1)
      val indexMath = Mux(contorlReg.col, rowIndex.asUInt === accessSubIndex, colIndex.asUInt === accessSubIndex)
      // todo: alu boundary correction
      val issueCorrect = (indexMath || contorlReg.aluType) && dataIssue
      // data enq queue
      reqQueue.enq.valid := issueCorrect
      reqQueue.enq.bits.colData(0) := colDataVec(2 * colIndex)
      reqQueue.enq.bits.colData(1) := colDataVec(2 * colIndex + 1)
      reqQueue.enq.bits.rowData(0) := rowDataVec(2 * rowIndex)
      reqQueue.enq.bits.rowData(1) := rowDataVec(2 * rowIndex + 1)
      reqQueue.enq.bits.execute := contorlReg.aluType
      reqQueue.enq.bits.writeTile := contorlReg.writeTile
      reqQueue.enq.bits.readTile := contorlReg.readTile
      reqQueue.enq.bits.accessIndex := contorlReg.accessIndex(0)
      reqQueue.enq.bits.col := contorlReg.col
      reqQueue.enq.bits.index := rowExecuteIndex ## colExecuteIndex

      // state ram
      val stateVec: Seq[SRAMInterface[UInt]] = Seq.tabulate(parameter.subArrayRamBank) { i =>
        SRAM(
          size = parameter.ramDepth,
          tpe = UInt((parameter.elen * 4).W),
          numReadPorts = 0,
          numWritePorts = 0,
          numReadwritePorts = 1
        )
      }

      // pipe reg
      // execute stage 0
      val dataPipe0: ZVMAExecute = RegEnable(reqQueue.deq.bits, 0.U.asTypeOf(reqQueue.deq.bits), reqQueue.deq.fire)
      val pipeValid0: Bool = RegNext(reqQueue.deq.fire, false.B)

      // execute stage 1
      val dataPipe1: ZVMAExecute = RegInit(0.U.asTypeOf(new ZVMAExecute(parameter)))
      val pipeValid1: Bool = RegNext(pipeValid0, false.B)
      val readData: UInt = RegInit(0.U((4 * parameter.elen).W))

      // execute stage 2
      val pipeValid2: Bool = RegNext(pipeValid1, false.B)
      val index2: UInt = RegInit(0.U.asTypeOf(dataPipe0.index))
      val result: UInt = RegInit(0.U((4 * parameter.elen).W))
      val resultR2: UInt = RegInit(0.U((2 * parameter.elen).W))
      val writeState2 = RegInit(false.B)

      // alu
      val readDataVec = cutUInt(readData, parameter.elen)
      val aluResult = Wire(Vec(4, UInt(parameter.elen.W)))
      dataPipe1.rowData.zipWithIndex.foreach { case (rd, ri) =>
        dataPipe1.colData.zipWithIndex.foreach { case (cd, ci) =>
          val di: Int = (ri << 1) + ci
          val base = readDataVec(di)
          // TODO: Temporarily there is int8
          val rdVec = cutUInt(rd, 8)
          val cdVec = cutUInt(cd, 8)
          val adderRes = base + rdVec.zipWithIndex.map {case (d, i) =>
            Mux(csrReg.tk > i.U, d * cdVec(i), 0.U)
          }.reduce(_ + _)
          val loadDataSelect = Mux(dataPipe1.col, cd, rd)
          val useLoadData = Mux(dataPipe1.col, ri.asUInt === dataPipe1.accessIndex, ci.asUInt === dataPipe1.accessIndex)
          aluResult(di) := Mux(dataPipe1.execute, adderRes, Mux(useLoadData, loadDataSelect, base))
        }
      }
      val mvData = Mux(
        dataPipe1.col,
        Mux(dataPipe1.accessIndex, readDataVec(3) ## readDataVec(2), readDataVec(1) ## readDataVec(0)),
        Mux(dataPipe1.accessIndex, readDataVec(3) ## readDataVec(1), readDataVec(2) ## readDataVec(0))
      )

      // control
      val readReady = !(pipeValid2 && writeState2) || (index2(0) ^ reqQueue.deq.bits.index(0))
      reqQueue.deq.ready := readReady

      when(pipeValid0) {
        dataPipe1 := dataPipe0
        readData := Mux(
          dataPipe0.index(0),
          stateVec.last.readwritePorts.head.readData,
          stateVec.head.readwritePorts.head.readData
        )
      }

      when(pipeValid1) {
        index2 := dataPipe1.index
        result := aluResult.asUInt
        writeState2 := !dataPipe1.readTile
        resultR2 := mvData
      }

      requestRelease := pipeValid2
      // rf read write
      stateVec.zipWithIndex.foreach {case (ram, index) =>
        val write = pipeValid2 && (index2(0) === index.U) && writeState2
        val tryToRead = reqQueue.deq.valid && (reqQueue.deq.bits.index(0) === index.U)
        ram.readwritePorts.head.enable := write || tryToRead
        ram.readwritePorts.head.address := contorlReg.accessTile ## Mux(
          write,
          index2,
          reqQueue.deq.bits.index
        ) >> 1
        ram.readwritePorts.head.isWrite := write
        ram.readwritePorts.head.writeData := result
      }
      subArrReadDataQueue(colIndex)(rowIndex).enq.valid := pipeValid2 && !writeState2
      subArrReadDataQueue(colIndex)(rowIndex).enq.bits := resultR2
    }
  }

  val mvSubIndex = contorlReg.accessIndex(1)
  val mvData = Mux(
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
  val mvValid = Mux(
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
  val dataBuffer: UInt = RegInit(0.U(parameter.dlen.W))
  val queueDeqReady: Bool = !dataBufferValid || io.dataToLSU.ready
  val queueDeqFire: Bool = queueDeqReady && mvValid.andR
  queueDeq := queueDeqFire
  when(queueDeqFire) {
    dataBufferValid := !dataBufferValid
    dataBuffer := mvData
  }

  io.dataToLSU.valid := dataBufferValid && mvValid.andR
  io.dataToLSU.bits := mvData ## dataBuffer
  io.idle := issueIdle
}
