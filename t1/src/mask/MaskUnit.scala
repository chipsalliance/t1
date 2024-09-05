// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.util._
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
@instantiable
class MaskUnit(parameter: T1Parameter) extends Module {
  // todo: param
  val readQueueSize:          Int = 4
  val readVRFLatency:         Int = 2
  val maskUnitWriteQueueSize: Int = 8

  @public
  val instReq: ValidIO[MaskUnitInstReq] = IO(Flipped(Valid(new MaskUnitInstReq(parameter))))

  @public
  val exeReq: Seq[DecoupledIO[MaskUnitExeReq]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Flipped(Decoupled(new MaskUnitExeReq(parameter.laneParam))))
  }

  @public
  val exeResp: Seq[ValidIO[MaskUnitExeResponse]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Valid(new MaskUnitExeResponse(parameter.laneParam)))
  }

  @public
  val readChannel: Seq[DecoupledIO[VRFReadRequest]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(
      Decoupled(
        new VRFReadRequest(
          parameter.vrfParam.regNumBits,
          parameter.laneParam.vrfOffsetBits,
          parameter.instructionIndexBits
        )
      )
    )
  }

  @public
  val readResult: Seq[UInt] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Input(UInt(parameter.datapathWidth.W)))
  }

  @public
  val writeRD: ValidIO[UInt] = IO(Valid(UInt(parameter.datapathWidth.W)))

  @public
  val lastReport: UInt = IO(Output(UInt(parameter.chainingSize.W)))

  // mask
  @public
  val lsuMaskInput: Vec[UInt] = IO(Output(Vec(parameter.lsuMSHRSize, UInt(parameter.maskGroupWidth.W))))

  @public
  val lsuMaskSelect: Vec[UInt] =
    IO(Input(Vec(parameter.lsuMSHRSize, UInt(parameter.lsuParameters.maskGroupSizeBits.W))))

  // mask
  @public
  val laneMaskInput: Vec[UInt] = IO(Output(Vec(parameter.laneNumber, UInt(parameter.maskGroupWidth.W))))

  @public
  val laneMaskSelect: Vec[UInt] =
    IO(Input(Vec(parameter.laneNumber, UInt(parameter.laneParam.maskGroupSizeBits.W))))

  @public
  val laneMaskSewSelect: Vec[UInt] = IO(Input(Vec(parameter.laneNumber, UInt(2.W))))

  @public
  val v0UpdateVec = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Flipped(Valid(new V0Update(parameter.laneParam))))
  }

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

  // lsu
  lsuMaskInput.zip(lsuMaskSelect).foreach { case (data, index) =>
    data := cutUInt(v0.asUInt, parameter.maskGroupWidth)(index)
  }

  val maskedWrite: BitLevelMaskWrite = Module(new BitLevelMaskWrite(parameter))

  val instReg:          MaskUnitInstReq = RegEnable(instReq.bits, 0.U.asTypeOf(instReq.bits), instReq.valid)
  val sew1H:            UInt            = UIntToOH(instReg.sew)(2, 0)
  val lastExecuteIndex: UInt            = Mux1H(sew1H, Seq(3.U(2.W), 2.U(2.W), 0.U(2.W)))

  // from decode
  val unitType:            UInt = UIntToOH(instReg.decodeResult(Decoder.topUop)(4, 3))
  val readType:            Bool = unitType(0)
  val gather16:            Bool = instReg.decodeResult(Decoder.topUop) === "b00101".U
  val maskDestinationType: Bool = instReg.decodeResult(Decoder.topUop) === "b11000".U

  // calculate last group
  val readDataEew1H:    UInt = sew1H
  val lastElementIndex: UInt = (instReg.vl - instReg.vl.orR)(parameter.laneParam.vlMaxBits - 2, 0)
  val laneNumberBits:   Int  = 1.max(log2Ceil(parameter.laneNumber))

  /** For an instruction, the last group is not executed by all lanes, here is the last group of the instruction xxxxx
    * xxx xx -> vsew = 0 xxxxxx xxx x -> vsew = 1 xxxxxxx xxx -> vsew = 2
    */
  val lastGroupForOther: UInt = Mux1H(
    readDataEew1H,
    Seq(
      lastElementIndex(parameter.laneParam.vlMaxBits - 2, laneNumberBits + 2),
      lastElementIndex(parameter.laneParam.vlMaxBits - 2, laneNumberBits + 1),
      lastElementIndex(parameter.laneParam.vlMaxBits - 2, laneNumberBits)
    )
  )

  val groupSizeForMaskDestination: Int  = parameter.laneNumber * parameter.datapathWidth
  val lastGroupForMaskDestination: UInt = (lastElementIndex >> log2Ceil(groupSizeForMaskDestination)).asUInt
  val lastGroupForInstruction:     UInt = Mux(maskDestinationType, lastGroupForMaskDestination, lastGroupForOther)

  /** Which lane the last element is in. */
  val lastLaneIndex:             UInt = Mux1H(
    readDataEew1H,
    Seq(
      lastElementIndex(laneNumberBits + 2 - 1, 2),
      lastElementIndex(laneNumberBits + 1 - 1, 1),
      lastElementIndex(laneNumberBits - 1, 0)
    )
  )
  val lastGroupDataNeedForOther: UInt = scanRightOr(UIntToOH(lastLaneIndex))

  val elementTailForMaskDestination = lastElementIndex(log2Ceil(groupSizeForMaskDestination) - 1, 0)
  //        xxx   -> widthForLaneIndex
  //           .. -> widthForDataPath
  //      ..      -> tailMsB
  // 0 -> ..xxx..
  // 1 -> ...xxx.
  // 2 -> ....xxx
  val lastGroupDataNeedForMaskDestination: UInt = Mux1H(
    readDataEew1H,
    VecInit(Seq(0, 1, 2).map { sewInt =>
      val widthForDataPath  = 2 - sewInt
      val widthForLaneIndex = log2Ceil(parameter.laneNumber)
      val tailMsB           = elementTailForMaskDestination >> (widthForDataPath + widthForLaneIndex)
      val allDataNeed       = tailMsB.asUInt.orR
      scanRightOr(UIntToOH(elementTailForMaskDestination(widthForDataPath + widthForLaneIndex - 1, widthForDataPath))) |
        Fill(parameter.laneNumber, allDataNeed)
    })
  )

  val lastGroupDataNeed: UInt = Mux(maskDestinationType, lastGroupDataNeedForMaskDestination, lastGroupDataNeedForOther)

  val sewCorrection: UInt = Mux(gather16, 1.U, instReg.sew)

  val exeRequestQueue: Seq[Queue[MaskUnitExeReq]] = exeReq.map { req =>
    // todo: max or token?
    val queue: Queue[MaskUnitExeReq] = Module(new Queue(chiselTypeOf(req.bits), 16, flow = true))
    queue.io.enq.valid := req.valid
    req.ready          := queue.io.enq.ready
    queue.io.enq.bits  := req.bits
    queue
  }

  val exeReqReg:             Seq[ValidIO[MaskUnitExeReq]] = Seq.tabulate(parameter.laneNumber) { _ =>
    RegInit(0.U.asTypeOf(Valid(new MaskUnitExeReq(parameter.laneParam))))
  }
  val lastGroup:             Bool                         = exeReqReg.head.bits.groupCounter === lastGroupForInstruction
  // todo: mask
  val groupDataNeed:         UInt                         = Mux(lastGroup, lastGroupDataNeed, (-1.S(parameter.laneNumber.W)).asUInt)
  // For read type, only sew * laneNumber data will be consumed each time
  // There will be a maximum of (dataPath * laneNumber) / (sew * laneNumber) times
  val executeIndex:          UInt                         = RegInit(0.U(2.W))
  // The status of an execution
  // Each execution ends with executeIndex + 1
  val requestStageReadState: MaskUnitExecuteState         = RegInit(0.U.asTypeOf(new MaskUnitExecuteState(parameter)))
  val requestStageValid:     Bool                         = RegInit(false.B)

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
    sew1H,
    Seq(
      exeReqReg.head.bits.groupCounter ## executeIndex,
      exeReqReg.head.bits.groupCounter ## executeIndex(1),
      exeReqReg.head.bits.groupCounter
    )
  )

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
  val readMaskSelect:      UInt =
    (executeGroup >> log2Ceil(parameter.datapathWidth / parameter.laneNumber)).asUInt
  val readMaskInput:       UInt = cutUInt(v0.asUInt, parameter.maskGroupWidth)(readMaskSelect)
  val selectReadStageMask: UInt = cutUIntBySize(readMaskInput, 4)(executeGroup(1, 0))
  val maskCorrection:      UInt =
    Mux(instReg.maskType, selectReadStageMask, -1.S(parameter.laneNumber.W).asUInt) &
      vlBoundaryCorrection

  // mask for destination
  val maskForDestination:             UInt = cutUInt(v0.asUInt, groupSizeForMaskDestination)(exeReqReg.head.bits.groupCounter)
  val lastGroupMask:                  UInt = scanRightOr(UIntToOH(elementTailForMaskDestination))
  val currentMaskGroupForDestination: UInt = maskEnable(lastGroup, lastGroupMask) &
    maskEnable(instReg.maskType, maskForDestination)

  val checkVec:           Seq[Seq[UInt]] = Seq(0, 1, 2).map { sewInt =>
    val dataByte = 1 << sewInt
    // All data of this group
    val groupSourceData:  UInt = VecInit(exeReqReg.map(_.bits.source1)).asUInt
    val groupSourceValid: UInt = VecInit(exeReqReg.map(_.valid)).asUInt
    // Single use length
    val singleWidth  = dataByte * 8 * parameter.laneNumber
    // How many times will a set of data be executed?
    val executeTimes = (parameter.datapathWidth / 8) / dataByte
    // Which part is selected as the source data this time?
    val executeDataSelect1H: UInt = if (sewInt == 0) {
      UIntToOH(executeIndex)
    } else if (sewInt == 1) {
      UIntToOH(executeIndex(1))
    } else {
      true.B
    }
    // Select source data
    val sourceSelect = Mux1H(
      executeDataSelect1H,
      cutUInt(groupSourceData, singleWidth)
    )
    val validSelect: UInt = Mux1H(
      executeDataSelect1H,
      cutUInt(groupSourceValid, singleWidth / parameter.datapathWidth)
    )

    // The length of an element
    val dataWidth = 8 * dataByte
    // Split into elements
    val source    = cutUInt(sourceSelect, dataWidth)
    val validVec  = FillInterleaved(parameter.datapathWidth / dataWidth, validSelect) & maskCorrection
    // read index check
    // (accessMask, dataOffset, accessLane, offset, reallyGrowth, overlap)
    val checkResultVec: Seq[Seq[UInt]] = source.zipWithIndex.map { case (s, i) =>
      indexAnalysis(sewInt)(s, instReg.vlmul, Some(validVec(i)))
    }
    val checkResult = checkResultVec.transpose.map(a => VecInit(a).asUInt)
    checkResult
  }
  val sewCorrection1H:    UInt           = UIntToOH(sewCorrection)(2, 0)
  val dataOffsetSelect:   UInt           = Mux1H(sewCorrection1H, checkVec.map(_(1)))
  val accessLaneSelect:   UInt           = Mux1H(sewCorrection1H, checkVec.map(_(2)))
  val offsetSelect:       UInt           = Mux1H(sewCorrection1H, checkVec.map(_(3)))
  val growthSelect:       UInt           = Mux1H(sewCorrection1H, checkVec.map(_(4)))
  val notReadSelect:      UInt           = Mux1H(sewCorrection1H, checkVec.map(_(5)))
  val elementValidSelect: UInt           = Mux1H(sewCorrection1H, checkVec.map(_(6)))

  val readCrossBar: MaskUnitReadCrossBar = Module(new MaskUnitReadCrossBar(parameter))

  // The queue waiting to read data. This queue contains other information about this group.
  // 64: todo: max or token?
  val readWaitQueue: Queue[MaskUnitWaitReadQueue] =
    Module(new Queue(new MaskUnitWaitReadQueue(parameter), 64))

  // s0 pipe request from lane
  val laseExecuteGroupDeq: Bool = Wire(Bool())
  exeRequestQueue.zip(exeReqReg).foreach { case (req, reg) =>
    req.io.deq.ready := !reg.valid || laseExecuteGroupDeq
    when(req.io.deq.fire) {
      reg.bits := req.io.deq.bits
    }
    when(req.io.deq.fire ^ laseExecuteGroupDeq) {
      reg.valid := req.io.deq.fire
    }
  }

  val isLastExecuteGroup: Bool = executeIndex === lastExecuteIndex
  val allDataValid:       Bool = exeReqReg.zipWithIndex.map { case (d, i) => d.valid || !groupDataNeed(i) }.reduce(_ && _)

  // select execute group
  val selectExecuteReq: Seq[ValidIO[MaskUnitReadReq]] = exeReqReg.zipWithIndex.map { case (_, index) =>
    val res: ValidIO[MaskUnitReadReq] = WireInit(0.U.asTypeOf(Valid(new MaskUnitReadReq(parameter))))
    res.bits.vs           := instReg.vs2 + requestStageReadState.vsGrowth(index)
    if (parameter.laneParam.vrfOffsetBits > 0) {
      res.bits.offset := requestStageReadState.readOffset(index)
    }
    res.bits.readLane     := requestStageReadState.accessLane(index)
    res.bits.dataOffset   := cutUIntBySize(requestStageReadState.readDataOffset, parameter.laneNumber)(index)
    res.bits.requestIndex := index.U
    res.valid             := requestStageValid && !requestStageReadState.groupReadState(index) &&
      requestStageReadState.needRead(index) && unitType(0)
    res
  }

  // read arbitration
  readCrossBar.input.zip(selectExecuteReq).foreach { case (cross, req) =>
    cross.valid := req.valid
    cross.bits  := req.bits
  }

  // read control register update
  val readFire:           UInt = VecInit(readCrossBar.input.map(_.fire)).asUInt
  val anyReadFire:        Bool = readFire.orR
  val readStateUpdate:    UInt = readFire | requestStageReadState.groupReadState
  val groupReadFinish:    Bool = readStateUpdate === requestStageReadState.needRead
  val readTypeRequestDeq: Bool =
    (anyReadFire && groupReadFinish) || (requestStageValid && requestStageReadState.needRead === 0.U)
  val otherTypeRequestDeq = requestStageValid && allDataValid
  val requestStageDeq     = Mux(readType, readTypeRequestDeq, otherTypeRequestDeq)
  val executeStateEnq: Bool = (allDataValid && readTypeRequestDeq) || !requestStageValid
  when(anyReadFire) {
    requestStageReadState.groupReadState := readStateUpdate
  }

  when(readTypeRequestDeq ^ executeStateEnq) {
    requestStageValid := executeStateEnq
  }

  val executeIndexGrowth: UInt = (1.U << instReg.sew).asUInt
  when(executeStateEnq) {
    requestStageReadState.groupReadState := 0.U
    requestStageReadState.needRead       := (~notReadSelect).asUInt
    requestStageReadState.elementValid   := elementValidSelect
    requestStageReadState.accessLane     := cutUIntBySize(accessLaneSelect, parameter.laneNumber)
    requestStageReadState.vsGrowth       := cutUIntBySize(growthSelect, parameter.laneNumber)
    requestStageReadState.readOffset     := offsetSelect
    requestStageReadState.groupCount     := exeReqReg.head.bits.groupCounter
    requestStageReadState.executeIndex   := executeIndex
    requestStageReadState.readDataOffset := dataOffsetSelect
    requestStageReadState.last           := isVlBoundary
    executeIndex                         := executeIndex + executeIndexGrowth
  }

  readWaitQueue.io.enq.valid             := readTypeRequestDeq
  readWaitQueue.io.enq.bits.groupCounter := requestStageReadState.groupCount
  readWaitQueue.io.enq.bits.executeIndex := requestStageReadState.executeIndex
  readWaitQueue.io.enq.bits.sourceValid  := requestStageReadState.elementValid
  readWaitQueue.io.enq.bits.needRead     := requestStageReadState.needRead
  readWaitQueue.io.enq.bits.last         := requestStageReadState.last

  // last execute group in this request group dequeue
  laseExecuteGroupDeq := requestStageDeq && isLastExecuteGroup

  // s1 read vrf
  val write1HPipe:    Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.laneNumber.W)))
  val pipeDataOffset: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(log2Ceil(parameter.datapathWidth / 8).W)))

  readCrossBar.output.zipWithIndex.foreach { case (request, index) =>
    val sourceLane = UIntToOH(request.bits.writeIndex)
    readChannel(index).valid                 := request.valid
    readChannel(index).bits.readSource       := 2.U
    readChannel(index).bits.vs               := request.bits.vs
    readChannel(index).bits.offset           := request.bits.offset
    readChannel(index).bits.instructionIndex := instReg.instructionIndex
    request.ready                            := readChannel(index).ready

    maskedWrite.readChannel(index).ready := readChannel(index).ready
    maskedWrite.readResult(index)        := readResult(index)
    when(maskDestinationType) {
      readChannel(index).valid       := maskedWrite.readChannel(index).valid
      readChannel(index).bits.vs     := maskedWrite.readChannel(index).bits.vs
      readChannel(index).bits.offset := maskedWrite.readChannel(index).bits.offset
    }

    // pipe read fire
    val pipeRead   = Pipe(readChannel(index).fire, sourceLane, readVRFLatency)
    val pipeOffset = Pipe(readChannel(index).fire, request.bits.dataOffset, readVRFLatency)
    write1HPipe(index)    := Mux(pipeRead.valid, pipeRead.bits, 0.U(parameter.laneNumber.W))
    pipeDataOffset(index) := pipeOffset.bits
  }

  // Processing read results
  val readData: Seq[DecoupledIO[UInt]] = Seq.tabulate(parameter.laneNumber) { index =>
    // todo: assert enq.read & use token
    val readDataQueue    = Module(new Queue(UInt(parameter.datapathWidth.W), 4, flow = true))
    val readResultSelect = VecInit(write1HPipe.map(_(index))).asUInt
    val dataOffset: UInt = Mux1H(readResultSelect, pipeDataOffset)
    readDataQueue.io.enq.valid := readResultSelect.orR
    readDataQueue.io.enq.bits  := Mux1H(readResultSelect, readResult) >> (dataOffset ## 0.U(3.W))
    readDataQueue.io.deq
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
  val writeDataVec  = Seq(0, 1, 2).map { sewInt =>
    val dataByte = 1 << sewInt
    val data     = VecInit(Seq.tabulate(parameter.laneNumber) { laneIndex =>
      val dataElement: UInt = Wire(UInt((dataByte * 8).W))
      val dataIsRead = waiteReadDataPipeReg.needRead(laneIndex)
      // todo: select vs1 when slide1
      dataElement := Mux(dataIsRead, waiteReadData(laneIndex), 0.U)
      dataElement
    }).asUInt

    val shifterData = (data << (waiteReadDataPipeReg.executeIndex ## 0.U(log2Ceil(eachIndexSize).W))).asUInt
    // align
    changeUIntSize(shifterData, dlen)
  }
  val writeData     = Mux1H(sew1H, writeDataVec)

  val writeMaskVec: Seq[UInt] = Seq(0, 1, 2).map { sewInt =>
    val MaskMagnification = 1 << sewInt
    val mask              = FillInterleaved(MaskMagnification, waiteReadDataPipeReg.sourceValid)
    val shifterMask       = (mask << (waiteReadDataPipeReg.executeIndex ## 0.U(log2Ceil(eachIndexSize / 8).W))).asUInt
    // align
    changeUIntSize(shifterMask, dlen / 8)
  }
  val writeMask = Mux1H(sew1H, writeMaskVec)

  val writeRequest:  Seq[MaskUnitExeResponse] = Seq.tabulate(parameter.laneNumber) { laneIndex =>
    val res: MaskUnitExeResponse = Wire(new MaskUnitExeResponse(parameter.laneParam))
    res.ffoByOther             := false.B
    res.index                  := instReg.instructionIndex
    res.writeData.groupCounter := waiteReadDataPipeReg.groupCounter
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
  val waiteStageEnqFire:  Bool = readWaitQueue.io.deq.valid && waiteStageEnqReady

  readWaitQueue.io.deq.ready := waiteStageEnqReady

  when(waiteStageEnqFire) {
    waiteReadDataPipeReg := readWaitQueue.io.deq.bits
  }

  when(waiteStageDeqFire ^ waiteStageEnqFire) {
    waiteReadStageValid := waiteStageEnqFire
  }

  waiteReadData.zipWithIndex.foreach { case (reg, index) =>
    val isWaiteForThisData = waiteReadDataPipeReg.needRead(index) && !waiteReadSate(index) && waiteReadStageValid
    val read               = readData(index)
    read.ready := isWaiteForThisData
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

  // start execute
  val compressUnit: MaskCompress = Module(new MaskCompress(parameter))
  val reduceUnit:   MaskReduce   = Module(new MaskReduce(parameter))
  val extendUnit:   MaskExtend   = Module(new MaskExtend(parameter))

  // todo
  val source2: UInt = VecInit(exeReqReg.map(_.bits.source2)).asUInt
  val source1: UInt = VecInit(exeReqReg.map(_.bits.source1)).asUInt

  compressUnit.in.valid               := executeEnqValid && unitType(1)
  compressUnit.in.bits.maskType       := instReg.maskType
  compressUnit.in.bits.eew            := instReg.sew
  compressUnit.in.bits.uop            := instReg.decodeResult(Decoder.topUop)
  compressUnit.in.bits.readFromScalar := instReg.readFromScala
  compressUnit.in.bits.source1        := source1
  compressUnit.in.bits.source2        := source2
  compressUnit.in.bits.groupCounter   := waiteReadDataPipeReg.groupCounter
  compressUnit.in.bits.lastCompress   := lastGroup
  compressUnit.newInstruction         := instReq.valid

  reduceUnit.in.valid             := executeEnqValid && unitType(2)
  reduceUnit.in.bits.maskType     := instReg.maskType
  reduceUnit.in.bits.eew          := instReg.sew
  reduceUnit.in.bits.uop          := instReg.decodeResult(Decoder.topUop)
  reduceUnit.in.bits.readVS1      := source1
  reduceUnit.in.bits.source2      := source2
  reduceUnit.in.bits.sourceValid  := waiteReadDataPipeReg.sourceValid
  reduceUnit.in.bits.groupCounter := waiteReadDataPipeReg.groupCounter
  reduceUnit.in.bits.lastGroup    := lastGroup
  reduceUnit.in.bits.vxrm         := instReg.vxrm
  reduceUnit.in.bits.aluUop       := instReg.decodeResult(Decoder.uop)
  reduceUnit.in.bits.sign         := !instReg.decodeResult(Decoder.unsigned1)
  reduceUnit.newInstruction       := instReq.valid

  extendUnit.in.eew          := instReg.sew
  extendUnit.in.uop          := instReg.decodeResult(Decoder.topUop)
  extendUnit.in.source2      := source2
  extendUnit.in.groupCounter := waiteReadDataPipeReg.groupCounter

  val executeResult: UInt = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnit.out.data,
      reduceUnit.out.bits.data,
      extendUnit.out
    )
  )

  //  val executeValid = Mux1H(
  //    unitType,
  //    Seq(
  //      executeEnqValid,
  //      compressUnit.out.compressValid,
  //      reduceUnit.out.valid,
  //      executeEnqValid
  //    )
  //  )
  val executeValid: Bool = Mux1H(
    unitType(3, 1),
    Seq(
      compressUnit.out.compressValid,
      reduceUnit.out.valid,
      executeEnqValid
    )
  )

  maskedWrite.needWAR := maskDestinationType
  maskedWrite.vd      := instReg.vd
  maskedWrite.in.zipWithIndex.foreach { case (req, index) =>
    req.valid             := executeValid
    req.bits.mask         := maskCorrection // todo
    req.bits.data         := cutUInt(executeResult, parameter.datapathWidth)(index)
    req.bits.bitMask      := cutUInt(currentMaskGroupForDestination, parameter.datapathWidth)(index)
    req.bits.groupCounter := exeReqReg.head.bits.groupCounter
  }

  // mask unit write queue
  val writeQueue: Seq[Queue[MaskUnitExeResponse]] = Seq.tabulate(parameter.laneNumber) { _ =>
    Module(
      new Queue(
        new MaskUnitExeResponse(parameter.laneParam),
        maskUnitWriteQueueSize
      )
    )
  }

  writeQueue.zipWithIndex.foreach { case (queue, index) =>
    val readTypeWriteVrf: Bool = waiteStageDeqFire && WillWriteLane(index)
    queue.io.enq.valid           := maskedWrite.out(index).valid || readTypeWriteVrf
    maskedWrite.out(index).ready := queue.io.enq.ready
    queue.io.enq.bits            := maskedWrite.out(index).bits
    when(readTypeWriteVrf) {
      queue.io.enq.bits := writeRequest(index)
    }
    queue.io.enq.bits.ffoByOther := false.B // todo
    queue.io.enq.bits.index      := instReg.instructionIndex

    // write vrf
    val writePort = exeResp(index)
    queue.io.deq.ready := true.B
    writePort.valid    := queue.io.deq.valid
    writePort.bits     := queue.io.deq.bits
  }
  waiteStageDeqReady := writeQueue.zipWithIndex.map { case (queue, index) =>
    !WillWriteLane(index) || queue.io.enq.ready
  }.reduce(_ && _)
  writeRD <> DontCare

  // todo: token
  val waiteLastRequest: Bool = RegInit(false.B)
  val waitQueueClear:   Bool = RegInit(false.B)
  val lastReportValid = waitQueueClear && !writeQueue.map(_.io.deq.valid).reduce(_ || _)
  when(lastReportValid) {
    waitQueueClear   := false.B
    waiteLastRequest := false.B
  }
  when(!readType && executeEnqValid && lastGroup) {
    waiteLastRequest := true.B
  }
  val executeStageClear: Bool = Mux(
    readType,
    waiteStageDeqFire && waiteReadDataPipeReg.last,
    waiteLastRequest && maskedWrite.stageClear
  )
  when(executeStageClear) {
    waitQueueClear := true.B
  }
  lastReport := maskAnd(
    lastReportValid,
    indexToOH(instReg.instructionIndex, parameter.chainingSize)
  )
}
