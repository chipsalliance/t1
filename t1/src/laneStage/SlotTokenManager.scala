// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder

class EnqReportBundle(parameter: LaneParameter) extends Bundle {
  val decodeResult: DecodeBundle = Decoder.bundle(parameter.fpuEnable)
  val instructionIndex: UInt = UInt(parameter.instructionIndexBits.W)
  val sSendResponse: Bool = Bool()
}
/**
 * stage0.enq             <->   [[enqReports]]         <-> slot token start
 *
 * stage1
 *
 * stage2(executionUnit)
 *
 * stage3                 <-> [[crossWriteReports]]
 *                        <-> [[responseReport]]
 *                        <-> [[responseFeedbackReport]]
 *
 * allVrfWriteAfterCheck  <-> [[slotWriteReport]]
 *
 *    <arbiter>           <-> [[writePipeEnqReport]]          <-> write pipe token start
 *
 * queueBeforeMaskWrite
 *
 * maskedWriteUnit
 *
 * vrf                    <-> [[writePipeDeqReport]]
 * */
@instantiable
class SlotTokenManager(parameter: LaneParameter) extends Module {
  // todo: param
  val tokenWith = 5
  @public
  val enqReports: Seq[ValidIO[EnqReportBundle]] = Seq.tabulate(parameter.chainingSize) { _ =>
    IO(Flipped(Valid(new EnqReportBundle(parameter))))
  }

  @public
  val crossWriteReports: Vec[ValidIO[UInt]] = IO(Vec(2, Flipped(Valid(UInt(parameter.instructionIndexBits.W)))))

  @public
  val responseReport: ValidIO[UInt] = Flipped(Valid(UInt(parameter.instructionIndexBits.W)))

  @public
  val responseFeedbackReport: ValidIO[UInt] = Flipped(Valid(UInt(parameter.instructionIndexBits.W)))

  @public
  val slotWriteReport: Seq[ValidIO[UInt]] = Seq.tabulate(parameter.chainingSize) { _ =>
    IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))
  }

  @public
  val writePipeEnqReport: ValidIO[UInt] = Flipped(Valid(UInt(parameter.instructionIndexBits.W)))

  @public
  val writePipeDeqReport: ValidIO[UInt] = Flipped(Valid(UInt(parameter.instructionIndexBits.W)))

  @public
  val instructionValid: UInt = IO(Output(UInt(parameter.instructionIndexBits.W)))

  def tokenUpdate(tokenData: Seq[UInt], enqWire: UInt, deqWire: UInt): UInt = {
    tokenData.zipWithIndex.foreach { case (t, i) =>
      val e = enqWire(i)
      val d = deqWire(i)
      val change = Mux(e, 1.U(tokenWith.W), -1.S(tokenWith.W).asUInt)
      when(e ^ d) {
        t := t + change
      }
    }
    VecInit(tokenData.map(_ === 0.U)).asUInt
  }

  val instructionInSlot: UInt = enqReports.zipWithIndex.map { case (enqReport, slotIndex) =>

    val writeToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))

    val enqOH = indexToOH(enqReport.bits.instructionIndex, parameter.chainingSize)

    val writeDoEnq: UInt =
      maskAnd(enqReport.valid && !enqReport.bits.decodeResult(Decoder.sWrite), enqOH).asUInt

    val writeDoDeq: UInt =
      maskAnd(
        slotWriteReport(slotIndex).valid,
        indexToOH(slotWriteReport(slotIndex).bits, parameter.chainingSize)).asUInt

    val pendingSlotWrite = tokenUpdate(writeToken, writeDoEnq, writeDoDeq)

    if (slotIndex == 0) {
      val responseToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
      val feedbackToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
      val crossWriteTokenLSB: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
      val crossWriteTokenMSB: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))

      // cross write update
      val crossWriteDoEnq: UInt =
        maskAnd(enqReport.valid && enqReport.bits.decodeResult(Decoder.crossWrite), enqOH).asUInt

      val crossWriteDeqLSB =
        maskAnd(crossWriteReports.head.valid, indexToOH(crossWriteReports.head.bits, parameter.chainingSize)).asUInt

      val crossWriteDeqMSB =
        maskAnd(crossWriteReports.last.valid, indexToOH(crossWriteReports.last.bits, parameter.chainingSize)).asUInt

      val pendingCrossWriteLSB = tokenUpdate(crossWriteTokenLSB, crossWriteDoEnq, crossWriteDeqLSB)
      val pendingCrossWriteMSB = tokenUpdate(crossWriteTokenMSB, crossWriteDoEnq, crossWriteDeqMSB)

      // response & feedback update
      val responseDoEnq: UInt =
        maskAnd(enqReport.valid && !enqReport.bits.sSendResponse, enqOH).asUInt

      val responseDoDeq: UInt =
        maskAnd(responseReport.valid, indexToOH(responseReport.bits, parameter.chainingSize)).asUInt

      val feedbackDoDeq: UInt =
        maskAnd(responseFeedbackReport.valid, indexToOH(responseFeedbackReport.bits, parameter.chainingSize)).asUInt

      val pendingResponse = tokenUpdate(responseToken, responseDoEnq, responseDoDeq)
      val pendingFeedback = tokenUpdate(feedbackToken, responseDoEnq, feedbackDoDeq)
      pendingSlotWrite | pendingCrossWriteLSB | pendingCrossWriteMSB | pendingResponse | pendingFeedback
    } else {
      pendingSlotWrite
    }
  }.reduce(_ | _)

  val writePipeToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
  val writePipeEnq: UInt =
    maskAnd(writePipeEnqReport.valid, indexToOH(writePipeEnqReport.bits, parameter.chainingSize)).asUInt
  val writePipeDeq: UInt =
    maskAnd(writePipeDeqReport.valid, indexToOH(writePipeDeqReport.bits, parameter.chainingSize)).asUInt

  val instructionInWritePipe: UInt = tokenUpdate(writePipeToken, writePipeEnq, writePipeDeq)

  instructionValid := instructionInWritePipe | instructionInSlot
}
