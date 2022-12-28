package v

import chisel3._
import chisel3.util.{Decoupled, DecoupledIO, Valid, ValidIO}

class VReq(param: VParam) extends Bundle {
  val inst:     UInt = UInt(32.W)
  val src1Data: UInt = UInt(param.XLEN.W)
  val src2Data: UInt = UInt(param.XLEN.W)
}

class VResp(param: VParam) extends Bundle {
  // todo: vector解出来是否需要写rd？
  val data: UInt = UInt(param.XLEN.W)
}

class InstRecord(param: VParam) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val vrfWrite:  Bool = Bool()

  /** Whether operation is widen */
  val w: Bool = Bool()

  /** Whether operation is narrowing */
  val n: Bool = Bool()
  // load | store
  val ls: Bool = Bool()
}

class InstState extends Bundle {
  val wLast:    Bool = Bool()
  val idle:     Bool = Bool()
  val sExecute: Bool = Bool()
  val sCommit:  Bool = Bool()
}

class specialInstructionType extends Bundle {
  val red:      Bool = Bool()
  val compress: Bool = Bool()
  val viota:    Bool = Bool()
  // 其他的需要对齐的指令
  val other: Bool = Bool()
}

class InstControl(param: VParam) extends Bundle {
  val record: InstRecord = new InstRecord(param)
  val state:  InstState = new InstState
  val endTag: Vec[Bool] = Vec(param.lane + 1, Bool())
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

  /** mask type ? */
  val mask: Bool = Bool()

  val ls:  Bool = Bool()
  val st:  Bool = Bool()
  val sp:  Bool = Bool()
  val seg: UInt = UInt(3.W)
  val eew: UInt = UInt(2.W)
  def ma:  Bool = decodeResult(21) && decodeResult(1, 0).orR

  def initState: InstGroupState = {
    val res:                InstGroupState = Wire(new InstGroupState(param))
    val decodeResFormat:    InstructionDecodeResult = decodeResult.asTypeOf(new InstructionDecodeResult)
    val decodeResFormatExt: ExtendInstructionDecodeResult = decodeResult.asTypeOf(new ExtendInstructionDecodeResult)
    res.sRead1 := !decodeResFormat.vType
    res.sRead2 := false.B
    res.sReadVD := !(decodeResFormat.firstWiden || ma)
    res.wRead1 := !decodeResFormat.firstWiden
    res.wRead2 := !decodeResFormat.firstWiden
    res.wScheduler := !sp
    res.sExecute := false.B
    //todo: red
    res.wExecuteRes := sp
    res.sWrite := (decodeResFormat.otherUnit && decodeResFormatExt.targetRD) || decodeResFormat.Widen
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

  /** 存 mask */
  val mask: ValidIO[UInt] = Valid(UInt(param.ELEN.W))

  /** 把mask按每四个分一个组,然后看orR */
  val maskGroupedOrR: UInt = UInt((param.ELEN / 4).W)

  /** 这一组写vrf的mask */
  val vrfWriteMask: UInt = UInt(4.W)
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

class ReadBusData(param: LaneParameters) extends Bundle {
  val data:      UInt = UInt(param.HLEN.W)
  val tail:      Bool = Bool()
  val target:    UInt = UInt(param.laneIndexBits.W)
  val instIndex: UInt = UInt(param.instIndexSize.W)
}

class WriteBusData(param: LaneParameters) extends Bundle {
  val data: UInt = UInt(param.ELEN.W)
  val tail: Bool = Bool()
  // 正常的跨lane写可能会有mask类型的指令
  val mask:   UInt = UInt(2.W)
  val target: UInt = UInt(param.laneIndexBits.W)
  // todo: for debug
  val from:      UInt = UInt(param.laneIndexBits.W)
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val counter:   UInt = UInt(param.groupSizeBits.W)
}

class RingPort[T <: Data](gen: T) extends Bundle {
  val enq: DecoupledIO[T] = Flipped(Decoupled(gen))
  val deq: DecoupledIO[T] = Decoupled(gen)
}

class SchedulerFeedback(param: LaneParameters) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val complete:  Bool = Bool()
}

class V0Update(param: LaneParameters) extends Bundle {
  val data:   UInt = UInt(param.ELEN.W)
  val offset: UInt = UInt(param.offsetBits.W)
  // mask/ld类型的有可能不会写完整的32bit
  val mask: UInt = UInt(4.W)
}
