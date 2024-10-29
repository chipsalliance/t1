// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.lane

import chisel3._
import chisel3.experimental.hierarchy._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.Decoder
import org.chipsalliance.dwbb.stdlib.queue.Queue

class EnqReportBundle(parameter: LaneParameter) extends Bundle {
  val decodeResult:     DecodeBundle = Decoder.bundle(parameter.decoderParam)
  val instructionIndex: UInt         = UInt(parameter.instructionIndexBits.W)
  val sSendResponse:    Bool         = Bool()
  val mask:             UInt         = UInt(4.W)
}

/** For each slot, it has 4 stages:
  * {{{
  * stage0.enq             <-> [[enqReports]]         <-> slot token start
  *   - handle mask
  *   - choose next datapath group
  *
  * stage1:
  *   - read VRF
  *     - send VRFRead(vs1,vs2,vd,crossread0,crossread1) task to each pre-queue.
  *     - pre-queue to post-queue need chaining check.
  *       @todo @Lucas-Wye add crossreadzk{0,1,2,3} here.
  *       @note think about using register as L1VRF$, using a wider VRF SRAM for better SRAM Physical design(larger datawidth, less port)
  *             this take an assumption that most of Lane are synchronized, and can use the larger memory width.
  *     - task dequeue from post-queue will access VRF read port, then data to read data queue
  *       - data will go to send cross read port if cross read
  *       - cross read data from other lane will enqueue to data queue
  *     - wait all data from the task arrived
  *     - @todo: ECC check here. if 1-bit recover for datapath，if 2-bit, exception.(TODO: feed to mbist and mrecovery)
  *
  * stage2(executionUnit)
  *   - exec
  *
  *
  * stage3
  *                        <-> [[responseReport]]: - report exec result to Sequencer(Top will exec it later);
  *                                                - send index load store offset to LSU
  *                        <-> [[responseFeedbackReport]]
  *                                                - wait for ack from Sequencer(only slot 0 will wait)
  *                                                  @todo if don't no wait for data from Sequencer use token to speed up the free speed.
  *                                                        this can make new instruction enter slot0 faster to increase the bandwidth usage ratio of VRF&EXEC
  * }}}
  * VrfWritePipe:
  *   - vrfWrite + cross write rx + lsu write + Sequencer write -> allVrfWrite
  *   - do {waw, war} check for allVrfWrite, go to allVrfWriteAfterCheck
  *   - allVrfWriteAfterCheck -> [[slotWriteReport]] -> [[crossWriteReports]]: cross lane write
  *   - allVrfWriteAfterCheck -> Arbiter -> [[writePipeEnqReport]] -> write pipe token acquire
  *   - VRF write
  *     - queueBeforeMaskWrite
  *     - maskedWriteUnit
  *     - \@todo @qinjun-li ECC encode and write
  *
  * vrf -> [[writePipeDeqReport]] -> write pipe token release
  */
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
  val responseReport: ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))

  @public
  val responseFeedbackReport: ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))

  @public
  val slotWriteReport: Seq[ValidIO[UInt]] = Seq.tabulate(parameter.chainingSize) { _ =>
    IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))
  }

  @public
  val writePipeEnqReport: ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))

  @public
  val writePipeDeqReport: ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))

  @public
  val topWriteEnq: ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))

  @public
  val topWriteDeq: ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))

  @public
  val instructionValid: UInt = IO(Output(UInt(parameter.chainingSize.W)))

  @public
  val dataInWritePipe: UInt = IO(Output(UInt(parameter.chainingSize.W)))

  def tokenUpdate(tokenData: Seq[UInt], enqWire: UInt, deqWire: UInt): UInt = {
    tokenData.zipWithIndex.foreach { case (t, i) =>
      val e      = enqWire(i)
      val d      = deqWire(i)
      val change = Mux(e, 1.U(tokenWith.W), -1.S(tokenWith.W).asUInt)
      when(e ^ d) {
        t := t + change
      }
    }
    VecInit(tokenData.map(_ =/= 0.U)).asUInt
  }

  // todo: Precise feedback
  def feedbackUpdate(tokenData: Seq[UInt], enqWire: UInt, deqWire: UInt): UInt = {
    tokenData.zipWithIndex.foreach { case (t, i) =>
      val e      = enqWire(i)
      val d      = deqWire(i)
      val change = Mux(e, 1.U(tokenWith.W), -1.S(tokenWith.W).asUInt)
      when((e ^ d) && (e || t =/= 0.U)) {
        t := t + change
      }
    }
    VecInit(tokenData.map(_ =/= 0.U)).asUInt
  }

  val instructionInSlot: UInt = enqReports.zipWithIndex.map { case (enqReport, slotIndex) =>
    val writeToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))

    val enqOH = indexToOH(enqReport.bits.instructionIndex, parameter.chainingSize)

    val writeDoEnq: UInt =
      maskAnd(enqReport.valid && !enqReport.bits.decodeResult(Decoder.sWrite), enqOH).asUInt

    val writeDoDeq: UInt =
      maskAnd(
        slotWriteReport(slotIndex).valid,
        indexToOH(slotWriteReport(slotIndex).bits, parameter.chainingSize)
      ).asUInt

    val pendingSlotWrite = tokenUpdate(writeToken, writeDoEnq, writeDoDeq)

    if (slotIndex == 0) {
      val responseToken:      Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
      val feedbackToken:      Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
      val crossWriteTokenLSB: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
      val crossWriteTokenMSB: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))

      // Feedback is not accurate (index load/store may have already finished the instruction)
      val responseIndexQueue =
        Queue.io(UInt(parameter.instructionIndexBits.W), parameter.chainingSize + 1, flow = true)
      responseIndexQueue.enq.valid := responseReport.valid
      responseIndexQueue.enq.bits  := responseReport.bits
      responseIndexQueue.deq.ready := responseFeedbackReport.valid

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

      val feedbackIndexSelect = Mux(
        responseIndexQueue.deq.valid,
        responseIndexQueue.deq.bits,
        responseFeedbackReport.bits
      )
      val feedbackDoDeq: UInt =
        maskAnd(responseFeedbackReport.valid, indexToOH(feedbackIndexSelect, parameter.chainingSize)).asUInt

      val pendingResponse = tokenUpdate(responseToken, responseDoEnq, responseDoDeq)
      // todo: Precise feedback
      val pendingFeedback = feedbackUpdate(feedbackToken, responseDoEnq, feedbackDoDeq)
      pendingSlotWrite | pendingCrossWriteLSB | pendingCrossWriteMSB | pendingResponse | pendingFeedback
    } else {
      pendingSlotWrite
    }
  }.reduce(_ | _)

  // write pipe token
  val writePipeToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
  val writePipeEnq:   UInt      =
    maskAnd(writePipeEnqReport.valid, indexToOH(writePipeEnqReport.bits, parameter.chainingSize)).asUInt
  val writePipeDeq:   UInt      =
    maskAnd(writePipeDeqReport.valid, indexToOH(writePipeDeqReport.bits, parameter.chainingSize)).asUInt

  val instructionInWritePipe: UInt = tokenUpdate(writePipeToken, writePipeEnq, writePipeDeq)

  // top write token
  val topWriteToken: Seq[UInt] = Seq.tabulate(parameter.chainingSize)(_ => RegInit(0.U(tokenWith.W)))
  val topWriteDoEnq: UInt      =
    maskAnd(topWriteEnq.valid, indexToOH(topWriteEnq.bits, parameter.chainingSize)).asUInt

  val topWriteDoDeq: UInt =
    maskAnd(topWriteDeq.valid, indexToOH(topWriteDeq.bits, parameter.chainingSize)).asUInt

  val instructionInTopWritePipe = tokenUpdate(topWriteToken, topWriteDoEnq, topWriteDoDeq)

  dataInWritePipe  := instructionInWritePipe | instructionInTopWritePipe
  instructionValid := dataInWritePipe | instructionInSlot
}
