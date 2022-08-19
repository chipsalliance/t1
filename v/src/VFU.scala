package v

import chisel3._
import chisel3.util._

/** The request from the controller to the lane (execution unit) */
class VFUReq(param: VFUParameters) extends Bundle {
  /** The index of lane */
  val index: UInt = UInt(param.idBits.W)

  /** The uop, to be re-encoded in lane */
  val uop: UInt = UInt(4.W)

  // TODO:
  /** Whether operation is widen */
  val w: Bool = Bool()

  /** Whether operation is narrowing */
  val n: Bool =  Bool()

  /** SEW encoded in 2 bits, corresponding to 8, 16, 32, 64 */
  val sew: UInt = UInt(2.W)

  /** Three source operands, TODO: wtf is the third operand
   */
  val src: Vec[UInt] = Vec(3, UInt(param.ELEN.W))

  /** Since lane requests are sent group by group, groupIndex is the index of it */
  val groupIndex: UInt = UInt(param.groupSizeBits.W)

  /** Whether the operation is signed */
  val sign: Bool = Bool()

  /** Whether operand2 is the mask */
  val maskOP2: Bool = Bool()

  /** Whether the output register is in mask format */
  val maskDestination: Bool = Bool()

  /** Rounding mode */
  val rm: UInt = UInt(2.W)
}

/** The response returned from the lane */
class VFUResp(param: VFUParameters) extends Bundle {
  val res: UInt = UInt(param.ELEN.W)

  /** For widen operations, this is the upper part of the result */
  val carry: UInt = UInt(param.ELEN.W)
}

/** The decoding result of vector instructions.
 *  8 bits onehot encoding of uop, indicating to type of operation
 *  3 bits encoding of subUop, indicating to some operation together with uop
 */
class LaneDecodeResult extends Bundle {
  /** Corresponding subUop
   *  0 => and
   *  1 => nand
   *  2 => andn
   *  3 => or
   *  4 => orn
   *  5 => xor
   *  6 => xnor
   *  7 => andn
   */
  val logic: Bool = Bool()
  val shift: Bool = Bool()
  /** Corresponding subUop
   *  0 => add
   *  1 => sub
   *  2 => adc
   *  3 => madc
   *  4 => sbc
   *  5 => msbc
   *  6 => slt,sle,sgt,sge
   *  7 => max,min
   */
  val arithmetic: Bool = Bool()

  /** Corresponding subUop
   *  0 => mul
   *  4 => wmul
   *  7 => ma
   */
  val mul: Bool = Bool()

  /** Corresponding subUop
   *  0 => div
   */
  val div: Bool = Bool()

  /** Corresponding subUop
   *  0 => popcount
   */
  val popCount: Bool = Bool()

  /** Corresponding subUop
   *  0 => ffo
   */
  val ffo: Bool = Bool()

  /** Corresponding subUop
   *  0 => getIndex
   */
  val getIndex: Bool = Bool()

  val dataProcessing: Bool = Bool()

  /** Whether operand 0 is signed */
  val s0: Bool = Bool()

  /** Whether operand 1 is signed */
  val s1: Bool = Bool()

  /** Whether subtraction applied on operand1 */
  val sub1: Bool = Bool()
  /** Whether subtraction applied on operand2 */
  val sub2: Bool = Bool()

  val subUop: UInt = UInt(3.W)
  /** Whether it is Averaging add/sub ? */
  val averaging: Bool = Bool()
}

class LaneSrcResult(param: VFUParameters) extends Bundle {
  val src0: UInt = UInt(param.ELEN.W)
  val src1: UInt = UInt(param.ELEN.W)
  val src2: UInt = UInt(param.ELEN.W)

  /** The additional operand required in adc, sbc and ma operations */
  val src3: UInt = UInt(2.W)

  val mask: UInt = UInt(param.ELEN.W)

  /** Destination mask, may differ from `mask` in widen/narrowing operation */
  val desMask: UInt = UInt(param.ELEN.W)
}

class VFU(param: VFUParameters) extends Module {
  val req: DecoupledIO[VFUReq] = IO(Flipped(Decoupled(new VFUReq(param))))
  val resp: ValidIO[VFUResp] = IO(Valid(new VFUResp(param)))

  // TODO: decode req
  val decodeRes: LaneDecodeResult = WireInit(0.U.asTypeOf(new LaneDecodeResult))

  val LaneSrcVec: Vec[LaneSrcResult] = VecInit(Seq.tabulate(param.maxVSew) { sew =>
    val res = WireDefault(0.U.asTypeOf(new LaneSrcResult(param)))
    val significantBit = 1 << (sew + 3)
    val remainder = param.ELEN - significantBit + 1

    // handle sign bit
    val sign0 = req.bits.src(0)(significantBit - 1) && decodeRes.s0
    val sign1 = req.bits.src(1)(significantBit - 1) && decodeRes.s1

    // operand sign correction
    val osc0 = Fill(remainder, sign0) ## req.bits.src(0)(significantBit - 1, 0)
    val osc1 = Fill(remainder, sign1) ## req.bits.src(1)(significantBit - 1, 0)

    // To output op0 +(-) op1 +(-) op2, when subtraction is applied instead of addition,
    // first invert all bits then plus an additional one (negativeCompensation)
    val SubtractionOperand1: UInt = osc1 ^ Fill(64, decodeRes.sub1)
    val SubtractionOperand2: UInt = req.bits.src(2) ^ Fill(64, decodeRes.sub2)
    val negativeCompensation: UInt = (!req.bits.maskOP2 & decodeRes.sub1 & decodeRes.sub2) ##
      ((req.bits.maskOP2 && req.bits.src(2)(0)) || (!req.bits.maskOP2 & (decodeRes.sub1 ^ decodeRes.sub2)))

    res.src0 := osc0
    res.src1 := SubtractionOperand1
    res.src2 := SubtractionOperand2
    res.src3 := negativeCompensation
    res.mask := ((1 << sew) - 1).U(param.ELEN.W)
    // TODO: handle w n
    res.desMask := ((1 << sew) - 1).U(param.ELEN.W)
    res
  })

  val srcSelect: LaneSrcResult = Mux1H(UIntToOH(req.bits.sew)(param.maxVSew, 0), LaneSrcVec)

  // make ALU units
  val logicUnit: LaneLogic = Module(new LaneLogic(param.datePathParam))
  val adder: LaneAdder = Module(new LaneAdder(param.datePathParam))
  val shifter: LaneShifter = Module(new LaneShifter(param.shifterParameter))
  val mul: LaneMul = Module(new LaneMul(param.mulParam))
  val div: LaneDiv = Module(new LaneDiv(param.datePathParam))
  val popCount: LanePopCount = Module(new LanePopCount(param.lanePopCountParameter))
  val ffo: LaneFFO = Module(new LaneFFO(param.datePathParam))
  val getID: LaneIndexCalculator = Module(new LaneIndexCalculator(param.indexParam))
  val dp: LaneDataProcessing = Module(new LaneDataProcessing(param.datePathParam))

  val resultVec: Vec[UInt] = Wire(Vec(8, UInt(param.mulRespWidth.W)))
  val carryRes: UInt = Wire(UInt(param.mulRespWidth.W))

  // Notice that both the input and the output of each functional unit is mux-ed, 
  // to reduce power consumption.

  // logicUnit connect
  val logicInput: LaneSrcResult = Mux(decodeRes.logic, srcSelect, 0.U.asTypeOf(srcSelect))
  logicUnit.src :=  VecInit(Seq(logicInput.src0, logicInput.src1))
  logicUnit.opcode :=  decodeRes.subUop
  resultVec.head := Mux(decodeRes.logic, logicUnit.resp, 0.U)

  // adder connect
  val addInput: LaneSrcResult = Mux(decodeRes.arithmetic, srcSelect, 0.U.asTypeOf(srcSelect))
  adder.src :=  VecInit(Seq(addInput.src0, addInput.src1, addInput.src3))
  resultVec(1) := Mux(decodeRes.logic, adder.resp, 0.U)

  // shifter connect
  val shiftInput: LaneSrcResult = Mux(decodeRes.shift, srcSelect, 0.U.asTypeOf(srcSelect))
  shifter.src := shiftInput.src0
  shifter.sign := decodeRes.subUop(0)
  shifter.direction := decodeRes.subUop(1)
  shifter.shifterSize := shiftInput.src2
  shifter.mask := shiftInput.mask
  resultVec(2) := Mux(decodeRes.logic, shifter.resp, 0.U)

  // mul connect
  val mulInput: LaneSrcResult = Mux(decodeRes.mul, srcSelect, 0.U.asTypeOf(srcSelect))
  mul.src :=  VecInit(Seq(addInput.src0, addInput.src1, addInput.src2))
  resultVec(3) := Mux(decodeRes.logic, mul.resp.head, 0.U)
  carryRes := mul.resp.last

  // div connect
  val divInput: LaneSrcResult = Mux(decodeRes.div, srcSelect, 0.U.asTypeOf(srcSelect))
  div.srcVec.bits := VecInit(Seq(divInput.src0, divInput.src1))
  div.mask := divInput.mask
  div.sign := decodeRes.subUop(0)
  div.div := decodeRes.subUop(1)
  resultVec(4) := Mux(decodeRes.div, div.resp.bits, 0.U)

  // pop count
  val popCountInput: LaneSrcResult = Mux(decodeRes.popCount, srcSelect, 0.U.asTypeOf(srcSelect))
  popCount.src := popCountInput.src0
  resultVec(5) := Mux(decodeRes.popCount, popCount.resp, 0.U)

  // ffo (find first one) connect
  val ffoInput: LaneSrcResult = Mux(decodeRes.ffo, srcSelect, 0.U.asTypeOf(srcSelect))
  ffo.src := ffoInput.src0
  ffo.resultSelect := decodeRes.subUop
  resultVec(6) := Mux(decodeRes.ffo, ffo.resp.bits, 0.U)

  // getIndex connect
  getID.groupIndex := req.bits.groupIndex
  getID.laneIndex := req.bits.index
  resultVec(7) := Mux(decodeRes.getIndex, getID.resp, 0.U)

  dp.in.src := resultVec.reduce(_ | _)
  dp.in.sign := decodeRes.s0
  dp.in.mask := srcSelect.desMask
  dp.in.rm := req.bits.rm
  dp.in.rSize.valid := decodeRes.dataProcessing
  dp.in.rSize.bits := Mux(decodeRes.averaging, req.bits.src(1), 1.U)

  req.ready := (!decodeRes.div) || div.srcVec.ready
  resp.valid := (!decodeRes.div) || div.resp.valid
  resp.bits.res := dp.resp
  resp.bits.carry := carryRes
  div.srcVec.valid := req.valid & decodeRes.div
}
