// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.SerializableModule
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.properties.{AnyClassType, ClassType, Property}
import chisel3.util._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.Decoder

// top uop decode
// uu ii x -> uu: unit index; ii: Internal encoding, x: additional encode

// slid & gather unit, need read vrf in mask unit(00)
// 00 00 x -> slid; x? up: down
// 00 01 x -> slid1; x? up: down
// 00 10 x -> gather; x? 16 : sew  todo:（multi address check/ index -> data cache?）

// compress & viota unit & vmv(01)
// These instructions cannot extend their execution width indefinitely.
// 01 00 x -> x ? compress : viota
// 01 01 x -> vmv; x: write rd ?

// reduce unit(10) n + 8 + m -> n + 3 + m // Folded into datapath, then folded into sew
// The Reduce instruction folds the data.
// Considering the sequential addition, a state machine is needed to control it.
// 10 00 x -> adder; x: widen reduce?
// 10 01 x -> logic; x: dc
// 10 10 x -> floatAdder; x: order?
// 10 11 x -> flotCompare; x: dc

// extend unit & maskdestination(11)
// These instructions write an entire data path each time they are executed.
// 11 mm x -> s(z)ext; mm: multiple(00, 01, 10); x ? sign : zero
// 11 11 1 -> maskdestination

class MaskUnitInterface(parameter: T1Parameter) extends Bundle {
  val clock:             Clock                             = Input(Clock())
  val reset:             Reset                             = Input(Reset())
  val instReq:           ValidIO[MaskUnitInstReq]          = Flipped(Valid(new MaskUnitInstReq(parameter)))
  val exeReq:            Vec[ValidIO[MaskUnitExeReq]]      = Flipped(
    Vec(parameter.laneNumber, Valid(new MaskUnitExeReq(parameter.laneParam)))
  )
  val exeResp:           Vec[DecoupledIO[VRFWriteRequest]] = Vec(
    parameter.laneNumber,
    Decoupled(
      new VRFWriteRequest(
        parameter.vrfParam.regNumBits,
        parameter.laneParam.vrfOffsetBits,
        parameter.instructionIndexBits,
        parameter.datapathWidth
      )
    )
  )
  val writeRelease:      Vec[Bool]                         = Vec(parameter.laneNumber, Input(Bool()))
  val tokenIO:           Vec[LaneTokenBundle]              = Flipped(Vec(parameter.laneNumber, new LaneTokenBundle))
  val readChannel:       Vec[DecoupledIO[VRFReadRequest]]  = Vec(
    parameter.laneNumber,
    Decoupled(
      new VRFReadRequest(
        parameter.vrfParam.regNumBits,
        parameter.laneParam.vrfOffsetBits,
        parameter.instructionIndexBits
      )
    )
  )
  val readResult:        Vec[ValidIO[UInt]]                = Flipped(Vec(parameter.laneNumber, Valid(UInt(parameter.datapathWidth.W))))
  val writeRD:           ValidIO[UInt]                     = Valid(UInt(parameter.datapathWidth.W))
  val lastReport:        UInt                              = Output(UInt((2 * parameter.chainingSize).W))
  val laneMaskInput:     Vec[UInt]                         = Output(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
  val laneMaskSelect:    Vec[UInt]                         = Input(Vec(parameter.laneNumber, UInt(parameter.laneParam.maskGroupSizeBits.W)))
  val laneMaskSewSelect: Vec[UInt]                         = Input(Vec(parameter.laneNumber, UInt(2.W)))
  val v0UpdateVec:       Vec[ValidIO[V0Update]]            = Flipped(
    Vec(parameter.laneNumber, Valid(new V0Update(parameter.laneParam.datapathWidth, parameter.laneParam.vrfOffsetBits)))
  )
  val writeRDData:       UInt                              = Output(UInt(parameter.xLen.W))
  val gatherData:        DecoupledIO[UInt]                 = Decoupled(UInt(parameter.xLen.W))
  val gatherRead:        Bool                              = Input(Bool())
  val om:                Property[ClassType]               = Output(Property[AnyClassType]())
}

@instantiable
class MaskUnitOM(parameter: T1Parameter) extends GeneralOM[T1Parameter, MaskUnit](parameter) {
  val reduceUnit   = IO(Output(Property[AnyClassType]()))
  @public
  val reduceUnitIn = IO(Input(Property[AnyClassType]()))
  reduceUnit := reduceUnitIn

  val compress   = IO(Output(Property[AnyClassType]()))
  @public
  val compressIn = IO(Input(Property[AnyClassType]()))
  compress := compressIn
}

// TODO: no T1Parameter here.
@instantiable
class MaskUnit(val parameter: T1Parameter)
    extends FixedIORawModule(new MaskUnitInterface(parameter))
    with SerializableModule[T1Parameter]
    with ImplicitClock
    with ImplicitReset {

  val omInstance: Instance[MaskUnitOM] = Instantiate(new MaskUnitOM(parameter))
  io.om := omInstance.getPropertyReference

  /** Method that should point to the user-defined Clock */
  override protected def implicitClock: Clock = io.clock

  /** Method that should point to the user-defined Reset */
  override protected def implicitReset: Reset = io.reset

  val instReq           = io.instReq
  val exeReq            = io.exeReq
  val exeResp           = io.exeResp
  val tokenIO           = io.tokenIO
  val readChannel       = io.readChannel
  val readResult        = io.readResult
  val writeRD           = io.writeRD
  val lastReport        = io.lastReport
  val laneMaskInput     = io.laneMaskInput
  val laneMaskSelect    = io.laneMaskSelect
  val laneMaskSewSelect = io.laneMaskSewSelect
  val v0UpdateVec       = io.v0UpdateVec
  val writeRDData       = io.writeRDData
  val gatherData        = io.gatherData
  val gatherRead        = io.gatherRead

  // todo: param
  val readQueueSize:          Int = 4
  val readVRFLatency:         Int = 3
  val maskUnitWriteQueueSize: Int = 8

  /** duplicate v0 for mask */
  val v0: Vec[UInt] = RegInit(
    VecInit(Seq.fill(parameter.vLen / parameter.datapathWidth)(0.U(parameter.datapathWidth.W)))
  )

  // write v0(mask)
  v0.zipWithIndex.foreach { case (data, index) =>
    // 属于哪个lane
    val laneIndex: Int = index % parameter.laneNumber
    // 取出写的端口
    val v0Write = v0UpdateVec(laneIndex)
    // offset
    val offset: Int = index / parameter.laneNumber
    val maskExt = FillInterleaved(8, v0Write.bits.mask)
    when(v0Write.valid && v0Write.bits.offset === offset.U) {
      data := (data & (~maskExt).asUInt) | (maskExt & v0Write.bits.data)
    }
  }

  // mask update & select
  // lane
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
  laneMaskInput.zipWithIndex.foreach { case (input, index) =>
    val v0ForThisLane: Seq[UInt] = regroupV0.map(rv => cutUInt(rv, parameter.vLen / parameter.laneNumber)(index))
    val v0SelectBySew = Mux1H(UIntToOH(laneMaskSewSelect(index))(2, 0), v0ForThisLane)
    input := cutUInt(v0SelectBySew, parameter.datapathWidth)(laneMaskSelect(index))
  }

  val maskedWrite: BitLevelMaskWrite = Module(new BitLevelMaskWrite(parameter))

  def gatherIndex(elementIndex: UInt, vlmul: UInt, sew: UInt): (UInt, UInt, UInt, UInt, Bool) = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionSize = parameter.laneParam.vlMaxBits - 1
    val dataPosition = (changeUIntSize(elementIndex, positionSize) << sew).asUInt(positionSize - 1, 0)
    val sewOHInput   = UIntToOH(sew)(2, 0)

    // The offset of the data starting position in 32 bits (currently only 32).
    // Since the data may cross lanes, it will be optimized during fusion.
    val dataOffset: UInt = (dataPosition(1) && sewOHInput(1, 0).orR) ## (dataPosition(0) && sewOHInput(0))
    val accessLane = if (parameter.laneNumber > 1) dataPosition(log2Ceil(parameter.laneNumber) + 1, 2) else 0.U(1.W)
    // 32 bit / group
    val dataGroup  = (dataPosition >> (log2Ceil(parameter.laneNumber) + 2)).asUInt
    val offsetWidth: Int = parameter.laneParam.vrfParam.vrfOffsetBits
    val offset            = dataGroup(offsetWidth - 1, 0)
    val accessRegGrowth   = (dataGroup >> offsetWidth).asUInt
    val decimalProportion = offset ## accessLane
    // 1/8 register
    val decimal           = decimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

    /** elementIndex needs to be compared with vlMax(vLen * lmul /sew) This calculation is too complicated We can change
      * the angle. Calculate the increment of the read register and compare it with lmul to know whether the index
      * exceeds vlMax. vlmul needs to distinguish between integers and floating points
      */
    val overlap     =
      (vlmul(2) && decimal >= intLMULInput(3, 1)) ||
        (!vlmul(2) && accessRegGrowth >= intLMULInput) ||
        (elementIndex >> log2Ceil(parameter.vLen)).asUInt.orR
    val notNeedRead = overlap
    val reallyGrowth: UInt = changeUIntSize(accessRegGrowth, 3)
    (dataOffset, accessLane, offset, reallyGrowth, notNeedRead)
  }
  val (dataOffset, accessLane, offset, reallyGrowth, notNeedRead) =
    gatherIndex(instReq.bits.readFromScala, instReq.bits.vlmul, instReq.bits.sew)
  val idle :: sRead :: wRead :: sResponse :: Nil = Enum(4)
  val gatherReadState:   UInt = RegInit(idle)
  val gatherRequestFire: Bool = Wire(Bool())
  val gatherSRead:       Bool = gatherReadState === sRead
  val gatherWaiteRead:   Bool = gatherReadState === wRead
  val gatherResponse:    Bool = gatherReadState === sResponse
  val gatherDatOffset:   UInt = RegEnable(dataOffset, 0.U, gatherRequestFire)
  val gatherLane:        UInt = RegEnable(accessLane, 0.U, gatherRequestFire)
  val gatherOffset:      UInt = RegEnable(offset, 0.U, gatherRequestFire)
  val gatherGrowth:      UInt = RegEnable(reallyGrowth, 0.U, gatherRequestFire)

  val instReg:     MaskUnitInstReq = RegEnable(instReq.bits, 0.U.asTypeOf(instReq.bits), instReq.valid)
  val enqMvRD:     Bool            = instReq.bits.decodeResult(Decoder.topUop) === BitPat("b01011")
  val instVlValid: Bool            =
    RegEnable((instReq.bits.vl.orR || enqMvRD) && instReq.valid, false.B, instReq.valid || lastReport.orR)
  gatherRequestFire := gatherReadState === idle && gatherRead && !instVlValid
  // viota mask read vs2. Also pretending to be reading vs1
  val viotaReq:   Bool            = instReq.bits.decodeResult(Decoder.topUop) === "b01000".U
  when(instReq.valid && (viotaReq || enqMvRD) || gatherRequestFire) {
    instReg.vs1              := instReq.bits.vs2
    instReg.instructionIndex := instReq.bits.instructionIndex
  }
  // register for read vs1
  val readVS1Reg: MaskUnitReadVs1 = RegInit(0.U.asTypeOf(new MaskUnitReadVs1(parameter)))
  val sew1H:      UInt            = UIntToOH(instReg.sew)(2, 0)
  // request for read vs1
  val readVS1Req: MaskUnitReadReq = WireDefault(0.U.asTypeOf(new MaskUnitReadReq(parameter)))

  when(instReq.valid || gatherRequestFire) {
    readVS1Reg.requestSend     := false.B
    readVS1Reg.dataValid       := false.B
    readVS1Reg.sendToExecution := false.B
    readVS1Reg.readIndex       := 0.U
  }

  // from decode
  val unitType:            UInt = UIntToOH(instReg.decodeResult(Decoder.topUop)(4, 3))
  val subType:             UInt = UIntToOH(instReg.decodeResult(Decoder.topUop)(2, 1))
  val readType:            Bool = unitType(0)
  val gather16:            Bool = instReg.decodeResult(Decoder.topUop) === "b00101".U
  val maskDestinationType: Bool = instReg.decodeResult(Decoder.topUop) === "b11000".U
  val compress:            Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b0100?")
  val viota:               Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b01000")
  val mv:                  Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b0101?")
  val mvRd:                Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b01011")
  val mvVd:                Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b01010")
  val orderReduce:         Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b101?1")
  val ffo:                 Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b0111?")
  val extendType:          Bool = unitType(3) && (subType(2) || subType(1))
  val pop:                 Bool = instReg.decodeResult(Decoder.popCount)

  // Instructions for writing vd without source
  val noSource: Bool = mv || viota

  val allGroupExecute: Bool = maskDestinationType || unitType(2) || compress || ffo
  val useDefaultSew:   Bool = unitType(0) && !gather16
  // todo: decode ?
  // Indicates how many times a set of data will be executed
  // 0 -> 4 times
  // 1 -> 2 times
  // 3 -> 1 times
  val dataSplitSew:    UInt = Mux1H(
    Seq(
      useDefaultSew                           -> instReg.sew,
      // extend
      (unitType(3) && subType(2))             -> 0.U,
      (unitType(3) && subType(1) || gather16) -> 1.U,
      allGroupExecute                         -> 2.U
    )
  )

  // Indicates that an element will use the width of the original data
  val sourceDataUseDefaultSew: Bool = !(unitType(3) || gather16)
  val sourceDataEEW:           UInt = Mux1H(
    Seq(
      sourceDataUseDefaultSew -> instReg.sew,
      // extend
      unitType(3)             -> (instReg.sew >> subType(2, 1)).asUInt,
      gather16                -> 1.U
    )
  )

  // ExecuteIndex is only related to how many times it will be executed, so use [dataSplitSew]
  val lastExecuteIndex: UInt = Mux1H(UIntToOH(dataSplitSew), Seq(3.U(2.W), 2.U(2.W), 0.U(2.W)))

  // calculate last group
  val sourceDataEEW1H:  UInt = UIntToOH(sourceDataEEW)(2, 0)
  val lastElementIndex: UInt = (instReg.vl - instReg.vl.orR)(parameter.laneParam.vlMaxBits - 2, 0)

  val maskFormatSource: Bool = ffo || maskDestinationType

  // When one row is not enough, should we prioritize filling one lane?
  val prioritizeLane: Bool = ffo

  // Seq(1, 2, 4) => element byte size
  val processingVl: Seq[(UInt, UInt)] = Seq(1, 2, 4).map { eByte =>
    val eByteLog      = log2Ceil(eByte)
    val lastByteIndex = (lastElementIndex << eByteLog).asUInt
    // The width of a row of data
    val rowWidth      = parameter.datapathWidth * parameter.laneNumber / 8
    val rowWidthLog:        Int  = log2Ceil(rowWidth)
    val lastGroupRemaining: UInt = changeUIntSize(lastByteIndex, rowWidthLog)
    // get last group index
    val lastRowIndex = (lastByteIndex >> rowWidthLog).asUInt

    // for last group remainder lastGroupRemaining
    val laneDatalog       = log2Ceil(parameter.datapathWidth)
    val lastLaneIndex     = (lastGroupRemaining >> laneDatalog).asUInt
    val lastGroupDataNeed = scanRightOr(UIntToOH(lastLaneIndex))
    (lastRowIndex, lastGroupDataNeed)
  }

  // mask format source, 1 bit/element
  val processingMaskVl: Seq[(UInt, UInt)] = Seq(1).map { eBit =>
    val lastBitIndex = lastElementIndex
    // The width of a row of data
    val rowWidth     = parameter.datapathWidth * parameter.laneNumber
    val rowWidthLog:        Int  = log2Ceil(rowWidth)
    val lastGroupRemaining: UInt = changeUIntSize(lastBitIndex, rowWidthLog)
    val lastGroupMisAlign:  Bool = lastGroupRemaining.orR
    // get last group index
    val lastRowIndex = (lastBitIndex >> rowWidthLog).asUInt

    // for prioritizeLane
    // for last group remainder lastGroupRemaining
    val laneDatalog   = log2Ceil(parameter.datapathWidth)
    val lastLaneIndex = (lastGroupRemaining >> laneDatalog).asUInt -
      !changeUIntSize(lastGroupRemaining, laneDatalog).orR
    val dataNeedForPL = scanRightOr(UIntToOH(lastLaneIndex))

    // for !prioritizeLane
    // Seq(4, 2, 1) => If it is in normal form, one datapath corresponds to several elements
    val dataNeedForNPL    = Mux1H(
      sew1H,
      Seq(4, 2, 1).map { eSize =>
        val eSizeLog     = log2Ceil(eSize)
        val misAlign     = if (eSizeLog > 0) changeUIntSize(lastGroupRemaining, eSizeLog).orR else false.B
        // How many datapaths will there be?
        val datapathSize = (lastGroupRemaining >> eSizeLog).asUInt +& misAlign

        val laneNumLog    = log2Ceil(parameter.laneNumber)
        // More than one group
        val allNeed       = (datapathSize >> laneNumLog).asUInt.orR
        val lastLaneIndex = changeUIntSize(datapathSize, laneNumLog)
        val dataNeed: UInt = (~scanLeftOr(UIntToOH(lastLaneIndex))).asUInt | Fill(parameter.laneNumber, allNeed)
        dataNeed
      }
    )
    val lastGroupDataNeed = Mux(prioritizeLane, dataNeedForPL, dataNeedForNPL)
    (lastRowIndex, lastGroupDataNeed)
  }

  val reduceLastDataNeed: UInt = Mux1H(
    sew1H,
    Seq(1, 2, 4).map { eByte =>
      val eLog        = log2Ceil(eByte)
      // byte size per row
      val rowByteSize = parameter.datapathWidth * parameter.laneNumber / 8
      // byte size for vl
      val byteForVl   = (instReg.vl << eLog).asUInt

      val vlMSB: Bool = (byteForVl >> log2Ceil(rowByteSize)).asUInt.orR
      // Unaligned row parts
      val vlLSB: UInt = changeUIntSize(instReg.vl, log2Ceil(rowByteSize))

      val dLog     = log2Ceil(parameter.datapathWidth / 8)
      // How many datapaths does LSB contain?
      val lsbDSize = (vlLSB >> dLog).asUInt - !changeUIntSize(vlLSB, dLog).orR
      scanRightOr(UIntToOH(lsbDSize)) | Fill(parameter.laneNumber, vlMSB)
    }
  )

  val dataSourceSew:   UInt = Mux(
    unitType(3),
    instReg.sew - instReg.decodeResult(Decoder.topUop)(2, 1),
    Mux(gather16, 1.U, instReg.sew)
  )
  val dataSourceSew1H: UInt = UIntToOH(dataSourceSew)(2, 0)

  val unorderReduce:           Bool = !orderReduce && unitType(2)
  val normalFormat:            Bool = !maskFormatSource && !unorderReduce && !mv
  val lastGroupForInstruction: UInt = Mux1H(
    Seq(
      (unorderReduce || mv)                -> 0.U,
      maskFormatSource                     -> processingMaskVl.head._1,
      (normalFormat && dataSourceSew1H(0)) -> processingVl.head._1,
      (normalFormat && dataSourceSew1H(1)) -> processingVl(1)._1,
      (normalFormat && dataSourceSew1H(2)) -> processingVl(2)._1
    )
  )

  val popDataNeed:       UInt = {
    val dataPathBit   = log2Ceil(parameter.datapathWidth)
    val lastLaneIndex = (lastElementIndex >> dataPathBit).asUInt
    scanRightOr(UIntToOH(lastLaneIndex))
  }
  val lastGroupDataNeed: UInt = Mux1H(
    Seq(
      (unorderReduce && pop)               -> popDataNeed,
      (unorderReduce && !pop)              -> reduceLastDataNeed,
      maskFormatSource                     -> processingMaskVl.head._2,
      (normalFormat && dataSourceSew1H(0)) -> processingVl.head._2,
      (normalFormat && dataSourceSew1H(1)) -> processingVl(1)._2,
      (normalFormat && dataSourceSew1H(2)) -> processingVl(2)._2
    )
  )

  val groupSizeForMaskDestination:   Int  = parameter.laneNumber * parameter.datapathWidth
  val elementTailForMaskDestination: UInt = lastElementIndex(log2Ceil(groupSizeForMaskDestination) - 1, 0)

  val exeRequestQueue: Seq[QueueIO[MaskUnitExeReq]] = exeReq.zipWithIndex.map { case (req, index) =>
    // todo: max or token?
    val queue: QueueIO[MaskUnitExeReq] =
      Queue.io(chiselTypeOf(req.bits), parameter.laneParam.maskRequestQueueSize, flow = true)
    tokenIO(index).maskRequestRelease := queue.deq.fire
    queue.enq.valid                   := req.valid
    queue.enq.bits                    := req.bits
    queue
  }

  val exeReqReg:           Seq[ValidIO[MaskUnitExeReq]] = Seq.tabulate(parameter.laneNumber) { _ =>
    RegInit(0.U.asTypeOf(Valid(new MaskUnitExeReq(parameter.laneParam))))
  }
  val requestCounter:      UInt                         = RegInit(0.U(parameter.laneParam.groupNumberBits.W))
  val executeGroupCounter: UInt                         = Wire(UInt(parameter.laneParam.groupNumberBits.W))

  val counterValid:    Bool          = requestCounter <= lastGroupForInstruction
  val lastGroup:       Bool          =
    requestCounter === lastGroupForInstruction || (!orderReduce && unitType(2)) || mv
  val slideAddressGen: SlideIndexGen = Module(new SlideIndexGen(parameter))
  slideAddressGen.newInstruction := instReq.valid & instReq.bits.vl.orR
  slideAddressGen.instructionReq := instReg
  slideAddressGen.slideMaskInput := cutUInt(v0.asUInt, parameter.laneNumber)(slideAddressGen.slideGroupOut)

  // change data group from lane
  val lastExecuteGroupDeq: Bool = Wire(Bool())
  val viotaCounterAdd:     Bool = Wire(Bool())
  val groupCounterAdd:     Bool = Mux(noSource, viotaCounterAdd, lastExecuteGroupDeq)
  when(instReq.valid || groupCounterAdd) {
    requestCounter := Mux(instReq.valid, 0.U, requestCounter + 1.U)
  }

  // todo: mask
  val groupDataNeed:       UInt              = Mux(lastGroup, lastGroupDataNeed, (-1.S(parameter.laneNumber.W)).asUInt)
  // For read type, only sew * laneNumber data will be consumed each time
  // There will be a maximum of (dataPath * laneNumber) / (sew * laneNumber) times
  val executeIndex:        UInt              = RegInit(0.U(2.W))
  // The status of an execution
  // Each execution ends with executeIndex + 1
  val readIssueStageState: MaskUnitReadState = RegInit(0.U.asTypeOf(new MaskUnitReadState(parameter)))
  val readIssueStageValid: Bool              = RegInit(false.B)

  def indexAnalysis(sewInt: Int)(elementIndex: UInt, vlmul: UInt, valid: Option[Bool] = None): Seq[UInt] = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionSize = parameter.laneParam.vlMaxBits - 1
    val dataPosition = (changeUIntSize(elementIndex, positionSize) << sewInt).asUInt(positionSize - 1, 0)
    val accessMask: UInt = Seq(
      UIntToOH(dataPosition(1, 0)),
      FillInterleaved(2, UIntToOH(dataPosition(1))),
      15.U(4.W)
    )(sewInt)
    // The offset of the data starting position in 32 bits (currently only 32).
    // Since the data may cross lanes, it will be optimized during fusion.
    // (dataPosition(1) && sewOHInput(1, 0).orR) ## (dataPosition(0) && sewOHInput(0))
    val dataOffset: UInt =
      (if (sewInt < 2) dataPosition(1) else false.B) ##
        (if (sewInt == 0) dataPosition(0) else false.B)
    val accessLane = if (parameter.laneNumber > 1) dataPosition(log2Ceil(parameter.laneNumber) + 1, 2) else 0.U(1.W)
    // 32 bit / group
    val dataGroup  = (dataPosition >> (log2Ceil(parameter.laneNumber) + 2)).asUInt
    val offsetWidth: Int = parameter.laneParam.vrfParam.vrfOffsetBits
    val offset            = dataGroup(offsetWidth - 1, 0)
    val accessRegGrowth   = (dataGroup >> offsetWidth).asUInt
    val decimalProportion = offset ## accessLane
    // 1/8 register
    val decimal           = decimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

    /** elementIndex needs to be compared with vlMax(vLen * lmul /sew) This calculation is too complicated We can change
      * the angle. Calculate the increment of the read register and compare it with lmul to know whether the index
      * exceeds vlMax. vlmul needs to distinguish between integers and floating points
      */
    val overlap      =
      (vlmul(2) && decimal >= intLMULInput(3, 1)) ||
        (!vlmul(2) && accessRegGrowth >= intLMULInput) ||
        (elementIndex >> log2Ceil(parameter.vLen)).asUInt.orR
    val elementValid = valid.getOrElse(true.B)
    val notNeedRead  = overlap || !elementValid
    val reallyGrowth: UInt = changeUIntSize(accessRegGrowth, 3)
    Seq(accessMask, dataOffset, accessLane, offset, reallyGrowth, notNeedRead, elementValid)
  }

  // datapath bit per mask group
  // laneNumber bit per execute group
  val executeGroup: UInt = Mux1H(
    UIntToOH(dataSplitSew)(2, 0),
    Seq(
      requestCounter ## executeIndex,
      requestCounter ## executeIndex(1),
      requestCounter
    )
  )

  // read vl boundary
  val executeSizeBit: Int = log2Ceil(parameter.laneNumber)
  val vlMisAlign = instReg.vl(executeSizeBit - 1, 0).orR
  val lastexecuteGroup:     UInt = (instReg.vl >> executeSizeBit).asUInt - !vlMisAlign
  val isVlBoundary:         Bool = executeGroup === lastexecuteGroup
  val validExecuteGroup:    Bool = executeGroup <= lastexecuteGroup
  val vlBoundaryCorrection: UInt = Mux(
    vlMisAlign && isVlBoundary,
    (~scanLeftOr(UIntToOH(instReg.vl(executeSizeBit - 1, 0)))).asUInt,
    -1.S(parameter.laneNumber.W).asUInt
  ) & Fill(parameter.laneNumber, validExecuteGroup)

  // handle mask
  val selectReadStageMask: UInt = cutUInt(v0.asUInt, parameter.laneNumber)(executeGroup)
  val readMaskCorrection:  UInt =
    Mux(instReg.maskType, selectReadStageMask, -1.S(parameter.laneNumber.W).asUInt) &
      vlBoundaryCorrection

  // write mask for normal execute
  val maskSplit = Seq(0, 1, 2).map { sewInt =>
    // byte / element
    val dataByte = 1 << sewInt
    val rowElementSize: Int = parameter.laneNumber * parameter.datapathWidth / dataByte / 8
    val maskSelect = cutUInt(v0.asUInt, rowElementSize)(executeGroupCounter)

    val executeSizeBit: Int = log2Ceil(rowElementSize)
    val vlMisAlign = instReg.vl(executeSizeBit - 1, 0).orR
    val lastexecuteGroup:     UInt = (instReg.vl >> executeSizeBit).asUInt - !vlMisAlign
    val isVlBoundary:         Bool = executeGroupCounter === lastexecuteGroup
    val validExecuteGroup:    Bool = executeGroupCounter <= lastexecuteGroup
    val vlBoundaryCorrection: UInt = maskEnable(
      vlMisAlign && isVlBoundary,
      (~scanLeftOr(UIntToOH(instReg.vl(executeSizeBit - 1, 0)))).asUInt
    ) & Fill(rowElementSize, validExecuteGroup)
    val elementMask = maskEnable(instReg.maskType, maskSelect) & vlBoundaryCorrection
    val byteMask    = FillInterleaved(dataByte, elementMask)
    (byteMask, elementMask)
  }
  val executeByteMask: UInt = Mux1H(sew1H, maskSplit.map(_._1))
  val executeElementMask: UInt = Mux1H(sew1H, maskSplit.map(_._2))

  // mask for destination
  val maskForDestination:             UInt = cutUInt(v0.asUInt, groupSizeForMaskDestination)(requestCounter)
  val lastGroupMask:                  UInt = scanRightOr(UIntToOH(elementTailForMaskDestination))
  val currentMaskGroupForDestination: UInt = maskEnable(lastGroup, lastGroupMask) &
    maskEnable(instReg.maskType && !instReg.decodeResult(Decoder.maskSource), maskForDestination)

  // select source & valid
  val minSourceSize: Int = 8 * parameter.laneNumber
  val minValidSize = parameter.laneNumber
  val groupSourceData:  UInt = VecInit(exeReqReg.map(_.bits.source1)).asUInt
  val groupSourceValid: UInt = VecInit(exeReqReg.map(_.valid)).asUInt
  val shifterSize:      UInt = Wire(UInt(2.W))
  shifterSize := Mux1H(
    sourceDataEEW1H(1, 0),
    Seq(
      executeIndex,
      executeIndex(1) ## false.B
    )
  )
  val shifterSource: UInt      = Mux1H(
    UIntToOH(shifterSize),
    Seq(
      groupSourceData,
      (groupSourceData >> minSourceSize).asUInt,
      (groupSourceData >> (minSourceSize * 2)).asUInt,
      (groupSourceData >> (minSourceSize * 3)).asUInt
    )
  )
  val selectValid:   UInt      = Mux1H(
    sourceDataEEW1H,
    Seq(
      cutUIntBySize(FillInterleaved(4, groupSourceValid), 4)(executeIndex),
      cutUIntBySize(FillInterleaved(2, groupSourceValid), 2)(executeIndex(1)),
      groupSourceValid
    )
  )
  val source:        Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
  source.zipWithIndex.foreach { case (d, i) =>
    d := Mux1H(
      sourceDataEEW1H,
      Seq(
        cutUInt(shifterSource, 8)(i),
        cutUInt(shifterSource, 16)(i),
        cutUInt(shifterSource, 32)(i)
      )
    )
  }

  val checkVec:           Seq[Seq[UInt]] = Seq(0, 1, 2).map { sewInt =>
    val validVec = selectValid & readMaskCorrection
    // read index check
    // (accessMask, dataOffset, accessLane, offset, reallyGrowth, overlap)
    val checkResultVec: Seq[Seq[UInt]] = source.zipWithIndex.map { case (s, i) =>
      indexAnalysis(sewInt)(s, instReg.vlmul, Some(validVec(i)))
    }
    val checkResult = checkResultVec.transpose.map(a => VecInit(a).asUInt)
    checkResult
  }
  val sewCorrection1H:    UInt           = sew1H
  val dataOffsetSelect:   UInt           = Mux1H(sewCorrection1H, checkVec.map(_(1)))
  val accessLaneSelect:   UInt           = Mux1H(sewCorrection1H, checkVec.map(_(2)))
  val offsetSelect:       UInt           = Mux1H(sewCorrection1H, checkVec.map(_(3)))
  val growthSelect:       UInt           = Mux1H(sewCorrection1H, checkVec.map(_(4)))
  val notReadSelect:      UInt           = Mux1H(sewCorrection1H, checkVec.map(_(5)))
  val elementValidSelect: UInt           = Mux1H(sewCorrection1H, checkVec.map(_(6)))

  val readCrossBar: MaskUnitReadCrossBar = Module(new MaskUnitReadCrossBar(parameter))

  // read data queue deq release
  val readTokenRelease: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))

  // todo: param
  val readDataQueueSize: Int = 8

  // The queue waiting to read data. This queue contains other information about this group.
  // 64: todo: max or token?
  val readWaitQueue: QueueIO[MaskUnitWaitReadQueue] = Queue.io(new MaskUnitWaitReadQueue(parameter), 64)

  // s0 pipe request from lane
  exeRequestQueue.zip(exeReqReg).foreach { case (req, reg) =>
    req.deq.ready := !reg.valid || lastExecuteGroupDeq || viota
    when(req.deq.fire) {
      reg.bits := req.deq.bits
    }
    when(req.deq.fire ^ lastExecuteGroupDeq) {
      reg.valid := req.deq.fire && !viota
    }
  }

  val isLastExecuteGroup: Bool = executeIndex === lastExecuteIndex
  val allDataValid:       Bool = exeReqReg.zipWithIndex.map { case (d, i) => d.valid || !groupDataNeed(i) }.reduce(_ && _)
  val anyDataValid:       Bool = exeReqReg.zipWithIndex.map { case (d, i) => d.valid }.reduce(_ || _)

  // try to read vs1
  val readVs1Valid: Bool =
    (unitType(2) || compress || mvRd) && !readVS1Reg.requestSend || gatherSRead
  readVS1Req.vs := instReg.vs1
  when(compress) {
    val logLaneNumber = log2Ceil(parameter.laneNumber)
    readVS1Req.vs       := instReg.vs1 + (readVS1Reg.readIndex >> (parameter.laneParam.vrfOffsetBits + logLaneNumber))
    readVS1Req.offset   := readVS1Reg.readIndex >> logLaneNumber
    readVS1Req.readLane := changeUIntSize(readVS1Reg.readIndex, logLaneNumber)
  }.elsewhen(gatherSRead) {
    readVS1Req.vs         := instReg.vs1 + gatherGrowth
    readVS1Req.offset     := gatherOffset
    readVS1Req.readLane   := gatherLane
    readVS1Req.dataOffset := gatherDatOffset
  }

  // select execute group
  val pipeReadFire:     Vec[Bool]                     = Wire(Vec(parameter.laneNumber, Bool()))
  val selectExecuteReq: Seq[ValidIO[MaskUnitReadReq]] = exeReqReg.zipWithIndex.map { case (_, index) =>
    val res: ValidIO[MaskUnitReadReq] = WireInit(0.U.asTypeOf(Valid(new MaskUnitReadReq(parameter))))
    res.bits.vs           := instReg.vs2 + readIssueStageState.vsGrowth(index)
    if (parameter.laneParam.vrfOffsetBits > 0) {
      res.bits.offset := cutUIntBySize(readIssueStageState.readOffset, parameter.laneNumber)(index)
    }
    res.bits.readLane     := readIssueStageState.accessLane(index)
    res.bits.dataOffset   := cutUIntBySize(readIssueStageState.readDataOffset, parameter.laneNumber)(index)
    res.bits.requestIndex := index.U
    res.valid             := readIssueStageValid && !readIssueStageState.groupReadState(index) &&
      readIssueStageState.needRead(index) && unitType(0)
    if (index == 0) {
      when(readVs1Valid) {
        res.valid := true.B
        res.bits  := readVS1Req
      }
      pipeReadFire(index) := !readVs1Valid && readCrossBar.input(index).fire
    } else {
      pipeReadFire(index) := readCrossBar.input(index).fire
    }
    res
  }

  when(readCrossBar.input.head.fire) {
    readVS1Reg.requestSend := true.B
  }

  // read arbitration
  readCrossBar.input.zip(selectExecuteReq).zipWithIndex.foreach { case ((cross, req), index) =>
    // read token
    val tokenCheck: Bool = pipeToken(readDataQueueSize)(cross.fire, readTokenRelease(index))
    cross.valid := req.valid && tokenCheck
    cross.bits  := req.bits
  }

  // read control register update
  val readFire:           UInt = pipeReadFire.asUInt
  val anyReadFire:        Bool = readFire.orR
  val readStateUpdate:    UInt = readFire | readIssueStageState.groupReadState
  val groupReadFinish:    Bool = readStateUpdate === readIssueStageState.needRead
  val readTypeRequestDeq: Bool =
    (anyReadFire && groupReadFinish) || (readIssueStageValid && readIssueStageState.needRead === 0.U)

  val noSourceValid:       Bool = noSource && counterValid &&
    (instReg.vl.orR || (mvRd && !readVS1Reg.sendToExecution))
  val vs1DataValid:        Bool = readVS1Reg.dataValid || !(unitType(2) || compress || mvRd)
  val executeReady:        Bool = Wire(Bool())
  val executeDeqReady:     Bool = VecInit(maskedWrite.in.map(_.ready)).asUInt.andR
  val otherTypeRequestDeq: Bool =
    Mux(noSource, noSourceValid, allDataValid) &&
      vs1DataValid && instVlValid && executeDeqReady
  val readIssueStageEnq:   Bool =
    (allDataValid || slideAddressGen.indexDeq.valid) &&
      (readTypeRequestDeq || !readIssueStageValid) && instVlValid && readType
  val requestStageDeq:     Bool = Mux(readType, readIssueStageEnq, otherTypeRequestDeq && executeReady)
  slideAddressGen.indexDeq.ready := readTypeRequestDeq || !readIssueStageValid
  when(anyReadFire) {
    readIssueStageState.groupReadState := readStateUpdate
  }

  when(readTypeRequestDeq ^ readIssueStageEnq) {
    readIssueStageValid := readIssueStageEnq
  }

  val executeIndexGrowth: UInt = (1.U << dataSplitSew).asUInt
  when(requestStageDeq && anyDataValid) {
    executeIndex := executeIndex + executeIndexGrowth
  }
  when(readIssueStageEnq) {
    readIssueStageState.groupReadState := 0.U
    readIssueStageState.needRead       := (~notReadSelect).asUInt
    readIssueStageState.elementValid   := elementValidSelect
    readIssueStageState.replaceVs1     := 0.U
    readIssueStageState.accessLane     := cutUIntBySize(accessLaneSelect, parameter.laneNumber)
    readIssueStageState.vsGrowth       := cutUIntBySize(growthSelect, parameter.laneNumber)
    readIssueStageState.readOffset     := offsetSelect
    readIssueStageState.executeGroup   := executeGroup
    readIssueStageState.readDataOffset := dataOffsetSelect
    readIssueStageState.last           := isVlBoundary
    when(slideAddressGen.indexDeq.fire) {
      readIssueStageState := slideAddressGen.indexDeq.bits
    }
  }

  readWaitQueue.enq.valid             := readTypeRequestDeq
  readWaitQueue.enq.bits.executeGroup := readIssueStageState.executeGroup
  readWaitQueue.enq.bits.sourceValid  := readIssueStageState.elementValid
  readWaitQueue.enq.bits.replaceVs1   := readIssueStageState.replaceVs1
  readWaitQueue.enq.bits.needRead     := readIssueStageState.needRead
  readWaitQueue.enq.bits.last         := readIssueStageState.last

  // last execute group in this request group dequeue
  lastExecuteGroupDeq := requestStageDeq && isLastExecuteGroup

  // s1 read vrf
  val write1HPipe:    Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.laneNumber.W)))
  val pipeDataOffset: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(log2Ceil(parameter.datapathWidth / 8).W)))

  readCrossBar.output.zipWithIndex.foreach { case (request, index) =>
    val readMessageQueue: QueueIO[MaskUnitReadPipe] =
      Queue.io(new MaskUnitReadPipe(parameter), readVRFLatency + 4)
    val sourceLane = UIntToOH(request.bits.writeIndex)
    readChannel(index).valid                 := request.valid && readMessageQueue.enq.ready
    readChannel(index).bits.readSource       := 2.U
    readChannel(index).bits.vs               := request.bits.vs
    readChannel(index).bits.offset           := request.bits.offset
    readChannel(index).bits.instructionIndex := instReg.instructionIndex
    request.ready                            := readChannel(index).ready && readMessageQueue.enq.ready

    maskedWrite.readChannel(index).ready := readChannel(index).ready
    maskedWrite.readResult(index)        := readResult(index)
    when(maskDestinationType) {
      readChannel(index).valid       := maskedWrite.readChannel(index).valid
      readChannel(index).bits.vs     := maskedWrite.readChannel(index).bits.vs
      readChannel(index).bits.offset := maskedWrite.readChannel(index).bits.offset
    }

    readMessageQueue.enq.valid           := readChannel(index).fire && !maskDestinationType
    readMessageQueue.enq.bits.readSource := sourceLane
    readMessageQueue.enq.bits.dataOffset := request.bits.dataOffset
    readMessageQueue.deq.ready           := readResult(index).valid

    write1HPipe(index)    := Mux(
      readMessageQueue.deq.valid && readResult(index).valid,
      readMessageQueue.deq.bits.readSource,
      0.U(parameter.laneNumber.W)
    )
    pipeDataOffset(index) := readMessageQueue.deq.bits.dataOffset
  }

  // Processing read results
  val readData: Seq[DecoupledIO[UInt]] = Seq.tabulate(parameter.laneNumber) { index =>
    val readDataQueue    = Queue.io(UInt(parameter.datapathWidth.W), readDataQueueSize, flow = true)
    val readResultSelect = VecInit(write1HPipe.map(_(index))).asUInt
    val dataOffset: UInt = Mux1H(readResultSelect, pipeDataOffset)
    readTokenRelease(index) := readDataQueue.deq.fire
    readDataQueue.enq.valid := readResultSelect.orR
    readDataQueue.enq.bits  := Mux1H(readResultSelect, readResult.map(_.bits)) >> (dataOffset ## 0.U(3.W))
    readDataQueue.deq
  }

  /** todo: [[waiteReadDataPipeReg]] enq && [[readWaitQueue]] enq * */
  // reg before execute
  val waiteReadDataPipeReg: MaskUnitWaitReadQueue = RegInit(0.U.asTypeOf(new MaskUnitWaitReadQueue(parameter)))
  val waiteReadData:        Seq[UInt]             = Seq.tabulate(parameter.laneNumber) { _ => RegInit(0.U(parameter.datapathWidth.W)) }
  val waiteReadSate:        UInt                  = RegInit(0.U(parameter.laneNumber.W))
  val waiteReadStageValid:  Bool                  = RegInit(false.B)

  // Process the data that needs to be written
  val dlen: Int = parameter.datapathWidth * parameter.laneNumber
  // Execute at most 4 times, each index represents 1/4 of dlen
  val eachIndexSize = dlen / 4
  val executeIndexVec: Seq[UInt] = Seq(
    waiteReadDataPipeReg.executeGroup(1, 0),
    waiteReadDataPipeReg.executeGroup(0) ## false.B,
    false.B
  )
  val writeDataVec = Seq(0, 1, 2).map { sewInt =>
    val dataByte     = 1 << sewInt
    val data         = VecInit(Seq.tabulate(parameter.laneNumber) { laneIndex =>
      val dataElement: UInt = Wire(UInt((dataByte * 8).W))
      val dataIsRead = waiteReadDataPipeReg.needRead(laneIndex)
      val unreadData = Mux(waiteReadDataPipeReg.replaceVs1(laneIndex), instReg.readFromScala, 0.U)

      dataElement := Mux(dataIsRead, waiteReadData(laneIndex), unreadData)
      dataElement
    }).asUInt
    val executeIndex = executeIndexVec(sewInt)
    val shifterData  = (data << (executeIndex ## 0.U(log2Ceil(eachIndexSize).W))).asUInt
    // align
    changeUIntSize(shifterData, dlen)
  }
  val writeData    = Mux1H(sew1H, writeDataVec)

  val writeMaskVec: Seq[UInt] = Seq(0, 1, 2).map { sewInt =>
    val MaskMagnification = 1 << sewInt
    val mask              = FillInterleaved(MaskMagnification, waiteReadDataPipeReg.sourceValid)
    val executeIndex      = executeIndexVec(sewInt)
    val shifterMask       = (mask << (executeIndex ## 0.U(log2Ceil(eachIndexSize / 8).W))).asUInt
    // align
    changeUIntSize(shifterMask, dlen / 8)
  }
  val writeMask = Mux1H(sew1H, writeMaskVec)

  val writeRequest:  Seq[MaskUnitExeResponse] = Seq.tabulate(parameter.laneNumber) { laneIndex =>
    val res: MaskUnitExeResponse = Wire(new MaskUnitExeResponse(parameter.laneParam))
    res.ffoByOther             := DontCare
    res.pipeData               := DontCare
    res.index                  := instReg.instructionIndex
    res.writeData.groupCounter := (waiteReadDataPipeReg.executeGroup << instReg.sew >> 2).asUInt
    res.writeData.vd           := instReg.vd
    res.writeData.data         := cutUIntBySize(writeData, parameter.laneNumber)(laneIndex)
    res.writeData.mask         := cutUIntBySize(writeMask, parameter.laneNumber)(laneIndex)
    res
  }
  val WillWriteLane: UInt                     = VecInit(cutUIntBySize(writeMask, parameter.laneNumber).map(_.orR)).asUInt

  // update waite read stage
  val waiteStageDeqValid: Bool =
    waiteReadStageValid &&
      (waiteReadSate === waiteReadDataPipeReg.needRead || waiteReadDataPipeReg.needRead === 0.U)
  val waiteStageDeqReady: Bool = Wire(Bool())
  val waiteStageDeqFire:  Bool = waiteStageDeqValid && waiteStageDeqReady

  val waiteStageEnqReady: Bool = !waiteReadStageValid || waiteStageDeqFire
  val waiteStageEnqFire:  Bool = readWaitQueue.deq.valid && waiteStageEnqReady

  readWaitQueue.deq.ready := waiteStageEnqReady

  when(waiteStageEnqFire) {
    waiteReadDataPipeReg := readWaitQueue.deq.bits
  }

  when(waiteStageDeqFire ^ waiteStageEnqFire) {
    waiteReadStageValid := waiteStageEnqFire
  }

  waiteReadData.zipWithIndex.foreach { case (reg, index) =>
    val isWaiteForThisData = waiteReadDataPipeReg.needRead(index) && !waiteReadSate(index) && waiteReadStageValid
    val read               = readData(index)
    read.ready := isWaiteForThisData
    if (index == 0) {
      read.ready := isWaiteForThisData || unitType(2) || compress || gatherWaiteRead || mvRd
      when(read.fire) {
        readVS1Reg.data      := read.bits
        readVS1Reg.dataValid := true.B
        when(gatherWaiteRead) {
          gatherReadState := sResponse
        }
      }
    }
    when(read.fire) {
      reg := read.bits
    }
  }
  val readResultValid: UInt = VecInit(readData.map(_.fire)).asUInt
  when(waiteStageEnqFire && readResultValid.orR) {
    waiteReadSate := readResultValid
  }.elsewhen(readResultValid.orR) {
    waiteReadSate := waiteReadSate | readResultValid
  }.elsewhen(waiteStageEnqFire) {
    waiteReadSate := 0.U
  }

  // Determine whether the data is ready
  val executeEnqValid: Bool = otherTypeRequestDeq && !readType

  val compressParam: CompressParam = CompressParam(
    parameter.datapathWidth,
    parameter.xLen,
    parameter.vLen,
    parameter.laneNumber,
    parameter.laneParam.groupNumberBits,
    2
  )
  // start execute
  val compressUnit = Instantiate(new MaskCompress(compressParam))
  val reduceUnit   = Instantiate(
    new MaskReduce(
      MaskReduceParameter(parameter.datapathWidth, parameter.laneNumber, parameter.fpuEnable)
    )
  )
  omInstance.reduceUnitIn := reduceUnit.io.om.asAnyClassType
  omInstance.compressIn   := compressUnit.io.om.asAnyClassType

  val extendUnit: MaskExtend = Module(new MaskExtend(parameter))

  // todo
  val source2: UInt = VecInit(exeReqReg.map(_.bits.source2)).asUInt
  val source1: UInt = VecInit(exeReqReg.map(_.bits.source1)).asUInt

  // compress data
  // compress executes a whole set of data
  val vs1Split: Seq[(UInt, Bool)] = Seq(0, 1, 2).map { sewInt =>
    val dataByte = 1 << sewInt
    // For compress, a set of data requires vs1Size bits of vs1
    val vs1Size  = (parameter.datapathWidth / 8) * parameter.laneNumber / dataByte
    // How many sets of vs1 can a dataPath have?
    val setSize  = parameter.datapathWidth / vs1Size
    val vs1SetIndex: UInt =
      if (parameter.datapathWidth <= vs1Size) true.B
      else
        requestCounter(log2Ceil(setSize) - 1, 0)
    val selectVS1:   UInt =
      if (parameter.datapathWidth <= vs1Size) readVS1Reg.data
      else
        cutUIntBySize(readVS1Reg.data, setSize)(vs1SetIndex)
    val willChangeVS1Index = vs1SetIndex.andR
    (selectVS1, willChangeVS1Index)
  }

  val compressSource1: UInt = Mux1H(sew1H, vs1Split.map(_._1))
  val source1Select:   UInt = Mux(mv, readVS1Reg.data, compressSource1)
  val source1Change:   Bool = Mux1H(sew1H, vs1Split.map(_._2))
  when(source1Change && compressUnit.io.in.fire) {
    readVS1Reg.dataValid   := false.B
    readVS1Reg.requestSend := false.B
    readVS1Reg.readIndex   := readVS1Reg.readIndex + 1.U

  }
  viotaCounterAdd := compressUnit.io.in.fire

  compressUnit.io.clock                  := implicitClock
  compressUnit.io.reset                  := implicitReset
  compressUnit.io.in.valid               := executeEnqValid && unitType(1)
  compressUnit.io.in.bits.maskType       := instReg.maskType
  compressUnit.io.in.bits.eew            := instReg.sew
  compressUnit.io.in.bits.uop            := instReg.decodeResult(Decoder.topUop)
  compressUnit.io.in.bits.readFromScalar := instReg.readFromScala
  compressUnit.io.in.bits.source1        := source1Select
  compressUnit.io.in.bits.mask           := executeElementMask
  compressUnit.io.in.bits.source2        := source2
  compressUnit.io.in.bits.groupCounter   := requestCounter
  compressUnit.io.in.bits.lastCompress   := lastGroup
  compressUnit.io.in.bits.ffoInput       := VecInit(exeReqReg.map(_.bits.ffo)).asUInt
  compressUnit.io.in.bits.validInput     := VecInit(exeReqReg.map(_.valid)).asUInt
  compressUnit.io.newInstruction         := instReq.valid
  compressUnit.io.ffoInstruction         := instReq.bits.decodeResult(Decoder.topUop)(2, 0) === BitPat("b11?")

  reduceUnit.io.clock               := implicitClock
  reduceUnit.io.reset               := implicitReset
  reduceUnit.io.in.valid            := executeEnqValid && unitType(2)
  reduceUnit.io.in.bits.maskType    := instReg.maskType
  reduceUnit.io.in.bits.eew         := instReg.sew
  reduceUnit.io.in.bits.uop         := instReg.decodeResult(Decoder.topUop)
  reduceUnit.io.in.bits.readVS1     := readVS1Reg.data
  reduceUnit.io.in.bits.source2     := source2
  reduceUnit.io.in.bits.sourceValid := VecInit(exeReqReg.map(_.valid)).asUInt
  reduceUnit.io.in.bits.lastGroup   := lastGroup
  reduceUnit.io.in.bits.vxrm        := instReg.vxrm
  reduceUnit.io.in.bits.aluUop      := instReg.decodeResult(Decoder.uop)
  reduceUnit.io.in.bits.sign        := !instReg.decodeResult(Decoder.unsigned1)
  reduceUnit.io.firstGroup          := !readVS1Reg.sendToExecution && reduceUnit.io.in.fire
  reduceUnit.io.newInstruction      := instReq.fire
  reduceUnit.io.validInst           := instReg.vl.orR
  reduceUnit.io.pop                 := pop

  reduceUnit.io.in.bits.fpSourceValid.foreach { sink =>
    sink := VecInit(exeReqReg.map(_.bits.fpReduceValid.get)).asUInt
  }

  when(reduceUnit.io.in.fire || compressUnit.io.in.fire) {
    readVS1Reg.sendToExecution := true.B
  }

  val extendGroupCount: UInt = Mux(
    extendType,
    Mux(
      subType(2),
      requestCounter ## executeIndex,
      requestCounter ## executeIndex(1)
    ),
    requestCounter
  )
  extendUnit.in.eew          := instReg.sew
  extendUnit.in.uop          := instReg.decodeResult(Decoder.topUop)
  extendUnit.in.source2      := source2
  extendUnit.in.groupCounter := extendGroupCount

  val executeResult: UInt = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnit.io.out.data,
      reduceUnit.io.out.bits.data,
      extendUnit.out
    )
  )

  // todo
  executeReady := Mux1H(
    unitType,
    Seq(
      true.B,                                         // read type
      true.B,                                         // compress
      reduceUnit.io.in.ready && readVS1Reg.dataValid, // reduce
      executeEnqValid                                 // extend unit
    )
  )

  val executeValid: Bool = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnit.io.out.compressValid,
      false.B,
      executeEnqValid
    )
  )

  executeGroupCounter := Mux1H(
    unitType(3, 1),
    Seq(
      requestCounter,
      requestCounter,
      extendGroupCount
    )
  )

  val executeDeqGroupCounter: UInt = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnit.io.out.groupCounter,
      requestCounter,
      extendGroupCount
    )
  )

  val executeWriteByteMask: UInt = Mux(compress || ffo || mvVd, compressUnit.io.out.mask, executeByteMask)
  maskedWrite.needWAR := maskDestinationType
  maskedWrite.vd      := instReg.vd
  maskedWrite.in.zipWithIndex.foreach { case (req, index) =>
    val bitMask    = cutUInt(currentMaskGroupForDestination, parameter.datapathWidth)(index)
    val maskFilter = !maskDestinationType || bitMask.orR
    req.valid             := executeValid && maskFilter
    req.bits.mask         := cutUIntBySize(executeWriteByteMask, parameter.laneNumber)(index)
    req.bits.data         := cutUInt(executeResult, parameter.datapathWidth)(index)
    req.bits.pipeData     := exeReqReg(index).bits.source1
    req.bits.bitMask      := bitMask
    req.bits.groupCounter := executeDeqGroupCounter
    req.bits.ffoByOther   := compressUnit.io.out.ffoOutput(index) && ffo
    if (index == 0) {
      // reduce result
      when(unitType(2)) {
        req.valid             := reduceUnit.io.out.valid
        req.bits.mask         := reduceUnit.io.out.bits.mask
        req.bits.data         := reduceUnit.io.out.bits.data
        req.bits.groupCounter := 0.U
      }
    }
  }

  // mask unit write queue
  val writeQueue: Seq[QueueIO[MaskUnitExeResponse]] = Seq.tabulate(parameter.laneNumber) { _ =>
    Queue.io(new MaskUnitExeResponse(parameter.laneParam), maskUnitWriteQueueSize)
  }

  val dataNotInShifter: Bool = writeQueue.zipWithIndex.map { case (queue, index) =>
    val readTypeWriteVrf: Bool = waiteStageDeqFire && WillWriteLane(index)
    queue.enq.valid              := maskedWrite.out(index).valid || readTypeWriteVrf
    maskedWrite.out(index).ready := queue.enq.ready
    queue.enq.bits               := maskedWrite.out(index).bits
    when(readTypeWriteVrf) {
      queue.enq.bits := writeRequest(index)
    }
    queue.enq.bits.index         := instReg.instructionIndex

    // write vrf
    val writePort = exeResp(index)
    queue.deq.ready                 := writePort.ready
    writePort.valid                 := queue.deq.valid
    writePort.bits.last             := DontCare
    writePort.bits.instructionIndex := instReg.instructionIndex
    writePort.bits.data             := Mux(queue.deq.bits.ffoByOther, queue.deq.bits.pipeData, queue.deq.bits.writeData.data)
    writePort.bits.mask             := queue.deq.bits.writeData.mask
    writePort.bits.vd               := instReg.vd + queue.deq.bits.writeData.groupCounter(
      parameter.laneParam.groupNumberBits - 1,
      parameter.laneParam.vrfOffsetBits
    )
    writePort.bits.offset           := queue.deq.bits.writeData.groupCounter

    val writeTokenSize    = 8
    val writeTokenWidth   = log2Ceil(writeTokenSize)
    val writeTokenCounter = RegInit(0.U(writeTokenWidth.W))

    val writeTokenChange = Mux(writePort.fire, 1.U(writeTokenWidth.W), -1.S(writeTokenWidth.W).asUInt)
    when(writePort.fire ^ io.writeRelease(index)) {
      writeTokenCounter := writeTokenCounter + writeTokenChange
    }
    writeTokenCounter === 0.U
  }.reduce(_ && _)
  waiteStageDeqReady := writeQueue.zipWithIndex.map { case (queue, index) =>
    !WillWriteLane(index) || queue.enq.ready
  }.reduce(_ && _)
  writeRD <> DontCare

  // todo: token
  val waiteLastRequest: Bool = RegInit(false.B)
  val waitQueueClear:   Bool = RegInit(false.B)
  val lastReportValid = waitQueueClear && !writeQueue.map(_.deq.valid).reduce(_ || _) && dataNotInShifter
  when(lastReportValid) {
    waitQueueClear   := false.B
    waiteLastRequest := false.B
  }
  when(!readType && requestStageDeq && lastGroup) {
    waiteLastRequest := true.B
  }
  val executeStageInvalid: Bool = Mux1H(
    unitType(3, 1),
    Seq(
      !compressUnit.io.out.compressValid,
      reduceUnit.io.in.ready,
      true.B
    )
  )
  val executeStageClean: Bool = Mux(
    readType,
    waiteStageDeqFire && waiteReadDataPipeReg.last,
    waiteLastRequest && maskedWrite.stageClear && executeStageInvalid
  )
  val alwaysNeedExecute: Bool = enqMvRD
  val invalidEnq:        Bool = instReq.fire && !instReq.bits.vl && !alwaysNeedExecute
  when(executeStageClean || invalidEnq) {
    waitQueueClear := true.B
  }
  lastReport := maskAnd(
    lastReportValid,
    indexToOH(instReg.instructionIndex, parameter.chainingSize)
  )
  writeRDData := Mux(pop, reduceUnit.io.out.bits.data, compressUnit.io.writeData)

  // gather read state
  when(gatherRequestFire) {
    when(notNeedRead) {
      gatherReadState := sResponse
    }.otherwise {
      gatherReadState := sRead
    }
  }

  when(readCrossBar.input.head.fire && gatherSRead) {
    gatherReadState := wRead
  }

  gatherData.valid := gatherResponse
  gatherData.bits  := Mux(readVS1Reg.dataValid, readVS1Reg.data, 0.U)
  when(gatherData.fire) {
    gatherReadState := idle
  }
}
