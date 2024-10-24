// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.properties.{AnyClassType, Class, ClassType, Property}
import chisel3.ltl.{CoverProperty, Sequence}
import chisel3.util.experimental.BitSet
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.util.{
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

import scala.collection.immutable.SeqMap

// TODO: this should be a object model. There should 3 object model here:
//       1. T1SubsystemOM(T1(OM), MemoryRegion, Cache configuration)
//       2. T1(Lane(OM), VLEN, DLEN, uarch parameters, customer IDs(for floorplan);)
//       3. Lane(Retime, VRF memory type, id, multiple instances(does it affect dedup? not for sure))
@instantiable
class T1OM extends Class {
  @public
  val vlen   = IO(Output(Property[Int]()))
  @public
  val vlenIn = IO(Input(Property[Int]()))
  vlen := vlenIn

  @public
  val dlen   = IO(Output(Property[Int]()))
  @public
  val dlenIn = IO(Input(Property[Int]()))
  dlen := dlenIn

  @public
  val extensions   = IO(Output(Property[Seq[String]]()))
  @public
  val extensionsIn = IO(Input(Property[Seq[String]]()))
  extensions := extensionsIn

  @public
  val march   = IO(Output(Property[String]()))
  @public
  val marchIn = IO(Input(Property[String]()))
  march := marchIn

  @public
  val lanes   = IO(Output(Property[Seq[AnyClassType]]()))
  @public
  val lanesIn = IO(Input(Property[Seq[AnyClassType]]()))
  lanes := lanesIn

  @public
  val decoder   = IO(Output(Property[AnyClassType]()))
  @public
  val decoderIn = IO(Input(Property[AnyClassType]()))
  decoder := decoderIn
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
  * @param extensions
  *   what extensions does T1 support. currently Zve32x or Zve32f, TODO: we may add
  *   - Zvfhmin, Zvfh for ML workloads
  *   - Zvbb, Zvbc, Zvkb for Crypto, and other Crypto accelerators in the future.
  * @param datapathWidth
  *   width of data path, can be 32 or 64, decides the memory bandwidth.
  * @param laneNumber
  *   how many lanes in the vector processor
  * @param physicalAddressWidth
  *   width of memory bus address width
  * @param chainingSize
  *   how many instructions can be chained TODO: make it a val, not parameter.
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

  /** how many chaining does T1 support, this is not a parameter yet. */
  val chainingSize: Int = 4

  /** datapath width of each lane should be aligned to xLen T1 only support 32 for now.
    */
  val datapathWidth: Int = xLen

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

  // each element: Each lane will be connected to the other two lanes,
  // and the values are their respective delays.
  val crossLaneConnectCycles: Seq[Seq[Int]] = Seq.tabulate(laneNumber)(_ => Seq(1, 1))

  val decoderParam: DecoderParam = DecoderParam(fpuEnable, zvbbEnable, allInstructions)

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
      datapathWidth = datapathWidth,
      laneNumber = laneNumber,
      chainingSize = chainingSize,
      crossLaneVRFWriteEscapeQueueSize = vrfWriteQueueSize,
      fpuEnable = fpuEnable,
      portFactor = vrfBankSize,
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
    name = "main"
  )
  def vrfParam:   VRFParam       = VRFParam(vLen, laneNumber, datapathWidth, chainingSize, vrfBankSize, vrfRamType)
  require(xLen == datapathWidth)
  def adderParam: LaneAdderParam = LaneAdderParam(datapathWidth, 0)
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
  val writeQueueEnq:      ValidIO[UInt]                  = Valid(UInt(parameter.instructionIndexBits.W))
  val writeQueueEnqMask:  UInt                           = UInt((parameter.datapathWidth / 8).W)
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

  val omInstance: Instance[T1OM] = Instantiate(new T1OM)
  val omType:     ClassType      = omInstance.toDefinition.getClassType
  io.om := omInstance.getPropertyReference.asAnyClassType

  omInstance.vlenIn       := Property(parameter.vLen)
  omInstance.dlenIn       := Property(parameter.dLen)
  omInstance.extensionsIn := Property(parameter.extensions)
  omInstance.marchIn      := Property(s"rv32gc_${parameter.extensions.mkString("_").toLowerCase}")

  /** the LSU Module */

  val lsu:    Instance[LSU]           = Instantiate(new LSU(parameter.lsuParameters))
  val decode: Instance[VectorDecoder] = Instantiate(new VectorDecoder(parameter.decoderParam))
  omInstance.decoderIn := Property(decode.om.asAnyClassType)

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
  val skipLastFromLane:    Bool = isLoadStoreType || maskUnitInstruction || readOnlyInstruction
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

  /** duplicate v0 for mask */
  val v0:        Vec[UInt] = RegInit(
    VecInit(Seq.fill(parameter.vLen / parameter.datapathWidth)(0.U(parameter.datapathWidth.W)))
  )
  // TODO: uarch doc for the regroup
  val regroupV0: Seq[UInt] = Seq(4, 2, 1).map { groupSize =>
    VecInit(
      cutUInt(v0.asUInt, groupSize)
        .grouped(parameter.laneNumber)
        .toSeq
        .transpose
        .map(seq => VecInit(seq).asUInt)
    ).asUInt
  }

  /** which slot the instruction is entering */
  val instructionToSlotOH: UInt = Wire(UInt(parameter.chainingSize.W))

  /** synchronize signal from each lane, for mask units.(ffo) */
  val laneSynchronize: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))

  /** all lanes are synchronized. */
  val synchronized: Bool = WireDefault(false.B)

  /** for mask unit that need to access VRF from lanes, use this signal to indicate it is finished access VRF(but
    * instruction might not finish).
    */
  val maskUnitReadOnlyFinish: Bool = WireDefault(false.B)

  /** last slot is committing. */
  val lastSlotCommit: Bool = Wire(Bool())

  /** for each lane, for instruction slot, when asserted, the corresponding instruction is finished.
    */
  val instructionFinished: Vec[Vec[Bool]] = Wire(Vec(parameter.laneNumber, Vec(parameter.chainingSize, Bool())))

  val vxsatReportVec: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.chainingSize.W)))
  val vxsatReport = vxsatReportVec.reduce(_ | _)

  // todo: 把lsu也放decode里去
  val maskUnitType: Bool = decodeResult(Decoder.maskUnit) && requestRegDequeue.bits.instruction(6)
  val maskDestination = decodeResult(Decoder.maskDestination)
  val unOrderType: Bool = decodeResult(Decoder.unOrderWrite)

  /** Special instructions which will be allocate to the last slot.
    *   - mask unit
    *   - Lane <-> Top has data exchange(top might forward to LSU.) TODO: move to normal slots(add `offset` fields)
    *   - unordered instruction(slide)
    *   - vd is v0
    */
  val specialInstruction: Bool      = decodeResult(Decoder.special) || requestReg.bits.vdIsV0
  val dataInWritePipeVec: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.chainingSize.W)))
  val dataInWritePipe:    UInt      = dataInWritePipeVec.reduce(_ | _)

  /** designed for unordered instruction(slide), it doesn't go to lane, it has RAW hazzard.
    */
  val instructionRAWReady: Bool = Wire(Bool())
  val allSlotFree:         Bool = Wire(Bool())
  val existMaskType:       Bool = Wire(Bool())

  // mask Unit 与lane交换数据
  val writeType:           VRFWriteRequest               = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfParam.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  val maskUnitWrite:       ValidIO[VRFWriteRequest]      = Wire(Valid(writeType))
  val maskUnitWriteVec:    Vec[ValidIO[VRFWriteRequest]] = Wire(Vec(3, Valid(writeType)))
  val maskWriteLaneSelect: Vec[UInt]                     = Wire(Vec(3, UInt(parameter.laneNumber.W)))
  // 默认是head
  val maskUnitWriteSelect: UInt                          = Mux1H(maskUnitWriteVec.map(_.valid), maskWriteLaneSelect)
  maskUnitWriteVec.foreach(_ := DontCare)
  maskUnitWrite := Mux1H(maskUnitWriteVec.map(_.valid), maskUnitWriteVec)
  val writeSelectMaskUnit: Vec[Bool]                     = Wire(Vec(parameter.laneNumber, Bool()))
  val maskUnitWriteReady:  Bool                          = writeSelectMaskUnit.asUInt.orR

  // read
  val readType:           VRFReadRequest               = new VRFReadRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfParam.vrfOffsetBits,
    parameter.instructionIndexBits
  )
  val maskUnitRead:       ValidIO[VRFReadRequest]      = Wire(Valid(readType))
  val maskUnitReadVec:    Vec[ValidIO[VRFReadRequest]] = Wire(Vec(3, Valid(readType)))
  val maskReadLaneSelect: Vec[UInt]                    = Wire(Vec(3, UInt(parameter.laneNumber.W)))
  val maskUnitReadSelect: UInt                         = Mux1H(maskUnitReadVec.map(_.valid), maskReadLaneSelect)
  maskUnitRead := Mux1H(maskUnitReadVec.map(_.valid), maskUnitReadVec)
  val readSelectMaskUnit: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))
  val maskUnitReadReady = readSelectMaskUnit.asUInt.orR
  val laneReadResult:   Vec[UInt]     = Wire(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
  val WARRedResult:     ValidIO[UInt] = RegInit(0.U.asTypeOf(Valid(UInt(parameter.datapathWidth.W))))
  // mask unit 最后的写
  val maskUnitFlushVrf: Bool          = WireDefault(false.B)

  // gather read state
  val gatherOverlap:        Bool = Wire(Bool())
  val gatherNeedRead:       Bool = requestRegDequeue.valid && decodeResult(Decoder.gather) &&
    !decodeResult(Decoder.vtype) && !gatherOverlap
  val gatherReadFinish:     Bool =
    RegEnable(
      !requestRegDequeue.fire,
      false.B,
      (RegNext(RegNext(maskUnitReadReady)) && gatherNeedRead) || requestRegDequeue.fire
    )
  val gatherReadDataOffset: UInt = Wire(UInt(5.W))
  val gatherData:           UInt = Mux(gatherOverlap, 0.U, (WARRedResult.bits >> gatherReadDataOffset).asUInt)

  /** data that need to be compute at top. */
  val data:                Vec[ValidIO[UInt]] = RegInit(
    VecInit(Seq.fill(parameter.laneNumber)(0.U.asTypeOf(Valid(UInt(parameter.datapathWidth.W)))))
  )
  val flotReduceValid:     Seq[Option[Bool]]  = Seq.tabulate(parameter.laneNumber) { _ =>
    Option.when(parameter.fpuEnable)(RegInit(false.B))
  }
  val maskDataForCompress: UInt               = RegInit(0.U(parameter.datapathWidth.W))
  // clear the previous set of data from lane
  val dataClear:           Bool               = WireDefault(false.B)
  val completedVec:        Vec[Bool]          = RegInit(VecInit(Seq.fill(parameter.laneNumber)(false.B)))
  // ffoIndexReg.valid: Already found the first one
  val ffoIndexReg:         ValidIO[UInt]      = RegInit(0.U.asTypeOf(Valid(UInt(parameter.xLen.W))))
  val ffoType:             Bool               = Wire(Bool())

  /** for find first one, need to tell the lane with higher index `1` . */
  val completedLeftOr: UInt          = (scanLeftOr(completedVec.asUInt) << 1).asUInt(parameter.laneNumber - 1, 0)
  // 按指定的sew拼成 {laneNumer * dataPathWidth} bit, 然后根据sew选择出来
  val sortedData:      UInt          = Mux1H(
    vSewOHForMask,
    Seq(4, 2, 1).map { groupSize =>
      VecInit(data.map { element =>
        element.bits.asBools  // [x] * 32 eg: sew = 1
          .grouped(groupSize) // [x, x] * 16
          .toSeq
          .map(VecInit(_).asUInt) // [xx] * 16
      }.transpose.map(VecInit(_).asUInt)).asUInt // [x*16] * 16 -> x * 256
    }
  )
  // 把已经排过序的数据重新分给各个lane
  val regroupData:     Vec[UInt]     = VecInit(Seq.tabulate(parameter.laneNumber) { laneIndex =>
    sortedData(
      laneIndex * parameter.datapathWidth + parameter.datapathWidth - 1,
      laneIndex * parameter.datapathWidth
    )
  })
  val dataResult:      ValidIO[UInt] = RegInit(0.U.asTypeOf(Valid(UInt(parameter.datapathWidth.W))))

  val executeForLastLaneFire: Bool = WireDefault(false.B)

  /** state machine register for each instruction. */
  val slots: Seq[InstructionControl] = Seq.tabulate(parameter.chainingSize) { index =>
    /** the control register in the slot. */
    val control = RegInit(
      (-1)
        .S(new InstructionControl(parameter.instructionIndexBits, parameter.laneNumber).getWidth.W)
        .asTypeOf(new InstructionControl(parameter.instructionIndexBits, parameter.laneNumber))
    )

    val mvToVRF: Option[Bool] = Option.when(index == parameter.chainingSize - 1)(RegInit(false.B))

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
      control.vxsat                   := false.B
      // two different initial states for endTag:
      // for load/store instruction, use the last bit to indicate whether it is the last instruction
      // for other instructions, use MSB to indicate whether it is the last instruction
      control.endTag                  := VecInit(Seq.fill(parameter.laneNumber)(skipLastFromLane) :+ !isLoadStoreType)
    }
      // state machine starts here
      .otherwise {
        when(laneAndLSUFinish && v0WriteFinish) {
          control.state.wLast := true.B
        }

        when(control.state.wLast && control.state.sMaskUnitExecution && !dataInWritePipeCheck) {
          control.state.wVRFWrite := true.B
        }

        when(responseCounter === control.record.instructionIndex && retire) {
          control.state.sCommit := true.B
        }

        when(control.state.sCommit && control.state.wVRFWrite && control.state.sMaskUnitExecution) {
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
    // logic like mask&reduce will be put to last slot
    // TODO: review later
    if (index == (parameter.chainingSize - 1)) {
      val feedBack:         UInt = RegInit(0.U(parameter.laneNumber.W))
      val executeCounter:   UInt = RegInit(0.U((log2Ceil(parameter.laneNumber) + 1).W))
      // mask destination时这两count都是以写vrf为视角
      val writeBackCounter: UInt = RegInit(0.U(log2Ceil(parameter.laneNumber).W))
      val groupCounter:     UInt = RegInit(0.U(parameter.groupNumberMaxBits.W))
      val iotaCount:        UInt = RegInit(0.U((parameter.laneParam.vlMaxBits - 1).W))
      val maskTypeInstruction    = RegInit(false.B)
      val vd                     = RegInit(0.U(5.W))
      val vs1                    = RegInit(0.U(5.W))
      val vs2                    = RegInit(0.U(5.W))
      val rs1                    = RegInit(0.U(parameter.xLen.W))
      val vm                     = RegInit(false.B)
      val executeFinishReg       = RegInit(true.B)
      val unOrderTypeInstruction = RegInit(false.B)
      val decodeResultReg        = RegInit(0.U.asTypeOf(decodeResult))
      val gather: Bool = decodeResultReg(Decoder.gather)
      // for slid
      val elementIndexCount  = RegInit(0.U(parameter.laneParam.vlMaxBits.W))
      val compressWriteCount = RegInit(0.U(parameter.laneParam.vlMaxBits.W))
      val nextElementIndex: UInt = elementIndexCount + 1.U
      val firstElement = elementIndexCount === 0.U
      val lastElement: Bool = nextElementIndex === csrRegForMaskUnit.vl
      val updateMaskIndex = WireDefault(false.B)
      when(updateMaskIndex) { elementIndexCount := nextElementIndex }
      // 特殊的指令,会阻止 wLast 后把 sExecute 拉回来, 因为需要等待读后才写
      val mixedUnit: Bool = Wire(Bool())
      // slid & gather & extend
      val slidUnitIdle:            Bool         = RegInit(true.B)
      // compress & iota
      val iotaUnitIdle:            Bool         = RegInit(true.B)
      val orderedReduceGroupCount: Option[UInt] = Option.when(parameter.fpuEnable)(
        RegInit(0.U(log2Ceil(parameter.vLen / parameter.laneNumber).W))
      )
      val orderedReduceIdle:       Option[Bool] = Option.when(parameter.fpuEnable)(RegInit(true.B))
      val maskUnitIdle = (Seq(slidUnitIdle, iotaUnitIdle) ++ orderedReduceIdle).reduce(_ && _)
      val reduce       = decodeResultReg(Decoder.red)
      val orderedReduce: Bool = if (parameter.fpuEnable) decodeResultReg(Decoder.orderReduce) else false.B
      val popCount  = decodeResultReg(Decoder.popCount)
      val extend    = decodeResultReg(Decoder.extend)
      // first type instruction
      val firstLane = ffo(completedVec.asUInt)
      val firstLaneIndex: UInt = OHToUInt(firstLane)(log2Ceil(parameter.laneNumber) - 1, 0)
      io.retire.rd.valid          := lastSlotCommit && decodeResultReg(Decoder.targetRd)
      io.retire.rd.bits.rdAddress := vd
      if (parameter.fpuEnable) {
        io.retire.rd.bits.isFp := decodeResultReg(Decoder.float)
      } else {
        io.retire.rd.bits.isFp := false.B
      }
      when(requestRegDequeue.fire) {
        ffoIndexReg.valid := false.B
        ffoIndexReg.bits  := -1.S(parameter.xLen.W).asUInt
      }.elsewhen(synchronized && completedVec.asUInt.orR && !ffoIndexReg.valid) {
        ffoIndexReg.valid := true.B
        ffoIndexReg.bits  := Mux1H(
          firstLane,
          // 3: firstLaneIndex.width
          data.map(i => i.bits(parameter.xLen - 1 - 3, 5) ## firstLaneIndex ## i.bits(4, 0))
        )
      }
      ffoType                     := decodeResultReg(Decoder.ffo)

      /** vlmax = vLen * (2**lmul) / (2 ** sew * 8) \= (vLen / 8) * 2 ** (lmul - sew) \= vlb * 2 ** (lmul - sew) lmul <-
        * (-3, -2, -1, 0 ,1, 2, 3) sew <- (0, 1, 2) lmul - sew <- [-5, 3] 选择信号 +5 -> lmul - sew + 5 <- [0, 8]
        */
      def largeThanVLMax(source: UInt, advance: Bool = false.B, lmul: UInt, sew: UInt): Bool = {
        val vlenLog2 = log2Ceil(parameter.vLen) // 10
        val cut      =
          if (source.getWidth >= vlenLog2) source(vlenLog2 - 1, vlenLog2 - 9)
          else (0.U(vlenLog2.W) ## source)(vlenLog2 - 1, vlenLog2 - 9)
        // 9: lmul - sew 的可能值的个数
        val largeList: Vec[Bool] = Wire(Vec(9, Bool()))
        cut.asBools.reverse.zipWithIndex.foldLeft(advance) { case (a, (b, i)) =>
          largeList(i) := a
          a || b
        }
        val extendVlmul = lmul(2) ## lmul
        val selectWire = UIntToOH(5.U(4.W) + extendVlmul - sew)(8, 0).asBools.reverse
        Mux1H(selectWire, largeList)
      }
      // 算req上面的分开吧
      val gatherWire =
        Mux(decodeResult(Decoder.itype), requestRegDequeue.bits.instruction(19, 15), requestRegDequeue.bits.rs1Data)
      val gatherAdvance = (gatherWire >> log2Ceil(parameter.vLen)).asUInt.orR
      gatherOverlap := largeThanVLMax(
        gatherWire,
        gatherAdvance,
        T1Issue.vlmul(requestReg.bits.issue),
        T1Issue.vsew(requestReg.bits.issue)
      )
      val slotValid       = !control.state.idle
      val storeAfterSlide = isStoreType && (requestRegDequeue.bits.instruction(11, 7) === vd)
      instructionRAWReady := !((unOrderTypeInstruction && slotValid &&
        // slid 类的会比执行得慢的指令快(div),会修改前面的指令的source
        ((vd === requestRegDequeue.bits.instruction(24, 20)) ||
          (vd === requestRegDequeue.bits.instruction(19, 15)) ||
          storeAfterSlide ||
          // slid 类的会比执行得快的指令慢(mv),会被后来的指令修改 source2
          (vs2 === requestRegDequeue.bits.instruction(11, 7))) ||
        (unOrderType && !allSlotFree) ||
        (requestReg.bits.vdIsV0 && existMaskType)) ||
        (vd === 0.U && maskType && slotValid))
      when(instructionToSlotOH(index)) {
        writeBackCounter                 := 0.U
        groupCounter                     := 0.U
        executeCounter                   := 0.U
        elementIndexCount                := 0.U
        compressWriteCount               := 0.U
        iotaCount                        := 0.U
        slidUnitIdle                     := !((decodeResult(Decoder.slid) || (decodeResult(Decoder.gather) && decodeResult(Decoder.vtype))
          || decodeResult(Decoder.extend)) && instructionValid)
        iotaUnitIdle                     := !((decodeResult(Decoder.compress) || decodeResult(Decoder.iota)) && instructionValid)
        orderedReduceIdle.foreach(_ := !(decodeResult(Decoder.orderReduce) && instructionValid))
        orderedReduceGroupCount.foreach(_ := 0.U)
        vd                               := requestRegDequeue.bits.instruction(11, 7)
        vs1                              := requestRegDequeue.bits.instruction(19, 15)
        vs2                              := requestRegDequeue.bits.instruction(24, 20)
        vm                               := requestRegDequeue.bits.instruction(25)
        executeFinishReg                 := false.B
        rs1                              := requestRegDequeue.bits.rs1Data
        decodeResultReg                  := decodeResult
        csrRegForMaskUnit                := requestRegCSR
        // todo: decode need execute
        control.state.sMaskUnitExecution := !maskUnitType
        maskTypeInstruction              := maskType && !decodeResult(Decoder.maskSource)
        completedVec.foreach(_ := false.B)
        WARRedResult.valid               := false.B
        unOrderTypeInstruction           := unOrderType
        dataResult                       := 0.U.asTypeOf(dataResult)
      }.elsewhen(control.state.wLast && maskUnitIdle) {
        // 如果真需要执行的lane会wScheduler,不会提前发出last确认
        when(!mixedUnit) {
          control.state.sMaskUnitExecution := true.B
        }
        maskUnitFlushVrf := !control.state.idle
      }
      when(laneSynchronize.asUInt.orR) {
        feedBack := feedBack | laneSynchronize.asUInt
      }.elsewhen(lastSlotCommit) {
        feedBack := 0.U
      }
      // 执行
      // mask destination write
      /** 对于mask destination 类型的指令需要特别注意两种不对齐 第一种是我们以 32(dataPatWidth) * 8(laneNumber) 为一个组, 但是我们vl可能不对齐一整个组 第二种是
        * 32(dataPatWidth) 的时候对不齐 vl假设最大1024,相应的会有11位的vl xxx xxx xxxxx
        */
      val dataPathMisaligned = csrRegForMaskUnit.vl(parameter.dataPathWidthBits - 1, 0).orR
      val groupMisaligned =
        if (parameter.laneNumber > 1)
          csrRegForMaskUnit
            .vl(parameter.dataPathWidthBits + log2Ceil(parameter.laneNumber) - 1, parameter.dataPathWidthBits)
            .orR
        else false.B

      /** 我们需要计算最后一次写的 [[writeBackCounter]] & [[groupCounter]] lastGroupCounter = vl(10, 8) - !([[dataPathMisaligned]]
        * \|| [[groupMisaligned]]) lastExecuteCounter = vl(7, 5) - ![[dataPathMisaligned]]
        */
      val lastGroupCounter:   UInt =
        csrRegForMaskUnit.vl(
          parameter.laneParam.vlMaxBits - 1,
          parameter.dataPathWidthBits + log2Ceil(parameter.laneNumber)
        ) - !(dataPathMisaligned || groupMisaligned)
      val lastExecuteCounter: UInt = if (parameter.laneNumber > 1) {
        csrRegForMaskUnit.vl(
          parameter.dataPathWidthBits + log2Ceil(parameter.laneNumber) - 1,
          parameter.dataPathWidthBits
        ) - !dataPathMisaligned
      } else 0.U
      val lastGroup           = groupCounter === lastGroupCounter
      val lastExecute         = lastGroup && writeBackCounter === lastExecuteCounter
      val lastExecuteForGroup = writeBackCounter.andR
      // 计算正写的这个lane是不是在边界上
      val endOH               = UIntToOH(csrRegForMaskUnit.vl(parameter.dataPathWidthBits - 1, 0))
      val border              = lastExecute && dataPathMisaligned &&
        !(decodeResultReg(Decoder.compress) || decodeResultReg(Decoder.gather))
      val lastGroupMask       = scanRightOr(endOH(parameter.datapathWidth - 1, 1))
      val mvType              = decodeResultReg(Decoder.mv)
      val readMv              = mvType && decodeResultReg(Decoder.targetRd)
      val writeMv             = mvType && !decodeResultReg(Decoder.targetRd) &&
        csrRegForMaskUnit.vl > csrRegForMaskUnit.vStart
      mvToVRF.foreach(d => when(requestRegDequeue.fire) { d := writeMv })
      // 读后写中的读
      val needWAR             = (maskTypeInstruction || border || reduce || readMv) && !popCount
      val skipLaneData: Bool = decodeResultReg(Decoder.mv)
      mixedUnit                            := writeMv || readMv
      maskReadLaneSelect.head              := UIntToOH(writeBackCounter)
      maskReadLaneSelect.head              := UIntToOH(writeBackCounter)
      maskWriteLaneSelect.head             := maskReadLaneSelect.head
      maskUnitReadVec.head.valid           := false.B
      maskUnitReadVec.head.bits.vs         := Mux(readMv, vs2, Mux(reduce, vs1, vd))
      maskUnitReadVec.head.bits.readSource := Mux(readMv, 1.U, Mux(reduce, 0.U, 2.U))
      maskUnitReadVec.head.bits.offset     := groupCounter
      maskUnitRead.bits.instructionIndex   := control.record.instructionIndex
      val readResultSelectResult = Mux1H(
        Pipe(true.B, maskUnitReadSelect, parameter.vrfReadLatency).bits,
        laneReadResult
      )
      // 把mask选出来
      val maskSelect             = v0(groupCounter ## writeBackCounter)
      val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt

      /** 正常全1 mask：[[maskSelect]] border: [[lastGroupMask]] mask && border: [[maskSelect]] & [[lastGroupMask]]
        */
      val maskCorrect: UInt = Mux(maskTypeInstruction, maskSelect, fullMask) &
        Mux(border, lastGroupMask, fullMask)
      // mask
      val sew1HCorrect = Mux(decodeResultReg(Decoder.widenReduce), vSewOHForMask ## false.B, vSewOHForMask)
      // 写的data
      val writeData    = (WARRedResult.bits & (~maskCorrect).asUInt) | (regroupData(writeBackCounter) & maskCorrect)
      val writeMask    = Mux(sew1HCorrect(2) || !reduce, 15.U, Mux(sew1HCorrect(1), 3.U, 1.U))
      maskUnitWriteVec.head.valid                 := false.B
      maskUnitWriteVec.head.bits.vd               := vd
      maskUnitWriteVec.head.bits.offset           := groupCounter
      maskUnitWriteVec.head.bits.data             := Mux(writeMv, rs1, Mux(reduce, dataResult.bits, writeData))
      maskUnitWriteVec.head.bits.last             := control.state.wLast || reduce
      maskUnitWriteVec.head.bits.instructionIndex := control.record.instructionIndex

      val waitReadResult: Bool = Wire(Bool())
      val maskUnitReadVrf = maskUnitReadReady && maskUnitReadVec.map(_.valid).reduce(_ || _) && !waitReadResult
      val readNext        = RegNext(maskUnitReadVrf)
      waitReadResult := RegNext(readNext) || readNext
      when(Pipe(maskUnitReadVrf, false.B, parameter.vrfReadLatency).valid) {
        WARRedResult.bits  := readResultSelectResult
        WARRedResult.valid := true.B
      }
      // alu start
      val aluInput1 = Mux(
        (Seq(executeCounter === 0.U) ++ orderedReduceGroupCount.map(_ === 0.U)).reduce(_ && _),
        Mux(
          needWAR,
          WARRedResult.bits & FillInterleaved(8, writeMask),
          0.U
        ),
        dataResult.bits
      )
      val aluInput2 = Mux1H(UIntToOH(executeCounter), data.map(d => Mux(d.valid, d.bits, 0.U)))
      val skipFlotReduce: Bool                           = !Mux1H(UIntToOH(executeCounter), flotReduceValid.map(_.getOrElse(false.B)))
      // red alu instance
      val adder:          Instance[ReduceAdder]          = Instantiate(new ReduceAdder(parameter.datapathWidth))
      val logicUnit:      Instance[LaneLogic]            = Instantiate(new LaneLogic(parameter.datapathWidth))
      // option unit for flot reduce
      val floatAdder:     Option[Instance[FloatAdder]]   =
        Option.when(parameter.fpuEnable)(Instantiate(new FloatAdder(8, 24)))
      val flotCompare:    Option[Instance[FloatCompare]] =
        Option.when(parameter.fpuEnable)(Instantiate(new FloatCompare(8, 24)))

      val sign = !decodeResultReg(Decoder.unsigned1)
      adder.request.src    := VecInit(
        Seq(
          (aluInput1(parameter.datapathWidth - 1) && sign) ## aluInput1,
          (aluInput2(parameter.datapathWidth - 1) && sign) ## aluInput2
        )
      )
      // popCount 在top视为reduce add
      adder.request.opcode := Mux(popCount, 0.U, decodeResultReg(Decoder.uop))
      adder.request.sign   := sign
      adder.request.vSew   := Mux(popCount, 2.U, OHToUInt(sew1HCorrect))

      floatAdder.foreach { fAdder =>
        fAdder.io.a            := aluInput1
        fAdder.io.b            := aluInput2
        fAdder.io.roundingMode := csrRegForMaskUnit.vxrm
      }

      flotCompare.foreach { fCompare =>
        fCompare.io.a     := aluInput1
        fCompare.io.b     := aluInput2
        // max -> 12, min -> 8
        fCompare.io.isMax := decodeResultReg(Decoder.uop)(2)
      }

      logicUnit.req.src    := VecInit(Seq(aluInput1, aluInput2))
      logicUnit.req.opcode := decodeResultReg(Decoder.uop)

      // reduce resultSelect
      val intReduceResult = Mux(
        decodeResultReg(Decoder.adder) || popCount,
        adder.response.data,
        logicUnit.resp
      )
      val flotReduceResult: Option[UInt] = Option.when(parameter.fpuEnable)(
        Mux(
          skipFlotReduce,
          aluInput1,
          Mux(decodeResultReg(Decoder.fpExecutionType) === 0.U, floatAdder.get.io.out, flotCompare.get.io.out)
        )
      )
      val aluOutPut = Mux1H(
        Seq(if (parameter.fpuEnable) reduce && !decodeResultReg(Decoder.float) else reduce) ++
          Option.when(parameter.fpuEnable)(reduce && decodeResultReg(Decoder.float)),
        Seq(intReduceResult) ++ flotReduceResult
      )
      // slid & gather unit
      val slideUp   = decodeResultReg(Decoder.topUop)(1)
      val slide1    = decodeResultReg(Decoder.topUop)(0) && decodeResultReg(Decoder.slid)

      /** special uop 里面编码了extend的信息： specialUop(1,0): 倍率 specialUop(2)：是否是符号
        */
      val extendSourceSew: Bool = (csrRegForMaskUnit.vSew >> decodeResultReg(Decoder.topUop)(1, 0))(0)
      val extendSign:      Bool = decodeResultReg(Decoder.topUop)(2)
      // gather 相关的控制
      val gather16:        Bool = decodeResultReg(Decoder.gather16)
      val maskUnitEEW = Mux(gather16, 1.U, Mux(extend, extendSourceSew, csrRegForMaskUnit.vSew))
      val maskUnitEEW1H: UInt = UIntToOH(maskUnitEEW)
      val maskUnitByteEnable = maskUnitEEW1H(2) ## maskUnitEEW1H(2) ## maskUnitEEW1H(2, 1).orR ## true.B
      val maskUnitBitEnable  = FillInterleaved(8, maskUnitByteEnable)
      maskUnitWriteVec.head.bits.mask := Mux(writeMv, maskUnitByteEnable, writeMask)
      // log2(dataWidth * laneNumber / 8)
      val maskUnitDataOffset =
        (elementIndexCount << maskUnitEEW).asUInt(
          log2Ceil(parameter.datapathWidth * parameter.laneNumber / 8) - 1,
          0
        ) ## 0.U(3.W)
      val maskUnitData       = ((VecInit(data.map(_.bits)).asUInt >> maskUnitDataOffset).asUInt & maskUnitBitEnable)(
        parameter.datapathWidth - 1,
        0
      )

      val compareWire = Mux(decodeResultReg(Decoder.slid), rs1, maskUnitData)
      val compareAdvance: Bool = (compareWire >> log2Ceil(parameter.vLen)).asUInt.orR
      val compareResult:  Bool =
        largeThanVLMax(compareWire, compareAdvance, csrRegForMaskUnit.vlmul, csrRegForMaskUnit.vSew)
      // 正在被gather使用的数据在data的那个组里
      val gatherDataSelect           =
        UIntToOH((false.B ## maskUnitDataOffset)(5 + (log2Ceil(parameter.laneNumber).max(1)) - 1, 5))
      val dataTail                   = Mux1H(UIntToOH(maskUnitEEW)(1, 0), Seq(3.U(2.W), 2.U(2.W)))
      val lastElementForData         = gatherDataSelect.asBools.last && maskUnitDataOffset(4, 3) === dataTail
      val lastElementForCompressMask = elementIndexCount(log2Ceil(parameter.datapathWidth) - 1, 0).andR
      val maskUnitDataReady: Bool = (gatherDataSelect & VecInit(data.map(_.valid)).asUInt).orR
      // 正在被gather使用的数据是否就绪了
      val isSlide = !(gather || extend)
      val slidUnitDataReady: Bool = maskUnitDataReady || isSlide
      val compressDataReady              = maskUnitDataReady || !(decodeResultReg(Decoder.compress) || decodeResultReg(Decoder.iota))
      // slid 先用状态机
      val idle :: sRead :: sWrite :: Nil = Enum(3)
      val slideState                     = RegInit(idle)
      val readState                      = slideState === sRead

      // slid 的立即数是0扩展的
      val slidSize           = Mux(slide1, 1.U, Mux(decodeResultReg(Decoder.itype), vs1, rs1))
      // todo: 这里是否有更好的处理方式
      val slidSizeLSB        = slidSize(parameter.laneParam.vlMaxBits - 1, 0)
      // down +
      // up -
      val directionSelection = Mux(slideUp, (~slidSizeLSB).asUInt, slidSizeLSB)
      val slideReadIndex     = elementIndexCount + directionSelection + slideUp
      val readIndex: UInt = Mux(
        !maskUnitIdle,
        Mux(
          decodeResultReg(Decoder.slid),
          slideReadIndex,
          maskUnitData
        ),
        gatherWire
      )

      def indexAnalysis(elementIndex: UInt, csrInput: CSRInterface = csrRegForMaskUnit) = {
        val sewInput   = csrInput.vSew
        val sewOHInput = UIntToOH(csrInput.vSew)(2, 0)
        val intLMULInput: UInt = (1.U << csrInput.vlmul(1, 0)).asUInt
        val dataPosition = (elementIndex(parameter.laneParam.vlMaxBits - 2, 0) << sewInput)
          .asUInt(parameter.laneParam.vlMaxBits - 2, 0)
        val accessMask   = Mux1H(
          sewOHInput(2, 0),
          Seq(
            UIntToOH(dataPosition(1, 0)),
            FillInterleaved(2, UIntToOH(dataPosition(1))),
            15.U(4.W)
          )
        )
        // 数据起始位置在32bit(暂时只32)中的偏移,由于数据会有跨lane的情况,融合的优化时再做
        val dataOffset   = (dataPosition(1) && sewOHInput(1, 0).orR) ## (dataPosition(0) && sewOHInput(0)) ## 0.U(3.W)
        val accessLane   = if (parameter.laneNumber > 1) dataPosition(log2Ceil(parameter.laneNumber) + 1, 2) else 0.U(1.W)
        // 32 bit / group
        val dataGroup    = (dataPosition >> (log2Ceil(parameter.laneNumber) + 2)).asUInt
        val offsetWidth: Int = parameter.laneParam.vrfParam.vrfOffsetBits
        val offset            = dataGroup(offsetWidth - 1, 0)
        val accessRegGrowth   = (dataGroup >> offsetWidth).asUInt
        val decimalProportion = offset ## accessLane
        // 1/8 register
        val decimal           = decimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

        /** elementIndex 需要与vlMax比较, vLen * lmul /sew 这个计算太复杂了 我们可以换一个角度,计算读寄存器的增量与lmul比较,就能知道下标是否超vlMax了 vlmul
          * 需要区分整数与浮点
          */
        val overlap      =
          (csrInput.vlmul(2) && decimal >= intLMULInput(3, 1)) ||
            (!csrInput.vlmul(2) && accessRegGrowth >= intLMULInput)
        accessRegGrowth >= csrInput.vlmul
        val reallyGrowth = accessRegGrowth(2, 0)
        (accessMask, dataOffset, accessLane, offset, reallyGrowth, overlap)
      }
      val srcOverlap: Bool = !decodeResultReg(Decoder.itype) && (rs1 >= csrRegForMaskUnit.vl)
      // rs1 >= vlMax
      val srcOversize                                                                   = !decodeResultReg(Decoder.itype) && !slide1 && compareResult
      val signBit                                                                       = Mux1H(
        vSewOHForMask,
        readIndex(parameter.laneParam.vlMaxBits - 1, parameter.laneParam.vlMaxBits - 3).asBools.reverse
      )
      // 对于up来说小于offset的element是不变得的
      val slideUpUnderflow                                                              = slideUp && !slide1 && (signBit || srcOverlap)
      val elementActive: Bool = v0.asUInt(elementIndexCount) || vm
      val slidActive = elementActive && (!slideUpUnderflow || !decodeResultReg(Decoder.slid))
      // index >= vlMax 是写0
      val overlapVlMax: Bool = !slideUp && (signBit || srcOversize)
      // select csr
      val csrSelect                                                          = Mux(control.state.idle, requestRegCSR, csrRegForMaskUnit)
      // slid read
      val (_, readDataOffset, readLane, readOffset, readGrowth, lmulOverlap) = indexAnalysis(readIndex, csrSelect)
      gatherReadDataOffset := readDataOffset
      val readOverlap           = lmulOverlap || overlapVlMax
      val skipRead              = readOverlap || (gather && compareResult) || extend
      val maskUnitWriteVecFire1 = maskUnitReadVec(1).valid && maskUnitReadReady
      val readFireNext1:      Bool = RegNext(maskUnitWriteVecFire1)
      val readFireNextNext1:  Bool = RegNext(readFireNext1)
      val port1WaitForResult: Bool = readFireNext1 || readFireNextNext1
      val gatherTryToRead =
        gatherNeedRead && !VecInit(lsu.vrfReadDataPorts.map(_.valid)).asUInt.orR && !gatherReadFinish
      maskUnitReadVec(1).valid           := (readState || gatherTryToRead) && !port1WaitForResult
      maskUnitReadVec(1).bits.vs         := Mux(readState, vs2, requestRegDequeue.bits.instruction(24, 20)) + readGrowth
      maskUnitReadVec(1).bits.readSource := 1.U
      maskUnitReadVec(1).bits.offset     := readOffset
      maskReadLaneSelect(1)              := UIntToOH(readLane)
      // slid write, vlXXX: 用element index 算出来的
      val (vlMask, vlDataOffset, vlLane, vlOffset, vlGrowth, _) = indexAnalysis(elementIndexCount)
      val writeState                                            = slideState === sWrite
      // 处理数据,先硬移位吧
      val slidReadData: UInt = ((WARRedResult.bits >> readDataOffset) << vlDataOffset)
        .asUInt(parameter.datapathWidth - 1, 0)
      val selectRS1 = slide1 && ((slideUp && firstElement) || (!slideUp && lastElement))
      // extend 类型的扩展和移位
      val extendData: UInt = (Mux(
        extendSourceSew,
        Fill(parameter.datapathWidth - 16, extendSign && maskUnitData(15)) ## maskUnitData(15, 0),
        Fill(parameter.datapathWidth - 8, extendSign && maskUnitData(7)) ## maskUnitData(7, 0)
      ) << vlDataOffset).asUInt(parameter.xLen - 1, 0)

      /** vd 的值有4种：
        *   1. 用readIndex读出来的vs2的值
        *   1. 0
        *   1. slide1 时插进来的rs1
        *   1. extend 的值
        */
      val slidWriteData = Mux1H(
        Seq((!(readOverlap || selectRS1 || extend)) || (gather && !compareResult), selectRS1, extend),
        Seq(slidReadData, (rs1 << vlDataOffset).asUInt(parameter.xLen - 1, 0), extendData)
      )
      maskUnitWriteVec(1).valid                 := writeState && slidActive
      maskUnitWriteVec(1).bits.vd               := vd + vlGrowth
      maskUnitWriteVec(1).bits.offset           := vlOffset
      maskUnitWriteVec(1).bits.mask             := vlMask
      maskUnitWriteVec(1).bits.data             := slidWriteData
      maskUnitWriteVec(1).bits.last             := lastElement
      maskUnitWriteVec(1).bits.instructionIndex := control.record.instructionIndex
      maskWriteLaneSelect(1)                    := UIntToOH(vlLane)
      // slid 跳状态机
      when(slideState === idle) {
        when((!slidUnitIdle) && slidUnitDataReady) {
          when(skipRead) {
            slideState := sWrite
          }.otherwise {
            slideState := sRead
          }
        }
      }
      when(readState) {
        // 不需要valid,因为这个状态下一定是valid的
        when(readFireNextNext1) {
          slideState := sWrite
        }
      }
      when(writeState) {
        when(maskUnitWriteReady || !slidActive) {
          when(lastElement) {
            slideState   := idle
            slidUnitIdle := true.B
            when(gather || extend) {
              synchronized           := true.B
              dataClear              := true.B
              maskUnitReadOnlyFinish := true.B
            }
          }.otherwise {
            when(lastElementForData && (gather || extend)) {
              synchronized := true.B
              dataClear    := true.B
              slideState   := idle
            }.otherwise {
              // todo: skip read
              slideState := sRead
            }
            updateMaskIndex := true.B
          }
        }
      }

      // compress & iota
      val idle1 :: sReadMask :: sWrite1 :: Nil = Enum(3)
      val compressState                        = RegInit(idle1)
      val compressStateIdle                    = compressState === idle1
      val compressStateRead                    = compressState === sReadMask
      val compressStateWrite                   = compressState === sWrite1

      // compress 用vs1当mask,需要先读vs1
      val readCompressMaskNext = Pipe(maskUnitReadReady && compressStateRead, false.B, parameter.vrfReadLatency).valid
      when(readCompressMaskNext) {
        maskDataForCompress := readResultSelectResult
      }

      // 处理 iota
      val iotaDataOffset:  UInt = elementIndexCount(log2Ceil(parameter.datapathWidth * parameter.laneNumber) - 1, 0)
      val lastDataForIota: Bool = iotaDataOffset.andR
      val iotaData = VecInit(data.map(_.bits)).asUInt(iotaDataOffset)
      val iota     = decodeResultReg(Decoder.iota)

      val maskUnitReadFire2: Bool = maskUnitReadVec(2).valid && maskUnitReadReady
      val readFireNext2      = RegNext(maskUnitReadFire2)
      val readFireNextNext2  = RegNext(readFireNext2)
      val port2WaitForResult = readFireNextNext2 || readFireNext2

      /** 计算需要读的mask的相关 elementIndexCount -> 11bit 只会访问单寄存器 elementIndexCount(4, 0)做为32bit内的offset elementIndexCount(7,
        * 5)作为lane的选择 elementIndexCount(9, 8)作为offset
        */
      // compress read
      maskUnitReadVec(2).valid           := compressStateRead && !port2WaitForResult
      maskUnitReadVec(2).bits.vs         := vs1
      maskUnitReadVec(2).bits.readSource := 0.U
      maskUnitReadVec(2).bits.offset     := elementIndexCount(
        log2Ceil(parameter.datapathWidth) + log2Ceil(parameter.laneNumber) +
          parameter.laneParam.vrfParam.vrfOffsetBits - 1,
        log2Ceil(parameter.datapathWidth) + log2Ceil(parameter.laneNumber)
      )
      maskReadLaneSelect(2)              := UIntToOH(
        elementIndexCount(
          log2Ceil(parameter.datapathWidth) + ((log2Ceil(parameter.laneNumber) - 1).max(0)),
          log2Ceil(parameter.datapathWidth)
        )
      )
      // val lastElementForMask: Bool = elementIndexCount(4, 0).andR
      val maskForCompress: Bool = maskDataForCompress(elementIndexCount(log2Ceil(parameter.datapathWidth) - 1, 0))

      // compress vm=0 是保留的
      val skipWrite = !Mux(decodeResultReg(Decoder.compress), maskForCompress, elementActive)
      val dataGroupTailForCompressUnit: Bool = Mux(iota, lastDataForIota, lastElementForData)

      // 计算compress write的位置信息
      val (compressMask, compressDataOffset, compressLane, compressOffset, compressGrowth, _) =
        indexAnalysis(compressWriteCount)
      val compressWriteData                                                                   = (maskUnitData << compressDataOffset).asUInt
      val iotaWriteData                                                                       = (iotaCount << vlDataOffset).asUInt
      // compress write
      maskUnitWriteVec(2).valid                 := compressStateWrite && !skipWrite
      maskUnitWriteVec(2).bits.vd               := vd + Mux(iota, vlGrowth, compressGrowth)
      maskUnitWriteVec(2).bits.offset           := Mux(iota, vlOffset, compressOffset)
      maskUnitWriteVec(2).bits.mask             := Mux(iota, vlMask, compressMask)
      maskUnitWriteVec(2).bits.data             := Mux(iota, iotaWriteData, compressWriteData)
      maskUnitWriteVec(2).bits.last             := lastElement
      maskUnitWriteVec(2).bits.instructionIndex := control.record.instructionIndex
      maskWriteLaneSelect(2)                    := UIntToOH(Mux(iota, vlLane, compressLane))

      // 跳状态机
      // compress每组数据先读mask
      val firstState = Mux(iota, sWrite1, sReadMask)
      when(compressStateIdle && (!iotaUnitIdle) && compressDataReady) {
        compressState := firstState
      }

      when(compressStateRead && readFireNextNext2) {
        compressState := sWrite1
      }

      when(compressStateWrite) {
        when(maskUnitWriteReady || skipWrite) {
          when(!skipWrite) {
            compressWriteCount := compressWriteCount + 1.U
            iotaCount          := iotaCount + iotaData
          }
          when(lastElement) {
            compressState          := idle
            iotaUnitIdle           := true.B
            synchronized           := true.B
            dataClear              := true.B
            maskUnitReadOnlyFinish := true.B
          }.otherwise {
            when(lastElementForCompressMask) {
              // update vs1 as mask for compress
              compressState := sRead
            }
            when(dataGroupTailForCompressUnit) {
              synchronized  := true.B
              dataClear     := true.B
              compressState := idle
            }
            updateMaskIndex := true.B
          }
        }
      }
      // for small vl & reduce
      val accessByte              = (csrRegForMaskUnit.vl << csrRegForMaskUnit.vSew).asUInt
      // vl < row(vl)
      val smallVL                 = accessByte < (parameter.datapathWidth * parameter.laneNumber / 8).U
      val byteSizePerDataPathBits = log2Ceil(parameter.datapathWidth / 8)
      val lastExecuteCounterForReduce: UInt = if (parameter.laneNumber > 1) {
        accessByte(
          byteSizePerDataPathBits + log2Ceil(parameter.laneNumber) - 1,
          byteSizePerDataPathBits
        ) - !accessByte(byteSizePerDataPathBits - 1, 0).orR
      } else 0.U
      val lastGroupDataWaitMaskForRed: UInt = scanRightOr(UIntToOH(lastExecuteCounterForReduce))
      // alu end
      val maskOperation =
        decodeResultReg(Decoder.maskLogic) ||
          decodeResultReg(Decoder.maskDestination) ||
          decodeResultReg(Decoder.ffo)
      // How many data path(32 bit) will used by maskDestination instruction.
      val maskDestinationByteSize: Bits =
        csrRegForMaskUnit.vl(log2Ceil(parameter.dLen) - 1, 0) << csrRegForMaskUnit.vSew
      val maskDestinationUseDataPathSize =
        (maskDestinationByteSize >> 2).asUInt + maskDestinationByteSize(1, 0).orR
      val lastGroupCountForThisGroup: UInt = maskDestinationUseDataPathSize(log2Ceil(parameter.laneNumber) - 1, 0)
      val counterForMaskDestination:  UInt = if (parameter.laneNumber > 1) {
        (lastGroupCountForThisGroup - 1.U) |
          Fill(
            log2Ceil(parameter.laneNumber),
            (maskDestinationUseDataPathSize >> log2Ceil(parameter.laneNumber)).asUInt.orR
          )
      } else 0.U

      val waitSourceDataCounter =
        Mux(decodeResultReg(Decoder.maskDestination), counterForMaskDestination, lastExecuteCounter)
      val lastGroupDataWaitMask = scanRightOr(UIntToOH(waitSourceDataCounter))
      // todo: other ways
      val lastOrderedGroup:  Option[Bool] = orderedReduceGroupCount.map(count =>
        (count ## 0
          .U(log2Ceil(parameter.laneNumber).W) + -1.S(log2Ceil(parameter.laneNumber).W).asUInt) >= csrRegForMaskUnit.vl
      )
      val misalignedOrdered: Bool         = if (parameter.fpuEnable) {
        lastOrderedGroup.get && csrRegForMaskUnit.vl(log2Ceil(parameter.laneNumber) - 1, 0).orR && decodeResultReg(
          Decoder.float
        )
      } else false.B
      val dataMask  =
        Mux(
          maskOperation && lastGroup,
          lastGroupDataWaitMask,
          Mux(
            reduce && (smallVL || misalignedOrdered),
            lastGroupDataWaitMaskForRed,
            -1.S(parameter.laneNumber.W).asUInt
          )
        )
      val dataReady = ((~dataMask).asUInt | VecInit(data.map(_.valid)).asUInt).andR || skipLaneData
      when(
        // data ready
        dataReady &&
          // state check
          !control.state.sMaskUnitExecution
      ) {
        // 读
        when(needWAR && !WARRedResult.valid) {
          maskUnitReadVec.head.valid := true.B
        }
        // 可能有的计算
        val nextExecuteIndex:          UInt         = executeCounter + 1.U
        val isLastExecuteForGroup:     Bool         = executeCounter(log2Ceil(parameter.laneNumber) - 1, 0).andR
        val lastExecuteForInstruction: Option[Bool] = orderedReduceGroupCount.map(count =>
          (count ## 0.U(log2Ceil(parameter.laneNumber).W) + nextExecuteIndex) === csrRegForMaskUnit.vl
        )
        val readFinish        = WARRedResult.valid || !needWAR
        val readDataSign      =
          Mux1H(vSewOHForMask(2, 0), Seq(WARRedResult.bits(7), WARRedResult.bits(15), WARRedResult.bits(31)))
        when(readFinish && !executeFinishReg) {
          when(readMv) {
            control.state.sMaskUnitExecution := true.B
            // signExtend for vmv.x.s
            dataResult.bits                  := Mux(vSewOHForMask(2), WARRedResult.bits(31, 16), Fill(16, readDataSign)) ##
              Mux(vSewOHForMask(0), Fill(8, readDataSign), WARRedResult.bits(15, 8)) ##
              WARRedResult.bits(7, 0)

          }.otherwise {
            executeCounter := nextExecuteIndex
            when(executeCounter =/= csrRegForMaskUnit.vl) {
              dataResult.bits := aluOutPut
            }
            if (parameter.fpuEnable) {
              when(!orderedReduceIdle.get) {
                when(lastExecuteForInstruction.get) {
                  orderedReduceIdle.get := true.B
                }.elsewhen(isLastExecuteForGroup) {
                  synchronized   := true.B
                  executeCounter := 0.U
                  dataClear      := true.B
                  orderedReduceGroupCount.foreach(d => d := d + 1.U)
                }
              }
            }
          }
        }
        // for vfredmax
        val lastReduceCounter =
          executeCounter === csrRegForMaskUnit.vl || executeCounter(log2Ceil(parameter.laneNumber))
        dontTouch(lastReduceCounter)
        val executeFinish: Bool =
          (lastReduceCounter || !(reduce || popCount) || orderedReduce) && maskUnitIdle
        val schedulerWrite = decodeResultReg(Decoder.maskDestination) || (reduce && !popCount) || writeMv
        val groupSync      = decodeResultReg(Decoder.ffo)
        // 写回
        when(readFinish && (executeFinish || writeMv || executeFinishReg)) {
          maskUnitWriteVec.head.valid := schedulerWrite
          executeFinishReg            := true.B
          when(maskUnitWriteReady || !schedulerWrite) {
            WARRedResult.valid := false.B
            writeBackCounter   := writeBackCounter + schedulerWrite
            when(lastExecuteForGroup || lastExecute || reduce || groupSync || writeMv || popCount) {
              synchronized := true.B
              dataClear    := true.B
              when(lastExecuteForGroup || groupSync) {
                executeForLastLaneFire := true.B
                groupCounter           := groupCounter + 1.U
              }
              when(lastExecute || reduce || writeMv || popCount) {
                control.state.sMaskUnitExecution := true.B
              }
            }
          }
        }
      }
    }
    control
  }

  /** lane is ready to receive new instruction. */
  val laneReady:    Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))
  val allLaneReady: Bool      = laneReady.asUInt.andR
  // TODO: review later
  // todo: 把scheduler的反馈也加上,lsu有更高的优先级

  /** the index type of instruction is finished. let LSU to kill the lane slot. todo: delete?
    */
  val completeIndexInstruction: Bool =
    ohCheck(lsu.lastReport, slots.last.record.instructionIndex, parameter.chainingSize) && !slots.last.state.idle

  val vrfWrite: Vec[DecoupledIO[VRFWriteRequest]] = Wire(
    Vec(
      parameter.laneNumber,
      Decoupled(
        new VRFWriteRequest(
          parameter.vrfParam.regNumBits,
          parameter.vrfParam.vrfOffsetBits,
          parameter.instructionIndexBits,
          parameter.datapathWidth
        )
      )
    )
  )

  val freeOR: Bool = VecInit(slots.map(_.state.idle)).asUInt.orR

  /** slot is ready to accept new instructions. */
  val slotReady: Bool = Mux(specialInstruction, slots.map(_.state.idle).last, freeOR)

  val source1Select: UInt =
    Mux(decodeResult(Decoder.gather), gatherData, Mux(decodeResult(Decoder.itype), immSignExtend, source1Extend))

  // data eew for extend type
  val extendDataEEW: Bool = (csrRegForMaskUnit.vSew >> decodeResult(Decoder.topUop)(1, 0))(0)
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

  /** instantiate lanes. TODO: move instantiate to top of class.
    */
  val laneVec: Seq[Instance[Lane]] = Seq.tabulate(parameter.laneNumber) { index =>
    val lane: Instance[Lane] = Instantiate(new Lane(parameter.laneParam))
    // lane.laneRequest.valid -> requestRegDequeue.ready -> lane.laneRequest.ready -> lane.laneRequest.bits
    // TODO: this is harmful for PnR design, since it broadcast ready singal to each lanes, which will significantly
    //       reduce the scalability for large number of lanes.
    lane.laneRequest.valid                 := requestRegDequeue.fire && !noOffsetReadLoadStore && !maskUnitInstruction
    // hard wire
    lane.laneRequest.bits.instructionIndex := requestReg.bits.instructionIndex
    lane.laneRequest.bits.decodeResult     := decodeResult
    lane.laneRequest.bits.vs1              := requestRegDequeue.bits.instruction(19, 15)
    lane.laneRequest.bits.vs2              := requestRegDequeue.bits.instruction(24, 20)
    lane.laneRequest.bits.vd               := requestRegDequeue.bits.instruction(11, 7)
    lane.laneRequest.bits.segment          := Mux(
      decodeResult(Decoder.nr),
      requestRegDequeue.bits.instruction(17, 15),
      requestRegDequeue.bits.instruction(31, 29)
    )

    lane.laneRequest.bits.loadStoreEEW   := requestRegDequeue.bits.instruction(13, 12)
    // if the instruction is vi and vx type of gather, gather from rs2 with mask VRF read channel from one lane,
    // and broadcast to all lanes.
    lane.laneRequest.bits.readFromScalar := source1Select

    lane.laneRequest.bits.issueInst  := requestRegDequeue.fire
    lane.laneRequest.bits.loadStore  := isLoadStoreType
    // let record in VRF to know there is a store instruction.
    lane.laneRequest.bits.store      := isStoreType
    // let lane know if this is a special instruction, which need group-level synchronization between lane and [[V]]
    lane.laneRequest.bits.special    := specialInstruction
    lane.laneRequest.bits.lsWholeReg := lsWholeReg
    // mask type instruction.
    lane.laneRequest.bits.mask       := maskType
    laneReady(index)                 := lane.laneRequest.ready

    lane.csrInterface      := requestRegCSR
    // index type EEW Decoded in the instruction
    lane.csrInterface.vSew := vSewSelect
    lane.csrInterface.vl   := evlForLane
    lane.laneIndex         := index.U

    // - LSU request next offset of group
    // - all lane are synchronized
    // - the index type of instruction is finished.
    lane.laneResponseFeedback.valid                 := lsu.lsuOffsetRequest || synchronized || completeIndexInstruction
    // - the index type of instruction is finished.
    // - for find first one.
    lane.laneResponseFeedback.bits.complete         :=
      completeIndexInstruction ||
        completedLeftOr(index) ||
        maskUnitReadOnlyFinish
    // tell lane which
    lane.laneResponseFeedback.bits.instructionIndex := slots.last.record.instructionIndex

    // lsu 优先会有死锁:
    // vmadc, v1, v2, 1 (vl=17) -> 需要先读后写
    // vse32.v v1, (a0) -> 依赖上一条,但是会先发出read
    // 读 lane
    lane.vrfReadAddressChannel.valid  := lsu.vrfReadDataPorts(index).valid ||
      (maskUnitRead.valid && maskUnitReadSelect(index))
    lane.vrfReadAddressChannel.bits   :=
      Mux(maskUnitRead.valid, maskUnitRead.bits, lsu.vrfReadDataPorts(index).bits)
    lsu.vrfReadDataPorts(index).ready := lane.vrfReadAddressChannel.ready && !maskUnitRead.valid
    readSelectMaskUnit(index)         :=
      lane.vrfReadAddressChannel.ready && maskUnitReadSelect(index)
    laneReadResult(index)             := lane.vrfReadDataChannel
    lsu.vrfReadResults(index)         := lane.vrfReadDataChannel

    // 写lane
    lane.vrfWriteChannel.valid := vrfWrite(index).valid || (maskUnitWrite.valid && maskUnitWriteSelect(index))
    lane.vrfWriteChannel.bits  :=
      Mux(vrfWrite(index).valid, vrfWrite(index).bits, maskUnitWrite.bits)
    vrfWrite(index).ready      := lane.vrfWriteChannel.ready
    writeSelectMaskUnit(index) :=
      lane.vrfWriteChannel.ready && !vrfWrite(index).valid && maskUnitWriteSelect(index)

    lsu.offsetReadResult(index).valid := lane.laneResponse.valid && lane.laneResponse.bits.toLSU
    lsu.offsetReadResult(index).bits  := lane.laneResponse.bits.data
    lsu.offsetReadIndex(index)        := lane.laneResponse.bits.instructionIndex

    instructionFinished(index).zip(slots.map(_.record.instructionIndex)).foreach { case (d, f) =>
      d := (UIntToOH(f(parameter.instructionIndexBits - 2, 0)) & lane.instructionFinished).orR
    }
    vxsatReportVec(index) := lane.vxsatReport
    val v0ForThisLane: Seq[UInt] = regroupV0.map(rv => cutUInt(rv, parameter.vLen / parameter.laneNumber)(index))
    val v0SelectBySew = Mux1H(UIntToOH(lane.maskSelectSew)(2, 0), v0ForThisLane)
    lane.maskInput     := cutUInt(v0SelectBySew, parameter.datapathWidth)(lane.maskSelect)
    lane.lsuLastReport := lsu.lastReport |
      Mux(
        maskUnitFlushVrf,
        indexToOH(slots.last.record.instructionIndex, parameter.chainingSize),
        0.U
      )

    lane.lsuMaskGroupChange      := lsu.lsuMaskGroupChange
    lane.loadDataInLSUWriteQueue := lsu.dataInWriteQueue(index)
    // 2 + 3 = 5
    val rowWith: Int = log2Ceil(parameter.datapathWidth / 8) + log2Ceil(parameter.laneNumber)
    lane.writeCount :=
      (requestReg.bits.writeByte >> rowWith).asUInt +
        (requestReg.bits.writeByte(rowWith - 1, 0) > ((parameter.datapathWidth / 8) * index).U)

    // 处理lane的mask类型请求
    laneSynchronize(index) := lane.laneResponse.valid && !lane.laneResponse.bits.toLSU
    when(laneSynchronize(index)) {
      data(index).valid   := true.B
      data(index).bits    := lane.laneResponse.bits.data
      completedVec(index) := lane.laneResponse.bits.ffoSuccess
      flotReduceValid(index).foreach(d => d := lane.laneResponse.bits.fpReduceValid.get)
    }

    // token manager
    tokenManager.writeV0(index).valid     := lane.vrfWriteChannel.fire && (lane.vrfWriteChannel.bits.vd === 0.U)
    tokenManager.writeV0(index).bits      := lane.vrfWriteChannel.bits.instructionIndex
    tokenManager.instructionFinish(index) := lane.instructionFinished

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

  lsu.maskInput.zip(lsu.maskSelect).foreach { case (data, index) =>
    data := cutUInt(v0.asUInt, parameter.maskGroupWidth)(index)
  }
  lsu.csrInterface     := requestRegCSR
  lsu.csrInterface.vl  := evlForLsu
  lsu.writeReadyForLsu := VecInit(laneVec.map(_.writeReadyForLsu)).asUInt.andR
  lsu.vrfReadyToStore  := VecInit(laneVec.map(_.vrfReadyToStore)).asUInt.andR

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
  // 暂时直接连lsu的写,后续需要处理scheduler的写
  vrfWrite.zip(lsu.vrfWritePort).foreach { case (sink, source) => sink <> source }

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
  val vrfAllocate:    Bool = VecInit(laneVec.map(_.vrfAllocateIssue)).asUInt.andR
  // - ready to issue instruction
  // - for vi and vx type of gather, it need to access vs2 for one time, we read vs2 firstly in `gatherReadFinish`
  //   and convert it to mv instruction.
  //   TODO: decode it directly
  // - for slide instruction, it is unordered, and may have RAW hazard,
  //   we detect the hazard and decide should we issue this slide or
  //   issue the instruction after the slide which already in the slot.
  requestRegDequeue.ready := executionReady && slotReady && (!gatherNeedRead || gatherReadFinish) &&
    instructionRAWReady && instructionIndexFree && vrfAllocate

  instructionToSlotOH := Mux(requestRegDequeue.fire, slotToEnqueue, 0.U)

  // instruction commit
  {
    val slotCommit: Vec[Bool] = VecInit(slots.map { inst =>
      // mask unit finish
      inst.state.sMaskUnitExecution &&
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
    io.retire.rd.bits.rdData := Mux(ffoType, ffoIndexReg.bits, dataResult.bits)
    // TODO: csr retire.
    io.retire.csr.bits.vxsat := (slotCommit.asUInt & VecInit(slots.map(_.vxsat)).asUInt).orR
    io.retire.csr.bits.fflag := DontCare
    io.retire.csr.valid      := false.B
    io.retire.mem.valid      := (slotCommit.asUInt & VecInit(slots.map(_.record.isLoadStore)).asUInt).orR
    lastSlotCommit           := slotCommit.last
  }

  // write v0(mask)
  v0.zipWithIndex.foreach { case (data, index) =>
    // 属于哪个lane
    val laneIndex: Int = index % parameter.laneNumber
    // 取出写的端口
    val v0Write = laneVec(laneIndex).v0Update
    // offset
    val offset: Int = index / parameter.laneNumber
    val maskExt = FillInterleaved(8, v0Write.bits.mask)
    when(v0Write.valid && v0Write.bits.offset === offset.U) {
      data := (data & (~maskExt).asUInt) | (maskExt & v0Write.bits.data)
    }
  }
  when(dataClear) {
    data.foreach(_.valid := false.B)
  }
  // don't care有可能会导致先读后写失败
  maskUnitReadVec.foreach(_.bits.instructionIndex := slots.last.record.instructionIndex)

  layer.block(layers.Verification) {

    /** Probes
      */
    val probeWire = Wire(new T1Probe(parameter))
    define(io.t1Probe, ProbeValue(probeWire))
    probeWire.instructionCounter  := instructionCounter
    probeWire.instructionIssue    := requestRegDequeue.fire
    probeWire.issueTag            := requestReg.bits.instructionIndex
    probeWire.retireValid         := retire
    probeWire.requestReg          := requestReg
    probeWire.requestRegReady     := requestRegDequeue.ready
    // maskUnitWrite maskUnitWriteReady
    probeWire.writeQueueEnq.valid := maskUnitWrite.valid && maskUnitWriteReady
    probeWire.writeQueueEnq.bits  := maskUnitWrite.bits.instructionIndex
    probeWire.writeQueueEnqMask   := maskUnitWrite.bits.mask
    probeWire.instructionValid    := maskAnd(
      !slots.last.state.sMaskUnitExecution && !slots.last.state.idle,
      indexToOH(slots.last.record.instructionIndex, parameter.chainingSize * 2)
    ).asUInt
    probeWire.responseCounter     := responseCounter
    probeWire.laneProbes.zip(laneVec).foreach { case (p, l) => p := probe.read(l.laneProbe) }
    probeWire.lsuProbe            := probe.read(lsu.lsuProbe)
    probeWire.issue.valid         := io.issue.fire
    probeWire.issue.bits          := instructionCounter
    probeWire.retire.valid        := io.retire.rd.valid
    probeWire.retire.bits         := io.retire.rd.bits.rdData
    probeWire.idle                := slots.map(_.state.idle).reduce(_ && _)
  }

  // coverage
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
  parameter.decoderParam.allInstructions.filter { instruction: Instruction =>
    // format: off
    !(zve64x.contains(instruction.name) && parameter.xLen == 32) &&
    !(zve64f.contains(instruction.name) && parameter.xLen == 32 && parameter.fpuEnable) &&
    !((zve32f ++ zve64f).contains(instruction.name) && !parameter.fpuEnable)
    // format: on
  }.map { instruction: Instruction =>
    val issueMatch =
      Sequence.BoolSequence(requestReg.bits.issue.instruction === BitPat("b" + instruction.encoding.toString))
    CoverProperty(issueMatch, label = Some(s"t1_cover_issue_${instruction.name}"))
  }

  // new V Request from core
  // val requestValidProbe: Bool = IO(Output(Probe(Bool())))
  // val requestReadyProbe: Bool = IO(Output(Probe(Bool())))
  // define(requestValidProbe, ProbeValue(request.valid))
  // define(requestReadyProbe, ProbeValue(request.ready))

  // Store decoded request
  // val requestRegValidProbe: Bool = IO(Output(Probe(Bool())))
  // define(requestRegValidProbe, ProbeValue(requestReg.valid))

  /** Dispatch request from requestReg to lane
    *
    * There are four cases that might affect the ready status of requestRegDequeue:
    *   1. executionReady: There are capable slot to load this instruction in top local 2. slotReady: Execution unit
    *      accept this instrution 3. !gatherNeedRead || gatherReadFinish: This is not a instrution which needs to wait
    *      for gather 4. instructionRAWReady: This is not instruction which will cause harzard that can not be avoid.
    */
  // val requestRegDequeueValidProbe: Bool = IO(Output(Probe(Bool())))
  // val requestRegDequeueReadyProbe: Bool = IO(Output(Probe(Bool())))
  // define(requestRegDequeueValidProbe, ProbeValue(requestRegDequeue.valid))
  // define(requestRegDequeueReadyProbe, ProbeValue(requestRegDequeue.ready))

  // val executionReadyProbe = IO(Output(Probe(Bool())))
  // define(executionReadyProbe, ProbeValue(executionReady))

  // val slotReadyProbe = IO(Output(Probe(Bool())))
  // define(slotReadyProbe, ProbeValue(slotReady))

  // val gatherNeedReadProbe = IO(Output(Probe(Bool())))
  // define(gatherNeedReadProbe, ProbeValue(gatherNeedRead))
  // val gatherReadFinishProbe = IO(Output(Probe(Bool())))
  // define(gatherReadFinishProbe, ProbeValue(gatherReadFinish))

  // val instructionRAWReadyProbe = IO(Output(Probe(Bool())))
  // define(instructionRAWReadyProbe, ProbeValue(instructionRAWReady))
  // End of requestRegDequeueProbe

  /** Response send back to core.
    *
    * There are four cases that might affect response is valid or not:
    *
    *   1. slot(n).state.sMaskUnit: The mask unit in slot n has finished its work. 2. slot(n).state.wLast: The execution
    *      unit in slot n has finished its work. 3. !slot(n).state.sCommit: This instruction doesn't committed. This is
    *      not an important signal so we don't capture it. 4. slot(n).record.instruction Index == responseCounter:
    *      current instruction is the oldest insn in V
    */
  // val responseValidProbe: Bool = IO(Output(Probe(Bool())))
  // define(responseValidProbe, ProbeValue(response.valid))

  // val slotStateProbe: Seq[(Bool, Bool, Bool)] = slots.map { inst =>
  //  val sMaskUnitProbe = IO(Output(Probe(Bool())))
  //  define(sMaskUnitProbe, ProbeValue(inst.state.sMaskUnitExecution))
  //  val wLastProbe = IO(Output(Probe(Bool())))
  //  define(wLastProbe, ProbeValue(inst.state.wLast))
  //  val isLastInstProbe = IO(Output(Probe(Bool())))
  //  define(isLastInstProbe, ProbeValue(inst.record.instructionIndex === responseCounter))
  //  (sMaskUnitProbe, wLastProbe, isLastInstProbe)
  // }
}
