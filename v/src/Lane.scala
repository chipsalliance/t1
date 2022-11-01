package v

import chisel3._
import chisel3.util._

case class LaneParameters(ELEN: Int = 32, VLEN: Int = 1024, lane: Int = 8, chainingSize: Int = 4) {
  val instIndexSize:  Int = log2Ceil(chainingSize) + 1
  val VLMax:          Int = VLEN
  val VLMaxWidth:     Int = log2Ceil(VLMax)
  // vlmul = 8时会有多少组,其中每一组长度是 ELEN
  val groupSize:      Int = VLEN * 8 / lane / ELEN
  val controlNum:     Int = 4
  val HLEN:           Int = ELEN / 2
  val executeUnitNum: Int = 6
  val laneIndexBits:  Int = log2Ceil(lane)
  val writeQueueSize: Int = 8
  val elenBits:       Int = log2Ceil(ELEN)
  val groupSizeBits:  Int = log2Ceil(groupSize)
  // 每一组会会包含32bit,在 vSew < 2时,需要有一个控制寄存器来管理,寄存器的长度
  val groupControlSize: Int = ELEN / 8
  // 单个寄存器在每个lane里能分成多少组, 每次只访问32bit
  val singleGroupSize: Int = VLEN / ELEN / lane
  val offsetBits:Int = log2Ceil(singleGroupSize)

  // mask 分组
  val maskGroupWidth: Int = ELEN
  val maskGroupSize: Int = VLEN / ELEN
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  def vrfParam:         VRFParam = VRFParam(VLEN, lane, ELEN)
  def datePathParam:    DataPathParam = DataPathParam(ELEN)
  def shifterParameter: LaneShifterParameter = LaneShifterParameter(ELEN, elenBits)
  def mulParam:         LaneMulParam = LaneMulParam(ELEN)
  def indexParam:       LaneIndexCalculatorParameter = LaneIndexCalculatorParameter(groupSizeBits, laneIndexBits)
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
  val vid:      Bool = Bool()
  val vSrc:     Bool = Bool()

  /** type of vs1 */
  val vType:     Bool = Bool()
  val xType:     Bool = Bool()
  val iType:     Bool = Bool()
  val pointless: Bool = Bool()
  val uop:       UInt = UInt(3.W)
}

class ExtendInstructionType extends Bundle {
  val vExtend:  Bool = Bool()
  val mv:       Bool = Bool()
  val ffo:      Bool = Bool()
  val popCount: Bool = Bool()
  val vid:      Bool = Bool()
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
  val readFromScalar: UInt = UInt(param.ELEN.W)

  val ls: Bool = Bool()
  val sp: Bool = Bool()

  def initState: InstGroupState = {
    val res:                InstGroupState = Wire(new InstGroupState(param))
    val decodeResFormat:    InstructionDecodeResult = decodeResult.asTypeOf(new InstructionDecodeResult)
    val decodeResFormatExt: ExtendInstructionDecodeResult = decodeResult.asTypeOf(new ExtendInstructionDecodeResult)
    res.sRead1 := !decodeResFormat.vType
    res.sRead2 := false.B
    res.sReadVD := !(decodeResFormat.firstWiden || (decodeResFormat.mulUnit && decodeResFormat.uop(1, 0).orR))
    res.wRead1 := !decodeResFormat.firstWiden
    res.wRead2 := !decodeResFormat.firstWiden
    res.wScheduler := !sp
    res.sExecute := false.B
    //todo: red
    res.wExecuteRes := sp
    res.sWrite := decodeResFormat.otherUnit && decodeResFormatExt.targetRD
    res.sCrossWrite0 := !decodeResFormat.Widen
    res.sCrossWrite1 := !decodeResFormat.Widen
    res.sSendResult0 := !decodeResFormat.firstWiden
    res.sSendResult1 := !decodeResFormat.firstWiden
    res
  }

  def instType: UInt = {
    val decodeResFormat: InstructionDecodeResult = decodeResult.asTypeOf(new InstructionDecodeResult)
    VecInit(
      Seq(
        decodeResFormat.logicUnit && !decodeResFormat.otherUnit,
        decodeResFormat.adderUnit && !decodeResFormat.otherUnit,
        decodeResFormat.shiftUnit && !decodeResFormat.otherUnit,
        decodeResFormat.mulUnit && !decodeResFormat.otherUnit,
        decodeResFormat.divUnit && !decodeResFormat.otherUnit,
        decodeResFormat.otherUnit
      )
    ).asUInt
  }
}

class InstGroupState(param: LaneParameters) extends Bundle {
  val sRead1:     Bool = Bool()
  val sRead2:     Bool = Bool()
  val sReadVD:    Bool = Bool()
  val wRead1:     Bool = Bool()
  val wRead2:     Bool = Bool()
  val wScheduler: Bool = Bool()
  val sExecute:   Bool = Bool()
  // 发送写的
  val sCrossWrite0: Bool = Bool()
  val sCrossWrite1: Bool = Bool()
  // 发送读的
  val sSendResult0: Bool = Bool()
  val sSendResult1: Bool = Bool()
  val wExecuteRes:  Bool = Bool()
  val sWrite:       Bool = Bool()
}

class InstControlRecord(param: LaneParameters) extends Bundle {
  val originalInformation: LaneReq = new LaneReq(param)
  val state:               InstGroupState = new InstGroupState(param)
  val initState:           InstGroupState = new InstGroupState(param)
  val counter:             UInt = UInt(param.groupSizeBits.W)
  val schedulerComplete:   Bool = Bool()
  // 这次执行从32bit的哪个位置开始执行
  val executeIndex: UInt = UInt(2.W)
  /** 应对vl很小的时候,不会用到这条lane */
  val instCompleted: Bool = Bool()
}

class LaneCsrInterface(vlWidth: Int) extends Bundle {
  val vl:     UInt = UInt(vlWidth.W)
  val vStart: UInt = UInt(vlWidth.W)
  val vlmul:  UInt = UInt(3.W)
  val vSew:   UInt = UInt(2.W)

  /** Rounding mode register */
  val vxrm: UInt = UInt(2.W)

  /** tail agnostic */
  val vta: Bool = Bool()

  /** mask agnostic */
  val vma: Bool = Bool()

  /** 如果不忽视异常, fault only first 类型的指令需要等第一个回应 */
  val ignoreException: Bool = Bool()
}

class LaneDataResponse(param: LaneParameters) extends Bundle {
  val data:      UInt = UInt(param.ELEN.W)
  val toLSU:     Bool = Bool()
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val last:      Bool = Bool()
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

class SchedulerFeedback(param: LaneParameters) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val complete:  Bool = Bool()
}

/**
  * ring & inst control & vrf & vfu
  */
class Lane(param: LaneParameters) extends Module {
  val laneReq:         DecoupledIO[LaneReq] = IO(Flipped(Decoupled(new LaneReq(param))))
  val csrInterface:    LaneCsrInterface = IO(Input(new LaneCsrInterface(param.VLMaxWidth)))
  val dataToScheduler: ValidIO[LaneDataResponse] = IO(Valid(new LaneDataResponse(param)))
  val laneIndex:       UInt = IO(Input(UInt(param.laneIndexBits.W)))
  val readBusPort:     RingPort = IO(new RingPort(param))
  val writeBusPort:    RingPort = IO(new RingPort(param))
  val feedback:        ValidIO[SchedulerFeedback] = IO(Flipped(Valid(new SchedulerFeedback(param))))
  val readDataPort:    DecoupledIO[VRFReadRequest] = IO(Flipped(Decoupled(new VRFReadRequest(param.vrfParam))))
  val readResult:      UInt = IO(Output(UInt(param.ELEN.W)))
  val vrfWritePort:    ValidIO[VRFWriteRequest] = IO(Flipped(Valid(new VRFWriteRequest(param.vrfParam))))
  /** 本来结束的通知放在[[dataToScheduler]],但是存在有多指令同时结束的情况,所以单独给出来 */
  val endNotice: UInt = IO(Output(UInt(param.controlNum.W)))

  val vrf: VRF = Module(new VRF(param.vrfParam))
  // reg
  val controlValid: Vec[Bool] = RegInit(VecInit(Seq.fill(param.controlNum)(false.B)))
  // read from vs1
  val source1: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // read from vs2
  val source2: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // read from vd
  val source3: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // execute result
  val result: Vec[UInt] = RegInit(VecInit(Seq.fill(param.controlNum)(0.U(param.ELEN.W))))
  // 额外两个给 lsu 和
  val rfWriteVec: Vec[ValidIO[VRFWriteRequest]] = Wire(
    Vec(param.controlNum + 1, Valid(new VRFWriteRequest(param.vrfParam)))
  )
  rfWriteVec(4) <> vrfWritePort
  val rfWriteFire: UInt = Wire(UInt((param.controlNum + 2).W))
  // 跨lane操作的寄存器
  // 从rf里面读出来的， 下一个周期试图上环
  val crossReadHeadTX: UInt = RegInit(0.U(param.HLEN.W))
  val crossReadTailTX: UInt = RegInit(0.U(param.HLEN.W))
  // 从环过来的， 两个都好会拼成source2
  val crossReadHeadRX: UInt = RegInit(0.U(param.HLEN.W))
  val crossReadTailRX: UInt = RegInit(0.U(param.HLEN.W))
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
  // 读的环index与这个lane匹配上了, 会出环
  val readBusDeq: ValidIO[RingBusData] = Wire(Valid(new RingBusData(param: LaneParameters)))

  // 以6个执行单元为视角的控制信号
  val executeEnqValid:  Vec[UInt] = Wire(Vec(param.controlNum, UInt(param.executeUnitNum.W)))
  val executeEnqFire:   UInt = Wire(UInt(param.executeUnitNum.W))
  val executeDeqFire:   UInt = Wire(UInt(param.executeUnitNum.W))
  val executeDeqData:   Vec[UInt] = Wire(Vec(param.executeUnitNum, UInt(param.ELEN.W)))
  val instTypeVec:      Vec[UInt] = Wire(Vec(param.controlNum, UInt(param.executeUnitNum.W)))
  val instWillComplete: Vec[Bool] = Wire(Vec(param.controlNum, Bool()))
  val maskReqValid:     Vec[Bool] = Wire(Vec(param.controlNum, Bool()))
  // 往执行单元的请求
  val logicRequests: Vec[LaneLogicRequest] = Wire(Vec(param.controlNum, new LaneLogicRequest(param.datePathParam)))
  val adderRequests: Vec[LaneAdderReq] = Wire(Vec(param.controlNum, new LaneAdderReq(param.datePathParam)))
  val shiftRequests: Vec[LaneShifterReq] = Wire(Vec(param.controlNum, new LaneShifterReq(param.shifterParameter)))
  val mulRequests:   Vec[LaneMulReq] = Wire(Vec(param.controlNum, new LaneMulReq(param.mulParam)))
  val divRequests:   Vec[LaneDivRequest] = Wire(Vec(param.controlNum, new LaneDivRequest(param.datePathParam)))
  val otherRequests: Vec[OtherUnitReq] = Wire(Vec(param.controlNum, Output(new OtherUnitReq(param))))
  val maskRequests:  Vec[LaneDataResponse] = Wire(Vec(param.controlNum, Output(new LaneDataResponse(param))))
  val endNoticeVec: Vec[UInt] = Wire(Vec(param.controlNum, UInt(param.controlNum.W)))

  // 作为最老的坑的控制信号
  val sendReady:      Bool = Wire(Bool())
  val sendWriteReady: Bool = Wire(Bool())
  val sendReadData:   ValidIO[RingBusData] = Wire(Valid(new RingBusData(param)))
  val sendWriteData:  ValidIO[RingBusData] = Wire(Valid(new RingBusData(param)))

  val vSewOrR: Bool = csrInterface.vSew.orR
  /**
    * 根据 vSew 算 mask
    * 00 -> 0001
    * 01 -> 0011
    * 10 -> 1111
    */
  val dataMask = Seq(true.B, vSewOrR, csrInterface.vSew(1), csrInterface.vSew(1))
  /** 每次 result 需要往里面移位 */
  val resultOffset: UInt = csrInterface.vSew(1) ## csrInterface.vSew(0) ## !vSewOrR ## 0.U(3.W)
  /** 符号的mask,外面好像不用处理符号 */
  val signMask = Seq(!vSewOrR, csrInterface.vSew(0))
  /** 不同 vSew 结束时候的index
    * 00 -> 11
    * 01 -> 01
    * 10 -> 00
    * */
  val endIndex: UInt = !vSewOrR ## !csrInterface.vSew(1)

  // 跨lane写rf需要一个queue
  val crossWriteQueue: Queue[VRFWriteRequest] = Module(
    new Queue(new VRFWriteRequest(param.vrfParam), param.writeQueueSize)
  )

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
      controlActive(index) := controlValid(
        index
      ) && controlValid.head && ((index == 0).B || !(needCrossRead || needCrossWrite))
      // todo: 能不能移动还需要纠结纠结
      controlCanShift(index) := !record.state.sExecute
      // vs1 read
      vrfReadWire(index)(0).valid := !record.state.sRead1 && controlActive(index)
      vrfReadWire(index)(0).bits.offset := record.counter(param.offsetBits - 1, 0)
      // todo: 在 vlmul > 0 的时候需要做的是cat而不是+,因为寄存器是对齐的
      vrfReadWire(index)(0).bits.vs := record.originalInformation.vs1 + record.counter(param.groupSizeBits - 1, param.offsetBits)
      vrfReadWire(index)(0).bits.instIndex := record.originalInformation.instIndex
      // Mux(decodeResFormat.eew16, 1.U, csrInterface.vSew)

      // vs2 read
      vrfReadWire(index)(1).valid := !record.state.sRead2 && controlActive(index)
      vrfReadWire(index)(1).bits.offset := Mux(needCrossRead, record.counter(param.offsetBits - 2, 0) ## false.B, record.counter(param.offsetBits - 1, 0))
      vrfReadWire(index)(1).bits.vs := record.originalInformation.vs2 + Mux(needCrossRead, record.counter(param.groupSizeBits - 1, param.offsetBits - 1) ## false.B, record.counter(param.groupSizeBits - 1, param.offsetBits - 1))
      vrfReadWire(index)(1).bits.instIndex := record.originalInformation.instIndex

      // vd read
      vrfReadWire(index)(2).valid := !record.state.sReadVD && controlActive(index)
      vrfReadWire(index)(2).bits.offset := Mux(needCrossRead, record.counter(param.offsetBits - 2, 0) ## true.B, record.counter(param.offsetBits - 1, 0))
      vrfReadWire(index)(2).bits.vs := record.originalInformation.vd + Mux(needCrossRead, record.counter(param.groupSizeBits - 1, param.offsetBits - 1) ## true.B, record.counter(param.groupSizeBits - 1, param.offsetBits - 1))
      vrfReadWire(index)(2).bits.instIndex := record.originalInformation.instIndex

      val readFinish =
        record.state.sReadVD && record.state.sRead1 && record.state.sRead2 && record.state.wRead1 && record.state.wRead2

      // 处理读出来的结果
      when(vrfReadWire(index)(0).fire) {
        record.state.sRead1 := true.B
        // todo: Mux
        source1(index) := vrfReadResult(index)(0)
      }
      when(vrfReadWire(index)(1).fire) {
        record.state.sRead2 := true.B
        source2(index) := vrfReadResult(index)(1)
      }

      when(vrfReadWire(index)(2).fire) {
        record.state.sReadVD := true.B
        source3(index) := vrfReadResult(index)(2)
      }
      // 处理上环的数据
      if (index == 0) {
        val tryToSendHead = record.state.sRead2 && !record.state.sSendResult0
        val tryToSendTail = record.state.sReadVD && !record.state.sSendResult1
        sendReadData.bits.target := tryToSendTail ## laneIndex(param.laneIndexBits - 1, 1)
        sendReadData.bits.tail := laneIndex(0)
        sendReadData.bits.instIndex := record.originalInformation.instIndex
        sendReadData.bits.data := Mux(tryToSendHead, crossReadHeadTX(0), crossReadTailTX(0))
        sendReadData.valid := tryToSendHead || tryToSendTail

        // 跨lane的写
        val sendWriteHead = record.state.sExecute && !record.state.sCrossWrite0
        val sendWriteTail = record.state.sExecute && !record.state.sCrossWrite1
        sendWriteData.bits.target := laneIndex(param.laneIndexBits - 2, 0) ## sendWriteTail
        sendWriteData.bits.tail := laneIndex(param.laneIndexBits - 1)
        sendWriteData.bits.instIndex := record.originalInformation.instIndex
        sendWriteData.bits.data := Mux(tryToSendHead, result(index), result(index) >> (csrInterface.vSew ## 0.U(3.W)))
        sendWriteData.valid := sendWriteHead || sendWriteTail

        // 跨lane读写的数据接收
        when(readBusDeq.valid) {
          assert(readBusDeq.bits.instIndex === record.originalInformation.instIndex)
          when(readBusDeq.bits.tail) {
            record.state.wRead2 := true.B
            crossReadTailRX := readBusDeq.bits.data
          }.otherwise {
            record.state.wRead1 := true.B
            crossReadHeadRX := readBusDeq.bits.data
          }
        }

        // 读环发送的状态变化
        // todo: 处理发给自己的, 可以在使用的时候直接用读的寄存器, init state的时候自己纠正回来
        when(sendReady) {
          record.state.sSendResult0 := true.B
          when(record.state.sSendResult0) {
            record.state.sSendResult1 := true.B
          }
        }
        // 写环发送的状态变化
        when(sendWriteReady) {
          record.state.sCrossWrite0 := true.B
          when(record.state.sCrossWrite0) {
            record.state.sCrossWrite1 := true.B
          }
        }

        // 跨lane的读记录
        when(vrfReadWire(index)(1).fire && decodeResFormat.firstWiden) {
          crossReadHeadTX := vrfReadResult(index)(1)
        }
        when(vrfReadWire(index)(2).fire && decodeResFormat.firstWiden) {
          crossReadTailTX := vrfReadResult(index)(2)
        }
      }
      // 发起执行单元的请求
      /** 计算原数据需要需要的偏移: (executeIndex << vSew) * 8 */
      val dataOffset: UInt = (record.executeIndex << csrInterface.vSew) ## 0.U(3.W)
      /** 虽然没有计算完一组,但是这一组剩余的都被mask去掉了 todo */
      val maskFilterEnd = false.B
      /** 需要一个除vl导致的end来决定下一个的 element index 是什么 */
      val dataDepletion = record.executeIndex === endIndex || maskFilterEnd
      /** 这一组计算全完成了 */
      val groupEnd = dataDepletion || instWillComplete(index)
      // 处理操作数
      /**
        * src1： src1有 IXV 三种类型,只有V类型的需要移位
        * */
      val finalSource1 = (source1(index) >> Mux(decodeResFormat.vType, dataOffset, 0.U)).asUInt
      /** source2 一定是V类型的 */
      val finalSource2 = (source2(index) >> dataOffset).asUInt
      /** source3 有两种：adc & ma, c等处理mask的时候再处理 */
      val finalSource3 = Mux(decodeResFormat.adderUnit, 0.U, (source3(index) >> dataOffset).asUInt)
      // 假如这个单元执行的是logic的类型的,请求应该是什么样子的
      val logicRequest = Wire(new LaneLogicRequest(param.datePathParam))
      logicRequest.src.head := finalSource2
      logicRequest.src.last := finalSource1
      logicRequest.opcode := decodeResFormat.uop
      val nextGroupCount = record.counter + 1.U
      val nextExecuteIndex: UInt = record.executeIndex + 1.U
      /** 因为寄存器的摆放在在32bit上是连续的,所以在计算element index 的时候需要根据sew选
        * sew = 0 -> record.counter ## laneIndex ## record.executeIndex
        * sew = 1 -> record.counter ## laneIndex ## record.executeIndex(0)
        * sew = 2 -> record.counter ## laneIndex
        * * */
      val nextElementIndex = Mux(dataDepletion, nextGroupCount, record.counter) ## laneIndex ## Mux(dataDepletion, 0.U(2.W), record.executeIndex)
      instWillComplete(index) := (nextElementIndex >> csrInterface.vSew).asUInt >= csrInterface.vl
      // 在手动做Mux1H
      logicRequests(index) := maskAnd(controlValid(index) && decodeResFormat.logicUnit && !decodeResFormat.otherUnit, logicRequest)

      // adder 的
      val adderRequest = Wire(new LaneAdderReq(param.datePathParam))
      adderRequest.src := VecInit(Seq(finalSource1, finalSource2, finalSource3))
      adderRequest.opcode := decodeResFormat.uop
      adderRequest.reverse := decodeResFormat.reverse
      adderRequest.average := decodeResFormat.average
      adderRequests(index) := maskAnd(controlValid(index) && decodeResFormat.adderUnit && !decodeResFormat.otherUnit, adderRequest)

      // shift 的
      val shiftRequest = Wire(new LaneShifterReq(param.shifterParameter))
      shiftRequest.src := finalSource2
      shiftRequest.shifterSize := Mux1H(
        UIntToOH(csrInterface.vSew)(2, 1),
        Seq(false.B ## finalSource1(3), finalSource1(4, 3))
      ) ## finalSource1(2, 0)
      shiftRequest.opcode := decodeResFormat.uop
      shiftRequests(index) := maskAnd(controlValid(index) && decodeResFormat.shiftUnit && !decodeResFormat.otherUnit, shiftRequest)

      // mul
      val mulRequest: LaneMulReq = Wire(new LaneMulReq(param.mulParam))
      mulRequest.src := VecInit(Seq(finalSource1, finalSource2, finalSource3))
      mulRequest.opcode := decodeResFormat.uop
      mulRequests(index) := maskAnd(controlValid(index) && decodeResFormat.mulUnit && !decodeResFormat.otherUnit, mulRequest)

      // div
      val divRequest = Wire(new LaneDivRequest(param.datePathParam))
      divRequest.src := VecInit(Seq(finalSource1, finalSource2))
      divRequest.rem := decodeResFormat.uop(0)
      divRequest.sign := decodeResFormat.unSigned0
      divRequests(index) := maskAnd(controlValid(index) && decodeResFormat.divUnit && !decodeResFormat.otherUnit, divRequest)

      // other
      val otherRequest: OtherUnitReq = Wire(Output(new OtherUnitReq(param)))
      otherRequest.src := VecInit(Seq(finalSource1, finalSource2))
      otherRequest.opcode := decodeResFormat.uop(2, 0)
      otherRequest.imm := record.originalInformation.vs1
      otherRequest.extendType.valid := decodeResFormat.uop(3)
      otherRequest.extendType.bits.elements.foreach { case (s, d) => d := decodeResFormatExt.elements(s) }
      otherRequest.mask := DontCare // todo
      otherRequest.laneIndex := laneIndex
      otherRequest.groupIndex := record.counter
      otherRequest.sign := !decodeResFormat.unSigned0
      otherRequests(index) := maskAnd(controlValid(index) && decodeResFormat.otherUnit, otherRequest)

      // 往scheduler的执行任务compress viota
      val maskRequest: LaneDataResponse = Wire(Output(new LaneDataResponse(param)))

      // viota & compress & ls 需要给外边数据
      val maskType: Bool = (record.originalInformation.sp || record.originalInformation.ls) && controlActive(index)
      val maskValid = maskType && record.state.sRead2 && !record.state.sExecute
      // 往外边发的是原始的数据
      maskRequest.data := source2(index)
      maskRequest.toLSU := record.originalInformation.ls
      maskRequest.instIndex := record.originalInformation.instIndex
      maskRequest.last := instWillComplete(index)
      maskRequests(index) := maskAnd(controlValid(index) && maskValid, maskRequest)
      maskReqValid(index) := maskValid

      when(feedback.valid && feedback.bits.instIndex === record.originalInformation.instIndex) {
        record.state.wScheduler := true.B
      }
      instTypeVec(index) := record.originalInformation.instType
      executeEnqValid(index) := maskAnd(readFinish, instTypeVec(index))
      when((instTypeVec(index) & executeEnqFire).orR || maskValid) {
        when(groupEnd || maskValid) {
          record.state.sExecute := true.B
        }.otherwise {
          record.executeIndex := nextExecuteIndex
        }
      }

      // todo: 暂时先这样把,处理mask的时候需要修
      val resultUpdate: UInt = ((Mux1H(instTypeVec(index), executeDeqData) ## result(index)) >> resultOffset).asUInt(param.ELEN - 1, 0)
      when((instTypeVec(index) & executeDeqFire).orR) {
        when(groupEnd) {
          record.state.wExecuteRes := true.B
        }
        result(index) := resultUpdate
      }
      // 写rf
      rfWriteVec(index).valid := record.state.wExecuteRes && !record.state.sWrite && controlActive(index)
      rfWriteVec(index).bits.vd := record.originalInformation.vd
      rfWriteVec(index).bits.offset := record.counter
      rfWriteVec(index).bits.data := result(index)
      rfWriteVec(index).bits.last := instWillComplete(index)
      rfWriteVec(index).bits.instIndex := record.originalInformation.instIndex
      val notLastWrite = !instWillComplete(index)
      // 判断这一个lane是否在body与tail的分界线上,只有分界线上的才需要特别计算mask
      val dividingLine: Bool = (csrInterface.vl << csrInterface.vSew).asUInt(4, 2) === laneIndex
      val useOriginalMask: Bool = notLastWrite || !dividingLine
      /** sew = 2 -> mask = 1111
        * sew = 1 -> mask = 1111 或者由vl决定
        * sew = 0 -> mask = 1111 或者由vl决定
        * todo: mask decode?
        */
      val writeMask: UInt = Mux1H(
        UIntToOH(csrInterface.vSew)(2, 0),
        Seq(
          /** vSew = 0; vl(1,0) -> mask
            * 00 -> ????,0不会在分界限上
            * 01 -> 0001
            * 10 -> 0011
            * 11 -> 0111
            * */
          useOriginalMask ## (csrInterface.vl(1, 0).andR || useOriginalMask) ## (csrInterface.vl(1) || useOriginalMask) ## true.B,
          Mux(csrInterface.vl(0) || useOriginalMask, 3.U(2.W), 0.U(2.W)) ## 3.U(2.W),
          15.U(4.W)
        )
      )
      rfWriteVec(index).bits.mask := writeMask
      when(rfWriteFire(index)) {
        record.state.sWrite := true.B
      }
      endNoticeVec(index) := 0.U(param.controlNum.W)
      when(record.state.asUInt.andR || record.instCompleted) {
        when(instWillComplete(index) || record.instCompleted) {
          controlValid(index) := false.B
          when(controlValid(index)) {
            endNoticeVec(index) := UIntToOH(record.originalInformation.instIndex(param.instIndexSize - 2, 0))
          }
        }.otherwise {
          record.state := record.initState
          record.counter := nextGroupCount
          record.executeIndex := 0.U
        }
      }
      when(feedback.bits.complete && feedback.bits.instIndex === record.originalInformation.instIndex) {
        // 例如:别的lane找到了第一个1
        record.schedulerComplete := true.B
        when(record.originalInformation.sp) {
          controlValid(index) := false.B
        }
      }
  }

  // 处理读环的
  {
    val readBusDataReg: ValidIO[RingBusData] = RegInit(0.U.asTypeOf(Valid(new RingBusData(param))))
    val readBusIndexMatch = readBusPort.enq.bits.target === laneIndex
    readBusDeq.valid := readBusIndexMatch && readBusPort.enq.valid
    readBusDeq.bits := readBusPort.enq.bits
    // 暂时优先级策略是环上的优先
    readBusPort.enq.ready := true.B
    readBusDataReg.valid := false.B

    when(readBusPort.enq.valid) {
      when(!readBusIndexMatch) {
        readBusDataReg.valid := true.B
        readBusDataReg.bits := readBusPort.enq.bits
      }
    }

    // 试图进环
    readBusPort.deq.valid := readBusDataReg.valid || sendReadData.valid
    readBusPort.deq.bits := Mux(readBusDataReg.valid, readBusDataReg.bits, sendReadData.bits)
    sendReady := !readBusDataReg.valid
  }

  // 处理写环
  {
    val writeBusDataReg: ValidIO[RingBusData] = RegInit(0.U.asTypeOf(Valid(new RingBusData(param))))
    // 策略依然是环上的优先,如果queue满了继续转
    val writeBusIndexMatch = writeBusPort.enq.bits.target === laneIndex && crossWriteQueue.io.enq.ready
    writeBusPort.enq.ready := true.B
    writeBusDataReg.valid := false.B
    crossWriteQueue.io.enq.bits.vd := control.head.originalInformation.vd
    crossWriteQueue.io.enq.bits.offset := control.head.originalInformation.instIndex ## writeBusPort.enq.bits.tail
    crossWriteQueue.io.enq.bits.data := writeBusPort.enq.bits.data
    crossWriteQueue.io.enq.bits.last := instWillComplete.head && writeBusPort.enq.bits.tail
    crossWriteQueue.io.enq.bits.instIndex := control.head.originalInformation.instIndex
    crossWriteQueue.io.enq.bits.mask := 15.U//todo
    //writeBusPort.enq.bits
    crossWriteQueue.io.enq.valid := false.B

    when(writeBusPort.enq.valid) {
      when(writeBusIndexMatch) {
        crossWriteQueue.io.enq.valid := true.B
      }.otherwise {
        writeBusDataReg.valid := true.B
        writeBusDataReg.bits := writeBusPort.enq.bits
      }
    }

    // 进写环
    writeBusPort.deq.valid := writeBusDataReg.valid || sendWriteData.valid
    writeBusPort.deq.bits := Mux(writeBusDataReg.valid, writeBusDataReg.bits, sendWriteData.bits)
    sendWriteReady := !writeBusDataReg.valid
  }

  // 执行单元
  {
    val logicUnit: LaneLogic = Module(new LaneLogic(param.datePathParam))
    val adder:     LaneAdder = Module(new LaneAdder(param.datePathParam))
    val shifter:   LaneShifter = Module(new LaneShifter(param.shifterParameter))
    val mul:       LaneMul = Module(new LaneMul(param.mulParam))
    val div:       LaneDiv = Module(new LaneDiv(param.datePathParam))
    val otherUnit: OtherUnit = Module(new OtherUnit(param))

    // 连接执行单元的请求
    logicUnit.req := VecInit(logicRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(new LaneLogicRequest(param.datePathParam))
    adder.req := VecInit(adderRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(new LaneAdderReq(param.datePathParam))
    shifter.req := VecInit(shiftRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(new LaneShifterReq(param.shifterParameter))
    mul.req := VecInit(mulRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(new LaneMulReq(param.mulParam))
    div.req.bits := VecInit(divRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(new LaneDivRequest(param.datePathParam))
    otherUnit.req := VecInit(otherRequests.map(_.asUInt)).reduce(_ | _).asTypeOf(Output(new OtherUnitReq(param)))
    dataToScheduler.bits := VecInit(maskRequests.map(_.asUInt))
      .reduce(_ | _)
      .asTypeOf(Output(new LaneDataResponse(param)))
    dataToScheduler.valid := maskReqValid.asUInt.orR
    // 执行单元的其他连接
    adder.csr.vSew := csrInterface.vSew
    adder.csr.vxrm := csrInterface.vxrm
    otherUnit.csr.vSew := csrInterface.vSew
    otherUnit.csr.vxrm := csrInterface.vxrm
    div.mask := DontCare
    div.vSew := csrInterface.vSew

    // 连接执行结果
    executeDeqData := VecInit(
      Seq(logicUnit.resp, adder.resp, shifter.resp, mul.resp, div.resp.bits, otherUnit.resp.data)
    )
    executeDeqFire := executeEnqFire(5) ## div.resp.valid ## executeEnqFire(3, 0)
    // 执行单元入口握手
    val tryToUseExecuteUnit = VecInit(executeEnqValid.map(_.asBools).transpose.map(VecInit(_).asUInt.orR)).asUInt
    executeEnqFire := tryToUseExecuteUnit & (true.B ## div.req.ready ## 15.U(4.W))
    div.req.valid := tryToUseExecuteUnit(4)
  }

  // 处理 rf
  {
    // 连接读口
    val readArbiter = Module(new Arbiter(new VRFReadRequest(param.vrfParam), 8))
    // 暂时把lsu的读放在了最低优先级,有问题再改
    (vrfReadWire(1).last +: (vrfReadWire(2) ++ vrfReadWire(3)) :+ readDataPort).zip(readArbiter.io.in).foreach {
      case (source, sink) =>
        sink <> source
    }
    (vrfReadWire.head ++ vrfReadWire(1).init :+ readArbiter.io.out).zip(vrf.read).foreach {
      case (source, sink) =>
        sink <> source
    }

    // 读的结果
    vrfReadResult.foreach(a => a.foreach(_ := vrf.readResult.last))
    (vrfReadResult.head ++ vrfReadResult(1).init).zip(vrf.readResult.init).foreach {
      case (sink, source) =>
        sink := source
    }
    readResult := vrf.readResult.last

    // 写 rf
    val normalWrite = VecInit(rfWriteVec.map(_.valid)).asUInt.orR
    val writeSelect = !normalWrite ## ffo(VecInit(rfWriteVec.map(_.valid)).asUInt)
    val writeEnqBits = Mux1H(writeSelect, rfWriteVec.map(_.bits) :+ crossWriteQueue.io.deq.bits)
    vrf.write.valid := normalWrite || crossWriteQueue.io.deq.valid
    vrf.write.bits := writeEnqBits
    crossWriteQueue.io.deq.ready := !normalWrite && vrf.write.ready
    rfWriteFire := Mux(vrf.write.ready, writeSelect, 0.U)
  }

  // 控制逻辑的移动
  val entranceControl: InstControlRecord = Wire(new InstControlRecord(param))
  val entranceFormat:  InstructionDecodeResult = laneReq.bits.decodeResult.asTypeOf(new InstructionDecodeResult)
  entranceControl.originalInformation := laneReq.bits
  entranceControl.state := laneReq.bits.initState
  entranceControl.initState := laneReq.bits.initState
  entranceControl.executeIndex := 0.U
  entranceControl.schedulerComplete := false.B
  entranceControl.instCompleted := ((laneIndex ## 0.U(2.W)) >> csrInterface.vSew).asUInt >= csrInterface.vl
  // todo: vStart(2,0) > lane index
  entranceControl.counter := (csrInterface.vStart >> 3).asUInt
  // todo: spec 10.1: imm 默认是 sign-extend,但是有特殊情况
  val vs1entrance: UInt =
    Mux(entranceFormat.vType, 0.U, Mux(entranceFormat.xType, laneReq.bits.readFromScalar,
      VecInit(Seq.fill(param.ELEN - 5)(laneReq.bits.vs1(4))).asUInt ## laneReq.bits.vs1))
  val entranceInstType: UInt = laneReq.bits.instType
  // todo: 修改v0的和使用v0作为mask的指令需要产生冲突
  val typeReady: Bool = VecInit(
    instTypeVec.zip(controlValid).map { case (t, v) => (t =/= entranceInstType) || !v }
  ).asUInt.andR
  val validRegulate: Bool = laneReq.valid && typeReady
  laneReq.ready := !controlValid.head && typeReady
  vrf.instWriteReport.valid := false.B
  when(!controlValid.head && (controlValid.asUInt.orR || validRegulate)) {
    controlValid := VecInit(controlValid.tail :+ validRegulate)
    source1 := VecInit(source1.tail :+ vs1entrance)
    control := VecInit(control.tail :+ entranceControl)
    vrf.instWriteReport.valid := true.B
  }
  vrf.flush := DontCare
  vrf.instWriteReport.bits.instIndex := laneReq.bits.instIndex
  vrf.instWriteReport.bits.offset := 0.U//todo
  vrf.instWriteReport.bits.vd := laneReq.bits.vd
  endNotice := endNoticeVec.reduce(_ | _)
}
