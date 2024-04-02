// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import org.chipsalliance.t1.ipemu.dpi._
import org.chipsalliance.t1.rtl.{T1, T1Parameter}

class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter]) extends RawModule {
  // Scheduler to schedule different DPI calls for online difftest,
  // TODO: after switching to offline version, everything should be cleaned up.
  val clockRate = 5
  val latPokeInst = 1
  val negLatPeekWriteQueue = 1
  val latPeekLsuEnq = 1
  val latPeekIssue = 2
  val latPeekTL = 2
  val latPokeTL = 1

  val clockGen = Module(new ClockGen(ClockGenParameter(clockRate)))
  val dpiDumpWave = Module(new DpiDumpWave)
  val dpiFinish = Module(new DpiFinish)
  val dpiError = Module(new DpiError)
  val dpiInit = Module(new DpiInitCosim)
  val dpiTimeoutCheck = Module(new TimeoutCheck(TimeoutCheckParameter(clockRate)))

  val clock: Clock = clockGen.clock.asClock
  val reset: Bool = clockGen.reset

  val dut: T1 = withClockAndReset(clock, reset)(Module(generator.module()))

  // TODO: remove me to connect signals
  chisel3.reflect.DataMirror.modulePorts(dut)
    .filter(p => p._1 != "laneProbes").foreach(_._2 <> DontCare)

  val laneProbes = Wire(dut.laneProbes.cloneType)
  dontTouch(laneProbes)
  laneProbes :#= probe.read(dut.laneProbes)
  // Driver to Scalar Core
  // val pokeInst = Module(new PokeInst(PokeInstParameter(dut.parameter.xLen, dut.parameter.laneParam.vlMaxBits, latPokeInst)))
  // val tlPoke = Seq.fill(dut.parameter.lsuBankParameters.size)(Module(new PokeTL(dut.parameter.tlParam.bundle(), latPokeTL)))

  // DPI Monitors
  // val tlPeek = Seq.fill(dut.parameter.lsuBankParameters.size)(Module(new PeekTL(dut.parameter.tlParam.bundle(), latPeekTL)))

  // Monitors
  // TODO: These monitors should be purged out after offline difftest is landed
  // val peekIssue = Module(new PeekIssue(PeekIssueParameter(dut.parameter.instructionIndexBits, latPeekIssue)))
  // val peekLsuEnq = Module(new PeekLsuEnq(PeekLsuEnqParameter(dut.parameter.lsuParameters.lsuMSHRSize, latPeekLsuEnq)))
  // val peekWriteQueue = Seq.tabulate(dut.parameter.laneNumber)( laneIdx => Module(new PeekWriteQueue(PeekWriteQueueParameter(
  //   dut.parameter.vrfParam.regNumBits,
  //   dut.parameter.laneNumber,
  //   dut.parameter.vrfParam.vrfOffsetBits,
  //   dut.parameter.vrfParam.instructionIndexBits,
  //   dut.parameter.vrfParam.datapathWidth,
  //   negLatPeekWriteQueue,
  // ))))
  // val peekVRFWriteQueue = Seq.tabulate(dut.parameter.laneNumber)( laneIdx => Module(new PeekVrfWrite(PeekVrfWriteParameter(
  //   dut.parameter.vrfParam.regNumBits,
  //   dut.parameter.laneNumber,
  //   dut.parameter.vrfParam.vrfOffsetBits,
  //   dut.parameter.vrfParam.instructionIndexBits,
  //   dut.parameter.vrfParam.datapathWidth,
  //   negLatPeekWriteQueue,
  // ))))
}