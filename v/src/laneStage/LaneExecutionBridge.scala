package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle

class LaneExecuteRequest(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val src: Vec[UInt] = Vec(3, UInt(parameter.datapathWidth.W))
  val crossReadSource: Option[UInt] = Option.when(isLastSlot)(UInt((parameter.datapathWidth * 2).W))
  val bordersForMaskLogic: Bool = Bool()
  val mask: UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.groupNumberBits.W)
  val sSendResponse: Option[Bool] = Option.when(isLastSlot)(Bool())
}

class LaneExecuteResponse(parameter: LaneParameter, isLastSlot: Boolean) extends Bundle {
  val data: UInt = UInt(parameter.datapathWidth.W)
  val ffoIndex: UInt = UInt(log2Ceil(parameter.vLen / 8).W)
  val crossWriteData: Option[Vec[UInt]] = Option.when(isLastSlot)(Vec(2, UInt(parameter.datapathWidth.W)))
  val ffoSuccess: Option[Bool] = Option.when(isLastSlot)(Bool())
}

class LaneExecutionBridge(parameter: LaneParameter, isLastSlot: Boolean) extends Module {
  // request from lane slot
  val enqueue: DecoupledIO[LaneExecuteRequest] = IO(Flipped(Decoupled(new LaneExecuteRequest(parameter, isLastSlot))))
  // request from lane slot
  val dequeue: DecoupledIO[LaneExecuteResponse] = IO(Decoupled(new LaneExecuteResponse(parameter, isLastSlot)))
  // request to vfu
  val vfuRequest: DecoupledIO[SlotRequestToVFU] = IO(Decoupled(new SlotRequestToVFU(parameter)))
  // response from vfu
  val dataResponse: ValidIO[VFUResponseToSlot] = IO(Flipped(Valid(new VFUResponseToSlot(parameter))))
  val state: LaneState = IO(Input(new LaneState(parameter)))
  val ffoByOtherLanes: Bool = IO(Input(Bool()))
  val selfCompleted: Bool = IO(Input(Bool()))

  val decodeResult: DecodeBundle = state.decodeResult

  val executionRecord: ExecutionUnitRecord = RegInit(0.U.asTypeOf(new ExecutionUnitRecord(parameter)(isLastSlot)))

  val executeIndex1H: UInt = UIntToOH(executionRecord.executeIndex)

  // ffo success in current data group?
  val ffoSuccess: Option[Bool] = Option.when(isLastSlot)(RegInit(false.B))
  /** result of reduce instruction. */
  val reduceResult: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  // execution result from execute unit
  val executionResult = RegInit(0.U(parameter.datapathWidth.W))
  // todo: only slot 0?
  val ffoIndexReg: UInt = RegInit(0.U(log2Ceil(parameter.vLen / 8).W))
  val crossWriteMSB: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))
  val crossWriteLSB: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.datapathWidth.W)))

  /** mask format result for current `mask group` */
  val maskFormatResultForGroup: Option[UInt] = Option.when(isLastSlot)(RegInit(0.U(parameter.maskGroupWidth.W)))

  // state register
  val stageValidReg: Bool = RegInit(false.B)
  val sSendExecuteRequest: Bool = RegInit(true.B)
  val wExecuteResult: Bool = RegInit(true.B)
  val executeRequestStateValid: Bool = !sSendExecuteRequest
  val executeOver: Bool = sSendExecuteRequest && wExecuteResult
  dequeue.valid := executeOver && stageValidReg
  when(enqueue.fire ^ dequeue.fire) {
    stageValidReg := enqueue.fire
  }
  enqueue.ready := !stageValidReg || (executeOver && dequeue.ready)

  when(enqueue.fire) {
    executionRecord.crossReadVS2 := decodeResult(Decoder.crossRead) && !decodeResult(Decoder.vwmacc)
    executionRecord.bordersForMaskLogic := enqueue.bits.bordersForMaskLogic
    executionRecord.mask := enqueue.bits.mask
    executionRecord.source := enqueue.bits.src
    executionRecord.crossReadSource.foreach(_ := enqueue.bits.crossReadSource.get)
    executionRecord.sSendResponse.foreach(_ := enqueue.bits.sSendResponse.get)
    executionRecord.groupCounter := enqueue.bits.groupCounter
    sSendExecuteRequest := decodeResult(Decoder.dontNeedExecuteInLane)
    wExecuteResult := decodeResult(Decoder.dontNeedExecuteInLane)
    ffoSuccess.foreach(_ := false.B)
  }

  /** the byte-level mask of current execution.
   * sew match:
   * 0:
   * executeIndex match:
   * 0: 0001
   * 1: 0010
   * 2: 0100
   * 3: 1000
   * 1:
   * executeIndex(0) match:
   * 0: 0011
   * 1: 1100
   * 2:
   * 1111
   */
  val byteMaskForExecution = Mux1H(
    state.vSew1H(2, 0),
    Seq(
      executeIndex1H,
      executionRecord.executeIndex(1) ## executionRecord.executeIndex(1) ##
        !executionRecord.executeIndex(1) ## !executionRecord.executeIndex(1),
      15.U(4.W)
    )
  )

  /** the bit-level mask of current execution. */
  val bitMaskForExecution: UInt = FillInterleaved(8, byteMaskForExecution)

  def CollapseOperand(data: UInt, enable: Bool = true.B, sign: Bool = false.B): UInt = {
    val dataMasked: UInt = data & bitMaskForExecution
    val select: UInt = Mux(enable, state.vSew1H(2, 0), 4.U(3.W))
    // when sew = 0
    val collapse0 = Seq.tabulate(4)(i => dataMasked(8 * i + 7, 8 * i)).reduce(_ | _)
    // when sew = 1
    val collapse1 = Seq.tabulate(2)(i => dataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
    Mux1H(
      select,
      Seq(
        Fill(25, sign && collapse0(7)) ## collapse0,
        Fill(17, sign && collapse1(15)) ## collapse1,
        (sign && data(31)) ## data
      )
    )
  }

  // 有2 * sew 的操作数需要折叠
  def CollapseDoubleOperand(sign: Bool = false.B): UInt = {
    val doubleBitEnable = FillInterleaved(16, byteMaskForExecution)
    val doubleDataMasked: UInt = executionRecord.crossReadSource.get & doubleBitEnable
    val select: UInt = state.vSew1H(1, 0)
    // when sew = 0
    val collapse0 = Seq.tabulate(4)(i => doubleDataMasked(16 * i + 15, 16 * i)).reduce(_ | _)
    // when sew = 1
    val collapse1 = Seq.tabulate(2)(i => doubleDataMasked(32 * i + 31, 32 * i)).reduce(_ | _)
    Mux1H(
      select,
      Seq(
        Fill(16, sign && collapse0(15)) ## collapse0,
        collapse1
      )
    )
  }

  /** collapse the dual SEW size operand for cross read.
   * it can be vd or src2.
   */
  val doubleCollapse = Option.when(isLastSlot)(CollapseDoubleOperand(!decodeResult(Decoder.unsigned1)))

  /** src1 for the execution
   * src1 has three types: V, I, X.
   * only V type need to use [[CollapseOperand]]
   */
  val finalSource1 = CollapseOperand(
    // A will be updated every time it is executed, so you can only choose here
    Mux(
      decodeResult(Decoder.red) && !decodeResult(Decoder.maskLogic),
      reduceResult.getOrElse(0.U),
      executionRecord.source.head
    ),
    decodeResult(Decoder.vtype) && (!decodeResult(Decoder.red) || decodeResult(Decoder.maskLogic)),
    !decodeResult(Decoder.unsigned0)
  )

  /** src2 for the execution,
   * need to take care of cross read.
   */
  val finalSource2 = if (isLastSlot) {
    Mux(
      executionRecord.crossReadVS2,
      doubleCollapse.get,
      CollapseOperand(executionRecord.source(1), true.B, !decodeResult(Decoder.unsigned1))
    )
  } else {
    CollapseOperand(executionRecord.source(1), true.B, !decodeResult(Decoder.unsigned1))
  }

  /** source3 有两种：adc & ma, c等处理mask的时候再处理
   * two types of source3:
   * - multiplier accumulate
   * - the third input of add with carry
   *
   * this line only handle the first type.
   */
  val finalSource3: UInt = if (isLastSlot) {
    Mux(
      decodeResult(Decoder.vwmacc),
      doubleCollapse.get,
      CollapseOperand(executionRecord.source(2))
    )
  } else {
    CollapseOperand(executionRecord.source(2))
  }

  val maskAsInput = Mux1H(
    state.vSew1H(2, 0),
    Seq(
      (UIntToOH(executionRecord.executeIndex) & executionRecord.mask).orR,
      Mux(executionRecord.executeIndex(1), executionRecord.mask(1), executionRecord.mask(0)),
      executionRecord.mask(0)
    )
  )

  /** use mask to fix the case that `vl` is not in the multiple of [[parameter.datapathWidth]].
   * it will fill the LSB of mask to `0`, mask it to not execute those elements.
   */
  val lastGroupMask = scanRightOr(UIntToOH(state.csr.vl(parameter.datapathWidthBits - 1, 0))) >> 1

  val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt
  /** if [[executionRecord.bordersForMaskLogic]],
   * use [[lastGroupMask]] to mask the result otherwise use [[fullMask]]. */
  val maskCorrect = Mux(executionRecord.bordersForMaskLogic, lastGroupMask, fullMask)

  vfuRequest.bits.src := VecInit(Seq(finalSource1, finalSource2, finalSource3, maskCorrect))
  vfuRequest.bits.opcode := decodeResult(Decoder.uop)
  vfuRequest.bits.mask := Mux(
    decodeResult(Decoder.adder),
    maskAsInput && decodeResult(Decoder.maskSource),
    maskAsInput || !state.maskType
  )
  vfuRequest.bits.sign := !decodeResult(Decoder.unsigned1)
  vfuRequest.bits.reverse := decodeResult(Decoder.reverse)
  vfuRequest.bits.average := decodeResult(Decoder.average)
  vfuRequest.bits.saturate := decodeResult(Decoder.saturate)
  vfuRequest.bits.vxrm := state.csr.vxrm
  vfuRequest.bits.vSew := state.csr.vSew
  vfuRequest.bits.shifterSize := Mux1H(
    Mux(executionRecord.crossReadVS2, state.vSew1H(1, 0), state.vSew1H(2, 1)),
    Seq(false.B ## finalSource1(3), finalSource1(4, 3))
  ) ## finalSource1(2, 0)
  vfuRequest.bits.rem := decodeResult(Decoder.uop)(0)
  vfuRequest.bits.executeIndex := executionRecord.executeIndex
  vfuRequest.bits.popInit := reduceResult.getOrElse(0.U)
  vfuRequest.bits.groupIndex := executionRecord.groupCounter
  vfuRequest.bits.laneIndex := state.laneIndex
  vfuRequest.bits.complete := ffoByOtherLanes || selfCompleted
  vfuRequest.bits.maskType := state.maskType
  vfuRequest.bits.unitSelet.foreach(_ := decodeResult(Decoder.fpExecutionType))
  vfuRequest.bits.floatMul.foreach(_ := decodeResult(Decoder.floatMul))

  // from float csr
  vfuRequest.bits.roundingMode.foreach(_ := state.csr.vxrm)

  vfuRequest.valid := executeRequestStateValid

  /** select from VFU, send to [[executionResult]], [[crossWriteLSB]], [[crossWriteMSB]]. */
  val dataDequeue = dataResponse.bits.data

  val executeRequestFire: Bool = vfuRequest.fire

  val executeResponseFire: Bool = Mux(decodeResult(Decoder.multiCycle), dataResponse.valid, executeRequestFire)

  // mask reg for filtering
  val maskForFilter = FillInterleaved(4, state.maskNotMaskedElement) |
    Mux(enqueue.fire, enqueue.bits.mask, executionRecord.mask)
  // current one hot depends on execute index
  val currentOHForExecuteGroup: UInt = UIntToOH(executionRecord.executeIndex)
  // Remaining to be requested
  val remainder: UInt = maskForFilter & (~scanRightOr(currentOHForExecuteGroup)).asUInt
  // Finds the first unfiltered execution.
  val nextIndex1H: UInt = ffo(remainder)

  // There are no more left.
  val isLastRequestForThisGroup: Bool =
    Mux1H(state.vSew1H, Seq(!remainder.orR, !remainder(1, 0).orR, true.B))

  /** the next index to execute.
   *
   * @note Requests into this disguised execution unit are not executed on the spot
   * */
  val nextExecuteIndex: UInt = Mux1H(
    state.vSew1H(1, 0),
    Seq(
      OHToUInt(nextIndex1H),
      // Mux(remainder(0), 0.U, 2.U)
      !remainder(0) ## false.B
    )
  )

  // next execute index if data group change
  val nextExecuteIndexForNextGroup: UInt = Mux1H(
    state.vSew1H(1, 0),
    Seq(
      OHToUInt(ffo(maskForFilter)),
      !maskForFilter(0) ## false.B,
    )
  )

  // update execute index
  when(executeRequestFire || enqueue.fire) {
    executionRecord.executeIndex := Mux(enqueue.fire, nextExecuteIndexForNextGroup, nextExecuteIndex)
  }

  when(executeRequestFire && isLastRequestForThisGroup) {
    sSendExecuteRequest := true.B
  }

  // execute response finish
  val responseFinish: Bool = Mux(
    decodeResult(Decoder.multiCycle),
    executeResponseFire && sSendExecuteRequest,
    executeRequestFire && isLastRequestForThisGroup
  )

  when(responseFinish) {
    wExecuteResult := true.B
  }

  val multiCycleWriteIndexLatch: UInt =
    RegEnable(dataResponse.bits.executeIndex, 0.U(2.W), dataResponse.valid)
  val multiCycleWriteIndex = Mux(dataResponse.valid, dataResponse.bits.executeIndex, multiCycleWriteIndexLatch)
  /** the index to write to VRF in [[parameter.dataPathByteWidth]].
   * for long latency pipe, the index will follow the pipeline.
   */
  val writeIndex = Mux(
    decodeResult(Decoder.multiCycle),
    multiCycleWriteIndex,
    executionRecord.executeIndex
  )

  val writeIndex1H = UIntToOH(writeIndex)

  /** VRF byte level mask */
  val writeMaskInByte = Mux1H(
    state.vSew1H(2, 0),
    Seq(
      writeIndex1H,
      writeIndex(1) ## writeIndex(1) ## !writeIndex(1) ## !writeIndex(1),
      "b1111".U(4.W)
    )
  )

  /** VRF bit level mask */
  val writeMaskInBit: UInt = FillInterleaved(8, writeMaskInByte)

  /** output of execution unit need to align to VRF in bit level(used in dynamic shift)
   * TODO: fix me
   */
  val dataOffset: UInt = writeIndex ## 0.U(3.W)

  // TODO: this is a dynamic shift logic, but if we switch to parallel execution unit, we don't need it anymore.
  val executeResult = (dataDequeue << dataOffset).asUInt(parameter.datapathWidth - 1, 0)

  // execute 1,2,4 times based on SEW, only write VRF when 32 bits is ready.
  val resultUpdate: UInt = (executeResult & writeMaskInBit) | (executionResult & (~writeMaskInBit).asUInt)

  // update execute result
  when(executeResponseFire) {
    // update the [[executionResult]]
    executionResult := resultUpdate

    // the find first one instruction is finished in this lane
    ffoSuccess.foreach(_ := dataResponse.bits.ffoSuccess)
    when(dataResponse.bits.ffoSuccess && !selfCompleted) {
      ffoIndexReg := executionRecord.groupCounter ## Mux1H(
        state.vSew1H,
        Seq(
          executionRecord.executeIndex ## dataResponse.bits.data(2, 0),
          executionRecord.executeIndex(1) ## dataResponse.bits.data(3, 0),
          dataResponse.bits.data(4, 0)
        )
      )
    }

    // update cross-lane write data
    /** sew:
     * 0:
     * executeIndex:
     * 0: mask = 0011, head
     * 1: mask = 1100, head
     * 2: mask = 0011, tail
     * 3: mask = 1100, tail
     * 1:
     * executeIndex:
     * 0: mask = 1111, head
     * 2: mask = 1111, tail
     *
     * 2: not valid in SEW = 2
     */
    if (isLastSlot) {
      when(executionRecord.executeIndex(1)) {
        crossWriteMSB.foreach { crossWriteData =>
          // update tail
          crossWriteData :=
            Mux(
              state.csr.vSew(0),
              dataDequeue(parameter.datapathWidth - 1, parameter.halfDatapathWidth),
              Mux(
                executionRecord.executeIndex(0),
                dataDequeue(parameter.halfDatapathWidth - 1, 0),
                crossWriteData(parameter.datapathWidth - 1, parameter.halfDatapathWidth)
              )
            ) ## Mux(
              !executionRecord.executeIndex(0) || state.csr.vSew(0),
              dataDequeue(parameter.halfDatapathWidth - 1, 0),
              crossWriteData(parameter.halfDatapathWidth - 1, 0)
            )
        }
      }.otherwise {
        crossWriteLSB.foreach { crossWriteData =>
          crossWriteData :=
            Mux(
              state.csr.vSew(0),
              dataDequeue(parameter.datapathWidth - 1, parameter.halfDatapathWidth),
              Mux(
                executionRecord.executeIndex(0),
                dataDequeue(parameter.halfDatapathWidth - 1, 0),
                crossWriteData(parameter.datapathWidth - 1, parameter.halfDatapathWidth)
              )
            ) ## Mux(
              !executionRecord.executeIndex(0) || state.csr.vSew(0),
              dataDequeue(parameter.halfDatapathWidth - 1, 0),
              crossWriteData(parameter.halfDatapathWidth - 1, 0)
            )
        }
      }
    }
  }

  // update mask result
  if (isLastSlot) {
    val current1HInGroup = Mux1H(
      state.vSew1H(2, 0),
      Seq(
        // 32bit, 4 bit per data group, it will had 8 data groups -> executeIndex1H << 4 * groupCounter(2, 0)
        executeIndex1H << (executionRecord.groupCounter(2, 0) ## 0.U(2.W)),
        // 2 bit per data group, it will had 16 data groups -> executeIndex1H << 2 * groupCounter(3, 0)
        (executionRecord.executeIndex(1) ## !executionRecord.executeIndex(1)) <<
          (executionRecord.groupCounter(3, 0) ## false.B),
        // 1 bit per data group, it will had 32 data groups -> executeIndex1H << 1 * groupCounter(4, 0)
        1.U << executionRecord.groupCounter(4, 0)
      )
    ).asUInt

    /** update value for [[maskFormatResultUpdate]],
     * it comes from ALU.
     */
    val elementMaskFormatResult: UInt = Mux(dataResponse.bits.adderMaskResp, current1HInGroup, 0.U)

    /** update value for [[maskFormatResultForGroup]] */
    val maskFormatResultUpdate: UInt = maskFormatResultForGroup.get | elementMaskFormatResult

    val updateMaskResult: Option[Bool] = executionRecord.sSendResponse.map(!_ && dequeue.fire)

    // update `maskFormatResultForGroup`
    when(executeResponseFire || updateMaskResult.get) {
      maskFormatResultForGroup.foreach(_ := Mux(executeResponseFire, maskFormatResultUpdate, 0.U))
    }
    // masked element don't update 'reduceResult'
    val updateReduceResult = (state.maskNotMaskedElement || maskAsInput) && executeResponseFire
    // update `reduceResult`
    when(updateReduceResult || updateMaskResult.get) {
      reduceResult.get := Mux(updateReduceResult && decodeResult(Decoder.red), dataDequeue, 0.U)
    }
  }

  if (isLastSlot) {
    dequeue.bits.data := Mux(
      decodeResult(Decoder.maskDestination),
      maskFormatResultForGroup.get,
      Mux(
        decodeResult(Decoder.red),
        reduceResult.get,
        executionResult
      )
    )
  } else {
    dequeue.bits.data := executionResult
  }
  dequeue.bits.ffoIndex := ffoIndexReg
  dequeue.bits.crossWriteData.foreach(_ := VecInit((crossWriteLSB ++ crossWriteMSB).toSeq))
  dequeue.bits.ffoSuccess.foreach(_ := ffoSuccess.get)
}
