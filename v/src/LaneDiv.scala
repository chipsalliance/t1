package v

import chisel3._
import chisel3.util._

class LaneDiv(param: DataPathParam) extends Module {
  val srcVec: DecoupledIO[Vec[UInt]] = IO(Flipped(Decoupled(Vec(2, UInt(param.dataWidth.W)))))
  val sign: Bool = IO(Input(Bool()))
  val div: Bool = IO(Input(Bool()))
  // mask for sew
  val mask: UInt = IO(Input(UInt(param.dataWidth.W)))
  val resp: ValidIO[UInt] = IO(Valid(UInt(param.dataWidth.W)))

  val sign1h: UInt = mask & (~mask >> 1).asUInt

  val srcExtend: IndexedSeq[UInt] = srcVec.bits.map { src =>
    val signValue: Bool = (sign1h & src).orR
    val signExtend: UInt = Fill(param.dataWidth, signValue)
    (src & mask) | (signExtend & (~mask).asUInt)
  }

  val count: UInt = RegInit(0.U(3.W))
  val busy: Bool = RegInit(false.B)
  when(resp.valid) {
    busy := false.B
  }
  when(srcVec.fire) {
    busy := true.B
  }
  when(busy || srcVec.fire) {
    count := count + 1.U
  }
  // todo: srt.req.ready
  srcVec.ready := count === 0.U
  resp.valid := count.andR

  // TODO: div
  resp.bits := srcExtend.head / srcExtend.last
}