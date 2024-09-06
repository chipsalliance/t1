// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.instantiable
import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneZvbbParam {
  implicit def rw: upickle.default.ReadWriter[LaneZvbbParam] = upickle.default.macroRW
}

case class LaneZvbbParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val inputBundle = new LaneZvbbRequest(datapathWidth)
  val decodeField: BoolField = Decoder.zvbb
  val outputBundle = new LaneZvbbResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvbbRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src         = Vec(3, UInt(datapathWidth.W))
  val opcode      = UInt(4.W)
  val vSew        = UInt(2.W)
  val shifterSize = UInt(log2Ceil(datapathWidth).W)
}

class LaneZvbbResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

@instantiable
class LaneZvbb(val parameter: LaneZvbbParam) extends VFUModule(parameter) with SerializableModule[LaneZvbbParam] {
  val response: LaneZvbbResponse = Wire(new LaneZvbbResponse(parameter.datapathWidth))
  val request:  LaneZvbbRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val zvbbSrc: UInt = request.src(1)         // vs2
  val zvbbRs:  UInt = request.src(0)         // vs1 or rs1
  val vSew:    UInt = UIntToOH(request.vSew) // sew = 0, 1, 2

  val zvbbBRev  = VecInit(zvbbSrc.asBools.reverse).asUInt                                       // element's bit reverse
  val zvbbBRev8 = VecInit(zvbbSrc.asBools.grouped(8).map(s => VecInit(s.reverse)).toSeq).asUInt // byte's bit reverse
  val zvbbRev8  = VecInit(zvbbSrc.asBools.grouped(8).map(s => VecInit(s)).toSeq.reverse).asUInt // element's byte reverse

  val zvbbSrc16a = zvbbSrc(parameter.datapathWidth - 1, parameter.datapathWidth - 16)
  val zvbbSrc16b = zvbbSrc(parameter.datapathWidth - 17, parameter.datapathWidth - 32)
  val zvbbSrc8a  = zvbbSrc(parameter.datapathWidth - 1, parameter.datapathWidth - 8)
  val zvbbSrc8b  = zvbbSrc(parameter.datapathWidth - 9, parameter.datapathWidth - 16)
  val zvbbSrc8c  = zvbbSrc(parameter.datapathWidth - 17, parameter.datapathWidth - 24)
  val zvbbSrc8d  = zvbbSrc(parameter.datapathWidth - 25, parameter.datapathWidth - 32)

  val zvbbRs16a = zvbbRs(parameter.datapathWidth - 1, parameter.datapathWidth - 16)
  val zvbbRs16b = zvbbRs(parameter.datapathWidth - 17, parameter.datapathWidth - 32)
  val zvbbRs8a  = zvbbRs(parameter.datapathWidth - 1, parameter.datapathWidth - 8)
  val zvbbRs8b  = zvbbRs(parameter.datapathWidth - 9, parameter.datapathWidth - 16)
  val zvbbRs8c  = zvbbRs(parameter.datapathWidth - 17, parameter.datapathWidth - 24)
  val zvbbRs8d  = zvbbRs(parameter.datapathWidth - 25, parameter.datapathWidth - 32)

  val zero32: UInt = 0.U(32.W)
  val zero16: UInt = 0.U(16.W)
  val zero10: UInt = 0.U(11.W)
  val zero8:  UInt = 0.U(8.W)
  val zero3:  UInt = 0.U(4.W)

  val zvbbCLZ32: UInt = (32.U - PopCount(scanRightOr(zvbbSrc))).asUInt
  val zvbbCLZ16: UInt = {
    val clz16a: UInt = (16.U - PopCount(scanRightOr(zvbbSrc16a))).asUInt(4, 0)
    val clz16b: UInt = (16.U - PopCount(scanRightOr(zvbbSrc16b))).asUInt(4, 0)
    zero10 ## clz16a ## zero10 ## clz16b
  }
  val zvbbCLZ8:  UInt = {
    val clz8a: UInt = (8.U - PopCount(scanRightOr(zvbbSrc8a))).asUInt(3, 0)
    val clz8b: UInt = (8.U - PopCount(scanRightOr(zvbbSrc8b))).asUInt(3, 0)
    val clz8c: UInt = (8.U - PopCount(scanRightOr(zvbbSrc8c))).asUInt(3, 0)
    val clz8d: UInt = (8.U - PopCount(scanRightOr(zvbbSrc8d))).asUInt(3, 0)
    zero3 ## clz8a ## zero3 ## clz8b ## zero3 ## clz8c ## zero3 ## clz8d
  }
  val zvbbCLZ:   UInt = Mux1H(
    vSew,
    Seq(
      zvbbCLZ8,
      zvbbCLZ16,
      zvbbCLZ32
    )
  )

  val zvbbCTZ32 = (32.U - PopCount(scanLeftOr(zvbbSrc))).asUInt
  val zvbbCTZ16: UInt = {
    val ctz16a: UInt = (16.U - PopCount(scanLeftOr(zvbbSrc16a))).asUInt(4, 0)
    val ctz16b: UInt = (16.U - PopCount(scanLeftOr(zvbbSrc16b))).asUInt(4, 0)
    zero10 ## ctz16a ## zero10 ## ctz16b
  }
  val zvbbCTZ8:  UInt = {
    val ctz8a: UInt = (8.U - PopCount(scanLeftOr(zvbbSrc8a))).asUInt(3, 0)
    val ctz8b: UInt = (8.U - PopCount(scanLeftOr(zvbbSrc8b))).asUInt(3, 0)
    val ctz8c: UInt = (8.U - PopCount(scanLeftOr(zvbbSrc8c))).asUInt(3, 0)
    val ctz8d: UInt = (8.U - PopCount(scanLeftOr(zvbbSrc8d))).asUInt(3, 0)
    zero3 ## ctz8a ## zero3 ## ctz8b ## zero3 ## ctz8c ## zero3 ## ctz8d
  }
  val zvbbCTZ = Mux1H(
    vSew,
    Seq(
      zvbbCTZ8,
      zvbbCTZ16,
      zvbbCTZ32
    )
  )

  val zvbbROL32 = zvbbSrc.rotateLeft(zvbbRs(4, 0)).asUInt
  val zvbbROL16: UInt = {
    val rol16a = zvbbSrc16a.rotateLeft(zvbbRs16a(3, 0)).asUInt(15, 0)
    val rol16b = zvbbSrc16b.rotateLeft(zvbbRs16b(3, 0)).asUInt(15, 0)
    rol16a ## rol16b
  }
  val zvbbROL8:  UInt = {
    val rol8a = zvbbSrc8a.rotateLeft(zvbbRs8a(2, 0)).asUInt(7, 0)
    val rol8b = zvbbSrc8b.rotateLeft(zvbbRs8b(2, 0)).asUInt(7, 0)
    val rol8c = zvbbSrc8c.rotateLeft(zvbbRs8c(2, 0)).asUInt(7, 0)
    val rol8d = zvbbSrc8d.rotateLeft(zvbbRs8d(2, 0)).asUInt(7, 0)
    rol8a ## rol8b ## rol8c ## rol8d
  }
  val zvbbROL = Mux1H(
    vSew,
    Seq(
      zvbbROL8,
      zvbbROL16,
      zvbbROL32
    )
  )

  val zvbbROR32 = zvbbSrc.rotateRight(zvbbRs(4, 0)).asUInt
  val zvbbROR16: UInt = {
    val ror16a = zvbbSrc16a.rotateRight(zvbbRs16a(3, 0)).asUInt(15, 0)
    val ror16b = zvbbSrc16b.rotateRight(zvbbRs16b(3, 0)).asUInt(15, 0)
    ror16a ## ror16b
  }
  val zvbbROR8:  UInt = {
    val ror8a = zvbbSrc8a.rotateRight(zvbbRs8a(2, 0)).asUInt(7, 0)
    val ror8b = zvbbSrc8b.rotateRight(zvbbRs8b(2, 0)).asUInt(7, 0)
    val ror8c = zvbbSrc8c.rotateRight(zvbbRs8c(2, 0)).asUInt(7, 0)
    val ror8d = zvbbSrc8d.rotateRight(zvbbRs8d(2, 0)).asUInt(7, 0)
    ror8a ## ror8b ## ror8c ## ror8d
  }
  val zvbbROR = Mux1H(
    vSew,
    Seq(
      zvbbROR8,
      zvbbROR16,
      zvbbROR32
    )
  )

  val zvbbSLL64_32 = ((zero32 ## zvbbSrc).asUInt << zvbbRs(4, 0)).asUInt(31, 0)
  val zvbbSLL64_16: UInt = {
    val sll64_16a = ((zero16 ## zvbbSrc16a).asUInt << zvbbRs16a(3, 0)).asUInt(15, 0)
    val sll64_16b = ((zero16 ## zvbbSrc16b).asUInt << zvbbRs16b(3, 0)).asUInt(15, 0)
    sll64_16a ## sll64_16b
  }
  val zvbbSLL64_8:  UInt = {
    val sll64_8a = ((zero8 ## zvbbSrc8a).asUInt << zvbbRs8a(2, 0)).asUInt(7, 0)
    val sll64_8b = ((zero8 ## zvbbSrc8b).asUInt << zvbbRs8b(2, 0)).asUInt(7, 0)
    val sll64_8c = ((zero8 ## zvbbSrc8c).asUInt << zvbbRs8c(2, 0)).asUInt(7, 0)
    val sll64_8d = ((zero8 ## zvbbSrc8d).asUInt << zvbbRs8d(2, 0)).asUInt(7, 0)
    sll64_8a ## sll64_8b ## sll64_8c ## sll64_8d
  }
  val zvbbSLL64 = Mux1H(
    vSew,
    Seq(
      zvbbSLL64_8,
      zvbbSLL64_16,
      zvbbSLL64_32
    )
  )
  val zvbbSLL = zvbbSLL64(parameter.datapathWidth - 1, 0)

  val zvbbANDN = zvbbSrc & (~zvbbRs)

  val zvbbPOP = {
    val zvbbPOP8a = 0.U(4.W) ## PopCount(zvbbSrc8a).asUInt(3, 0)
    val zvbbPOP8b = 0.U(4.W) ## PopCount(zvbbSrc8b).asUInt(3, 0)
    val zvbbPOP8c = 0.U(4.W) ## PopCount(zvbbSrc8c).asUInt(3, 0)
    val zvbbPOP8d = 0.U(4.W) ## PopCount(zvbbSrc8d).asUInt(3, 0)
    Mux1H(
      vSew,
      Seq(
        zvbbPOP8a ## zvbbPOP8b ## zvbbPOP8c ## zvbbPOP8d,
        0.U(8.W) ## (zvbbPOP8a + zvbbPOP8b).asUInt(7, 0) ## 0.U(8.W) ## (zvbbPOP8c + zvbbPOP8d).asUInt(7, 0),
        0.U(24.W) ## (zvbbPOP8a + zvbbPOP8b + zvbbPOP8c + zvbbPOP8d).asUInt(7, 0)
      )
    )
  }

  response.data := Mux1H(
    UIntToOH(request.opcode),
    Seq(
      zvbbBRev,
      zvbbBRev8,
      zvbbRev8,
      zvbbCLZ,
      zvbbCTZ,
      zvbbROL,
      zvbbROR,
      zvbbSLL,
      zvbbANDN,
      zvbbPOP
    )
  )
}
