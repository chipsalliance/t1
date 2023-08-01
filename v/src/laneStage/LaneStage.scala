package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

class LaneState(parameter: LaneParameter) extends Bundle {
  val vSew1H: UInt = UInt(3.W)
  val loadStore: Bool = Bool()
  val laneIndex: UInt = UInt(parameter.laneNumberBits.W)
  val decodeResult: DecodeBundle = Decoder.bundle(parameter.fpuEnable)
  /** which group is the last group for instruction. */
  val lastGroupForInstruction: UInt = UInt(parameter.groupNumberBits.W)
  val instructionFinished: Bool = Bool()
  val csr: CSRInterface = new CSRInterface(parameter.vlMaxBits)
  // vm = 0
  val maskType: Bool = Bool()
  val maskNotMaskedElement: Bool = Bool()
  val maskForMaskGroup: UInt = UInt(parameter.datapathWidth.W)
  val mask: ValidIO[UInt] = Valid(UInt(parameter.datapathWidth.W))
  val ffoByOtherLanes: Bool = Bool()

  /** vs1 or imm */
  val vs1: UInt = UInt(5.W)

  /** vs2 or rs2 */
  val vs2: UInt = UInt(5.W)

  /** vd or rd */
  val vd: UInt = UInt(5.W)

  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
}

abstract class LaneStage[A <: Data, B <:Data](pipe: Boolean)(input: A, output: B) extends Module{
  val enqueue: DecoupledIO[A] = IO(Flipped(Decoupled(input)))
  val dequeue: DecoupledIO[B] = IO(Decoupled(output))
  val stageValid = IO(Output(Bool()))
  val stageFinish: Bool = WireDefault(true.B)
  val stageValidReg: Bool = RegInit(false.B)
  dontTouch(enqueue)
  dontTouch(dequeue)
  if(pipe) {
    enqueue.ready := !stageValidReg || (dequeue.ready && stageFinish)
  } else {
    enqueue.ready := !stageValidReg
  }

  dequeue.valid := stageValidReg && stageFinish
  stageValid := stageValidReg
}
