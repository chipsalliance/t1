package v

import chisel3._
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.util.{log2Ceil, Decoupled, DecoupledIO, Valid, ValidIO}

/** Interface from CPU. */
class VRequest(xLen: Int) extends Bundle {
  val instruction: UInt = UInt(32.W)
  val src1Data:    UInt = UInt(xLen.W)
  val src2Data:    UInt = UInt(xLen.W)
}

/** Interface to CPU. */
class VResponse(xLen: Int) extends Bundle {

  /** data write to scalar rd. */
  val data: UInt = UInt(xLen.W)

  /** Vector Fixed-Point Saturation Flag, propagate to vcsr in CSR.
    * This is not maintained in the vector coprocessor since it is not used in the Vector processor.
    */
  val vxsat: Bool = Bool()

  /** assert of [[rd.valid]] indicate vector need to write rd,
    * the [[rd.bits]] is the index of rd
    * TODO: merge [[data]] to rd.
    */
  val rd: ValidIO[UInt] = Valid(UInt(log2Ceil(32).W))

  /** when [[mem]] is asserted, indicate the instruction need to access memory.
    * if the vector instruction need to access memory,
    * to maintain the order of memory access:
    * the scalar core maintains a counter of vector memory access,
    * if the counter is not zero, the memory access instruction of scalar core will stall until the counter is zero.
    *
    * TODO: rename to `memAccess`
    */
  val mem: Bool = Bool()
}

class InstructionRecord(instructionIndexWidth: Int) extends Bundle {
  val instructionIndex: UInt = UInt(instructionIndexWidth.W)
  val vrfWrite:         Bool = Bool()

  /** Whether operation is widen */
  val widen: Bool = Bool()

  /** Whether operation is narrowing */
  val narrowing: Bool = Bool()
  // load | store
  val loadStore: Bool = Bool()
}

class InstructionState extends Bundle {
  val wLast:    Bool = Bool()
  val idle:     Bool = Bool()
  val sExecute: Bool = Bool()
  val sCommit:  Bool = Bool()
}

// TODO: rename
class SpecialInstructionType extends Bundle {
  val red:      Bool = Bool()
  val compress: Bool = Bool()
  val viota:    Bool = Bool()
  val ffo:      Bool = Bool()
  val slid:     Bool = Bool()
  // 其他的需要对齐的指令
  val other: Bool = Bool()
  // 只有v类型的gather指令需要在top执行
  val vGather:  Bool = Bool()
  val mv:       Bool = Bool()
  val popCount: Bool = Bool()
  val extend:   Bool = Bool()
}

class InstructionControl(instIndexWidth: Int, laneSize: Int) extends Bundle {
  val record: InstructionRecord = new InstructionRecord(instIndexWidth)
  val state:  InstructionState = new InstructionState

  /** tag for recording each lane and lsu is finished for this instruction. */
  val endTag: Vec[Bool] = Vec(laneSize + 1, Bool())
}

class ExtendInstructionType extends Bundle {
  val extend:   Bool = Bool()
  val mv:       Bool = Bool()
  val ffo:      Bool = Bool()
  val popCount: Bool = Bool()
  val id:       Bool = Bool()
}

class LaneRequest(param: LaneParameter) extends Bundle {
  val instructionIndex: UInt = UInt(param.instructionIndexBits.W)
  // decode
  val decodeResult: DecodeBundle = Decoder.bundle
  val loadStore:    Bool = Bool()
  val store:        Bool = Bool()
  val special:      Bool = Bool()

  // instruction
  /** vs1 or imm */
  val vs1: UInt = UInt(5.W)

  /** vs2 or rs2 */
  val vs2: UInt = UInt(5.W)

  /** vd or rd */
  val vd: UInt = UInt(5.W)

  val loadStoreEEW: UInt = UInt(2.W)

  /** mask type ? */
  val mask: Bool = Bool()

  val segment: UInt = UInt(3.W)

  /** data of rs1 */
  val readFromScalar: UInt = UInt(param.datapathWidth.W)

  // vmacc 的vd需要跨lane读 TODO: move to [[V]]
  def ma: Bool =
    decodeResult(Decoder.multiplier) && decodeResult(Decoder.uop)(1, 0).xorR && !decodeResult(Decoder.vwmacc)

  // TODO: move to Module
  def initState: InstGroupState = {
    val res: InstGroupState = Wire(new InstGroupState(param))
    val crossRead = decodeResult(Decoder.firstWiden) || decodeResult(Decoder.narrow)
    val crossWrite = decodeResult(Decoder.widen) && !loadStore
    val readOnly = decodeResult(Decoder.readOnly)
    // decode的时候需要注意有些bit操作的指令虽然不需要读vs1,但是需要读v0
    res.sRead1 := !decodeResult(Decoder.vtype) || (decodeResult(Decoder.gather) && !decodeResult(Decoder.vtype))
    res.sRead2 := false.B
    res.sReadVD := !(ma || decodeResult(Decoder.maskLogic))
    res.wRead1 := !crossRead
    res.wRead2 := !crossRead
    res.wScheduler := !(special || readOnly || decodeResult(Decoder.popCount))
    // todo
    res.sScheduler := !(decodeResult(Decoder.maskDestination) || decodeResult(Decoder.red) || readOnly
      || decodeResult(Decoder.ffo) || decodeResult(Decoder.popCount) || loadStore)
    res.sExecute := readOnly || decodeResult(Decoder.nr) || loadStore
    //todo: red
    res.wExecuteRes := (special && !decodeResult(Decoder.ffo)) || readOnly || decodeResult(Decoder.nr)
    res.sWrite := (decodeResult(Decoder.other) && decodeResult(Decoder.targetRd)) || readOnly ||
    decodeResult(Decoder.widen) || decodeResult(Decoder.maskDestination) ||
    decodeResult(Decoder.red) || decodeResult(Decoder.popCount) || loadStore
    res.sCrossWrite0 := !crossWrite
    res.sCrossWrite1 := !crossWrite
    res.sSendResult0 := !crossRead
    res.sSendResult1 := !crossRead
    res.sCrossRead0 := !crossRead
    res.sCrossRead1 := !crossRead
    res
  }

  // TODO: move to Module
  def instType: UInt = {
    VecInit(
      Seq(
        decodeResult(Decoder.logic) && !decodeResult(Decoder.other),
        decodeResult(Decoder.adder) && !decodeResult(Decoder.other),
        decodeResult(Decoder.shift) && !decodeResult(Decoder.other),
        decodeResult(Decoder.multiplier) && !decodeResult(Decoder.other),
        decodeResult(Decoder.divider) && !decodeResult(Decoder.other),
        decodeResult(Decoder.other)
      )
    ).asUInt
  }
}

class InstGroupState(param: LaneParameter) extends Bundle {
  // s for scheduler
  //   0 is for need to do but not do for now
  //   1 is done or don't need to do
  // w for wait
  //   0 is for need to wait and still waiting for finishing
  //   1 is for don't need to wait or already finished
  val sRead1:     Bool = Bool()
  val sRead2:     Bool = Bool()
  val sReadVD:    Bool = Bool()
  val wRead1:     Bool = Bool()
  val wRead2:     Bool = Bool()
  val wScheduler: Bool = Bool()
  val sScheduler: Bool = Bool()
  val sExecute:   Bool = Bool()
  // 发送写的
  val sCrossWrite0: Bool = Bool()
  val sCrossWrite1: Bool = Bool()
  // 读跨lane的
  val sCrossRead0: Bool = Bool()
  val sCrossRead1: Bool = Bool()
  // 发送读的
  val sSendResult0: Bool = Bool()
  val sSendResult1: Bool = Bool()
  val wExecuteRes:  Bool = Bool()
  val sWrite:       Bool = Bool()
}

class InstControlRecord(param: LaneParameter) extends Bundle {
  val originalInformation: LaneRequest = new LaneRequest(param)
  val state:               InstGroupState = new InstGroupState(param)
  val initState:           InstGroupState = new InstGroupState(param)

  /** which group in the slot is executing. */
  val groupCounter: UInt = UInt(param.groupNumberBits.W)
  // mask 类型的被别的lane完成了, 然后scheduler会通知 eg：sbf
  val schedulerComplete: Bool = Bool()
  // mask 类型的被这一组数据完成了 eg：sbf
  val selfCompleted: Bool = Bool()

  /** the execution index in group
    * use byte as granularity,
    * SEW
    * 0 -> 00 01 10 11
    * 1 -> 00    10
    * 2 -> 00
    * TODO: 3(64 only)
    */
  val executeIndex: UInt = UInt(log2Ceil(param.dataPathByteWidth).W)

  /** 应对vl很小的时候,不会用到这条lane */
  val instCompleted: Bool = Bool()

  /** 存 mask */
  val mask: ValidIO[UInt] = Valid(UInt(param.datapathWidth.W))

  /** 把mask按每四个分一个组,然后看orR */
  val maskGroupedOrR: UInt = UInt((param.datapathWidth / param.sewMin).W)

  /** 这一组写vrf的mask */
  val vrfWriteMask: UInt = UInt(4.W)
}

/** CSR Interface from Scalar Core. */
class CSRInterface(vlWidth: Int) extends Bundle {

  /** Vector Length Register `vl`,
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#35-vector-length-register-vl]]
    */
  val vl: UInt = UInt(vlWidth.W)

  /** Vector Start Index CSR `vstart`,
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#37-vector-start-index-csr-vstart]]
    * TODO: rename to `vstart`
    */
  val vStart: UInt = UInt(vlWidth.W)

  /** Vector Register Grouping `vlmul[2:0]`
    * subfield of `vtype``
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#342-vector-register-grouping-vlmul20]]
    */
  val vlmul: UInt = UInt(3.W)

  /** Vector Register Grouping (vlmul[2:0])
    * subfield of `vtype``
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#341-vector-selected-element-width-vsew20]]
    */
  val vSew: UInt = UInt(2.W)

  /** Rounding mode register
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm]]
    */
  val vxrm: UInt = UInt(2.W)

  /** Vector Tail Agnostic
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm]]
    *
    * we always keep the undisturbed behavior, since there is no rename here.
    */
  val vta: Bool = Bool()

  /** Vector Mask Agnostic
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm]]
    *
    * we always keep the undisturbed behavior, since there is no rename here.
    */
  val vma: Bool = Bool()

  /** TODO: remove it. */
  val ignoreException: Bool = Bool()
}

class LaneDataResponse(param: LaneParameter) extends Bundle {
  val data: UInt = UInt(param.datapathWidth.W)
  // TODO: move to top?
  val toLSU:            Bool = Bool()
  val ffoSuccess:       Bool = Bool()
  val instructionIndex: UInt = UInt(param.instructionIndexBits.W)
}

class ReadBusData(param: LaneParameter) extends Bundle {
  val data: UInt = UInt(param.datapathWidth.W)
  val tail: Bool = Bool()
  // todo: for debug
  val from:      UInt = UInt(param.laneNumberBits.W)
  val target:    UInt = UInt(param.laneNumberBits.W)
  val instIndex: UInt = UInt(param.instructionIndexBits.W)
  val counter:   UInt = UInt(param.groupNumberBits.W)
}

class WriteBusData(param: LaneParameter) extends Bundle {
  val data: UInt = UInt(param.datapathWidth.W)
  val tail: Bool = Bool()
  // 正常的跨lane写可能会有mask类型的指令
  val mask:   UInt = UInt(2.W)
  val target: UInt = UInt(param.laneNumberBits.W)
  // todo: for debug
  val from:      UInt = UInt(param.laneNumberBits.W)
  val instIndex: UInt = UInt(param.instructionIndexBits.W)
  val counter:   UInt = UInt(param.groupNumberBits.W)
}

class RingPort[T <: Data](gen: T) extends Bundle {
  val enq: DecoupledIO[T] = Flipped(Decoupled(gen))
  val deq: DecoupledIO[T] = Decoupled(gen)
}

class SchedulerFeedback(param: LaneParameter) extends Bundle {
  val instructionIndex: UInt = UInt(param.instructionIndexBits.W)

  /** for instructions that might finish in other lanes, use [[complete]] to tell the target lane */
  val complete: Bool = Bool()
}

class V0Update(param: LaneParameter) extends Bundle {
  val data:   UInt = UInt(param.datapathWidth.W)
  val offset: UInt = UInt(param.vrfOffsetBits.W)
  // mask/ld类型的有可能不会写完整的32bit
  val mask: UInt = UInt(4.W)
}

/** Request to access VRF in each lanes. */
class VRFReadRequest(regNumBits: Int, offsetBits: Int, instructionIndexBits: Int) extends Bundle {

  /** address to access VRF.(v0, v1, v2, ...) */
  val vs: UInt = UInt(regNumBits.W)

  /** the offset of VRF access. */
  val offset: UInt = UInt(offsetBits.W)

  /** index for record the age of instruction, designed for handling RAW hazard */
  val instructionIndex: UInt = UInt(instructionIndexBits.W)
}

class VRFWriteRequest(regNumBits: Int, offsetBits: Int, instructionIndexSize: Int, dataPathWidth: Int) extends Bundle {

  /** address to access VRF.(v0, v1, v2, ...) */
  val vd: UInt = UInt(regNumBits.W)

  /** the offset of VRF access. */
  val offset: UInt = UInt(offsetBits.W)

  /** write mask in byte. */
  val mask: UInt = UInt((dataPathWidth / 8).W)

  /** data to write to VRF. */
  val data: UInt = UInt(dataPathWidth.W)

  /** this is the last write of this instruction
    * TODO: rename to isLast.
    */
  val last: Bool = Bool()

  /** used to update the record in VRF. */
  val instructionIndex: UInt = UInt(instructionIndexSize.W)
}

class VRFWriteReport(param: VRFParam) extends Bundle {
  val vd:        ValidIO[UInt] = Valid(UInt(param.regNumBits.W))
  val vs1:       ValidIO[UInt] = Valid(UInt(param.regNumBits.W))
  val vs2:       UInt = UInt(param.regNumBits.W)
  val instIndex: UInt = UInt(param.instructionIndexBits.W)
  val vdOffset:  UInt = UInt(3.W)
  val offset:    UInt = UInt(param.vrfOffsetBits.W)
  val seg:       ValidIO[UInt] = Valid(UInt(3.W))
  val eew:       UInt = UInt(2.W)
  val ls:        Bool = Bool()
  val st:        Bool = Bool()
  val narrow:    Bool = Bool()
  val widen:     Bool = Bool()
  val stFinish:  Bool = Bool()
  // 乘加
  val ma:           Bool = Bool()
  val unOrderWrite: Bool = Bool()
}

/** 为了decode, 指令需要在入口的时候打一拍, 这是需要保存的信息 */
class InstructionPipeBundle(parameter: VParameter) extends Bundle {
  // 原始指令信息
  val request: VRequest = new VRequest(parameter.xLen)
  // decode 的结果
  val decodeResult: DecodeBundle = new DecodeBundle(Decoder.all)
  // 这条指令被vector分配的index
  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  // 指令的csr信息
  val csr = new CSRInterface(parameter.laneParam.vlMaxBits)
}

class LSUWriteQueueBundle(param: LSUParam) extends Bundle {
  val data: VRFWriteRequest =
    new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
  val targetLane: UInt = UInt(param.laneNumber.W)
}

class LSUInstructionInformation extends Bundle {

  /** specifies the number of fields in each segment, for segment load/stores
    * NFIELDS = nf + 1
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#71-vector-loadstore-instruction-encoding]]
    */
  val nf: UInt = UInt(3.W)

  /** extended memory element width
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#sec-vector-loadstore-width-encoding]]
    *
    * this field is always tied to 0, since spec is reserved for future use.
    *
    * TODO: add illegal instruction exception in scalar decode stage for it.
    */
  val mew: Bool = Bool()

  /** specifies memory addressing mode
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#72-vector-loadstore-addressing-modes]]
    * table 7, and table 8
    *
    * - 00: unit stride
    * - 01: indexed unordered
    * - 10: stride
    * - 11: indexed ordered
    */
  val mop: UInt = UInt(2.W)

  /** additional field encoding variants of unit-stride instructions
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#71-vector-loadstore-instruction-encoding]]
    * and [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#72-vector-loadstore-addressing-modes]]
    * table 9 and table 10
    *
    * 0b00000 -> unit stride
    * 0b01000 -> whole register
    * 0b01011 -> mask, eew = 8
    * 0b10000 -> fault only first (load)
    */
  val lumop: UInt = UInt(5.W)

  /** specifies size of memory elements, and distinguishes from FP scalar
    * MSB is ignored, which is used in FP.
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#71-vector-loadstore-instruction-encoding]]
    * and [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#73-vector-loadstore-width-encoding]]
    * table 11
    *
    * 00 -> 8
    * 01 -> 16
    * 10 -> 32
    * 11 -> 64
    */
  val eew: UInt = UInt(2.W)

  /** specifies v register holding store data
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#71-vector-loadstore-instruction-encoding]]
    */
  val vs3: UInt = UInt(5.W)

  /** indicate if this instruction is store. */
  val isStore: Bool = Bool()

  /** indicate if this instruction use mask. */
  val useMask: Bool = Bool()

  /** fault only first element
    * TODO: extract it
    */
  def fof: Bool = mop === 0.U && lumop(4) && !isStore
}

/** request interface from [[V]] to [[LSU]] */
class LSURequest(dataWidth: Int) extends Bundle {

  /** from instruction. */
  val instructionInformation: LSUInstructionInformation = new LSUInstructionInformation

  /** data from rs1 in scalar core, if necessary. */
  val rs1Data: UInt = UInt(dataWidth.W)

  /** data from rs2 in scalar core, if necessary. */
  val rs2Data: UInt = UInt(dataWidth.W)

  /** tag from [[V]] to record instruction.
    * TODO: parameterize it.
    */
  val instructionIndex: UInt = UInt(3.W)
}
