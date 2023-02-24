package v

import chisel3._
import chisel3.util._

class OtherUnitReq(param: LaneParameter) extends Bundle {
  val src:        Vec[UInt] = Vec(3, UInt(param.datapathWidth.W))
  val opcode:     UInt = UInt(3.W)
  val extendType: Valid[ExtendInstructionType] = Valid(new ExtendInstructionType)
  val imm:        UInt = UInt(3.W)
  val groupIndex: UInt = UInt(param.groupNumberWidth.W)
  val laneIndex:  UInt = UInt(param.laneNumberWidth.W)
  val sign:       Bool = Bool()
  val mask:       Bool = Bool()
  val complete:   Bool = Bool()
  // eg: ffo
  val specialOpcode: UInt = UInt(4.W)
  // vm = 0
  val maskType: Bool = Bool()
}

class OtherUnitCsr extends Bundle {
  val vSew: UInt = UInt(2.W)
  val vxrm: UInt = UInt(2.W)
}

class OtherUnitResp(param: DataPathParam) extends Bundle {
  val data: UInt = UInt(param.dataWidth.W)
  val clipFail:    Bool = Bool()
  val ffoSuccess:  Bool = Bool()
}

class OtherUnit(param: LaneParameter) extends Module {
  val req:  OtherUnitReq = IO(Input(new OtherUnitReq(param)))
  val resp: OtherUnitResp = IO(Output(new OtherUnitResp(param.datePathParam)))
  val csr:  OtherUnitCsr = IO(Input(new OtherUnitCsr))

  val ffo:      LaneFFO = Module(new LaneFFO(param.datePathParam))
  val popCount: LanePopCount = Module(new LanePopCount(param.datePathParam))
  val vSewOH:   UInt = UIntToOH(csr.vSew)(2, 0)
  // ["slide", "rgather", "merge", "mv", "clip", "compress"]
  val opcodeOH: UInt = UIntToOH(req.opcode)(5, 0)

  ffo.src := req.src
  ffo.resultSelect := req.specialOpcode
  ffo.complete := req.complete
  ffo.maskType := req.maskType
  popCount.src := req.src.head

  val signValue:  Bool = req.src(1)(param.datapathWidth - 1) && req.sign
  val signExtend: UInt = Fill(param.datapathWidth, signValue)

  // clip 2sew -> sew
  // vSew 0 -> sew = 8 => log2(sew) = 4
  val clipSize: UInt = Mux1H(vSewOH(2, 1), Seq(false.B ## req.src.head(4), req.src.head(5, 4))) ## req.src.head(3, 0)
  val clipMask: UInt = FillInterleaved(8, vSewOH(2) ## vSewOH(2) ## vSewOH(2, 1).orR ## true.B)
  val largestClipResult: UInt = (clipMask >> req.sign).asUInt
  val clipMaskRemainder: UInt = FillInterleaved(8, !vSewOH(2) ## !vSewOH(2) ## vSewOH(0) ## false.B)
  val roundTail: UInt = (1.U << clipSize).asUInt
  val lostMSB:   UInt = (roundTail >> 1).asUInt
  val roundMask: UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & req.src(1)).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & req.src(1)).orR // TODO: is this WithoutMSB
  // v[d]
  val vd: Bool = (roundTail & req.src(1)).orR
  // r
  val roundR:      Bool = Mux1H(UIntToOH(csr.vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB)))
  val roundResult: UInt = (((signExtend ## req.src(1)) >> clipSize).asUInt + roundR)(param.datapathWidth - 1, 0)
  val roundRemainder = roundResult & clipMaskRemainder
  val roundSignBits = Mux1H(vSewOH(2, 0), Seq(roundResult(7), roundResult(15), roundResult(31)))
  val roundResultOverlap: Bool = roundRemainder.orR && !(req.sign && (roundRemainder | clipMask).andR && roundSignBits)
  val clipResult = Mux(roundResultOverlap, largestClipResult, roundResult)

  val indexRes: UInt = req.groupIndex ## req.laneIndex

  val extendSign: Bool = req.sign && Mux1H(vSewOH, Seq(req.src.head(7), req.src.head(15), req.src.head(31)))
  // todo
  val extendRes: UInt = Mux(vSewOH(2), req.src.head(31, 16), Fill(16, extendSign)) ##
    Mux(vSewOH(1), Fill(8, extendSign), req.src.head(15, 8)) ## req.src.head(7, 0)

  /**
    * 需要特别注意 vmerge/vmv 类型的指令的编码方式是一样的,
    * 区别在于vmerge是mask类型的
    * 我们不需要纠结相应的mask_bit的值,因为执行意味着它一定是1
    * 然而mask是1的情况下vmerge与vmv的行为都是一样的:都是选vs1/rs1/imm
    */
  // extend: vExtend mv ffo popCount viota
  // slide, rgather, merge, mv, clip, compress
  val r0: Bool = (opcodeOH(3, 0) ## opcodeOH(5)).orR
  // 处理merge & mask
  // 排除 extend 的影响
  val originalOpcodeOH: UInt = Mux(req.extendType.valid, 0.U, opcodeOH)
  // 选source1的情况 todo: 需要执行的 gather 可以视为merge, 前提不读vs2
  val selectSource1: Bool = ((originalOpcodeOH(2) || originalOpcodeOH(1)) && req.mask) || originalOpcodeOH(3)
  val selectSource2: Bool = originalOpcodeOH(0) || (originalOpcodeOH(2) && !req.mask) ||
    originalOpcodeOH(5)
  val resultSelect: UInt = VecInit(
    Seq(
      req.extendType.valid && req.extendType.bits.extend,
      req.extendType.valid && req.extendType.bits.ffo,
      req.extendType.valid && req.extendType.bits.popCount,
      req.extendType.valid && req.extendType.bits.id,
      !req.extendType.valid && opcodeOH(4),
      (req.extendType.valid && req.extendType.bits.mv) || selectSource1,
      selectSource2
    )
  ).asUInt
  val result: UInt = Mux1H(
    resultSelect,
    Seq(extendRes, ffo.resp.bits, popCount.resp, indexRes, clipResult, req.src.head, req.src(1))
  )
  resp.data := result
  resp.ffoSuccess := ffo.resp.valid
  resp.clipFail := DontCare
}
