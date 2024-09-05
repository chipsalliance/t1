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
  val readQueueSize: Int = 4
  val readVRFLatency: Int = 2
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
    IO(Decoupled(new VRFReadRequest(
      parameter.vrfParam.regNumBits,
      parameter.laneParam.vrfOffsetBits,
      parameter.instructionIndexBits
    )))
  }

  @public
  val readResult: Seq[UInt] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Input(UInt(parameter.datapathWidth.W)))
  }

  @public
  val writeRD: ValidIO[UInt] = IO(Valid(UInt(parameter.datapathWidth.W)))

  val instReg: MaskUnitInstReq = RegEnable(instReq.bits, 0.U.asTypeOf(instReq.bits), instReq.valid)
  val sew1H: UInt = UIntToOH(instReg.eew)(2, 0)
  val lastExecuteIndex: UInt = Mux1H(sew1H, Seq(3.U(2.W), 1.U(2.W), 0.U(2.W)))

  // calculate last group
  val readDataEew1H: UInt = sew1H
  val lastElementIndex: UInt = (instReg.vl - instReg.vl.orR)(parameter.laneParam.vlMaxBits - 2, 0)
  val laneNumberBits: Int = 1.max(log2Ceil(parameter.laneNumber))

  /** For an instruction, the last group is not executed by all lanes, here is the last group of the instruction xxxxx
   * xxx xx -> vsew = 0 xxxxxx xxx x -> vsew = 1 xxxxxxx xxx -> vsew = 2
   */
  val lastGroupForInstruction: UInt = Mux1H(
    readDataEew1H,
    Seq(
      lastElementIndex(parameter.laneParam.vlMaxBits - 2, laneNumberBits + 2),
      lastElementIndex(parameter.laneParam.vlMaxBits - 2, laneNumberBits + 1),
      lastElementIndex(parameter.laneParam.vlMaxBits - 2, laneNumberBits)
    )
  )

  /** Which lane the last element is in. */
  val lastLaneIndex: UInt = Mux1H(
    readDataEew1H,
    Seq(
      lastElementIndex(laneNumberBits + 2 - 1, 2),
      lastElementIndex(laneNumberBits + 1 - 1, 1),
      lastElementIndex(laneNumberBits - 1, 0)
    )
  )
  val lastGroupDataNeed: UInt = scanRightOr(UIntToOH(lastLaneIndex))

  // from decode
  val unitType: UInt = UIntToOH(instReg.decodeResult(Decoder.topUop)(4, 3))
  val readType: Bool = unitType(0)
  val gather16: Bool = instReg.decodeResult(Decoder.topUop) === "b00101".U

  val sewCorrection: UInt = Mux(gather16, 1.U, instReg.eew)

  val exeRequestQueue: Seq[Queue[MaskUnitExeReq]] = exeReq.map { req =>
    val queue: Queue[MaskUnitExeReq] = Module(new Queue(chiselTypeOf(req.bits), 4, flow = true))
    queue.io.enq.valid := req.valid
    req.ready := queue.io.enq.ready
    queue.io.enq.bits := req.bits
    queue
  }

  val exeReqReg: Seq[ValidIO[MaskUnitExeReq]] = Seq.tabulate(parameter.laneNumber) { _ =>
    RegInit(0.U.asTypeOf(Valid(new MaskUnitExeReq(parameter.laneParam))))
  }
  val lastGroup: Bool = exeReqReg.head.bits.groupCounter === lastGroupForInstruction
  // todo: mask
  val groupDataNeed: UInt = Mux(lastGroup, lastGroupDataNeed, (-1.S(parameter.laneNumber.W)).asUInt)
  // For read type, only sew * laneNumber data will be consumed each time
  // There will be a maximum of (dataPath * laneNumber) / (sew * laneNumber) times
  val executeIndex: UInt = RegInit(0.U(2.W))
  // The status of an execution
  // Each execution ends with executeIndex + 1
  val readGroupState: MaskUnitExecuteState = RegInit(0.U.asTypeOf(new MaskUnitExecuteState(parameter)))
  val executeStateValid: Bool = RegInit(false.B)

  def indexAnalysis(sewInt: Int)(elementIndex: UInt, vlmul: UInt, valid: Option[Bool]=None): Seq[UInt] = {
    val intLMULInput: UInt = (1.U << vlmul(1, 0)).asUInt
    val positionBits = (parameter.laneParam.vlMaxBits - 1) min elementIndex.getWidth
    val dataPosition = (elementIndex(positionBits - 1, 0) << sewInt).
      asUInt(positionBits - 1, 0)
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
    val accessLane   = if (parameter.laneNumber > 1) dataPosition(log2Ceil(parameter.laneNumber) + 1, 2) else 0.U(1.W)
    // 32 bit / group
    val dataGroup    = (dataPosition >> (log2Ceil(parameter.laneNumber) + 2)).asUInt
    val offsetWidth: Int = parameter.laneParam.vrfParam.vrfOffsetBits
    val offset            = dataGroup(offsetWidth - 1, 0)
    val accessRegGrowth   = (dataGroup >> offsetWidth).asUInt
    val decimalProportion = offset ## accessLane
    // 1/8 register
    val decimal           = decimalProportion(decimalProportion.getWidth - 1, 0.max(decimalProportion.getWidth - 3))

    /** elementIndex needs to be compared with vlMax(vLen * lmul /sew)
     * This calculation is too complicated We can change the angle.
     * Calculate the increment of the read register and compare it with lmul
     * to know whether the index exceeds vlMax.
     * vlmul needs to distinguish between integers and floating points
     */
    val overlap      =
      (vlmul(2) && decimal >= intLMULInput(3, 1)) ||
        (!vlmul(2) && accessRegGrowth >= intLMULInput)
    val elementValid = valid.getOrElse(true.B)
    val notNeedRead = overlap || !elementValid
    val reallyGrowth: UInt = changeUIntSize(accessRegGrowth, 3)
    Seq(accessMask, dataOffset, accessLane, offset, reallyGrowth, notNeedRead, elementValid)
  }

  val checkVec: Seq[Seq[UInt]] = Seq(0, 1, 2).map { sewInt =>
    val dataByte = 1 << sewInt
    // All data of this group
    val groupSourceData: UInt = VecInit(exeReqReg.map(_.bits.source1)).asUInt
    val groupSourceValid: UInt = VecInit(exeReqReg.map(_.valid)).asUInt
    // Single use length
    val singleWidth = dataByte * 8 * parameter.laneNumber
    // How many times will a set of data be executed?
    val executeTimes = (parameter.datapathWidth / 8) / dataByte
    // Which part is selected as the source data this time?
    val executeDataSelect1H = UIntToOH(executeIndex)(executeTimes -1, 0)
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
    val source = cutUInt(sourceSelect, dataWidth)
    // todo: mask
    val validVec = FillInterleaved(parameter.datapathWidth / dataWidth, validSelect)
    // read index check
    // (accessMask, dataOffset, accessLane, offset, reallyGrowth, overlap)
    val checkResultVec: Seq[Seq[UInt]] = source.zipWithIndex.map { case(s, i) =>
      indexAnalysis(sewInt)(s, instReg.vlmul, Some(validVec(i)))
    }
    val checkResult = checkResultVec.transpose.map(a => VecInit(a).asUInt)
    checkResult
  }
  val sewCorrection1H: UInt = UIntToOH(sewCorrection)(2, 0)
  val accessMaskSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_.head))
  val dataOffsetSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_(1)))
  val accessLaneSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_(2)))
  val offsetSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_(3)))
  val growthSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_(4)))
  val notReadSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_(5)))
  val elementValidSelect: UInt = Mux1H(sewCorrection1H, checkVec.map(_(6)))

  val readCrossBar: MaskUnitReadCrossBar = Module(new MaskUnitReadCrossBar(parameter))

  val maskUnitReadQueue: Seq[Queue[MaskUnitReadQueue]] = Seq.tabulate(parameter.laneNumber)(_ =>
    Module(new Queue(new MaskUnitReadQueue(parameter), readQueueSize))
  )

  // The queue waiting to read data. This queue contains other information about this group.
  val readWaitQueue: Queue[MaskUnitWaitReadQueue] =
    Module(new Queue(new MaskUnitWaitReadQueue(parameter), readQueueSize + readVRFLatency))

  // s0 pipe request from lane
  val laseExecuteGroupDeq: Bool = Wire(Bool())
  exeRequestQueue.zip(exeReqReg).foreach {case (req, reg) =>
    req.io.deq.ready := !reg.valid || laseExecuteGroupDeq
    when(req.io.deq.fire) {
      reg.bits := req.io.deq.bits
    }
    when(req.io.deq.fire ^ laseExecuteGroupDeq) {
      reg.valid := req.io.deq.fire
    }
  }

  val isLastExecuteGroup: Bool = executeIndex === lastExecuteIndex
  val allDataValid: Bool = exeReqReg.zipWithIndex.map{case (d, i) => d.valid || !groupDataNeed(i)}.reduce(_ && _)
  val canIssueGroup: Bool = allDataValid && readWaitQueue.io.enq.ready

  // select execute group
  val selectExecuteReq: Seq[ValidIO[MaskUnitReadReq]] = exeReqReg.zipWithIndex.map { case (_, index) =>
    val res: ValidIO[MaskUnitReadReq] = WireInit(0.U.asTypeOf(Valid(new MaskUnitReadReq(parameter))))
    res.bits.vs := instReg.vs2 + readGroupState.vsGrowth(index)
    res.bits.offset := readGroupState.readOffset(index)
    res.bits.readLane := readGroupState.accessLane(index)
    res.bits.dataOffset := cutUIntBySize(readGroupState.readDataOffset, parameter.laneNumber)(index)
    res.bits.requestIndex := index.U
    res.valid := canIssueGroup && !readGroupState.groupReadState(index) && readGroupState.needRead(index)
    res
  }

  // read arbitration
  readCrossBar.input.zip(selectExecuteReq).foreach { case (cross, req) =>
    cross.valid := req.valid
    cross.bits := req.bits
  }

  readCrossBar.output.zip(maskUnitReadQueue).foreach { case(source, sink) => sink.io.enq <> source }

  // read control register update
  val readFire: UInt = VecInit(readCrossBar.input.map(_.fire)).asUInt
  val anyReadFire: Bool = readFire.orR
  val readStateUpdate: UInt = readFire | readGroupState.groupReadState
  val groupReadFinish: Bool = readStateUpdate === readGroupState.needRead
  val readStateDeq: Bool = (anyReadFire && groupReadFinish) || (executeStateValid && readGroupState.needRead === 0.U)
  val executeStateEnq: Bool = allDataValid && (readStateDeq || !executeStateValid)
  when(anyReadFire) {
    readGroupState.groupReadState := readStateUpdate
  }

  when(readStateDeq ^ executeStateEnq) {
    executeStateValid := executeStateEnq
  }

  val executeIndexGrowth: UInt = (1.U << instReg.eew).asUInt
  when(executeStateEnq) {
    readGroupState.groupReadState := 0.U
    readGroupState.needRead := (~notReadSelect).asUInt
    readGroupState.elementValid := elementValidSelect
    readGroupState.accessLane := cutUIntBySize(accessLaneSelect, parameter.laneNumber)
    readGroupState.vsGrowth := cutUIntBySize(growthSelect, parameter.laneNumber)
    readGroupState.readOffset := offsetSelect
    readGroupState.groupCount := exeReqReg.head.bits.groupCounter
    readGroupState.executeIndex := executeIndex
    readGroupState.readDataOffset := dataOffsetSelect
    executeIndex := executeIndex + executeIndexGrowth
  }

  readWaitQueue.io.enq.valid := readStateDeq && readGroupState.elementValid.orR
  readWaitQueue.io.enq.bits.groupCounter := readGroupState.groupCount
  readWaitQueue.io.enq.bits.executeIndex := readGroupState.executeIndex
  readWaitQueue.io.enq.bits.sourceValid := readGroupState.elementValid
  readWaitQueue.io.enq.bits.needRead := readGroupState.needRead

  laseExecuteGroupDeq := Mux(readType, executeStateEnq, readWaitQueue.io.enq.fire) && isLastExecuteGroup

  // s1 read vrf
  val write1HPipe: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.laneNumber.W)))
  val pipeDataOffset: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(log2Ceil(parameter.datapathWidth / 8).W)))

  maskUnitReadQueue.zipWithIndex.foldLeft(0.U(parameter.laneNumber.W)) {
    case (sourceOccupied, (queue, index)) =>
      val sourceLane = UIntToOH(queue.io.deq.bits.writeIndex)
      val sourceFree = !(sourceLane & sourceOccupied).orR
      readChannel(index).valid := queue.io.deq.valid && sourceFree
      readChannel(index).bits.readSource := 2.U
      readChannel(index).bits.vs := queue.io.deq.bits.vs
      readChannel(index).bits.offset := queue.io.deq.bits.offset
      readChannel(index).bits.instructionIndex := instReg.instructionIndex
      queue.io.deq.ready := readChannel(index).ready && sourceFree

      // pipe read fire
      val pipeRead = Pipe(readChannel(index).fire, sourceLane, readVRFLatency)
      val pipeOffset = Pipe(readChannel(index).fire, queue.io.deq.bits.dataOffset, readVRFLatency)
      write1HPipe(index) := Mux(pipeRead.valid, pipeRead.bits, 0.U(parameter.laneNumber.W))
      pipeDataOffset(index) := pipeOffset.bits
      // Only valid, using ready will explode the timing
      sourceLane | sourceOccupied
  }

  // Processing read results
  val readData: Seq[ValidIO[UInt]] = Seq.tabulate(parameter.laneNumber) { index =>
    val res: ValidIO[UInt] = Wire(Valid(UInt(parameter.datapathWidth.W)))
    val readResultSelect = VecInit(write1HPipe.map(_(index))).asUInt
    val dataOffset: UInt = Mux1H(readResultSelect, pipeDataOffset)
    res.valid := readResultSelect.orR
    res.bits := Mux1H(readResultSelect, readResult) >> (dataOffset ## 0.U(3.W))
    res
  }

  /** todo: [[waitReadDataPipeReg]] enq && [[readWaitQueue]] enq **/
  // reg before execute
  val waitReadDataPipeReg: MaskUnitWaitReadQueue = RegInit(0.U.asTypeOf(new MaskUnitWaitReadQueue(parameter)))
  val waitReadData: Seq[UInt] = Seq.tabulate(parameter.laneNumber){ _ => RegInit(0.U(parameter.datapathWidth.W))}
  val waitReadSate: UInt = RegInit(0.U(parameter.laneNumber.W))
  val waiteReadStageValid: Bool = RegInit(false.B)

  // Process the data that needs to be written
  val dlen: Int = parameter.datapathWidth * parameter.laneNumber
  // Execute at most 4 times, each index represents 1/4 of dlen
  val eachIndexSize = dlen / 4
  val writeDataVec = Seq(0, 1, 2).map { sewInt =>
    val dataByte = 1 << sewInt
    val data = VecInit(Seq.tabulate(parameter.laneNumber){ laneIndex =>
      val dataElement: UInt = Wire(UInt((dataByte * 8).W))
      val dataIsRead = waitReadDataPipeReg.needRead(laneIndex)
      // todo: select vs1 when slide1
      dataElement := Mux(dataIsRead, waitReadData(laneIndex), 0.U)
      dataElement
    }).asUInt

    val shifterData = (data << (waitReadDataPipeReg.executeIndex ## 0.U(log2Ceil(eachIndexSize).W))).asUInt
    // align
    changeUIntSize(shifterData, dlen)
  }
  val writeData = Mux1H(sew1H, writeDataVec)

  val writeMaskVec: Seq[UInt] = Seq(0, 1, 2).map { sewInt =>
    val MaskMagnification = 1 << sewInt
    val mask = FillInterleaved(MaskMagnification, waitReadDataPipeReg.sourceValid)
    val shifterMask = (mask << (waitReadDataPipeReg.executeIndex ## 0.U(log2Ceil(eachIndexSize / 8).W))).asUInt
    // align
    changeUIntSize(shifterMask, dlen / 8)
  }
  val writeMask = Mux1H(sew1H, writeMaskVec)

  val writeRequest: Seq[MaskUnitExeResponse] = Seq.tabulate(parameter.laneNumber){ laneIndex =>
    val res: MaskUnitExeResponse = Wire(new MaskUnitExeResponse(parameter.laneParam))
    res.ffoByOther := false.B
    res.index := instReg.instructionIndex
    res.writeData.groupCounter := waitReadDataPipeReg.groupCounter
    res.writeData.data := cutUIntBySize(writeData, parameter.laneNumber)(laneIndex)
    res.writeData.mask := cutUIntBySize(writeMask, parameter.laneNumber)(laneIndex)
    res
  }
  val WillWriteLane: UInt = VecInit(cutUIntBySize(writeMask, parameter.laneNumber).map(_.orR)).asUInt

  // update waite read stage
  val waiteStageDeqValid: Bool = waiteReadStageValid && waitReadSate === waitReadDataPipeReg.needRead
  val waiteStageDeqReady: Bool = Wire(Bool())
  val waiteStageDeqFire: Bool = waiteStageDeqValid && waiteStageDeqReady

  val waiteStageEnqReady: Bool = !waiteReadStageValid || waiteStageDeqFire
  val waiteStageEnqFire: Bool = readWaitQueue.io.deq.valid && readWaitQueue.io.deq.valid

  readWaitQueue.io.deq.ready := waiteStageEnqReady

  when(waiteStageEnqFire) {
    waitReadDataPipeReg := readWaitQueue.io.deq.bits
  }

  when(waiteStageDeqFire ^ waiteStageEnqFire) {
    waiteReadStageValid := waiteStageEnqFire
  }

  waitReadData.zip(readData).foreach { case (reg, read) =>
    when(read.valid) {
      reg := read.bits
    }
  }
  val readResultValid: UInt = VecInit(readData.map(_.valid)).asUInt
  when(waiteStageEnqFire && readResultValid.orR){
    waitReadSate := readResultValid
  }.elsewhen(readResultValid.orR) {
    waitReadSate := waitReadSate | readResultValid
  }.elsewhen(waiteStageEnqFire) {
    waitReadSate := 0.U
  }

  // Determine whether the data is ready
  val executeEnqValid: Bool = waiteReadStageValid && waitReadDataPipeReg.needRead === waitReadSate

  // start execute
  val compressUnit: MaskCompress = Module(new MaskCompress(parameter))
  val reduceUnit: MaskReduce = Module(new MaskReduce(parameter))
  val extendUnit: MaskExtend = Module(new MaskExtend(parameter))

  // todo
  val source2: UInt = VecInit(exeReqReg.map(_.bits.source2)).asUInt
  val source1: UInt = VecInit(exeReqReg.map(_.bits.source1)).asUInt

  compressUnit.in.valid := executeEnqValid
  compressUnit.in.bits.vm := instReg.vm
  compressUnit.in.bits.eew := instReg.eew
  compressUnit.in.bits.uop := instReg.decodeResult(Decoder.topUop)
  compressUnit.in.bits.readFromScalar := instReg.readFromScala
  compressUnit.in.bits.source1 := source1
  compressUnit.in.bits.source2 := source2
  compressUnit.in.bits.groupCounter := waitReadDataPipeReg.groupCounter
  compressUnit.in.bits.lastCompress := lastGroup
  compressUnit.newInstruction := instReq.valid

  reduceUnit.in.valid := executeEnqValid && unitType(2)
  reduceUnit.in.bits.vm := instReg.vm
  reduceUnit.in.bits.eew := instReg.eew
  reduceUnit.in.bits.uop := instReg.decodeResult(Decoder.topUop)
  reduceUnit.in.bits.readVS1 := source1
  reduceUnit.in.bits.source2 := source2
  reduceUnit.in.bits.sourceValid := waitReadDataPipeReg.sourceValid
  reduceUnit.in.bits.groupCounter := waitReadDataPipeReg.groupCounter
  reduceUnit.in.bits.lastGroup := lastGroup
  reduceUnit.in.bits.vxrm := instReg.vxrm
  reduceUnit.in.bits.aluUop := instReg.decodeResult(Decoder.uop)
  reduceUnit.in.bits.sign := !instReg.decodeResult(Decoder.unsigned1)
  reduceUnit.newInstruction := instReq.valid

  extendUnit.in.eew := instReg.eew
  extendUnit.in.uop := instReg.decodeResult(Decoder.topUop)
  extendUnit.in.source2 := source2
  extendUnit.in.groupCounter := waitReadDataPipeReg.groupCounter

  val executeResult = Mux1H(
    unitType,
    Seq(
      source2,
      compressUnit.out.data,
      reduceUnit.out.bits.data,
      extendUnit.out
    )
  )

  // todo
  val executeMask: UInt = VecInit(exeReqReg.map(_.bits.source2)).asUInt
  val executeDeqCount: UInt = waitReadDataPipeReg.groupCounter

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
    unitType(2, 0) | unitType(3),
    Seq(
      executeEnqValid,
      compressUnit.out.compressValid,
      reduceUnit.out.valid,
    )
  )

  // mask unit write queue
  val writeQueue: Seq[Queue[MaskUnitExeResponse]] = Seq.tabulate(parameter.laneNumber) { _ =>
    Module(new Queue(
      new MaskUnitExeResponse(parameter.laneParam),
      maskUnitWriteQueueSize
    ))
  }

  writeQueue.zipWithIndex.foreach {case (queue, index) =>
    val readTypeWriteVrf: Bool = waiteStageDeqFire && WillWriteLane(index)
    queue.io.enq.valid := executeValid || readTypeWriteVrf
    queue.io.enq.bits.writeData.data := cutUInt(executeResult, parameter.datapathWidth)(index)
    queue.io.enq.bits.writeData.mask := cutUInt(executeMask, parameter.datapathWidth / 8)(index)
    queue.io.enq.bits.writeData.groupCounter := executeDeqCount
    queue.io.enq.bits.ffoByOther := false.B //todo
    queue.io.enq.bits.index := instReg.instructionIndex
    when(readTypeWriteVrf) {
      queue.io.enq.bits := writeRequest(index)
    }

    // write vrf
    val writePort = exeResp(index)
    queue.io.deq.ready := true.B
    writePort.valid := queue.io.deq.valid
    writePort.bits := queue.io.deq.bits
  }
  waiteStageDeqReady := writeQueue.zipWithIndex.map { case (queue, index) =>
    !WillWriteLane(index) || queue.io.enq.ready
  }.reduce(_ && _)
  writeRD <> DontCare
}
