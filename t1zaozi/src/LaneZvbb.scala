// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

case class LaneZvbbParam(datapathWidth: Int, latency: Int) extends Parameter:
  val shifterSizeBit: Int = log2Ceil(datapathWidth)

given upickle.default.ReadWriter[LaneZvbbParam] = upickle.default.macroRW

class LaneZvbbRequest(parameter: LaneZvbbParam) extends HWBundle(parameter):
  val tag:         BundleField[UInt]      = Aligned(UInt(4))
  val src:         BundleField[Vec[UInt]] = Aligned(Vec(3, UInt(parameter.datapathWidth)))
  val opcode:      BundleField[UInt]      = Aligned(UInt(4))
  val vSew:        BundleField[UInt]      = Aligned(UInt(2))
  val shifterSize: BundleField[UInt]      = Aligned(UInt(parameter.shifterSizeBit))

class LaneZvbbResponse(parameter: LaneZvbbParam) extends HWBundle(parameter):
  val tag:  BundleField[UInt] = Aligned(UInt(4))
  val data: BundleField[UInt] = Aligned(UInt(parameter.datapathWidth))

class LaneZvbbInterface(parameter: LaneZvbbParam) extends HWBundle(parameter):
  val clock:      BundleField[Clock]                         = Flipped(Clock())
  val reset:      BundleField[Reset]                         = Flipped(Reset())
  val requestIO:  BundleField[DecoupledIO[LaneZvbbRequest]]  = Flipped(Decoupled(new LaneZvbbRequest(parameter)))
  val responseIO: BundleField[DecoupledIO[LaneZvbbResponse]] =
    Aligned(Decoupled(new LaneZvbbResponse(parameter)))

class LaneZvbbLayers(parameter: LaneZvbbParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class LaneZvbbProbe(parameter: LaneZvbbParam) extends DVBundle[LaneZvbbParam, LaneZvbbLayers](parameter)

@generator
object LaneZvbb extends Generator[LaneZvbbParam, LaneZvbbLayers, LaneZvbbInterface, LaneZvbbProbe]:
  override def moduleName(parameter: LaneZvbbParam): String = "LaneZvbb"

  def architecture(parameter: LaneZvbbParam) =
    val p  = parameter
    val io = summon[Interface[LaneZvbbInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    // SingleCycleVFU pipeline
    io.requestIO.ready := true.B

    val requestRegValid = RegInit(false.B)
    requestRegValid := io.requestIO.valid & io.requestIO.ready

    val reqType    = new LaneZvbbRequest(p)
    val requestReg = RegInit(0.B(reqType.width).asType(reqType))
    when(io.requestIO.valid & io.requestIO.ready):
      requestReg := io.requestIO.bits

    val response = Wire(new LaneZvbbResponse(p))
    response.tag := requestReg.tag

    val zvbbSrc = requestReg.src(1)         // vs2
    val zvbbRs  = requestReg.src(0)         // vs1 or rs1
    val vSew    = UIntToOH(requestReg.vSew) // sew = 0, 1, 2

    // brev: element's bit reverse (## puts left at MSB, so bit(i) ascending gives bit(0) at MSB)
    val zvbbBRev  = Seq
      .tabulate(p.datapathWidth)(i => zvbbSrc.asBits.bit(i).asBits)
      .reduce((a: Referable[Bits], b: Referable[Bits]) => a ## b)
      .asUInt
    // brev8: byte's bit reverse (reverse outer to keep byte positions, ascending inner to reverse bits within each byte)
    val zvbbBRev8 = Seq
      .tabulate(p.datapathWidth / 8) { byteIdx =>
        Seq
          .tabulate(8)(i => zvbbSrc.asBits.bit(byteIdx * 8 + i).asBits)
          .reduce((a: Referable[Bits], b: Referable[Bits]) => a ## b)
      }
      .reverse
      .reduce((a: Referable[Bits], b: Referable[Bits]) => a ## b)
      .asUInt
    // rev8: element's byte reverse (ascending byte index puts byte0 at MSB)
    val zvbbRev8  = Seq
      .tabulate(p.datapathWidth / 8) { byteIdx =>
        zvbbSrc.asBits.bits(byteIdx * 8 + 7, byteIdx * 8)
      }
      .reduce(_ ## _)
      .asUInt

    val zvbbSrc16a = zvbbSrc.asBits.bits(p.datapathWidth - 1, p.datapathWidth - 16).asUInt
    val zvbbSrc16b = zvbbSrc.asBits.bits(p.datapathWidth - 17, p.datapathWidth - 32).asUInt
    val zvbbSrc8a  = zvbbSrc.asBits.bits(p.datapathWidth - 1, p.datapathWidth - 8).asUInt
    val zvbbSrc8b  = zvbbSrc.asBits.bits(p.datapathWidth - 9, p.datapathWidth - 16).asUInt
    val zvbbSrc8c  = zvbbSrc.asBits.bits(p.datapathWidth - 17, p.datapathWidth - 24).asUInt
    val zvbbSrc8d  = zvbbSrc.asBits.bits(p.datapathWidth - 25, p.datapathWidth - 32).asUInt

    val zvbbRs16a = zvbbRs.asBits.bits(p.datapathWidth - 1, p.datapathWidth - 16).asUInt
    val zvbbRs16b = zvbbRs.asBits.bits(p.datapathWidth - 17, p.datapathWidth - 32).asUInt
    val zvbbRs8a  = zvbbRs.asBits.bits(p.datapathWidth - 1, p.datapathWidth - 8).asUInt
    val zvbbRs8b  = zvbbRs.asBits.bits(p.datapathWidth - 9, p.datapathWidth - 16).asUInt
    val zvbbRs8c  = zvbbRs.asBits.bits(p.datapathWidth - 17, p.datapathWidth - 24).asUInt
    val zvbbRs8d  = zvbbRs.asBits.bits(p.datapathWidth - 25, p.datapathWidth - 32).asUInt

    val zero32 = 0.B(32)
    val zero16 = 0.B(16)
    val zero10 = 0.B(11)
    val zero8  = 0.B(8)
    val zero3  = 0.B(4)

    // CLZ (count leading zeros)
    val zvbbCLZ32 = (32.U - popCount(scanRightOr(zvbbSrc))).asBits.bits(5, 0).asUInt
    val zvbbCLZ16 =
      val clz16a = (16.U - popCount(scanRightOr(zvbbSrc16a))).asBits.bits(4, 0).asUInt
      val clz16b = (16.U - popCount(scanRightOr(zvbbSrc16b))).asBits.bits(4, 0).asUInt
      (zero10 ## clz16a.asBits ## zero10 ## clz16b.asBits).asUInt
    val zvbbCLZ8  =
      val clz8a = (8.U - popCount(scanRightOr(zvbbSrc8a))).asBits.bits(3, 0).asUInt
      val clz8b = (8.U - popCount(scanRightOr(zvbbSrc8b))).asBits.bits(3, 0).asUInt
      val clz8c = (8.U - popCount(scanRightOr(zvbbSrc8c))).asBits.bits(3, 0).asUInt
      val clz8d = (8.U - popCount(scanRightOr(zvbbSrc8d))).asBits.bits(3, 0).asUInt
      (zero3 ## clz8a.asBits ## zero3 ## clz8b.asBits ## zero3 ## clz8c.asBits ## zero3 ## clz8d.asBits).asUInt
    val zvbbCLZ   = mux1H(
      vSew,
      Seq(zvbbCLZ8, zvbbCLZ16, zvbbCLZ32)
    )

    // CTZ (count trailing zeros)
    val zvbbCTZ32 = (32.U - popCount(scanLeftOr(zvbbSrc))).asBits.bits(5, 0).asUInt
    val zvbbCTZ16 =
      val ctz16a = (16.U - popCount(scanLeftOr(zvbbSrc16a))).asBits.bits(4, 0).asUInt
      val ctz16b = (16.U - popCount(scanLeftOr(zvbbSrc16b))).asBits.bits(4, 0).asUInt
      (zero10 ## ctz16a.asBits ## zero10 ## ctz16b.asBits).asUInt
    val zvbbCTZ8  =
      val ctz8a = (8.U - popCount(scanLeftOr(zvbbSrc8a))).asBits.bits(3, 0).asUInt
      val ctz8b = (8.U - popCount(scanLeftOr(zvbbSrc8b))).asBits.bits(3, 0).asUInt
      val ctz8c = (8.U - popCount(scanLeftOr(zvbbSrc8c))).asBits.bits(3, 0).asUInt
      val ctz8d = (8.U - popCount(scanLeftOr(zvbbSrc8d))).asBits.bits(3, 0).asUInt
      (zero3 ## ctz8a.asBits ## zero3 ## ctz8b.asBits ## zero3 ## ctz8c.asBits ## zero3 ## ctz8d.asBits).asUInt
    val zvbbCTZ   = mux1H(
      vSew,
      Seq(zvbbCTZ8, zvbbCTZ16, zvbbCTZ32)
    )

    // ROL (rotate left)
    val zvbbROL32 = rotateLeft(zvbbSrc, zvbbRs.asBits.bits(4, 0).asUInt, 32)
    val zvbbROL16 =
      val rol16a = rotateLeft(zvbbSrc16a, zvbbRs16a.asBits.bits(3, 0).asUInt, 16).asBits.bits(15, 0).asUInt
      val rol16b = rotateLeft(zvbbSrc16b, zvbbRs16b.asBits.bits(3, 0).asUInt, 16).asBits.bits(15, 0).asUInt
      (rol16a.asBits ## rol16b.asBits).asUInt
    val zvbbROL8  =
      val rol8a = rotateLeft(zvbbSrc8a, zvbbRs8a.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      val rol8b = rotateLeft(zvbbSrc8b, zvbbRs8b.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      val rol8c = rotateLeft(zvbbSrc8c, zvbbRs8c.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      val rol8d = rotateLeft(zvbbSrc8d, zvbbRs8d.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      (rol8a.asBits ## rol8b.asBits ## rol8c.asBits ## rol8d.asBits).asUInt
    val zvbbROL   = mux1H(
      vSew,
      Seq(zvbbROL8, zvbbROL16, zvbbROL32)
    )

    // ROR (rotate right)
    val zvbbROR32 = rotateRight(zvbbSrc, zvbbRs.asBits.bits(4, 0).asUInt, 32)
    val zvbbROR16 =
      val ror16a = rotateRight(zvbbSrc16a, zvbbRs16a.asBits.bits(3, 0).asUInt, 16).asBits.bits(15, 0).asUInt
      val ror16b = rotateRight(zvbbSrc16b, zvbbRs16b.asBits.bits(3, 0).asUInt, 16).asBits.bits(15, 0).asUInt
      (ror16a.asBits ## ror16b.asBits).asUInt
    val zvbbROR8  =
      val ror8a = rotateRight(zvbbSrc8a, zvbbRs8a.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      val ror8b = rotateRight(zvbbSrc8b, zvbbRs8b.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      val ror8c = rotateRight(zvbbSrc8c, zvbbRs8c.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      val ror8d = rotateRight(zvbbSrc8d, zvbbRs8d.asBits.bits(2, 0).asUInt, 8).asBits.bits(7, 0).asUInt
      (ror8a.asBits ## ror8b.asBits ## ror8c.asBits ## ror8d.asBits).asUInt
    val zvbbROR   = mux1H(
      vSew,
      Seq(zvbbROR8, zvbbROR16, zvbbROR32)
    )

    // SLL64
    val zvbbSLL64_32 =
      ((zero32 ## zvbbSrc.asBits).asUInt << zvbbRs.asBits.bits(4, 0).asUInt).asBits.bits(31, 0).asUInt
    val zvbbSLL64_16 =
      val sll64_16a =
        ((zero16 ## zvbbSrc16a.asBits).asUInt << zvbbRs16a.asBits.bits(3, 0).asUInt).asBits.bits(15, 0).asUInt
      val sll64_16b =
        ((zero16 ## zvbbSrc16b.asBits).asUInt << zvbbRs16b.asBits.bits(3, 0).asUInt).asBits.bits(15, 0).asUInt
      (sll64_16a.asBits ## sll64_16b.asBits).asUInt
    val zvbbSLL64_8  =
      val sll64_8a =
        ((zero8 ## zvbbSrc8a.asBits).asUInt << zvbbRs8a.asBits.bits(2, 0).asUInt).asBits.bits(7, 0).asUInt
      val sll64_8b =
        ((zero8 ## zvbbSrc8b.asBits).asUInt << zvbbRs8b.asBits.bits(2, 0).asUInt).asBits.bits(7, 0).asUInt
      val sll64_8c =
        ((zero8 ## zvbbSrc8c.asBits).asUInt << zvbbRs8c.asBits.bits(2, 0).asUInt).asBits.bits(7, 0).asUInt
      val sll64_8d =
        ((zero8 ## zvbbSrc8d.asBits).asUInt << zvbbRs8d.asBits.bits(2, 0).asUInt).asBits.bits(7, 0).asUInt
      (sll64_8a.asBits ## sll64_8b.asBits ## sll64_8c.asBits ## sll64_8d.asBits).asUInt
    val zvbbSLL64    = mux1H(
      vSew,
      Seq(zvbbSLL64_8, zvbbSLL64_16, zvbbSLL64_32)
    )
    val zvbbSLL      = zvbbSLL64.asBits.bits(p.datapathWidth - 1, 0).asUInt

    // ANDN
    val zvbbANDN = (zvbbSrc.asBits & (~zvbbRs.asBits)).asUInt

    // POPCOUNT
    val zvbbPOP =
      val zvbbPOP8a = (0.B(4) ## popCount(zvbbSrc8a).asBits.bits(3, 0)).asUInt
      val zvbbPOP8b = (0.B(4) ## popCount(zvbbSrc8b).asBits.bits(3, 0)).asUInt
      val zvbbPOP8c = (0.B(4) ## popCount(zvbbSrc8c).asBits.bits(3, 0)).asUInt
      val zvbbPOP8d = (0.B(4) ## popCount(zvbbSrc8d).asBits.bits(3, 0)).asUInt
      mux1H(
        vSew,
        Seq(
          (zvbbPOP8a.asBits ## zvbbPOP8b.asBits ## zvbbPOP8c.asBits ## zvbbPOP8d.asBits).asUInt,
          (0.B(8) ## (zvbbPOP8a + zvbbPOP8b).asBits.bits(7, 0) ## 0.B(8) ## (zvbbPOP8c + zvbbPOP8d).asBits
            .bits(7, 0)).asUInt,
          (0.B(24) ## (zvbbPOP8a + zvbbPOP8b + zvbbPOP8c + zvbbPOP8d).asBits.bits(7, 0)).asUInt
        )
      )

    response.data := mux1H(
      UIntToOH(requestReg.opcode),
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

    // Pipe the response (valid gates data writes, matching Chisel Pipe)
    val (pipedValid, pipedData) = pipeValidData(requestRegValid, response.asBits.asUInt, p.latency)

    io.responseIO.valid := pipedValid
    io.responseIO.bits  := pipedData.asBits.asType(new LaneZvbbResponse(p))
