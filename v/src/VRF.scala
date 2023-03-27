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
  * @param eLen ELEN
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
  vLen:         Int,
  laneNumber:   Int,
  eLen:         Int,
  chainingSize: Int)
    extends SerializableModuleParameter {

  /** See documentation for VRF.
    * TODO: document this
    */
  val vrfReadPort: Int = 6

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
  val rowWidth: Int = eLen * portFactor

  /** the depth of memory */
  val rfDepth: Int = vLen * regNum / rowWidth / laneNumber

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / eLen / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  /** TODO: remove it, we use 32bits memory with mask for minimal granularity */
  val rfBankNum: Int = rowWidth / 8

  /** used to instantiate VRF. */
  val VLMaxWidth: Int = log2Ceil(vLen) + 1

  /** Parameter for [[RegFile]] */
  def rfParam: RFParam = RFParam(rfDepth)
}

class VRF(val parameter: VRFParam) extends Module with SerializableModule[VRFParam] {
  val read: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      parameter.vrfReadPort,
      Flipped(
        Decoupled(new VRFReadRequest(parameter.regNumBits, parameter.vrfOffsetBits, parameter.instructionIndexBits))
      )
    )
  )
  val readResult: Vec[UInt] = IO(Output(Vec(parameter.vrfReadPort, UInt(parameter.eLen.W))))
  val write: DecoupledIO[VRFWriteRequest] = IO(
    Flipped(
      Decoupled(
        new VRFWriteRequest(
          parameter.regNumBits,
          parameter.vrfOffsetBits,
          parameter.instructionIndexBits,
          parameter.eLen
        )
      )
    )
  )
  val instWriteReport: DecoupledIO[VRFWriteReport] = IO(Flipped(Decoupled(new VRFWriteReport(parameter))))
  val flush:           Bool = IO(Input(Bool()))
  val csrInterface:    LaneCsrInterface = IO(Input(new LaneCsrInterface(parameter.VLMaxWidth)))
  val lsuLastReport:   ValidIO[UInt] = IO(Flipped(Valid(UInt(parameter.instructionIndexBits.W))))
  // write queue empty
  val bufferClear: Bool = IO(Input(Bool()))
  // todo: delete
  dontTouch(write)

  val chainingRecord: Vec[ValidIO[VRFWriteReport]] = RegInit(
    VecInit(Seq.fill(parameter.chainingSize)(0.U.asTypeOf(Valid(new VRFWriteReport(parameter)))))
  )

  val vsOffsetMask: UInt = {
    // 不用管浮点的
    val mul = csrInterface.vlmul(1, 0)
    mul.andR ## mul(1) ## mul.orR
  }
  val vsBaseMask: UInt = 3.U(2.W) ## (~vsOffsetMask).asUInt
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
    val raw: Bool = rawCheck(record.bits, enq)
    val war: Bool = rawCheck(enq, record.bits)
    val waw: Bool = enq.vd.valid && recordBits.vd.valid && enq.vd.valid && enq.vd.bits === recordBits.vd.bits

    /** 两种暂时处理不了的冲突
      * 自己会乱序写 & wax: enq.unOrderWrite && (war || waw)
      * 老的会乱序写 & raw: record.bits.unOrderWrite && raw
      * todo: ld 需要更大粒度的channing更新或检测,然后除开segment的ld就能chaining起来了
      */
    (!((enq.unOrderWrite && (war || waw)) || (record.bits.unOrderWrite && raw))) || !record.valid
  }

  // todo: 根据 portFactor 变形
  // first read
  val bankReadF:   Vec[Bool] = Wire(Vec(parameter.vrfReadPort, Bool()))
  val bankReadS:   Vec[Bool] = Wire(Vec(parameter.vrfReadPort, Bool()))
  val readResultF: Vec[UInt] = Wire(Vec(parameter.rfBankNum, UInt(8.W)))
  val readResultS: Vec[UInt] = Wire(Vec(parameter.rfBankNum, UInt(8.W)))
  // portFactor = 1 的可以直接握手
  val (_, secondOccupied) = read.zipWithIndex.foldLeft((false.B, false.B)) {
    case ((o, t), (v, i)) =>
      // 先找到自的record
      val readRecord =
        Mux1H(chainingRecord.map(_.bits.instIndex === v.bits.instructionIndex), chainingRecord.map(_.bits))
      val checkResult:  Bool = chainingRecord.map(r => chainingCheck(v.bits, readRecord, r)).reduce(_ && _)
      val validCorrect: Bool = v.valid && checkResult
      // TODO: 加信号名
      v.ready := !t && checkResult
      bankReadF(i) := validCorrect & !o
      bankReadS(i) := validCorrect & !t & o
      readResult(i) := Mux(RegNext(o), readResultS.asUInt, readResultF.asUInt)
      (o || validCorrect, (validCorrect && o) || t)
  }
  write.ready := !secondOccupied

  val rfVec: Seq[RegFile] = Seq.tabulate(parameter.rfBankNum) { bank =>
    // rf instant
    val rf = Module(new RegFile(parameter.rfParam))
    // connect readPorts
    rf.readPorts.head.addr := Mux1H(bankReadF, read.map(r => r.bits.vs ## r.bits.offset))
    rf.readPorts.last.addr := Mux1H(bankReadS, read.map(r => r.bits.vs ## r.bits.offset))
    readResultF(bank) := rf.readPorts.head.data
    readResultS(bank) := rf.readPorts.last.data
    // connect writePort
    rf.writePort.valid := write.fire && write.bits.mask(bank)
    rf.writePort.bits.addr := write.bits.vd ## write.bits.offset
    rf.writePort.bits.data := write.bits.data(8 * bank + 7, 8 * bank)
    rf
  }

  val initRecord: ValidIO[VRFWriteReport] = WireDefault(0.U.asTypeOf(Valid(new VRFWriteReport(parameter))))
  initRecord.valid := true.B
  initRecord.bits := instWriteReport.bits
  val freeRecord: UInt = VecInit(chainingRecord.map(!_.valid)).asUInt
  val recordFFO:  UInt = ffo(freeRecord)
  val recordEnq:  UInt = Wire(UInt(parameter.chainingSize.W))
  instWriteReport.ready := chainingRecord.map(r => enqCheck(instWriteReport.bits, r)).reduce(_ && _)
  recordEnq := Mux(instWriteReport.fire, recordFFO, 0.U(parameter.chainingSize.W))

  chainingRecord.zipWithIndex.foreach {
    case (record, i) =>
      when(recordEnq(i)) {
        record := initRecord
      }
      when(
        write.valid && write.bits.instructionIndex === record.bits.instIndex && (write.bits.last || write.bits.mask(3))
      ) {
        record.bits.offset := write.bits.offset
        record.bits.vdOffset := vsOffsetMask & write.bits.vd
        when(write.bits.last) {
          record.valid := false.B
        }
      }
      when(flush) {
        record.valid := false.B
      }
      when(lsuLastReport.valid && lsuLastReport.bits === record.bits.instIndex) {
        record.bits.stFinish := true.B
      }
      when(record.bits.stFinish && bufferClear && record.valid) {
        record.valid := false.B
      }
  }
}
