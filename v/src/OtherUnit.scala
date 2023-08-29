// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
object OtherUnitParam {
  implicit def rw: upickle.default.ReadWriter[OtherUnitParam] = upickle.default.macroRW
}
case class OtherUnitParam(
                           datapathWidth: Int,
                           vlMaxBits: Int,
                           groupNumberBits: Int,
                           laneNumberBits: Int,
                           dataPathByteWidth: Int
                         ) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.other
  val inputBundle = new OtherUnitReq(this)
  val outputBundle = new OtherUnitResp(datapathWidth)
}

class OtherUnitReq(param: OtherUnitParam) extends Bundle {
  val src:     Vec[UInt] = Vec(3, UInt(param.datapathWidth.W))
  val popInit: UInt = UInt(param.vlMaxBits.W)
  val opcode:  UInt = UInt(4.W)
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

class OtherUnit(val parameter: OtherUnitParam) extends VFUModule(parameter) with SerializableModule[OtherUnitParam] {
  val response: OtherUnitResp = Wire(new OtherUnitResp(parameter.datapathWidth))
  val request: OtherUnitReq = connectIO(response).asTypeOf(parameter.inputBundle)

  val ffo:      LaneFFO = Module(new LaneFFO(parameter.datapathWidth))
  val popCount: LanePopCount = Module(new LanePopCount(parameter.datapathWidth))
  val vSewOH:   UInt = UIntToOH(request.vSew)(2, 0)
  // ["", "", "", "", "rgather", "merge", "clip", "mv", "pop", "id"]
  val opcodeOH:         UInt = UIntToOH(request.opcode)(9, 0)
  val isffo:            Bool = opcodeOH(3, 0).orR
  val originalOpcodeOH: UInt = opcodeOH(9, 4)

  ffo.src := request.src
  ffo.resultSelect := request.opcode
  ffo.complete := request.complete
  ffo.maskType := request.maskType
  popCount.src := request.src(1) & Mux(request.maskType, request.src.head, -1.S(parameter.datapathWidth.W).asUInt)

  val signValue:  Bool = request.src(1)(parameter.datapathWidth - 1) && request.sign
  val signExtend: UInt = Fill(parameter.datapathWidth, signValue)

  // clip 2sew -> sew
  // vSew 0 -> sew = 8 => log2(sew) = 4
  val clipSize:          UInt = Mux1H(vSewOH(2, 1), Seq(false.B ## request.src.head(4), request.src.head(5, 4))) ## request.src.head(3, 0)
  val clipMask:          UInt = FillInterleaved(8, vSewOH(2) ## vSewOH(2) ## vSewOH(2, 1).orR ## true.B)
  val largestClipResult: UInt = (clipMask >> request.sign).asUInt
  val clipMaskRemainder: UInt = FillInterleaved(8, !vSewOH(2) ## !vSewOH(2) ## vSewOH(0) ## false.B)
  val roundTail:         UInt = (1.U << clipSize).asUInt
  val lostMSB:           UInt = (roundTail >> 1).asUInt
  val roundMask:         UInt = roundTail - 1.U

  // v[d - 1]
  val vds1: Bool = (lostMSB & request.src(1)).orR
  // v[d -2 : 0]
  val vLostLSB: Bool = (roundMask & request.src(1)).orR // TODO: is this WithoutMSB
  // v[d]
  val vd: Bool = (roundTail & request.src(1)).orR
  // r
  val roundR:      Bool = Mux1H(UIntToOH(request.vxrm), Seq(vds1, vds1 & (vLostLSB | vd), false.B, !vd & (vds1 | vLostLSB)))
  val roundResult: UInt = (((signExtend ## request.src(1)) >> clipSize).asUInt + roundR)(parameter.datapathWidth - 1, 0)
  val roundRemainder = roundResult & clipMaskRemainder
  val roundSignBits = Mux1H(vSewOH(2, 0), Seq(roundResult(7), roundResult(15), roundResult(31)))
  val roundResultOverlap: Bool = roundRemainder.orR && !(request.sign && (roundRemainder | clipMask).andR && roundSignBits)
  val clipResult = Mux(roundResultOverlap, largestClipResult, roundResult)

  val indexRes: UInt = ((request.groupIndex ## request.laneIndex ## request.executeIndex) >> request.vSew).asUInt

  val extendSign: Bool = request.sign && Mux1H(vSewOH, Seq(request.src.head(7), request.src.head(15), request.src.head(31)))

  /**
    * 需要特别注意 vmerge/vmv 类型的指令的编码方式是一样的,
    * 区别在于vmerge是mask类型的
    * 我们不需要纠结相应的mask_bit的值,因为执行意味着它一定是1
    * 然而mask是1的情况下vmerge与vmv的行为都是一样的:都是选vs1/rs1/imm
    */
  // ["rgather", "merge", "clip", "mv", "pop", "id"]
  // 选source1的情况 todo: 需要执行的 gather 可以视为merge, 前提不读vs2
  val selectSource1: Bool = ((originalOpcodeOH(0) || originalOpcodeOH(1)) && request.mask) || originalOpcodeOH(3)
  val selectSource2: Bool = originalOpcodeOH(1) && !request.mask
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
  val popCountResult: UInt = popCount.resp + request.popInit(7, 0)
  val result: UInt = Mux1H(
    resultSelect,
    Seq(ffo.resp.bits, popCountResult, indexRes, clipResult, request.src.head, request.src(1))
  )
  response.data := result
  response.ffoSuccess := ffo.resp.valid && isffo
  response.clipFail := DontCare
}
