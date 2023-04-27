package v

import chisel3._
import chisel3.util._

class OtherUnitReq(param: LaneParameter) extends Bundle {
  val src:     Vec[UInt] = Vec(3, UInt(param.datapathWidth.W))
  val popInit: UInt = UInt(param.vlMaxBits.W)
  val opcode:  UInt = UInt(4.W)
  // TODO: remove it.
  val imm:        UInt = UInt(3.W)
  val groupIndex: UInt = UInt(param.groupNumberBits.W)
  val laneIndex:  UInt = UInt(param.laneNumberBits.W)
  // 给vid计算index用的
  val executeIndex: UInt = UInt(log2Ceil(param.dataPathByteWidth).W)
  val sign:         Bool = Bool()
  val mask:         Bool = Bool()
  val complete:     Bool = Bool()
  // vm = 0
  val maskType: Bool = Bool()
  // csr
  val vSew: UInt = UInt(2.W)
  val vxrm: UInt = UInt(2.W)
}

class OtherUnitResp(datapathWidth: Int) extends Bundle {
  val data:       UInt = UInt(datapathWidth.W)
  val clipFail:   Bool = Bool()
  val ffoSuccess: Bool = Bool()
}

class OtherUnit(param: LaneParameter) extends Module {
  val req:  OtherUnitReq = IO(Input(new OtherUnitReq(param)))
  val resp: OtherUnitResp = IO(Output(new OtherUnitResp(param.datapathWidth)))

  val ffo:      LaneFFO = Module(new LaneFFO(param.datapathWidth))
  val popCount: LanePopCount = Module(new LanePopCount(param.datapathWidth))
  val vSewOH:   UInt = UIntToOH(req.vSew)(2, 0)
  // ["", "", "", "", "rgather", "merge", "clip", "mv", "pop", "id"]
  val opcodeOH:         UInt = UIntToOH(req.opcode)(9, 0)
  val isffo:            Bool = opcodeOH(3, 0).orR
  val originalOpcodeOH: UInt = opcodeOH(9, 4)

  ffo.src := req.src
  ffo.resultSelect := req.opcode
  ffo.complete := req.complete
  ffo.maskType := req.maskType
  popCount.src := req.src(1) & Mux(req.maskType, req.src.head, -1.S(param.datapathWidth.W).asUInt)

  val signValue:  Bool = req.src(1)(param.datapathWidth - 1) && req.sign
  val signExtend: UInt = Fill(param.datapathWidth, signValue)

  // clip 2sew -> sew
  // vSew 0 -> sew = 8 => log2(sew) = 4
  val clipSize:          UInt = Mux1H(vSewOH(2, 1), Seq(false.B ## req.src.head(4), req.src.head(5, 4))) ## req.src.head(3, 0)
  val clipMask:          UInt = FillInterleaved(8, vSewOH(2) ## vSewOH(2) ## vSewOH(2, 1).orR ## true.B)
  val largestClipResult: UInt = (clipMask >> req.sign).asUInt
  val clipMaskRemainder: UInt = FillInterleaved(8, !vSewOH(2) ## !vSewOH(2) ## vSewOH(0) ## false.B)
  val roundTail:         UInt = (1.U << clipSize).asUInt
  val lostMSB:           UInt = (roundTail >> 1).asUInt
  val roundMask:         UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & req.src(1)).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & req.src(1)).orR // TODO: is this WithoutMSB
  // v[d]
  val vd: Bool = (roundTail & req.src(1)).orR
  // r
  val roundR:      Bool = Mux1H(UIntToOH(req.vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB)))
  val roundResult: UInt = (((signExtend ## req.src(1)) >> clipSize).asUInt + roundR)(param.datapathWidth - 1, 0)
  val roundRemainder = roundResult & clipMaskRemainder
  val roundSignBits = Mux1H(vSewOH(2, 0), Seq(roundResult(7), roundResult(15), roundResult(31)))
  val roundResultOverlap: Bool = roundRemainder.orR && !(req.sign && (roundRemainder | clipMask).andR && roundSignBits)
  val clipResult = Mux(roundResultOverlap, largestClipResult, roundResult)

  val indexRes: UInt = ((req.groupIndex ## req.laneIndex ## req.executeIndex) >> req.vSew).asUInt

  val extendSign: Bool = req.sign && Mux1H(vSewOH, Seq(req.src.head(7), req.src.head(15), req.src.head(31)))

  /**
    * 需要特别注意 vmerge/vmv 类型的指令的编码方式是一样的,
    * 区别在于vmerge是mask类型的
    * 我们不需要纠结相应的mask_bit的值,因为执行意味着它一定是1
    * 然而mask是1的情况下vmerge与vmv的行为都是一样的:都是选vs1/rs1/imm
    */
  // ["rgather", "merge", "clip", "mv", "pop", "id"]
  // 选source1的情况 todo: 需要执行的 gather 可以视为merge, 前提不读vs2
  val selectSource1: Bool = ((originalOpcodeOH(0) || originalOpcodeOH(1)) && req.mask) || originalOpcodeOH(3)
  val selectSource2: Bool = originalOpcodeOH(1) && !req.mask
  val resultSelect: UInt = VecInit(
    Seq(
      isffo,
      originalOpcodeOH(4),
      originalOpcodeOH(5),
      originalOpcodeOH(2),
      selectSource1,
      selectSource2
    )
  ).asUInt
  val popCountResult: UInt = popCount.resp + req.popInit(7, 0)
  val result: UInt = Mux1H(
    resultSelect,
    Seq(ffo.resp.bits, popCountResult, indexRes, clipResult, req.src.head, req.src(1))
  )
  resp.data := result
  resp.ffoSuccess := ffo.resp.valid && isffo
  resp.clipFail := DontCare
}
