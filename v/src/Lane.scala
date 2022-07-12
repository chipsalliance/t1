package v

import chisel3._
import chisel3.util._

class LaneReq(param: LaneParameters) extends Bundle {
  val index: UInt = UInt(param.idBits.W)
  val uop: UInt = UInt(4.W)
  val w: Bool = Bool()
  val n: Bool =  Bool()
  val sew: UInt = UInt(2.W)
  val src: Vec[UInt] = Vec(3, UInt(param.ELEN.W))
  val groupIndex: UInt = UInt(param.groupSizeBits.W)
  val sign: Bool = Bool()
  val maskOP2: Bool = Bool()
  val maskDestination: Bool = Bool()
  val rm: UInt = UInt(2.W)
}

class LaneResp(param: LaneParameters) extends Bundle {
  val res: UInt = UInt(param.ELEN.W)
  val carry: UInt = UInt(param.ELEN.W)
}

class LaneDecodeResult extends Bundle {
  // sub unit
  val logic: Bool = Bool()
  val arithmetic: Bool = Bool()
  val shift: Bool = Bool()
  val mul: Bool = Bool()
  val div: Bool = Bool()
  val popCount: Bool = Bool()
  val ffo: Bool = Bool()
  val getIndex: Bool = Bool()
  val dataProcessing: Bool = Bool()
  // operand
  // operand 0 is signed
  val s0: Bool = Bool()
  val s1: Bool = Bool()

  // - operand1?
  val sub1: Bool = Bool()
  val sub2: Bool = Bool()

  val subUop: UInt = UInt(3.W)
}

class LaneSrcResult(param: LaneParameters) extends Bundle {
  val src0: UInt = UInt(param.ELEN.W)
  val src1: UInt = UInt(param.ELEN.W)
  val src2: UInt = UInt(param.ELEN.W)
  val src3: UInt = UInt(2.W)
  val mask: UInt = UInt(param.ELEN.W)
  val desMask: UInt = UInt(param.ELEN.W)
}

class Lane(param: LaneParameters) extends Module {
  val req: DecoupledIO[LaneReq] = IO(Flipped(Decoupled(new LaneReq(param))))
  val resp: ValidIO[LaneResp] = IO(Valid(new LaneResp(param)))

  val decodeRes: LaneDecodeResult = WireInit(0.U.asTypeOf(new LaneDecodeResult))

  // sew
  /*val sewByteMask: UInt = (1.U << req.bits.sew).asUInt - 1.U
  val sewBitMask: UInt = FillInterleaved(8, sewByteMask)*/
  val LaneSrcVec: Vec[LaneSrcResult] = VecInit(Seq.tabulate(4) { sew =>
    val res = WireDefault(0.U.asTypeOf(new LaneSrcResult(param)))
    val significantBit = 1 << (sew + 3)
    val remainder = param.ELEN - significantBit + 1

    // handle sign bit
    val sign0 = req.bits.src(0)(significantBit - 1) && decodeRes.s0
    // operand sign correction
    val osc0 = Fill(remainder, sign0) ## req.bits.src(0)(significantBit - 1, 0)
    val sign1 = req.bits.src(1)(significantBit - 1) && decodeRes.s1
    val osc1 = Fill(remainder, sign1) ## req.bits.src(1)(significantBit - 1, 0)
    /*val sign2 = req.bits.src(2)(significantBit - 1)
    val osc2 = Fill(remainder, sign2) ## req.bits.src(2)(significantBit - 1, 0)*/

    // op0 - op1 (- op2)
    val SubtractionOperand1: UInt = osc1 ^ Fill(64, decodeRes.sub1)
    val SubtractionOperand2: UInt = req.bits.src(2) ^ Fill(64, decodeRes.sub2)

    // - op1 = ~op1 + 1
    val negativeCompensation: UInt = (!req.bits.maskOP2 & decodeRes.sub1 & decodeRes.sub2) ##
      ((req.bits.maskOP2 && req.bits.src(2)(0)) || (!req.bits.maskOP2 & (decodeRes.sub1 ^ decodeRes.sub2)))
    res.src0 := osc0
    res.src1 := SubtractionOperand1
    res.src2 := SubtractionOperand2
    res.src3 := negativeCompensation
    res.mask := ((1 << sew) - 1).U(param.ELEN.W)
    // todo handle w n
    res.desMask := ((1 << sew) - 1).U(param.ELEN.W)
    res
  })

  val srcSelect: LaneSrcResult = Mux1H(UIntToOH(req.bits.sew), LaneSrcVec)

  // alu
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

  // div
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

  // find first one
  val ffoInput: LaneSrcResult = Mux(decodeRes.ffo, srcSelect, 0.U.asTypeOf(srcSelect))
  ffo.src := ffoInput.src0
  ffo.resultSelect := decodeRes.subUop
  resultVec(6) := Mux(decodeRes.ffo, ffo.resp.bits, 0.U)

  // index
  getID.groupIndex := req.bits.groupIndex
  getID.laneIndex := req.bits.index
  resultVec(7) := Mux(decodeRes.getIndex, getID.resp, 0.U)

  dp.in.src := resultVec.reduce(_ | _)
  dp.in.sign := decodeRes.s0
  dp.in.mask := srcSelect.desMask
  dp.in.rm := req.bits.rm
  dp.in.rSize.valid := decodeRes.dataProcessing
  dp.in.rSize.bits := Mux(decodeRes.subUop(2), req.bits.src(1), 1.U)

  req.ready := (!decodeRes.div) || div.srcVec.ready
  resp.valid := (!decodeRes.div) || div.resp.valid
  resp.bits.res := dp.resp
  resp.bits.carry := carryRes
  div.srcVec.valid := req.valid & decodeRes.div
}
