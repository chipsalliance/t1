// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import org.chipsalliance.t1.rtl.{LSUWriteCheck, VRFReadRequest, VRFWriteReport, VRFWriteRequest, ffo, instIndexL, ohCheck}

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
  chainingSize:  Int,
  portFactor:    Int)
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

  val writeCheck: Vec[LSUWriteCheck] = IO(Vec(parameter.chainingSize + 2, Input(new LSUWriteCheck(
    parameter.regNumBits,
    parameter.vrfOffsetBits,
    parameter.instructionIndexBits,
    parameter.datapathWidth
  ))))

  val writeAllow: Vec[Bool] = IO(Vec(parameter.chainingSize + 2, Output(Bool())))

  /** when instruction is fired, record it in the VRF for chaining. */
  val instructionWriteReport: DecoupledIO[VRFWriteReport] = IO(Flipped(Decoupled(new VRFWriteReport(parameter))))

  /** similar to [[flush]]. */
  val instructionLastReport: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  /** data in write queue */
  val dataInWriteQueue: UInt = IO(Input(UInt(parameter.chainingSize.W)))

  val crossWriteBusClear: Bool = IO(Input(Bool()))

  val lsuMaskGroupChange: UInt = IO(Input(UInt(parameter.chainingSize.W)))
  val writeReadyForLsu: Bool = IO(Output(Bool()))
  val vrfReadyToStore: Bool = IO(Output(Bool()))

  /** we can only chain LSU instructions, after [[LSU.writeQueueVec]] is cleared. */
  val loadDataInLSUWriteQueue: UInt = IO(Input(UInt(parameter.chainingSize.W)))
  // todo: delete
  dontTouch(write)
  val portFireCount: UInt = PopCount(VecInit(readRequests.map(_.fire) :+ write.fire))
  dontTouch(portFireCount)

  // Add one more record slot to prevent there is no free slot when the instruction comes in
  // (the slot will die a few cycles later than the instruction)
  val chainingRecord: Vec[ValidIO[VRFWriteReport]] = RegInit(
    VecInit(Seq.fill(parameter.chainingSize + 1)(0.U.asTypeOf(Valid(new VRFWriteReport(parameter)))))
  )
  val recordValidVec: Seq[Bool] = chainingRecord.map(r => !r.bits.elementMask.andR && r.valid)

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
      val checkResult:  Bool =
        chainingRecord.zip(recordValidVec).zipWithIndex.map {
          case ((r, f), recordIndex) =>
            val checkModule = Module(new ChainingCheck(parameter))
              .suggestName(s"ChainingCheck_readPort${i}_record${recordIndex}")
            checkModule.read := v.bits
            checkModule.readRecord := readRecord
            checkModule.record := r
            checkModule.recordValid := f
            checkModule.checkResult
        }.reduce(_ && _)
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
  val recordEnq:  UInt = Wire(UInt((parameter.chainingSize + 1).W))
  // handle VRF hazard
  // TODO: move to [[V]]
  instructionWriteReport.ready := true.B
  recordEnq := Mux(
    // 纯粹的lsu指令的记录不需要ready
    instructionWriteReport.valid,
    recordFFO,
    0.U((parameter.chainingSize + 1).W)
  )

  val writePort: Seq[DecoupledIO[VRFWriteRequest]] = Seq(write)
  val writeOH = writePort.map(p => UIntToOH((p.bits.vd ## p.bits.offset)(parameter.vrfOffsetBits + 3 - 1, 0)))
  val loadUnitReadPorts: Seq[DecoupledIO[VRFReadRequest]] = Seq(readRequests.last)
  val loadReadOH: Seq[UInt] =
    loadUnitReadPorts.map(p => UIntToOH((p.bits.vs ## p.bits.offset)(parameter.vrfOffsetBits + 3 - 1, 0)))
  chainingRecord.zipWithIndex.foreach {
    case (record, i) =>
      val dataIndexWriteQueue = ohCheck(dataInWriteQueue, record.bits.instIndex, parameter.chainingSize)
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
      val queueClear = !dataIndexWriteQueue
      val busClear = crossWriteBusClear || !record.bits.crossWrite
      when(ohCheck(instructionLastReport, record.bits.instIndex, parameter.chainingSize)) {
        when(record.bits.ls) {
          record.bits.stFinish := true.B
        }.otherwise {
          record.bits.wWriteQueueClear := true.B
        }
      }
      when(record.bits.stFinish && (!dataInLsuQueue || record.bits.st) && record.valid) {
        when(dataIndexWriteQueue) {
          record.bits.wWriteQueueClear
        } otherwise {
          record.valid := false.B
        }
      }
      when(record.bits.wWriteQueueClear) {
        when(busClear) {
          record.bits.wBusClear := true.B
        }
        when((busClear || record.bits.wBusClear) && queueClear) {
          record.bits.wQueueClear := true.B
        }
      }
      when(record.bits.wWriteQueueClear && record.bits.wBusClear && record.bits.wQueueClear) {
        record.valid := false.B
      }
      when(recordEnq(i)) {
        record := initRecord
      }.elsewhen(elementUpdateValid) {
        record.bits.elementMask := record.bits.elementMask | elementUpdate1H
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

  writeCheck.zip(writeAllow).foreach{ case (check, allow) =>
    val checkOH: UInt = UIntToOH((check.vd ## check.offset)(parameter.vrfOffsetBits + 3 - 1, 0))
    allow := chainingRecord.map { record =>
      // 先看新老
      val older = instIndexL(check.instructionIndex, record.bits.instIndex)
      val sameInst = check.instructionIndex === record.bits.instIndex

      val waw: Bool = record.bits.vd.valid && check.vd(4, 3) === record.bits.vd.bits(4, 3) &&
        (checkOH & record.bits.elementMask) === 0.U
      !((!older && waw) && !sameInst && record.valid)
    }.reduce(_ && _)
  }
}
