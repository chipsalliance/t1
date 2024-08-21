// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import chisel3._
import chisel3.experimental.hierarchy.{Instantiate, instantiable, public}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.probe.{Probe, ProbeValue, define}
import chisel3.util._
import chisel3.ltl._
import chisel3.ltl.Sequence._
import org.chipsalliance.t1.rtl.{LSUWriteCheck, VRFReadPipe, VRFReadRequest, VRFWriteReport, VRFWriteRequest, ffo, instIndexL, instIndexLE, ohCheck}

sealed trait RamType
object RamType {
  implicit val rwP: upickle.default.ReadWriter[RamType] = upickle.default.ReadWriter.merge(
    upickle.default.macroRW[p0rw.type],
    upickle.default.macroRW[p0rp1w.type],
    upickle.default.macroRW[p0rwp1rw.type]
  )

  case object p0rw extends RamType

  case object p0rp1w extends RamType

  case object p0rwp1rw extends RamType
}
object VRFParam {
  implicit val rwP: upickle.default.ReadWriter[VRFParam] = upickle.default.macroRW
}

/** Parameter for [[Lane]].
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  * @param portFactor How many ELEN(32 in current design) can be accessed for one memory port accessing.
  *
  * @note:
  *  if increasing portFactor:
  *   - we can have more memory ports.
  *   - a big VRF memory is split into small memories, the shell of memory contributes more area...
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
  chainingSize:  Int,
  portFactor:    Int,
  ramType:       RamType)
    extends SerializableModuleParameter {

  /** See documentation for VRF.
    * chainingSize * 3 + 1 + 1: 3 read /slot + maskedWrite + lsu read
    * 0: maskedWrite
    * last: lsu read
    * Each element represents a read port of vrf,
    * The number inside is which of the above requests will share this port.
    */
  val connectTree: Seq[Seq[Int]] = Seq.tabulate(chainingSize * 3 + 1 + 1) { i => Seq(i)}
  val vrfReadPort: Int = connectTree.size

  /** VRF index number is 32, defined in spec. */
  val regNum: Int = 32

  /** The hardware width of [[regNum]] */
  val regNumBits: Int = log2Ceil(regNum)
  // One more bit for sorting
  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

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

  val elementSize: Int = vLen * 8 / datapathWidth / laneNumber

  // todo: 4 bit for ecc
  val memoryWidth: Int = ramWidth + 4

  // 1: pipe access request + 1: SyncReadMem
  val vrfReadLatency = 2
}

class VRFProbe(parameter: VRFParam) extends Bundle {
  val valid: Bool = Bool()
  val requestVd: UInt = UInt(parameter.regNumBits.W)
  val requestOffset: UInt = UInt(parameter.vrfOffsetBits.W)
  val requestMask: UInt = UInt((parameter.datapathWidth / 8).W)
  val requestData: UInt = UInt(parameter.datapathWidth.W)
  val requestInstruction: UInt = UInt(parameter.instructionIndexBits.W)
}

/** Vector Register File.
  * contains logic:
  * - RAM as VRF.
  * - chaining detection
  * - bank split
  * - out of order chaining hazard detection:
  *   TODO: move to Top.
  *
  * TODO: probe each ports to benchmark the bandwidth.
  */
@instantiable
class VRF(val parameter: VRFParam) extends Module with SerializableModule[VRFParam] {

  /** VRF read requests
    * ready will couple from valid from [[readRequests]],
    * ready is asserted when higher priority valid is less than 2.
    */
  @public
  val readRequests: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      parameter.vrfReadPort,
      Flipped(
        Decoupled(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
      )
    )
  )

  // @todo @Clo91eaf should pull read&write check
  //                 performance checker, in the difftest, it can observe all previous events, and know should it be stalled?
  //                 then, we can know the accuracy read&write check hardware signal.
  // 3 * slot + 2 cross read
  @public
  val readCheck: Vec[VRFReadRequest] = IO(Vec(parameter.chainingSize * 3 + 2, Input(
    new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
  )))

  @public
  val readCheckResult: Vec[Bool] = IO(Vec(parameter.chainingSize * 3 + 2, Output(Bool())))

  /** VRF read results. */
  @public
  val readResults: Vec[UInt] = IO(Output(Vec(parameter.vrfReadPort, UInt(parameter.datapathWidth.W))))

  /** VRF write requests
    * ready will couple from valid from [[write]],
    * ready is asserted when higher priority valid is less than 2.
    * TODO: rename to `vrfWriteRequests`
    */
  @public
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

  @public
  val writeCheck: Vec[LSUWriteCheck] = IO(Vec(parameter.chainingSize + 3, Input(new LSUWriteCheck(
    parameter.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits
  ))))

  @public
  val writeAllow: Vec[Bool] = IO(Vec(parameter.chainingSize + 3, Output(Bool())))

  /** when instruction is fired, record it in the VRF for chaining. */
  @public
  val instructionWriteReport: DecoupledIO[VRFWriteReport] = IO(Flipped(Decoupled(new VRFWriteReport(parameter))))

  /** similar to [[flush]]. */
  @public
  val instructionLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  @public
  val lsuLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  @public
  val dataInLane: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  @public
  val lsuMaskGroupChange: UInt = IO(Input(UInt(parameter.chainingSize.W)))
  @public
  val writeReadyForLsu: Bool = IO(Output(Bool()))
  @public
  val vrfReadyToStore: Bool = IO(Output(Bool()))

  @public
  val vrfAllocateIssue: Bool = IO(Output(Bool()))

  /** we can only chain LSU instructions, after [[LSU.writeQueueVec]] is cleared. */
  @public
  val loadDataInLSUWriteQueue: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  @public
  val vrfProbe = IO(Output(Probe(new VRFProbe(parameter), layers.Verification)))

  // reset sram
  val sramReady: Bool = RegInit(false.B)
  val sramResetCount: UInt = RegInit(0.U(log2Ceil(parameter.rfDepth).W))
  val resetValid: Bool = !sramReady
  when(resetValid) {
    sramResetCount := sramResetCount + 1.U
    when(sramResetCount.andR) { sramReady := true.B }
  }
  // TODO: add Chaining Check Probe

  // todo: delete
  dontTouch(write)
  val portFireCount: UInt = PopCount(VecInit(readRequests.map(_.fire) :+ write.fire))
  dontTouch(portFireCount)

  val writeBank: UInt =
    if (parameter.rfBankNum == 1) true.B else UIntToOH(write.bits.offset(log2Ceil(parameter.rfBankNum) - 1, 0))

  // Add one more record slot to prevent there is no free slot when the instruction comes in
  // (the slot will die a few cycles later than the instruction)
  val chainingRecord: Vec[ValidIO[VRFWriteReport]] = RegInit(
    VecInit(Seq.fill(parameter.chainingSize + 1)(0.U.asTypeOf(Valid(new VRFWriteReport(parameter)))))
  )
  val chainingRecordCopy: Vec[ValidIO[VRFWriteReport]] = RegInit(
    VecInit(Seq.fill(parameter.chainingSize + 1)(0.U.asTypeOf(Valid(new VRFWriteReport(parameter)))))
  )
  val recordValidVec: Seq[Bool] = chainingRecord.map(r => !r.bits.elementMask.andR && r.valid)

  // first read
  val bankReadF: Vec[UInt] = Wire(Vec(parameter.vrfReadPort, UInt(parameter.rfBankNum.W)))
  val bankReadS: Vec[UInt] = Wire(Vec(parameter.vrfReadPort, UInt(parameter.rfBankNum.W)))
  val readResultF: Vec[UInt] = Wire(Vec(parameter.rfBankNum, UInt(parameter.ramWidth.W)))
  val readResultS: Vec[UInt] = Wire(Vec(parameter.rfBankNum, UInt(parameter.ramWidth.W)))

  val firstReadPipe: Seq[ValidIO[VRFReadPipe]] = Seq.tabulate(parameter.rfBankNum) { _ => RegInit(0.U.asTypeOf(Valid(new VRFReadPipe(parameter.rfDepth))))}
  val secondReadPipe: Seq[ValidIO[VRFReadPipe]] = Seq.tabulate(parameter.rfBankNum) { _ => RegInit(0.U.asTypeOf(Valid(new VRFReadPipe(parameter.rfDepth))))}
  val writePipe: ValidIO[VRFWriteRequest] = RegInit(0.U.asTypeOf(Valid(chiselTypeOf(write.bits))))
  writePipe.valid := write.fire
  when(write.fire) { writePipe.bits := write.bits }
  val writeBankPipe: UInt = RegNext(writeBank)

  // lane chaining check
  readCheck.zip(readCheckResult).foreach { case (req, res) =>
    val recordSelect = chainingRecord
    // 先找到自的record
    val readRecord =
      Mux1H(recordSelect.map(_.bits.instIndex === req.instructionIndex), recordSelect.map(_.bits))
    res :=
      recordSelect.zip(recordValidVec).zipWithIndex.map {
        case ((r, f), recordIndex) =>
          val checkModule = Instantiate(new ChainingCheck(parameter))
          checkModule.read := req
          checkModule.readRecord := readRecord
          checkModule.record := r
          checkModule.recordValid := f
          checkModule.checkResult
      }.reduce(_ && _)
  }

  val checkSize: Int = readRequests.size
  val (firstOccupied, secondOccupied) = readRequests.zipWithIndex.foldLeft(
    (0.U(parameter.rfBankNum.W), 0.U(parameter.rfBankNum.W))
  ) {
    // o: 第一个read port是否被占用
    // t: 第二个read port是否被占用
    // v: readRequest
    // i: 第几个readRequests
    case ((o, t), (v, i)) =>
      val recordSelect = if (i < (checkSize / 2)) chainingRecord else chainingRecordCopy
      // 先找到自的record
      val readRecord =
        Mux1H(recordSelect.map(_.bits.instIndex === v.bits.instructionIndex), recordSelect.map(_.bits))
      // @todo @Clo91eaf read&write in the same cycle.
      val portConflictCheck = Wire(Bool())
      val checkResult: Option[Bool] = Option.when(i == (readRequests.size - 1)) {
        recordSelect.zip(recordValidVec).zipWithIndex.map {
          case ((r, f), recordIndex) =>
            val checkModule = Instantiate(new ChainingCheck(parameter))
            checkModule.suggestName(s"ChainingCheck_readPort${i}_record${recordIndex}")
            checkModule.read := v.bits
            checkModule.readRecord := readRecord
            checkModule.record := r
            checkModule.recordValid := f
            checkModule.checkResult
        }.reduce(_ && _) && portConflictCheck
      }
      val validCorrect: Bool = if (i == (readRequests.size - 1)) v.valid && checkResult.get else v.valid
      // select bank
      val bank = if (parameter.rfBankNum == 1) true.B else UIntToOH(v.bits.offset(log2Ceil(parameter.rfBankNum) - 1, 0))
      val pipeBank = Pipe(true.B, bank, parameter.vrfReadLatency).bits
      val bankCorrect = Mux(validCorrect, bank, 0.U(parameter.rfBankNum.W))
      val readPortCheckSelect = parameter.ramType match {
        case RamType.p0rw => o
        case RamType.p0rp1w => o
        case RamType.p0rwp1rw => t
      }
      portConflictCheck := (parameter.ramType match {
        case RamType.p0rw => true.B
        case _ =>
          !((write.valid && bank === writeBank && write.bits.vd === v.bits.vs && write.bits.offset === v.bits.offset) ||
            (writePipe.valid && bank === writeBankPipe && writePipe.bits.vd === v.bits.vs && writePipe.bits.offset === v.bits.offset))
      })
      val portReady: Bool = if (i == (readRequests.size - 1)) {
        (bank & (~readPortCheckSelect)).orR && checkResult.get
      } else {
        // @todo @Clo91eaf read check port is ready.
        // if there are additional read port for the bank.
        (bank & (~readPortCheckSelect)).orR
      }
      v.ready := portReady && sramReady
      val firstUsed = (bank & o).orR
      bankReadF(i) := bankCorrect & (~o)
      bankReadS(i) := bankCorrect & (~t) & o
      val pipeFirstUsed = Pipe(true.B, firstUsed, parameter.vrfReadLatency).bits
      val pipeFire = Pipe(true.B, v.fire, parameter.vrfReadLatency).bits
      readResults(i) := Mux1H(Seq(
        (!pipeFirstUsed && pipeFire, Mux1H(pipeBank, readResultF)),
        (pipeFirstUsed && pipeFire, Mux1H(pipeBank, readResultS)),
      ))
      (o | bankCorrect, (bankCorrect & o) | t)
  }
  // @todo @Clo91eaf check write port is ready.
  write.ready := sramReady && (parameter.ramType match {
    case RamType.p0rw => (writeBank & (~firstOccupied)).orR
    case RamType.p0rp1w => true.B
    case RamType.p0rwp1rw => (writeBank & (~secondOccupied)).orR
  })

  val writeData: UInt = Mux(resetValid, 0.U(parameter.datapathWidth.W), writePipe.bits.data)
  val writeAddress: UInt =
    Mux(
      resetValid,
      sramResetCount,
      ((writePipe.bits.vd ## writePipe.bits.offset) >> log2Ceil(parameter.rfBankNum)).asUInt
    )
  // @todo @Clo91eaf VRF write&read singal should be captured here.
  // @todo           in the future, we need to maintain a layer to trace the original requester to each read&write.
  val rfVec: Seq[SRAMInterface[UInt]] = Seq.tabulate(parameter.rfBankNum) { bank =>
    // rf instant
    val rf: SRAMInterface[UInt] = SRAM(
      size = parameter.rfDepth,
      tpe = UInt(parameter.memoryWidth.W),
      numReadPorts = parameter.ramType match {
       case RamType.p0rw => 0
       case RamType.p0rp1w => 1
       case RamType.p0rwp1rw => 0
      },
      numWritePorts = parameter.ramType match {
       case RamType.p0rw => 0
       case RamType.p0rp1w => 1
       case RamType.p0rwp1rw => 0
      },
      numReadwritePorts = parameter.ramType match {
       case RamType.p0rw => 1
       case RamType.p0rp1w => 0
       case RamType.p0rwp1rw => 2
      }
    )
    val writeValid = writePipe.valid && writeBankPipe(bank)
    val ramWriteValid: Bool = writeValid || resetValid
    parameter.ramType match {
      case RamType.p0rw =>
        firstReadPipe(bank).bits.address :=
          Mux1H(bankReadF.map(_(bank)), readRequests.map(r => (r.bits.vs ## r.bits.offset) >> log2Ceil(parameter.rfBankNum)))
        firstReadPipe(bank).valid := bankReadF.map(_(bank)).reduce(_ || _)
        rf.readwritePorts.last.address := Mux(
          ramWriteValid,
          writeAddress,
          firstReadPipe(bank).bits.address
        )
        rf.readwritePorts.last.enable := ramWriteValid || firstReadPipe(bank).valid
        rf.readwritePorts.last.isWrite := ramWriteValid
        rf.readwritePorts.last.writeData := writeData
        AssertProperty(BoolSequence(!(writeValid && firstReadPipe(bank).valid)))
        readResultF(bank) := rf.readwritePorts.head.readData
        readResultS(bank) := DontCare
      case RamType.p0rp1w =>
        firstReadPipe(bank).bits.address :=
          Mux1H(bankReadF.map(_(bank)), readRequests.map(r => (r.bits.vs ## r.bits.offset) >> log2Ceil(parameter.rfBankNum)))
        firstReadPipe(bank).valid := bankReadF.map(_(bank)).reduce(_ || _)
        // connect readPorts
        rf.readPorts.head.address := firstReadPipe(bank).bits.address
        rf.readPorts.head.enable := firstReadPipe(bank).valid
        readResultF(bank) := rf.readPorts.head.data
        readResultS(bank) := DontCare

        rf.writePorts.head.enable := ramWriteValid
        rf.writePorts.head.address := writeAddress
        rf.writePorts.head.data := writeData
      case RamType.p0rwp1rw =>
        firstReadPipe(bank).bits.address :=
          Mux1H(bankReadF.map(_(bank)), readRequests.map(r => (r.bits.vs ## r.bits.offset) >> log2Ceil(parameter.rfBankNum)))
        firstReadPipe(bank).valid := bankReadF.map(_(bank)).reduce(_ || _)
        // connect readPorts
        rf.readwritePorts.head.address := firstReadPipe(bank).bits.address
        rf.readwritePorts.head.enable := firstReadPipe(bank).valid
        rf.readwritePorts.head.isWrite := false.B
        rf.readwritePorts.head.writeData := DontCare

        readResultF(bank) := rf.readwritePorts.head.readData
        readResultS(bank) := rf.readwritePorts.last.readData

        secondReadPipe(bank).bits.address :=
          Mux1H(bankReadS.map(_(bank)), readRequests.map(r => (r.bits.vs ## r.bits.offset) >> log2Ceil(parameter.rfBankNum)))
        secondReadPipe(bank).valid := bankReadS.map(_(bank)).reduce(_ || _)
        rf.readwritePorts.last.address := Mux(
          ramWriteValid,
          writeAddress,
          secondReadPipe(bank).bits.address
        )
        rf.readwritePorts.last.enable := ramWriteValid || secondReadPipe(bank).valid
        rf.readwritePorts.last.isWrite := ramWriteValid
        rf.readwritePorts.last.writeData := writeData
        AssertProperty(BoolSequence(!(writeValid && secondReadPipe(bank).valid)))
    }

    rf
  }

  val initRecord: ValidIO[VRFWriteReport] = WireDefault(0.U.asTypeOf(Valid(new VRFWriteReport(parameter))))
  initRecord.valid := true.B
  initRecord.bits := instructionWriteReport.bits
  // @todo @Clo91eaf VRF ready signal for performance.
  val freeRecord: UInt = VecInit(chainingRecord.map(!_.valid)).asUInt
  val recordFFO:  UInt = ffo(freeRecord)
  val recordEnq:  UInt = Wire(UInt((parameter.chainingSize + 1).W))
  val olderCheck = chainingRecord.map(
    re => !re.valid || instIndexL(re.bits.instIndex, instructionWriteReport.bits.instIndex)
  ).reduce(_ && _)
  // handle VRF hazard
  // @todo @Clo91eaf VRF ready signal for performance.
  instructionWriteReport.ready := freeRecord.orR && olderCheck
  recordEnq := Mux(
    // 纯粹的lsu指令的记录不需要ready
    instructionWriteReport.valid,
    recordFFO,
    0.U((parameter.chainingSize + 1).W)
  )
  vrfAllocateIssue := freeRecord.orR && olderCheck

  val writePort: Seq[ValidIO[VRFWriteRequest]] = Seq(writePipe)
  val writeOH = writePort.map(p => UIntToOH((p.bits.vd ## p.bits.offset)(parameter.vrfOffsetBits + 3 - 1, 0)))
  val loadUnitReadPorts: Seq[DecoupledIO[VRFReadRequest]] = Seq(readRequests.last)
  val loadReadOH: Seq[UInt] =
    loadUnitReadPorts.map(p => UIntToOH((p.bits.vs ## p.bits.offset)(parameter.vrfOffsetBits + 3 - 1, 0)))
  Seq(chainingRecord, chainingRecordCopy).foreach{ recordVec => recordVec.zipWithIndex.foreach {
    case (record, i) =>
      val dataInLsuQueue = ohCheck(loadDataInLSUWriteQueue, record.bits.instIndex, parameter.chainingSize)
      // elementMask update by write
      val writeUpdateValidVec: Seq[Bool] = writePort.map( p =>
        p.fire && p.bits.instructionIndex === record.bits.instIndex && p.bits.mask(3)
      )
      val writeUpdate1HVec: Seq[UInt] =writeOH.zip(writeUpdateValidVec).map{ case (oh, v) => Mux(v, oh, 0.U) }
      // elementMask update by read of store instruction
      val loadUpdateValidVec = loadUnitReadPorts.map( p =>
        p.fire && p.bits.instructionIndex === record.bits.instIndex && record.bits.st
      )
      val loadUpdate1HVec: Seq[UInt] = loadReadOH.zip(loadUpdateValidVec).map{ case (oh, v) => Mux(v, oh, 0.U) }
      // all elementMask update
      val elementUpdateValid: Bool = (writeUpdateValidVec ++ loadUpdateValidVec).reduce(_ || _)
      val elementUpdate1H: UInt = (writeUpdate1HVec ++ loadUpdate1HVec).reduce(_ | _)
      val dataInLaneCheck = ohCheck(dataInLane, record.bits.instIndex, parameter.chainingSize)
      val laneLastReport = ohCheck(instructionLastReport, record.bits.instIndex, parameter.chainingSize)
      val topLastReport = ohCheck(lsuLastReport, record.bits.instIndex, parameter.chainingSize)
      // only wait lane clear
      val waitLaneClear =
        record.bits.state.stFinish && record.bits.state.wWriteQueueClear &&
          record.bits.state.wLaneLastReport && record.bits.state.wTopLastReport
      val stateClear: Bool = waitLaneClear && record.bits.state.wLaneClear

      when(topLastReport) {
        record.bits.state.stFinish := true.B
        record.bits.state.wTopLastReport := true.B
      }

      when(laneLastReport) {
        record.bits.state.wLaneLastReport := true.B
      }

      when(record.bits.state.stFinish && !dataInLsuQueue) {
        record.bits.state.wWriteQueueClear := true.B
      }

      when(waitLaneClear && !dataInLaneCheck) {
        record.bits.state.wLaneClear := true.B
      }

      when(stateClear) {
        record.valid := false.B
      }

      when(recordEnq(i)) {
        record := initRecord
      }.elsewhen(elementUpdateValid) {
        record.bits.elementMask := record.bits.elementMask | elementUpdate1H
      }
  }}
  // @todo @qinjun-li DV&RTL, here is a bug, LSU hazard
  //       @Clo91eaf original of LSU hazard is coming here.
  val hazardVec: Seq[IndexedSeq[(Bool, Bool)]] = chainingRecordCopy.init.zipWithIndex.map { case (sourceRecord, sourceIndex) =>
    chainingRecordCopy.drop(sourceIndex + 1).zipWithIndex.map { case (sinkRecord, _) =>
      val recordSeq: Seq[ValidIO[VRFWriteReport]] = Seq(sourceRecord, sinkRecord)
      val isLoad = recordSeq.map(r => r.valid && r.bits.ls && !r.bits.st)
      val isStore = recordSeq.map(r => r.valid && r.bits.ls && r.bits.st)
      val isSlow = recordSeq.map(r => r.valid && r.bits.slow)
      // todo: 重叠而不是相等
      val samVd =
        (sourceRecord.bits.vd.valid && sinkRecord.bits.vd.valid) &&
          (sourceRecord.bits.vd.bits(4, 3) === sinkRecord.bits.vd.bits(4, 3)) &&
          // Want to write the same datapath(There are 0 in the same position)
          (~(sourceRecord.bits.elementMask | sinkRecord.bits.elementMask)).asUInt.orR
      val sourceVdEqSinkVs: Bool = sourceRecord.bits.vd.valid && (
        (sourceRecord.bits.vd.bits === sinkRecord.bits.vs2) ||
          ((sourceRecord.bits.vd.bits === sinkRecord.bits.vs1.bits) && sinkRecord.bits.vs1.valid)
      )
      val sinkVdEqSourceVs: Bool = sinkRecord.bits.vd.valid && (
        (sinkRecord.bits.vd.bits === sourceRecord.bits.vs2) ||
          ((sinkRecord.bits.vd.bits === sourceRecord.bits.vs1.bits) && sourceRecord.bits.vs1.valid)
      )
      // source更新
      val older = instIndexLE(sinkRecord.bits.instIndex, sourceRecord.bits.instIndex)
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

  writeCheck.zip(writeAllow).foreach{ case (check, allow) =>
    allow := chainingRecordCopy.zip(recordValidVec).map { case (record, rv) =>
      val checkModule = Instantiate(new WriteCheck(parameter))
      checkModule.check := check
      checkModule.record := record
      checkModule.recordValid := rv
      checkModule.checkResult
    }.reduce(_ && _)
  }

  layer.block(layers.Verification) {
    val probeWire = Wire(new VRFProbe(parameter))
    define(vrfProbe, ProbeValue(probeWire))

    probeWire.valid := writePipe.valid
    probeWire.requestVd := writePipe.bits.vd
    probeWire.requestOffset := writePipe.bits.offset
    probeWire.requestMask := writePipe.bits.mask
    probeWire.requestData := writePipe.bits.data
    probeWire.requestInstruction := writePipe.bits.instructionIndex
  }
}
