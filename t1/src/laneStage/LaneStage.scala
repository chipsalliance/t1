// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.{CSRInterface, LaneParameter}
import org.chipsalliance.t1.rtl.decoder.Decoder

class LaneState(parameter: LaneParameter) extends Bundle {
  val vSew1H:       UInt         = UInt(3.W)
  val loadStore:    Bool         = Bool()
  val laneIndex:    UInt         = UInt(parameter.laneNumberBits.W)
  val decodeResult: DecodeBundle = Decoder.bundle(parameter.decoderParam)

  /** which group is the last group for instruction. */
  val lastGroupForInstruction:  UInt         = UInt(parameter.groupNumberBits.W)
  val isLastLaneForInstruction: Bool         = Bool()
  val instructionFinished:      Bool         = Bool()
  val csr:                      CSRInterface = new CSRInterface(parameter.vlMaxBits)
  // vm = 0
  val maskType:                 Bool         = Bool()
  val maskNotMaskedElement:     Bool         = Bool()
  val skipEnable:               Bool         = Bool()

  /** vs1 or imm */
  val vs1: UInt = UInt(5.W)

  /** vs2 or rs2 */
  val vs2: UInt = UInt(5.W)

  /** vd or rd */
  val vd: UInt = UInt(5.W)

  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  val additionalRW:     Bool = Bool()
  // skip vrf read in stage 1?
  val skipRead:         Bool = Bool()
}

@instantiable
abstract class LaneStage[A <: Data, B <: Data](pipe: Boolean)(input: A, output: B) extends Module {
  @public
  val enqueue: DecoupledIO[A] = IO(Flipped(Decoupled(input)))
  @public
  val dequeue: DecoupledIO[B] = IO(Decoupled(output))
  @public
  val stageValid = IO(Output(Bool()))
  val stageFinish:   Bool = WireDefault(true.B)
  val stageValidReg: Bool = RegInit(false.B)
  dontTouch(enqueue)
  dontTouch(dequeue)
  if (pipe) {
    enqueue.ready := !stageValidReg || (dequeue.ready && stageFinish)
  } else {
    enqueue.ready := !stageValidReg
  }

  dequeue.valid := stageValidReg && stageFinish
  stageValid    := stageValidReg
}
