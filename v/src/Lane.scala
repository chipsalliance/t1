package v

import chisel3._
import chisel3.util._

case class LaneParameters(ELEN: Int = 32, VLEN: Int = 1024, lane: Int = 8, chainingSize: Int = 4) {
  val instIndexSize:  Int = log2Ceil(chainingSize) + 1
  val VLMax:          Int = VLEN
  val VLMaxBits:      Int = log2Ceil(VLMax)
  val groupSize:      Int = VLMax / lane
  val controlNum:     Int = 4
  val HLEN:           Int = ELEN / 2
  val executeUnitNum: Int = 6
  val laneIndexBits:  Int = log2Ceil(lane)
  val writeQueueSize = 8

  def vrfParam: VRFParam = VRFParam(VLEN, lane, groupSize, ELEN)
}

class InstructionDecodeResult extends Bundle {
  val logicUnit:  Bool = Bool()
  val adderUnit:  Bool = Bool()
  val shiftUnit:  Bool = Bool()
  val mulUnit:    Bool = Bool()
  val divUnit:    Bool = Bool()
  val otherUnit:  Bool = Bool()
  val firstWiden: Bool = Bool()
  val eew16:      Bool = Bool()
  val nr:         Bool = Bool()
  val red:        Bool = Bool()
  val maskB:      Bool = Bool()
  val reverse:    Bool = Bool()
  val narrow:     Bool = Bool()
  val Widen:      Bool = Bool()
  val saturate:   Bool = Bool()
  val average:    Bool = Bool()
  val unSigned0:  Bool = Bool()
  val unSigned1:  Bool = Bool()

  /** type of vs1 */
  val vType: Bool = Bool()
  val xType: Bool = Bool()
  val iType: Bool = Bool()
  val uop:   UInt = UInt(4.W)
}

class ExtendInstructionDecodeResult extends Bundle {
  val targetRD: Bool = Bool()
  val vExtend:  Bool = Bool()
  val mv:       Bool = Bool()
  val ffo:      Bool = Bool()
  val popCount: Bool = Bool()
  val viota:    Bool = Bool()
  val vSrc:     Bool = Bool()

  /** type of vs1 */
  val vType:     Bool = Bool()
  val xType:     Bool = Bool()
  val iType:     Bool = Bool()
  val pointless: Bool = Bool()
  val uop:       UInt = UInt(3.W)
}

class LaneReq(param: LaneParameters) extends Bundle {
  val instIndex:    UInt = UInt(param.instIndexSize.W)
  val decodeResult: UInt = UInt(25.W)

  /** vs1 or imm */
  val vs1: UInt = UInt(5.W)

  /** vs2 or rs2 */
  val vs2: UInt = UInt(5.W)

  /** vd or rd */
  val vd: UInt = UInt(5.W)

  /** data of rs1 */
  val readFromScala: UInt = UInt(param.ELEN.W)
}

class InstGroupState(param: LaneParameters) extends Bundle {
  val sRead1:       Bool = Bool()
  val sRead2:       Bool = Bool()
  val sReadVD:      Bool = Bool()
  val wRead1:       Bool = Bool()
  val wRead2:       Bool = Bool()
  val wScheduler:   Bool = Bool()
  val sExecute:     Bool = Bool()
  val sCrossWrite:  Bool = Bool()
  val sSendResult0: Bool = Bool()
  val sSendResult1: Bool = Bool()
}

class InstControlRecord(param: LaneParameters) extends Bundle {
  val originalInformation: LaneReq = new LaneReq(param)
  val state:               InstGroupState = new InstGroupState(param)
  val counter:             UInt = UInt(param.VLMaxBits.W)
}

class LaneCsrInterface(param: LaneParameters) extends Bundle {
  val vl:     UInt = UInt(param.VLMaxBits.W)
  val vStart: UInt = UInt(param.VLMaxBits.W)
  val vlmul:  UInt = UInt(3.W)
  val vSew:   UInt = UInt(2.W)

  /** Rounding mode register */
  val vxrm: UInt = UInt(2.W)

  /** tail agnostic */
  val vta: Bool = Bool()

  /** mask agnostic */
  val vma: Bool = Bool()
}

class LaneDataResponse(param: LaneParameters) extends Bundle {
  val data:      UInt = UInt(param.ELEN.W)
  val maskInput: Bool = Bool()
}

class RingBusData(param: LaneParameters) extends Bundle {
  val data:      UInt = UInt(param.HLEN.W)
  val tail:      Bool = Bool()
  val target:    UInt = UInt(param.laneIndexBits.W)
  val instIndex: UInt = UInt(param.instIndexSize.W)
}

class RingPort(param: LaneParameters) extends Bundle {
  val enq: DecoupledIO[RingBusData] = Flipped(Decoupled(new RingBusData(param)))
  val deq: DecoupledIO[RingBusData] = Decoupled(new RingBusData(param))
}

/**
  * ring & inst control & vrf & vfu
  */
class Lane(param: LaneParameters) extends Module {
  val laneReq:         DecoupledIO[LaneReq] = IO(Flipped(Decoupled(new LaneReq(param))))
  val csrInterface:    LaneCsrInterface = IO(Input(new LaneCsrInterface(param)))
  val dataToScheduler: LaneDataResponse = IO(Flipped(new LaneDataResponse(param)))
  val laneIndex:       UInt = IO(Input(UInt(param.laneIndexBits.W)))
  val readBusPort:     RingPort = IO(new RingPort(param))
  val writeBusPort:    RingPort = IO(new RingPort(param))

  // reg
  val controlValid: Vec[Bool] = RegInit(VecInit(Seq.fill(param.controlNum)(false.B)))
  // read from vs1
  val source1: Vec[Bool] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // read from vs2
  val source2: Vec[Bool] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // read from vd
  val source3: Vec[Bool] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // execute result
  val result: Vec[Bool] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // 跨lane操作的寄存器实际上只有最后一个坑有，但是先全声明了，没用到的让它自己优化去
  // 从rf里面读出来的， 下一个周期试图上环
  val crossReadHeadTX: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.HLEN.W))))
  val crossReadTailTX: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.HLEN.W))))
  // 从环过来的， 两个都好会拼成source2
  val crossReadHeadRX: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.HLEN.W))))
  val crossReadTailRX: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.HLEN.W))))
  val control: Vec[InstControlRecord] = RegInit(
    VecInit(Seq.fill(param.controlNum)(0.U.asTypeOf(new InstControlRecord(param))))
  )

  // wire
  val vrfReadWire: Vec[Vec[DecoupledIO[VRFReadRequest]]] = Wire(
    Vec(param.controlNum, Vec(3, Decoupled(new VRFReadRequest(param.vrfParam))))
  )
  val vrfReadResult:   Vec[Vec[UInt]] = Wire(Vec(param.controlNum, Vec(3, UInt(param.ELEN.W))))
  val controlActive:   Vec[Bool] = Wire(Vec(param.controlNum, Bool()))
  val controlCanShift: Vec[Bool] = Wire(Vec(param.controlNum, Bool()))
  val executeValid:    Vec[Vec[Bool]] = Wire(Vec(param.controlNum, Vec(param.executeUnitNum, Bool())))
  // 读的环index与这个lane匹配上了, 会出环
  val readBusMatch:  Bool = Wire(Bool())
  val writeBusMatch: Bool = Wire(Bool())

  // 以6个执行单元为视角的控制信号
  val executeEnqFire: Vec[Bool] = Wire(Vec(param.executeUnitNum, Bool()))
  val executeDeqFire: Vec[Bool] = Wire(Vec(param.executeUnitNum, Bool()))

  // 作为最老的坑的控制信号
  val tryToSendHead:  Bool = Wire(Bool())
  val tryToSendTail:  Bool = Wire(Bool())
  val sendReady:      Bool = Wire(Bool())
  val sendReadData:   RingBusData = Wire(new RingBusData(param))
  val sendWriteData:  Vec[RingBusData] = Wire(Vec(2, new RingBusData(param)))
  val sendWriteValid: Vec[Bool] = Wire(Vec(2, Bool()))
  val sendWriteReady: Bool = Wire(Bool())

  // 跨lane写rf需要一个queue
  val crossWriteQueue: Queue[RingBusData] = Module(new Queue(new RingBusData(param), param.writeQueueSize))

  control.zipWithIndex.foreach {
    case (record, index) =>
      // read only
      val decodeRes = record.originalInformation.decodeResult
      val decodeResFormat:    InstructionDecodeResult = decodeRes.asTypeOf(new InstructionDecodeResult)
      val decodeResFormatExt: ExtendInstructionDecodeResult = decodeRes.asTypeOf(new ExtendInstructionDecodeResult)
      val extendInst = decodeRes(19) && decodeRes(1, 0).orR
      val needCrossRead = !extendInst && decodeResFormat.firstWiden
      val needCrossWrite = !extendInst && decodeResFormat.Widen
      when(needCrossRead) {
        assert(csrInterface.vSew != 2.U)
      }
      // 跨lane读写的指令我们只有到最老才开始做
      controlActive(index) := controlValid.head && ((index == 0).B || !(needCrossRead || needCrossWrite))
      controlCanShift(index) := !record.state.sExecute
      // vs1 read
      vrfReadWire(index)(0).valid := !record.state.sRead1 && controlActive(index)
      vrfReadWire(index)(0).bits.groupIndex := record.counter
      vrfReadWire(index)(0).bits.vs := record.originalInformation.vs1
      vrfReadWire(index)(0).bits.eew := csrInterface.vSew
      // Mux(decodeResFormat.eew16, 1.U, csrInterface.vSew)

      // vs2 read
      vrfReadWire(index)(1).valid := !record.state.sRead2 && controlActive(index)
      vrfReadWire(index)(1).bits.groupIndex := Mux(needCrossRead, record.counter ## false.B, record.counter)
      vrfReadWire(index)(1).bits.vs := record.originalInformation.vs2
      vrfReadWire(index)(1).bits.eew := csrInterface.vSew

      // vd read
      vrfReadWire(index)(2).valid := !record.state.sReadVD && controlActive(index)
      vrfReadWire(index)(2).bits.groupIndex := Mux(needCrossRead, record.counter ## true.B, record.counter)
      vrfReadWire(index)(2).bits.vs := record.originalInformation.vd
      vrfReadWire(index)(2).bits.eew := csrInterface.vSew

      val readFinish =
        record.state.sReadVD && record.state.sRead1 && record.state.sRead2 && record.state.wRead1 && record.state.wRead2
      executeValid(index) := VecInit(Mux(readFinish, decodeRes(24, 19), 0.U).asBools)

      // 处理读出来的结果
      when(vrfReadWire(index)(0).fire) {
        record.state.sRead1 := true.B
        source1(index) := vrfReadResult(index)(0)
      }
      when(vrfReadWire(index)(1).fire) {
        record.state.sRead2 := true.B
        when(decodeResFormat.firstWiden) {
          crossReadHeadTX(index) := vrfReadResult(index)(1)
        }.otherwise {
          source2(index) := vrfReadResult(index)(1)
        }
      }

      when(vrfReadWire(index)(2).fire) {
        record.state.sReadVD := true.B
        when(decodeResFormat.firstWiden) {
          crossReadTailTX(index) := vrfReadResult(index)(2)
        }.otherwise {
          source3(index) := vrfReadResult(index)(2)
        }
      }
      if (index == 0) {
        tryToSendHead := record.state.sRead2 && !record.state.sSendResult0
        tryToSendTail := record.state.sReadVD && !record.state.sSendResult1
        sendReadData.target := tryToSendTail ## laneIndex(param.laneIndexBits - 1, 1)
        sendReadData.tail := laneIndex(0)
        sendReadData.instIndex := record.originalInformation.instIndex
        sendReadData.data := Mux(tryToSendHead, crossReadHeadTX(0), crossReadTailTX(0))
      }
  }

  // 处理读环的
  {
    val readBusDataReg: ValidIO[RingBusData] = RegInit(0.U.asTypeOf(Valid(new RingBusData(param))))
    val readBusIndexMatch = readBusPort.enq.bits.target === laneIndex
    readBusMatch := readBusIndexMatch && readBusPort.enq.valid
    // 暂时优先级策略是环上的优先
    readBusPort.enq.ready := true.B
    readBusDataReg.valid := false.B

    when(readBusPort.enq.valid) {
      when(readBusIndexMatch) {
        // todo
      }.otherwise {
        readBusDataReg.valid := true.B
        readBusDataReg.bits := readBusPort.enq.bits
      }
    }

    // 试图进环
    readBusPort.deq.valid := readBusDataReg.valid || tryToSendHead || tryToSendTail
    readBusPort.deq.bits := Mux(readBusDataReg.valid, readBusDataReg.bits, sendReadData)
    sendReady := !readBusDataReg.valid
  }

  // 处理写环
  {
    val writeBusDataReg: ValidIO[RingBusData] = RegInit(0.U.asTypeOf(Valid(new RingBusData(param))))
    // 策略依然是环上的优先,如果queue满了继续转
    val writeBusIndexMatch = readBusPort.enq.bits.target === laneIndex && crossWriteQueue.io.enq.ready
    writeBusPort.enq.ready := true.B
    writeBusDataReg.valid := false.B
    crossWriteQueue.io.enq.bits := writeBusPort.enq.bits
    crossWriteQueue.io.enq.valid := false.B

    when(writeBusPort.enq.valid) {
      when(writeBusIndexMatch) {

      }.otherwise {
        writeBusDataReg.valid := true.B
        writeBusDataReg.bits := readBusPort.enq.bits
      }
    }

    // 进写环
    writeBusPort.deq.valid := writeBusDataReg.valid || sendWriteValid.asUInt.orR
    writeBusPort.deq.bits := Mux(
      sendWriteValid.head,
      sendWriteData.head,
      Mux(sendWriteValid.last, sendWriteData.last, writeBusDataReg.bits)
    )
    sendWriteReady := !writeBusDataReg.valid
  }

}
