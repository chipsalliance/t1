// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.instantiable
import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneZvk256Param {
  implicit def rw: upickle.default.ReadWriter[LaneZvk256Param] = upickle.default.macroRW
}

case class LaneZvk256Param(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val inputBundle = new LaneZvk256Request(datapathWidth) // TODO: make `datapathWidth` as 256 bits
  val decodeField: BoolField = Decoder.zvk256
  val outputBundle = new LaneZvk256Response(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvk256Request(datapathWidth: Int) extends VFUPipeBundle {
  val src    = Vec(3, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val vSew   = UInt(2.W)
  // val shifterSize = UInt(log2Ceil(datapathWidth).W)
}

class LaneZvk256Response(datapathWidth: Int) extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

@instantiable
class LaneZvk256(val parameter: LaneZvk256Param) extends VFUModule(parameter) with SerializableModule[LaneZvk256Param] {
  val response: LaneZvk256Response = Wire(new LaneZvk256Response(parameter.datapathWidth))
  val request:  LaneZvk256Request  = connectIO(response).asTypeOf(parameter.inputBundle)

  val vs1:  UInt = request.src(0)
  val vs2:  UInt = request.src(1)
  val vd:   UInt = request.src(2)
  val vSew: UInt = UIntToOH(request.vSew) // sew = 0, 1, 2

  private def rev8(x: UInt): UInt = {
    VecInit(x.asBools.grouped(8).map(s => VecInit(s)).toSeq.reverse).asUInt // element's byte reverse
  }

  private def FF1(X: UInt, Y: UInt, Z: UInt): UInt = ((X) ^ (Y) ^ (Z))

  private def FF2(X: UInt, Y: UInt, Z: UInt): UInt = (((X) & (Y)) | ((X) & (Z)) | ((Y) & (Z)))

  private def FF_j(X: UInt, Y: UInt, Z: UInt, J: UInt): UInt = {
    Mux(((J) <= 15.U), FF1(X, Y, Z), FF2(X, Y, Z))
  }

  private def GG1(X: UInt, Y: UInt, Z: UInt): UInt = ((X) ^ (Y) ^ (Z))

  private def GG2(X: UInt, Y: UInt, Z: UInt): UInt = (((X) & (Y)) | ((~(X)) & (Z)))

  private def GG_j(X: UInt, Y: UInt, Z: UInt, J: UInt): UInt = {
    Mux(((J) <= 15.U), GG1(X, Y, Z), GG2(X, Y, Z))
  }

  private def T_j(J: UInt): UInt = {
    Mux(((J) <= 15.U), ("h79CC4519".U), ("h7A879D8A".U))
  }

  private def P_0(X: UInt): UInt = {
    (X) ^
      X.rotateLeft(9) ^
      X.rotateLeft(17)
  }

  private def P_1(X: UInt):                                                UInt = {
    ((X) ^ X.rotateLeft(15)) ^ X.rotateLeft(23)
  }
  private def ZVKSH_W(M16: UInt, M9: UInt, M3: UInt, M13: UInt, M6: UInt): UInt = {
    P_1((M16) ^ (M9) ^ (M3.rotateLeft(15))) ^
      M13.rotateLeft(7) ^
      M6
  }

  val outSM3C = {
    val hi = vd(255, 224)
    val gi = vd(223, 192)
    val fi = vd(191, 160)
    val ei = vd(159, 128)
    val di = vd(127, 96)
    val ci = vd(95, 64)
    val bi = vd(63, 32)
    val ai = vd(31, 0)

    val u_w7 = vs2(255, 224)
    val u_w6 = vs2(223, 192)
    val w5i  = vs2(191, 160)
    val w4i  = vs2(159, 128)
    val u_w3 = vs2(127, 96)
    val u_w2 = vs2(95, 64)
    val w1i  = vs2(63, 32)
    val w0i  = vs2(31, 0)

    val mH = rev8(hi)
    val mG = rev8(gi)
    val mF = rev8(fi)
    val mE = rev8(ei)
    val mD = rev8(di)
    val mC = rev8(ci)
    val mB = rev8(bi)
    val mA = rev8(ai)

    val w5 = rev8(w5i)
    val w4 = rev8(w4i)
    val w1 = rev8(w1i)
    val w0 = rev8(w0i)

    val x0 = w0 ^ w4
    val x1 = w1 ^ w5

    val rnds = vs1(4, 0)
    val j    = rnds << 1
    val ss1  = (mA(31, 0).rotateLeft(12) + mE + T_j(j).rotateLeft(j(4, 0))).rotateLeft(7)
    val ss2  = ss1 ^ mA(31, 0).rotateLeft(12)
    val tt1  = FF_j(mA, mB, mC, j) + mD + ss2 + x0
    val tt2  = GG_j(mE, mF, mG, j) + mH + ss1 + w0

    val mmD = mC
    val mC1 = mB.rotateLeft(9)
    val mmB = mA

    val mA1 = tt1
    val mmH = mG
    val mG1 = mF.rotateLeft(19)
    val mmF = mE
    val mE1 = P_0(tt2)

    val j1   = (rnds << 1) + 1.U
    val mss1 = (mA1.rotateLeft(12) + mE1 + T_j(j1).rotateLeft(j(4, 0))).rotateLeft(7)
    val mss2 = mss1 ^ mA1.rotateLeft(12)

    val mtt1 = FF_j(mA1, mmB, mC1, j1) + mmD + mss2 + x1
    val mtt2 = GG_j(mE1, mmF, mG1, j1) + mmH + mss1 + w1
    val mmmD = mC1
    val mC2  = mmB.rotateLeft(9)
    val mmmB = mA1
    val mA2  = mtt1
    val mmmH = mG1
    val mG2  = mmF.rotateLeft(19)
    val mmmF = mE1
    val mE2  = P_0(mtt2)

    mG1 ## mG2 ## mE1 ## mE2 ## mC1 ## mC2 ## mA1 ## mA2
  }

  val outSM3ME = {
    val w7 = rev8(vs1(255, 224))
    val w6 = rev8(vs1(223, 192))
    val w5 = rev8(vs1(191, 160))
    val w4 = rev8(vs1(159, 128))
    val w3 = rev8(vs1(127, 96))
    val w2 = rev8(vs1(95, 64))
    val w1 = rev8(vs1(63, 32))
    val w0 = rev8(vs1(31, 0))

    val w15 = rev8(vs2(255, 224))
    val w14 = rev8(vs2(223, 192))
    val w13 = rev8(vs2(191, 160))
    val w12 = rev8(vs2(159, 128))
    val w11 = rev8(vs2(127, 96))
    val w10 = rev8(vs2(95, 64))
    val w9  = rev8(vs2(63, 32))
    val w8  = rev8(vs2(31, 0))

    val mw16 = rev8(ZVKSH_W(w0, w7, w13, w3, w10))
    val mw17 = rev8(ZVKSH_W(w1, w8, w14, w4, w11))
    val mw18 = rev8(ZVKSH_W(w2, w9, w15, w5, w12))
    val mw19 = rev8(ZVKSH_W(w3, w10, mw16, w6, w13))
    val mw20 = rev8(ZVKSH_W(w4, w11, mw17, w7, w14))
    val mw21 = rev8(ZVKSH_W(w5, w12, mw18, w8, w15))
    val mw22 = rev8(ZVKSH_W(w6, w13, mw19, w9, mw16))
    val mw23 = rev8(ZVKSH_W(w7, w14, mw20, w10, mw17))

    mw23 ## mw22 ## mw21 ## mw20 ## mw19 ## mw18 ## mw17 ## mw16
  }

  response.data := Mux1H(
    UIntToOH(request.opcode(0)),
    Seq(
      outSM3C,
      outSM3ME
    )
  )
}
