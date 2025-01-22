package sqrt

import chisel3._
import chisel3.util._

class SquareRootInput(width: Int) extends Bundle{
  val operand = UInt(width.W)
}

/** 0.1**** = 0.resultOrigin */
class SquareRootOutput(outputWidth: Int) extends Bundle{
  val result = UInt((outputWidth).W)
  val zeroRemainder = Bool()
}
class SqrtIterIn(width: Int) extends Bundle{
  val partialSum = UInt(width.W)
  val partialCarry = UInt(width.W)
}

class SqrtIterOut(width: Int) extends Bundle{
  val partialSum = UInt(width.W)
  val partialCarry = UInt(width.W)
  val isLastCycle = Bool()
}

class QDSInput(rWidth: Int, partialDividerWidth: Int) extends Bundle {
  val partialReminderCarry: UInt = UInt(rWidth.W)
  val partialReminderSum:   UInt = UInt(rWidth.W)
  /** truncated divisor without the most significant bit  */
  val partialDivider: UInt = UInt(partialDividerWidth.W)
}

class QDSOutput(ohWidth: Int) extends Bundle {
  val selectedQuotientOH: UInt = UInt(ohWidth.W)
}
