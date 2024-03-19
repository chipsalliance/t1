// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu

import chisel3._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.subsystem._

class TestBench(implicit p: Parameters) extends RawModule {
  val ldut = LazyModule(new T1Subsystem)
  val dut = Module(ldut.module)
  val verificationModule = Module(new VerificationModule(ldut))

  // connect clock and reset
  ldut.clock := verificationModule.clock
  ldut.reset := verificationModule.reset

  // connect resetVector
  dut.reset_vector.head := verificationModule.resetVector

  // connect tl
  ldut.vectorPorts.zip(verificationModule.tlPort).foreach {
    case (dutPort, verificationPort) =>
      dutPort.apply(0) <> verificationPort
  }
  ldut.mmioPort.apply(0) <> verificationModule.mmioPort
  ldut.scalarPort.apply(0) <> verificationModule.scarlarPort
}
