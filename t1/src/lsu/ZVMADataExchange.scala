// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lsu

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.{BitPat, Decoupled, DecoupledIO, Mux1H, RegEnable, UIntToOH, Valid, ValidIO, log2Ceil}
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl.{CSRInterface, LSURequest, LaneParameter, UIntToOH1, VRFReadRequest, VRFWriteRequest, changeUIntSize, cutUInt, cutUIntBySize, pipeToken, pipeTokenCount}

case class ZVMADataExchangeParam(chainingSize:     Int,
                                 datapathWidth:    Int,
                                 vLen:             Int,
                                 laneNumber:       Int,
                                 paWidth:          Int,
                                 lsuTransposeSize: Int,
                                 vrfReadLatency:   Int,
                                 TE:               Int) extends SerializableModuleParameter {
  val vlMax: Int = vLen
  val vlMaxBits: Int = log2Ceil(vlMax) + 1
  val regNumBits: Int = log2Ceil(32)
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)
  val vrfReadOutStanding: Int = 8
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1
  val cacheLineIndexBits: Int = log2Ceil(vLen / lsuTransposeSize + 1)
  val cacheLineBits: Int = log2Ceil(lsuTransposeSize)
  val storeDataQueueSize = 8
  val loadDataQueueSize = 8
  val dlen = datapathWidth * laneNumber

  // row || col read index
  val zvmaGroupIndexWidth = log2Ceil(TE * 32 / (datapathWidth * laneNumber))
  val memIndexBit = log2Ceil(TE * 32 / 8 / lsuTransposeSize)
}

class ZVMAInstRequest(dataPathWidth: Int) extends Bundle {
  val inst: UInt = UInt(32.W)
  val instructionIndex: UInt = UInt(3.W)
  val address: UInt = UInt(dataPathWidth.W)
}

class DataToZVMA(dlen: Int) extends Bundle {
  val data = UInt(dlen.W)
  val vs1 = Bool()
}

class ZVMAMemRequest(param: ZVMADataExchangeParam) extends Bundle {
  val src:     UInt = UInt(param.cacheLineIndexBits.W)
  val address: UInt = UInt(param.paWidth.W)
  val data:    UInt = UInt((param.lsuTransposeSize * 8).W)
  val mask:    UInt = UInt(param.lsuTransposeSize.W)
  val write:   Bool = Bool()
}

class ZVMADataExchangeInterface(param: ZVMADataExchangeParam) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(Reset())

  val instRequest: ValidIO[ZVMAInstRequest] = Flipped(Valid(new ZVMAInstRequest(param.datapathWidth)))

  val csrInterface: CSRInterface = Input(new CSRInterface(param.vlMaxBits))

  val memRequest: DecoupledIO[ZVMAMemRequest] = Decoupled(new ZVMAMemRequest(param))

  val memResponse: DecoupledIO[UInt] = Flipped(Decoupled(UInt(param.dlen.W)))

  val dataFromZVMA: DecoupledIO[UInt] = Flipped(Decoupled(UInt(param.dlen.W)))
  val datatoZVMA: DecoupledIO[DataToZVMA] = Decoupled(new DataToZVMA(param.dlen))

  val idle: Bool = Output(Bool())
  val instructionIndex: UInt = Output(UInt(3.W))

  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = Vec(
    param.laneNumber,
    Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
  )

  val vrfReadResults:  Vec[ValidIO[UInt]] = Input(Vec(param.laneNumber, Valid(UInt(param.datapathWidth.W))))

  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = Vec(
    param.laneNumber,
    Decoupled(
      new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
    )
  )

}

class ZVMAReadMessage(parameter: ZVMADataExchangeParam) extends Bundle {
  val index: UInt = UInt(parameter.zvmaGroupIndexWidth.W)
  val vs1: Bool = Bool()
  val tk: UInt = UInt(2.W)
}

// opcode: mv: 1010111, alu: 1110111, load: 0000111, st: 0100111
class ZVMADecode  extends Bundle {
  val ls: Bool = Bool()
  val mv: Bool = Bool()
  val arithmetic: Bool = Bool()
  val fromTile: Bool = Bool()
  val readVrf: Bool = Bool()
}

class ZVMAInstructionPipe extends Bundle {
  val decode: ZVMADecode = new ZVMADecode
  val address: UInt = UInt(32.W)
  val vs1: UInt = UInt(5.W)
  val vs2: UInt = UInt(5.W)
  val vd: UInt = UInt(5.W)
  val instructionIndex: UInt = UInt(3.W)
}

class ZVMAState(parameter: ZVMADataExchangeParam) extends Bundle {
  val vs2Index: UInt = UInt(parameter.zvmaGroupIndexWidth.W)
  val vs1Index: UInt = UInt(parameter.zvmaGroupIndexWidth.W)

  val idle: Bool = Bool()

  val readVs1: Bool = Bool()
  val readTkIndex: UInt = UInt(2.W)

  val memAccessIndex: UInt = UInt(parameter.memIndexBit.W)
  val mvIndex: UInt = UInt(parameter.memIndexBit.W)
  val lastVs1 = Bool()
  val lastVs2 = Bool()
}

class ZVMADataExchange (val parameter: ZVMADataExchangeParam)
  extends FixedIORawModule(new ZVMADataExchangeInterface(parameter))
    with SerializableModule[ZVMADataExchangeParam]
    with ImplicitClock
    with ImplicitReset {
  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val instructionReg: ZVMAInstructionPipe = RegInit(0.U.asTypeOf(new ZVMAInstructionPipe))
  val csrReg: CSRInterface = RegEnable(io.csrInterface, 0.U.asTypeOf(io.csrInterface), io.instRequest.valid)

  val stateInit: ZVMAState = WireDefault(0.U.asTypeOf(new ZVMAState(parameter)))
  stateInit.idle := true.B
  val state: ZVMAState = RegInit(stateInit)

  val messageQueue: QueueIO[ZVMAReadMessage] =  Queue.io(new ZVMAReadMessage(parameter), parameter.vrfReadOutStanding)
  // todo: move to channel
  val readRequestQueue = Queue.io(
    new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits),
    parameter.vrfReadOutStanding,
    pipe = true,
    flow = true
  )

  val opcode: UInt = io.instRequest.bits.inst(6, 0)
  val fun6: UInt = io.instRequest.bits.inst(31, 26)
  val lastTkIndex: UInt = csrReg.tk - 1.U

  // handle tm|tn
  val groupByteBit = log2Ceil((parameter.datapathWidth / 8) * parameter.laneNumber)
  val lastVs1Byte: UInt = (Mux(instructionReg.decode.arithmetic, csrReg.vl, 0.U) << csrReg.vSew).asUInt
  val lastVs2Byte: UInt = (Mux(instructionReg.decode.arithmetic, csrReg.tm, csrReg.vl) << csrReg.vSew).asUInt
  val lastVs1Group = (lastVs1Byte >> groupByteBit).asUInt - !changeUIntSize(lastVs1Byte, groupByteBit).orR
  val lastVs2Group = (lastVs2Byte >> groupByteBit).asUInt - !changeUIntSize(lastVs2Byte, groupByteBit).orR
  val isLastVs1Group = lastVs1Group === state.vs1Index
  val isLastVs2Group = lastVs2Group === state.vs2Index
  val lastTk: Bool = state.readTkIndex === lastTkIndex || !instructionReg.decode.arithmetic

  // mem access
  /** How many byte will be accessed by this instruction(write vrf) */
  val bytePerInstruction: UInt = (io.csrInterface.vl << io.instRequest.bits.inst(30, 29)).asUInt

  /** access byte + address offset(access memory) */
  val accessMemSize: UInt = bytePerInstruction + io.instRequest.bits.address(parameter.cacheLineBits - 1, 0)

  /** How many cache lines will be accessed by this instruction nFiled * vl * (2 ** eew) / 32
   */
  val lastCacheLineIndex: UInt = (accessMemSize >> parameter.cacheLineBits).asUInt -
    !accessMemSize(parameter.cacheLineBits - 1, 0).orR

  val lastCacheLineReg: UInt = RegEnable(lastCacheLineIndex, 0.U, io.instRequest.valid)
  val lastAccess = state.memAccessIndex === lastCacheLineReg

  when(io.instRequest.valid) {
    instructionReg.vs1 := io.instRequest.bits.inst(19, 15)
    instructionReg.vs2 := io.instRequest.bits.inst(24, 20)
    instructionReg.vd := io.instRequest.bits.inst(11, 7)
    instructionReg.address := io.instRequest.bits.address
    instructionReg.instructionIndex := io.instRequest.bits.instructionIndex
    instructionReg.decode.ls := opcode === BitPat("b0?00111")
    instructionReg.decode.mv := opcode === BitPat("b1010111")
    instructionReg.decode.arithmetic := opcode === BitPat("b1110111")
    instructionReg.decode.fromTile := opcode === BitPat("b0100111") ||
      (opcode === BitPat("b1010111") && fun6 === BitPat("b010000"))
    instructionReg.decode.readVrf := opcode === BitPat("b1110111") ||
      (opcode === BitPat("b1010111") && fun6 === BitPat("b010111"))

    state := 0.U.asTypeOf(state)
  }.elsewhen(messageQueue.enq.ready && instructionReg.decode.readVrf && !state.idle) {
    when(lastTk) {
      state.readVs1 := Mux(
        state.readVs1,
        state.lastVs2,
        !state.lastVs1
      )
      state.vs1Index := state.vs1Index + state.readVs1
      state.vs2Index := state.vs2Index + !state.readVs1
      state.readTkIndex := 0.U
      when(isLastVs2Group && !state.readVs1) {
        state.lastVs2 := true.B
      }
      when(isLastVs1Group && state.readVs1) {
        state.lastVs1 := true.B
      }
      when(isLastVs2Group && state.lastVs1 || isLastVs1Group && state.lastVs2) {
        state.idle := true.B
      }
    }.otherwise {
      state.readTkIndex := state.readTkIndex + 1.U
    }
  }.elsewhen(io.memRequest.fire && instructionReg.decode.ls) {
    when(lastAccess) {
      state.idle := true.B
    }.otherwise {
      state.memAccessIndex := state.memAccessIndex + 1.U
    }
  }

  val vrfReadQueueVec: Seq[QueueIO[UInt]] = Seq.tabulate(parameter.laneNumber)(_ =>
    Queue.io(UInt(parameter.datapathWidth.W), parameter.vrfReadOutStanding, flow = true, pipe = true)
  )

  val needReadForVRF = instructionReg.decode.arithmetic || (instructionReg.decode.mv && !instructionReg.decode.mv)
  val rotateSource = instructionReg.decode.arithmetic
  val tkIndexMax: UInt = Mux(instructionReg.decode.arithmetic, csrReg.tk - 1.U, 0.U)

  val readIndex = Mux(state.readVs1, state.vs1Index, state.vs2Index)
  val readOffset = readIndex(parameter.vrfOffsetBits - 1, 0)

  val regGroupSize = Mux1H(UIntToOH(csrReg.vSew)(1, 0), Seq(2.U, 4.U))
  val readRegIndex: UInt = Mux(state.readVs1, instructionReg.vs1, instructionReg.vs2) +
    state.readTkIndex * regGroupSize + (readIndex >> parameter.vrfOffsetBits).asUInt

  // request enq readRequestQueue
  readRequestQueue.enq.valid                 := !state.idle && messageQueue.enq.ready && instructionReg.decode.readVrf
  readRequestQueue.enq.bits.vs               := readRegIndex
  readRequestQueue.enq.bits.readSource       := 2.U
  readRequestQueue.enq.bits.offset           := readOffset
  readRequestQueue.enq.bits.instructionIndex := instructionReg.instructionIndex

  // enq messageQueue
  messageQueue.enq.valid := !state.idle && instructionReg.decode.readVrf
  messageQueue.enq.bits.vs1 := state.readVs1
  messageQueue.enq.bits.tk := state.readTkIndex
  messageQueue.enq.bits.index := Mux(state.readVs1, state.vs1Index, state.vs2Index)

  val readSendState: UInt = RegInit(0.U(parameter.laneNumber.W))
  val sendPort: UInt = VecInit(io.vrfReadDataPorts.map(_.fire)).asUInt
  val sendUpdate: UInt = readSendState | sendPort
  readRequestQueue.deq.ready := sendPort.andR || (sendPort.orR && sendUpdate.andR)
  when(sendPort.orR || readRequestQueue.deq.fire) {
    readSendState := Mux(readRequestQueue.deq.fire, 0.U, sendUpdate)
  }

  Seq.tabulate(parameter.laneNumber) { laneIndex =>
    val readPort: DecoupledIO[VRFReadRequest] = io.vrfReadDataPorts(laneIndex)
    val dataQueue = vrfReadQueueVec(laneIndex)

    // read request send
    readPort.valid := readRequestQueue.deq.valid && !readSendState(laneIndex)
    readPort.bits := readRequestQueue.deq.bits

    // read result
    dataQueue.enq.valid := io.vrfReadResults(laneIndex).valid
    dataQueue.enq.bits := io.vrfReadResults(laneIndex).bits
  }

  // 0 -> vs1, 1 -> vs2
  val dataBufferReady: Vec[Bool] = Wire(Vec(2, Bool()))
  val dataValid: Bool = VecInit(vrfReadQueueVec.map(_.deq.valid)).asUInt.andR
  val dataReady: Bool = Mux(messageQueue.deq.bits.vs1, dataBufferReady.head, dataBufferReady.last)
  val dataFire = dataValid && dataReady
  vrfReadQueueVec.foreach(_.deq.ready := dataFire)
  messageQueue.deq.ready := dataFire

  val dataEntry = VecInit(vrfReadQueueVec.map(_.deq.bits)).asUInt
  val sendSew: UInt = Mux(instructionReg.decode.arithmetic, csrReg.vSew, 2.U)
  val sendSew1H: UInt = UIntToOH(sendSew)(2, 0)
  val sendLastIndex: UInt = Mux1H(
    sendSew1H,
    Seq(
      3.U(2.W),
      1.U(2.W),
      0.U(2.W),
    )
  )
  val bufferEnqFire = Seq(dataFire && messageQueue.deq.bits.vs1, dataFire && !messageQueue.deq.bits.vs1)

  // buffer deq
  val deqDataValid: Vec[Bool] = Wire(Vec(2, Bool()))
  val deqDataLast: Vec[Bool] = Wire(Vec(2, Bool()))
  val deqDataReady: Vec[Bool] = Wire(Vec(2, Bool()))
  val deqData: Vec[UInt] = Wire(Vec(2, UInt((parameter.datapathWidth * parameter.laneNumber).W)))
  // data buffer
  Seq.tabulate(2){i =>
    val valid = RegInit(false.B)
    val index = RegInit(0.U(parameter.zvmaGroupIndexWidth.W))
    val deqIndex = RegInit(0.U(2.W))
    val dataVec: Vec[UInt] = RegInit(VecInit(Seq.tabulate(4)(_ =>
      0.U((parameter.datapathWidth * parameter.laneNumber).W)
    )))

    dataVec.zipWithIndex.foreach {case (d, di) =>
      when(bufferEnqFire(i) && di.U === messageQueue.deq.bits.tk) {
        d := dataEntry
      }
    }

    val bufferEnq = bufferEnqFire(i) && messageQueue.deq.bits.tk === lastTkIndex
    deqDataValid(i) := valid
    deqDataLast(i) := deqIndex === sendLastIndex
    deqData(i) := Mux1H(
      sendSew1H,
      Seq(
        VecInit(dataVec.map(s => Mux1H(UIntToOH(deqIndex), cutUIntBySize(s, 4))).
          map(s => cutUInt(s, 8)).transpose.map(s => VecInit(s).asUInt)).asUInt,
        VecInit(dataVec.init.init.map(s => Mux1H(UIntToOH(deqIndex(0)), cutUIntBySize(s, 2))).
          map(s => cutUInt(s, 16)).transpose.map(s => VecInit(s).asUInt)).asUInt,
        dataVec.head,
      )
    )
    val bufferDeq = valid && deqDataReady(i) && deqDataLast(i)
    when(bufferEnq ^ bufferDeq) {
      valid := bufferEnq
    }
    when(valid && deqDataReady(i)) {
      deqIndex := Mux(deqDataLast(i), 0.U, deqIndex + 1.U)
    }
    when(bufferEnq) {
      index := messageQueue.deq.bits.index
    }
    dataBufferReady(i) := !valid || (deqDataReady(i) && deqDataLast(i))
  }

  // data from zvma
  val zvmaDataQueue = Queue.io(UInt(parameter.dlen.W), parameter.storeDataQueueSize, flow = true, pipe = true)
  // data from mem
  val memDataQueue = Queue.io(UInt(parameter.dlen.W), parameter.loadDataQueueSize, flow = true, pipe = true)


  zvmaDataQueue.enq <> io.dataFromZVMA
  memDataQueue.enq <> io.memResponse

  when(!io.instRequest.valid && zvmaDataQueue.deq.fire && instructionReg.decode.mv) {
    state.mvIndex := state.mvIndex + 1.U
  }

  // load token
  val loadIssue = Wire(Bool())
  val loadCount: UInt = pipeTokenCount(parameter.loadDataQueueSize)(loadIssue, memDataQueue.deq.fire)
  val loadToken: Bool = !loadCount.asBools.last
  val loadIdle:  Bool = !loadCount.orR


  val addressInit = instructionReg.address(parameter.cacheLineBits - 1, 0)
  val baseAddress: UInt = (instructionReg.address >> parameter.cacheLineBits << parameter.cacheLineBits).asUInt
  val shifterReg: UInt = RegInit(0.U(parameter.dlen.W))

  val storeDataAfterAlign: UInt = (((zvmaDataQueue.deq.bits ## shifterReg) << (addressInit ## 0.U(3.W))) >> parameter.dlen).asUInt
  val loadDataAfterAlign: UInt = (((memDataQueue.deq.bits ## shifterReg) << (addressInit ## 0.U(3.W))) >> parameter.dlen).asUInt

  when(instructionReg.decode.ls && zvmaDataQueue.deq.fire || memDataQueue.deq.fire) {
    shifterReg := Mux(zvmaDataQueue.deq.fire, zvmaDataQueue.deq.bits, memDataQueue.deq.bits)
  }

  val store = instructionReg.decode.ls && instructionReg.decode.fromTile
  val storeValid = store && zvmaDataQueue.deq.valid
  val accessAddress = baseAddress + state.memAccessIndex ## 0.U(parameter.cacheLineBits.W)

  val load = instructionReg.decode.ls && !instructionReg.decode.fromTile
  val loadValid = load && loadToken


  // mem access
  io.memRequest.valid := (storeValid || loadValid) && !state.idle
  io.memRequest.bits.src := state.memAccessIndex
  io.memRequest.bits.address := accessAddress
  io.memRequest.bits.data := storeDataAfterAlign
  // todo:
  io.memRequest.bits.mask := -1.S.asTypeOf(io.memRequest.bits.mask)
  io.memRequest.bits.write := instructionReg.decode.fromTile

  loadIssue := loadValid && io.memRequest.ready && !state.idle

  // zvma write to lane
  val writeVrfState = RegInit(0.U(parameter.laneNumber.W))
  val tryToWriteVrf = zvmaDataQueue.deq.valid && instructionReg.decode.mv
  io.vrfWritePort.zipWithIndex.foreach {case (write, i) =>
    write.valid := tryToWriteVrf && !writeVrfState(i)
    write.bits.vd := instructionReg.vd + (state.mvIndex >> parameter.vrfOffsetBits).asUInt
    write.bits.offset := state.mvIndex
    // todo: tail mask
    write.bits.mask := (-1.S((parameter.datapathWidth/8).W)).asUInt
    write.bits.data := cutUInt(zvmaDataQueue.deq.bits, parameter.datapathWidth)(i)
    write.bits.last := false.B
    write.bits.instructionIndex := instructionReg.instructionIndex
  }
  val writeFire = VecInit(io.vrfWritePort.map(_.fire)).asUInt
  val writeStateUpdate = writeFire | writeVrfState
  val allWrite = writeStateUpdate.andR
  when(writeFire.orR || io.instRequest.valid) {
    writeVrfState := Mux(allWrite || io.instRequest.valid, 0.U, writeStateUpdate)
  }
  zvmaDataQueue.deq.ready := Mux(instructionReg.decode.mv, allWrite, io.memRequest.ready)


  // mem -> zvma <- vrf
  io.datatoZVMA.valid := memDataQueue.deq.valid || deqDataValid.asUInt.orR
  io.datatoZVMA.bits.data := Mux(
    instructionReg.decode.ls,
    loadDataAfterAlign,
    Mux(deqDataValid.head, deqData.head, deqData.last)
  )
  io.datatoZVMA.bits.vs1 := deqDataValid.head
  memDataQueue.deq.ready := io.datatoZVMA.ready
  deqDataReady.head := io.datatoZVMA.ready
  deqDataReady.last := io.datatoZVMA.ready && !deqDataValid.head

  io.idle := state.idle && loadIdle
  io.instructionIndex := instructionReg.instructionIndex
}
