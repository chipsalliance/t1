package v

import chisel3._
import chisel3.util._

case class VRFParam(
  VLEN:           Int,
  lane:           Int,
  ELEN:           Int,
  vrfReadPort:    Int = 6,
  chainingSize:   Int = 4,
  writeQueueSize: Int = 4) {
  val regNum:     Int = 32
  val regNumBits: Int = log2Ceil(regNum)
  // One more bit for sorting
  val instIndexSize: Int = log2Ceil(chainingSize) + 1
  // 需要可配一行的宽度
  val portFactor:    Int = 1
  val rowWidth:      Int = ELEN * portFactor
  val rfDepth:       Int = VLEN * regNum / rowWidth / lane
  val rfAddBits:     Int = log2Ceil(rfDepth)
  // 单个寄存器能分成多少组, 每次只访问32bit
  val singleGroupSize: Int = VLEN / ELEN / lane
  // 记录一个寄存器的offset需要多长
  val offsetBits:Int = log2Ceil(singleGroupSize)
  val rfBankNum: Int = rowWidth / 8
  val maxVSew:   Int = log2Ceil(ELEN / 8)

  def rfParam: RFParam = RFParam(rfDepth)
}

class VRFReadRequest(param: VRFParam) extends Bundle {
  // 为了方便处理seg类型的ld st, vs需要是明确的地址, 而不是一个base
  val vs:         UInt = UInt(param.regNumBits.W)
  // 访问寄存器的 offset
  val offset:     UInt = UInt(param.offsetBits.W)
  // 用来阻塞 raw
  val instIndex:  UInt = UInt(param.instIndexSize.W)
}

class VRFWriteRequest(param: VRFParam) extends Bundle {
  val vd:         UInt = UInt(param.regNumBits.W)
  val offset:     UInt = UInt(param.offsetBits.W)
  // mask/ld类型的有可能不会写完整的32bit
  val mask:       UInt = UInt(4.W)
  val data:       UInt = UInt(param.ELEN.W)
  val last:       Bool = Bool()
  val instIndex: UInt = UInt(param.instIndexSize.W)
}

class WriteQueueBundle(param: VRFParam) extends Bundle {
  val data: VRFWriteRequest = new VRFWriteRequest(param)
  val index: UInt = UInt(3.W)
}

class VRFWriteReport(param: VRFParam) extends Bundle {
  val offset:    UInt = UInt(param.offsetBits.W)
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val vd:        UInt = UInt(param.regNumBits.W)
}

class ChainingRecord(param: VRFParam) extends Bundle {
  // todo:跨寄存器的也需要检测
  val offset:    UInt = UInt(param.offsetBits.W)
  val instIndex:  UInt = UInt(param.instIndexSize.W)
}

class VRF(param: VRFParam) extends Module {
  val read:            Vec[DecoupledIO[VRFReadRequest]] = IO(Vec(param.vrfReadPort, Flipped(Decoupled(new VRFReadRequest(param)))))
  val readResult:      Vec[UInt] = IO(Output(Vec(param.vrfReadPort, UInt(param.ELEN.W))))
  val write:           DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(new VRFWriteRequest(param))))
  val instWriteReport: ValidIO[VRFWriteReport] = IO(Flipped(Valid(new VRFWriteReport(param))))
  val flush:           Bool = IO(Input(Bool()))
  // todo: delete
  dontTouch(write.bits.instIndex)

  val chainingRecord: Vec[ValidIO[ChainingRecord]] = RegInit(
    VecInit(Seq.fill(31)(0.U.asTypeOf(Valid(new ChainingRecord(param)))))
  )
  val recordCheckVec: IndexedSeq[ValidIO[ChainingRecord]] =
    WireInit(0.U.asTypeOf(Valid(new ChainingRecord(param)))) +: chainingRecord
  val writeQueue: DecoupledIO[VRFWriteRequest] = Queue(write, param.writeQueueSize)
  writeQueue.ready := true.B

  // todo: 根据 portFactor 变形
  // first read
  val bankReadF: Vec[Bool] = Wire(Vec(param.vrfReadPort, Bool()))
  val bankReadS: Vec[Bool] = Wire(Vec(param.vrfReadPort, Bool()))
  val readResultF: Vec[UInt] = Wire(Vec(param.rfBankNum, UInt(8.W)))
  val readResultS: Vec[UInt] = Wire(Vec(param.rfBankNum, UInt(8.W)))
  // portFactor = 1 的可以直接握手
  read.zipWithIndex.foldLeft((false.B, false.B)) {
    case ((o, t), (v, i)) =>
      val chainingCheckRecord = Mux1H(UIntToOH(v.bits.vs), recordCheckVec)
      val checkResult: Bool = instIndexGE(v.bits.instIndex, chainingCheckRecord.bits.instIndex) ||
        v.bits.offset <= chainingCheckRecord.bits.offset || !chainingCheckRecord.valid
      val validCorrect: Bool = v.valid && checkResult
      // TODO: 加信号名
      v.ready := !t
      bankReadF(i) := validCorrect & !o
      bankReadS(i) := validCorrect & !t & o
      readResult(i) := Mux(o, readResultS.asUInt, readResultF.asUInt)
      (o || validCorrect, (validCorrect && o) || t)
  }

  val rfVec: Seq[RegFile] = Seq.tabulate(param.rfBankNum) { bank =>
    // rf instant
    val rf = Module(new RegFile(param.rfParam))
    // connect readPorts
    rf.readPorts.head.addr := Mux1H(bankReadF, read.map(r => r.bits.vs ## r.bits.offset))
    rf.readPorts.last.addr := Mux1H(bankReadS, read.map(r => r.bits.vs ## r.bits.offset))
    readResultF(bank) := rf.readPorts.head.data
    readResultS(bank) := rf.readPorts.last.data
    // connect writePort
    rf.writePort.valid := writeQueue.valid & writeQueue.bits.mask(bank)
    rf.writePort.bits.addr := writeQueue.bits.vd ## writeQueue.bits.offset
    rf.writePort.bits.data := writeQueue.bits.data(8 * bank + 7, 8 * bank)
    rf
  }

  val initRecord: ValidIO[ChainingRecord] = WireDefault(0.U.asTypeOf(Valid(new ChainingRecord(param))))
  initRecord.valid := true.B
  initRecord.bits.instIndex := instWriteReport.bits.instIndex
  initRecord.bits.offset := instWriteReport.bits.offset

  chainingRecord.zipWithIndex.foreach {
    case (record, i) =>
      when(flush) {
        record.valid := false.B
      }.elsewhen(instWriteReport.valid && instWriteReport.bits.vd === (i + 1).U) {
        record := initRecord
      }.elsewhen(writeQueue.valid && writeQueue.bits.vd === (i + 1).U) {
        record.bits.offset := writeQueue.bits.offset
        when(writeQueue.bits.last) {
          record.valid := false.B
        }
      }
  }
}
