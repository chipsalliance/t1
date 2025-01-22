package float

import chisel3._
import chisel3.util._

object RoundingMode {
    def RNE   = "b000".U(3.W)
    def RTZ   = "b001".U(3.W)
    def RDN   = "b010".U(3.W)
    def RUP   = "b011".U(3.W)
    def RMM   = "b100".U(3.W)
}

class RawFloat(val expWidth: Int, val sigWidth: Int) extends Bundle
{
    val isNaN: Bool  = Bool()
    val isInf: Bool  = Bool()
    val isZero: Bool = Bool()
    val isSNaN:Bool  = Bool()
    val sExpIsEven   = Bool()
    val sign: Bool   = Bool()
    // sExp = 0.U(1.W) ## (Exp &- bias)
    // Exp - bias width is expWidth + 1
    val sExp: SInt   = SInt((expWidth + 2).W)
    // sig = 0 ## restored 1 or 0 ## fracIn
    val sig: UInt    = UInt((sigWidth + 1).W)   // 2 m.s. bits cannot both be 0
}

object countLeadingZeros
{
  def apply(in: UInt): UInt = PriorityEncoder(in.asBools.reverse)
}

