// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3.util.experimental.decode._
import tilelink.{TLBundle, TLBundleParameter, TLChannelAParameter, TLChannelDParameter}

object VParameter {
  implicit def rwP: upickle.default.ReadWriter[VParameter] = upickle.default.macroRW
}

/**
  * @param xLen XLEN
  * @param vLen VLEN
  * @param datapathWidth width of data path, can be 32 or 64, decides the memory bandwidth.
  * @param laneNumber how many lanes in the vector processor
  * @param physicalAddressWidth width of memory bus address width
  * @param chainingSize how many instructions can be chained
  *                     TODO: make it a val, not parameter.
  *
  * @note
  * Chaining:
  *  - limited by VRF Memory Port. TODO: add bank in VRF.
  *  - the chaining size is decided by logic units. if the bandwidth is limited by the logic units, we should increase lane size.
  * TODO: sort a machine-readable chaining matrix for test case generation.
  */
case class VParameter(
                       xLen:                 Int,
                       vLen:                 Int,
                       datapathWidth:        Int,
                       laneNumber:           Int,
                       physicalAddressWidth: Int,
                       chainingSize:         Int,
                       vrfWriteQueueSize:    Int,
                       fpuEnable:            Boolean,
                       vfuInstantiateParameter: VFUInstantiateParameter)
    extends SerializableModuleParameter {

  /** TODO: make it a parameter. */
  val instructionQueueSize: Int = 8

  /** TODO: make it a parameter.
    *
    * xLen data bits.
    *
    * @note
    * bandwidth = dataPathWidth * memoryBankSize
    */
  val memoryBankSize: Int = 2

  /** minimum of sew, defined in spec. */
  val sewMin: Int = 8

  /** TODO: uarch docs for mask(v0) group and normal vrf groups.
    *
    * The state machine in LSU.mshr will handle `dataPathWidth` bits data for each group.
    * For each lane, it will cache corresponding data in `dataPathWidth` bits.
    *
    * The reason of this design, we cannot fanout all wires in mask to LSU.
    * So we group them into `maskGroupSize` groups, and LSU will handle them one by one in cycle.
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

  /** data group in lane:
    * for each instruction, it will operate on `vLen * lmulMax` databits,
    * we split them to different lanes, and partitioned into groups.
    */
  val groupNumberMax: Int = vLen * lmulMax / datapathWidth / laneNumber

  /** the hardware width of [[groupNumberMax]]. */
  val groupNumberMaxBits: Int = log2Ceil(groupNumberMax)

  /** Used in memory bundle parameter. */
  val memoryDataWidth: Int = datapathWidth

  /** LSU MSHR Size, from experience, we use 3 for 2R1W，this is also limited by the number of memory ports.
    * TODO: in vector design, there are some instructions which have 3R1W, this may decrease performance. we need perf it.
    */
  val lsuMSHRSize: Int = 3

  /** TODO: make it configurable for perf. */
  val lsuVRFWriteQueueSize: Int = 4

  /** width of tilelink source id
    * log2(maskGroupWidth) for offset
    * 3 for segment index
    * 2 for 3 MSHR(2 read + 1 write)
    */
  val sourceWidth: Int = {
    log2Ceil(maskGroupWidth) + // offset of mask group
      3 + // segment index, this is decided by spec.
      log2Ceil(lsuMSHRSize) // 3 MSHR(2 read + 1 write)
  }
  // todo
  val cacheLineSize = 32

  /** for TileLink `size` element.
    * for most of the time, size is 2'b10, which means 4 bytes.
    * EEW = 8bit, indexed LSU will access 1 byte.(bandwidth is 1/4).
    * TODO: perf it.
    */
  val sizeWidth: Int = log2Ceil(log2Ceil(cacheLineSize))

  /** for TileLink `mask` element. */
  val maskWidth: Int = memoryDataWidth / 8

  /** parameter for TileLink. */
  val tlParam: TLBundleParameter = TLBundleParameter(
    a = TLChannelAParameter(physicalAddressWidth, sourceWidth, memoryDataWidth, sizeWidth, maskWidth),
    b = None,
    c = None,
    d = TLChannelDParameter(sourceWidth, sourceWidth, memoryDataWidth, sizeWidth),
    e = None
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
      vfuInstantiateParameter = vfuInstantiateParameter
    )
  def lsuParam: LSUParam = LSUParam(
    datapathWidth,
    chainingSize,
    vLen,
    laneNumber,
    xLen,
    sourceWidth,
    sizeWidth,
    maskWidth,
    memoryBankSize,
    lsuMSHRSize,
    lsuVRFWriteQueueSize,
    cacheLineSize,
    tlParam
  )
  def vrfParam: VRFParam = VRFParam(vLen, laneNumber, datapathWidth, chainingSize)
  require(xLen == datapathWidth)
  def adderParam: LaneAdderParam = LaneAdderParam(datapathWidth)
}

/** Top of Vector processor:
  * couple to Rocket Core;
  * instantiate LSU, Decoder, Lane, CSR, Instruction Queue.
  * The logic of [[V]] contains the Vector Sequencer and Mask Unit.
  */
class V(val parameter: VParameter) extends Module with SerializableModule[VParameter] {

  /** request from CPU.
    * because the interrupt and exception of previous instruction is unpredictable,
    * and the `kill` logic in Vector processor is too high,
    * thus the request should come from commit stage to avoid any interrupt or excepiton.
    */
  val request: DecoupledIO[VRequest] = IO(Flipped(Decoupled(new VRequest(parameter.xLen))))

  /** response to CPU. */
  val response: ValidIO[VResponse] = IO(Valid(new VResponse(parameter.xLen)))

  /** CSR interface from CPU. */
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(parameter.laneParam.vlMaxBits)))

  /** from CPU LSU, store buffer is cleared, memory can observe memory requests after this is asserted. */
  val storeBufferClear: Bool = IO(Input(Bool()))

  /** TileLink memory ports. */
  val memoryPorts: Vec[TLBundle] = IO(Vec(parameter.memoryBankSize, parameter.tlParam.bundle()))

  /** the LSU Module */
  val lsu:    LSU = Module(new LSU(parameter.lsuParam))
  val decode: VectorDecoder = Module(new VectorDecoder(parameter.fpuEnable))

  // TODO: cover overflow
  // TODO: uarch doc about the order of instructions
  val instructionCounter:     UInt = RegInit(0.U(parameter.instructionIndexBits.W))
  val nextInstructionCounter: UInt = instructionCounter + 1.U
  when(request.fire) { instructionCounter := nextInstructionCounter }

  // todo: handle waw
  val responseCounter:     UInt = RegInit(0.U(parameter.instructionIndexBits.W))
  val nextResponseCounter: UInt = responseCounter + 1.U
  when(response.fire) { responseCounter := nextResponseCounter }

  // maintained a 1 depth queue for VRequest.
  // TODO: directly maintain a `ready` signal
  /** register to latch instruction. */
  val requestReg: ValidIO[InstructionPipeBundle] = RegInit(0.U.asTypeOf(Valid(new InstructionPipeBundle(parameter))))

  /** maintain a [[DecoupleIO]] for [[requestReg]]. */
  val requestRegDequeue = Wire(Decoupled(new VRequest(parameter.xLen)))
  // latch instruction, csr, decode result and instruction index to requestReg.
  when(request.fire) {
    // The LSU only need to know the instruction, and don't need information from decoder.
    // Thus we latch the request here, and send it to LSU.
    requestReg.bits.request := request.bits
    requestReg.bits.decodeResult := decode.decodeResult
    requestReg.bits.csr := csrInterface
    requestReg.bits.instructionIndex := instructionCounter
    requestReg.bits.vdIsV0 := request.bits.instruction(11, 7) === 0.U
  }
  // 0 0 -> don't update
  // 0 1 -> update to false
  // 1 0 -> update to true
  // 1 1 -> don't update
  requestReg.valid := Mux(request.fire ^ requestRegDequeue.fire, request.fire, requestReg.valid)
  // ready when requestReg is free or it will be free in this cycle.
  request.ready := !requestReg.valid || requestRegDequeue.ready
  // manually maintain a queue for requestReg.
  requestRegDequeue.bits := requestReg.bits.request
  requestRegDequeue.valid := requestReg.valid
  // TODO: decode the 7 bits in LSB, to get the instruction type.
  //       we only need to use it to find if it's a load/store instruction.
  decode.decodeInput := (request.bits.instruction >> 12) ## request.bits.instruction(6)

  /** alias to [[requestReg.bits.decodeResult]], it is commonly used. */
  val decodeResult: DecodeBundle = requestReg.bits.decodeResult
  // 这是当前正在mask unit 里面的那一条指令的csr信息,用来计算mask unit的控制信号
  val csrRegForMaskUnit: CSRInterface = RegInit(0.U.asTypeOf(new CSRInterface(parameter.laneParam.vlMaxBits)))
  val vSewOHForMask: UInt = UIntToOH(csrRegForMaskUnit.vSew)(2, 0)

  // TODO: no valid here
  // TODO: these should be decoding results
  val isLoadStoreType: Bool = !requestRegDequeue.bits.instruction(6) && requestRegDequeue.valid
  val isStoreType:     Bool = !requestRegDequeue.bits.instruction(6) && requestRegDequeue.bits.instruction(5)
  val maskType:        Bool = !requestRegDequeue.bits.instruction(25)
  // lane 只读不执行的指令
  val readOnlyInstruction: Bool = decodeResult(Decoder.readOnly)
  // 只进mask unit的指令
  val maskUnitInstruction: Bool = (decodeResult(Decoder.slid) || decodeResult(Decoder.mv))
  val skipLastFromLane: Bool = isLoadStoreType || maskUnitInstruction || readOnlyInstruction
  val instructionValid: Bool = requestReg.bits.csr.vl > requestReg.bits.csr.vStart

  // TODO: these should be decoding results
  /** load store that don't read offset. */
  val noOffsetReadLoadStore: Bool = isLoadStoreType && (!requestRegDequeue.bits.instruction(26))
  val indexTypeLS:           Bool = isLoadStoreType && requestRegDequeue.bits.instruction(26)

  val vSew1H: UInt = UIntToOH(requestReg.bits.csr.vSew)
  val source1Extend: UInt = Mux1H(
    vSew1H(2, 0),
    Seq(
      Fill(parameter.datapathWidth - 8, requestRegDequeue.bits.src1Data(7) && !decodeResult(Decoder.unsigned0))
        ## requestRegDequeue.bits.src1Data(7, 0),
      Fill(parameter.datapathWidth - 16, requestRegDequeue.bits.src1Data(15) && !decodeResult(Decoder.unsigned0))
        ## requestRegDequeue.bits.src1Data(15, 0),
      requestRegDequeue.bits.src1Data(31, 0)
    )
  )
  /** src1 from scalar core is a signed number. */
  val src1IsSInt: Bool = !requestReg.bits.decodeResult(Decoder.unsigned0)
  val imm: UInt = requestReg.bits.request.instruction(19, 15)
  // todo: spec 10.1: imm 默认是 sign-extend,但是有特殊情况
  val immSignExtend: UInt = Fill(16, imm(4) && (vSew1H(2) || src1IsSInt)) ##
    Fill(8, imm(4) && (vSew1H(1) || vSew1H(2) || src1IsSInt)) ##
    Fill(3, imm(4)) ## imm

  /** duplicate v0 for mask */
  val v0: Vec[UInt] = RegInit(VecInit(Seq.fill(parameter.maskGroupSize)(0.U(parameter.maskGroupWidth.W))))
  // TODO: uarch doc for the regroup
  val regroupV0: Seq[Seq[UInt]] = Seq(4, 2, 1).map { groupSize =>
    v0.map { element =>
      element.asBools
        .grouped(groupSize)
        .toSeq
        .map(VecInit(_).asUInt)
        .grouped(parameter.laneNumber)
        .toSeq
        .transpose
        .map(seq => VecInit(seq).asUInt)
    }.transpose.map(VecInit(_).asUInt)
  }.transpose

  /** which slot the instruction is entering */
  val instructionToSlotOH: UInt = Wire(UInt(parameter.chainingSize.W))

  /** synchronize signal from each lane, for mask units.(ffo) */
  val laneSynchronize: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))

  /** all lanes are synchronized. */
  val synchronized: Bool = WireDefault(false.B)

  /** for mask unit that need to access VRF from lanes,
    * use this signal to indicate it is finished access VRF(but instruction might not finish).
    */
  val maskUnitReadOnlyFinish: Bool = WireDefault(false.B)

  /** last slot is committing. */
  val lastSlotCommit: Bool = Wire(Bool())

  /** for each lane, for instruction slot,
    * when asserted, the corresponding instruction is finished.
    */
  val instructionFinished: Vec[Vec[Bool]] = Wire(Vec(parameter.laneNumber, Vec(parameter.chainingSize, Bool())))

  // todo: 把lsu也放decode里去
  val maskUnitType: Bool = decodeResult(Decoder.maskUnit) && requestRegDequeue.bits.instruction(6)
  val maskDestination = decodeResult(Decoder.maskDestination)
  val unOrderType: Bool = decodeResult(Decoder.unOrderWrite)
  /** Special instructions which will be allocate to the last slot.
    * - mask unit
    * - Lane <-> Top has data exchange(top might forward to LSU.)
    *   TODO: move to normal slots(add `offset` fields)
    * - unordered instruction(slide)
    * - vd is v0
    */
  val specialInstruction: Bool = decodeResult(Decoder.special) || requestReg.bits.vdIsV0
  val busClear: Bool = Wire(Bool())
  val writeQueueClearVec = Wire(Vec(parameter.laneNumber, Bool()))
  val writeQueueClear: Bool = !writeQueueClearVec.asUInt.orR

  /** designed for unordered instruction(slide),
    * it doesn't go to lane, it has RAW hazzard.
    */
  val instructionRAWReady: Bool = Wire(Bool())
  val allSlotFree:         Bool = Wire(Bool())

  // mask Unit 与lane交换数据
  val writeType: VRFWriteRequest = new VRFWriteRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfParam.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  )
  val maskUnitWrite:       ValidIO[VRFWriteRequest] = Wire(Valid(writeType))
  val maskUnitWriteVec:    Vec[ValidIO[VRFWriteRequest]] = Wire(Vec(3, Valid(writeType)))
  val maskWriteLaneSelect: Vec[UInt] = Wire(Vec(3, UInt(parameter.laneNumber.W)))
  // 默认是head
  val maskUnitWriteSelect: UInt = Mux1H(maskUnitWriteVec.map(_.valid), maskWriteLaneSelect)
  maskUnitWriteVec.foreach(_ := DontCare)
  maskUnitWrite := Mux1H(maskUnitWriteVec.map(_.valid), maskUnitWriteVec)
  val writeSelectMaskUnit: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))
  val maskUnitWriteReady:  Bool = writeSelectMaskUnit.asUInt.orR

  // read
  val readType: VRFReadRequest = new VRFReadRequest(
    parameter.vrfParam.regNumBits,
    parameter.vrfParam.vrfOffsetBits,
    parameter.instructionIndexBits
  )
  val maskUnitRead:       ValidIO[VRFReadRequest] = Wire(Valid(readType))
  val maskUnitReadVec:    Vec[ValidIO[VRFReadRequest]] = Wire(Vec(3, Valid(readType)))
  val maskReadLaneSelect: Vec[UInt] = Wire(Vec(3, UInt(parameter.laneNumber.W)))
  val maskUnitReadSelect: UInt = Mux1H(maskUnitReadVec.map(_.valid), maskReadLaneSelect)
  maskUnitRead := Mux1H(maskUnitReadVec.map(_.valid), maskUnitReadVec)
  val readSelectMaskUnit: Vec[Bool] = Wire(Vec(parameter.laneNumber, Bool()))
  val maskUnitReadReady = readSelectMaskUnit.asUInt.orR
  val laneReadResult: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.datapathWidth.W)))
  val WARRedResult:   ValidIO[UInt] = RegInit(0.U.asTypeOf(Valid(UInt(parameter.datapathWidth.W))))
  // mask unit 最后的写
  val lastMaskUnitWrite: Bool = Wire(Bool())

  // gather read state
  val gatherOverlap: Bool = Wire(Bool())
  val gatherNeedRead: Bool = requestRegDequeue.valid && decodeResult(Decoder.gather) &&
    !decodeResult(Decoder.vtype) && !gatherOverlap
  val gatherReadFinish: Bool =
    RegEnable(
      !requestRegDequeue.fire,
      false.B,
      (RegNext(maskUnitReadReady) && gatherNeedRead) || requestRegDequeue.fire
    )
  val gatherReadDataOffset: UInt = Wire(UInt(5.W))
  val gatherData:           UInt = Mux(gatherOverlap, 0.U, (WARRedResult.bits >> gatherReadDataOffset).asUInt)

  /** data that need to be compute at top. */
  val data: Vec[ValidIO[UInt]] = RegInit(
    VecInit(Seq.fill(parameter.laneNumber)(0.U.asTypeOf(Valid(UInt(parameter.datapathWidth.W)))))
  )
  val maskDataForCompress: UInt = RegInit(0.U(parameter.datapathWidth.W))
  val dataClear:           Bool = WireDefault(false.B)
  val completedVec:        Vec[Bool] = RegInit(VecInit(Seq.fill(parameter.laneNumber)(false.B)))
  val selectffoIndex:      ValidIO[UInt] = Wire(Valid(UInt(parameter.xLen.W)))

  /** for find first one, need to tell the lane with higher index `1` . */
  val completedLeftOr: UInt = (scanLeftOr(completedVec.asUInt) << 1).asUInt(parameter.laneNumber - 1, 0)
  // 按指定的sew拼成 {laneNumer * dataPathWidth} bit, 然后根据sew选择出来
  val sortedData: UInt = Mux1H(
    vSewOHForMask,
    Seq(4, 2, 1).map { groupSize =>
      VecInit(data.map { element =>
        element.bits.asBools //[x] * 32 eg: sew = 1
          .grouped(groupSize) //[x, x] * 16
          .toSeq
          .map(VecInit(_).asUInt) //[xx] * 16
      }.transpose.map(VecInit(_).asUInt)).asUInt //[x*16] * 16 -> x * 256
    }
  )
  // 把已经排过序的数据重新分给各个lane
  val regroupData: Vec[UInt] = VecInit(Seq.tabulate(parameter.laneNumber) { laneIndex =>
    sortedData(
      laneIndex * parameter.datapathWidth + parameter.datapathWidth - 1,
      laneIndex * parameter.datapathWidth
    )
  })
  val dataResult: ValidIO[UInt] = RegInit(0.U.asTypeOf(Valid(UInt(parameter.datapathWidth.W))))
  // todo: viota & compress & reduce

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

    /** the execution is finished.
      * (but there might still exist some data in the ring.)
      */
    val laneAndLSUFinish: Bool = control.endTag.asUInt.andR

    /** lsu is finished when report bits matched corresponding slot
      * lsu send `lastReport` to [[V]], this check if the report contains this slot.
      * this signal is used to update the `control.endTag`.
      */
    val lsuFinished: Bool = ohCheck(lsu.lastReport, control.record.instructionIndex, parameter.chainingSize)
    // instruction is allocated to this slot.
    when(requestRegDequeue.fire && instructionToSlotOH(index)) {
      // instruction metadata
      control.record.instructionIndex := requestReg.bits.instructionIndex
      // TODO: remove
      control.record.isLoadStore := isLoadStoreType
      // control signals
      control.state.idle := false.B
      control.state.wLast := false.B
      control.state.sCommit := false.B
      // two different initial states for endTag:
      // for load/store instruction, use the last bit to indicate whether it is the last instruction
      // for other instructions, use MSB to indicate whether it is the last instruction
      control.endTag := VecInit(Seq.fill(parameter.laneNumber)(skipLastFromLane) :+ !isLoadStoreType)
    }
      // state machine starts here
      .otherwise {
        when(laneAndLSUFinish) {
          control.state.wLast := !control.record.hasCrossWrite || (busClear && writeQueueClear)
        }
        // TODO: execute first, then commit
        when(responseCounter === control.record.instructionIndex && response.fire) {
          control.state.sCommit := true.B
        }
        val maskUnitEnd: Bool = (mvToVRF.map(!_ || !writeQueueClearVec.head) ++
          Seq(control.state.sMaskUnitExecution, control.state.sCommit)).reduce(_ && _)
        // TODO: remove `control.state.sMaskUnitExecution`
        when(maskUnitEnd) {
          control.state.idle := true.B
        }

        // endTag update logic from slot and lsu to instructionFinished.
        control.endTag.zip(instructionFinished.map(_(index)) :+ lsuFinished).foreach {
          case (d, c) => d := d || c
        }
      }
    // logic like mask&reduce will be put to last slot
    // TODO: review later
    if (index == (parameter.chainingSize - 1)) {
      val feedBack:       UInt = RegInit(0.U(parameter.laneNumber.W))
      val executeCounter: UInt = RegInit(0.U((log2Ceil(parameter.laneNumber) + 1).W))
      // mask destination时这两count都是以写vrf为视角
      val writeBackCounter: UInt = RegInit(0.U(log2Ceil(parameter.laneNumber).W))
      val groupCounter:     UInt = RegInit(0.U(parameter.groupNumberMaxBits.W))
      val iotaCount:        UInt = RegInit(0.U((parameter.laneParam.vlMaxBits - 1).W))
      val maskTypeInstruction = RegInit(false.B)
      val vd = RegInit(0.U(5.W))
      val vs1 = RegInit(0.U(5.W))
      val vs2 = RegInit(0.U(5.W))
      val rs1 = RegInit(0.U(parameter.xLen.W))
      val vm = RegInit(false.B)
      val unOrderTypeInstruction = RegInit(false.B)
      val decodeResultReg = RegInit(0.U.asTypeOf(decodeResult))
      val gather: Bool = decodeResultReg(Decoder.gather)
      // for slid
      val elementIndexCount = RegInit(0.U(parameter.laneParam.vlMaxBits.W))
      val compressWriteCount = RegInit(0.U(parameter.laneParam.vlMaxBits.W))
      val nextElementIndex: UInt = elementIndexCount + 1.U
      val firstElement = elementIndexCount === 0.U
      val lastElement: Bool = nextElementIndex === csrRegForMaskUnit.vl
      val updateMaskIndex = WireDefault(false.B)
      when(updateMaskIndex) { elementIndexCount := nextElementIndex }
      // 特殊的指令,会阻止 wLast 后把 sExecute 拉回来, 因为需要等待读后才写
      val mixedUnit: Bool = Wire(Bool())
      // slid & gather & extend
      val slidUnitIdle: Bool = RegInit(true.B)
      // compress & iota
      val iotaUnitIdle: Bool = RegInit(true.B)
      val maskUnitIdle = slidUnitIdle && iotaUnitIdle
      val reduce = decodeResultReg(Decoder.red)
      val popCount = decodeResultReg(Decoder.popCount)
      val extend = decodeResultReg(Decoder.extend)
      // first type instruction
      val firstLane = ffo(completedVec.asUInt)
      val firstLaneIndex: UInt = OHToUInt(firstLane)(2, 0)
      response.bits.rd.valid := lastSlotCommit && decodeResultReg(Decoder.targetRd)
      response.bits.rd.bits := vd
      selectffoIndex.valid := decodeResultReg(Decoder.ffo)
      selectffoIndex.bits := Mux(
        !completedVec.asUInt.orR,
        -1.S(parameter.xLen.W).asUInt,
        Mux1H(
          firstLane,
          // 3: firstLaneIndex.width
          data.map(i => i.bits(parameter.xLen - 1 - 3, 5) ## firstLaneIndex ## i.bits(4, 0))
        )
      )

      /** vlmax = vLen * (2**lmul) / (2 ** sew * 8)
        * = (vLen / 8) * 2 ** (lmul - sew)
        * = vlb * 2 ** (lmul - sew)
        * lmul <- (-3, -2, -1, 0 ,1, 2, 3)
        * sew <- (0, 1, 2)
        * lmul - sew <- [-5, 3]
        * 选择信号 +5 -> lmul - sew + 5 <- [0, 8]
        */
      def largeThanVLMax(source: UInt, advance: Bool = false.B, csrInput:CSRInterface): Bool = {
        val vlenLog2 = log2Ceil(parameter.vLen) // 10
        val cut =
          if (source.getWidth >= vlenLog2) source(vlenLog2 - 1, vlenLog2 - 9)
          else (0.U(vlenLog2.W) ## source)(vlenLog2 - 1, vlenLog2 - 9)
        // 9: lmul - sew 的可能值的个数
        val largeList: Vec[Bool] = Wire(Vec(9, Bool()))
        cut.asBools.reverse.zipWithIndex.foldLeft(advance) {
          case (a, (b, i)) =>
            largeList(i) := a
            a || b
        }
        val extendVlmul = csrInput.vlmul(2) ## csrInput.vlmul
        val selectWire = UIntToOH(5.U(4.W) + extendVlmul - csrInput.vSew)(8, 0).asBools.reverse
        Mux1H(selectWire, largeList)
      }
      // 算req上面的分开吧
      val gatherWire =
        Mux(decodeResult(Decoder.itype), requestRegDequeue.bits.instruction(19, 15), requestRegDequeue.bits.src1Data)
      val gatherAdvance = (gatherWire >> log2Ceil(parameter.vLen)).asUInt.orR
      gatherOverlap := largeThanVLMax(gatherWire, gatherAdvance, requestReg.bits.csr)
      val slotValid = !control.state.idle
      val storeAfterSlide = isStoreType && (requestRegDequeue.bits.instruction(11, 7) === vd)
      instructionRAWReady := !((unOrderTypeInstruction && slotValid &&
        // slid 类的会比执行得慢的指令快(div),会修改前面的指令的source
        ((vd === requestRegDequeue.bits.instruction(24, 20)) ||
          (vd === requestRegDequeue.bits.instruction(19, 15)) ||
          storeAfterSlide ||
          // slid 类的会比执行得快的指令慢(mv),会被后来的指令修改 source2
          (vs2 === requestRegDequeue.bits.instruction(11, 7))) ||
        ((unOrderType || requestReg.bits.vdIsV0) && !allSlotFree)) || (vd === 0.U && maskType && slotValid))
      when(requestRegDequeue.fire && instructionToSlotOH(index)) {
        writeBackCounter := 0.U
        groupCounter := 0.U
        executeCounter := 0.U
        elementIndexCount := 0.U
        compressWriteCount := 0.U
        iotaCount := 0.U
        slidUnitIdle := !((decodeResult(Decoder.slid) || (decodeResult(Decoder.gather) && decodeResult(Decoder.vtype))
          || decodeResult(Decoder.extend)) && instructionValid)
        iotaUnitIdle := !((decodeResult(Decoder.compress) || decodeResult(Decoder.iota)) && instructionValid)
        vd := requestRegDequeue.bits.instruction(11, 7)
        vs1 := requestRegDequeue.bits.instruction(19, 15)
        vs2 := requestRegDequeue.bits.instruction(24, 20)
        vm := requestRegDequeue.bits.instruction(25)
        rs1 := requestRegDequeue.bits.src1Data
        decodeResultReg := decodeResult
        csrRegForMaskUnit := requestReg.bits.csr
        // todo: decode need execute
        control.state.sMaskUnitExecution := !maskUnitType
        maskTypeInstruction := maskType && !decodeResult(Decoder.maskSource)
        completedVec.foreach(_ := false.B)
        WARRedResult.valid := false.B
        unOrderTypeInstruction := unOrderType
      }.elsewhen(control.state.wLast && maskUnitIdle && !mixedUnit) {
        // 如果真需要执行的lane会wScheduler,不会提前发出last确认
        control.state.sMaskUnitExecution := true.B
      }
      when(laneSynchronize.asUInt.orR) {
        feedBack := feedBack | laneSynchronize.asUInt
      }.elsewhen(lastSlotCommit) {
        feedBack := 0.U
      }
      // 执行
      // mask destination write
      /** 对于mask destination 类型的指令需要特别注意两种不对齐
        *   第一种是我们以 32(dataPatWidth) * 8(laneNumber) 为一个组, 但是我们vl可能不对齐一整个组
        *   第二种是 32(dataPatWidth) 的时候对不齐
        * vl假设最大1024,相应的会有11位的vl
        *   xxx xxx xxxxx
        */
      val dataPathMisaligned = csrRegForMaskUnit.vl(parameter.dataPathWidthBits - 1, 0).orR
      val groupMisaligned = csrRegForMaskUnit
        .vl(parameter.dataPathWidthBits + log2Ceil(parameter.laneNumber) - 1, parameter.dataPathWidthBits)
        .orR

      /**
        * 我们需要计算最后一次写的 [[writeBackCounter]] & [[groupCounter]]
        *   lastGroupCounter = vl(10, 8) - !([[dataPathMisaligned]] || [[groupMisaligned]])
        *   lastExecuteCounter = vl(7, 5) - ![[dataPathMisaligned]]
        */
      val lastGroupCounter: UInt =
        csrRegForMaskUnit.vl(
          parameter.laneParam.vlMaxBits - 1,
          parameter.dataPathWidthBits + log2Ceil(parameter.laneNumber)
        ) - !(dataPathMisaligned || groupMisaligned)
      val lastExecuteCounter: UInt =
        csrRegForMaskUnit.vl(
          parameter.dataPathWidthBits + log2Ceil(parameter.laneNumber) - 1,
          parameter.dataPathWidthBits
        ) - !dataPathMisaligned
      val lastGroup = groupCounter === lastGroupCounter
      val lastExecute = lastGroup && writeBackCounter === lastExecuteCounter
      val lastExecuteForGroup = writeBackCounter.andR
      // 计算正写的这个lane是不是在边界上
      val endOH = UIntToOH(csrRegForMaskUnit.vl(parameter.dataPathWidthBits - 1, 0))
      val border = lastExecute && dataPathMisaligned
      val lastGroupMask = scanRightOr(endOH(parameter.datapathWidth - 1, 1))
      val mvType = decodeResultReg(Decoder.mv)
      val readMv = mvType && decodeResultReg(Decoder.targetRd)
      val writeMv = mvType && !decodeResultReg(Decoder.targetRd) &&
        csrRegForMaskUnit.vl > csrRegForMaskUnit.vStart
      mvToVRF.foreach(d => when(requestRegDequeue.fire){d := writeMv})
      // 读后写中的读
      val needWAR = maskTypeInstruction || border || reduce || readMv
      val skipLaneData: Bool = decodeResultReg(Decoder.mv)
      mixedUnit := writeMv || readMv
      maskReadLaneSelect.head := UIntToOH(writeBackCounter)
      maskWriteLaneSelect.head := maskReadLaneSelect.head
      maskUnitReadVec.head.valid := false.B
      maskUnitReadVec.head.bits.vs := Mux(readMv, vs2, Mux(reduce, vs1, vd))
      maskUnitReadVec.head.bits.offset := groupCounter
      maskUnitRead.bits.instructionIndex := control.record.instructionIndex
      val readResultSelectResult = Mux1H(RegNext(maskUnitReadSelect), laneReadResult)
      // 把mask选出来
      val maskSelect = v0(groupCounter ## writeBackCounter)
      val fullMask: UInt = (-1.S(parameter.datapathWidth.W)).asUInt

      /** 正常全1
        * mask：[[maskSelect]]
        * border: [[lastGroupMask]]
        * mask && border: [[maskSelect]] & [[lastGroupMask]]
        */
      val maskCorrect: UInt = Mux(maskTypeInstruction, maskSelect, fullMask) &
        Mux(border, lastGroupMask, fullMask)
      // mask
      val sew1HCorrect = Mux(decodeResultReg(Decoder.widenReduce), vSewOHForMask ## false.B, vSewOHForMask)
      // 写的data
      val writeData = (WARRedResult.bits & (~maskCorrect).asUInt) | (regroupData(writeBackCounter) & maskCorrect)
      val writeMask = Mux(sew1HCorrect(2) || !reduce, 15.U, Mux(sew1HCorrect(1), 3.U, 1.U))
      maskUnitWriteVec.head.valid := false.B
      maskUnitWriteVec.head.bits.vd := vd
      maskUnitWriteVec.head.bits.offset := groupCounter
      maskUnitWriteVec.head.bits.data := Mux(writeMv, rs1, Mux(reduce, dataResult.bits, writeData))
      maskUnitWriteVec.head.bits.last := control.state.wLast || reduce
      maskUnitWriteVec.head.bits.instructionIndex := control.record.instructionIndex

      val maskUnitReadVrf = maskUnitReadReady && maskUnitReadVec.map(_.valid).reduce(_ || _)
      when(RegNext(maskUnitReadVrf)) {
        WARRedResult.bits := readResultSelectResult
        WARRedResult.valid := true.B
      }
      // alu start
      val aluInput1 = Mux(
        executeCounter === 0.U,
        Mux(
          needWAR,
          WARRedResult.bits & FillInterleaved(8, writeMask),
          0.U
        ),
        dataResult.bits
      )
      val aluInput2 = Mux1H(UIntToOH(executeCounter), data.map(d => Mux(d.valid, d.bits, 0.U)))
      // red alu instance
      val adder:     LaneAdder = Module(new LaneAdder(parameter.adderParam))
      val logicUnit: LaneLogic = Module(new LaneLogic(parameter.datapathWidth))

      val sign = !decodeResultReg(Decoder.unsigned1)
      val adderRequest = Wire(LaneAdderParam(parameter.datapathWidth).inputBundle)
      adderRequest.src := VecInit(
        Seq(
          (aluInput1(parameter.datapathWidth - 1) && sign) ## aluInput1,
          (aluInput2(parameter.datapathWidth - 1) && sign) ## aluInput2
        )
      )
      // popCount 在top视为reduce add
      adderRequest.opcode := Mux(popCount, 0.U, decodeResultReg(Decoder.uop))
      adderRequest.sign := sign
      adderRequest.mask := false.B
      adderRequest.reverse := false.B
      adderRequest.average := false.B
      adderRequest.saturate := false.B
      adderRequest.vxrm := csrRegForMaskUnit.vxrm
      adderRequest.vSew := csrRegForMaskUnit.vSew
      adder.requestIO.bits := adderRequest.asTypeOf(adder.requestIO.bits)
      adder.requestIO.valid := DontCare
      adder.responseIO.ready := DontCare

      logicUnit.req.src := VecInit(Seq(aluInput1, aluInput2))
      logicUnit.req.opcode := decodeResultReg(Decoder.uop)

      // reduce resultSelect
      val reduceResult = Mux(
        decodeResultReg(Decoder.adder) || popCount,
        adder.responseIO.bits.asTypeOf(parameter.adderParam.outputBundle).data,
        logicUnit.resp
      )
      val aluOutPut = Mux(reduce, reduceResult, 0.U)
      // slid & gather unit
      val slideUp = decodeResultReg(Decoder.topUop)(1)
      val slide1 = decodeResultReg(Decoder.topUop)(0) && decodeResultReg(Decoder.slid)

      /** special uop 里面编码了extend的信息：
        * specialUop(1,0): 倍率
        * specialUop(2)：是否是符号
        */
      val extendSourceSew: Bool = (csrRegForMaskUnit.vSew >> decodeResultReg(Decoder.topUop)(1, 0))(0)
      val extendSign:      Bool = decodeResultReg(Decoder.topUop)(2)
      // gather 相关的控制
      val gather16: Bool = decodeResultReg(Decoder.gather16)
      val maskUnitEEW = Mux(gather16, 1.U, Mux(extend, extendSourceSew, csrRegForMaskUnit.vSew))
      val maskUnitEEW1H: UInt = UIntToOH(maskUnitEEW)
      val maskUnitByteEnable = maskUnitEEW1H(2) ## maskUnitEEW1H(2) ## maskUnitEEW1H(2, 1).orR ## true.B
      val maskUnitBitEnable = FillInterleaved(8, maskUnitByteEnable)
      maskUnitWriteVec.head.bits.mask := Mux(writeMv, maskUnitByteEnable, writeMask)
      // log2(dataWidth * laneNumber / 8)
      val maskUnitDataOffset = (elementIndexCount << maskUnitEEW).asUInt(4, 0) ## 0.U(3.W)
      val maskUnitData = ((VecInit(data.map(_.bits)).asUInt >> maskUnitDataOffset).asUInt & maskUnitBitEnable)(
        parameter.datapathWidth - 1,
        0
      )

      val compareWire = Mux(decodeResultReg(Decoder.slid), rs1, maskUnitData)
      val compareAdvance: Bool = (rs1 >> log2Ceil(parameter.vLen)).asUInt.orR
      val compareResult:  Bool = largeThanVLMax(compareWire, compareAdvance, csrRegForMaskUnit)
      // 正在被gather使用的数据在data的那个组里
      val gatherDataSelect = UIntToOH(maskUnitDataOffset(7, 5))
      val dataTail = Mux1H(UIntToOH(maskUnitEEW)(1, 0), Seq(3.U(2.W), 2.U(2.W)))
      val lastElementForData = gatherDataSelect.asBools.last && maskUnitDataOffset(4, 3) === dataTail
      val maskUnitDataReady: Bool = (gatherDataSelect & VecInit(data.map(_.valid)).asUInt).orR
      // 正在被gather使用的数据是否就绪了
      val isSlide = !(gather || extend)
      val slidUnitDataReady: Bool = maskUnitDataReady || isSlide
      val compressDataReady = maskUnitDataReady || !(decodeResultReg(Decoder.compress) || decodeResultReg(Decoder.iota))
      // slid 先用状态机
      val idle :: sRead :: sWrite :: Nil = Enum(3)
      val slideState = RegInit(idle)
      val readState = slideState === sRead

      // slid 的立即数是0扩展的
      val slidSize = Mux(slide1, 1.U, Mux(decodeResultReg(Decoder.itype), vs1, rs1))
      // todo: 这里是否有更好的处理方式
      val slidSizeLSB = slidSize(parameter.laneParam.vlMaxBits - 1, 0)
      // down +
      // up -
      val directionSelection = Mux(slideUp, (~slidSizeLSB).asUInt, slidSizeLSB)
      val slideReadIndex = elementIndexCount + directionSelection + slideUp
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
        val sewInput = csrInput.vSew
        val sewOHInput = UIntToOH(csrInput.vSew)(2, 0)
        val intLMULInput:UInt = (1.U << csrInput.vlmul(1, 0)).asUInt
        val dataPosition = (elementIndex(parameter.laneParam.vlMaxBits - 2, 0) << sewInput)
          .asUInt(parameter.laneParam.vlMaxBits - 2, 0)
        val accessMask = Mux1H(
          sewOHInput(2, 0),
          Seq(
            UIntToOH(dataPosition(1, 0)),
            FillInterleaved(2, UIntToOH(dataPosition(1))),
            15.U(4.W)
          )
        )
        // 数据起始位置在32bit(暂时只32)中的偏移,由于数据会有跨lane的情况,融合的优化时再做
        val dataOffset = (dataPosition(1) && sewOHInput(1, 0).orR) ## (dataPosition(0) && sewOHInput(0)) ## 0.U(3.W)
        val accessLane = dataPosition(log2Ceil(parameter.laneNumber) + 1, 2)
        // 32 bit / group
        val dataGroup = (dataPosition >> (log2Ceil(parameter.laneNumber) + 2)).asUInt
        val offset = dataGroup(1, 0)
        val accessRegGrowth = (dataGroup >> 2).asUInt

        /** elementIndex 需要与vlMax比较, vLen * lmul /sew 这个计算太复杂了
          * 我们可以换一个角度,计算读寄存器的增量与lmul比较,就能知道下标是否超vlMax了
          * vlmul 需要区分整数与浮点
          */
        val overlap =
          (csrInput.vlmul(2) && (offset ## accessLane(2)) >= intLMULInput(3, 1)) ||
            (!csrInput.vlmul(2) && accessRegGrowth >= intLMULInput)
        accessRegGrowth >= csrInput.vlmul
        val reallyGrowth = accessRegGrowth(2, 0)
        (accessMask, dataOffset, accessLane, offset, reallyGrowth, overlap)
      }
      val srcOverlap: Bool = !decodeResultReg(Decoder.itype) && (rs1 >= csrRegForMaskUnit.vl)
      // rs1 >= vlMax
      val srcOversize = !decodeResultReg(Decoder.itype) && !slide1 && compareResult
      val signBit = Mux1H(
        vSewOHForMask,
        readIndex(parameter.laneParam.vlMaxBits - 1, parameter.laneParam.vlMaxBits - 3).asBools.reverse
      )
      // 对于up来说小于offset的element是不变得的
      val slideUpUnderflow = slideUp && !slide1 && (signBit || srcOverlap)
      val elementActive: Bool = v0.asUInt(elementIndexCount) || vm
      val slidActive = elementActive && (!slideUpUnderflow || !decodeResultReg(Decoder.slid))
      // index >= vlMax 是写0
      val overlapVlMax: Bool = !slideUp && (signBit || srcOversize)
      // select csr
      val csrSelect = Mux(control.state.idle, requestReg.bits.csr, csrRegForMaskUnit)
      // slid read
      val (_, readDataOffset, readLane, readOffset, readGrowth, lmulOverlap) = indexAnalysis(readIndex, csrSelect)
      gatherReadDataOffset := readDataOffset
      val readOverlap = lmulOverlap || overlapVlMax
      val skipRead = readOverlap || (gather && compareResult) || extend
      val maskUnitWriteVecFire1 = maskUnitReadVec(1).valid && maskUnitReadReady
      val readFireNext1: Bool = RegNext(maskUnitWriteVecFire1)
      val gatherTryToRead = gatherNeedRead && !VecInit(lsu.vrfReadDataPorts.map(_.valid)).asUInt.orR
      maskUnitReadVec(1).valid := (readState || gatherTryToRead) && !readFireNext1
      maskUnitReadVec(1).bits.vs := Mux(readState, vs2, requestRegDequeue.bits.instruction(24, 20)) + readGrowth
      maskUnitReadVec(1).bits.offset := readOffset
      maskReadLaneSelect(1) := UIntToOH(readLane)
      // slid write, vlXXX: 用element index 算出来的
      val (vlMask, vlDataOffset, vlLane, vlOffset, vlGrowth, _) = indexAnalysis(elementIndexCount)
      val writeState = slideState === sWrite
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

      /**
        * vd 的值有4种：
        *   1. 用readIndex读出来的vs2的值
        *   1. 0
        *   1. slide1 时插进来的rs1
        *   1. extend 的值
        */
      val slidWriteData = Mux1H(
        Seq((!(readOverlap || selectRS1 || extend)) || (gather && !compareResult), selectRS1, extend),
        Seq(slidReadData, (rs1 << vlDataOffset).asUInt(parameter.xLen - 1, 0), extendData)
      )
      maskUnitWriteVec(1).valid := writeState && slidActive
      maskUnitWriteVec(1).bits.vd := vd + vlGrowth
      maskUnitWriteVec(1).bits.offset := vlOffset
      maskUnitWriteVec(1).bits.mask := vlMask
      maskUnitWriteVec(1).bits.data := slidWriteData
      maskUnitWriteVec(1).bits.last := lastElement
      maskUnitWriteVec(1).bits.instructionIndex := control.record.instructionIndex
      maskWriteLaneSelect(1) := UIntToOH(vlLane)
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
        when(readFireNext1) {
          slideState := sWrite
        }
      }
      when(writeState) {
        when(maskUnitWriteReady || !slidActive) {
          when(lastElement) {
            slideState := idle
            slidUnitIdle := true.B
            when(gather || extend) {
              synchronized := true.B
              dataClear := true.B
              maskUnitReadOnlyFinish := true.B
            }
          }.otherwise {
            when(lastElementForData && (gather || extend)) {
              synchronized := true.B
              dataClear := true.B
              slideState := idle
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
      val compressState = RegInit(idle1)
      val compressStateIdle = compressState === idle1
      val compressStateRead = compressState === sReadMask
      val compressStateWrite = compressState === sWrite1

      // compress 用vs1当mask,需要先读vs1
      val readCompressMaskNext = RegNext(maskUnitReadReady && compressStateRead)
      when(readCompressMaskNext) {
        maskDataForCompress := readResultSelectResult
      }

      // 处理 iota
      val iotaDataOffset:  UInt = elementIndexCount(7, 0)
      val lastDataForIota: Bool = iotaDataOffset.andR
      val iotaData = VecInit(data.map(_.bits)).asUInt(iotaDataOffset)
      val iota = decodeResultReg(Decoder.iota)

      val maskUnitReadFire2: Bool = maskUnitReadVec(2).valid && maskUnitReadReady
      val readFireNext2 = RegNext(maskUnitReadFire2)

      /** 计算需要读的mask的相关
        * elementIndexCount -> 11bit
        * 只会访问单寄存器
        * elementIndexCount(4, 0)做为32bit内的offset
        * elementIndexCount(7, 5)作为lane的选择
        * elementIndexCount(9, 8)作为offset
        */
      // compress read
      maskUnitReadVec(2).valid := compressStateRead && !readFireNext2
      maskUnitReadVec(2).bits.vs := vs1
      maskUnitReadVec(2).bits.offset := elementIndexCount(9, 8)
      maskReadLaneSelect(2) := UIntToOH(elementIndexCount(7, 5))
      // val lastElementForMask: Bool = elementIndexCount(4, 0).andR
      val maskForCompress: Bool = maskDataForCompress(elementIndexCount(4, 0))

      // compress vm=0 是保留的
      val skipWrite = !Mux(decodeResultReg(Decoder.compress), maskForCompress, elementActive)
      val dataGroupTailForCompressUnit: Bool = Mux(iota, lastDataForIota, lastElementForData)

      // 计算compress write的位置信息
      val (compressMask, compressDataOffset, compressLane, compressOffset, compressGrowth, _) =
        indexAnalysis(compressWriteCount)
      val compressWriteData = (maskUnitData << compressDataOffset).asUInt
      val iotaWriteData = (iotaCount << vlDataOffset).asUInt
      // compress write
      maskUnitWriteVec(2).valid := compressStateWrite && !skipWrite
      maskUnitWriteVec(2).bits.vd := vd + Mux(iota, vlGrowth, compressGrowth)
      maskUnitWriteVec(2).bits.offset := Mux(iota, vlOffset, compressOffset)
      maskUnitWriteVec(2).bits.mask := Mux(iota, vlMask, compressMask)
      maskUnitWriteVec(2).bits.data := Mux(iota, iotaWriteData, compressWriteData)
      maskUnitWriteVec(2).bits.last := lastElement
      maskUnitWriteVec(2).bits.instructionIndex := control.record.instructionIndex
      maskWriteLaneSelect(2) := UIntToOH(Mux(iota, vlLane, compressLane))

      // 跳状态机
      // compress每组数据先读mask
      val firstState = Mux(iota, sWrite1, sReadMask)
      when(compressStateIdle && (!iotaUnitIdle) && compressDataReady) {
        compressState := firstState
      }

      when(compressStateRead && readFireNext2) {
        compressState := sWrite1
      }

      when(compressStateWrite) {
        when(maskUnitWriteReady || skipWrite) {
          when(!skipWrite) {
            compressWriteCount := compressWriteCount + 1.U
            iotaCount := iotaCount + iotaData
          }
          when(lastElement) {
            compressState := idle
            iotaUnitIdle := true.B
            synchronized := true.B
            dataClear := true.B
            maskUnitReadOnlyFinish := true.B
          }.otherwise {
            when(dataGroupTailForCompressUnit) {
              synchronized := true.B
              dataClear := true.B
              compressState := idle
            }
            updateMaskIndex := true.B
          }
        }
      }
      // reduce 也需要flush vrf
      lastMaskUnitWrite := (lastElement && !maskUnitIdle) ||
      ((control.state.wLast || reduce || lastExecute) && maskUnitWriteVec.head.valid && maskUnitWriteReady)

      // alu end
      val maskOperation = decodeResultReg(Decoder.maskLogic)
      val lastGroupDataWaitMask = scanRightOr(UIntToOH(lastExecuteCounter))
      val dataMask = Mux(maskOperation && lastGroup, lastGroupDataWaitMask, -1.S(parameter.laneNumber.W).asUInt)
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
        val readFinish = WARRedResult.valid || !needWAR
        val readDataSign = Mux1H(vSewOHForMask(2, 0), Seq(WARRedResult.bits(7), WARRedResult.bits(15), WARRedResult.bits(31)))
        when(readFinish) {
          when(readMv) {
            control.state.sMaskUnitExecution := true.B
            // signExtend for vmv.x.s
            dataResult.bits := Mux(vSewOHForMask(2), WARRedResult.bits(31, 16), Fill(16, readDataSign)) ##
              Mux(vSewOHForMask(0), Fill(8, readDataSign), WARRedResult.bits(15, 8)) ##
              WARRedResult.bits(7, 0)

          }.otherwise {
            executeCounter := executeCounter + 1.U
            dataResult.bits := aluOutPut
          }
        }
        val executeFinish: Bool =
          (executeCounter(log2Ceil(parameter.laneNumber)) || !(reduce || popCount)) && maskUnitIdle
        val schedulerWrite = decodeResultReg(Decoder.maskDestination) || (reduce && !popCount) || writeMv
        // todo: decode
        val groupSync = decodeResultReg(Decoder.ffo)
        // 写回
        when(readFinish && (executeFinish || writeMv)) {
          maskUnitWriteVec.head.valid := schedulerWrite
          when(maskUnitWriteReady || !schedulerWrite) {
            WARRedResult.valid := false.B
            writeBackCounter := writeBackCounter + schedulerWrite
            when(lastExecuteForGroup || lastExecute || reduce || groupSync || writeMv || popCount) {
              synchronized := true.B
              dataClear := true.B
              when(lastExecuteForGroup) {
                executeForLastLaneFire := true.B
                groupCounter := groupCounter + 1.U
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
  val allLaneReady: Bool = laneReady.asUInt.andR
  // TODO: review later
  // todo: 把scheduler的反馈也加上,lsu有更高的优先级

  /** the index type of instruction is finished.
    * let LSU to kill the lane slot.
    * todo: delete?
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
  val gather16: Bool = decodeResult(Decoder.gather16)
  val vSewSelect: UInt = Mux(
    isLoadStoreType,
    requestRegDequeue.bits.instruction(13, 12),
    Mux(
      decodeResult(Decoder.nr) || decodeResult(Decoder.maskLogic),
      2.U,
      Mux(gather16, 1.U, Mux(decodeResult(Decoder.extend), extendDataEEW, requestReg.bits.csr.vSew))
    )
  )

  val evlForLane: UInt = Mux(
    decodeResult(Decoder.nr),
    // evl for Whole Vector Register Move ->  vs1 * (vlen / datapathWidth)
    (requestRegDequeue.bits.instruction(17, 15) +& 1.U) ## 0.U(log2Ceil(parameter.vLen / parameter.datapathWidth).W),
    requestReg.bits.csr.vl
  )

  /** instantiate lanes.
    * TODO: move instantiate to top of class.
    */
  val laneVec: Seq[Lane] = Seq.tabulate(parameter.laneNumber) { index =>
    // TODO: use D/I
    val lane: Lane = Module(new Lane(parameter.laneParam))
    // lane.laneRequest.valid -> requestRegDequeue.ready -> lane.laneRequest.ready -> lane.laneRequest.bits
    // TODO: this is harmful for PnR design, since it broadcast ready singal to each lanes, which will significantly
    //       reduce the scalability for large number of lanes.
    lane.laneRequest.valid := requestRegDequeue.fire && !noOffsetReadLoadStore && !maskUnitInstruction
    // hard wire
    lane.laneRequest.bits.instructionIndex := requestReg.bits.instructionIndex
    lane.laneRequest.bits.decodeResult := decodeResult
    lane.laneRequest.bits.vs1 := requestRegDequeue.bits.instruction(19, 15)
    lane.laneRequest.bits.vs2 := requestRegDequeue.bits.instruction(24, 20)
    lane.laneRequest.bits.vd := requestRegDequeue.bits.instruction(11, 7)
    lane.laneRequest.bits.segment := requestRegDequeue.bits.instruction(31, 29)
    lane.laneRequest.bits.loadStoreEEW := requestRegDequeue.bits.instruction(13, 12)
    // if the instruction is vi and vx type of gather, gather from rs2 with mask VRF read channel from one lane,
    // and broadcast to all lanes.
    lane.laneRequest.bits.readFromScalar := source1Select

    // TODO: these are from decoder?
    // let record in VRF to know there is a load store instruction.
    // 是不经过lane的lsu指令: noOffsetReadLoadStore
    // 然后握手fire了:
    //  valid: requestReg.valid
    //  ready: slotReady && lsu.request.ready && instructionRAWReady
    lane.laneRequest.bits.LSUFire := slotReady && requestReg.valid && noOffsetReadLoadStore &&
      lsu.request.ready && instructionRAWReady
    lane.laneRequest.bits.loadStore := isLoadStoreType
    // let record in VRF to know there is a store instruction.
    lane.laneRequest.bits.store := isStoreType
    // let lane know if this is a special instruction, which need group-level synchronization between lane and [[V]]
    lane.laneRequest.bits.special := specialInstruction
    // mask type instruction.
    lane.laneRequest.bits.mask := maskType
    laneReady(index) := lane.laneRequest.ready

    lane.csrInterface := requestReg.bits.csr
    // index type EEW Decoded in the instruction
    lane.csrInterface.vSew := vSewSelect
    lane.csrInterface.vl := evlForLane
    lane.laneIndex := index.U

    // - LSU request next offset of group
    // - all lane are synchronized
    // - the index type of instruction is finished.
    lane.laneResponseFeedback.valid := lsu.lsuOffsetRequest || synchronized || completeIndexInstruction
    // - the index type of instruction is finished.
    // - for find first one.
    lane.laneResponseFeedback.bits.complete :=
      completeIndexInstruction ||
      completedLeftOr(index) ||
      maskUnitReadOnlyFinish
    // tell lane which
    lane.laneResponseFeedback.bits.instructionIndex := slots.last.record.instructionIndex

    // lsu 优先会有死锁:
    // vmadc, v1, v2, 1 (vl=17) -> 需要先读后写
    // vse32.v v1, (a0) -> 依赖上一条,但是会先发出read
    // 读 lane
    lane.vrfReadAddressChannel.valid := lsu.vrfReadDataPorts(index).valid ||
    (maskUnitRead.valid && maskUnitReadSelect(index))
    lane.vrfReadAddressChannel.bits :=
      Mux(maskUnitRead.valid, maskUnitRead.bits, lsu.vrfReadDataPorts(index).bits)
    lsu.vrfReadDataPorts(index).ready := lane.vrfReadAddressChannel.ready && !maskUnitRead.valid
    readSelectMaskUnit(index) :=
      lane.vrfReadAddressChannel.ready && maskUnitReadSelect(index)
    laneReadResult(index) := lane.vrfReadDataChannel
    lsu.vrfReadResults(index) := lane.vrfReadDataChannel

    // 写lane
    lane.vrfWriteChannel.valid := vrfWrite(index).valid || (maskUnitWrite.valid && maskUnitWriteSelect(index))
    lane.vrfWriteChannel.bits :=
      Mux(vrfWrite(index).valid, vrfWrite(index).bits, maskUnitWrite.bits)
    vrfWrite(index).ready := lane.vrfWriteChannel.ready
    writeSelectMaskUnit(index) :=
      lane.vrfWriteChannel.ready && !vrfWrite(index).valid && maskUnitWriteSelect(index)

    lsu.offsetReadResult(index).valid := lane.laneResponse.valid && lane.laneResponse.bits.toLSU
    lsu.offsetReadResult(index).bits := lane.laneResponse.bits.data
    lsu.offsetReadIndex(index) := lane.laneResponse.bits.instructionIndex

    instructionFinished(index).zip(slots.map(_.record.instructionIndex)).foreach {
      case (d, f) => d := (UIntToOH(f(parameter.instructionIndexBits - 2, 0)) & lane.instructionFinished).orR
    }
    lane.maskInput := regroupV0.map(Mux1H(UIntToOH(lane.maskSelectSew)(2, 0), _)).map { v0ForLane =>
      VecInit(v0ForLane.asBools.grouped(parameter.datapathWidth).toSeq.map(VecInit(_).asUInt))
    }(index)(lane.maskSelect)
    lane.lsuLastReport := lsu.lastReport |
      Mux(
        lastMaskUnitWrite,
        indexToOH(slots.last.record.instructionIndex, parameter.chainingSize),
        0.U
      )

    lane.lsuMaskGroupChange := lsu.lsuMaskGroupChange
    lane.lsuVRFWriteBufferClear := !lsu.vrfWritePort(index).valid && busClear

    // 处理lane的mask类型请求
    laneSynchronize(index) := lane.laneResponse.valid && !lane.laneResponse.bits.toLSU
    when(laneSynchronize(index)) {
      data(index).valid := true.B
      data(index).bits := lane.laneResponse.bits.data
      completedVec(index) := lane.laneResponse.bits.ffoSuccess
    }
    lane
  }
  busClear := !VecInit(laneVec.map(_.writeBusPort.deq.valid)).asUInt.orR
  writeQueueClearVec := VecInit(laneVec.map(_.writeQueueValid))

  // 连lsu
  lsu.request.valid := requestRegDequeue.fire && isLoadStoreType
  lsu.request.bits.instructionIndex := requestReg.bits.instructionIndex
  lsu.request.bits.rs1Data := requestRegDequeue.bits.src1Data
  lsu.request.bits.rs2Data := requestRegDequeue.bits.src2Data
  lsu.request.bits.instructionInformation.nf := requestRegDequeue.bits.instruction(31, 29)
  lsu.request.bits.instructionInformation.mew := requestRegDequeue.bits.instruction(28)
  lsu.request.bits.instructionInformation.mop := requestRegDequeue.bits.instruction(27, 26)
  lsu.request.bits.instructionInformation.lumop := requestRegDequeue.bits.instruction(24, 20)
  lsu.request.bits.instructionInformation.vs3 := requestRegDequeue.bits.instruction(11, 7)
  // (0b000 0b101 0b110 0b111) -> (8, 16, 32, 64)忽略最高位
  lsu.request.bits.instructionInformation.eew := requestRegDequeue.bits.instruction(13, 12)
  lsu.request.bits.instructionInformation.isStore := isStoreType
  lsu.request.bits.instructionInformation.maskedLoadStore := maskType

  lsu.maskInput.zip(lsu.maskSelect).foreach { case (data, index) => data := v0(index) }
  lsu.csrInterface := requestReg.bits.csr
  lsu.writeReadyForLsu := VecInit(laneVec.map(_.writeReadyForLsu)).asUInt.andR
  lsu.vrfReadyToStore := VecInit(laneVec.map(_.vrfReadyToStore)).asUInt.andR

  // 连lane的环
  laneVec.map(_.readBusPort).foldLeft(laneVec.last.readBusPort) {
    case (previous, current) =>
      current.enq <> previous.deq
      current
  }
  laneVec.map(_.writeBusPort).foldLeft(laneVec.last.writeBusPort) {
    case (previous, current) =>
      current.enq <> previous.deq
      current
  }

  // 连 tilelink
  memoryPorts.zip(lsu.tlPort).foreach {
    case (source, sink) =>
      val dBuffer = Queue(source.d, 1, flow = true)
      sink <> source
      sink.d <> dBuffer
  }
  // 暂时直接连lsu的写,后续需要处理scheduler的写
  vrfWrite.zip(lsu.vrfWritePort).foreach { case (sink, source) => sink <> source }

  /** Slot has free entries. */
  val free = VecInit(slots.map(_.state.idle)).asUInt
  allSlotFree := free.andR

  // instruction issue
  {
    val free1H = ffo(free)

    /** try to issue instruction to which slot. */
    val slotToEnqueue: UInt = Mux(specialInstruction, true.B ## 0.U((parameter.chainingSize - 1).W), free1H)

    /** for lsu instruction lsu is ready, for normal instructions, lanes are ready. */
    val executionReady: Bool = (!isLoadStoreType || lsu.request.ready) && (noOffsetReadLoadStore || allLaneReady)
    // - ready to issue instruction
    // - for vi and vx type of gather, it need to access vs2 for one time, we read vs2 firstly in `gatherReadFinish`
    //   and convert it to mv instruction.
    //   TODO: decode it directly
    // - for slide instruction, it is unordered, and may have RAW hazard,
    //   we detect the hazard and decide should we issue this slide or
    //   issue the instruction after the slide which already in the slot.
    requestRegDequeue.ready := executionReady && slotReady && (!gatherNeedRead || gatherReadFinish) &&
      instructionRAWReady

    // TODO: change to `requestRegDequeue.fire`.
    instructionToSlotOH := Mux(requestRegDequeue.ready, slotToEnqueue, 0.U)
  }

  // instruction commit
  {
    val slotCommit: Vec[Bool] = VecInit(slots.map { inst =>
      inst.state.sMaskUnitExecution && inst.state.wLast && !inst.state.sCommit && inst.record.instructionIndex === responseCounter
    })
    response.valid := slotCommit.asUInt.orR
    response.bits.data := Mux(selectffoIndex.valid, selectffoIndex.bits, dataResult.bits)
    response.bits.vxsat := DontCare
    response.bits.mem := (slotCommit.asUInt & VecInit(slots.map(_.record.isLoadStore)).asUInt).orR
    lastSlotCommit := slotCommit.last
  }

  // write v0(mask)
  v0.zipWithIndex.foreach {
    case (data, index) =>
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
}
