// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._

object VRFParam {
  implicit val rwP: upickle.default.ReadWriter[VRFParam] = upickle.default.macroRW
}

/** Parameter for [[Lane]].
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  *
  * TODO: change to use 32bits memory + mask,
  *       use portFactor to increase port number
  *
  * TODO: add ECC cc @sharzyL
  *       8bits -> 5bits
  *       16bits -> 6bits
  *       32bits -> 7bits
  */
case class VRFParam(
  vLen:          Int,
  laneNumber:    Int,
  datapathWidth: Int,
  chainingSize:  Int)
    extends SerializableModuleParameter {

  /** See documentation for VRF.
    * TODO: document this
    */
  val vrfReadPort: Int = 7

  /** VRF index number is 32, defined in spec. */
  val regNum: Int = 32

  /** The hardware width of [[regNum]] */
  val regNumBits: Int = log2Ceil(regNum)
  // One more bit for sorting
  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** How many ELEN(32 in current design) can be accessed for one memory port accessing.
    *
    * @note:
    * if increasing portFactor:
    * - we can have more memory ports.
    * - a big VRF memory is split into small memories, the shell of memory contributes more area...
    */
  val portFactor: Int = 1

  /** the width of VRF banked together. */
  val rowWidth: Int = datapathWidth * portFactor

  /** the depth of memory */
  val rfDepth: Int = vLen * regNum / rowWidth / laneNumber

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  // 用data path width 的 ram 应该是不会变了
  val ramWidth: Int = datapathWidth
  val rfBankNum: Int = rowWidth / ramWidth

  /** used to instantiate VRF. */
  val VLMaxWidth: Int = log2Ceil(vLen) + 1

  /** bits of mask group counter */
  val maskGroupCounterBits: Int = log2Ceil(vLen/datapathWidth)

  val vlWidth: Int = log2Ceil(vLen)

  /** Parameter for [[RegFile]] */
  def rfParam: RFParam = RFParam(rfDepth, width = ramWidth)
}

/** Vector Register File.
  * contains logic:
  * - RAM as VRF.
  * - chaining detection
  * - bank split
  * - out of order chaining hazard detection:
  *   TODO: move to Top.
  *
  * TODO: implement [[parameter.portFactor]] for increasing VRF bandwidth.
  * TODO: probe each ports to benchmark the bandwidth.
  */
class VRF(val parameter: VRFParam) extends Module with SerializableModule[VRFParam] {

  /** VRF read requests
    * ready will couple from valid from [[readRequests]],
    * ready is asserted when higher priority valid is less than 2.
    */
  val readRequests: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      parameter.vrfReadPort,
      Flipped(
        Decoupled(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
      )
    )
  )

  /** VRF read results. */
  val readResults: Vec[UInt] = IO(Output(Vec(parameter.vrfReadPort, UInt(parameter.datapathWidth.W))))

  /** VRF write requests
    * ready will couple from valid from [[write]],
    * ready is asserted when higher priority valid is less than 2.
    * TODO: rename to `vrfWriteRequests`
    */
  val write: DecoupledIO[VRFWriteRequest] = IO(
    Flipped(
      Decoupled(
        new VRFWriteRequest(
          parameter.regNumBits,
          parameter.vrfOffsetBits,
          parameter.instructionIndexBits,
          parameter.datapathWidth
        )
      )
    )
  )

  /** when instruction is fired, record it in the VRF for chaining. */
  val instructionWriteReport: DecoupledIO[VRFWriteReport] = IO(Flipped(Decoupled(new VRFWriteReport(parameter))))

  val lsuInstructionFire: Bool = IO(Input(Bool()))

  /** similar to [[flush]]. */
  val lsuLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  val lsuMaskGroupChange: UInt = IO(Input(UInt(parameter.chainingSize.W)))
  val writeReadyForLsu: Bool = IO(Output(Bool()))
  val vrfReadyToStore: Bool = IO(Output(Bool()))

  /** we can only chain LSU instructions, after [[LSU.writeQueueVec]] is cleared. */
  val lsuWriteBufferClear: Bool = IO(Input(Bool()))
  // todo: delete
  dontTouch(write)

  val chainingRecord: Vec[ValidIO[VRFWriteReport]] = RegInit(
    VecInit(Seq.fill(parameter.chainingSize)(0.U.asTypeOf(Valid(new VRFWriteReport(parameter)))))
  )

  def rawCheck(before: VRFWriteReport, after: VRFWriteReport): Bool = {
    before.vd.valid &&
    ((before.vd.bits === after.vs1.bits && after.vs1.valid) ||
    (before.vd.bits === after.vs2) ||
    (before.vd.bits === after.vd.bits && after.ma))
  }

  def regOffsetCheck(beforeVsOffset: UInt, beforeOffset: UInt, afterVsOffset: UInt, afterOffset: UInt): Bool = {
    (beforeVsOffset > afterVsOffset) || ((beforeVsOffset === afterVsOffset) && (beforeOffset > afterOffset))
  }

  /** @param read : 发起读请求的相应信息
    * @param readRecord : 发起读请求的指令的记录\
    * @param record : 要做比对的指令的记录
    * todo: 维护冲突表,免得每次都要算一次
    */
  def chainingCheck(read: VRFReadRequest, readRecord: VRFWriteReport, record: ValidIO[VRFWriteReport]): Bool = {
    // 先看新老
    val older = instIndexL(read.instructionIndex, record.bits.instIndex)
    val sameInst = read.instructionIndex === record.bits.instIndex

    val vsOffsetMask = record.bits.mul.andR ## record.bits.mul(1) ## record.bits.mul.orR
    val vsBaseMask: UInt = 3.U(2.W) ## (~vsOffsetMask).asUInt
    // todo: 处理双倍的
    val vs:       UInt = read.vs & vsBaseMask
    val vsOffset: UInt = read.vs & vsOffsetMask
    val vd = readRecord.vd.bits

    val raw: Bool = record.bits.vd.valid && (vs === record.bits.vd.bits) &&
      !regOffsetCheck(record.bits.vdOffset, record.bits.offset, vsOffset, read.offset)
    val waw: Bool = readRecord.vd.valid && record.bits.vd.valid && readRecord.vd.bits === record.bits.vd.bits &&
      !regOffsetCheck(record.bits.vdOffset, record.bits.offset, vsOffset, read.offset)
    val offsetCheckFail: Bool = !regOffsetCheck(record.bits.vdOffset, record.bits.offset, vsOffset, read.offset)
    val war: Bool = readRecord.vd.valid &&
      (((vd === record.bits.vs1.bits) && record.bits.vs1.valid) || (vd === record.bits.vs2) ||
        ((vd === record.bits.vd.bits) && record.bits.ma)) && offsetCheckFail
    !((!older && (waw || raw || war)) && !sameInst && record.valid)
  }

  def enqCheck(enq: VRFWriteReport, record: ValidIO[VRFWriteReport]): Bool = {
    val recordBits = record.bits
    val sameVd = enq.vd.valid && enq.vd.bits === recordBits.vd.bits
    val raw: Bool = rawCheck(record.bits, enq)
    val war: Bool = rawCheck(enq, record.bits)
    val waw: Bool = recordBits.vd.valid && sameVd
    val stWar = record.valid && record.bits.st && sameVd

    /** 两种暂时处理不了的冲突
      * 自己会乱序写 & wax: enq.unOrderWrite && (war || waw)
      * 老的会乱序写 & raw: record.bits.unOrderWrite && raw
      * 老的是st & war
      * todo: ld 需要更大粒度的channing更新或检测,然后除开segment的ld就能chaining起来了
      */
    (!((enq.unOrderWrite && (war || waw)) || (record.bits.unOrderWrite && raw) || stWar)) || !record.valid
  }

  // first read
  val bankReadF: Vec[UInt] = Wire(Vec(parameter.vrfReadPort, UInt(parameter.rfBankNum.W)))
  val bankReadS: Vec[UInt] = Wire(Vec(parameter.vrfReadPort, UInt(parameter.rfBankNum.W)))
  val readResultF: Vec[UInt] = Wire(Vec(parameter.rfBankNum, UInt(parameter.ramWidth.W)))
  val readResultS: Vec[UInt] = Wire(Vec(parameter.rfBankNum, UInt(parameter.ramWidth.W)))

  val (_, secondOccupied) = readRequests.zipWithIndex.foldLeft(
    (0.U(parameter.rfBankNum.W), 0.U(parameter.rfBankNum.W))
  ) {
    // o: 第一个read port是否被占用
    // t: 第二个read port是否被占用
    // v: readRequest
    // i: 第几个readRequests
    case ((o, t), (v, i)) =>
      // 先找到自的record
      val readRecord =
        Mux1H(chainingRecord.map(_.bits.instIndex === v.bits.instructionIndex), chainingRecord.map(_.bits))
      val checkResult:  Bool = chainingRecord.map(r => chainingCheck(v.bits, readRecord, r)).reduce(_ && _)
      val validCorrect: Bool = if (i == 0) v.valid else v.valid && checkResult
      // select bank
      val bank = if (parameter.rfBankNum == 1) true.B else UIntToOH(v.bits.offset(log2Ceil(parameter.rfBankNum) - 1, 0))
      val bankNext = RegNext(bank)
      val bankCorrect = Mux(validCorrect, bank, 0.U(parameter.rfBankNum.W))
      // 我选的这个port的第二个read port 没被占用
      v.ready := (bank & (~t)).orR && checkResult
      val firstUsed = (bank & o).orR
      bankReadF(i) := bankCorrect & (~o)
      bankReadS(i) := bankCorrect & (~t) & o
      readResults(i) := Mux(RegNext(firstUsed), Mux1H(bankNext, readResultS), Mux1H(bankNext, readResultF))
      (o | bankCorrect, (bankCorrect & o) | t)
  }
  val writeBank: UInt =
    if (parameter.rfBankNum == 1) true.B else UIntToOH(write.bits.offset(log2Ceil(parameter.rfBankNum) - 1, 0))
  write.ready := (writeBank & (~secondOccupied)).orR

  val rfVec: Seq[RegFile] = Seq.tabulate(parameter.rfBankNum) { bank =>
    // rf instant
    val rf = Module(new RegFile(parameter.rfParam))
    // connect readPorts
    rf.readPorts.head.addr :=
      Mux1H(bankReadF.map(_(bank)), readRequests.map(r => (r.bits.vs ## r.bits.offset) >> log2Ceil(parameter.rfBankNum)))
    rf.readPorts.last.addr :=
      Mux1H(bankReadS.map(_(bank)), readRequests.map(r => (r.bits.vs ## r.bits.offset) >> log2Ceil(parameter.rfBankNum)))
    readResultF(bank) := rf.readPorts.head.data
    readResultS(bank) := rf.readPorts.last.data
    // connect writePort
    rf.writePort.valid := write.fire && writeBank(bank)
    rf.writePort.bits.addr := (write.bits.vd ## write.bits.offset) >> log2Ceil(parameter.rfBankNum)
    rf.writePort.bits.data := write.bits.data
    rf
  }

  val initRecord: ValidIO[VRFWriteReport] = WireDefault(0.U.asTypeOf(Valid(new VRFWriteReport(parameter))))
  initRecord.valid := true.B
  initRecord.bits := instructionWriteReport.bits
  val freeRecord: UInt = VecInit(chainingRecord.map(!_.valid)).asUInt
  val recordFFO:  UInt = ffo(freeRecord)
  val recordEnq:  UInt = Wire(UInt(parameter.chainingSize.W))
  // handle VRF hazard
  // TODO: move to [[V]]
  instructionWriteReport.ready := chainingRecord.map(r => enqCheck(instructionWriteReport.bits, r)).reduce(_ && _)
  recordEnq := Mux(
    // 纯粹的lsu指令的记录不需要ready
    instructionWriteReport.valid && (instructionWriteReport.ready || lsuInstructionFire),
    recordFFO,
    0.U(parameter.chainingSize.W)
  )

  chainingRecord.zipWithIndex.foreach {
    case (record, i) =>
      val vsOffsetMask = record.bits.mul.andR ## record.bits.mul(1) ## record.bits.mul.orR

      // lsu更新相关
      val nextMaskGroupCount = record.bits.maskGroupCounter + 1.U
      // 一个mask group 会访问 dataPath * seg * (2 ** sew * 8)
      // dataByteForMaskGroup = (record.bits.seg.bits << record.bits.eew) << log2Ceil(parameter.datapathWidth)
      // 这一次换组意味着已经执行了 maskGroupCounter * dataByteForMaskGroup
      val executedByte: UInt =
        ((nextMaskGroupCount * (record.bits.seg.bits +& 1.U)) << record.bits.eew) << log2Ceil(parameter.datapathWidth)
      // 从低到高:
      // (1, 0): 是data path的偏移
      // (4, 2): 是lane的 index
      // (6, 5): 是单个寄存器的偏移
      // (9, 7): 是最终寄存器的偏移 -> (log2ceil(1024) - 1, ...-3)
      val offsetForExecutedByte = executedByte(parameter.vlWidth - 4, parameter.vlWidth - 4 - parameter.vrfOffsetBits + 1)
      val nextOffset = offsetForExecutedByte - 1.U
      val nextVDOffset = executedByte(parameter.vlWidth - 1, parameter.vlWidth - 3) - (offsetForExecutedByte === 0.U)

      when(
        write.valid &&
          // lsu's record is modified by lsuMaskGroupChange
          !record.bits.ls &&
          write.bits.instructionIndex === record.bits.instIndex &&
          (write.bits.last || write.bits.mask(3))
      ) {
        // widen 类型的可能后一个先到,所以直接-1吧
        record.bits.offset := Mux(write.bits.offset === 0.U, write.bits.offset, write.bits.offset - 1.U)
        record.bits.vdOffset := vsOffsetMask & write.bits.vd
        when(write.bits.last) {
          record.valid := false.B
        }
      }
      when(ohCheck(lsuLastReport, record.bits.instIndex, parameter.chainingSize)) {
        record.bits.stFinish := true.B
      }
      when(ohCheck(lsuMaskGroupChange, record.bits.instIndex, parameter.chainingSize) && record.bits.ls) {
        record.bits.maskGroupCounter := nextMaskGroupCount
        record.bits.offset := nextOffset
        record.bits.vdOffset := nextVDOffset
      }
      when(record.bits.stFinish && lsuWriteBufferClear && record.valid) {
        record.valid := false.B
      }
      when(recordEnq(i)) {
        record := initRecord
      }
  }
  // 判断lsu 是否可以写
  val hazardVec: Seq[IndexedSeq[(Bool, Bool)]] = chainingRecord.init.zipWithIndex.map { case (sourceRecord, sourceIndex) =>
    chainingRecord.drop(sourceIndex + 1).zipWithIndex.map { case (sinkRecord, _) =>
      val recordSeq: Seq[ValidIO[VRFWriteReport]] = Seq(sourceRecord, sinkRecord)
      val isLoad = recordSeq.map(r => r.valid && r.bits.ls && !r.bits.st)
      val isStore = recordSeq.map(r => r.valid && r.bits.ls && r.bits.st)
      val isSlow = recordSeq.map(r => r.valid && r.bits.slow)
      // todo: 重叠而不是相等
      val samVd = sourceRecord.bits.vd === sinkRecord.bits.vd
      val sourceVdEqSinkVs: Bool = sourceRecord.bits.vd.valid && (
        (sourceRecord.bits.vd.bits === sinkRecord.bits.vs2) ||
          ((sourceRecord.bits.vd.bits === sinkRecord.bits.vs1.bits) && sinkRecord.bits.vs1.valid)
      )
      val sinkVdEqSourceVs: Bool = sinkRecord.bits.vd.valid && (
        (sinkRecord.bits.vd.bits === sourceRecord.bits.vs2) ||
          ((sinkRecord.bits.vd.bits === sourceRecord.bits.vs1.bits) && sourceRecord.bits.vs1.valid)
      )
      // source更新
      val older = instIndexL(sinkRecord.bits.instIndex, sourceRecord.bits.instIndex)
      val hazardForeLoad = Mux(older, isLoad.head && isSlow.last, isLoad.last && isSlow.head) && (
        // waw
        samVd ||
          // war
          Mux(older, sourceVdEqSinkVs, sinkVdEqSourceVs)
        )
      val rawForeStore = Mux(older, isStore.head && isSlow.last, isStore.last && isSlow.head) && samVd
      (hazardForeLoad, rawForeStore)
    }
  }
  writeReadyForLsu := !hazardVec.map(_.map(_._1).reduce(_ || _)).reduce(_ || _)
  vrfReadyToStore := !hazardVec.map(_.map(_._2).reduce(_ || _)).reduce(_ || _)
}
