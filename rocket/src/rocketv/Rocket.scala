// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.util.{Decoupled, Valid}
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.t1.rockettile.{VectorRequest, VectorResponse}

/** This is all user configurable parameters for the [[Rocket]]
 * It should be serializable and providing API to downstream users.
 * The interface should be stable for linking.
 *
 * @param instructions all instructions that supports
 * @param pipelinedMul uarch config:
 *                     enabled: add [[Mul]] in [[Rocket]], [[MulDiv]] will only be a divider.
 *                     disabled: [[MulDiv]] will handle both mul and div instructions.
 * @param hartIdLen the size of hartId.
 */
case class RocketParameter(
                            instructions: Seq[Instruction],
                            pipelinedMul: Boolean,
                            hartIdLen: Int
                          )
  extends SerializableModuleParameter {
  private def hasInstructionSet(setName: String): Boolean =
    instructions.flatMap(_.instructionSets.map(_.name)).contains(setName)

  private def hasInstruction(instName: String): Boolean = instructions.map(_.name).contains(instName)

  // static to false for now
  val hasBeu: Boolean = false

  // todo: configured by [[RocketTileParameter]]
  // cfg.scratch.isEmpty && !node.edges.out(0).manager.managers.forall(m => !m.supportsAcquireB || !m.executable || m.regionType >= RegionType.TRACKED || m.regionType <= RegionType.IDEMPOTENT)
  val flushOnFenceI: Boolean = false

  val xLen: Int =
    (hasInstructionSet("rv32_i"), hasInstructionSet("rv64_i")) match {
      case (true, true) => throw new Exception("cannot support both rv32 and rv64 together")
      case (true, false) => 32
      case (false, true) => 64
      case (false, false) => throw new Exception("no basic instruction found.")
    }

  val fLen: Option[Int] =
    (
      hasInstructionSet("rv_f") || hasInstructionSet("rv64_f"),
      hasInstructionSet("rv_d") || hasInstructionSet("rv64_d")
    ) match {
      case (false, false) => None
      case (true, false) => Some(32)
      case (false, true) => Some(64)
      case (true, true) => Some(64)
    }

  val minFLen: Option[Int] =
    if (hasInstructionSet("rv_zfh") || hasInstructionSet("rv64_zfh") || hasInstructionSet("rv_d_zfh"))
      Some(16)
    else
      fLen

  val usingMulDiv = hasInstructionSet("rv_m") || hasInstructionSet("rv64_m")

  val usingAtomics = hasInstructionSet("rv_a") || hasInstructionSet("rv64_a")

  val usingVM = hasInstructionSet("sfence.vma")

  val usingSupervisor = hasInstruction("sret")

  // static to false for now
  val usingHypervisor = hasInstructionSet("rv_h") || hasInstructionSet("rv64_h")

  val usingDebug = hasInstructionSet("rv_sdext")

  // static to false for now
  val haveCease = hasInstruction("cease")

  // static to false for now
  val usingNMI = hasInstructionSet("rv_smrnmi")

  val usingVector = hasInstructionSet("rv_v")

  // ZICSR ZIFENCEI are always supported.
  val decoder = new org.chipsalliance.t1.rocketcore.InstructionDecoder(
    org.chipsalliance.t1.rocketcore.InstructionDecoderParameter(
      instructions,
      pipelinedMul,
      flushOnFenceI
    )
  )

  val fetchWidth: Int = 1

  val nLocalInterrupts: Int = ???
  val vaddrBitsExtended: Int = ???
  val vaddrBits: Int = ???
  val asidBits: Int = ???
  val entries: Int = ???
  val bhtHistoryLength: Option[Int] = ???
  val bhtCounterLength: Option[Int] = ???
  val coreInstBits: Int = ???
  val coreMaxAddrBits: Int = ???
  val untagBits: Int = ???
  val pgIdxBits: Int = ???
  val dcacheReqTagBits: Int = ???
  val dcacheArbPorts: Int = ???
  val coreDataBytes: Int = ???
  val paddrBits: Int = ???
  val separateUncachedResp: Boolean = ???
  val pgLevels: Int = ???
  val minPgLevels: Int = ???
  val maxPAddrBits: Int = ???
  val nPMPs: Int = ???
  val nBreakpoints: Int = ???

  //
  val csrParameter: CSRParameter = ???
  val decoderParameter: DecoderParameter = ???
  val iBufParameter: IBufParameter = ???
  val breakpointUnitParameter: BreakpointUnitParameter = ???
  val aluParameter: ALUParameter = ???
  val mulDivParameter: MulDivParameter = ???
  val mulParameter: Option[MulParameter] = ???
}

/** The Interface of [[Rocket]].
 * The [[Rocket]] is the public
 */
class RocketInterface(parameter: RocketParameter) extends Bundle {
  val hartid = IO(Flipped(UInt(parameter.hartIdLen.W)))
  val interrupts = IO(Flipped(new TileInterrupts(parameter.usingSupervisor, parameter.nLocalInterrupts)))
  val imem = IO(
    new FrontendIO(
      parameter.vaddrBitsExtended,
      parameter.vaddrBits,
      parameter.asidBits,
      parameter.fetchWidth,
      parameter.entries,
      parameter.bhtHistoryLength,
      parameter.bhtCounterLength,
      parameter.coreInstBits
    )
  )
  val dmem = IO(
    new HellaCacheIO(
      parameter.coreMaxAddrBits,
      parameter.usingVM,
      parameter.untagBits,
      parameter.pgIdxBits,
      parameter.dcacheReqTagBits,
      parameter.dcacheArbPorts,
      parameter.coreDataBytes,
      parameter.paddrBits,
      parameter.vaddrBitsExtended,
      parameter.separateUncachedResp
    )
  )
  val ptw = IO(
    Flipped(
      new DatapathPTWIO(
        parameter.pgLevels,
        parameter.minPgLevels,
        parameter.xLen,
        parameter.maxPAddrBits,
        parameter.pgIdxBits: Int,
        parameter.vaddrBits: Int,
        parameter.asidBits: Int,
        parameter.nPMPs,
        parameter.paddrBits: Int
      )
    )
  )
  val fpu = parameter.fLen.map(fLen => IO(Flipped(new FPUCoreIO(parameter.hartIdLen, parameter.xLen, fLen))))
  val bpwatch = IO(Output(Vec(parameter.nBreakpoints, new BPWatch)))
  val cease = IO(Output(Bool()))
  val wfi = IO(Output(Bool()))
  val traceStall = IO(Input(Bool()))
  val t1Request = Option.when(parameter.usingVector)(IO(Valid(new VectorRequest(parameter.xLen))))
  val t1Response = Option.when(parameter.usingVector)(IO(Flipped(Decoupled(new VectorResponse(parameter.xLen)))))
}

/** The [[Rocket]] is the next version of the RocketCore,
 * All micro architectures are from the original RocketCore.
 * The development of [[Rocket]] happens in the T1 project.
 * It will be moved to the standalone pacakge until it get verified.
 *
 * Here are some basic idea of [[Rocket]],
 *  - it should be linkable by providing an verification constraint to other components.
 *  - open expose [[RocketParameter]] and [[RocketInterface]] to users, all internal API are subject to be changed.
 *  - There is no coherent support for the [[Rocket]] until chipsalliance having the CHI interconnect and cache IP.
 *  - The in-tile components contains Frontend, HellaCache, FPU, T1, but the memory subsystem only supports AXI.
 */
class Rocket(val parameter: RocketParameter)
  extends FixedIORawModule(new RocketInterface(parameter))
    with SerializableModule[RocketParameter] {
  val csr: Instance[CSR] = Instantiate(new CSR(parameter.csrParameter))
  val decoder: Instance[Decoder] = Instantiate(new Decoder(parameter.decoderParameter))
  val instructionBuffer: Instance[IBuf] = Instantiate(new IBuf(parameter.iBufParameter))
  val breakpointUnit: Instance[BreakpointUnit] = Instantiate(new BreakpointUnit(parameter.breakpointUnitParameter))
  val alu: Instance[ALU] = Instantiate(new ALU(parameter.aluParameter))
  val mulDiv: Instance[MulDiv] = Instantiate(new MulDiv(parameter.mulDivParameter))
  val mul: Option[Instance[Mul]] = parameter.mulParameter.map(p => Instantiate(new Mul(p)))
}
