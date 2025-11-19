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
  val clock:         Clock                             = Input(Clock())
  val reset:         Reset                             = Input(Reset())
  val instReq:       ValidIO[MaskUnitInstReq]          = Flipped(Valid(new MaskUnitInstReq(parameter)))
  val maskPipeReq:   ValidIO[maskPipeRequest]          = Flipped(Valid(new maskPipeRequest(parameter)))
  val exeReq:        Vec[DecoupledIO[MaskUnitExeReq]]  = Flipped(
    Vec(
      parameter.laneNumber,
      Decoupled(
        new MaskUnitExeReq(parameter.eLen, parameter.datapathWidth, parameter.instructionIndexBits, parameter.fpuEnable)
      )
    )
  )
  val exeResp:       Vec[DecoupledIO[VRFWriteRequest]] = Vec(
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
  val writeRelease:  Vec[Bool]                         = Vec(parameter.laneNumber, Input(Bool()))
  val tokenIO:       Vec[LaneTokenBundle]              = Flipped(Vec(parameter.laneNumber, new LaneTokenBundle))
  val readChannel:   Vec[DecoupledIO[VRFReadRequest]]  = Vec(
    parameter.laneNumber,
    Decoupled(
      new VRFReadRequest(
        parameter.vrfParam.regNumBits,
        parameter.laneParam.vrfOffsetBits,
        parameter.instructionIndexBits
      )
    )
  )
  val readResult:    Vec[ValidIO[UInt]]                = Flipped(Vec(parameter.laneNumber, Valid(UInt(parameter.datapathWidth.W))))
  val writeRD:       ValidIO[UInt]                     = Valid(UInt(parameter.datapathWidth.W))
  val lastReport:    UInt                              = Output(UInt(parameter.chaining1HBits.W))
  val laneMaskInput: Vec[UInt]                         = Output(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
  val askMaskVec:    Vec[MaskRequest]                  = Input(
    Vec(parameter.laneNumber, new MaskRequest(parameter.laneParam.maskGroupSizeBits))
  )
  val v0UpdateVec:   Vec[ValidIO[V0Update]]            = Flipped(
    Vec(parameter.laneNumber, Valid(new V0Update(parameter.laneParam.datapathWidth, parameter.laneParam.vrfOffsetBits)))
  )
  val writeRDData:   UInt                              = Output(UInt(parameter.xLen.W))
  val gatherData:    DecoupledIO[UInt]                 = Decoupled(UInt(parameter.xLen.W))
  val gatherRead:    Bool                              = Input(Bool())

  val writeCountVec: Vec[Valid[WriteCountReport]] =
    Vec(
      parameter.laneNumber,
      Valid(new WriteCountReport(parameter.vLen, parameter.laneNumber, parameter.instructionIndexBits))
    )

  val maskE0 = Output(Bool())
  val om: Property[ClassType] = Output(Property[AnyClassType]())
}

@instantiable
class MaskUnitOM(parameter: T1Parameter) extends GeneralOM[T1Parameter, MaskUnit](parameter) {
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

  val instReq       = io.instReq
  val exeReq        = io.exeReq
  val exeResp       = io.exeResp
  val tokenIO       = io.tokenIO
  val readChannel   = io.readChannel
  val readResult    = io.readResult
  val writeRD       = io.writeRD
  val lastReport    = io.lastReport
  val laneMaskInput = io.laneMaskInput
  val askMaskVec    = io.askMaskVec
  val v0UpdateVec   = io.v0UpdateVec
  val writeRDData   = io.writeRDData
  val gatherData    = io.gatherData
  val gatherRead    = io.gatherRead

  // todo: handle
  io.tokenIO.foreach { tk =>
    tk.maskRequestRelease := true.B
  }
  // todo: param
  val readQueueSize:          Int = 4
  val readVRFLatency:         Int = 3
  val maskUnitWriteQueueSize: Int = 8

  val compressParam: CompressParam = CompressParam(
    parameter.datapathWidth,
    parameter.xLen,
    parameter.vLen,
    parameter.laneNumber,
    parameter.laneParam.groupNumberBits,
    2,
    parameter.eLen
  )

  /** duplicate v0 for mask */
  val v0: Vec[UInt] = RegInit(
    VecInit(Seq.fill(parameter.vLen / parameter.datapathWidth)(0.U(parameter.datapathWidth.W)))
  )

  val slide            = io.maskPipeReq.bits.uop === BitPat("b001??")
  val gather           = io.maskPipeReq.bits.uop === BitPat("b0001?")
  val extend           = io.maskPipeReq.bits.uop === BitPat("b0000?")
  val slideScalar      = io.maskPipeReq.bits.uop(0)
  val slideUp          = io.maskPipeReq.bits.uop(1)
  val sew1HForMaskPipe = UIntToOH(instReq.bits.sew)(2, 0)
  val slideSize:     UInt = Mux(slideScalar, instReq.bits.readFromScala, 1.U)
  val dByte:         Int  = parameter.laneNumber * parameter.datapathWidth / 8
  val shifterUpSize: UInt = Mux1H(
    sew1HForMaskPipe,
    Seq(
      changeUIntSize(slideSize, log2Ceil(dByte)),
      changeUIntSize(slideSize, log2Ceil(dByte) - 1),
      changeUIntSize(slideSize, log2Ceil(dByte) - 2)
    )
  ) & Fill(log2Ceil(dByte), !(slideSize >> parameter.laneParam.vlMaxBits).asUInt.orR)
  // todo: parameter.vLen + param.dByte ???
  val slideUpV0:     UInt = changeUIntSize((v0.asUInt >> slideSize).asUInt, parameter.vLen)
  val slideDownV0Shift = (v0.asUInt << shifterUpSize).asUInt
  val slideDownV0: UInt = changeUIntSize(slideDownV0Shift, parameter.vLen)
  val slideV0Enq:  UInt = Mux(slideUp, slideUpV0, slideDownV0)
  val slideV0Reg:  UInt = RegEnable(slideV0Enq, 0.U.asTypeOf(slideV0Enq), io.maskPipeReq.valid && slide)
  val slideV0Overlap = (slideDownV0Shift >> parameter.vLen).asUInt
  val slideV0OverReg = RegEnable(slideV0Overlap, 0.U(dByte.W), io.maskPipeReq.valid && slide)
  val slideV0: Vec[UInt] = cutUInt(slideV0Reg, parameter.datapathWidth)

  // Calculate the write of slide
  // 1. Normal type, Directly determined by vl & v0, Include slide1 slide down
  // 1. slide up, slide up, Directly determined by vl & v0 & slideSize
  val baseV0:               UInt = Mux(instReq.bits.maskType, v0.asUInt, -1.S(parameter.vLen.W).asUInt)
  val vlCorrection:         UInt = (scanRightOr(UIntToOH(instReq.bits.vl)) >> 1).asUInt
  val shifterValidSize:     UInt = changeUIntSize(instReq.bits.readFromScala, parameter.laneParam.vlMaxBits)
  val shifterSizeOverlap:   Bool = (instReq.bits.readFromScala >> parameter.laneParam.vlMaxBits).asUInt.orR
  val upCorrection:         UInt = Mux(
    slideScalar && slideUp && slide,
    scanLeftOr(UIntToOH(shifterValidSize)) & Fill(parameter.vLen, !shifterSizeOverlap),
    -1.S(parameter.vLen.W).asUInt
  )
  val writeMaskForMaskPipe: UInt = changeUIntSize(baseV0 & vlCorrection & upCorrection, parameter.vLen)

  val sew1HForExtend: UInt = (sew1HForMaskPipe << instReq.bits.decodeResult(Decoder.crossWrite)).asUInt

  class WriteCountPipe0 extends Bundle {
    val instructionIndex:     UInt = UInt(parameter.instructionIndexBits.W)
    val writeMaskForMaskPipe: UInt = UInt(parameter.vLen.W)
    val sew1HForExtend:       UInt = UInt(3.W)
    val sew1HForMaskPipe:     UInt = UInt(3.W)
    val typeVec:              UInt = UInt(2.W)
  }

  val writeCountPipeWire0: WriteCountPipe0 = Wire(new WriteCountPipe0)
  writeCountPipeWire0.instructionIndex     := io.instReq.bits.instructionIndex
  writeCountPipeWire0.writeMaskForMaskPipe := writeMaskForMaskPipe
  writeCountPipeWire0.sew1HForExtend       := sew1HForExtend
  writeCountPipeWire0.sew1HForMaskPipe     := sew1HForMaskPipe
  writeCountPipeWire0.typeVec              := VecInit(
    Seq(
      slide || gather,
      extend
    )
  ).asUInt

  val writeCountPipe0: Valid[WriteCountPipe0] = Pipe(io.maskPipeReq.valid, writeCountPipeWire0, 1)

  val writeBitMaskForSlide: Vec[UInt] = VecInit(
    Seq(4, 2, 1).map { singleSize =>
      val groupSize = singleSize * (parameter.datapathWidth / parameter.eLen)
      cutUInt(writeCountPipe0.bits.writeMaskForMaskPipe, groupSize)
        .grouped(parameter.laneNumber)
        .toSeq
        .transpose
        .map(seq => VecInit(seq).asUInt)
    }.transpose.map(a =>
      changeUIntSize(Mux1H(writeCountPipe0.bits.sew1HForMaskPipe, a), parameter.vLen / parameter.laneNumber)
    )
  )

  val writeBitMaskForExtend: Vec[UInt] = VecInit(
    Seq(4, 2, 1).map { singleSize =>
      val groupSize = singleSize * (parameter.datapathWidth / parameter.eLen)
      cutUInt(writeCountPipe0.bits.writeMaskForMaskPipe, groupSize)
        .grouped(parameter.laneNumber)
        .toSeq
        .transpose
        .map(seq => VecInit(seq.map(_.orR)).asUInt)
    }.transpose.map(a =>
      changeUIntSize(Mux1H(writeCountPipe0.bits.sew1HForExtend, a), parameter.vLen / parameter.laneNumber)
    )
  )

  class WriteCountPipe1 extends Bundle {
    val writeBitMask:     Vec[UInt] = Vec(parameter.laneNumber, UInt((parameter.vLen / parameter.laneNumber).W))
    val instructionIndex: UInt      = UInt(parameter.instructionIndexBits.W)
  }

  val writeCountPipeWire1: WriteCountPipe1 = Wire(new WriteCountPipe1)
  writeCountPipeWire1.instructionIndex := writeCountPipe0.bits.instructionIndex
  writeCountPipeWire1.writeBitMask     := Mux1H(
    writeCountPipe0.bits.typeVec,
    Seq(
      writeBitMaskForSlide,
      writeBitMaskForExtend
    )
  )
  val writeCountPipe1: Valid[WriteCountPipe1] = Pipe(writeCountPipe0.valid, writeCountPipeWire1, 1)

  class WriteCountPipe2 extends Bundle {
    val writeCount:       Vec[UInt] = Vec(parameter.laneNumber, UInt(log2Ceil(parameter.vLen / parameter.laneNumber).W))
    val instructionIndex: UInt      = UInt(parameter.instructionIndexBits.W)
  }

  val writeCountPipeWire2: WriteCountPipe2 = Wire(new WriteCountPipe2)
  writeCountPipeWire2.instructionIndex := writeCountPipe1.bits.instructionIndex
  writeCountPipeWire2.writeCount       := VecInit(writeCountPipe1.bits.writeBitMask.map(PopCount(_)))
  val writeCountPipe2: Valid[WriteCountPipe2] = Pipe(writeCountPipe1.valid, writeCountPipeWire2, 1)

  io.writeCountVec.zipWithIndex.foreach { case (req, index) =>
    req.valid                 := writeCountPipe2.valid
    req.bits.count            := writeCountPipe2.bits.writeCount(index)
    req.bits.instructionIndex := writeCountPipe2.bits.instructionIndex
  }
  io.maskE0 := v0(0)(0)

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
  val regroupV0:      Seq[UInt] = Seq(4, 2, 1).map { singleSize =>
    val groupSize = singleSize * (parameter.datapathWidth / parameter.eLen)
    VecInit(
      cutUInt(v0.asUInt, groupSize)
        .grouped(parameter.laneNumber)
        .toSeq
        .transpose
        .map(seq => VecInit(seq).asUInt)
    ).asUInt
  }
  val regroupSlideV0: Seq[UInt] = Seq(4, 2, 1).map { singleSize =>
    val groupSize = singleSize * (parameter.datapathWidth / parameter.eLen)
    VecInit(
      cutUInt(slideV0.asUInt, groupSize)
        .grouped(parameter.laneNumber)
        .toSeq
        .transpose
        .map(seq => VecInit(seq).asUInt)
    ).asUInt
  }
  laneMaskInput.zipWithIndex.foreach { case (input, index) =>
    val res: Seq[UInt] = Seq(regroupV0, regroupSlideV0).map { regroup =>
      val v0ForThisLane: Seq[UInt] = regroup.map(rv => cutUInt(rv, parameter.vLen / parameter.laneNumber)(index))
      val v0SelectBySew = Mux1H(UIntToOH(askMaskVec(index).maskSelectSew)(2, 0), v0ForThisLane)
      val overlapSelect = Mux1H(
        UIntToOH(askMaskVec(index).maskSelectSew)(2, 0),
        Seq(4, 2, 1).map { singleSize =>
          val groupSize = singleSize * (parameter.datapathWidth / parameter.eLen)
          cutUInt(slideV0OverReg, groupSize)(index)
        }
      )
      val overlap       =
        ((parameter.vLen / parameter.datapathWidth / parameter.laneNumber).U & askMaskVec(index).maskSelect).orR
      Mux(
        overlap,
        overlapSelect,
        cutUInt(v0SelectBySew, parameter.datapathWidth)(askMaskVec(index).maskSelect)
      )
    }

    input := Mux(askMaskVec(index).slide, res.last, res.head)
  }

  val maskedWrite: BitLevelMaskWrite = Module(new BitLevelMaskWrite(parameter))

  def gatherIndex(elementIndex: UInt, vlmul: UInt, sew: UInt): (UInt, UInt, UInt, UInt, Bool) = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionSize = parameter.laneParam.vlMaxBits - 1
    val dataPosition = (changeUIntSize(elementIndex, positionSize) << sew).asUInt(positionSize - 1, 0)
    val sewOHInput   = UIntToOH(sew)(2, 0)

    val dataPathBaseBits = log2Ceil(parameter.datapathWidth / 8)
    val dataOffset: UInt = dataPosition(dataPathBaseBits - 1, 0)
    val accessLane =
      if (parameter.laneNumber > 1)
        dataPosition(log2Ceil(parameter.laneNumber) + dataPathBaseBits - 1, dataPathBaseBits)
      else 0.U(1.W)

    // 32 bit / group
    val dataGroup = (dataPosition >> (log2Ceil(parameter.laneNumber) + dataPathBaseBits)).asUInt
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
  val maskDestinationType: Bool = instReg.decodeResult(Decoder.topUop) === "b11000".U
  val compress:            Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b0100?")
  val viota:               Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b01000")
  val mv:                  Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b0101?")
  val mvRd:                Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b01011")
  val mvVd:                Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b01010")
  val ffo:                 Bool = instReg.decodeResult(Decoder.topUop) === BitPat("b0111?")
  val readValid:           Bool = readType && instVlValid

  // Instructions for writing vd without source
  val noSource: Bool = mv || viota

  val allGroupExecute: Bool = maskDestinationType || unitType(2) || compress || ffo
  val useDefaultSew:   Bool = unitType(0)
  // todo: decode ?
  // Indicates how many times a set of data will be executed
  // 0 -> 4 times
  // 1 -> 2 times
  // 3 -> 1 times
  val dataSplitSew:    UInt = Mux1H(
    Seq(
      useDefaultSew               -> instReg.sew,
      // extend
      (unitType(3) && subType(2)) -> (0 + log2Ceil(parameter.laneScale)).U,
      (unitType(3) && subType(1)) -> (1 + log2Ceil(parameter.laneScale)).U,
      allGroupExecute             -> 2.U
    )
  )

  // Indicates that an element will use the width of the original data
  val sourceDataUseDefaultSew: Bool = !unitType(3)
  val sourceDataEEW:           UInt = Mux1H(
    Seq(
      sourceDataUseDefaultSew -> instReg.sew,
      // extend
      unitType(3)             -> (instReg.sew >> subType(2, 1)).asUInt
    )
  )

  // ExecuteIndex is only related to how many times it will be executed, so use [dataSplitSew]
  val lastExecuteIndex: UInt = Mux1H(
    UIntToOH(dataSplitSew),
    Seq(8, 16, 32).map { dw =>
      ((parameter.datapathWidth / dw - 1) * dw / 8).U(parameter.dataPathByteBits.W)
    }
  )

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
    val laneDatalog       = log2Ceil(parameter.datapathWidth / 8)
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
      Seq(4, 2, 1).map { sewSize =>
        val eSize        = sewSize * parameter.laneScale
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

  val dataSourceSew:   UInt = Mux(
    unitType(3),
    instReg.sew - instReg.decodeResult(Decoder.topUop)(2, 1),
    instReg.sew
  )
  val dataSourceSew1H: UInt = UIntToOH(dataSourceSew)(2, 0)

  val normalFormat:            Bool = !maskFormatSource && !mv
  val lastGroupForInstruction: UInt = Mux1H(
    Seq(
      mv                                   -> 0.U,
      maskFormatSource                     -> processingMaskVl.head._1,
      (normalFormat && dataSourceSew1H(0)) -> processingVl.head._1,
      (normalFormat && dataSourceSew1H(1)) -> processingVl(1)._1,
      (normalFormat && dataSourceSew1H(2)) -> processingVl(2)._1
    )
  )

  val lastGroupDataNeed: UInt = Mux1H(
    Seq(
      maskFormatSource                     -> processingMaskVl.head._2,
      (normalFormat && dataSourceSew1H(0)) -> processingVl.head._2,
      (normalFormat && dataSourceSew1H(1)) -> processingVl(1)._2,
      (normalFormat && dataSourceSew1H(2)) -> processingVl(2)._2
    )
  )

  val groupSizeForMaskDestination:   Int  = parameter.laneNumber * parameter.datapathWidth
  val elementTailForMaskDestination: UInt = lastElementIndex(log2Ceil(groupSizeForMaskDestination) - 1, 0)

  val exeRequestQueue: Seq[QueueIO[MaskUnitExeReq]] = exeReq.zipWithIndex.map { case (req, index) =>
    val queue: QueueIO[MaskUnitExeReq] =
      Queue.io(chiselTypeOf(req.bits), parameter.laneParam.maskRequestQueueSize, flow = true)
    queue.enq <> req
    queue
  }

  val exeReqReg:           Seq[ValidIO[MaskUnitExeReq]] = Seq.tabulate(parameter.laneNumber) { _ =>
    RegInit(
      0.U.asTypeOf(
        Valid(
          new MaskUnitExeReq(
            parameter.eLen,
            parameter.datapathWidth,
            parameter.instructionIndexBits,
            parameter.fpuEnable
          )
        )
      )
    )
  }
  val requestCounter:      UInt                         = RegInit(0.U(parameter.laneParam.groupNumberBits.W))
  val executeGroupCounter: UInt                         = Wire(UInt(parameter.laneParam.groupNumberBits.W))

  val counterValid: Bool = requestCounter <= lastGroupForInstruction
  val lastGroup:    Bool =
    requestCounter === lastGroupForInstruction || mv

  // change data group from lane
  val lastExecuteGroupDeq: Bool = Wire(Bool())
  val viotaCounterAdd:     Bool = Wire(Bool())
  val groupCounterAdd:     Bool = Mux(noSource, viotaCounterAdd, lastExecuteGroupDeq)
  when(instReq.valid || groupCounterAdd) {
    requestCounter := Mux(instReq.valid, 0.U, requestCounter + 1.U)
  }

  // todo: mask
  val groupDataNeed: UInt = Mux(lastGroup, lastGroupDataNeed, (-1.S(parameter.laneNumber.W)).asUInt)
  // For read type, only sew * laneNumber data will be consumed each time
  // There will be a maximum of (dataPath * laneNumber) / (sew * laneNumber) times
  val executeIndex:  UInt = RegInit(0.U(parameter.dataPathByteBits.W))

  def indexAnalysis(sewInt: Int)(elementIndex: UInt, vlmul: UInt, valid: Option[Bool] = None): Seq[UInt] = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionSize = parameter.laneParam.vlMaxBits - 1
    val dataPosition = (changeUIntSize(elementIndex, positionSize) << sewInt).asUInt(positionSize - 1, 0)
    val accessMask: UInt = Seq(
      UIntToOH(dataPosition(1, 0)),
      FillInterleaved(2, UIntToOH(dataPosition(1))),
      15.U(4.W)
    )(sewInt)
    val dataPathBaseBits = log2Ceil(parameter.datapathWidth / 8)
    val dataOffset: UInt = dataPosition(dataPathBaseBits - 1, 0)
    val accessLane =
      if (parameter.laneNumber > 1)
        dataPosition(log2Ceil(parameter.laneNumber) + dataPathBaseBits - 1, dataPathBaseBits)
      else 0.U(1.W)
    // 32 bit / group
    val dataGroup  = (dataPosition >> (log2Ceil(parameter.laneNumber) + dataPathBaseBits)).asUInt
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
      requestCounter ## (executeIndex >> 1),
      requestCounter ## (executeIndex >> 2)
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
  val minSourceSize:    Int       = 8 * parameter.laneNumber
  val groupSourceData:  UInt      = VecInit(exeReqReg.map(_.bits.source1)).asUInt
  val groupSourceValid: UInt      = VecInit(exeReqReg.map(_.valid)).asUInt
  val shifterSource:    UInt      = Mux1H(
    UIntToOH(executeIndex),
    Seq.tabulate(parameter.datapathWidth / 8) { i =>
      (groupSourceData >> (minSourceSize * i)).asUInt
    }
  )
  val maxExecuteTimes:  Int       = parameter.datapathWidth / 8
  val selectValid:      UInt      = Mux1H(
    sourceDataEEW1H,
    Seq(
      cutUIntBySize(FillInterleaved(maxExecuteTimes, groupSourceValid), maxExecuteTimes)(executeIndex),
      cutUIntBySize(FillInterleaved(maxExecuteTimes / 2, groupSourceValid), maxExecuteTimes / 2)(
        executeIndex(parameter.dataPathByteBits - 1, 1)
      ),
      if (maxExecuteTimes > 4)
        cutUIntBySize(FillInterleaved(maxExecuteTimes / 4, groupSourceValid), maxExecuteTimes / 4)(
          executeIndex(parameter.dataPathByteBits - 1, 2)
        )
      else
        groupSourceValid
    )
  )
  val source:           Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
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

  val isLastExecuteGroup: Bool = (executeIndex === lastExecuteIndex) || allGroupExecute
  val allDataValid:       Bool = exeReqReg.zipWithIndex.map { case (d, i) => d.valid || !groupDataNeed(i) }.reduce(_ && _)
  val anyDataValid:       Bool = exeReqReg.zipWithIndex.map { case (d, i) => d.valid }.reduce(_ || _)

  // try to read vs1
  val readVs1Valid: Bool =
    (compress || mvRd) && !readVS1Reg.requestSend || gatherSRead
  readVS1Req.vs := instReg.vs1
  when(compress) {
    val logLaneNumber = log2Ceil(parameter.laneNumber)
    readVS1Req.vs       := instReg.vs1 + (readVS1Reg.readIndex >> (parameter.laneParam.vrfOffsetBits + logLaneNumber))
    readVS1Req.offset   := readVS1Reg.readIndex >> logLaneNumber
    readVS1Req.readLane := changeUIntSize(readVS1Reg.readIndex, logLaneNumber)
  }.elsewhen(gatherSRead || gatherWaiteRead) {
    readVS1Req.vs         := instReg.vs1 + gatherGrowth
    readVS1Req.offset     := gatherOffset
    readVS1Req.readLane   := gatherLane
    readVS1Req.dataOffset := gatherDatOffset
  }

  val compressUnitResultQueue: QueueIO[CompressOutput] = Queue.io(new CompressOutput(compressParam), 4, flow = true)

  val noSourceValid:       Bool = noSource && counterValid &&
    (instReg.vl.orR || (mvRd && !readVS1Reg.sendToExecution))
  val vs1DataValid:        Bool = readVS1Reg.dataValid || !(compress || mvRd)
  val executeReady:        Bool = Wire(Bool())
  val executeDeqReady:     Bool = VecInit(maskedWrite.in.map(_.ready)).asUInt.andR && compressUnitResultQueue.empty
  val otherTypeRequestDeq: Bool =
    Mux(noSource, noSourceValid, allDataValid) &&
      vs1DataValid && instVlValid && executeDeqReady
  val requestStageDeq:     Bool = otherTypeRequestDeq && executeReady

  val executeIndexGrowth: UInt = (1.U << dataSplitSew).asUInt
  when(requestStageDeq && anyDataValid) {
    executeIndex := executeIndex + executeIndexGrowth
  }

  // last execute group in this request group dequeue
  lastExecuteGroupDeq := requestStageDeq && isLastExecuteGroup

  val readVs1Fire:    Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))
  when(readVs1Fire.asUInt.orR) {
    readVS1Reg.requestSend := true.B

    when(gatherSRead) {
      gatherReadState := wRead
    }
  }
  val readVs1AckFire: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))
  val readVs1AckData: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
  when(readVs1AckFire.asUInt.orR) {
    readVS1Reg.data      := Mux1H(readVs1AckFire, readVs1AckData) >> (readVS1Req.dataOffset ## 0.U(3.W))
    readVS1Reg.dataValid := true.B
    when(gatherWaiteRead) {
      gatherReadState := sResponse
    }
  }
  // s1 read vrf
  readChannel.zipWithIndex.foreach { case (request, index) =>
    maskedWrite.readResult(index)            := readResult(index)
    maskedWrite.readChannel(index).ready     := readChannel(index).ready
    readChannel(index).valid                 := maskedWrite.readChannel(index).valid
    readChannel(index).bits.vs               := maskedWrite.readChannel(index).bits.vs
    readChannel(index).bits.offset           := maskedWrite.readChannel(index).bits.offset
    readChannel(index).bits.readSource       := 2.U
    readChannel(index).bits.instructionIndex := instReg.instructionIndex
    when(readVs1Valid && readVS1Req.readLane === index.U) {
      readChannel(index).valid       := true.B
      readChannel(index).bits.vs     := readVS1Req.vs
      readChannel(index).bits.offset := readVS1Req.offset
    }
    readVs1Fire(index)                       := request.fire && readVS1Req.readLane === index.U
    readVs1AckFire(index)                    := readResult(index).fire && readVS1Req.readLane === index.U
    readVs1AckData(index)                    := readResult(index).bits
  }

  // Determine whether the data is ready
  val executeEnqValid: Bool = otherTypeRequestDeq

  // start execute
  val compressUnit = Instantiate(new MaskCompress(compressParam))
  omInstance.compressIn := compressUnit.io.om.asAnyClassType

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
  compressUnit.io.in.bits.pipeData       := source1
  compressUnit.io.in.bits.groupCounter   := requestCounter
  compressUnit.io.in.bits.lastCompress   := lastGroup
  compressUnit.io.in.bits.ffoInput       := VecInit(exeReqReg.map(_.bits.ffo)).asUInt
  compressUnit.io.in.bits.validInput     := VecInit(exeReqReg.map(_.valid)).asUInt
  compressUnit.io.newInstruction         := instReq.valid
  compressUnit.io.ffoInstruction         := instReq.bits.decodeResult(Decoder.topUop)(2, 0) === BitPat("b11?")

  compressUnitResultQueue.enq.valid := compressUnit.io.out.compressValid
  compressUnitResultQueue.enq.bits  := compressUnit.io.out

  when(compressUnit.io.in.fire) {
    readVS1Reg.sendToExecution := true.B
  }

  extendUnit.in.eew          := instReg.sew
  extendUnit.in.uop          := instReg.decodeResult(Decoder.topUop)
  extendUnit.in.source2      := source2
  extendUnit.in.groupCounter := requestCounter

  val executeResult: UInt = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnitResultQueue.deq.bits.data,
      extendUnit.out,
      extendUnit.out
    )
  )

  // todo
  executeReady := Mux1H(
    unitType,
    Seq(
      true.B,         // read type
      true.B,         // compress
      true.B,         // reduce
      executeEnqValid // extend unit
    )
  )

  compressUnitResultQueue.deq.ready := VecInit(maskedWrite.in.map(_.ready)).asUInt.andR
  val compressDeq:  Bool = compressUnitResultQueue.deq.fire
  val executeValid: Bool = Mux1H(
    unitType(3, 1),
    Seq(
      compressDeq,
      false.B,
      executeEnqValid
    )
  )

  executeGroupCounter := requestCounter

  val executeDeqGroupCounter: UInt = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnitResultQueue.deq.bits.groupCounter,
      requestCounter,
      requestCounter
    )
  )

  val executeWriteByteMask: UInt = Mux(compress || ffo || mvVd, compressUnitResultQueue.deq.bits.mask, executeByteMask)
  maskedWrite.needWAR := maskDestinationType
  maskedWrite.vd      := instReg.vd
  maskedWrite.in.zipWithIndex.foreach { case (req, index) =>
    val bitMask    = cutUInt(currentMaskGroupForDestination, parameter.datapathWidth)(index)
    val maskFilter = !maskDestinationType || bitMask.orR
    req.valid             := executeValid && maskFilter
    req.bits.mask         := cutUIntBySize(executeWriteByteMask, parameter.laneNumber)(index)
    req.bits.data         := cutUInt(executeResult, parameter.datapathWidth)(index)
    req.bits.bitMask      := bitMask
    req.bits.groupCounter := executeDeqGroupCounter
    req.bits.ffoByOther   := compressUnitResultQueue.deq.bits.ffoOutput(index) && ffo
  }

  // mask unit write queue
  val writeQueue: Seq[QueueIO[MaskUnitExeResponse]] = Seq.tabulate(parameter.laneNumber) { _ =>
    Queue.io(new MaskUnitExeResponse(parameter.laneParam), maskUnitWriteQueueSize)
  }

  val dataNotInShifter: Bool = writeQueue.zipWithIndex.map { case (queue, index) =>
    queue.enq.valid              := maskedWrite.out(index).valid
    maskedWrite.out(index).ready := queue.enq.ready
    queue.enq.bits               := maskedWrite.out(index).bits
    queue.enq.bits.index         := instReg.instructionIndex

    // write vrf
    val writePort = exeResp(index)
    queue.deq.ready                 := writePort.ready
    writePort.valid                 := queue.deq.valid
    writePort.bits.last             := DontCare
    writePort.bits.instructionIndex := instReg.instructionIndex
    writePort.bits.data             := queue.deq.bits.writeData.data
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
      !compressUnitResultQueue.deq.valid && !compressUnit.io.stageValid,
      true.B,
      true.B
    )
  )
  val executeStageClean: Bool = waiteLastRequest && maskedWrite.stageClear && executeStageInvalid
  val alwaysNeedExecute: Bool = enqMvRD
  val invalidEnq:        Bool = instReq.fire && !instReq.bits.vl && !alwaysNeedExecute
  when(executeStageClean || invalidEnq) {
    waitQueueClear := true.B
  }
  lastReport := maskAnd(
    lastReportValid,
    indexToOH(instReg.instructionIndex, parameter.chainingSize)
  )
  writeRDData := compressUnit.io.writeData

  // gather read state
  when(gatherRequestFire) {
    when(notNeedRead) {
      gatherReadState := sResponse
    }.otherwise {
      gatherReadState := sRead
    }
  }

  gatherData.valid := gatherResponse
  gatherData.bits  := Mux(readVS1Reg.dataValid, readVS1Reg.data, 0.U)
  when(gatherData.fire) {
    gatherReadState := idle
  }
}
