// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{Instantiate, instantiable}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._

object IBufParameter {
  implicit def rwP: upickle.default.ReadWriter[IBufParameter] = upickle.default.macroRW[IBufParameter]
}

case class IBufParameter(
                          useAsyncReset:     Boolean,
                          xLen: Int,
                          usingCompressed:   Boolean,
                          vaddrBits:         Int,
                          entries:           Int,
                          // TODO: have a better way to calculate it, like what we did in the CSR...
                          vaddrBitsExtended: Int,
                          bhtHistoryLength:  Option[Int],
                          bhtCounterLength:  Option[Int],
                          fetchWidth:        Int
                        ) extends SerializableModuleParameter {
  val retireWidth: Int = 1
  val coreInstBits: Int = if (usingCompressed) 16 else 32
  val coreInstBytes: Int = coreInstBits / 8
}

class IBufInterface(parameter: IBufParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val imem = Flipped(
    Decoupled(
      new FrontendResp(
        parameter.vaddrBits,
        parameter.entries,
        parameter.bhtHistoryLength,
        parameter.bhtCounterLength,
        parameter.vaddrBitsExtended,
        parameter.coreInstBits,
        parameter.fetchWidth
      )
    )
  )
  val kill = Input(Bool())
  val pc = Output(UInt(parameter.vaddrBitsExtended.W))
  val btb_resp = Output(
    new BTBResp(
      parameter.vaddrBits,
      parameter.entries,
      parameter.fetchWidth,
      parameter.bhtHistoryLength,
      parameter.bhtCounterLength
    )
  )
  // 4. Give out the instruction to Decode.
  val inst = Vec(parameter.retireWidth, Decoupled(new Instruction))
}

@instantiable
class IBuf(val parameter: IBufParameter)
  extends FixedIORawModule(new IBufInterface(parameter))
    with SerializableModule[IBufParameter]
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val xLen = parameter.xLen
  val fetchWidth = parameter.fetchWidth
  val vaddrBits = parameter.vaddrBits
  val entries = parameter.entries
  val bhtHistoryLength = parameter.bhtHistoryLength
  val bhtCounterLength = parameter.bhtCounterLength
  val coreInstBytes = parameter.coreInstBytes
  val vaddrBitsExtended = parameter.vaddrBitsExtended
  val coreInstBits = parameter.coreInstBits
  val retireWidth = parameter.retireWidth
  val usingCompressed = parameter.usingCompressed

  val n = fetchWidth - 1
  val nBufValid = if (n == 0) 0.U else RegInit(init = 0.U(log2Ceil(fetchWidth).W))
  val buf = Reg(chiselTypeOf(io.imem.bits))
  val ibufBTBResp = Reg(
    new BTBResp(
      vaddrBits,
      entries,
      fetchWidth,
      bhtHistoryLength,
      bhtCounterLength
    )
  )
  val pcWordMask = (coreInstBytes * fetchWidth - 1).U(vaddrBitsExtended.W)
  val pcWordBits = io.imem.bits.pc(log2Ceil(fetchWidth*coreInstBytes)-1, log2Ceil(coreInstBytes))
  val nReady = WireDefault(0.U(log2Ceil(fetchWidth + 1).W))
  val nIC = Mux(io.imem.bits.btb.taken, io.imem.bits.btb.bridx +& 1.U, fetchWidth.U) - pcWordBits
  val nICReady = nReady - nBufValid
  val nValid = Mux(io.imem.valid, nIC, 0.U) + nBufValid
  io.imem.ready := io.inst(0).ready && nReady >= nBufValid && (nICReady >= nIC || n.U >= nIC - nICReady)

  if (n > 0) {
    when(io.inst(0).ready) {
      nBufValid := Mux((nReady >= nBufValid) || nBufValid === 0.U, 0.U, nBufValid - nReady)
      if (n > 1) when(nReady > 0.U && nReady < nBufValid) {
        val shiftedBuf =
          shiftInsnRight(buf.data(n * coreInstBits - 1, coreInstBits), (nReady - 1.U)(log2Ceil(n - 1) - 1, 0))
        buf.data := Cat(
          buf.data(n * coreInstBits - 1, (n - 1) * coreInstBits),
          shiftedBuf((n - 1) * coreInstBits - 1, 0)
        )
        buf.pc := buf.pc & ~pcWordMask | (buf.pc + (nReady << log2Ceil(coreInstBytes))) & pcWordMask
      }
      when(io.imem.valid && nReady >= nBufValid && nICReady < nIC && n.U >= nIC - nICReady) {
        val shamt = pcWordBits + nICReady
        nBufValid := nIC - nICReady
        buf := io.imem.bits
        buf.data := shiftInsnRight(io.imem.bits.data, shamt)(n * coreInstBits - 1, 0)
        buf.pc := io.imem.bits.pc & ~pcWordMask | (io.imem.bits.pc + (nICReady << log2Ceil(coreInstBytes))) & pcWordMask
        ibufBTBResp := io.imem.bits.btb
      }
    }
    when(io.kill) {
      nBufValid := 0.U
    }
  }

  val icShiftAmt = (fetchWidth.U + nBufValid - pcWordBits)(log2Ceil(fetchWidth), 0)
  val icData =
    shiftInsnLeft(Cat(io.imem.bits.data, Fill(fetchWidth, io.imem.bits.data(coreInstBits - 1, 0))), icShiftAmt)(
      3 * fetchWidth * coreInstBits - 1,
      2 * fetchWidth * coreInstBits
    )
  val icMask =
    (~0.U((fetchWidth * coreInstBits).W) << (nBufValid << log2Ceil(coreInstBits)))(fetchWidth * coreInstBits - 1, 0)
  val inst = icData & icMask | buf.data & ~icMask

  val valid = (UIntToOH(nValid) - 1.U)(fetchWidth - 1, 0)
  val bufMask = UIntToOH(nBufValid) - 1.U
  val xcpt = (0 until bufMask.getWidth).map(i => Mux(bufMask(i), buf.xcpt, io.imem.bits.xcpt))
  val buf_replay = Mux(buf.replay, bufMask, 0.U)
  val ic_replay = buf_replay | Mux(io.imem.bits.replay, valid & ~bufMask, 0.U)
  assert(!io.imem.valid || !io.imem.bits.btb.taken || io.imem.bits.btb.bridx >= pcWordBits)

  io.btb_resp := io.imem.bits.btb
  io.pc := Mux(nBufValid > 0.U, buf.pc, io.imem.bits.pc)
  expand(0, 0.U, inst)

  def expand(i: Int, j: UInt, curInst: UInt): Unit = if (i < retireWidth) {
    // TODO: Dont instantiate it unless usingCompressed is true
    val exp = Instantiate(new RVCExpander(RVCExpanderParameter(xLen, usingCompressed)))
    exp.io.in := curInst
    io.inst(i).bits.inst := exp.io.out
    io.inst(i).bits.raw := curInst

    if (usingCompressed) {
      val replay = ic_replay(j) || (!exp.io.rvc && ic_replay(j + 1.U))
      val full_insn = exp.io.rvc || valid(j + 1.U) || buf_replay(j)
      io.inst(i).valid := valid(j) && full_insn
      io.inst(i).bits.xcpt0 := VecInit(xcpt)(j)
      io.inst(i).bits.xcpt1 := Mux(exp.io.rvc, 0.U, VecInit(xcpt)(j + 1.U).asUInt).asTypeOf(new FrontendExceptions)
      io.inst(i).bits.replay := replay
      io.inst(i).bits.rvc := exp.io.rvc

      when((bufMask(j) && exp.io.rvc) || bufMask(j + 1.U)) { io.btb_resp := ibufBTBResp }

      when(full_insn && ((i == 0).B || io.inst(i).ready)) { nReady := Mux(exp.io.rvc, j + 1.U, j + 2.U) }

      expand(i + 1, Mux(exp.io.rvc, j + 1.U, j + 2.U), Mux(exp.io.rvc, curInst >> 16, curInst >> 32))
    } else {
      when((i == 0).B || io.inst(i).ready) { nReady := (i + 1).U }
      io.inst(i).valid := valid(i)
      io.inst(i).bits.xcpt0 := xcpt(i)
      io.inst(i).bits.xcpt1 := 0.U.asTypeOf(new FrontendExceptions)
      io.inst(i).bits.replay := ic_replay(i)
      io.inst(i).bits.rvc := false.B

      expand(i + 1, null, curInst >> 32)
    }
  }

  def shiftInsnLeft(in: UInt, dist: UInt): UInt = {
    val r = in.getWidth / coreInstBits
    require(in.getWidth % coreInstBits == 0)
    val data = Cat(Fill((1 << (log2Ceil(r) + 1)) - r, in >> (r - 1) * coreInstBits), in)
    data << (dist << log2Ceil(coreInstBits))
  }

  def shiftInsnRight(in: UInt, dist: UInt): UInt = {
    val r = in.getWidth / coreInstBits
    require(in.getWidth % coreInstBits == 0)
    val data = Cat(Fill((1 << (log2Ceil(r) + 1)) - r, in >> (r - 1) * coreInstBits), in)
    data >> (dist << log2Ceil(coreInstBits))
  }
}
