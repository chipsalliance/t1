// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package tests.elaborate

import chisel3._
import chisel3.probe._
import chisel3.experimental.hierarchy._
import chisel3.util.experimental.BoringUtils.{bore, tap, tapAndRead}
import chisel3.util.{Decoupled, DecoupledIO, HasExtModuleInline, Valid, ValidIO, log2Ceil}
import tilelink.{TLBundle, TLChannelA}
import v.{CSRInterface, LSUWriteQueueBundle, V, VRFWriteRequest, VRequest, VResponse}
import elaborate.dpi._

class VerificationModule(dut: V) extends RawModule {
  override val desiredName = "VerificationModule"

  val clockRate = 5

  val latPeekLsuEnq = 1
  val latPeekVrfWrite = 1
  val latPokeInst = 1
  val latPokeTL = 1

  val latPeekIssue = 2 // get se_to_issue here
  val latPeekTL = 2

  val negLatPeekWriteQueue = 1

  val clockGen = Module(new ClockGen(ClockGenParameter(clockRate)))

  val dumpDumpWave = Module(new DpiDumpWave)
  val dpiFinish = Module(new DpiFinish)
  val dpiError = Module(new DpiError)
  val dpiInit = Module(new DpiInitCosim)
  val dpiTimeoutCheck = Module(new TimeoutCheck(new TimeoutCheckParameter(clockRate)))

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))

  val genClock = read(clockGen.clock)
  clock := genClock.asClock
  reset := read(clockGen.reset)

  // clone IO from V(I need types)
  val req:              DecoupledIO[VRequest] = IO(Decoupled(new VRequest(dut.parameter.xLen)))
  val resp:             ValidIO[VResponse] = IO(Flipped(Valid(new VResponse(dut.parameter.xLen))))
  val csrInterface:     CSRInterface = IO(Output(new CSRInterface(dut.parameter.laneParam.vlMaxBits)))
  val storeBufferClear: Bool = IO(Output(Bool()))
  val tlPort:           Vec[TLBundle] = IO(Vec(dut.parameter.memoryBankSize, Flipped(dut.parameter.tlParam.bundle())))
  storeBufferClear := true.B

  val peekLsuEnq = Module(new PeekLsuEnq(PeekLsuEnqParameter(dut.parameter.lsuParam.lsuMSHRSize, latPeekLsuEnq)))
  peekLsuEnq.clock.ref := genClock
  peekLsuEnq.enq.ref := tapAndRead(dut.lsu.reqEnq).asUInt

  dut.lsu.writeQueueVec.zipWithIndex.foreach {
    case (mshr, i) =>
      val peekWriteQueue = Module(new PeekWriteQueue(PeekWriteQueueParameter(
        dut.parameter.vrfParam.regNumBits,
        dut.parameter.laneNumber,
        dut.parameter.vrfParam.vrfOffsetBits,
        dut.parameter.vrfParam.instructionIndexBits,
        dut.parameter.vrfParam.datapathWidth,
        negLatPeekWriteQueue,
      )))
      peekWriteQueue.mshrIdx.ref := i.U
      peekWriteQueue.clock.ref := genClock
      peekWriteQueue.data_vd.ref := tapAndRead(mshr.io.enq.bits.data.vd)
      peekWriteQueue.data_offset.ref := tapAndRead(mshr.io.enq.bits.data.offset)
      peekWriteQueue.data_mask.ref := tapAndRead(mshr.io.enq.bits.data.mask)
      peekWriteQueue.data_data.ref := tapAndRead(mshr.io.enq.bits.data.data)
      peekWriteQueue.data_instruction.ref := tapAndRead(mshr.io.enq.bits.data.instructionIndex)
      peekWriteQueue.writeValid.ref := tapAndRead(mshr.io.enq.valid)
      peekWriteQueue.targetLane.ref := tapAndRead(mshr.io.enq.bits.targetLane)
  }

  dut.laneVec.zipWithIndex.foreach {
    case (lane, i) =>
      val peekVrfWrite = Module(new PeekVrfWrite(PeekVrfWriteParameter(
        dut.parameter.vrfParam.regNumBits,
        dut.parameter.laneNumber,
        dut.parameter.vrfParam.vrfOffsetBits,
        dut.parameter.vrfParam.instructionIndexBits,
        dut.parameter.vrfParam.datapathWidth,
        negLatPeekWriteQueue,
      )))
      peekVrfWrite.landIdx.ref := i.U
      peekVrfWrite.valid.ref := tapAndRead(lane.vrf.write.valid)
      peekVrfWrite.clock.ref := genClock
      peekVrfWrite.request_vd.ref := tapAndRead(lane.vrf.write.bits.vd)
      peekVrfWrite.request_offset.ref := tapAndRead(lane.vrf.write.bits.offset)
      peekVrfWrite.request_mask.ref := tapAndRead(lane.vrf.write.bits.mask)
      peekVrfWrite.request_data.ref := tapAndRead(lane.vrf.write.bits.data)
      peekVrfWrite.request_instruction.ref := tapAndRead(lane.vrf.write.bits.instructionIndex)
  }

  val pokeInst = Module(new PokeInst(PokeInstParameter(dut.parameter.xLen, dut.parameter.laneParam.vlMaxBits, latPokeInst)))
  pokeInst.clock.ref := genClock
  pokeInst.respValid.ref := tapAndRead(resp.valid)
  pokeInst.response_data.ref := tapAndRead(resp.bits.data)
  pokeInst.response_vxsat.ref := tapAndRead(resp.bits.vxsat)
  pokeInst.response_rd_valid.ref := tapAndRead(resp.bits.rd.valid)
  pokeInst.response_rd_bits.ref := tapAndRead(resp.bits.rd.bits)
  pokeInst.response_mem.ref := tapAndRead(resp.bits.mem)
  bore(csrInterface.vl) := pokeInst.csrInterface_vl.ref
  bore(csrInterface.vStart) := pokeInst.csrInterface_vStart.ref
  bore(csrInterface.vlmul) := pokeInst.csrInterface_vlMul.ref
  bore(csrInterface.vSew) := pokeInst.csrInterface_vSew.ref
  bore(csrInterface.vxrm) := pokeInst.csrInterface_vxrm.ref
  bore(csrInterface.vta) := pokeInst.csrInterface_vta.ref
  bore(csrInterface.vma) := pokeInst.csrInterface_vma.ref
  bore(csrInterface.ignoreException) := pokeInst.csrInterface_ignoreException.ref
  bore(req.bits.instruction) := pokeInst.request_instruction.ref
  bore(req.bits.src1Data) := pokeInst.request_src1Data.ref
  bore(req.bits.src2Data) := pokeInst.request_src2Data.ref
  bore(req.valid) := pokeInst.instructionValid.ref

  val peekIssue = Module(new PeekIssue(PeekIssueParameter(dut.parameter.instructionIndexBits, latPeekIssue)))
  peekIssue.clock.ref := genClock
  peekIssue.ready.ref := tapAndRead(req.ready)
  peekIssue.issueIdx.ref := tapAndRead(dut.instructionCounter)

  tlPort.zipWithIndex.foreach {
    case (bundle, idx) =>
      val peek = Module(new PeekTL(dut.parameter.tlParam.bundle(), latPeekTL))
      peek.clock.ref := genClock
      peek.channel.ref := idx.U
      peek.aBits_opcode.ref := tapAndRead(bundle.a.bits.opcode)
      peek.aBits_param.ref := tapAndRead(bundle.a.bits.param)
      peek.aBits_size.ref := tapAndRead(bundle.a.bits.size)
      peek.aBits_source.ref := tapAndRead(bundle.a.bits.source)
      peek.aBits_address.ref := tapAndRead(bundle.a.bits.address)
      peek.aBits_mask.ref := tapAndRead(bundle.a.bits.mask)
      peek.aBits_data.ref := tapAndRead(bundle.a.bits.data)
      peek.aBits_corrupt.ref := tapAndRead(bundle.a.bits.corrupt)

      peek.aValid.ref := tapAndRead(bundle.a.valid)
      peek.dReady.ref := tapAndRead(bundle.d.ready)

      val poke = Module(new PokeTL(dut.parameter.tlParam.bundle(), latPokeTL))
      poke.clock.ref := genClock
      poke.channel.ref := idx.U
      bore(bundle.d.bits.opcode) := poke.dBits_opcode.ref
      bore(bundle.d.bits.param) := poke.dBits_param.ref
      bore(bundle.d.bits.sink) := poke.dBits_sink.ref
      bore(bundle.d.bits.source) := poke.dBits_source.ref
      bore(bundle.d.bits.size) := poke.dBits_size.ref
      bore(bundle.d.bits.denied) := poke.dBits_denied.ref
      bore(bundle.d.bits.data) := poke.dBits_data.ref
      bore(bundle.d.bits.corrupt) := poke.dBits_corrupt.ref
      bore(bundle.d.valid) := poke.dValid.ref
      poke.dReady.ref := tapAndRead(bundle.d.ready)
      bore(bundle.a.ready) := poke.aReady.ref
  }
}
