// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.vrf

import mainargs.TokensReader
import me.jiuyang.zaozi.*
import me.jiuyang.zaozi.default.{*, given}
import me.jiuyang.zaozi.reftpe.*
import me.jiuyang.zaozi.valuetpe.*
import me.jiuyang.stdlib.*
import me.jiuyang.stdlib.default.{*, given}
import org.chipsalliance.t1.rtl.zvma.*
import org.llvm.mlir.scalalib.capi.ir.{*, given}

import java.lang.foreign.Arena

sealed trait RamType
object RamType:
  given upickle.default.ReadWriter[RamType] = upickle.default.ReadWriter.merge(
    upickle.default.macroRW[p0rw.type],
    upickle.default.macroRW[p0rp1w.type],
    upickle.default.macroRW[p0rwp1rw.type]
  )
  given TokensReader.Simple[RamType]:
    def shortName               = "ram-type"
    def read(strs: Seq[String]) = Right(
      strs.head match
        case "p0rw"     => p0rw
        case "p0rp1w"   => p0rp1w
        case "p0rwp1rw" => p0rwp1rw
    )

  case object p0rw extends RamType

  case object p0rp1w extends RamType

  case object p0rwp1rw extends RamType

object VRFParam:
  given upickle.default.ReadWriter[VRFParam] = upickle.default.macroRW

/** Parameter for [[Lane]].
  * @param vLen
  *   VLEN
  * @param laneNumber
  *   how many lanes in the vector processor
  * @param datapathWidth
  *   ELEN
  * @param chainingSize
  *   how many instructions can be chained
  * @param portFactor
  *   How many ELEN(32 in current design) can be accessed for one memory port accessing.
  *
  * @note:
  *   if increasing portFactor:
  *   - we can have more memory ports.
  *   - a big VRF memory is split into small memories, the shell of memory contributes more area...
  *
  * TODO: add ECC cc @sharzyL 8bits -> 5bits 16bits -> 6bits 32bits -> 7bits
  */
case class VRFParam(
  vLen:          Int,
  laneNumber:    Int,
  datapathWidth: Int,
  chainingSize:  Int,
  portFactor:    Int,
  ramType:       RamType)
    extends Parameter:

  val chaining1HBits: Int = 2 << chiselLog2Ceil(chainingSize)

  /** See documentation for VRF. chainingSize * 3 + 1 + 1: 3 read /slot + maskedWrite + lsu read 0: maskedWrite last:
    * lsu read Each element represents a read port of vrf, The number inside is which of the above requests will share
    * this port.
    */
  val connectTree: Seq[Seq[Int]] = Seq.tabulate(chainingSize * 3 + 1 + 1) { i => Seq(i) }
  val vrfReadPort: Int           = connectTree.size

  /** VRF index number is 32, defined in spec. */
  val regNum: Int = 32

  /** The hardware width of [[regNum]] */
  val regNumBits:           Int = chiselLog2Ceil(regNum)
  // One more bit for sorting
  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = chiselLog2Ceil(chainingSize) + 1

  /** the width of VRF banked together. */
  val rowWidth: Int = datapathWidth * portFactor

  /** the depth of memory */
  val rfDepth: Int = vLen * regNum / rowWidth / laneNumber

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = chiselLog2Ceil(singleGroupSize)

  // 用data path width 的 ram 应该是不会变了
  val ramWidth:  Int = datapathWidth
  val rfBankNum: Int = rowWidth / ramWidth

  /** used to instantiate VRF. */
  val VLMaxWidth: Int = chiselLog2Ceil(vLen) + 1

  /** bits of mask group counter */
  val maskGroupCounterBits: Int = chiselLog2Ceil(vLen / datapathWidth)

  val vlWidth: Int = chiselLog2Ceil(vLen)

  val elementSize: Int = vLen * 8 / datapathWidth / laneNumber

  // todo: 4 bit for ecc
  val memoryWidth: Int = ramWidth + 4

  // 1: pipe access request + 1: SyncReadMem
  val vrfReadLatency = 2

class VRFReadRequest(regNumBits: Int, offsetBits: Int, instructionIndexBits: Int) extends Bundle:

  /** address to access VRF.(v0, v1, v2, ...) */
  val vs = Aligned(UInt(regNumBits))

  /** read vs1 vs2 vd? */
  val readSource = Aligned(UInt(2))

  /** the offset of VRF access. TODO: rename to offsetForVSInLane
    */
  val offset = Aligned(UInt(offsetBits))

  /** index for record the age of instruction, designed for handling RAW hazard */
  val instructionIndex = Aligned(UInt(instructionIndexBits))

class VRFWriteRequest(regNumBits: Int, offsetBits: Int, instructionIndexSize: Int, dataPathWidth: Int) extends Bundle:

  /** address to access VRF.(v0, v1, v2, ...) */
  val vd = Aligned(UInt(regNumBits))

  /** the offset of VRF access. */
  val offset = Aligned(UInt(offsetBits))

  /** write mask in byte. */
  val mask = Aligned(UInt(dataPathWidth / 8))

  /** data to write to VRF. */
  val data = Aligned(UInt(dataPathWidth))

  /** this is the last write of this instruction TODO: rename to isLast.
    */
  val last = Aligned(Bool())

  /** used to update the record in VRF. */
  val instructionIndex = Aligned(UInt(instructionIndexSize))

class LSUWriteCheck(regNumBits: Int, offsetBits: Int, instructionIndexSize: Int) extends Bundle:

  /** address to access VRF.(v0, v1, v2, ...) */
  val vd = Aligned(UInt(regNumBits))

  /** the offset of VRF access. */
  val offset = Aligned(UInt(offsetBits))

  /** used to update the record in VRF. */
  val instructionIndex = Aligned(UInt(instructionIndexSize))

class VRFInstructionState extends Bundle:
  val stFinish         = Aligned(Bool())
  // execute finish, wait for write queue clear
  val wWriteQueueClear = Aligned(Bool())
  val wLaneLastReport  = Aligned(Bool())
  val wTopLastReport   = Aligned(Bool())
  val wLaneClear       = Aligned(Bool())

class VRFWriteReport(param: VRFParam) extends Bundle:
  // 8 reg/group; which group?
  val vd               = Aligned(Valid(UInt(param.regNumBits)))
  val vs1              = Aligned(Valid(UInt(param.regNumBits)))
  val vs2              = Aligned(UInt(param.regNumBits))
  val instIndex        = Aligned(UInt(param.instructionIndexBits))
  val ls               = Aligned(Bool())
  val st               = Aligned(Bool())
  val gather           = Aligned(Bool())
  // unaligned read vs1: compress read vs1 as 1bit, gather16 read vs1 as 16bit
  val unalignedReadVs1 = Aligned(Bool())
  // instruction will cross write
  val crossWrite       = Aligned(Bool())
  // instruction will cross read
  val crossRead        = Aligned(Bool())
  // index type lsu
  val indexType        = Aligned(Bool())
  // 乘加
  val ma               = Aligned(Bool())
  // Read everything, but write very little
  val onlyRead         = Aligned(Bool())
  // 慢指令 mask unit
  val slow             = Aligned(Bool())
  val oooWrite         = Aligned(Bool())
  // which element will access(write or store read)
  // true: No access or access has been completed
  val elementMask      = Aligned(UInt(param.elementSize))
  val state            = Aligned(new VRFInstructionState)

class VRFReadPipe(size: Int) extends Bundle:
  val address = Aligned(UInt(chiselLog2Ceil(size)))

class VRFProbe(parameter: VRFParam) extends Bundle:
  val valid              = Aligned(Bool())
  val requestVd          = Aligned(UInt(parameter.regNumBits))
  val requestOffset      = Aligned(UInt(parameter.vrfOffsetBits))
  val requestMask        = Aligned(UInt(parameter.datapathWidth / 8))
  val requestData        = Aligned(UInt(parameter.datapathWidth))
  val requestInstruction = Aligned(UInt(parameter.instructionIndexBits))

class VRFLayers(parameter: VRFParam) extends LayerInterface(parameter):
  def layers = Seq.empty

class VRFInterface(parameter: VRFParam) extends HWBundle(parameter):
  val clock = Flipped(Clock())
  val reset = Flipped(Reset())

  /** VRF read requests ready will couple from valid from [[readRequests]], ready is asserted when higher priority valid
    * is less than 2.
    */
  val readRequests =
    Flipped(
      Vec(
        parameter.vrfReadPort,
        Decoupled(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
      )
    )

  // @todo @Clo91eaf should pull read&write check
  //                 performance checker, in the difftest, it can observe all previous events, and know should it be stalled?
  //                 then, we can know the accuracy read&write check hardware signal.
  // 3 * slot + 2 cross read
  val readCheck =
    Flipped(
      Vec(
        parameter.chainingSize * 3 + 2,
        new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
      )
    )

  val readCheckResult = Aligned(Vec(parameter.chainingSize * 3 + 2, Bool()))

  /** VRF read results. */
  val readResults = Aligned(Vec(parameter.vrfReadPort, UInt(parameter.datapathWidth)))

  /** VRF write requests ready will couple from valid from [[write]], ready is asserted when higher priority valid is
    * less than 2. TODO: rename to `vrfWriteRequests`
    */
  val write = Flipped(
    Decoupled(
      new VRFWriteRequest(
        parameter.regNumBits,
        parameter.vrfOffsetBits,
        parameter.instructionIndexBits,
        parameter.datapathWidth
      )
    )
  )

  val writeCheck = Flipped(
    Vec(
      parameter.chainingSize + 1,
      new LSUWriteCheck(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits)
    )
  )

  val writeAllow = Aligned(Vec(parameter.chainingSize + 1, Bool()))

  /** when instruction is fired, record it in the VRF for chaining. */
  val instructionWriteReport = Flipped(Valid(new VRFWriteReport(parameter)))

  /** similar to [[flush]]. */
  val instructionLastReport = Flipped(UInt(parameter.chaining1HBits))

  val lsuLastReport = Flipped(UInt(parameter.chaining1HBits))

  val vrfSlotRelease = Aligned(UInt(parameter.chaining1HBits))

  val instructionValid = Aligned(UInt(parameter.chaining1HBits))

  val dataInLane = Flipped(UInt(parameter.chaining1HBits))

  val writeReadyForLsu = Aligned(Bool())
  val vrfReadyToStore  = Aligned(Bool())

  /** we can only chain LSU instructions, after [[LSU.writeQueueVec]] is cleared. */
  val loadDataInLSUWriteQueue = Flipped(UInt(parameter.chaining1HBits))

  val vrfProbe = Aligned(new VRFProbe(parameter))

class VRFProbeInterface(parameter: VRFParam) extends DVBundle[VRFParam, VRFLayers](parameter)

/** Vector Register File. contains logic:
  *   - RAM as VRF.
  *   - chaining detection
  *   - bank split
  *   - out of order chaining hazard detection: TODO: move to Top.
  *
  * TODO: probe each ports to benchmark the bandwidth.
  */
@generator
object VRF extends Generator[VRFParam, VRFLayers, VRFInterface, VRFProbeInterface]:
  override def moduleName(parameter: VRFParam): String = "VRF"

  def architecture(parameter: VRFParam) =
    val io = summon[Interface[VRFInterface]]

    given Ref[Clock] = io.clock
    given Ref[Reset] = io.reset

    def andReduce(data: Seq[Referable[Bool]]): Referable[Bool] =
      data.map(Node(_)).reduce(_ & _)

    def orReduce(data: Seq[Referable[Bool]]): Referable[Bool] =
      data.map(Node(_)).reduce(_ | _)

    def orReduceUInt(data: Seq[Referable[UInt]]): Referable[UInt] =
      data.map(d => Node(d.asBits)).reduce(_ | _).asUInt

    val readRequests     = io.readRequests.toSeq
    val readCheck        = io.readCheck.toSeq
    val readCheckResult  = io.readCheckResult.toSeq
    val readResults      = io.readResults.toSeq
    val writeCheck       = io.writeCheck.toSeq
    val writeAllow       = io.writeAllow.toSeq
    val rfBankNumLog2    = if parameter.rfBankNum == 1 then 0 else log2Ceil(parameter.rfBankNum)
    val rfDepthLog2      = chiselLog2Ceil(parameter.rfDepth)
    val readAddressVec   = readRequests.map { r =>
      val address = (r.bits.vs.asBits ## r.bits.offset.asBits).asUInt
      if rfBankNumLog2 == 0 then address else (address >> rfBankNumLog2).asBits.bits(rfDepthLog2 - 1, 0).asUInt
    }
    val validRecordType  = Valid(new VRFWriteReport(parameter))
    val readPipeType     = Valid(new VRFReadPipe(parameter.rfDepth))
    val writeRequestType = Valid(
      new VRFWriteRequest(
        parameter.regNumBits,
        parameter.vrfOffsetBits,
        parameter.instructionIndexBits,
        parameter.datapathWidth
      )
    )

    // reset sram
    val sramReady      = RegInit(false.B)
    val sramResetCount = RegInit(0.U(chiselLog2Ceil(parameter.rfDepth)))
    val resetValid     = !sramReady
    when(resetValid):
      sramResetCount := ((sramResetCount + 1.U(sramResetCount.width)).asBits.bits(sramResetCount.width - 1, 0)).asUInt
      when(sramResetCount.asBits.andR):
        sramReady := true.B
    // TODO: add Chaining Check Probe

    val writeIndex = (io.write.bits.vd.asBits ## io.write.bits.offset.asBits).asUInt
    val writeBank  =
      if parameter.rfBankNum == 1 then 1.U(1)
      else UIntToOH(writeIndex.asBits.bits(rfBankNumLog2 - 1, 0).asUInt)

    // Add one more record slot to prevent there is no free slot when the instruction comes in
    // (the slot will die a few cycles later than the instruction)
    val chainingRecord     = Seq.tabulate(parameter.chainingSize + 1) { _ =>
      RegInit(0.B(validRecordType.width).asType(validRecordType))
    }
    val chainingRecordCopy = Seq.tabulate(parameter.chainingSize + 1) { _ =>
      RegInit(0.B(validRecordType.width).asType(validRecordType))
    }
    val recordRelease      = Wire(Vec(parameter.chainingSize + 1, UInt(parameter.chaining1HBits)))
    recordRelease.toSeq.foreach(_ := 0.U(parameter.chaining1HBits))
    val recordValidVec     = chainingRecord.map(r => !r.bits.elementMask.asBits.andR & r.valid)

    // first read
    val bankReadF   = Wire(Vec(parameter.vrfReadPort, UInt(parameter.rfBankNum)))
    val bankReadS   = Wire(Vec(parameter.vrfReadPort, UInt(parameter.rfBankNum)))
    val readResultF = Wire(Vec(parameter.rfBankNum, UInt(parameter.ramWidth)))
    val readResultS = Wire(Vec(parameter.rfBankNum, UInt(parameter.ramWidth)))

    val firstReadPipe  = Seq.tabulate(parameter.rfBankNum) { _ =>
      RegInit(0.B(readPipeType.width).asType(readPipeType))
    }
    val secondReadPipe = Seq.tabulate(parameter.rfBankNum) { _ =>
      RegInit(0.B(readPipeType.width).asType(readPipeType))
    }
    val writePipe      = RegInit(0.B(writeRequestType.width).asType(writeRequestType))
    writePipe.valid := io.write.fire
    when(io.write.fire):
      writePipe.bits := io.write.bits
    val writeBankPipe = RegInit(0.U(writeBank.width))
    writeBankPipe := writeBank

    // lane chaining check
    readCheck.zip(readCheckResult).foreach { case (req, res) =>
      val recordSelect = chainingRecord
      // 先找到自的record
      val readRecord   = mux1H(
        recordSelect.map(_.bits.instIndex === req.instructionIndex),
        recordSelect.map(_.bits.asBits.asUInt)
      ).asBits.asType(recordSelect.head.bits.getType)
      res := andReduce(
        recordSelect
          .zip(recordValidVec)
          .zipWithIndex
          .map { case ((r, f), _) =>
            val checkModule = ChainingCheck.instantiate(parameter)
            checkModule.io.read        := req
            checkModule.io.readRecord  := readRecord
            checkModule.io.record      := r
            checkModule.io.recordValid := f
            checkModule.io.checkResult
          }
      )
    }

    val checkSize                       = readRequests.size
    val (firstOccupied, secondOccupied) = readRequests.zipWithIndex.foldLeft(
      (0.U(parameter.rfBankNum): Referable[UInt], 0.U(parameter.rfBankNum): Referable[UInt])
    ) { case ((o, t), (v, i)) =>
      val recordSelect        = if i < (checkSize / 2) then chainingRecord else chainingRecordCopy
      // 先找到自的record
      val readRecord          = mux1H(
        recordSelect.map(_.bits.instIndex === v.bits.instructionIndex),
        recordSelect.map(_.bits.asBits.asUInt)
      ).asBits.asType(recordSelect.head.bits.getType)
      // @todo @Clo91eaf read&write in the same cycle.
      val portConflictCheck   = Wire(Bool())
      val checkResult         = Option.when(i == (readRequests.size - 1)) {
        andReduce(
          recordSelect
            .zip(recordValidVec)
            .zipWithIndex
            .map { case ((r, f), _) =>
              val checkModule = ChainingCheck.instantiate(parameter)
              checkModule.io.read        := v.bits
              checkModule.io.readRecord  := readRecord
              checkModule.io.record      := r
              checkModule.io.recordValid := f
              checkModule.io.checkResult
            }
        ) & portConflictCheck
      }
      val validCorrect        = if i == (readRequests.size - 1) then v.valid & checkResult.get else v.valid
      val address             = (v.bits.vs.asBits ## v.bits.offset.asBits).asUInt
      // select bank
      val bank                =
        if parameter.rfBankNum == 1 then 1.U(1)
        else UIntToOH(address.asBits.bits(rfBankNumLog2 - 1, 0).asUInt)
      val pipeBank            = pipeUInt(bank, parameter.vrfReadLatency)
      val bankCorrect         = validCorrect ? (bank, 0.U(parameter.rfBankNum))
      val readPortCheckSelect = parameter.ramType match
        case RamType.p0rw     => o
        case RamType.p0rp1w   => o
        case RamType.p0rwp1rw => t
      portConflictCheck := (parameter.ramType match
        case RamType.p0rw => true.B
        case _            =>
          !(
            (io.write.valid & (bank === writeBank) & (io.write.bits.vd === v.bits.vs) & (io.write.bits.offset === v.bits.offset)) |
              (writePipe.valid & (bank === writeBankPipe) & (writePipe.bits.vd === v.bits.vs) & (writePipe.bits.offset === v.bits.offset))
          ))
      val portReady           =
        if i == (readRequests.size - 1) then (bank.asBits & (~readPortCheckSelect.asBits)).orR & checkResult.get
        else
          // @todo @Clo91eaf read check port is ready.
          // if there are additional read port for the bank.
          (bank.asBits & (~readPortCheckSelect.asBits)).orR
      v.ready := portReady & sramReady
      val firstUsed = (bank.asBits & o.asBits).orR
      bankReadF(i) := (bankCorrect.asBits & (~o.asBits)).asUInt
      bankReadS(i) := ((bankCorrect.asBits & (~t.asBits)) & o.asBits).asUInt
      val pipeFirstUsed = pipeBool(firstUsed, parameter.vrfReadLatency)
      val pipeFire      = pipeBool(v.fire, parameter.vrfReadLatency)
      readResults(i) := mux1H(
        Seq(!pipeFirstUsed & pipeFire, pipeFirstUsed & pipeFire),
        Seq(mux1H(pipeBank, readResultF.toSeq), mux1H(pipeBank, readResultS.toSeq))
      )
      ((o.asBits | bankCorrect.asBits).asUInt, ((bankCorrect.asBits & o.asBits) | t.asBits).asUInt)
    }
    // @todo @Clo91eaf check write port is ready.
    io.write.ready := sramReady & (parameter.ramType match
      case RamType.p0rw     => (writeBank.asBits & (~firstOccupied.asBits)).orR
      case RamType.p0rp1w   => true.B
      case RamType.p0rwp1rw => (writeBank.asBits & (~secondOccupied.asBits)).orR)

    val writeData    = resetValid ? (0.U(parameter.datapathWidth), writePipe.bits.data)
    val writeAddress =
      (resetValid ? (
        sramResetCount,
        (if rfBankNumLog2 == 0 then (writePipe.bits.vd.asBits ## writePipe.bits.offset.asBits).asUInt
         else
           ((writePipe.bits.vd.asBits ## writePipe.bits.offset.asBits).asUInt >> rfBankNumLog2).asBits
             .bits(rfDepthLog2 - 1, 0)
             .asUInt
        )
      )).asBits.bits(rfDepthLog2 - 1, 0).asUInt
    // @todo @Clo91eaf VRF write&read singal should be captured here.
    // @todo           in the future, we need to maintain a layer to trace the original requester to each read&write.
    parameter.ramType match
      case RamType.p0rw     =>
        Seq.tabulate(parameter.rfBankNum) { bank =>
          val rf            = SRAM.instantiate(SRAMParameter(depth = parameter.rfDepth, width = parameter.memoryWidth))
          val writeValid    = writePipe.valid & writeBankPipe.asBits.bit(bank)
          val ramWriteValid = writeValid | resetValid
          rf.io.clock                      := io.clock
          firstReadPipe(bank).bits.address := mux1H(
            bankReadF.toSeq.map(_.asBits.bit(bank)),
            readAddressVec
          ).asBits.bits(rfDepthLog2 - 1, 0).asUInt
          firstReadPipe(bank).valid        := orReduce(bankReadF.toSeq.map(_.asBits.bit(bank)))
          rf.io.address                    := (ramWriteValid ? (writeAddress, firstReadPipe(bank).bits.address)).asBits
            .bits(rfDepthLog2 - 1, 0)
            .asUInt
          rf.io.enable                     := ramWriteValid | firstReadPipe(bank).valid
          rf.io.isWrite                    := ramWriteValid
          rf.io.writeData                  := (0.U(parameter.memoryWidth - parameter.datapathWidth).asBits ## writeData.asBits)
          readResultF(bank)                := rf.io.readData.bits(parameter.ramWidth - 1, 0).asUInt
          readResultS(bank).dontCare()
        }
      case RamType.p0rp1w   =>
        Seq.tabulate(parameter.rfBankNum) { bank =>
          val rf            = SRAM1R1W.instantiate(SRAMParameter(depth = parameter.rfDepth, width = parameter.memoryWidth))
          val ramWriteValid = (writePipe.valid & writeBankPipe.asBits.bit(bank)) | resetValid
          rf.io.clock                      := io.clock
          firstReadPipe(bank).bits.address := mux1H(
            bankReadF.toSeq.map(_.asBits.bit(bank)),
            readAddressVec
          ).asBits.bits(rfDepthLog2 - 1, 0).asUInt
          firstReadPipe(bank).valid        := orReduce(bankReadF.toSeq.map(_.asBits.bit(bank)))
          // connect readPorts
          rf.io.readAddress                := firstReadPipe(bank).bits.address
          rf.io.readEnable                 := firstReadPipe(bank).valid
          readResultF(bank)                := rf.io.readData.bits(parameter.ramWidth - 1, 0).asUInt
          readResultS(bank).dontCare()

          rf.io.writeEnable  := ramWriteValid
          rf.io.writeAddress := writeAddress
          rf.io.writeData    := (0.U(parameter.memoryWidth - parameter.datapathWidth).asBits ## writeData.asBits)
        }
      case RamType.p0rwp1rw =>
        Seq.tabulate(parameter.rfBankNum) { bank =>
          val rf            = SRAM2RW.instantiate(SRAMParameter(depth = parameter.rfDepth, width = parameter.memoryWidth))
          val writeValid    = writePipe.valid & writeBankPipe.asBits.bit(bank)
          val ramWriteValid = writeValid | resetValid
          rf.io.clock0                     := io.clock
          rf.io.clock1                     := io.clock
          firstReadPipe(bank).bits.address := mux1H(
            bankReadF.toSeq.map(_.asBits.bit(bank)),
            readAddressVec
          ).asBits.bits(rfDepthLog2 - 1, 0).asUInt
          firstReadPipe(bank).valid        := orReduce(bankReadF.toSeq.map(_.asBits.bit(bank)))
          // connect readPorts
          rf.io.address0                   := firstReadPipe(bank).bits.address
          rf.io.enable0                    := firstReadPipe(bank).valid
          rf.io.isWrite0                   := false.B
          rf.io.writeData0.dontCare()

          readResultF(bank) := rf.io.readData0.bits(parameter.ramWidth - 1, 0).asUInt
          readResultS(bank) := rf.io.readData1.bits(parameter.ramWidth - 1, 0).asUInt

          secondReadPipe(bank).bits.address := mux1H(
            bankReadS.toSeq.map(_.asBits.bit(bank)),
            readAddressVec
          ).asBits.bits(rfDepthLog2 - 1, 0).asUInt
          secondReadPipe(bank).valid        := orReduce(bankReadS.toSeq.map(_.asBits.bit(bank)))
          rf.io.address1                    := (ramWriteValid ? (writeAddress, secondReadPipe(bank).bits.address)).asBits
            .bits(rfDepthLog2 - 1, 0)
            .asUInt
          rf.io.enable1                     := ramWriteValid | secondReadPipe(bank).valid
          rf.io.isWrite1                    := ramWriteValid
          rf.io.writeData1                  := (0.U(parameter.memoryWidth - parameter.datapathWidth).asBits ## writeData.asBits)
        }

    val initRecord = Wire(validRecordType)
    initRecord       := 0.B(validRecordType.width).asType(validRecordType)
    initRecord.valid := true.B
    initRecord.bits  := io.instructionWriteReport.bits
    // @todo @Clo91eaf VRF ready signal for performance.
    val freeRecord = chainingRecord.map(!_.valid).toVec.asBits.asUInt
    val recordFFO  = ffo(freeRecord)
    val recordEnq  = io.instructionWriteReport.valid ? (
      recordFFO,
      0.U(parameter.chainingSize + 1)
    )

    val writePort         = Seq(writePipe)
    val loadUnitReadPorts = Seq(readRequests.last)
    Seq(chainingRecord, chainingRecordCopy).foreach { recordVec =>
      recordVec.zipWithIndex.foreach { case (record, i) =>
        // read write one hot base on base address
        val writeOH             = writePort.map(p =>
          UIntToOH((((p.bits.vd - record.bits.vd.bits).asBits.bits(2, 0)) ## p.bits.offset.asBits).asUInt)
        )
        val loadReadOH          = loadUnitReadPorts.map(p =>
          UIntToOH((((p.bits.vs - record.bits.vs2).asBits.bits(2, 0)) ## p.bits.offset.asBits).asUInt)
        )
        val dataInLsuQueue      = ohCheck(io.loadDataInLSUWriteQueue, record.bits.instIndex, parameter.chainingSize)
        // elementMask update by write
        val writeUpdateValidVec =
          writePort.map(p =>
            p.valid & (p.bits.instructionIndex === record.bits.instIndex) &
              // Only index load will split the datapath into separate parts.
              p.bits.mask.asBits.bit(parameter.datapathWidth / 8 - 1) & !record.bits.oooWrite
          )
        val writeUpdate1HVec    = writeOH.zip(writeUpdateValidVec).map { case (oh, v) => v ? (oh, 0.U(oh.width)) }
        // elementMask update by read of store instruction
        val loadUpdateValidVec  =
          loadUnitReadPorts.map(p => p.fire & (p.bits.instructionIndex === record.bits.instIndex) & record.bits.st)
        val loadUpdate1HVec     = loadReadOH.zip(loadUpdateValidVec).map { case (oh, v) => v ? (oh, 0.U(oh.width)) }
        // all elementMask update
        val elementUpdateValid  = orReduce(writeUpdateValidVec ++ loadUpdateValidVec)
        val elementUpdate1H     = orReduceUInt(writeUpdate1HVec ++ loadUpdate1HVec)
        val dataInLaneCheck     = ohCheck(io.dataInLane, record.bits.instIndex, parameter.chainingSize)
        val laneLastReport      = ohCheck(io.instructionLastReport, record.bits.instIndex, parameter.chainingSize)
        val topLastReport       = ohCheck(io.lsuLastReport, record.bits.instIndex, parameter.chainingSize)
        // only wait lane clear
        val waitLaneClear       =
          record.bits.state.stFinish & record.bits.state.wWriteQueueClear &
            record.bits.state.wLaneLastReport & record.bits.state.wTopLastReport
        val stateClear          = (waitLaneClear & record.bits.state.wLaneClear) |
          (record.bits.elementMask.asBits.andR & !record.bits.onlyRead)

        when(topLastReport):
          record.bits.state.stFinish       := true.B
          record.bits.state.wTopLastReport := true.B

        when(laneLastReport):
          record.bits.state.wLaneLastReport := true.B

        when(record.bits.state.stFinish & !dataInLsuQueue):
          record.bits.state.wWriteQueueClear := true.B

        when(waitLaneClear & !dataInLaneCheck):
          record.bits.state.wLaneClear := true.B

        when(stateClear):
          record.valid := false.B
          when(record.valid):
            recordRelease(i) := indexToOH(record.bits.instIndex, parameter.chainingSize)

        when(recordEnq.asBits.bit(i)):
          record := initRecord
        when(!recordEnq.asBits.bit(i) & elementUpdateValid):
          record.bits.elementMask := (record.bits.elementMask.asBits | elementUpdate1H.asBits)
            .bits(
              parameter.elementSize - 1,
              0
            )
            .asUInt
      }
    }
    // @todo @qinjun-li DV&RTL, here is a bug, LSU hazard
    //       @Clo91eaf original of LSU hazard is coming here.
    val hazardVec         = chainingRecordCopy.init.zipWithIndex.map { case (sourceRecord, sourceIndex) =>
      chainingRecordCopy.drop(sourceIndex + 1).zipWithIndex.map { case (sinkRecord, _) =>
        val recordSeq        = Seq(sourceRecord, sinkRecord)
        val isLoad           = recordSeq.map(r => r.valid & r.bits.ls & !r.bits.st)
        val isStore          = recordSeq.map(r => r.valid & r.bits.ls & r.bits.st)
        val isSlow           = recordSeq.map(r => r.valid & r.bits.slow)
        // todo: 重叠而不是相等
        val samVd            =
          sourceRecord.bits.vd.valid & sinkRecord.bits.vd.valid &
            (sourceRecord.bits.vd.bits.asBits.bits(4, 3).asUInt === sinkRecord.bits.vd.bits.asBits.bits(4, 3).asUInt) &
            // Want to write the same datapath(There are 0 in the same position)
            (~(sourceRecord.bits.elementMask.asBits | sinkRecord.bits.elementMask.asBits)).orR
        val sourceVdEqSinkVs = sourceRecord.bits.vd.valid & (
          (sourceRecord.bits.vd.bits === sinkRecord.bits.vs2) |
            ((sourceRecord.bits.vd.bits === sinkRecord.bits.vs1.bits) & sinkRecord.bits.vs1.valid)
        )
        val sinkVdEqSourceVs = sinkRecord.bits.vd.valid & (
          (sinkRecord.bits.vd.bits === sourceRecord.bits.vs2) |
            ((sinkRecord.bits.vd.bits === sourceRecord.bits.vs1.bits) & sourceRecord.bits.vs1.valid)
        )
        // source更新
        val older            = instIndexLE(sinkRecord.bits.instIndex, sourceRecord.bits.instIndex)
        val hazardForeLoad   = (older ? (isLoad.head & isSlow.last, isLoad.last & isSlow.head)) & (
          // waw
          samVd |
            // war
            (older ? (sourceVdEqSinkVs, sinkVdEqSourceVs))
        )
        val rawForeStore     = (older ? (isStore.head & isSlow.last, isStore.last & isSlow.head)) & samVd
        // (hazardForeLoad, rawForeStore) todo: need check hazard?
        (false.B, false.B)
      }
    }
    io.writeReadyForLsu := !orReduce(hazardVec.map(v => orReduce(v.map(_._1))))
    io.vrfReadyToStore  := !orReduce(hazardVec.map(v => orReduce(v.map(_._2))))
    io.vrfSlotRelease   := orReduceUInt(recordRelease.toSeq)
    io.instructionValid := orReduceUInt(
      chainingRecord
        .map(r =>
          maskAnd(
            r.valid,
            indexToOH(r.bits.instIndex, parameter.chainingSize)
          )
        )
    )

    writeCheck.zip(writeAllow).foreach { case (check, allow) =>
      allow := andReduce(
        chainingRecordCopy
          .zip(recordValidVec)
          .map { case (record, rv) =>
            val checkModule = WriteCheck.instantiate(parameter)
            checkModule.io.check       := check
            checkModule.io.record      := record
            checkModule.io.recordValid := rv
            checkModule.io.checkResult
          }
      )
    }

    io.vrfProbe.valid              := writePipe.valid
    io.vrfProbe.requestVd          := writePipe.bits.vd
    io.vrfProbe.requestOffset      := writePipe.bits.offset
    io.vrfProbe.requestMask        := writePipe.bits.mask
    io.vrfProbe.requestData        := writePipe.bits.data
    io.vrfProbe.requestInstruction := writePipe.bits.instructionIndex
