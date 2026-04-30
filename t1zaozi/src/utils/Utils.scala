// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2026 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.rtl

import scala.math.{ceil, log}

import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

def log2Ceil(x: Int): Int =
  require(x > 0)
  (ceil(log(x.toDouble) / log(2.0))).toInt.max(1)

def chiselLog2Ceil(x: Int): Int =
  require(x > 0)
  if x == 1 then 0 else log2Ceil(x)

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
  val result     = Wire(Vec(count, UInt(width)))
  Seq.tabulate(count) { groupIndex =>
    result(groupIndex) := data.asBits.bits(groupIndex * width + width - 1, groupIndex * width).asUInt
  }
  result

def cutUIntBySize(
  data: Referable[UInt],
  size: Int
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Wire[Vec[UInt]] =
  require(data.width % size == 0)
  cutUInt(data, data.width / size)

def UIntToOH(
  data: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Node[UInt] =
  val width = 1 << data.width
  ((1.U(width) << data).asBits.bits(width - 1, 0)).asUInt

def fill(
  size:  Int,
  value: Referable[Bool]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[Bits] =
  require(size > 0)
  Seq.fill(size)(value.asBits).reduce(_ ## _)

def instIndexL(
  a: Referable[UInt],
  b: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[Bool] =
  require(a.width == b.width)
  (a.asBits.bits(a.width - 2, 0).asUInt < b.asBits.bits(b.width - 2, 0).asUInt) ^
    a.asBits.bit(a.width - 1) ^ b.asBits.bit(a.width - 1)

def instIndexLE(
  a: Referable[UInt],
  b: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[Bool] =
  require(a.width == b.width)
  ((a === b).asBits | instIndexL(a, b).asBits).asBool

def scanLeftOr(
  input: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  val width = input.width
  Iterator
    .iterate(1)(_ * 2)
    .takeWhile(_ < width)
    .foldLeft(input.asBits: Referable[Bits]) { (result, shift) =>
      result | (result.bits(width - 1 - shift, 0) ## 0.U(shift).asBits)
    }
    .asUInt

def scanRightOr(
  input: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  val width = input.width
  Iterator
    .iterate(1)(_ * 2)
    .takeWhile(_ < width)
    .foldLeft(input.asBits: Referable[Bits]) { (result, shift) =>
      result | (0.U(shift).asBits ## result.bits(width - 1, shift))
    }
    .asUInt

def ffo(
  input: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  val width   = input.width
  val scanned = scanLeftOr(input).asBits
  val shifted = if width > 1 then scanned.bits(width - 2, 0) ## 0.B(1) else 0.B(1)
  (~shifted & input.asBits).asUInt.asBits.bits(width - 1, 0).asUInt

def popCount(
  input: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  def treeReduce(level: Seq[Referable[UInt]]): Referable[UInt] =
    if level.size <= 1 then level.head
    else
      treeReduce(level.grouped(2).toSeq.map {
        case Seq(a, b) =>
          val w = a.width.max(b.width) + 1
          (0.U(w - a.width).asBits ## a.asBits).asUInt + (0.U(w - b.width).asBits ## b.asBits).asUInt
        case Seq(a)    => a
      })
  val width = input.width
  if width == 1 then input
  else treeReduce(Seq.tabulate(width)(i => input.asBits.bit(i).asBits.asUInt))

def OHToUInt(
  input: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  val width    = input.width
  val outWidth = log2Ceil(width)
  if width <= 1 then 0.U(1)
  else
    val outBits: Seq[Referable[Bits]] = Seq.tabulate(outWidth) { i =>
      val bits: Seq[Referable[Bits]] = Seq.tabulate(width) { j =>
        if ((j >> i) & 1) == 1 then input.asBits.bit(j).asBits else false.B.asBits
      }
      bits.reduce(_ | _)
    }
    outBits.reverse.reduce(_ ## _).asUInt

def rotateLeft(
  data:   Referable[UInt],
  amount: Referable[UInt],
  width:  Int
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  val doubled = (data.asBits ## data.asBits).asUInt
  (doubled << amount).asBits.bits(2 * width - 1, width).asUInt

def rotateRight(
  data:   Referable[UInt],
  amount: Referable[UInt],
  width:  Int
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  val doubled = (data.asBits ## data.asBits).asUInt
  (doubled >> amount).asBits.bits(width - 1, 0).asUInt

def maskAnd(
  mask: Referable[Bool],
  data: Referable[UInt]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  mask ? (data, 0.U(data.width))

def mux1H(
  select: Seq[Referable[Bool]],
  data:   Seq[Referable[UInt]]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  require(select.nonEmpty)
  require(select.size == data.size)
  val maskedData: Seq[Referable[Bits]] = select.zip(data).map { case (sel, value) =>
    (sel ? (value, 0.U(value.width))).asBits
  }
  maskedData.reduce(_ | _).asUInt

def mux1H(
  select: Referable[UInt],
  data:   Seq[Referable[UInt]]
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  mux1H(Seq.tabulate(data.size)(i => select.asBits.bit(i)), data)

def indexToOH(
  index:        Referable[UInt],
  chainingSize: Int
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[UInt] =
  UIntToOH(index.asBits.bits(log2Ceil(chainingSize), 0).asUInt)

def ohCheck(
  lastReport:   Referable[UInt],
  index:        Referable[UInt],
  chainingSize: Int
)(
  using Arena,
  Context,
  Block,
  sourcecode.File,
  sourcecode.Line,
  sourcecode.Name.Machine,
  InstanceContext
): Referable[Bool] =
  (indexToOH(index, chainingSize).asBits & lastReport.asBits).orR

def pipeUInt(
  input: Referable[UInt],
  n:     Int
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
): Referable[UInt] =
  (0 until n).foldLeft(input) { (prev, _) =>
    val reg = RegInit(0.U(input.width))
    reg := prev
    reg
  }

def pipeBool(
  input: Referable[Bool],
  n:     Int
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
): Referable[Bool] =
  (0 until n).foldLeft(input) { (prev, _) =>
    val reg = RegInit(false.B)
    reg := prev
    reg
  }

// Pipe valid+data together matching Chisel Pipe semantics:
// valid: RegNext(valid, false.B) — always updates, reset to false
// data:  RegEnable(data, valid)  — only updates when valid, no functional reset
def pipeValidData(
  valid: Referable[Bool],
  data:  Referable[UInt],
  n:     Int
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
): (Referable[Bool], Referable[UInt]) =
  (0 until n).foldLeft((valid, data)) { case ((prevValid, prevData), _) =>
    val vReg = RegInit(false.B)
    vReg := prevValid
    val dReg = Reg(UInt(data.width))
    when(prevValid):
      dReg := prevData
    (vReg, dReg)
  }

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
  val counter       = RegInit(0.U(counterSize))
  val allOnes       = ((1 << counterSize) - 1).U(counterSize)
  val counterChange = enq ? (1.U(counterSize), allOnes)
  when(enq ^ deq):
    counter := (counter + counterChange).asBits.bits(counterSize - 1, 0).asUInt
  !counter.asBits.bit(log2Ceil(size))
