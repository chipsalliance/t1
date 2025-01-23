package org.chipsalliance.t1.rtl.zvma

import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

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
  val csr = new ZVMCsrInterface(parameter)
}

class ZVMADecodeResult extends Bundle {
  val loadType: Bool = Bool()
  val storeType: Bool = Bool()
  val aluType: Bool = Bool()
  val accessTile: UInt = UInt(4.W)
}

class ZVMADataChannel(parameter: ZVMAParameter) extends Bundle {
  // The data will be converted into segment format in lsu
  val data: UInt = UInt(parameter.dlen.W)

  val index: UInt = UInt(parameter.dataIndexBit.W)
}

class ZVMAExecute(parameter: ZVMAParameter) extends Bundle {
  val colData = Vec(2, Vec(4, UInt(parameter.elen.W)))
  val rowData = Vec(2, Vec(4, UInt(parameter.elen.W)))
  val index: UInt = UInt((
    log2Ceil(parameter.TE / parameter.aluColSize / 2) +
      log2Ceil(parameter.TE / parameter.aluRowSize / 2)
    ).W)
}

class ZVMAInterface(parameter: ZVMAParameter) extends Bundle {
  val clock          = Input(Clock())
  val reset          = Input(Reset())
  val request: ValidIO[ZVMAInstRequest] = Flipped(Valid(new ZVMAInstRequest(parameter)))
  val dataFromLSU: DecoupledIO[ZVMADataChannel] = Flipped(Decoupled(new ZVMADataChannel(parameter)))
  val dataToLSU: DecoupledIO[ZVMADataChannel] = Decoupled(new ZVMADataChannel(parameter))
  val idle: Bool = Output(Bool())
}

class ZVMA(val parameter: ZVMAParameter)
  extends FixedIORawModule(new ZVMAInterface(parameter))
  with SerializableModule[ZVMAParameter]
  with ImplicitClock
  with ImplicitReset {
  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val instReg: ZVMAInstRequest = RegEnable(io.request.bits, 0.U.asTypeOf(io.request.bits), io.request.fire)

  // todo: decode
  val decodeResult: ZVMADecodeResult = WireDefault(0.U.asTypeOf(new ZVMADecodeResult))
  val decodeReg: ZVMADecodeResult = RegEnable(decodeResult, 0.U.asTypeOf(decodeResult), io.request.fire)

  // [tk]*[sewB] => [1, 2, 3, 4] * [1, 2, 4] = [1, 2, 3, 4, 6, 8, 12, 16]
  val groupWidth = (instReg.csr.tk << instReg.csr.sew)(4, 0)

  // Data alignment
  val ptrSize: Int = log2Ceil(parameter.dlen / 8)
  val dataReg: UInt = RegInit(0.U(parameter.dlen.W))
  val dataValid: Bool = RegInit(false.B)
  val dataPtr: UInt = RegInit(0.U(ptrSize.W))

  val ptrUpdate: UInt = dataPtr +& (groupWidth ## false.B)
  // The data in the register is enough for the next distribution
  val dataEnough: Bool = !ptrUpdate(ptrSize) || !ptrUpdate(ptrSize - 1, 0).orR
  val dataDeqValid: Bool = io.dataFromLSU.valid || dataEnough
  val dataEnqReady: Bool = !dataValid || ptrUpdate(ptrSize)
  val dataEnqFire: Bool = dataEnqReady && io.dataFromLSU.valid
  val dataDeqFire: Bool = dataDeqValid && ptrUpdate(ptrSize)

  val deqData0: UInt = ((io.dataFromLSU.bits.data ## dataReg) >> (dataPtr ## 0.U(3.W))).asUInt
  val deqData1: UInt = (deqData0 >> (groupWidth ## 0.U(3.W))).asUInt
  val deqDataVec: Seq[UInt] = Seq(deqData0, deqData1)

  when(dataDeqFire ^ dataEnqFire) {
    dataValid := dataEnqFire
  }
  when(dataDeqFire || dataEnqFire) {
    dataPtr := ptrUpdate
  }
  when(dataEnqFire) {
    dataReg := io.dataFromLSU.bits.data
  }


  // source buffer
  val sourceSew1H: UInt = UIntToOH(instReg.csr.sew)(2, 0)
  val Seq(rowBuffer, colBuffer) = parameter.aluSizeVec.zipWithIndex.map { case (_, index) =>
    val buffer: Seq[ValidIO[Vec[UInt]]] = Seq.tabulate(parameter.TE) { _ =>
      RegInit(0.U.asTypeOf(Valid(Vec(4, UInt(parameter.elen.W)))))
    }

    // update entry index
    val updateIndex = RegInit(0.U(log2Ceil(parameter.TE).W))
    when(dataDeqFire || io.request.valid) {
      updateIndex := Mux(dataDeqFire, updateIndex + 1.U, 0.U)
    }

    val deqData = deqDataVec(index)
    val dataSplitVec = Seq.tabulate(4) {dataIndex =>
      Mux1H(sourceSew1H, Seq(8, 16, 32).map{ w =>
        0.U((parameter.elen - w).W) ## deqData(w * (dataIndex + 1) - 1, 16 * dataIndex)
      })
    }

    buffer.zipWithIndex.foreach {case (data, i) =>
      when(dataDeqFire && updateIndex === i.U){
        data.valid := true.B
        data.bits.zipWithIndex.foreach {case (de, di) =>
          when(di.U < instReg.csr.tk) {
            de := dataSplitVec(di)
          }
        }
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

  val colDataVec: Seq[Vec[UInt]] = Seq.tabulate(colElementSize) { ei =>
    val accessIndex: UInt = colExecuteIndex ## ei.U(log2Ceil(colElementSize).W)
    VecInit(colBuffer.map(_.bits))(accessIndex)
  }

  val rowDataVec: Seq[Vec[UInt]] = Seq.tabulate(rowElementSize) { ei =>
    val accessIndex: UInt = rowExecuteIndex ## ei.U(log2Ceil(rowElementSize).W)
    VecInit(rowBuffer.map(_.bits))(accessIndex)
  }

  val aluReadyVec: Vec[Vec[Bool]] = Wire(Vec(parameter.aluColSize, Vec(parameter.aluRowSize, Bool())))
  val aluReady: Bool = aluReadyVec.asUInt.andR

  val nextColIndex: UInt = colExecuteIndex + 1.U
  val nextRowIndex: UInt = rowExecuteIndex + 1.U

  val isLastCol = (nextColIndex ## 0.U(log2Ceil(colElementSize).W)) > instReg.csr.tm
  val isLastRow = (nextRowIndex ## 0.U(log2Ceil(rowElementSize).W)) > instReg.csr.tn

  val issueIdle = RegInit(false.B)
  val dataIssue = aluReady && colBufferValid && rowBufferValid && !issueIdle

  when(dataIssue) {
    colExecuteIndex := Mux(isLastCol, 0.U, nextColIndex)
    when(isLastCol) {
      rowExecuteIndex := nextRowIndex
    }
    when(isLastCol && isLastRow) {
      issueIdle := true.B
    }
  }

  when(io.request.valid) {
    issueIdle := false.B
    colExecuteIndex := 0.U
    rowExecuteIndex := 0.U
  }

  //  execute Unit & Storage
  val subArrayVec = Seq.tabulate(parameter.aluColSize) { colIndex =>
    Seq.tabulate(parameter.aluRowSize) { rowIndex =>
      val reqQueue = Queue.io(new ZVMAExecute(parameter), parameter.subArrayBufferDepth)
      val requestRelease = Wire(Bool())
      val reqToken = pipeToken(parameter.subArrayBufferDepth)(reqQueue.enq.fire, requestRelease)
      aluReadyVec(colIndex)(rowIndex) := reqToken

      // data enq queue
      reqQueue.enq.valid := dataIssue
      reqQueue.enq.bits.colData(0) := colDataVec(2 * colIndex)
      reqQueue.enq.bits.colData(1) := colDataVec(2 * colIndex + 1)
      reqQueue.enq.bits.rowData(0) := rowDataVec(2 * rowIndex)
      reqQueue.enq.bits.rowData(1) := rowDataVec(2 * rowIndex + 1)
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
      val index: UInt = RegInit(0.U.asTypeOf(dataPipe0.index))
      val resultC: UInt = RegInit(0.U((4 * parameter.elen).W))
      val resultS: UInt = RegInit(0.U((4 * parameter.elen).W))

      // execute stage 3
      val pipeValid3: Bool = RegNext(pipeValid2, false.B)
      val index3: UInt = RegInit(0.U.asTypeOf(dataPipe0.index))
      val result: UInt = RegInit(0.U((4 * parameter.elen).W))

      // alu
      val sourceByteMask: UInt = Mux1H(
        UIntToOH(instReg.csr.sew),
        Seq(1.U(4.W), 3.U(4.W), 15.U(4.W))
      )
      val sourceBitMask = FillInterleaved(8, sourceByteMask)
      val readDataVec = cutUInt(readData, parameter.elen)
      val aluResult = Wire(Vec(4, UInt(parameter.elen.W)))
      dataPipe1.rowData.zipWithIndex.foreach { case (rd, ri) =>
        dataPipe1.colData.zipWithIndex.foreach { case (cd, ci) =>
          val di: Int = ri << 1 + ci
          val base = readDataVec(di)
          aluResult(di) := base + rd.zipWithIndex.map {case (d, i) =>
            Mux(instReg.csr.tk > i.U, (d & sourceBitMask) * (cd(i) & sourceBitMask), 0.U)
          }.reduce(_ + _)
        }
      }

      // control
      val readReady = !pipeValid3 || (index3(0) ^ reqQueue.deq.bits.index(0))
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
        index := dataPipe1.index
        // todo
        resultC := 0.U
        resultS := aluResult.asUInt
      }

      when(pipeValid2) {
        index3 := index
        // todo
        result := resultC + resultS
      }

      // rf read write
      stateVec.zipWithIndex.foreach {case (ram, index) =>
        val write = pipeValid3 && (index3(0) === index.U)
        val tryToRead = reqQueue.deq.valid && (reqQueue.deq.bits.index(0) === index.U)
        ram.readwritePorts.head.enable := write || tryToRead
        ram.readwritePorts.head.address := decodeReg.accessTile ## Mux(
          write,
          index3,
          reqQueue.deq.bits.index
        ) >> 1
        ram.readwritePorts.head.isWrite := write
        ram.readwritePorts.head.writeData := result
      }
    }
  }
}
