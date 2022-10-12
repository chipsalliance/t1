package v

import chisel3._
import chisel3.experimental.hierarchy.{Definition, Instance}
import chisel3.util._

case class VRFParam(
  VLEN:           Int,
  lane:           Int,
  groupSizeBits:  Int,
  ELEN:           Int,
  vrfReadPort:    Int = 6,
  chainingSize:   Int = 4,
  writeQueueSize: Int = 4) {
  val regNum:     Int = 32
  val regNumBits: Int = log2Ceil(regNum)
  // One more bit for sorting
  val instIndexSize: Int = log2Ceil(chainingSize) + 1
  val rfDepth:       Int = VLEN * regNum / ELEN / lane
  val rfAddBits:     Int = log2Ceil(rfDepth)
  // TODO: make rfBankSize configurable (possibly *2)
  val rfBankNum: Int = ELEN / 8
  val maxVSew:   Int = log2Ceil(ELEN / 8)

  def rfParam: RFParam = RFParam(rfDepth)
}

class VRFReadRequest(param: VRFParam) extends Bundle {
  val vs:         UInt = UInt(param.regNumBits.W)
  val groupIndex: UInt = UInt(param.groupSizeBits.W)
  val eew:        UInt = UInt(2.W)
  val instIndex:  UInt = UInt(param.instIndexSize.W)
}

class VRFWriteRequest(param: VRFParam) extends Bundle {
  val vd:         UInt = UInt(param.regNumBits.W)
  val groupIndex: UInt = UInt(param.groupSizeBits.W)
  val eew:        UInt = UInt(2.W)
  val data:       UInt = UInt(param.ELEN.W)
  val last:       Bool = Bool()
}

class WriteQueueBundle(param: VRFParam) extends Bundle {
  val data: VRFWriteRequest = new VRFWriteRequest(param)
  val index: UInt = UInt(3.W)
}

class VRFWriteReport(param: VRFParam) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val vd:        UInt = UInt(param.regNumBits.W)
}

class ChainingRecord(param: VRFParam) extends Bundle {
  val groupIndex: UInt = UInt(param.groupSizeBits.W)
  val instIndex:  UInt = UInt(param.instIndexSize.W)
}

class VRF(param: VRFParam) extends Module {
  val read:            Vec[DecoupledIO[VRFReadRequest]] = IO(Vec(param.vrfReadPort, Flipped(Decoupled(new VRFReadRequest(param)))))
  val readResult:      Vec[UInt] = IO(Output(Vec(param.vrfReadPort, UInt(param.ELEN.W))))
  val write:           DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(new VRFWriteRequest(param))))
  val instWriteReport: ValidIO[VRFWriteReport] = IO(Flipped(Valid(new VRFWriteReport(param))))
  val flush:           Bool = IO(Input(Bool()))

  val chainingRecord: Vec[ValidIO[ChainingRecord]] = RegInit(
    VecInit(Seq.fill(31)(0.U.asTypeOf(Valid(new ChainingRecord(param)))))
  )
  val recordCheckVec: IndexedSeq[ValidIO[ChainingRecord]] =
    WireInit(0.U.asTypeOf(Valid(new ChainingRecord(param)))) +: chainingRecord

  // todo: 改sew需要交换数据,csr可配
  val readIndex:      Vec[UInt] = Wire(Vec(param.vrfReadPort, UInt(param.rfAddBits.W)))
  val readBankSelect: Vec[UInt] = Wire(Vec(param.vrfReadPort, UInt(param.rfBankNum.W)))
  val readValid:      Vec[Bool] = Wire(Vec(param.vrfReadPort, Bool()))
  val bankFire:       Vec[Vec[Bool]] = Wire(Vec(param.vrfReadPort, Vec(param.rfBankNum, Bool())))
  // first read
  val bankReadF: Vec[Vec[Bool]] = Wire(Vec(param.vrfReadPort, Vec(param.rfBankNum, Bool())))
  // second read
  val bankReadS: Vec[Vec[Bool]] = Wire(Vec(param.vrfReadPort, Vec(param.rfBankNum, Bool())))

  val readResultF: Vec[UInt] = Wire(Vec(param.rfBankNum, UInt(8.W)))
  val readResultS: Vec[UInt] = Wire(Vec(param.rfBankNum, UInt(8.W)))
  val writeData:   Vec[UInt] = Wire(Vec(param.rfBankNum, UInt(8.W)))
  val writeBe:     Vec[Bool] = Wire(Vec(param.rfBankNum, Bool()))
  val writeIndex:  UInt = Wire(UInt(param.rfAddBits.W))

  val rfVec: Seq[RegFile] = Seq.tabulate(param.rfBankNum) { bank =>
    // rf instant
    val rf = Module(new RegFile(param.rfParam))

    val BankReadValid: IndexedSeq[Bool] = readBankSelect.map(_(bank))
    // o 已经有一个了, t 有两个了, v这次要, i -> index
    BankReadValid.zipWithIndex.foldLeft((false.B, false.B)) {
      case ((o, t), (v, i)) =>
        // TODO: 加信号名
        bankFire(i)(bank) := v & !t
        bankReadF(i)(bank) := v & !o
        bankReadS(i)(bank) := v & !t & o
        (o || v, (v && o) || t)
    }

    // connect readPorts
    rf.readPorts.head.addr := Mux1H(bankReadF.map(_(bank)), readIndex)
    rf.readPorts.last.addr := Mux1H(bankReadS.map(_(bank)), readIndex)
    readResultF(bank) := rf.readPorts.head.data
    readResultS(bank) := rf.readPorts.last.data
    // connect writePort
    rf.writePort.valid := writeBe(bank)
    rf.writePort.bits.addr := writeIndex
    rf.writePort.bits.data := writeData(bank)
    rf
  }

  read.zipWithIndex.foreach {
    case (rPort, i) =>
      val decodeRes = bankSelect(rPort.bits.vs, rPort.bits.eew, rPort.bits.groupIndex, readValid(i))
      val chainingCheckRecord = Mux1H(UIntToOH(rPort.bits.vs), recordCheckVec)
      val checkResult = instIndexGE(rPort.bits.instIndex, chainingCheckRecord.bits.instIndex) ||
        rPort.bits.groupIndex <= chainingCheckRecord.bits.groupIndex || !chainingCheckRecord.valid
      readIndex(i) := rPort.bits.vs ## 0.U(2.W) + (rPort.bits.groupIndex >> (param.maxVSew.U - rPort.bits.eew)).asUInt
      readBankSelect(i) := decodeRes(3, 0)
      // read result
      // TODO: Shift optimization
      readResult(i) := ((VecInit(bankReadF(i).zip(readResultF).map { case (en, data) => Mux(en, data, 0.U) }).asUInt |
        VecInit(bankReadS(i).zip(readResultS).map { case (en, data) => Mux(en, data, 0.U) }).asUInt) >>
        (decodeRes(5, 4) ## 0.U(3.W))).asUInt

      //control
      read(i).ready := (bankReadF(i).asUInt | bankReadS(i).asUInt) === readBankSelect(i)
      readValid(i) := read(i).valid & checkResult

  }

  // write
  val writeQueue: DecoupledIO[VRFWriteRequest] = Queue(write, param.writeQueueSize)
  writeIndex := writeQueue.bits.vd ## (writeQueue.bits.groupIndex >> (param.maxVSew.U - writeQueue.bits.eew))
    .asUInt(1, 0)
  val writeDecodeRes: UInt = bankSelect(writeQueue.bits.vd, writeQueue.bits.eew, writeQueue.bits.groupIndex, true.B)
  writeData := (writeQueue.bits.data << (writeDecodeRes(5, 4) ## 0.U(3.W))).asUInt.asTypeOf(writeData)
  writeBe := writeDecodeRes(3, 0).asTypeOf(writeBe)
  // TODO: second write
  writeQueue.ready := true.B

  val initRecord: ValidIO[ChainingRecord] = WireDefault(0.U.asTypeOf(Valid(new ChainingRecord(param))))
  initRecord.valid := true.B
  initRecord.bits.instIndex := instWriteReport.bits.instIndex

  chainingRecord.zipWithIndex.foreach {
    case (record, i) =>
      when(flush) {
        record.valid := false.B
      }.elsewhen(instWriteReport.valid && instWriteReport.bits.vd === (i + 1).U) {
        record := initRecord
      }.elsewhen(writeQueue.valid && writeQueue.bits.vd === (i + 1).U) {
        record.bits.groupIndex := writeQueue.bits.groupIndex
        when(writeQueue.bits.last) {
          record.valid := false.B
        }
      }
  }
}
