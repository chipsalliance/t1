// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.rocketv.csr

import chisel3._
import chisel3.util.log2Ceil

// context for Vector
class V(vlen: Int, hypervisor: Boolean) {
  require(Module.currentModule.isDefined)
  def vlWidth: Int = log2Ceil(vlen) + 1
  def vlenbWidth = log2Ceil(vlen / 8)
  val contents:                    Seq[String]  = Seq(
    "misa.V",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#32-vector-context-status-in-mstatus
    "mstatus.VS",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#33-vector-context-status-in-vsstatus
    "vsstatus.VS",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#341-vector-selected-element-width-vsew20
    "vsew",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#342-vector-register-grouping-vlmul20
    "vlmul",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#343-vector-tail-agnostic-and-vector-mask-agnostic-vta-and-vma
    "vta",
    "vma",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#344-vector-type-illegal-vill
    "vill",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#35-vector-length-register-vl
    "vl",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#36-vector-byte-length-vlenb
    "vlenb",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#37-vector-start-index-csr-vstart
    "vstart",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm
    "vxrm",
    // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#39-vector-fixed-point-saturation-flag-vxsat
    "vxsat"
  )
  def chiselType(content: String): Data         = content match {
    case "misa.V"      => Bool()
    case "mstatus.VS"  => UInt(2.W)
    case "vsstatus.VS" => UInt(2.W)
    case "vlmul"       => UInt(3.W)
    case "vsew"        => UInt(3.W)
    case "vta"         => Bool()
    case "vma"         => Bool()
    case "vill"        => Bool()
    case "vl"          => UInt(vlWidth.W)
    case "vlenb"       => UInt(vlenbWidth.W)
    case "vstart"      => UInt(vlWidth.W)
    case "vxrm"        => UInt(2.W)
    case "vxsat"       => Bool()
    case "tm"          => UInt(14.W)
    case "tk"          => UInt(3.W)
    case "vtwiden"     => UInt(2.W)
    case "altfmt"     => Bool()
  }
  // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#311-state-of-vector-extension-at-reset
  def reset(content: String):      Option[UInt] = content match {
    // 1 -> Initial; 2 -> Clean; 3 -> Dirty
    case "mstatus.VS" => Some(0.U)
    //  It is recommended that at reset, vtype.vill is set, the remaining bits in vtype are zero, and vl is set to zero.
    case "vlmul"      => Some(0.U)
    case "vsew"       => Some(0.U)
    case "vta"        => Some(false.B)
    case "vma"        => Some(false.B)
    case "vill"       => Some(true.B)
    // The vector extension must have a consistent state at reset. In particular, vtype and vl must have values that can be read and then restored with a single vsetvl instruction.
    case "vl"         => Some(0.U)
    case "tm"         => Some(0.U)
    case "tk"         => Some(0.U)
    case "vtwiden"    => Some(0.U)
    // The vstart, vxrm, vxsat CSRs can have arbitrary values at reset.
    case _            => None
  }
  def constant(content: String):   Option[UInt] = content match {
    // MISA in Rocket is not writable.
    case "misa.V" => Some(true.B)
    case "vlenb"  => Some((vlen / 8).U)
    case _        => None
  }

  val states: Map[String, UInt] =
    (Seq(
      "mstatus.VS",
      "vsew",
      "vlmul",
      "vta",
      "vma",
      "vill",
      "vl",
      "vstart",
      "vxrm",
      "vxsat",
      "tm", // todo: option?
      "tk",
      "vtwiden",
      "altfmt"
    ) ++ Option.when(hypervisor)(
      // https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#33-vector-context-status-in-vsstatus
      "vsstatus.VS"
    )).map { content: String =>
      content ->
        reset(content)
          .map(resetValue => RegInit(resetValue.asTypeOf(chiselType(content))))
          .getOrElse(Reg(chiselType(content)))
          .suggestName(content)
          .asUInt
    }.toMap

  val constants: Map[String, UInt] = Seq(
    // MISA in Rocket is not writable
    "misa.V",
    "vlenb"
  ).map { content: String =>
    content -> constant(content).get
  }.toMap
}
