package v

import chisel3.util._

import scala.language.postfixOps

object TableGenerator extends App {
  implicit class CrossAble[X](xs: Traversable[X]) {
    def cross[Y](ys: Traversable[Y]): Traversable[(X, Y)] = for { x <- xs; y <- ys } yield (x, y)
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
    case object nand extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = !(op0 && op1)
    }
    case object andn extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 && !op1
    }
    case object or extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 || op1
    }
    case object nor extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = !(op0 || op1)
    }
    case object orn extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 || !op1
    }
    case object xor extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 != op1
    }
    case object xnor extends BinaryOperand with LogicOpcode {
      override def op(op0: Boolean, op1: Boolean): Boolean = op0 == op1
    }

    val opList:   Seq[BinaryOperand with LogicOpcode] = Seq(and, nand, andn, or, nor, orn, xor, xnor)
    val bitValue: Seq[Boolean] = Seq(true, false)

    val table: List[(BitPat, BitPat)] = bitValue
      .cross(bitValue)
      .cross(opList)
      .map {
        case ((op0, op1), op) =>
          BitPat(toBinary(op.value)) ## BitPat(op0) ## BitPat(op1) -> BitPat(op.op(op0, op1))
      } toList
  }
}
