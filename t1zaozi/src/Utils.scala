// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl.zvma

import scala.math.{ceil, log}

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

def log2Ceil(x: Int): Int =
  require(x > 0)
  (ceil(log(x.toDouble) / log(2.0))).toInt.max(1)

def cutUInt(
  data:  Referable[UInt],
  width: Int
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Wire[Vec[UInt]] =
  val totalWidth = data.asBits.width
  require(totalWidth % width == 0)
  val count      = totalWidth / width
  val result     = Wire(Vec(count, UInt(width.W)))
  Seq.tabulate(count) { groupIndex =>
    result(groupIndex) := data.asBits.bits(groupIndex * width + width - 1, groupIndex * width).asUInt
  }
  result

def pipeToken(
  size: Int
)(enq:  Referable[Bool],
  deq:  Referable[Bool]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext,
  Ref[Clock],
  Ref[Reset]
): Node[Bool] =
  require(Integer.bitCount(size) == 1)
  val counterSize   = log2Ceil(size) + 1
  val counter       = RegInit(0.U(counterSize.W))
  val allOnes       = ((1 << counterSize) - 1).U(counterSize.W)
  val counterChange = enq ? (1.U(counterSize.W), allOnes)
  when(enq ^ deq):
    counter := (counter + counterChange).asBits.bits(counterSize - 1, 0).asUInt
  !counter.asBits.bit(log2Ceil(size))
