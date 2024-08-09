// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3.experimental.hierarchy.instantiable
import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.t1.rtl.decoder.{BoolField, Decoder}

object LaneZvkParam {
  implicit def rw: upickle.default.ReadWriter[LaneZvkParam] = upickle.default.macroRW
}

case class LaneZvkParam(datapathWidth: Int, latency: Int) extends VFUParameter with SerializableModuleParameter {
  val inputBundle = new LaneZvkRequest(datapathWidth) // TODO: make `datapathWidth` as 128 bits
  val decodeField: BoolField = Decoder.zvk128
  val outputBundle = new LaneZvkResponse(datapathWidth)
  override val NeedSplit: Boolean = false
}

class LaneZvkRequest(datapathWidth: Int) extends VFUPipeBundle {
  val src    = Vec(3, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val vSew   = UInt(2.W)
  // val shifterSize = UInt(log2Ceil(datapathWidth).W)
}

class LaneZvkResponse(datapathWidth: Int) extends VFUPipeBundle {
  val data = UInt(datapathWidth.W)
}

@instantiable
class LaneZvk(val parameter: LaneZvkParam) extends VFUModule(parameter) with SerializableModule[LaneZvkParam] {
  val response: LaneZvkResponse = Wire(new LaneZvkResponse(parameter.datapathWidth))
  val request:  LaneZvkRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val vs1:  UInt = request.src(0)         // vs1 / rs1 / uimm
  val vs2:  UInt = request.src(1)
  val vd:   UInt = request.src(2)
  val vSew: UInt = UIntToOH(request.vSew) // sew = 0, 1, 2

  private def UInt2BRev8(x: UInt): UInt = VecInit(
    x.asBools.grouped(8).map(s => VecInit(s.reverse)).toSeq
  ).asUInt // byte's bit reverse

  private def aes_get_column(x: UInt, y: UInt): UInt = {
    Mux1H(
      UIntToOH(y),
      Seq(
        x(31, 0),
        x(63, 32),
        x(95, 64),
        x(127, 96)
      )
    )
  }

  private def aes_shift_rows_inv(x: UInt): UInt = {
    val ic3 = aes_get_column(x, 3.U)
    val ic2 = aes_get_column(x, 2.U)
    val ic1 = aes_get_column(x, 1.U)
    val ic0 = aes_get_column(x, 0.U)
    val oc0 = ic1(31, 24) ## ic2(23, 16) ## ic3(15, 8) ## ic0(7, 0)
    val oc1 = ic2(31, 24) ## ic3(23, 16) ## ic0(15, 8) ## ic1(7, 0)
    val oc2 = ic3(31, 24) ## ic0(23, 16) ## ic1(15, 8) ## ic2(7, 0)
    val oc3 = ic0(31, 24) ## ic1(23, 16) ## ic2(15, 8) ## ic3(7, 0)
    oc3 ## oc2 ## oc1 ## oc0
  }

  val aes_sbox_inv_table:               Vec[UInt] = {
    val tmp = Seq(
      0x52, 0x09, 0x6a, 0xd5, 0x30, 0x36, 0xa5, 0x38, 0xbf, 0x40, 0xa3, 0x9e, 0x81, 0xf3, 0xd7, 0xfb, 0x7c, 0xe3, 0x39,
      0x82, 0x9b, 0x2f, 0xff, 0x87, 0x34, 0x8e, 0x43, 0x44, 0xc4, 0xde, 0xe9, 0xcb, 0x54, 0x7b, 0x94, 0x32, 0xa6, 0xc2,
      0x23, 0x3d, 0xee, 0x4c, 0x95, 0x0b, 0x42, 0xfa, 0xc3, 0x4e, 0x08, 0x2e, 0xa1, 0x66, 0x28, 0xd9, 0x24, 0xb2, 0x76,
      0x5b, 0xa2, 0x49, 0x6d, 0x8b, 0xd1, 0x25, 0x72, 0xf8, 0xf6, 0x64, 0x86, 0x68, 0x98, 0x16, 0xd4, 0xa4, 0x5c, 0xcc,
      0x5d, 0x65, 0xb6, 0x92, 0x6c, 0x70, 0x48, 0x50, 0xfd, 0xed, 0xb9, 0xda, 0x5e, 0x15, 0x46, 0x57, 0xa7, 0x8d, 0x9d,
      0x84, 0x90, 0xd8, 0xab, 0x00, 0x8c, 0xbc, 0xd3, 0x0a, 0xf7, 0xe4, 0x58, 0x05, 0xb8, 0xb3, 0x45, 0x06, 0xd0, 0x2c,
      0x1e, 0x8f, 0xca, 0x3f, 0x0f, 0x02, 0xc1, 0xaf, 0xbd, 0x03, 0x01, 0x13, 0x8a, 0x6b, 0x3a, 0x91, 0x11, 0x41, 0x4f,
      0x67, 0xdc, 0xea, 0x97, 0xf2, 0xcf, 0xce, 0xf0, 0xb4, 0xe6, 0x73, 0x96, 0xac, 0x74, 0x22, 0xe7, 0xad, 0x35, 0x85,
      0xe2, 0xf9, 0x37, 0xe8, 0x1c, 0x75, 0xdf, 0x6e, 0x47, 0xf1, 0x1a, 0x71, 0x1d, 0x29, 0xc5, 0x89, 0x6f, 0xb7, 0x62,
      0x0e, 0xaa, 0x18, 0xbe, 0x1b, 0xfc, 0x56, 0x3e, 0x4b, 0xc6, 0xd2, 0x79, 0x20, 0x9a, 0xdb, 0xc0, 0xfe, 0x78, 0xcd,
      0x5a, 0xf4, 0x1f, 0xdd, 0xa8, 0x33, 0x88, 0x07, 0xc7, 0x31, 0xb1, 0x12, 0x10, 0x59, 0x27, 0x80, 0xec, 0x5f, 0x60,
      0x51, 0x7f, 0xa9, 0x19, 0xb5, 0x4a, 0x0d, 0x2d, 0xe5, 0x7a, 0x9f, 0x93, 0xc9, 0x9c, 0xef, 0xa0, 0xe0, 0x3b, 0x4d,
      0xae, 0x2a, 0xf5, 0xb0, 0xc8, 0xeb, 0xbb, 0x3c, 0x83, 0x53, 0x99, 0x61, 0x17, 0x2b, 0x04, 0x7e, 0xba, 0x77, 0xd6,
      0x26, 0xe1, 0x69, 0x14, 0x63, 0x55, 0x21, 0x0c, 0x7d
    )
    VecInit(tmp.map { s => s.asUInt(8.W) })
  }
  private def inv_sbox_lookup(x: UInt): UInt      = {
    Mux(x === 0.U, aes_sbox_inv_table(0.U), aes_sbox_inv_table(x - 1.U))
  }

  val aes_sbox_fwd_table: Vec[UInt] = {
    val tmp = Seq(
      0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76, 0xca, 0x82, 0xc9,
      0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0, 0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f,
      0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15, 0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07,
      0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75, 0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3,
      0x29, 0xe3, 0x2f, 0x84, 0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58,
      0xcf, 0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8, 0x51, 0xa3,
      0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2, 0xcd, 0x0c, 0x13, 0xec, 0x5f,
      0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73, 0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88,
      0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb, 0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac,
      0x62, 0x91, 0x95, 0xe4, 0x79, 0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a,
      0xae, 0x08, 0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a, 0x70,
      0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e, 0xe1, 0xf8, 0x98, 0x11,
      0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf, 0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42,
      0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16
    )
    VecInit(tmp.map { s => s.asUInt(8.W) })
  }

  private def sbox_lookup(x: UInt): UInt = {
    Mux(x === 0.U, aes_sbox_fwd_table(0.U), aes_sbox_fwd_table(x - 1.U))
  }

  private def aes_subword_inv(x: UInt): UInt = {
    inv_sbox_lookup(x(31, 24)) ##
      inv_sbox_lookup(x(23, 16)) ##
      inv_sbox_lookup(x(15, 8)) ##
      inv_sbox_lookup(x(7, 0))
  }

  private def aes_subbytes_inv(x: UInt): UInt = {
    val ic0 = aes_get_column(x, 0.U)
    val ic1 = aes_get_column(x, 1.U)
    val ic2 = aes_get_column(x, 2.U)
    val ic3 = aes_get_column(x, 3.U)

    val oc0 = aes_subword_inv(ic0)
    val oc1 = aes_subword_inv(ic1)
    val oc2 = aes_subword_inv(ic2)
    val oc3 = aes_subword_inv(ic3)

    oc3 ## oc2 ## oc1 ## oc0
  }

  private def xt2(x: UInt): UInt = {
    (x << 1) ^ Mux(x(7) === 1.U, "h1b".U, 0.U)
  }

  private def xt3(x: UInt): UInt = {
    x ^ xt2(x)
  }

  private def gfmul(x: UInt, y: UInt): UInt = {
    Mux(y(0) === 1.U, x, 0.U) ^
      Mux(y(1) === 1.U, xt2(x), 0.U) ^
      Mux(y(2) === 1.U, xt2(xt2(x)), 0.U) ^
      Mux(y(3) === 1.U, xt2(xt2(xt2(x))), 0.U)
  }

  private def aes_mixcolumn_inv(x: UInt): UInt = {
    val s0 = x(7, 0)
    val s1 = x(15, 8)
    val s2 = x(23, 16)
    val s3 = x(31, 24)

    val b0 = gfmul(s0, "hE".U) ^ gfmul(s1, "hB".U) ^ gfmul(s2, "hD".U) ^ gfmul(s3, "h9".U)
    val b1 = gfmul(s0, "h9".U) ^ gfmul(s1, "hE".U) ^ gfmul(s2, "hB".U) ^ gfmul(s3, "hD".U)
    val b2 = gfmul(s0, "hD".U) ^ gfmul(s1, "h9".U) ^ gfmul(s2, "hE".U) ^ gfmul(s3, "hB".U)
    val b3 = gfmul(s0, "hB".U) ^ gfmul(s1, "hD".U) ^ gfmul(s2, "h9".U) ^ gfmul(s3, "hE".U)

    b3 ## b2 ## b1 ## b0
  }

  private def aes_mixcolumns_inv(x: UInt): UInt = {
    val ic0 = aes_get_column(x, 0.U)
    val ic1 = aes_get_column(x, 1.U)
    val ic2 = aes_get_column(x, 2.U)
    val ic3 = aes_get_column(x, 3.U)

    val oc0 = aes_mixcolumn_inv(ic0)
    val oc1 = aes_mixcolumn_inv(ic1)
    val oc2 = aes_mixcolumn_inv(ic2)
    val oc3 = aes_mixcolumn_inv(ic3)

    oc3 ## oc2 ## oc1 ## oc0
  }

  private def aes_subword_fwd(x: UInt): UInt = {
    sbox_lookup(x(31, 24)) ##
      sbox_lookup(x(23, 16)) ##
      sbox_lookup(x(15, 8)) ##
      sbox_lookup(x(7, 0))
  }

  private def aes_rotword(x: UInt):      UInt = {
    val a0 = x(7, 0)
    val a1 = x(15, 8)
    val a2 = x(23, 16)
    val a3 = x(31, 24)

    a0 ## a3 ## a2 ## a1
  }
  private def aes_subbytes_fwd(x: UInt): UInt = {
    val ic0 = aes_get_column(x, 0.U)
    val ic1 = aes_get_column(x, 1.U)
    val ic2 = aes_get_column(x, 2.U)
    val ic3 = aes_get_column(x, 3.U)

    val oc0 = aes_subword_fwd(ic0)
    val oc1 = aes_subword_fwd(ic1)
    val oc2 = aes_subword_fwd(ic2)
    val oc3 = aes_subword_fwd(ic3)

    oc3 ## oc2 ## oc1 ## oc0
  }

  private def aes_shift_rows_fwd(x: UInt): UInt = {
    val ic0 = aes_get_column(x, 0.U)
    val ic1 = aes_get_column(x, 1.U)
    val ic2 = aes_get_column(x, 2.U)
    val ic3 = aes_get_column(x, 3.U)

    val oc0 = ic3(31, 24) ## ic2(23, 16) ## ic1(15, 8) ## ic0(7, 0);
    val oc1 = ic0(31, 24) ## ic3(23, 16) ## ic2(15, 8) ## ic1(7, 0);
    val oc2 = ic1(31, 24) ## ic0(23, 16) ## ic3(15, 8) ## ic2(7, 0);
    val oc3 = ic2(31, 24) ## ic1(23, 16) ## ic0(15, 8) ## ic3(7, 0);

    oc3 ## oc2 ## oc1 ## oc0
  }

  private def aes_mixcolumn_fwd(x: UInt): UInt = {
    val s0 = x(7, 0)
    val s1 = x(15, 8)
    val s2 = x(23, 16)
    val s3 = x(31, 24)
    val b0 = xt2(s0) ^ xt3(s1) ^ (s2) ^ (s3)
    val b1 = (s0) ^ xt2(s1) ^ xt3(s2) ^ (s3)
    val b2 = (s0) ^ (s1) ^ xt2(s2) ^ xt3(s3)
    val b3 = xt3(s0) ^ (s1) ^ (s2) ^ xt2(s3)

    b3 ## b2 ## b1 ## b0
  }

  private def aes_mixcolumns_fwd(x: UInt): UInt = {
    val ic0 = aes_get_column(x, 0.U)
    val ic1 = aes_get_column(x, 1.U)
    val ic2 = aes_get_column(x, 2.U)
    val ic3 = aes_get_column(x, 3.U)

    val oc0 = aes_mixcolumn_fwd(ic0)
    val oc1 = aes_mixcolumn_fwd(ic1)
    val oc2 = aes_mixcolumn_fwd(ic2)
    val oc3 = aes_mixcolumn_fwd(ic3)

    oc3 ## oc2 ## oc1 ## oc0
  }

  private def aes_decode_rcon(r: UInt): UInt = {
    Mux1H(
      UIntToOH(r),
      Seq(
        "h00000001".U,
        "h00000002".U,
        "h00000004".U,
        "h00000008".U,
        "h00000010".U,
        "h00000020".U,
        "h00000040".U,
        "h00000080".U,
        "h0000001b".U,
        "h00000036".U,
        "h00000000".U,
        "h00000000".U,
        "h00000000".U,
        "h00000000".U,
        "h00000000".U,
        "h00000000".U
      )
    )
  }

  private def sig0(x: UInt): UInt = {
    // NOTE: only support SEW=32
    x(31, 0).rotateRight(7).asUInt(31, 0) ^
      x(31, 0).rotateRight(18).asUInt(31, 0) ^
      (x(31, 0) >> 3).asUInt(31, 0)
  }

  private def sig1(x: UInt): UInt = {
    // NOTE: only support SEW=32
    x(31, 0).rotateRight(17).asUInt(31, 0) ^
      x(31, 0).rotateRight(19).asUInt(31, 0) ^
      (x(31, 0) >> 10).asUInt(31, 0)
  }

  private def sum0(x: UInt): UInt = {
    // NOTE: only support SEW=32
    x(31, 0).rotateRight(2).asUInt(31, 0) ^
      x(31, 0).rotateRight(13).asUInt(31, 0) ^
      x(31, 0).rotateRight(22).asUInt(31, 0)
  }

  private def sum1(x: UInt): UInt = {
    // NOTE: only support SEW=32
    x(31, 0).rotateRight(6).asUInt(31, 0) ^
      x(31, 0).rotateRight(11).asUInt(31, 0) ^
      x(31, 0).rotateRight(25).asUInt(31, 0)
  }

  private def ch(x: UInt, y: UInt, z: UInt): UInt = ((x & y) ^ ((~x) & z))

  private def maj(x: UInt, y: UInt, z: UInt): UInt = ((x & y) ^ (x & z) ^ (y & z))

  private def ROUND_KEY(X: UInt, S: UInt) = {
    X(31, 0) ^ (
      S ^
        S(31, 0).rotateLeft(13).asUInt(31, 0) ^
        S(31, 0).rotateLeft(23).asUInt(31, 0)
    )
  }

  val sm4_sbox_table: Vec[UInt] = {
    val tmp = Seq(
      0xd6, 0x90, 0xe9, 0xfe, 0xcc, 0xe1, 0x3d, 0xb7, 0x16, 0xb6, 0x14, 0xc2, 0x28, 0xfb, 0x2c, 0x05, 0x2b, 0x67, 0x9a,
      0x76, 0x2a, 0xbe, 0x04, 0xc3, 0xaa, 0x44, 0x13, 0x26, 0x49, 0x86, 0x06, 0x99, 0x9c, 0x42, 0x50, 0xf4, 0x91, 0xef,
      0x98, 0x7a, 0x33, 0x54, 0x0b, 0x43, 0xed, 0xcf, 0xac, 0x62, 0xe4, 0xb3, 0x1c, 0xa9, 0xc9, 0x08, 0xe8, 0x95, 0x80,
      0xdf, 0x94, 0xfa, 0x75, 0x8f, 0x3f, 0xa6, 0x47, 0x07, 0xa7, 0xfc, 0xf3, 0x73, 0x17, 0xba, 0x83, 0x59, 0x3c, 0x19,
      0xe6, 0x85, 0x4f, 0xa8, 0x68, 0x6b, 0x81, 0xb2, 0x71, 0x64, 0xda, 0x8b, 0xf8, 0xeb, 0x0f, 0x4b, 0x70, 0x56, 0x9d,
      0x35, 0x1e, 0x24, 0x0e, 0x5e, 0x63, 0x58, 0xd1, 0xa2, 0x25, 0x22, 0x7c, 0x3b, 0x01, 0x21, 0x78, 0x87, 0xd4, 0x00,
      0x46, 0x57, 0x9f, 0xd3, 0x27, 0x52, 0x4c, 0x36, 0x02, 0xe7, 0xa0, 0xc4, 0xc8, 0x9e, 0xea, 0xbf, 0x8a, 0xd2, 0x40,
      0xc7, 0x38, 0xb5, 0xa3, 0xf7, 0xf2, 0xce, 0xf9, 0x61, 0x15, 0xa1, 0xe0, 0xae, 0x5d, 0xa4, 0x9b, 0x34, 0x1a, 0x55,
      0xad, 0x93, 0x32, 0x30, 0xf5, 0x8c, 0xb1, 0xe3, 0x1d, 0xf6, 0xe2, 0x2e, 0x82, 0x66, 0xca, 0x60, 0xc0, 0x29, 0x23,
      0xab, 0x0d, 0x53, 0x4e, 0x6f, 0xd5, 0xdb, 0x37, 0x45, 0xde, 0xfd, 0x8e, 0x2f, 0x03, 0xff, 0x6a, 0x72, 0x6d, 0x6c,
      0x5b, 0x51, 0x8d, 0x1b, 0xaf, 0x92, 0xbb, 0xdd, 0xbc, 0x7f, 0x11, 0xd9, 0x5c, 0x41, 0x1f, 0x10, 0x5a, 0xd8, 0x0a,
      0xc1, 0x31, 0x88, 0xa5, 0xcd, 0x7b, 0xbd, 0x2d, 0x74, 0xd0, 0x12, 0xb8, 0xe5, 0xb4, 0xb0, 0x89, 0x69, 0x97, 0x4a,
      0x0c, 0x96, 0x77, 0x7e, 0x65, 0xb9, 0xf1, 0x09, 0xc5, 0x6e, 0xc6, 0x84, 0x18, 0xf0, 0x7d, 0xec, 0x3a, 0xdc, 0x4d,
      0x20, 0x79, 0xee, 0x5f, 0x3e, 0xd7, 0xcb, 0x39, 0x48
    )
    VecInit(tmp.map { s => s.asUInt(8.W) })
  }

  private def sm_sbox_lookup(x: UInt): UInt = {
    Mux(x === 0.U, sm4_sbox_table(0.U), sm4_sbox_table(x - 1.U))
  }

  private def sm4_subword(x: UInt): UInt = {
    sm_sbox_lookup(x(31, 24)) ##
      sm_sbox_lookup(x(23, 16)) ##
      sm_sbox_lookup(x(15, 8)) ##
      sm_sbox_lookup(x(7, 0))
  }

  val ck: Vec[UInt] = {
    val tmp = Seq(
      BigInt("00070e15", 16),
      BigInt("1c232a31", 16),
      BigInt("383f464d", 16),
      BigInt("545b6269", 16),
      BigInt("70777e85", 16),
      BigInt("8c939aa1", 16),
      BigInt("a8afb6bd", 16),
      BigInt("c4cbd2d9", 16),
      BigInt("e0e7eef5", 16),
      BigInt("fc030a11", 16),
      BigInt("181f262d", 16),
      BigInt("343b4249", 16),
      BigInt("50575e65", 16),
      BigInt("6c737a81", 16),
      BigInt("888f969d", 16),
      BigInt("a4abb2b9", 16),
      BigInt("c0c7ced5", 16),
      BigInt("dce3eaf1", 16),
      BigInt("f8ff060d", 16),
      BigInt("141b2229", 16),
      BigInt("30373e45", 16),
      BigInt("4c535a61", 16),
      BigInt("686f767d", 16),
      BigInt("848b9299", 16),
      BigInt("a0a7aeb5", 16),
      BigInt("bcc3cad1", 16),
      BigInt("d8dfe6ed", 16),
      BigInt("f4fb0209", 16),
      BigInt("10171e25", 16),
      BigInt("2c333a41", 16),
      BigInt("484f565d", 16),
      BigInt("646b7279", 16)
    )
    VecInit(tmp.map { s => s.asUInt(32.W) })
  }

  private def sm4_round(X: UInt, S: UInt): UInt = {
    X(31, 0) ^ (
      S(31, 0) ^
        S(31, 0).rotateLeft(2).asUInt(31, 0) ^
        S(31, 0).rotateLeft(10).asUInt(31, 0) ^
        S(31, 0).rotateLeft(18).asUInt(31, 0) ^
        S(31, 0).rotateLeft(24).asUInt(31, 0)
    )
  }

  // for vghsh.vv, vgmul.vv
  val isVGHSH = (request.opcode === 0.U)
  val outVG   = {
    val valZ = 0.U(parameter.datapathWidth.W)
    val valS = UInt2BRev8(Mux(isVGHSH, vd ^ vs1, vd))
    val valH = Mux(isVGHSH, vs2, UInt2BRev8(vs2))

    // TODO: 128 rounds
    // valZ := Mux(valS(0), valZ ^ valH, valZ)
    // valH := Mux(valH(127), (valH << 1) & "h87".U, valH << 1.U)

    UInt2BRev8(valZ)
  }

  val outAESDF = {
    val state = vd
    val rkey  = vs2 // TODO: for vs, rkey is fixed to the first lane, support it before this module
    val sr    = aes_shift_rows_inv(state)
    val sb    = aes_subbytes_inv(sr)
    val ark   = sb ^ rkey
    ark
  }

  val outAESDM = {
    val state = vd
    val rkey  = vs2 // TODO: for vs, rkey is fixed to the first lane
    val sr    = aes_shift_rows_inv(state)
    val sb    = aes_subbytes_inv(sr)
    val ark   = sb ^ rkey
    val mix   = aes_mixcolumns_inv(ark)
    mix
  }

  val outAESEF = {
    val state = vd
    val rkey  = vs2 // TODO: for vs, rkey is fixed to the first lane
    val sb    = aes_subbytes_fwd(state)
    val sr    = aes_shift_rows_fwd(sb)
    val ark   = sr ^ rkey
    ark
  }

  val outAESEM = {
    val state = vd
    val rkey  = vs2 // TODO: for vs, rkey is fixed to the first lane
    val sb    = aes_subbytes_fwd(state)
    val sr    = aes_shift_rows_fwd(sb)
    val mix   = aes_mixcolumns_fwd(sr)
    val ark   = mix ^ rkey
    ark
  }

  val outAESZ = {
    val state = vd
    val rkey  = vs2 // TODO: rkey is fixed to the first lane
    val ark   = state ^ rkey
    ark
  }

  val outAESKF1 = {
    val currentRoundKey = vs2
    val rnd             = Mux((vs1(3, 0) >= 10.U) | (vs1(3, 0) === 0.U), (~vs1(3)) ## vs1(2, 0), vs1(3, 0))
    val r               = rnd - 1.U

    val w0 = aes_subword_fwd(aes_rotword(currentRoundKey(127, 96))) ^
      aes_decode_rcon(r) ^ currentRoundKey(31, 0)
    val w1 = w0 ^ currentRoundKey(63, 32)
    val w2 = w1 ^ currentRoundKey(95, 64)
    val w3 = w2 ^ currentRoundKey(127, 96)

    w3 ## w2 ## w1 ## w0
  }

  val outAESKF2 = {
    val currentRoundKey = vs2
    val roundKey        = vd
    val rnd             = Mux((vs1(3, 0) < 2.U) | (vs1(3, 0) > 14.U), (~vs1(3)) ## vs1(2, 0), vs1(3, 0))

    val w0 = Mux(
      rnd(0) === 1.U,
      aes_subword_fwd(currentRoundKey(127, 96)) ^ roundKey(31, 0),
      aes_subword_fwd(aes_rotword(currentRoundKey(127, 96))) ^
        aes_decode_rcon((rnd >> 1.U) - 1.U) ^
        roundKey(31, 0)
    )
    val w1 = w0 ^ roundKey(63, 32)
    val w2 = w1 ^ roundKey(95, 64)
    val w3 = w2 ^ roundKey(127, 96)

    w3 ## w2 ## w1 ## w0
  }

  val outSHA2MS = {
    val mw = vd
    val mx = vs2
    val my = vs1

    val mz0 = sig1(my(95, 64)) + mx(63, 32) + sig0(mw(63, 32)) + mw(31, 0)
    val mz1 = sig1(my(127, 96)) + mx(95, 64) + sig0(mw(95, 64)) + mw(63, 32)
    val mz2 = sig1(mz0) + mx(127, 96) + sig0(mw(127, 96)) + mw(95, 64)
    val mz3 = sig1(mz1) + my(31, 0) + sig0(mx(31, 0)) + mw(127, 96)

    mz3 ## mz2 ## mz1 ## mz0
  }

  val outSHA2CHL = {
    val isVSHA2CL = (request.opcode === 13.U)
    val ma        = vs2(127, 96)
    val mb        = vs2(95, 64)
    val me        = vs2(63, 32)
    val mf        = vs2(31, 0)

    val mc = vd(127, 96)
    val md = vd(95, 64)
    val mg = vd(63, 32)
    val mh = vd(31, 0)

    val messageShedPlusCa = vs1(127, 96)
    val messageShedPlusCb = vs1(95, 64)
    val messageShedPlusCc = vs1(63, 32)
    val messageShedPlusCd = vs1(31, 0)

    val w1 = Mux(isVSHA2CL, messageShedPlusCc, messageShedPlusCa)
    val w0 = Mux(isVSHA2CL, messageShedPlusCd, messageShedPlusCb)

    val t1 = mh + sum1(me) + ch(me, mf, mg) + w0
    val t2 = sum0(ma) + maj(ma, mb, mc)

    val mmh = mg
    val mmg = mf
    val mmf = me
    val mme = md + t1
    val mmd = mc
    val mmc = mb
    val mmb = ma
    val mma = t1 + t2
    val mt1 = mmh + sum1(mme) + ch(mme, mmf, mmg) + w1
    val mt2 = sum0(mma) + maj(mma, mmb, mmc)

    val mmmh = mmg
    val mmmg = mmf
    val mmmf = mme
    val mmme = mmd + mt1
    val mmmd = mmc
    val mmmc = mmb
    val mmmb = mma
    val mmma = mt1 + mt2

    mmma ## mmmb ## mmme ## mmmf
  }

  val outSM4K = {
    val rnd = vs1(2, 0)

    val rk3 = vs2(127, 96)
    val rk2 = vs2(95, 64)
    val rk1 = vs2(63, 32)
    val rk0 = vs2(31, 0)

    val B   = rk1 ^ rk2 ^ rk3 ^ ck(rnd << 2.U)(31, 0)
    val S   = sm4_subword(B)
    val rk4 = ROUND_KEY(rk0, S)

    val mB  = rk2 ^ rk3 ^ rk4 ^ ck((rnd << 2.U) + 1.U)(31, 0)
    val mS  = sm4_subword(mB)
    val rk5 = ROUND_KEY(rk1, S)

    val mmB = rk3 ^ rk4 ^ rk5 ^ ck((rnd << 2.U) + 2.U)(31, 0)
    val mmS = sm4_subword(mmB)

    val rk6  = ROUND_KEY(rk2, mmS)
    val mmmB = rk4 ^ rk5 ^ rk6 ^ ck((rnd << 2.U) + 3.U)(31, 0)
    val mmmS = sm4_subword(mmmB)

    val rk7 = ROUND_KEY(rk3, mmmS)

    rk7 ## rk6 ## rk5 ## rk4
  }

  val outSM4R = {
    val rk3 = vs2(127, 96) // TODO: for vs, rkey is fixed to the first lane
    val rk2 = vs2(95, 64)  // TODO: for vs, rkey is fixed to the first lane
    val rk1 = vs2(63, 32)  // TODO: for vs, rkey is fixed to the first lane
    val rk0 = vs2(31, 0)   // TODO: for vs, rkey is fixed to the first lane

    val x3 = vd(127, 96)
    val x2 = vd(95, 64)
    val x1 = vd(63, 32)
    val x0 = vd(31, 0)

    val mb = x1 ^ x2 ^ x3 ^ rk0
    val ms = sm4_subword(mb)
    val x4 = sm4_round(x0, ms)

    val mmb = x2 ^ x3 ^ x4 ^ rk1
    val mms = sm4_subword(mmb)
    val x5  = sm4_round(x1, mms)

    val mmmb = x3 ^ x4 ^ x5 ^ rk2
    val mmms = sm4_subword(mmmb)
    val x6   = sm4_round(x2, mmms)

    val mmmmb = x4 ^ x5 ^ x6 ^ rk3
    val mmmms = sm4_subword(mmmmb)
    val x7    = sm4_round(x3, mmmms)

    x7 ## x6 ## x5 ## x4
  }

  response.data := Mux1H(
    UIntToOH(request.opcode),
    Seq(
      outVG,
      outVG,
      outAESDF,
      outAESDM,
      outAESEF,
      outAESEM,
      outAESZ,
      outAESKF1,
      outAESKF2,
      outSHA2MS,
      outSHA2CHL,
      outSM4K,
      outSM4R,
      outSHA2CHL
    )
  )
}
