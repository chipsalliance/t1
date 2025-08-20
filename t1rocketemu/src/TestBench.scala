// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.t1rocketemu

import chisel3._
import chisel3.experimental.dataview.DataViewable
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.hierarchy.Instance
import chisel3.experimental.{ExtModule, SerializableModule, SerializableModuleGenerator}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.util.circt.dpi.{RawClockedNonVoidFunctionCall, RawUnclockedNonVoidFunctionCall}
import chisel3.util.{HasExtModuleInline, PopCount, UIntToOH, Valid}
import org.chipsalliance.amba.axi4.bundle._
import org.chipsalliance.t1.t1rocketemu.dpi._
import org.chipsalliance.t1.tile.{T1RocketTile, T1RocketTileParameter}
import org.chipsalliance.t1.rtl.T1Probe
import org.chipsalliance.t1.tile.T1RocketProbe

@instantiable
class TestBenchOM extends Class {
  @public
  val t1RocketTile   = IO(Output(Property[AnyClassType]()))
  @public
  val t1RocketTileIn = IO(Input(Property[AnyClassType]()))
  t1RocketTile := t1RocketTileIn
}

class TestBench(val parameter: T1RocketTileParameter)
    extends RawModule
    with SerializableModule[T1RocketTileParameter]
    with ImplicitClock
    with ImplicitReset {
  layer.enable(layers.Verification)

  val omInstance: Instance[TestBenchOM] = Instantiate(new TestBenchOM)
  val omType:     ClassType             = omInstance.toDefinition.getClassType
  @public
  val om:         Property[ClassType]   = IO(Output(Property[omType.Type]()))
  om := omInstance.getPropertyReference

  val verbatimModule         = Module(
    new ExtModule(
      Map(
        "T1_VLEN"       -> parameter.vLen,
        "T1_DLEN"       -> parameter.dLen,
        "T1_LANE_WIDTH" -> parameter.laneScale * 32,
        "T1_SPIKE_ISA"  -> parameter.t1Parameter.spikeMarch.split("_").filter(!_.startsWith("xsfmm")).mkString("_")
      )
    ) {
      override def desiredName = "VerbatimModule"
      val clock                = IO(Output(Bool()))
      val reset                = IO(Output(Bool()))
      val initFlag             = IO(Output(Bool()))
      val idle                 = IO(Input(Bool()))
    }
  )
  def clock                  = verbatimModule.clock.asClock
  def reset                  = verbatimModule.reset
  def initFlag               = verbatimModule.initFlag
  override def implicitClock = verbatimModule.clock.asClock
  override def implicitReset = verbatimModule.reset
  val dut: Instance[T1RocketTile] = SerializableModuleGenerator(classOf[T1RocketTile], parameter).instance()
  omInstance.t1RocketTileIn := Property(dut.io.om.asAnyClassType)

  dut.io.clock := clock
  dut.io.reset := reset

  // control simulation
  val simulationTime: UInt = RegInit(0.U(64.W))
  simulationTime := simulationTime + 1.U

  // get resetVector from simulator
  dut.io.resetVector := RawClockedNonVoidFunctionCall("get_resetvector", Const(UInt(64.W)))(clock, initFlag)

  dut.io.hartid   := 0.U
  dut.io.debug    := 0.U
  dut.io.mtip     := 0.U
  dut.io.msip     := 0.U
  dut.io.meip     := 0.U
  dut.io.buserror := 0.U

  // memory driver
  Seq(
    dut.io.highBandwidthAXI,  // index 0
    dut.io.highOutstandingAXI // index 1
  ).map(_.viewAs[AXI4RWIrrevocableVerilog])
    .lazyZip(
      Seq("highBandwidthAXI", "highOutstandingAXI")
    )
    .zipWithIndex
    .foreach { case ((bundle: AXI4RWIrrevocableVerilog, channelName: String), index: Int) =>
      val agent = Module(
        new AXI4SlaveAgent(
          AXI4SlaveAgentParameter(
            name = channelName,
            axiParameter = bundle.parameter,
            outstanding = 4,
            readPayloadSize = 1,
            writePayloadSize = 1
          )
        )
      ).suggestName(s"axi4_channel${index}_${channelName}")
      agent.io.channel match {
        case io: AXI4RWIrrevocableVerilog => io <> bundle
      }
      agent.io.clock := clock
      agent.io.reset     := reset
      agent.io.channelId := index.U
      agent.io.gateRead  := false.B
      agent.io.gateWrite := false.B
    }

  val instFetchAXI   = dut.io.instructionFetchAXI.viewAs[AXI4ROIrrevocableVerilog]
  val instFetchAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "instructionFetchAXI",
        axiParameter = instFetchAXI.parameter,
        outstanding = 4,
        readPayloadSize = 8,
        writePayloadSize = 1
      )
    ).suggestName("axi4_channel2_instructionFetchAXI")
  )
  instFetchAgent.io.channel match {
    case io: AXI4ROIrrevocableVerilog => io <> instFetchAXI
  }
  instFetchAgent.io.clock := clock
  instFetchAgent.io.reset     := reset
  instFetchAgent.io.channelId := 2.U
  instFetchAgent.io.gateRead  := false.B
  instFetchAgent.io.gateWrite := false.B

  val loadStoreAXI   = dut.io.loadStoreAXI.viewAs[AXI4RWIrrevocableVerilog]
  val loadStoreAgent = Module(
    new AXI4SlaveAgent(
      AXI4SlaveAgentParameter(
        name = "loadStoreAXI",
        axiParameter = loadStoreAXI.parameter,
        outstanding = 4,
        // TODO: add payloadSize config to parameter
        readPayloadSize = 8, // todo: align with parameter in the future
        writePayloadSize = 8
      )
    ).suggestName("axi4_channel3_loadStoreAXI")
  )
  loadStoreAgent.io.channel match {
    case io: AXI4RWIrrevocableVerilog => io <> loadStoreAXI
  }
  loadStoreAgent.io.clock := clock
  loadStoreAgent.io.reset     := reset
  loadStoreAgent.io.channelId := 3.U
  loadStoreAgent.io.gateRead  := false.B
  loadStoreAgent.io.gateWrite := false.B

  // probes
  val t1RocketProbe  = probe.read(dut.io.t1RocketProbe)
  val rocketProbe    = t1RocketProbe.rocketProbe.suggestName(s"rocketProbe")
  val t1Probe        = t1RocketProbe.t1Probe.suggestName(s"t1Probe")
  val lsuProbe       = t1Probe.lsuProbe.suggestName(s"t1LSUProbe")
  val laneProbes     = t1Probe.laneProbes.zipWithIndex.map { case (p, idx) =>
    val wire = WireDefault(p).suggestName(s"lane${idx}Probe")
    wire
  }
  val laneVrfProbes  = t1Probe.laneProbes.map(_.vrfProbe).zipWithIndex.map { case (p, idx) =>
    val wire = WireDefault(p).suggestName(s"lane${idx}VrfProbe")
    wire
  }
  val storeUnitProbe = t1Probe.lsuProbe.storeUnitProbe.suggestName("storeUnitProbe")
  val otherUnitProbe = t1Probe.lsuProbe.otherUnitProbe.suggestName("otherUnitProbe")

  val log = SimLog.file("rtl_event.jsonl")

  // output the probes
  // rocket reg write
  when(
    rocketProbe.rfWen && !rocketProbe.vectorWriteRD && rocketProbe.rfWaddr =/= 0.U && !(rocketProbe.waitWen && rocketProbe.waitWaddr =/= 0.U)
  )(
    log.printf(
      cf"""{"event":"RegWrite","idx":${rocketProbe.rfWaddr},"data":"${rocketProbe.rfWdata}%x","cycle":${simulationTime}}\n"""
    )
  )

  when(rocketProbe.waitWen && !rocketProbe.isVectorCommit && rocketProbe.waitWaddr =/= 0.U)(
    log.printf(
      cf"""{"event":"RegWriteWait","idx":${rocketProbe.waitWaddr},"cycle":${simulationTime}}\n"""
    )
  )

  // [[option]] rocket fpu reg write
  parameter.fpuParameter.zip(t1RocketProbe.fpuProbe).zip(rocketProbe.fpuScoreboard).map {
    case ((fpuParameter, fpu), fpuScoreboard) => {
      val fpToIEEE           = Module(
        new FPToIEEE(
          FPToIEEEParameter(
            fpuParameter.useAsyncReset,
            fpuParameter.xLen,
            fpuParameter.fLen,
            fpuParameter.minFLen
          )
        )
      )
      val isVectorForLLWrite = RegNext(rocketProbe.vectorWriteFD, false.B)

      fpToIEEE.io.clock           := clock
      fpToIEEE.io.reset           := reset
      fpToIEEE.io.in.valid        := fpu.pipeWrite.rfWen || (fpu.loadOrVectorWrite.rfWen && !isVectorForLLWrite)
      fpToIEEE.io.in.bits.data    := Mux(fpu.pipeWrite.rfWen, fpu.pipeWrite.rfWdata, fpu.loadOrVectorWrite.rfWdata)
      fpToIEEE.io.in.bits.typeTag := Mux(
        fpu.pipeWrite.rfWen,
        fpu.pipeWrite.rfWtypeTag,
        fpu.loadOrVectorWrite.rfWtypeTag
      )

      val rfWen   = fpToIEEE.io.out.valid
      val rfWaddr = Mux(fpu.pipeWrite.rfWen, fpu.pipeWrite.rfWaddr, fpu.loadOrVectorWrite.rfWaddr)
      val rfWdata = fpToIEEE.io.out.bits
      when(rfWen) {
        log.printf(
          cf"""{"event":"FregWrite","idx":$rfWaddr,"data":"$rfWdata%x","cycle":$simulationTime}\n"""
        )
      }

      when(fpuScoreboard.fpuSetScoreBoard && !rfWen) {
        log.printf(
          cf"""{"event":"FregWriteWait","idx":${fpuScoreboard.scoreBoardSetAddress},"cycle":${simulationTime}}\n"""
        )
      }
      when(fpuScoreboard.memSetScoreBoard && !rfWen) {
        log.printf(
          cf"""{"event":"FregWriteWait","idx":${fpuScoreboard.scoreBoardSetAddress},"cycle":${simulationTime}}\n"""
        )
      }
    }
  }

  // t1 vrf write
  laneVrfProbes.zipWithIndex.foreach { case (lane, i) =>
    val datapathWidth = parameter.t1Parameter.datapathWidth.U(32.W)

    val vrfOffsetInBytes  = parameter.vLen.U(32.W) / 8.U(32.W) * lane.requestVd
    val laneOffsetInBytes =
      parameter.dLen.U(32.W) / 8.U(32.W) * lane.requestOffset + datapathWidth / 8.U(32.W) * i.U(32.W)

    val vrfIdx = vrfOffsetInBytes + laneOffsetInBytes
    when(lane.valid)(
      log.printf(
        cf"""{"event":"VrfWrite","issue_idx":${lane.requestInstruction},"vrf_idx":${vrfIdx},"mask":"${lane.requestMask}%x","data":"${lane.requestData}%x","cycle":${simulationTime}}\n"""
      )
    )
  }

  // t1 memory write from store unit
  when(storeUnitProbe.valid)(
    log.printf(
      cf"""{"event":"MemoryWrite","lsu_idx":${storeUnitProbe.index},"mask":"${storeUnitProbe.mask}%x","data":"${storeUnitProbe.data}%x","address":"${storeUnitProbe.address}%x","cycle":${simulationTime}}\n"""
    )
  )

  // t1 memory write from other unit
  when(otherUnitProbe.valid)(
    log.printf(
      cf"""{"event":"MemoryWrite","lsu_idx":${otherUnitProbe.index},"mask":"${otherUnitProbe.mask}%x","data":"${otherUnitProbe.data}%x","address":"${otherUnitProbe.address}%x","cycle":${simulationTime}}\n"""
    )
  )

  // t1 issue
  when(t1Probe.issue.valid)(
    log.printf(cf"""{"event":"Issue","idx":${t1Probe.issue.bits},"cycle":${simulationTime}}\n""")
  )

  // t1 retire
  when(t1Probe.retire.valid)(
    log.printf(
      cf"""{"event":"CheckRd","data":"${t1Probe.retire.bits}%x","issue_idx":${t1Probe.responseCounter},"cycle":${simulationTime}}\n"""
    )
  )

  // t1 lsu enq
  when(t1Probe.lsuProbe.reqEnq.orR)(
    log.printf(cf"""{"event":"LsuEnq","enq":${t1Probe.lsuProbe.reqEnq},"cycle":${simulationTime}}\n""")
  )

  // t1 vrf scoreboard
  val vrfWriteScoreboard: Seq[Valid[UInt]] = Seq.tabulate(parameter.t1Parameter.chaining1HBits) { _ =>
    RegInit(0.U.asTypeOf(Valid(UInt(16.W))))
  }
  vrfWriteScoreboard.foreach(scoreboard => dontTouch(scoreboard))
  val instructionValid =
    (laneProbes.map(laneProbe => laneProbe.instructionValid) :+
      lsuProbe.lsuInstructionValid :+ t1Probe.instructionValid).reduce(_ | _)
  val scoreboardEnq    =
    Mux(t1Probe.instructionIssue, UIntToOH(t1Probe.issueTag), 0.U(parameter.t1Parameter.chaining1HBits.W))
  vrfWriteScoreboard.zipWithIndex.foreach { case (scoreboard, tag) =>
    val writeEnq: UInt = VecInit(
      // vrf write from lane
      laneProbes.flatMap(laneProbe =>
        laneProbe.slots.map(slot => slot.writeTag === tag.U && slot.writeQueueEnq && slot.writeMask.orR)
      ) ++
        // vrf write from lsu
        lsuProbe.slots.map(slot => slot.dataInstruction === tag.U && slot.writeValid && slot.dataMask.orR) ++
        // vrf write from Sequencer
        t1Probe.writeQueueEnqVec.map(maskWrite => maskWrite.valid && maskWrite.bits === tag.U)
    ).asUInt
    // always equal to array index
    scoreboard.bits := scoreboard.bits + PopCount(writeEnq)
    when(scoreboard.valid && !instructionValid(tag)) {
      log.printf(
        cf"""{"event":"VrfScoreboard","count":${scoreboard.bits},"issue_idx":${tag},"cycle":${simulationTime}}\n"""
      )
      scoreboard.valid := false.B
    }
    when(scoreboardEnq(tag)) {
      scoreboard.valid := true.B
      assert(!scoreboard.valid)
      scoreboard.bits  := 0.U
    }
  }

  // t1 quit
  verbatimModule.idle := t1Probe.idle && rocketProbe.idle

  // t1rocket ProfData
  layer.block(layers.Verification) {
    val profData = Module(new Module {
      override def desiredName: String = "ProfData"
      val probe = IO(Input(new T1RocketProbe(parameter)))

      val t1IssueEnqPc       = WireInit(probe.rocketProbe.wbRegPc)
      val t1IssueEnq         = WireInit(probe.rocketProbe.t1IssueEnq.get)
      val t1IssueDeq         = WireInit(probe.t1IssueDeq)
      val t1IssueRegDeq      = WireInit(probe.t1Probe.requestReg)
      val t1IssueRegDeqReady = WireInit(probe.t1Probe.requestRegReady)
      val t1Retire           = WireInit(probe.t1Retire)

      dontTouch(this.clock)
      dontTouch(this.reset)
      dontTouch(t1IssueEnq)
      dontTouch(t1IssueDeq)
      dontTouch(t1IssueRegDeq)
      dontTouch(t1IssueRegDeqReady)
      dontTouch(t1Retire)
    })
    profData.probe := t1RocketProbe
  }
}
