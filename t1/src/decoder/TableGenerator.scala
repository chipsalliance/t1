// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder

import chisel3.util._
import chisel3.util.experimental.decode.TruthTable

import scala.language.postfixOps

object TableGenerator extends App {
  implicit class CrossAble[X](xs: Traversable[X]) {
    def cross[Y](ys: Traversable[Y]): Traversable[(X, Y)] = for {
      x <- xs
      y <- ys
    } yield (x, y)
  }
  implicit def bool2str(b: Boolean): String = if (b) "b1" else "b0"

  object LogicTable {
    object LogicOpcode {
      var index = 0
    }

    sealed trait LogicOpcode {
      val value: Int = LogicOpcode.index
      LogicOpcode.index = LogicOpcode.index + 1
    }

    trait Operand

    trait BinaryOperand extends Operand {
      def op(op0: Boolean, op1: Boolean): Boolean
    }

    case object and extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 && op1
    }

    case object or extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 || op1
    }

    case object xor extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 != op1
    }

    val opList:   Seq[BinaryOperand with LogicOpcode] = Seq(and, or, xor)
    val bitValue: Seq[Boolean]                        = Seq(true, false)

    val table: List[(BitPat, BitPat)] = bitValue
      .cross(bitValue)
      .cross(opList)
      .map { case ((op0, op1), op) =>
        BitPat(toBinary(op.value)) ## BitPat(op0) ## BitPat(op1) -> BitPat(op.op(op0, op1))
      }
      .toList
  }

  object LaneDecodeTable {
    object LaneUop {
      var index = 0
    }

    sealed trait LaneUop {
      val value: Int = LaneUop.index
      LaneUop.index = LaneUop.index + 1
    }

    /*object SubUnitCode {
      var index = 1
    }

    sealed trait SubUnitCode {
      val value: Int = SubUnitCode.index
      SubUnitCode.index = SubUnitCode.index << 1
    }*/
    trait BaseObject
    trait SubUnit extends BaseObject

    trait LogicUnit   extends SubUnit
    trait Arithmetic  extends SubUnit
    trait Shift       extends SubUnit
    trait Mul         extends SubUnit
    trait Div         extends SubUnit
    trait PopCount    extends SubUnit
    trait FFO         extends SubUnit
    trait GetIndex    extends SubUnit
    trait DataProcess extends SubUnit

    def subUnitCode(in: SubUnit): Int = {
      in match {
        case unit:       LogicUnit   => 1
        case arithmetic: Arithmetic  => 2
        case shift:      Shift       => 4
        case mul:        Mul         => 8
        case div:        Div         => 16
        case count:      PopCount    => 32
        case ffo:        FFO         => 64
        case index:      GetIndex    => 128
        case process:    DataProcess => 256
        case _ => 0
      }
    }

    case object and      extends LogicUnit with LaneUop
    case object nand     extends LogicUnit with LaneUop
    case object andn     extends LogicUnit with LaneUop
    case object or       extends LogicUnit with LaneUop
    case object nor      extends LogicUnit with LaneUop
    case object orn      extends LogicUnit with LaneUop
    case object xor      extends LogicUnit with LaneUop
    case object xnor     extends LogicUnit with LaneUop
    case object add      extends Arithmetic with LaneUop
    case object sub      extends Arithmetic with LaneUop
    case object adc      extends Arithmetic with LaneUop
    case object madc     extends Arithmetic with LaneUop
    case object sbc      extends Arithmetic with LaneUop
    case object msbc     extends Arithmetic with LaneUop
    case object slt      extends Arithmetic with LaneUop
    case object sltu     extends Arithmetic with LaneUop
    case object sle      extends Arithmetic with LaneUop
    case object sleu     extends Arithmetic with LaneUop
    case object sgt      extends Arithmetic with LaneUop
    case object sgtu     extends Arithmetic with LaneUop
    case object sge      extends Arithmetic with LaneUop
    case object sgeu     extends Arithmetic with LaneUop
    case object max      extends Arithmetic with LaneUop
    case object maxu     extends Arithmetic with LaneUop
    case object min      extends Arithmetic with LaneUop
    case object minu     extends Arithmetic with LaneUop
    case object sll      extends Shift with LaneUop
    case object srl      extends Shift with LaneUop
    case object sra      extends Shift with LaneUop
    case object ssrl     extends Shift with LaneUop
    case object ssra     extends Shift with LaneUop
    case object mul      extends Mul with LaneUop
    case object mulh     extends Mul with LaneUop
    case object mulhu    extends Mul with LaneUop
    case object mulhsu   extends Mul with LaneUop
    case object ma       extends Mul with LaneUop
    case object ms       extends Mul with LaneUop
    case object div      extends Div with LaneUop
    case object divu     extends Div with LaneUop
    case object rem      extends Div with LaneUop
    case object remu     extends Div with LaneUop
    case object popCount extends PopCount with LaneUop
    case object ffo      extends FFO with LaneUop
    case object ffB      extends FFO with LaneUop
    case object ffInc    extends FFO with LaneUop
    case object ffID     extends FFO with LaneUop
    case object getID    extends GetIndex with LaneUop
  }

  object BankEnableTable {
    // TODO
    val maskList:     Seq[Int]               = Seq(1, 3, 15)
    val maskSizeList: Seq[Int]               = Seq(1, 2, 4)
    var table:        List[(BitPat, BitPat)] = List.empty
    for {
      eew     <- 0 to 2
      vs      <- 0 to 3
      groupId <- 0 to 3
      v       <- Seq(true, false)
    } {
      var mask     = if (v) maskList(eew) else 0
      val maskSize = maskSizeList(eew)
      val index    = (maskSize * (vs + groupId)) % 4
      mask <<= index
      table :+= BitPat(v) ## BitPat(toBinary(eew, 2)) ## BitPat(toBinary(vs, 2)) ## BitPat(
        toBinary(groupId, 2)
      ) -> BitPat(toBinary(index, 2)) ## BitPat(toBinary(mask, 4))
    }
    val res:          TruthTable             = TruthTable(table, BitPat.dontCare(6))
  }

  def toBinary(i: Int, digits: Int = 3): String = {
    String.format("b%" + digits + "s", i.toBinaryString).replace(' ', '0')
  }
}
