// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.ipemu

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import org.chipsalliance.t1.rtl.{T1, T1Parameter}

class TestBench(generator: SerializableModuleGenerator[T1, T1Parameter]) extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val dut: T1 = withClockAndReset(clock, reset)(Module(generator.module()))

  val verificationModule = Module(new VerificationModule(dut))
  dut.request <> verificationModule.req
  dut.response <> verificationModule.resp
  dut.csrInterface <> verificationModule.csrInterface
  dut.storeBufferClear <> verificationModule.storeBufferClear
  dut.memoryPorts <> verificationModule.tlPort
  clock := verificationModule.clock
  reset := verificationModule.reset
 }
