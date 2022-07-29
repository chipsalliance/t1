package v

import chisel3._
import chisel3.experimental.hierarchy.{Definition, Instance}
import chisel3.util._

case class VRFParam(groupSizeBits: Int, ELEN: Int, vrfReadPort: Int = 6, channingSize: Int = 4) {
  val regSize:     Int = 32
  val regSizeBits: Int = log2Ceil(regSize)
  // One more bit for sorting
  val instIndexSize: Int = log2Ceil(channingSize) + 1
  val rfDepth:       Int = 128
  val rfAddBits:     Int = log2Ceil(rfDepth)
  val rfBankSize:    Int = ELEN / 8
  val maxVSew:       Int = log2Ceil(ELEN / 8)

  def rfParam: RFParam = RFParam(rfDepth)
}

class VRFReadRequest(param: VRFParam) extends Bundle {
  val vs:         UInt = UInt(param.regSizeBits.W)
  val groupIndex: UInt = UInt(param.groupSizeBits.W)
  val eew:        UInt = UInt(2.W)
  val instIndex:  UInt = UInt(param.instIndexSize.W)
}

class VRFWriteRequest(param: VRFParam) extends Bundle {
  val vs:         UInt = UInt(param.regSizeBits.W)
  val groupIndex: UInt = UInt(param.groupSizeBits.W)
  val eew:        UInt = UInt(2.W)
  val data:       UInt = UInt(param.ELEN.W)
  val last:       Bool = Bool()
}

class VRFWriteReport(param: VRFParam) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val vs:        UInt = UInt(param.regSizeBits.W)
}

class VRF(param: VRFParam) extends Module {
  val read:            Vec[DecoupledIO[VRFReadRequest]] = IO(Vec(param.vrfReadPort, Flipped(Decoupled(new VRFReadRequest(param)))))
  val readResult:      Vec[UInt] = IO(Vec(param.vrfReadPort, UInt(param.ELEN.W)))
  val write:           DecoupledIO[VRFWriteRequest] = IO(Flipped(Decoupled(new VRFWriteRequest(param))))
  val instWriteReport: ValidIO[VRFWriteReport] = IO(Flipped(Valid(new VRFWriteReport(param))))

  val readIndex:      Vec[UInt] = Wire(Vec(param.vrfReadPort, UInt(param.rfAddBits.W)))
  val readBankSelect: Vec[UInt] = Wire(Vec(param.vrfReadPort, UInt(param.rfBankSize.W)))
  val readValid: Vec[Bool] = Wire(Vec(param.vrfReadPort, Bool()))
  val readFire: Vec[Bool] = Wire(Vec(param.vrfReadPort, Bool()))
  val bankReady: Vec[Vec[Bool]] = Wire(Vec(param.vrfReadPort, Vec(param.rfBankSize, Bool())))
  // first read
  val bankReadF: Vec[Vec[Bool]] = Wire(Vec(param.vrfReadPort, Vec(param.rfBankSize, Bool())))
  // second read
  val bankReadS: Vec[Vec[Bool]] = Wire(Vec(param.vrfReadPort, Vec(param.rfBankSize, Bool())))

  val readResultF: Vec[UInt] = Wire(Vec(param.rfBankSize, UInt(8.W)))
  val readResultS: Vec[UInt] = Wire(Vec(param.rfBankSize, UInt(8.W)))

  val rfVec: Seq[RegFile] = Seq.tabulate(param.rfBankSize) { bank =>
    // rf instant
    val rf = Module(new RegFile(param.rfParam))

    val BankReadValid: IndexedSeq[Bool] = readBankSelect.map(_(bank))
    BankReadValid.zipWithIndex.foldLeft((false.B, false.B)) {case ((o, t), (v, i)) =>
      bankReady(i)(bank) := v & !t
      bankReadF(i)(bank) := v & !o
      bankReadS(i)(bank) := v & !t & o
      (o || v, (v && o) || t)
    }

    // connect readPorts
    rf.readPorts.head.addr := Mux1H(bankReadF.map(_(bank)), readIndex)
    rf.readPorts.last.addr := Mux1H(bankReadS.map(_(bank)), readIndex)
    readResultF(bank) := rf.readPorts.head.data
    readResultS(bank) := rf.readPorts.last.data
    rf
  }

  read.zipWithIndex.foreach {
    case (rPort, i) =>
      val decodeRes = bankSelect(rPort.bits.vs, rPort.bits.eew, rPort.bits.groupIndex, readValid(i))
      readIndex(i) := rPort.bits.vs ## (rPort.bits.groupIndex >> (param.maxVSew.U - rPort.bits.eew)).asUInt(1, 0)
      readBankSelect(i) := decodeRes(3, 0)
      // read result
      // TODO: Shift optimization
      readResult(i) := ((VecInit(bankReadF(i).zip(readResultF).map {case (en, data) => Mux(en, data, 0.U)}).asUInt |
        VecInit(bankReadS(i).zip(readResultS).map {case (en, data) => Mux(en, data, 0.U)}).asUInt) >>
        (decodeRes(5, 4) ## 0.U(3.W))).asUInt

      //control
      read(i).ready := (bankReadF(i).asUInt | bankReadF(i).asUInt) === readBankSelect(i)
      // TODO: Channing check
      readValid(i) := read(i).valid
  }


}
