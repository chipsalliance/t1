package v

import chisel3._
import chisel3.util._
import division.srt.SRT

class LaneDivRequest(param: DataPathParam) extends Bundle {
  val src: Vec[UInt] = Vec(2, UInt(param.dataWidth.W))
  val rem: Bool = Bool()
  val sign: Bool = Bool()
}

class LaneDiv(param: DataPathParam) extends Module {
  val req: DecoupledIO[LaneDivRequest] = IO(Flipped(Decoupled(new LaneDivRequest(param))))
  val vSew: UInt = IO(Input(UInt(2.W)))
  // mask for sew
  val mask: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resp: ValidIO[UInt] = IO(Valid(UInt(param.dataWidth.W)))

  val sign1h: UInt = mask & (~mask >> 1).asUInt

  val srcExtend: IndexedSeq[UInt] = req.bits.src.map { src =>
    val signValue: Bool = (sign1h & src).orR
    val signExtend: UInt = Fill(param.dataWidth, signValue)
    (src & mask) | (signExtend & (~mask).asUInt)
  }

  val div: SRT = Module(new SRT(32, 32, 32))
  div.input.bits.divider := srcExtend.head
  div.input.bits.dividend := srcExtend.head
  div.input.bits.counter := 8.U

  resp.valid := div.output.valid
  resp.bits := div.output.bits
}