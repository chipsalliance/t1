// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, ClassType, Property}
import chisel3.ltl.{CoverProperty, Sequence}
import chisel3.util.experimental.BitSet
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.util.{
  isPow2,
  log2Ceil,
  scanLeftOr,
  scanRightOr,
  BitPat,
  Decoupled,
  DecoupledIO,
  Enum,
  Fill,
  FillInterleaved,
  Mux1H,
  OHToUInt,
  Pipe,
  PriorityEncoder,
  RegEnable,
  UIntToOH,
  Valid,
  ValidIO
}
import org.chipsalliance.amba.axi4.bundle.{AXI4BundleParameter, AXI4RWIrrevocable}
import org.chipsalliance.rvdecoderdb.Instruction
import org.chipsalliance.t1.rtl.decoder.{Decoder, DecoderParam, T1CustomInstruction}
import org.chipsalliance.t1.rtl.lsu.{LSU, LSUParameter, LSUProbe}
import org.chipsalliance.t1.rtl.vrf.{RamType, VRFParam}
import org.chipsalliance.stdlib.GeneralOM

import scala.collection.immutable.SeqMap

// TODO: this should be a object model. There should 3 object model here:
//       1. T1SubsystemOM(T1(OM), MemoryRegion, Cache configuration)
//       2. T1(Lane(OM), VLEN, DLEN, uarch parameters, customer IDs(for floorplan);)
//       3. Lane(Retime, VRF memory type, id, multiple instances(does it affect dedup? not for sure))
@instantiable
class T1OM(parameter: T1Parameter) extends GeneralOM[T1Parameter, T1](parameter) {
  val vlen = IO(Output(Property[Int]()))
  vlen := Property(parameter.vLen)

  val dlen = IO(Output(Property[Int]()))
  dlen := Property(parameter.dLen)

  val elen = IO(Output(Property[Int]()))
  elen := Property(parameter.eLen)

  val extensions = IO(Output(Property[Seq[String]]()))
  extensions := Property(parameter.extensions)

  val laneScale = IO(Output(Property[Int]()))
  laneScale := Property(parameter.laneScale)

  val chainingSize = IO(Output(Property[Int]()))
  chainingSize := Property(parameter.chainingSize)

  val march = IO(Output(Property[String]()))
  march := Property(parameter.spikeMarch)

  val lanes   = IO(Output(Property[Seq[AnyClassType]]()))
  @public
  val lanesIn = IO(Input(Property[Seq[AnyClassType]]()))
  lanes := lanesIn

  val decoder   = IO(Output(Property[AnyClassType]()))
  @public
  val decoderIn = IO(Input(Property[AnyClassType]()))
  decoder := decoderIn

  val permutation   = IO(Output(Property[AnyClassType]()))
  @public
  val permutationIn = IO(Input(Property[AnyClassType]()))
  permutation := permutationIn
}

object T1Parameter {
  implicit def bitSetP: upickle.default.ReadWriter[BitSet] = upickle.default
    .readwriter[String]
    .bimap[BitSet](
      bs => bs.terms.map("b" + _.rawString).mkString("\n"),
      str => BitSet.fromString(str)
    )

  implicit def rwP: upickle.default.ReadWriter[T1Parameter] = upickle.default.macroRW
}

/** @param xLen
  *   XLEN
  * @param vLen
  *   VLEN
  * @param dLen
  *   DLEN
  * @param eLen
  *   ELEN
  * @param extensions
  *   what extensions does T1 support. currently Zve32x or Zve32f, TODO: we may add
  *   - Zvfhmin, Zvfh for ML workloads
  *   - Zvbb, Zvbc, Zvkb for Crypto, and other Crypto accelerators in the future.
  * @param datapathWidth
  *   width of data path which decides the memory bandwidth.
  * @param laneNumber
  *   how many lanes in the vector processor
  * @param physicalAddressWidth
  *   width of memory bus address width
  * @param chainingSize
  *   how many instructions can be chained
  * @param laneScale
  *   factor of the data path width relative to ELEN.
  *
  * @note
  *   Chaining:
  *   - limited by VRF Memory Port.
  *   - the chaining size is decided by logic units. if the bandwidth is limited by the logic units, we should increase
  *     lane size. TODO: sort a machine-readable chaining matrix for test case generation.
  */
case class T1Parameter(
  dLen:                    Int,
  extensions:              Seq[String],
  // Lane
  laneScale:               Int,
  chainingSize:            Int,
  vrfBankSize:             Int,
  vrfRamType:              RamType,
  // TODO: simplify it. this is user-level API.
  vfuInstantiateParameter: VFUInstantiateParameter)
    extends SerializableModuleParameter {
  // TODO: expose it with the Property API
  override def toString: String =
    s"""T1-${extensions.mkString(",")}
       |${dLen / 32} Lanes
       |VRF Banks: ${vrfBankSize}
       |VRFRAMType: ${vrfRamType match {
        case RamType.p0rw     => "Single Port."
        case RamType.p0rp1w   => "First Port Read, Second Port Write."
        case RamType.p0rwp1rw => "Dual Ports Read Write."
      }}
       |""".stripMargin

  def t1customInstructions: Seq[T1CustomInstruction] = Nil

  def vLen: Int = extensions.collectFirst { case s"zvl${vlen}b" =>
    vlen.toInt
  }.get

  def eLen: Int = extensions.collectFirst {
    case pattern if pattern.matches("zve\\d+x") || pattern.matches("zve\\d+f") || pattern.matches("zve\\d+d") =>
      "\\d+".r.findFirstIn(pattern).get.toInt
  }.get

  def spikeMarch: String = s"rv32gc_${extensions.mkString("_").toLowerCase}"

  val allInstructions: Seq[Instruction] = {
    org.chipsalliance.rvdecoderdb
      .instructions(org.chipsalliance.rvdecoderdb.extractResource(getClass.getClassLoader))
      .filter { instruction =>
        instruction.instructionSet.name match {
          case "rv_v"    => true
          case "rv_zvbb" => if (zvbbEnable) true else false
          case _         => false
        }
      } ++
      t1customInstructions.map(_.instruction)
  }.toSeq.filter { insn =>
    insn.name match {
      case s if Seq("vsetivli", "vsetvli", "vsetvl").contains(s) => false
      case _                                                     => true
    }
  }.sortBy(_.instructionSet.name)

  require(
    extensions.forall(
      (Seq("zve32x", "zve32f", "zvbb") ++
        Seq(128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536).map(vlen => s"zvl${vlen}b")).contains
    ),
    "unsupported extension."
  )
  // TODO: require bank not overlap
  /** xLen of T1, we currently only support 32. */
  val xLen: Int = 32

  /** minimum of sew, defined in spec. */
  val sewMin: Int = 8

  /** TODO: configure it. */
  val instructionQueueSize: Int = 4

  /** crosslane write token size */
  val vrfWriteQueueSize: Int = 4

  /** does t1 has floating datapath? */
  val fpuEnable: Boolean = extensions.contains("zve32f")

  /** support of zvbb */
  lazy val zvbbEnable: Boolean = extensions.contains("zvbb")

  /** datapath width of each lane should be aligned to xLen T1 only support 32 for now.
    */
  val datapathWidth: Int = laneScale * eLen

  /** How many lanes does T1 have. */
  val laneNumber: Int = dLen / datapathWidth

  /** MMU is living in the subsystem, T1 only fires physical address. */
  val physicalAddressWidth: Int = datapathWidth

  /** TODO: uarch docs for mask(v0) group and normal vrf groups.
    *
    * The state machine in LSU.mshr will handle `dataPathWidth` bits data for each group. For each lane, it will cache
    * corresponding data in `dataPathWidth` bits.
    *
    * The reason of this design, we cannot fanout all wires in mask to LSU. So we group them into `maskGroupSize`
    * groups, and LSU will handle them one by one in cycle.
    */
  val maskGroupWidth: Int = datapathWidth * laneNumber / sewMin

  /** how many groups will be divided into for mask(v0).
    *
    * The VRF(0) is duplicated from each lanes, this is used for mask broadcasting to each lanes.
    */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** vLen in Byte. */
  val vlenb: Int = vLen / 8

  /** The hardware width of [[datapathWidth]]. */
  val dataPathWidthBits: Int = log2Ceil(datapathWidth)

  /** 1 in MSB for instruction order. */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** maximum of lmul, defined in spec. */
  val lmulMax = 8

  /** data group in lane: for each instruction, it will operate on `vLen * lmulMax` databits, we split them to different
    * lanes, and partitioned into groups.
    */
  val groupNumberMax: Int = vLen * lmulMax / datapathWidth / laneNumber

  /** the hardware width of [[groupNumberMax]]. */
  val groupNumberMaxBits: Int = log2Ceil(groupNumberMax)

  /** LSU MSHR Size, Contains a load unit, a store unit and an other unit. */
  val lsuMSHRSize: Int = 3

  /** 2 for 3 MSHR(read + write + otherUnit) */
  val sourceWidth: Int = log2Ceil(lsuMSHRSize)

  // Read all lanes at once and send the data obtained at once.
  val lsuTransposeSize = datapathWidth * laneNumber / 8

  /** for TileLink `size` element. for most of the time, size is 2'b10, which means 4 bytes. EEW = 8bit, indexed LSU
    * will access 1 byte.(bandwidth is 1/4). TODO: perf it.
    */
  val sizeWidth: Int = log2Ceil(log2Ceil(lsuTransposeSize))

  val vrfReadLatency = 2

  val maskUnitVefWriteQueueSize: Int = 8

  // each element: Each lane will be connected to the other two lanes,
  // and the values are their respective delays.
  val crossLaneConnectCycles: Seq[Seq[Int]] = Seq.tabulate(laneNumber)(_ => Seq(1, 1))

  val laneRequestTokenSize:   Int      = 4
  val laneRequestShifterSize: Seq[Int] = Seq.tabulate(laneNumber)(_ => 1)

  val maskUnitReadTokenSize:   Seq[Int] = Seq.tabulate(laneNumber)(_ => 4)
  val maskUnitReadShifterSize: Seq[Int] = Seq.tabulate(laneNumber)(_ => 1)

  val lsuReadTokenSize:   Seq[Int] = Seq.tabulate(laneNumber)(_ => 4)
  val lsuReadShifterSize: Seq[Int] = Seq.tabulate(laneNumber)(_ => 1)

  val maskRequestLatency = 2

  val releaseShifterSize: Seq[Int] = Seq.tabulate(laneNumber)(_ => 1)

  val decoderParam: DecoderParam = DecoderParam(fpuEnable, zvbbEnable, allInstructions)

  val chaining1HBits: Int = 2 << log2Ceil(chainingSize)

  /** paraemter for AXI4. */
  val axi4BundleParameter: AXI4BundleParameter = AXI4BundleParameter(
    idWidth = sourceWidth,
    dataWidth = dLen,
    addrWidth = physicalAddressWidth,
    userReqWidth = 0,
    userDataWidth = 0,
    userRespWidth = 0,
    hasAW = true,
    hasW = true,
    hasB = true,
    hasAR = true,
    hasR = true,
    supportId = true,
    supportRegion = false,
    supportLen = true,
    supportSize = true,
    supportBurst = true,
    supportLock = false,
    supportCache = false,
    supportQos = false,
    supportStrb = true,
    supportResp = true,
    supportProt = false
  )

  /** Parameter for [[Lane]] */
  def laneParam: LaneParameter =
    LaneParameter(
      vLen = vLen,
      eLen = eLen,
      datapathWidth = datapathWidth,
      laneNumber = laneNumber,
      chainingSize = chainingSize,
      crossLaneVRFWriteEscapeQueueSize = vrfWriteQueueSize,
      fpuEnable = fpuEnable,
      portFactor = vrfBankSize,
      maskRequestLatency = 2 * maskRequestLatency,
      vrfRamType = vrfRamType,
      decoderParam = decoderParam,
      vfuInstantiateParameter = vfuInstantiateParameter
    )

  /** Parameter for each LSU. */
  def lsuParameters = LSUParameter(
    datapathWidth = datapathWidth,
    chainingSize = chainingSize,
    vLen = vLen,
    laneNumber = laneNumber,
    paWidth = xLen,
    // TODO: configurable for each LSU
    sourceWidth = sourceWidth,
    sizeWidth = sizeWidth,
    // TODO: configurable for each LSU [[p.supportMask]]
    maskWidth = dLen / 32,
    lsuMSHRSize = lsuMSHRSize,
    // TODO: make it configurable for each lane
    toVRFWriteQueueSize = 96,
    transferSize = lsuTransposeSize,
    vrfReadLatency = vrfReadLatency,
    axi4BundleParameter = axi4BundleParameter,
    lsuReadShifterSize = lsuReadShifterSize,
    name = "main"
  )
  def vrfParam:   VRFParam       = VRFParam(vLen, laneNumber, datapathWidth, chainingSize, vrfBankSize, vrfRamType)
  def adderParam: LaneAdderParam = LaneAdderParam(datapathWidth, 0)

  require(isPow2(laneNumber))
}

class T1Probe(parameter: T1Parameter) extends Bundle {
  val instructionCounter: UInt                           = UInt(parameter.instructionIndexBits.W)
  val instructionIssue:   Bool                           = Bool()
  val issueTag:           UInt                           = UInt(parameter.instructionIndexBits.W)
  val retireValid:        Bool                           = Bool()
  // for profiler
  val requestReg:         ValidIO[InstructionPipeBundle] = ValidIO(new InstructionPipeBundle(parameter))
  val requestRegReady:    Bool                           = Bool()
  // write queue enq for mask unit
  val writeQueueEnqVec:   Vec[ValidIO[UInt]]             = Vec(parameter.laneNumber, Valid(UInt(parameter.instructionIndexBits.W)))
  // mask unit instruction valid
  val instructionValid:   UInt                           = UInt((parameter.chainingSize * 2).W)
  // instruction index for check rd
  val responseCounter:    UInt                           = UInt(parameter.instructionIndexBits.W)
  // probes
  val lsuProbe:           LSUProbe                       = new LSUProbe(parameter.lsuParameters)
  val laneProbes:         Vec[LaneProbe]                 = Vec(parameter.laneNumber, new LaneProbe(parameter.laneParam))
  val issue:              ValidIO[UInt]                  = Valid(UInt(parameter.instructionIndexBits.W))
  val retire:             ValidIO[UInt]                  = Valid(UInt(parameter.xLen.W))
  val idle:               Bool                           = Bool()
}

class T1Interface(parameter: T1Parameter) extends Record {
  def clock  = elements("clock").asInstanceOf[Clock]
  def reset  = elements("reset").asInstanceOf[Bool]
  def issue  = elements("issue").asInstanceOf[DecoupledIO[T1Issue]]
  def retire = elements("retire").asInstanceOf[T1Retire]
  def highBandwidthLoadStorePort: AXI4RWIrrevocable    =
    elements("highBandwidthLoadStorePort").asInstanceOf[AXI4RWIrrevocable]
  def indexedLoadStorePort:       AXI4RWIrrevocable    = elements("indexedLoadStorePort").asInstanceOf[AXI4RWIrrevocable]
  def om:                         Property[ClassType]  = elements("om").asInstanceOf[Property[ClassType]]
  def t1Probe:                    T1Probe              = elements("t1Probe").asInstanceOf[T1Probe]
  val elements:                   SeqMap[String, Data] = SeqMap.from(
    Seq(
      "clock"                      -> Input(Clock()),
      "reset"                      -> Input(Bool()),
      "issue"                      -> Flipped(Decoupled(new T1Issue(parameter.xLen, parameter.vLen))),
      "retire"                     -> new T1Retire(parameter.xLen),
      "highBandwidthLoadStorePort" -> new AXI4RWIrrevocable(parameter.axi4BundleParameter),
      "indexedLoadStorePort"       -> new AXI4RWIrrevocable(parameter.axi4BundleParameter.copy(dataWidth = 32)),
      "om"                         -> Output(Property[AnyClassType]()),
      "t1Probe"                    -> Output(Probe(new T1Probe(parameter), layers.Verification))
    )
  )
}

/** Top of Vector processor: couple to Rocket Core; instantiate LSU, Decoder, Lane, CSR, Instruction Queue. The logic of
  * [[T1]] contains the Vector Sequencer and Mask Unit.
  */
@instantiable
class T1(val parameter: T1Parameter)
    extends FixedIORawModule(new T1Interface(parameter))
    with SerializableModule[T1Parameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  def implicitClock: Clock = io.clock
  def implicitReset: Reset = io.reset

  val omInstance: Instance[T1OM] = Instantiate(new T1OM(parameter))
  val omType:     ClassType      = omInstance.toDefinition.getClassType
  io.om := omInstance.getPropertyReference.asAnyClassType

  /** the LSU Module */

  val lsu:    Instance[LSU]           = Instantiate(new LSU(parameter.lsuParameters))
  val decode: Instance[VectorDecoder] = Instantiate(new VectorDecoder(parameter.decoderParam))
  omInstance.decoderIn := Property(decode.om.asAnyClassType)
  val maskUnit: Instance[MaskUnit] = Instantiate(new MaskUnit(parameter))
  maskUnit.io.clock        := implicitClock
  maskUnit.io.reset        := implicitReset
  omInstance.permutationIn := Property(maskUnit.io.om.asAnyClassType)

  val tokenManager: Instance[T1TokenManager] = Instantiate(new T1TokenManager(parameter))

  // TODO: cover overflow
  // TODO: uarch doc about the order of instructions
  val instructionCounter:     UInt = RegInit(0.U(parameter.instructionIndexBits.W))
  val nextInstructionCounter: UInt = instructionCounter + 1.U
  when(io.issue.fire) { instructionCounter := nextInstructionCounter }

  val retire = WireDefault(false.B)
  // todo: handle waw
  val responseCounter:     UInt = RegInit(0.U(parameter.instructionIndexBits.W))
  val nextResponseCounter: UInt = responseCounter + 1.U
  when(retire) { responseCounter := nextResponseCounter }

  // maintained a 1 depth queue for VRequest.
  // TODO: directly maintain a `ready` signal
  /** register to latch instruction. */
  val requestReg:    ValidIO[InstructionPipeBundle] = RegInit(0.U.asTypeOf(Valid(new InstructionPipeBundle(parameter))))
  val requestRegCSR: CSRInterface                   = WireDefault(0.U.asTypeOf(new CSRInterface(parameter.laneParam.vlMaxBits)))
  requestRegCSR.vlmul  := requestReg.bits.issue.vtype(2, 0)
  requestRegCSR.vSew   := requestReg.bits.issue.vtype(5, 3)
  requestRegCSR.vta    := requestReg.bits.issue.vtype(6)
  requestRegCSR.vma    := requestReg.bits.issue.vtype(7)
  requestRegCSR.vl     := requestReg.bits.issue.vl
  requestRegCSR.vStart := requestReg.bits.issue.vstart
  requestRegCSR.vxrm   := requestReg.bits.issue.vcsr(2, 1)

  /** maintain a [[DecoupleIO]] for [[requestReg]]. */
  val requestRegDequeue = Wire(Decoupled(new T1Issue(parameter.xLen, parameter.vLen)))
  // latch instruction, csr, decode result and instruction index to requestReg.
  when(io.issue.fire) {
    // The LSU only need to know the instruction, and don't need information from decoder.
    // Thus we latch the request here, and send it to LSU.
    requestReg.bits.issue            := io.issue.bits
    requestReg.bits.decodeResult     := decode.decodeResult
    requestReg.bits.instructionIndex := instructionCounter
    // vd === 0 && not store type
    requestReg.bits.vdIsV0           := (io.issue.bits.instruction(11, 7) === 0.U) &&
      (io.issue.bits.instruction(6) || !io.issue.bits.instruction(5))
    requestReg.bits.writeByte        := Mux(
      decode.decodeResult(Decoder.red),
      // Must be smaller than dataPath
      1.U,
      Mux(
        decode.decodeResult(Decoder.maskDestination),
        (io.issue.bits.vl >> 3).asUInt + io.issue.bits.vl(2, 0).orR,
        io.issue.bits.vl << (T1Issue.vsew(io.issue.bits) + decode.decodeResult(Decoder.crossWrite))
      )
    )
  }
  // 0 0 -> don't update
  // 0 1 -> update to false
  // 1 0 -> update to true
  // 1 1 -> don't update
  requestReg.valid := Mux(io.issue.fire ^ requestRegDequeue.fire, io.issue.fire, requestReg.valid)
  // ready when requestReg is free or it will be free in this cycle.
  io.issue.ready          := !requestReg.valid || requestRegDequeue.ready
  // manually maintain a queue for requestReg.
  requestRegDequeue.bits  := requestReg.bits.issue
  requestRegDequeue.valid := requestReg.valid
  decode.decodeInput      := io.issue.bits.instruction

  /** alias to [[requestReg.bits.decodeResult]], it is commonly used. */
  val decodeResult:      DecodeBundle = requestReg.bits.decodeResult
  val csrRegForMaskUnit: CSRInterface = RegInit(0.U.asTypeOf(new CSRInterface(parameter.laneParam.vlMaxBits)))
  val vSewOHForMask:     UInt         = UIntToOH(csrRegForMaskUnit.vSew)(2, 0)

  // TODO: no valid here
  // TODO: these should be decoding results
  val isLoadStoreType:     Bool = !requestRegDequeue.bits.instruction(6) && requestRegDequeue.valid
  val isStoreType:         Bool = !requestRegDequeue.bits.instruction(6) && requestRegDequeue.bits.instruction(5)
  val maskType:            Bool = !requestRegDequeue.bits.instruction(25)
  val lsWholeReg:          Bool = isLoadStoreType && requestRegDequeue.bits.instruction(27, 26) === 0.U &&
    requestRegDequeue.bits.instruction(24, 20) === 8.U
  // lane 只读不执行的指令
  val readOnlyInstruction: Bool = decodeResult(Decoder.readOnly)
  // 只进mask unit的指令
  val maskUnitInstruction: Bool = (decodeResult(Decoder.slid) || decodeResult(Decoder.mv))
  val skipLastFromLane:    Bool = isStoreType || maskUnitInstruction || readOnlyInstruction
  val instructionValid:    Bool = requestReg.bits.issue.vl > requestReg.bits.issue.vstart

  // TODO: these should be decoding results
  /** load store that don't read offset. */
  val noOffsetReadLoadStore: Bool = isLoadStoreType && (!requestRegDequeue.bits.instruction(26))

  val vSew1H:        UInt = UIntToOH(T1Issue.vsew(requestReg.bits.issue))
  val source1Extend: UInt = Mux1H(
    vSew1H(2, 0),
    Seq(
      Fill(parameter.datapathWidth - 8, requestRegDequeue.bits.rs1Data(7) && !decodeResult(Decoder.unsigned0))
        ## requestRegDequeue.bits.rs1Data(7, 0),
      Fill(parameter.datapathWidth - 16, requestRegDequeue.bits.rs1Data(15) && !decodeResult(Decoder.unsigned0))
        ## requestRegDequeue.bits.rs1Data(15, 0),
      requestRegDequeue.bits.rs1Data(31, 0)
    )
  )

  /** src1 from scalar core is a signed number. */
  val src1IsSInt:    Bool = !requestReg.bits.decodeResult(Decoder.unsigned0)
  val imm:           UInt = requestReg.bits.issue.instruction(19, 15)
  // todo: spec 10.1: imm 默认是 sign-extend,但是有特殊情况
  val immSignExtend: UInt = Fill(16, imm(4) && (vSew1H(2) || src1IsSInt)) ##
    Fill(8, imm(4) && (vSew1H(1) || vSew1H(2) || src1IsSInt)) ##
    Fill(3, imm(4)) ## imm

  /** which slot the instruction is entering */
  val instructionToSlotOH: UInt = Wire(UInt(parameter.chainingSize.W))

  /** last slot is committing. */
  val lastSlotCommit: Bool = Wire(Bool())

  /** for each lane, for instruction slot, when asserted, the corresponding instruction is finished.
    */
  val instructionFinished: Vec[Vec[Bool]] = Wire(Vec(parameter.laneNumber, Vec(parameter.chainingSize, Bool())))

  val vxsatReportVec: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.chainingSize.W)))
  val vxsatReport = vxsatReportVec.reduce(_ | _)

  /** Special instructions which will be allocate to the last slot.
    *   - mask unit
    *   - Lane <-> Top has data exchange(top might forward to LSU.) TODO: move to normal slots(add `offset` fields)
    *   - unordered instruction(slide)
    *   - vd is v0
    */
  val specialInstruction: Bool      = decodeResult(Decoder.special) || requestReg.bits.vdIsV0
  val dataInWritePipeVec: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.chaining1HBits.W)))
  val dataInWritePipe:    UInt      = dataInWritePipeVec.reduce(_ | _)

  // todo: instructionRAWReady -> v0 write token
  val allSlotFree:   Bool = Wire(Bool())
  val existMaskType: Bool = Wire(Bool())

  // read
  val readType: VRFReadRequest = new VRFReadRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfParam.vrfOffsetBits,
    parameter.instructionIndexBits
  )

  val gatherNeedRead: Bool = requestRegDequeue.valid && decodeResult(Decoder.gather) && !decodeResult(Decoder.vtype)

  /** state machine register for each instruction. */
  val slots: Seq[InstructionControl] = Seq.tabulate(parameter.chainingSize) { index =>
    /** the control register in the slot. */
    val control = RegInit(
      (-1)
        .S(new InstructionControl(parameter.instructionIndexBits, parameter.laneNumber).getWidth.W)
        .asTypeOf(new InstructionControl(parameter.instructionIndexBits, parameter.laneNumber))
    )

    /** the execution is finished. (but there might still exist some data in the ring.)
      */
    val laneAndLSUFinish: Bool = control.endTag.asUInt.andR

    val v0WriteFinish = !ohCheck(tokenManager.v0WriteValid, control.record.instructionIndex, parameter.chainingSize)

    /** lsu is finished when report bits matched corresponding slot lsu send `lastReport` to [[T1]], this check if the
      * report contains this slot. this signal is used to update the `control.endTag`.
      */
    val lsuFinished: Bool = ohCheck(lsu.lastReport, control.record.instructionIndex, parameter.chainingSize)
    val vxsatUpdate = ohCheck(vxsatReport, control.record.instructionIndex, parameter.chainingSize)

    val dataInWritePipeCheck = ohCheck(dataInWritePipe, control.record.instructionIndex, parameter.chainingSize)
    // instruction is allocated to this slot.
    when(instructionToSlotOH(index)) {
      // instruction metadata
      control.record.instructionIndex := requestReg.bits.instructionIndex
      // TODO: remove
      control.record.isLoadStore      := isLoadStoreType
      control.record.maskType         := maskType
      // control signals
      control.state.idle              := false.B
      control.state.wLast             := false.B
      control.state.sCommit           := false.B
      control.state.wVRFWrite         := !requestReg.bits.decodeResult(Decoder.maskUnit)
      control.state.wMaskUnitLast     := !requestReg.bits.decodeResult(Decoder.maskUnit)
      control.vxsat                   := false.B
      // two different initial states for endTag:
      // for load/store instruction, use the last bit to indicate whether it is the last instruction
      // for other instructions, use MSB to indicate whether it is the last instruction
      control.endTag                  := VecInit(Seq.fill(parameter.laneNumber)(skipLastFromLane) :+ !isLoadStoreType)
    }
      // state machine starts here
      .otherwise {
        when(maskUnit.io.lastReport.orR) {
          control.state.wMaskUnitLast := true.B
        }
        when(laneAndLSUFinish && v0WriteFinish) {
          control.state.wLast := true.B
        }

        when(control.state.wLast && control.state.wMaskUnitLast && !dataInWritePipeCheck) {
          control.state.wVRFWrite := true.B
        }

        when(responseCounter === control.record.instructionIndex && retire) {
          control.state.sCommit := true.B
        }

        when(control.state.sCommit && control.state.wVRFWrite && control.state.wMaskUnitLast) {
          control.state.idle := true.B
        }

        // endTag update logic from slot and lsu to instructionFinished.
        control.endTag.zip(instructionFinished.map(_(index)) :+ lsuFinished).foreach { case (d, c) =>
          d := d || c
        }
        when(vxsatUpdate) {
          control.vxsat := true.B
        }
      }
    if (index == (parameter.chainingSize - 1)) {
      val writeRD = RegInit(false.B)
      val float: Option[Bool] = Option.when(parameter.fpuEnable)(RegInit(false.B))
      val vd = RegInit(0.U(5.W))
      when(instructionToSlotOH(index)) {
        writeRD := decodeResult(Decoder.targetRd)
        float.foreach(_ := decodeResult(Decoder.float))
        vd      := requestRegDequeue.bits.instruction(11, 7)
      }
      io.retire.rd.valid := lastSlotCommit && writeRD
      io.retire.rd.bits.rdAddress := vd
      if (parameter.fpuEnable) {
        io.retire.rd.bits.isFp := float.getOrElse(false.B)
      } else {
        io.retire.rd.bits.isFp := false.B
      }
    }
    control
  }

  // Close to top
  val laneRequestSourceWire: Vec[DecoupledIO[LaneRequest]] = Wire(
    Vec(parameter.laneNumber, Decoupled(new LaneRequest(parameter.laneParam)))
  )
  // Close to lane
  val laneRequestSinkWire:   Vec[DecoupledIO[LaneRequest]] = Wire(
    Vec(parameter.laneNumber, Decoupled(new LaneRequest(parameter.laneParam)))
  )

  laneRequestSourceWire.zipWithIndex.foreach { case (source, index) =>
    val sink = laneRequestSinkWire(index)
    connectDecoupledWithShifter(parameter.laneRequestShifterSize(index), parameter.laneRequestTokenSize)(source, sink)
  }

  val allLaneReady: Bool = VecInit(laneRequestSourceWire.map(_.ready)).asUInt.andR
  // TODO: review later
  // todo: 把scheduler的反馈也加上,lsu有更高的优先级

  /** the index type of instruction is finished. let LSU to kill the lane slot. todo: delete?
    */
  val completeIndexInstruction: Bool =
    ohCheck(lsu.lastReport, slots.last.record.instructionIndex, parameter.chainingSize) && !slots.last.state.idle

  val freeOR: Bool = VecInit(slots.map(_.state.idle)).asUInt.orR

  /** slot is ready to accept new instructions. */
  val slotReady: Bool = Mux(specialInstruction, slots.map(_.state.idle).last, freeOR)

  val olderCheck: Bool = slots.map { re =>
    // The same lsb will make it difficult to distinguish between the new and the old
    val notSameLSB: Bool = re.record.instructionIndex(parameter.instructionIndexBits - 2, 0) =/=
      requestReg.bits.instructionIndex(parameter.instructionIndexBits - 2, 0)
    re.state.idle || (instIndexL(re.record.instructionIndex, requestReg.bits.instructionIndex) && notSameLSB)
  }.reduce(_ && _)

  val source1Select: UInt =
    Mux(
      decodeResult(Decoder.gather),
      maskUnit.io.gatherData.bits,
      Mux(decodeResult(Decoder.itype), immSignExtend, source1Extend)
    )

  // data eew for extend type
  val extendDataEEW: Bool = (T1Issue.vsew(requestReg.bits.issue) - decodeResult(Decoder.topUop)(2, 1))(0)
  val gather16:      Bool = decodeResult(Decoder.gather16)
  val vSewSelect:    UInt = Mux(
    isLoadStoreType,
    requestRegDequeue.bits.instruction(13, 12),
    Mux(
      decodeResult(Decoder.nr) || decodeResult(Decoder.maskLogic),
      2.U,
      Mux(gather16, 1.U, Mux(decodeResult(Decoder.extend), extendDataEEW, T1Issue.vsew(requestReg.bits.issue)))
    )
  )

  val evlForLane: UInt = Mux(
    decodeResult(Decoder.nr),
    // evl for Whole Vector Register Move ->  vs1 * (vlen / datapathWidth)
    (requestRegDequeue.bits.instruction(17, 15) +& 1.U) ## 0.U(log2Ceil(parameter.vLen / parameter.datapathWidth).W),
    requestReg.bits.issue.vl
  )

  val vSewForLsu: UInt = Mux(lsWholeReg, 2.U, requestRegDequeue.bits.instruction(13, 12))
  val evlForLsu:  UInt = Mux(
    lsWholeReg,
    (requestRegDequeue.bits.instruction(31, 29) +& 1.U) ## 0.U(log2Ceil(parameter.vLen / parameter.datapathWidth).W),
    requestReg.bits.issue.vl
  )

  laneRequestSourceWire.foreach { request =>
    request.valid                 := requestRegDequeue.fire
    // hard wire
    request.bits.instructionIndex := requestReg.bits.instructionIndex
    request.bits.decodeResult     := decodeResult
    request.bits.vs1              := requestRegDequeue.bits.instruction(19, 15)
    request.bits.vs2              := requestRegDequeue.bits.instruction(24, 20)
    request.bits.vd               := requestRegDequeue.bits.instruction(11, 7)
    request.bits.segment          := Mux(
      decodeResult(Decoder.nr),
      requestRegDequeue.bits.instruction(17, 15),
      requestRegDequeue.bits.instruction(31, 29)
    )

    request.bits.loadStoreEEW   := requestRegDequeue.bits.instruction(13, 12)
    // if the instruction is vi and vx type of gather, gather from rs2 with mask VRF read channel from one lane,
    // and broadcast to all lanes.
    request.bits.readFromScalar := source1Select

    request.bits.issueInst  := !noOffsetReadLoadStore && !maskUnitInstruction
    request.bits.loadStore  := isLoadStoreType
    // let record in VRF to know there is a store instruction.
    request.bits.store      := isStoreType
    // let lane know if this is a special instruction, which need group-level synchronization between lane and [[V]]
    request.bits.special    := specialInstruction
    request.bits.lsWholeReg := lsWholeReg
    // mask type instruction.
    request.bits.mask       := maskType

    // connect csrInterface
    request.bits.csrInterface      := requestRegCSR
    // index type EEW Decoded in the instruction
    request.bits.csrInterface.vSew := vSewSelect
    request.bits.csrInterface.vl   := evlForLane
  }

  /** instantiate lanes. TODO: move instantiate to top of class.
    */
  val laneVec: Seq[Instance[Lane]] = Seq.tabulate(parameter.laneNumber) { index =>
    val lane: Instance[Lane] = Instantiate(new Lane(parameter.laneParam))
    lane.laneRequest.valid           := laneRequestSinkWire(index).valid && laneRequestSinkWire(index).bits.issueInst
    lane.laneRequest.bits            := laneRequestSinkWire(index).bits
    lane.laneRequest.bits.issueInst  := laneRequestSinkWire(index).fire
    laneRequestSinkWire(index).ready := !laneRequestSinkWire(index).bits.issueInst || lane.laneRequest.ready

    lane.laneIndex := index.U

    connectVrfAccess(
      Seq(parameter.maskUnitReadShifterSize(index), parameter.lsuReadShifterSize(index)),
      Seq(parameter.maskUnitReadTokenSize(index), parameter.lsuReadTokenSize(index)),
      Some(parameter.vrfReadLatency)
    )(
      VecInit(Seq(maskUnit.io.readChannel(index), lsu.vrfReadDataPorts(index))),
      lane.vrfReadAddressChannel,
      0,
      Some(lane.vrfReadDataChannel),
      Some(Seq(maskUnit.io.readResult(index), lsu.vrfReadResults(index)))
    )

    connectVrfAccess(
      Seq(parameter.maskUnitReadShifterSize(index), parameter.lsuReadShifterSize(index)),
      Seq(parameter.maskUnitReadTokenSize(index), parameter.lsuReadTokenSize(index))
    )(
      VecInit(Seq(maskUnit.io.exeResp(index), lsu.vrfWritePort(index))),
      lane.vrfWriteChannel,
      0,
      releaseSource = Some(Seq(maskUnit.io.writeRelease(index), lsu.writeRelease(index)))
    )
    lane.writeFromMask := maskUnit.io.exeResp(index).fire

    lsu.offsetReadResult(index).valid := lane.maskUnitRequest.valid && lane.maskRequestToLSU
    lsu.offsetReadResult(index).bits  := lane.maskUnitRequest.bits.source2
    lsu.offsetReadIndex(index)        := lane.maskUnitRequest.bits.index

    val instructionFinishedPipe = Pipe(true.B, lane.instructionFinished, parameter.releaseShifterSize(index)).bits
    instructionFinished(index).zip(slots.map(_.record.instructionIndex)).foreach { case (d, f) =>
      d := ohCheck(instructionFinishedPipe, f, parameter.chainingSize)
    }
    vxsatReportVec(index) := lane.vxsatReport
    lane.maskInput                       := Pipe(true.B, maskUnit.io.laneMaskInput(index), parameter.maskRequestLatency).bits
    maskUnit.io.laneMaskSelect(index)    := Pipe(true.B, lane.maskSelect, parameter.maskRequestLatency).bits
    maskUnit.io.laneMaskSewSelect(index) := Pipe(true.B, lane.maskSelectSew, parameter.maskRequestLatency).bits
    maskUnit.io.v0UpdateVec(index) <> lane.v0Update
    lsu.v0UpdateVec(index) <> lane.v0Update

    // Must arrive after the instruction request
    val lsuLastPipe:  UInt = Pipe(true.B, lsu.lastReport, parameter.laneRequestShifterSize(index)).bits
    val maskLastPipe: UInt = Pipe(true.B, maskUnit.io.lastReport, parameter.laneRequestShifterSize(index)).bits
    lane.lsuLastReport := lsuLastPipe | maskLastPipe

    lane.loadDataInLSUWriteQueue := lsu.dataInWriteQueue(index)
    // 2 + 3 = 5
    val rowWith:      Int  = log2Ceil(parameter.datapathWidth / 8) + log2Ceil(parameter.laneNumber)
    val writeCounter: UInt = (requestReg.bits.writeByte >> rowWith).asUInt +
      (requestReg.bits.writeByte(rowWith - 1, 0) > ((parameter.datapathWidth / 8) * index).U)
    lane.writeCount := Pipe(true.B, writeCounter, parameter.laneRequestShifterSize(index)).bits

    // token manager
    tokenManager.instructionFinish(index) := instructionFinishedPipe

    lane
  }

  omInstance.lanesIn := Property(laneVec.map(_.om.asAnyClassType))
  dataInWritePipeVec := VecInit(laneVec.map(_.writeQueueValid))

  // 连lsu
  lsu.request.valid                                       := requestRegDequeue.fire && isLoadStoreType
  lsu.request.bits.instructionIndex                       := requestReg.bits.instructionIndex
  lsu.request.bits.rs1Data                                := requestRegDequeue.bits.rs1Data
  lsu.request.bits.rs2Data                                := requestRegDequeue.bits.rs2Data
  lsu.request.bits.instructionInformation.nf              := requestRegDequeue.bits.instruction(31, 29)
  lsu.request.bits.instructionInformation.mew             := requestRegDequeue.bits.instruction(28)
  lsu.request.bits.instructionInformation.mop             := requestRegDequeue.bits.instruction(27, 26)
  lsu.request.bits.instructionInformation.lumop           := requestRegDequeue.bits.instruction(24, 20)
  lsu.request.bits.instructionInformation.vs3             := requestRegDequeue.bits.instruction(11, 7)
  // (0b000 0b101 0b110 0b111) -> (8, 16, 32, 64)忽略最高位
  lsu.request.bits.instructionInformation.eew             := vSewForLsu
  lsu.request.bits.instructionInformation.isStore         := isStoreType
  lsu.request.bits.instructionInformation.maskedLoadStore := maskType

  lsu.csrInterface     := requestRegCSR
  lsu.csrInterface.vl  := evlForLsu
  lsu.writeReadyForLsu := VecInit(laneVec.map(_.writeReadyForLsu)).asUInt.andR
  lsu.vrfReadyToStore  := VecInit(laneVec.map(_.vrfReadyToStore)).asUInt.andR

  // connect mask unit
  maskUnit.io.instReq.valid                 := requestRegDequeue.fire && requestReg.bits.decodeResult(Decoder.maskUnit)
  maskUnit.io.instReq.bits.instructionIndex := requestReg.bits.instructionIndex
  maskUnit.io.instReq.bits.decodeResult     := decodeResult
  maskUnit.io.instReq.bits.readFromScala    := Mux(decodeResult(Decoder.itype), imm, requestRegDequeue.bits.rs1Data)
  maskUnit.io.instReq.bits.sew              := T1Issue.vsew(requestReg.bits.issue)
  maskUnit.io.instReq.bits.maskType         := maskType
  maskUnit.io.instReq.bits.vxrm             := requestReg.bits.issue.vcsr(2, 1)
  maskUnit.io.instReq.bits.vlmul            := requestReg.bits.issue.vtype(2, 0)
  maskUnit.io.instReq.bits.vs1              := requestRegDequeue.bits.instruction(19, 15)
  maskUnit.io.instReq.bits.vs2              := requestRegDequeue.bits.instruction(24, 20)
  maskUnit.io.instReq.bits.vd               := requestRegDequeue.bits.instruction(11, 7)
  maskUnit.io.instReq.bits.vl               := requestReg.bits.issue.vl
  // gather read
  maskUnit.io.gatherRead                    := gatherNeedRead
  maskUnit.io.gatherData.ready              := requestRegDequeue.fire

  maskUnit.io.exeReq.zip(laneVec).foreach { case (maskInput, lane) =>
    maskInput.valid := lane.maskUnitRequest.valid && !lane.maskRequestToLSU
    maskInput.bits  := lane.maskUnitRequest.bits
  }

  maskUnit.io.tokenIO.zip(laneVec).zipWithIndex.foreach { case ((token, lane), index) =>
    lane.tokenIO.maskRequestRelease := token.maskRequestRelease || lsu.tokenIO.offsetGroupRelease(index)
  }

  // 连lane的环
  parameter.crossLaneConnectCycles.zipWithIndex.foreach { case (cycles, index) =>
    cycles.zipWithIndex.foreach { case (cycle, portIndex) =>
      // read source <=> write sink
      val readSourceIndex = (2 * index + portIndex) % parameter.laneNumber
      val readSourcePort  = (2 * index + portIndex) / parameter.laneNumber

      // read connect
      laneVec(readSourceIndex).readBusPort(readSourcePort).deqRelease := Pipe(
        laneVec(index).readBusPort(portIndex).enqRelease,
        0.U.asTypeOf(new EmptyBundle),
        cycle
      ).valid
      connectWithShifter(cycle)(
        laneVec(readSourceIndex).readBusPort(readSourcePort).deq,
        laneVec(index).readBusPort(portIndex).enq
      )

      // write connect
      laneVec(index).writeBusPort(portIndex).deqRelease := Pipe(
        laneVec(readSourceIndex).writeBusPort(readSourcePort).enqRelease,
        0.U.asTypeOf(new EmptyBundle),
        cycle
      ).valid
      connectWithShifter(cycle)(
        laneVec(index).writeBusPort(portIndex).deq,
        laneVec(readSourceIndex).writeBusPort(readSourcePort).enq
      )
    }
  }

  io.highBandwidthLoadStorePort <> lsu.axi4Port
  io.indexedLoadStorePort <> lsu.simpleAccessPorts

  /** Slot has free entries. */
  val free = VecInit(slots.map(_.state.idle)).asUInt
  allSlotFree := free.andR

  existMaskType := VecInit(slots.map(slot => !slot.state.idle && slot.record.maskType)).asUInt.orR

  // instruction issue
  val free1H = ffo(free)

  /** try to issue instruction to which slot. */
  val slotToEnqueue: UInt = Mux(specialInstruction, true.B ## 0.U((parameter.chainingSize - 1).W), free1H)

  // Identical subscripts lead to incorrect early release of endtag\
  // If the performance impact is too great, you can lengthen the tag.
  val instructionIndexFree: Bool = slots
    .map(s => s.state.idle || s.record.instructionIndex(1, 0) =/= requestReg.bits.instructionIndex(1, 0))
    .reduce(_ && _)

  /** for lsu instruction lsu is ready, for normal instructions, lanes are ready. */
  val executionReady: Bool = (!isLoadStoreType || lsu.request.ready) && (noOffsetReadLoadStore || allLaneReady)
  // - ready to issue instruction
  // - for vi and vx type of gather, it need to access vs2 for one time, we read vs2 firstly in `gatherReadFinish`
  //   and convert it to mv instruction.
  //   TODO: decode it directly
  // - for slide instruction, it is unordered, and may have RAW hazard,
  //   we detect the hazard and decide should we issue this slide or
  //   issue the instruction after the slide which already in the slot.
  requestRegDequeue.ready := executionReady && slotReady && (!gatherNeedRead || maskUnit.io.gatherData.valid) &&
    tokenManager.issueAllow && instructionIndexFree && olderCheck

  instructionToSlotOH := Mux(requestRegDequeue.fire, slotToEnqueue, 0.U)

  tokenManager.instructionIssue.valid                 := requestRegDequeue.fire
  tokenManager.instructionIssue.bits.instructionIndex := requestReg.bits.instructionIndex
  tokenManager.instructionIssue.bits.writeV0          :=
    (!requestReg.bits.decodeResult(Decoder.targetRd) && !isStoreType) && requestReg.bits.vdIsV0
  tokenManager.instructionIssue.bits.useV0AsMask      := maskType
  tokenManager.instructionIssue.bits.isLoadStore      := !requestRegDequeue.bits.instruction(6)
  tokenManager.instructionIssue.bits.toLane           := !noOffsetReadLoadStore && !maskUnitInstruction
  tokenManager.instructionIssue.bits.toMask           := requestReg.bits.decodeResult(Decoder.maskUnit)
  tokenManager.lsuWriteV0.zip(lsu.vrfWritePort).foreach { case (token, write) =>
    token.valid := write.fire && write.bits.vd === 0.U && write.bits.mask.orR
    token.bits  := write.bits.instructionIndex
  }
  tokenManager.maskUnitFree                           := slots.last.state.idle

  // instruction commit
  {
    val slotCommit: Vec[Bool] = VecInit(slots.map { inst =>
      // mask unit finish
      inst.state.wMaskUnitLast &&
      // lane|lsu finish
      inst.state.wLast &&
      // mask unit write finish
      inst.state.wVRFWrite &&
      // Ensure that only one cycle is committed
      !inst.state.sCommit &&
      // Ensuring commit order
      inst.record.instructionIndex === responseCounter
    })
    retire                   := slotCommit.asUInt.orR
    io.retire.rd.bits.rdData := maskUnit.io.writeRDData
    // TODO: csr retire.
    io.retire.csr.bits.vxsat := (slotCommit.asUInt & VecInit(slots.map(_.vxsat)).asUInt).orR
    io.retire.csr.bits.fflag := DontCare
    io.retire.csr.valid      := false.B
    io.retire.mem.valid      := (slotCommit.asUInt & VecInit(slots.map(_.record.isLoadStore)).asUInt).orR
    lastSlotCommit           := slotCommit.last
  }

  layer.block(layers.Verification) {

    /** Probes
      */
    val probeWire = Wire(new T1Probe(parameter))
    define(io.t1Probe, ProbeValue(probeWire))
    probeWire.instructionCounter := instructionCounter
    probeWire.instructionIssue   := requestRegDequeue.fire
    probeWire.issueTag           := requestReg.bits.instructionIndex
    probeWire.retireValid        := retire
    probeWire.requestReg         := requestReg
    probeWire.requestRegReady    := requestRegDequeue.ready
    // maskUnitWrite maskUnitWriteReady
    probeWire.writeQueueEnqVec.zip(maskUnit.io.exeResp).foreach { case (probe, write) =>
      probe.valid := write.fire && write.bits.mask.orR
      probe.bits  := write.bits.instructionIndex
    }
    probeWire.instructionValid   := slots
      .map(s => maskAnd(!s.state.idle, indexToOH(s.record.instructionIndex, parameter.chainingSize)).asUInt)
      .reduce(_ | _)
    probeWire.responseCounter    := responseCounter
    probeWire.laneProbes.zip(laneVec).foreach { case (p, l) => p := probe.read(l.laneProbe) }
    probeWire.lsuProbe           := probe.read(lsu.lsuProbe)
    probeWire.issue.valid        := io.issue.fire
    probeWire.issue.bits         := instructionCounter
    probeWire.retire.valid       := io.retire.rd.valid
    probeWire.retire.bits        := io.retire.rd.bits.rdData
    probeWire.idle               := slots.map(_.state.idle).reduce(_ && _)

    // coverage
    import Sequence.BoolSequence
    // unsupported 64-bit instructions for 32-bit xlen
    val zve32f = Seq(
      // format: off
      "vfadd.vf", "vfadd.vv", "vfclass.v", "vfcvt.f.x.v", 
      "vfcvt.f.xu.v", "vfcvt.rtz.x.f.v", "vfcvt.rtz.xu.f.v", "vfcvt.x.f.v", 
      "vfcvt.xu.f.v", "vfdiv.vf", "vfdiv.vv", "vfmacc.vf",
      "vfmacc.vv", "vfmadd.vf", "vfmadd.vv", "vfmax.vf",
      "vfmax.vv", "vfmerge.vfm", "vfmin.vf", "vfmin.vv",
      "vfmsac.vf", "vfmsac.vv", "vfmsub.vf", "vfmsub.vv",
      "vfmul.vf", "vfmul.vv", "vfmv.f.s", "vfmv.s.f",
      "vfmv.v.f", "vfnmacc.vf", "vfnmacc.vv", "vfnmadd.vf",
      "vfnmadd.vv", "vfnmsac.vf", "vfnmsac.vv", "vfnmsub.vf",
      "vfnmsub.vv", "vfrdiv.vf", "vfrec7.v", "vfredmax.vs",
      "vfredmin.vs", "vfredosum.vs", "vfredusum.vs", "vfrsqrt7.v",
      "vfrsub.vf", "vfsgnj.vf", "vfsgnj.vv", "vfsgnjn.vf",
      "vfsgnjn.vv", "vfsgnjx.vf", "vfsgnjx.vv", "vfsqrt.v",
      "vfsub.vf", "vfsub.vv", "vmfeq.vf", "vmfeq.vv",
      "vmfge.vf", "vmfgt.vf", "vmfle.vf", "vmfle.vv", 
      "vmflt.vf", "vmflt.vv", "vmfne.vf", "vmfne.vv"
      // format: on
    )
    val zve64f = Seq(
      // format: off
      "vfncvt.f.f.w", "vfncvt.f.x.w", "vfncvt.f.xu.w", "vfncvt.rod.f.f.w", "vfncvt.rtz.x.f.w", "vfncvt.rtz.xu.f.w", "vfncvt.x.f.w", "vfncvt.xu.f.w",
      "vfslide1down.vf", "vfslide1up.vf",
      "vfwadd.vf", "vfwadd.vv", "vfwadd.wf", "vfwadd.wv",
      "vfwcvt.f.f.v", "vfwcvt.f.x.v", "vfwcvt.f.xu.v", "vfwcvt.rtz.x.f.v", "vfwcvt.rtz.xu.f.v", "vfwcvt.x.f.v", "vfwcvt.xu.f.v",
      "vfwmacc.vf", "vfwmacc.vv", "vfwmsac.vf", "vfwmsac.vv",
      "vfwmul.vf", "vfwmul.vv", "vfwnmacc.vf", "vfwnmacc.vv",
      "vfwnmsac.vf", "vfwnmsac.vv", "vfwredosum.vs", "vfwredusum.vs",
      "vfwsub.vf", "vfwsub.vv", "vfwsub.wf", "vfwsub.wv", 
      // format: on
    )
    val zve64x = Seq(
      // format: off
      "vl1re64.v", "vl2re64.v", "vl4re64.v", "vl8re64.v", 
      "vle64.v", "vle64ff.v", "vloxei64.v", "vlse64.v", "vluxei64.v",
      "vse64.v", "vsoxei64.v", "vsse64.v", "vsuxei64.v", 
      "vsext.vf8", "vzext.vf8"
      // format: on
    )
    val instructions: Seq[Instruction] = parameter.decoderParam.allInstructions.filter { instruction: Instruction =>
      // format: off
      !(zve64x.contains(instruction.name) && parameter.xLen == 32) &&
      !(zve64f.contains(instruction.name) && parameter.xLen == 32 && parameter.fpuEnable) &&
      !((zve32f ++ zve64f).contains(instruction.name) && !parameter.fpuEnable)
      // format: on
    }

    // coverage for one instruction
    instructions.map { instruction: Instruction =>
      val coverMatch = BoolSequence(
        requestReg.valid && requestReg.bits.issue.instruction === BitPat("b" + instruction.encoding.toString)
      )
      CoverProperty(coverMatch, label = Some(s"${instruction.name}"))
    }

    // // coverage for two instructions
    // instructions.map { case instructionNew: Instruction =>
    //   instructions.map { case instructionOld: Instruction =>
    //     val issueInstructionOld = RegEnable(requestReg.bits.issue.instruction, requestReg.valid)
    //     val coverMatchNew       = BoolSequence(
    //       requestReg.valid && requestReg.bits.issue.instruction === BitPat("b" + instructionNew.encoding.toString)
    //     )
    //     val coverMatchOld       = BoolSequence(issueInstructionOld === BitPat("b" + instructionOld.encoding.toString))
    //     CoverProperty(
    //       coverMatchNew.and(coverMatchOld),
    //       label = Some(s"2_${instructionOld.name}_and_${instructionNew.name}")
    //     )
    //   }
    // }

    // // coverage for different sew / vlmul / vl
    // val vsews:  Seq[Int]           = Seq(0, 1, 2)
    // val sews:   Seq[Int]           = Seq(8, 16, 32)
    // val vlmuls: Seq[Int]           = Seq(0, 1, 2, 3, 6, 7)
    // val lmuls:  Seq[Double]        = Seq(1, 2, 4, 8, -1, -1, 0.25, 0.5)
    // val vls:    Seq[(Double, Int)] =
    //   Seq((1.0, 0), (1.0, -1), (0.25, -1), (0.25, 0), (0.25, 1), (0.5, -1), (0.5, 0), (0.5, 1))
    // vsews.map { vsew =>
    //   vlmuls.map { vlmul =>
    //     vls.map { case (vla, vlb) =>
    //       val sew   = sews(vsew)
    //       val lmul  = lmuls(vlmul)
    //       val vlmax = parameter.vLen * lmul / sew
    //       val vl    = (vlmax * vla).toInt + vlb

    //       val coverMatch = BoolSequence(
    //         requestReg.valid && requestReg.bits.issue.vl === vl.U &&
    //           T1Issue.vlmul(requestReg.bits.issue) === vlmul.U &&
    //           T1Issue.vsew(requestReg.bits.issue) === vsew.U
    //       )

    //       CoverProperty(coverMatch, label = Some(s"3_sew_${sew}_lmul_${lmul}_vl_${vl}"))
    //     }
    //   }
    // }

    // // coverage for lsu (load / store / other) with slots (contain / intersection / disjoint)

    // // TODO:load unit probe

    // // store unit probe
    // val storeUnitProbe = probeWire.lsuProbe.storeUnitProbe

    // val storeRangeStartNew = storeUnitProbe.address
    // val storeRangeEndNew   = storeUnitProbe.address + PriorityEncoder(storeUnitProbe.mask)
    // val storeRangeStartOld = RegEnable(storeUnitProbe.address, storeUnitProbe.valid)
    // val storeRangeEndOld   =
    //   RegEnable(storeUnitProbe.address + PriorityEncoder(storeUnitProbe.mask), storeUnitProbe.valid)

    // val storeContain      = storeRangeStartOld <= storeRangeStartNew && storeRangeEndNew <= storeRangeEndOld ||
    //   storeRangeStartNew <= storeRangeStartOld && storeRangeEndOld <= storeRangeEndNew
    // val storeIntersection = storeRangeStartNew <= storeRangeStartOld && storeRangeEndNew <= storeRangeEndOld ||
    //   storeRangeStartOld <= storeRangeStartNew && storeRangeEndOld <= storeRangeEndNew
    // val storeDisjoint     = storeRangeEndNew <= storeRangeStartOld || storeRangeEndOld <= storeRangeEndNew

    // CoverProperty(BoolSequence(storeUnitProbe.valid && storeContain), label = Some("4_store_contain"))
    // CoverProperty(BoolSequence(storeUnitProbe.valid && storeIntersection), label = Some("4_store_intersection"))
    // CoverProperty(BoolSequence(storeUnitProbe.valid && storeDisjoint), label = Some("4_store_disjoint"))

    // // other unit probe
    // val otherUnitProbe = probeWire.lsuProbe.otherUnitProbe

    // val otherRangeStartNew = otherUnitProbe.address
    // val otherRangeEndNew   = otherUnitProbe.address + PriorityEncoder(otherUnitProbe.mask)
    // val otherRangeStartOld = RegEnable(otherUnitProbe.address, otherUnitProbe.valid)
    // val otherRangeEndOld   =
    //   RegEnable(otherUnitProbe.address + PriorityEncoder(otherUnitProbe.mask), otherUnitProbe.valid)

    // val otherContain      = otherRangeStartOld <= otherRangeStartNew && otherRangeEndNew <= otherRangeEndOld ||
    //   otherRangeStartNew <= otherRangeStartOld && otherRangeEndOld <= otherRangeEndNew
    // val otherIntersection = otherRangeStartNew <= otherRangeStartOld && otherRangeEndNew <= otherRangeEndOld ||
    //   otherRangeStartOld <= otherRangeStartNew && otherRangeEndOld <= otherRangeEndNew
    // val otherDisjoint     = otherRangeEndNew <= otherRangeStartOld || otherRangeEndOld <= otherRangeEndNew

    // CoverProperty(BoolSequence(otherUnitProbe.valid && otherContain), label = Some("4_other_contain"))
    // CoverProperty(BoolSequence(otherUnitProbe.valid && otherIntersection), label = Some("4_other_intersection"))
    // CoverProperty(BoolSequence(otherUnitProbe.valid && otherDisjoint), label = Some("4_other_disjoint"))
  } // end of verification layer
}
