// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.util.{Decoupled, Valid, log2Ceil}
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
                            hartIdLen: Int,
                            // max slave address
                            paddrBits: Int
                          )
  extends SerializableModuleParameter {
  private def hasInstructionSet(setName: String): Boolean =
    instructions.flatMap(_.instructionSets.map(_.name)).contains(setName)

  private def hasInstruction(instName: String): Boolean = instructions.map(_.name).contains(instName)

  // static to false for now
  val hasBeu: Boolean = false
  def b2i(in: Boolean) = if (in) 1 else 0
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

  val resetVectorLen: Int = {
    val externalLen = paddrBits
    require(externalLen <= xLen, s"External reset vector length ($externalLen) must be <= XLEN ($xLen)")
    require(externalLen <= vaddrBitsExtended, s"External reset vector length ($externalLen) must be <= virtual address bit width ($vaddrBitsExtended)")
    externalLen
  }
  val nLocalInterrupts: Int = 0
  val pgIdxBits: Int = 12
  val pgLevels: Int = if (xLen == 64) 3 /* Sv39 */ else 2 /* Sv32 */
  val pgLevelBits: Int = 10 - log2Ceil(xLen / 32)
  val maxSVAddrBits: Int = pgIdxBits + pgLevels * pgLevelBits
  val maxHypervisorExtraAddrBits: Int = 2
  val hypervisorExtraAddrBits: Int = {
    if (usingHypervisor) maxHypervisorExtraAddrBits
    else 0
  }
  val maxHVAddrBits: Int = maxSVAddrBits + hypervisorExtraAddrBits
  val vaddrBits: Int = if (usingVM) {
    val v = maxHVAddrBits
    require(v == xLen || xLen > v && v > paddrBits)
    v
  } else {
    // since virtual addresses sign-extend but physical addresses
    // zero-extend, make room for a zero sign bit for physical addresses
    (paddrBits + 1) min xLen
  }
  val vpnBits: Int = vaddrBits - pgIdxBits
  val ppnBits: Int = paddrBits - pgIdxBits
  val vpnBitsExtended: Int = vpnBits + (if (vaddrBits < xLen) b2i(usingHypervisor) + 1 else 0)

  val vaddrBitsExtended: Int = vpnBitsExtended + pgIdxBits
  val asidBits: Int = 0
  // btb entries
  val entries: Int = 28
  val bhtHistoryLength: Option[Int] = Some(8)
  val bhtCounterLength: Option[Int] = Some(1)
  // if (useCompressed) 16 else 32; useCompressed = true
  val coreInstBits: Int = 16
  val coreMaxAddrBits: Int = paddrBits max vaddrBitsExtended
  // todo: 64 -> dcacheParan.blockBytes
  val blockOffBits = log2Ceil(64)
  // todo: 64 -> dcacheParan.nset
  val idxBits = log2Ceil(64)
  // dCache untage bits
  val untagBits: Int = blockOffBits + idxBits
  val dcacheReqTagBits: Int = 6
  val usingDataScratchpad: Boolean = false
  val buildRocc: Boolean = false
  //   //                  Core   PTW                DTIM                    coprocessors
  //  def dcacheArbPorts = 1 + usingVM.toInt + usingDataScratchpad.toInt + p(BuildRoCC).size + (tileParams.core.useVector && tileParams.core.vectorUseDCache).toInt
  val dcacheArbPorts: Int = 1 + b2i(usingVM) + b2i(usingDataScratchpad) + b2i(buildRocc)
  // def vMemDataBits = if (usingVector) coreParams.vMemDataBits(is 0) else 0
  val vMemDataBits = 0
  val coreDataBits: Int = xLen max fLen.getOrElse(0) max vMemDataBits
  val coreDataBytes: Int = coreDataBits / 8
  val separateUncachedResp: Boolean = ???
  val minPgLevels: Int = {
    val res = xLen match { case 32 => 2; case 64 => 3 }
    require(pgLevels >= res)
    res
  }
  val maxPAddrBits: Int = {
    require(xLen == 32 || xLen == 64, s"Only XLENs of 32 or 64 are supported, but got $xLen")
    xLen match { case 32 => 34; case 64 => 56 }
  }
  val nPMPs: Int = 8
  val nBreakpoints: Int = 1
  val useBPWatch: Boolean = false
  val mcontextWidth: Int = 0
  val scontextWidth: Int = 0

  //
  val csrParameter: CSRParameter = ???
  val decoderParameter: DecoderParameter = ???
  val iBufParameter: IBufParameter = ???
  val breakpointUnitParameter: BreakpointUnitParameter = BreakpointUnitParameter(nBreakpoints, xLen, useBPWatch, vaddrBits, mcontextWidth, scontextWidth)
  val aluParameter: ALUParameter = ALUParameter(xLen)
  val mulDivParameter: MulDivParameter = ???
  val mulParameter: Option[MulParameter] = ???
}

/** The Interface of [[Rocket]].
 * The [[Rocket]] is the public
 */
class RocketInterface(parameter: RocketParameter) extends Bundle {
  val hartid = IO(Flipped(UInt(parameter.hartIdLen.W)))
  val interrupts = IO(Flipped(new TileInterrupts(parameter.usingSupervisor, parameter.nLocalInterrupts, parameter.usingNMI, parameter.resetVectorLen)))
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
