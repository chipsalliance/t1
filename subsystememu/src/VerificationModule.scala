// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.subsystememu

import chisel3._
import chisel3.probe._
import chisel3.util.experimental.BoringUtils.{bore, tapAndRead}
import org.chipsalliance.t1.subsystem.T1Subsystem
import org.chipsalliance.t1.subsystememu.dpi._

class VerificationModule(dut: T1Subsystem) extends RawModule {
  override val desiredName = "VerificationModule"

  // config
  val clockRate = 5
  val latPokeTL = 1
  val latPeekTL = 2

  val clockGen = Module(new ClockGen(ClockGenParameter(clockRate)))

  val dpiDumpWave = Module(new DpiDumpWave)
  val dpiFinish = Module(new DpiFinish)
  val dpiError = Module(new DpiError)
  val dpiInit = Module(new DpiInitCosim)
  val dpiTimeoutCheck = Module(new TimeoutCheck(new TimeoutCheckParameter(clockRate)))

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))
  val resetVector = IO(Output(UInt(32.W)))

  val genClock = read(clockGen.clock)
  clock := genClock.asClock
  reset := read(clockGen.reset)
  resetVector := dpiInit.resetVector.ref

  // clone IO from Subsystem(I need types)
  val tlPort      = IO(Vec(dut.vectorPorts.size, Flipped(dut.vectorPorts.apply(0).head.cloneType)))
  val mmioPort    = IO(Flipped(dut.mmioPort.apply(0).cloneType))
  val scarlarPort = IO(Flipped(dut.scalarPort.apply(0).cloneType))

  tlPort.zipWithIndex.foreach { // connect tl
    case (bundle, idx) =>
      val peek = Module(new PeekTL(bundle.cloneType, latPeekTL, "PeekVectorTL"))
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

      val poke = Module(new PokeTL(bundle.cloneType, latPokeTL, "PokeVectorTL"))
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
      bore(bundle.a.ready) := poke.aReady.ref
  }

  { // connect MMIO
    val peek = Module(new PeekTL(mmioPort.cloneType, latPeekTL, "PeekMMIOTL"))
    peek.clock.ref := genClock
    peek.channel.ref := 0.U
    peek.aBits_opcode.ref := tapAndRead(mmioPort.a.bits.opcode)
    peek.aBits_param.ref := tapAndRead(mmioPort.a.bits.param)
    peek.aBits_size.ref := tapAndRead(mmioPort.a.bits.size)
    peek.aBits_source.ref := tapAndRead(mmioPort.a.bits.source)
    peek.aBits_address.ref := tapAndRead(mmioPort.a.bits.address)
    peek.aBits_mask.ref := tapAndRead(mmioPort.a.bits.mask)
    peek.aBits_data.ref := tapAndRead(mmioPort.a.bits.data)
    peek.aBits_corrupt.ref := tapAndRead(mmioPort.a.bits.corrupt)
    peek.aValid.ref := tapAndRead(mmioPort.a.valid)
    peek.dReady.ref := tapAndRead(mmioPort.d.ready)

    val poke = Module(new PokeTL(mmioPort.cloneType, latPokeTL, "PokeMMIOTL"))
    poke.clock.ref := genClock
    poke.channel.ref := 0.U
    bore(mmioPort.d.bits.opcode) := poke.dBits_opcode.ref
    bore(mmioPort.d.bits.param) := poke.dBits_param.ref
    bore(mmioPort.d.bits.sink) := poke.dBits_sink.ref
    bore(mmioPort.d.bits.source) := poke.dBits_source.ref
    bore(mmioPort.d.bits.size) := poke.dBits_size.ref
    bore(mmioPort.d.bits.denied) := poke.dBits_denied.ref
    bore(mmioPort.d.bits.data) := poke.dBits_data.ref
    bore(mmioPort.d.bits.corrupt) := poke.dBits_corrupt.ref
    bore(mmioPort.d.valid) := poke.dValid.ref
    bore(mmioPort.a.ready) := poke.aReady.ref
  }

  { // connect scalar
    val peek = Module(new PeekTL(scarlarPort.cloneType, latPeekTL, "PeekScarlarTL"))
    peek.clock.ref := genClock
    peek.channel.ref := 0.U
    peek.aBits_opcode.ref := tapAndRead(scarlarPort.a.bits.opcode)
    peek.aBits_param.ref := tapAndRead(scarlarPort.a.bits.param)
    peek.aBits_size.ref := tapAndRead(scarlarPort.a.bits.size)
    peek.aBits_source.ref := tapAndRead(scarlarPort.a.bits.source)
    peek.aBits_address.ref := tapAndRead(scarlarPort.a.bits.address)
    peek.aBits_mask.ref := tapAndRead(scarlarPort.a.bits.mask)
    peek.aBits_data.ref := tapAndRead(scarlarPort.a.bits.data)
    peek.aBits_corrupt.ref := tapAndRead(scarlarPort.a.bits.corrupt)
    peek.aValid.ref := tapAndRead(scarlarPort.a.valid)
    peek.dReady.ref := tapAndRead(scarlarPort.d.ready)

    val poke = Module(new PokeTL(scarlarPort.cloneType, latPokeTL, "PokeScarlarTL"))
    poke.clock.ref := genClock
    poke.channel.ref := 0.U
    bore(scarlarPort.d.bits.opcode) := poke.dBits_opcode.ref
    bore(scarlarPort.d.bits.param) := poke.dBits_param.ref
    bore(scarlarPort.d.bits.sink) := poke.dBits_sink.ref
    bore(scarlarPort.d.bits.source) := poke.dBits_source.ref
    bore(scarlarPort.d.bits.size) := poke.dBits_size.ref
    bore(scarlarPort.d.bits.denied) := poke.dBits_denied.ref
    bore(scarlarPort.d.bits.data) := poke.dBits_data.ref
    bore(scarlarPort.d.bits.corrupt) := poke.dBits_corrupt.ref
    bore(scarlarPort.d.valid) := poke.dValid.ref
    bore(scarlarPort.a.ready) := poke.aReady.ref
  }
}
