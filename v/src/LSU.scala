package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundle, TLBundleParameter, TLChannelAParameter, TLChannelD, TLChannelDParameter}

/**
  * @param datapathWidth ELEN
  * @param chainingSize how many instructions can be chained
  * @param vLen VLEN
  * @param laneNumber how many lanes in the vector processor
  * @param paWidth physical address width
  */
case class LSUParam(
  datapathWidth:        Int,
  chainingSize:         Int,
  vLen:                 Int,
  laneNumber:           Int,
  paWidth:              Int,
  sourceWidth:          Int,
  sizeWidth:            Int,
  maskWidth:            Int,
  memoryBankSize:       Int,
  lsuMSHRSize:          Int,
  lsuVRFWriteQueueSize: Int,
  tlParam:              TLBundleParameter) {

  /** see [[VParameter.maskGroupWidth]]. */
  val maskGroupWidth: Int = datapathWidth

  /** see [[VParameter.maskGroupSize]]. */
  val maskGroupSize: Int = vLen / datapathWidth

  /** hardware width of [[maskGroupSize]] */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** hardware width of [[LaneCsrInterface.vl]]
    *
    * `+1` is for vl being 0 to vl(not vlMax - 1).
    * we use less than for comparing, rather than less equal.
    */
  val vLenBits: Int = log2Ceil(vLen) + 1

  /** TODO: configured by cache line size. */
  val bankPosition: Int = 6

  def mshrParam: MSHRParam = MSHRParam(chainingSize, datapathWidth, vLen, laneNumber, paWidth, tlParam)

  /** see [[VRFParam.regNumBits]] */
  val regNumBits: Int = log2Ceil(32)

  /** see [[VParameter.instructionIndexBits]] */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** see [[LaneParameter.singleGroupSize]] */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** see [[LaneParameter.vrfOffsetBits]] */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)
}

class LSUWriteQueueBundle(param: LSUParam) extends Bundle {
  val data: VRFWriteRequest =
    new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
  val targetLane: UInt = UInt(param.laneNumber.W)
}
class LSUInstInformation extends Bundle {

  /** nf + 1 */
  val nf: UInt = UInt(3.W)

  /** mew = 1 reserved */
  val mew: Bool = Bool()

  /** unit-stride index-uo stride index-o */
  val mop: UInt = UInt(2.W)

  /** vs2 | rs2 | umop
    * 0         ->  unit stride
    * 0b01000   ->  whole register
    * 0b01011   ->  mask, eew = 8
    * 0b10000   ->  fault only first (load)
    */
  val vs2: UInt = UInt(5.W)
  val vs1: UInt = UInt(5.W)

  /** 0 -> 8
    * size(0) -> 16
    * size(1) -> 32
    */
  val eew:  UInt = UInt(2.W)
  val vs3:  UInt = UInt(5.W)
  val st:   Bool = Bool()
  val mask: Bool = Bool()
  // fault only first
  def fof: Bool = mop === 0.U && vs2(4) && !st
}

class LSUReq(dataWidth: Int) extends Bundle {
  val instInf:   LSUInstInformation = new LSUInstInformation
  val rs1Data:   UInt = UInt(dataWidth.W)
  val rs2Data:   UInt = UInt(dataWidth.W)
  val instIndex: UInt = UInt(3.W)
}

class LSU(param: LSUParam) extends Module {
  val req:          DecoupledIO[LSUReq] = IO(Flipped(Decoupled(new LSUReq(param.datapathWidth))))
  val maskRegInput: Vec[UInt] = IO(Input(Vec(param.lsuMSHRSize, UInt(param.maskGroupWidth.W))))
  val maskSelect:   Vec[UInt] = IO(Output(Vec(param.lsuMSHRSize, UInt(param.maskGroupSizeBits.W))))
  val tlPort:       Vec[TLBundle] = IO(Vec(param.memoryBankSize, param.tlParam.bundle()))
  val readDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )
  val readResults: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))
  val vrfWritePort: Vec[DecoupledIO[VRFWriteRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(
        new VRFWriteRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits, param.datapathWidth)
      )
    )
  )
  val csrInterface:     LaneCsrInterface = IO(Input(new LaneCsrInterface(param.vLenBits)))
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.laneNumber, Flipped(Valid(UInt(param.datapathWidth.W)))))
  val offsetReadTag:    Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(3.W))))
  val lastReport:       ValidIO[UInt] = IO(Output(Valid(UInt(3.W))))
  val lsuOffsetReq:     Bool = IO(Output(Bool()))

  val reqEnq:          Vec[Bool] = Wire(Vec(param.lsuMSHRSize, Bool()))
  val tryToReadData:   Vec[UInt] = Wire(Vec(param.lsuMSHRSize, UInt(param.laneNumber.W)))
  val readDataArbiter: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))
  val readDataFire:    Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))
  val getReadPort:     IndexedSeq[Bool] = readDataFire.map(_.asUInt.orR)

  val tryToAGet:        Vec[UInt] = Wire(Vec(param.lsuMSHRSize, UInt(param.memoryBankSize.W)))
  val getArbiter:       Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.memoryBankSize, Bool())))
  val tileChannelReady: IndexedSeq[Bool] = getArbiter.map(_.asUInt.orR)

  val tryToAckData: Vec[UInt] = Wire(Vec(param.memoryBankSize, UInt(param.lsuMSHRSize.W)))
  val readyArbiter: Vec[Vec[Bool]] = Wire(Vec(param.memoryBankSize, Vec(param.lsuMSHRSize, Bool())))
  val ackArbiter:   Vec[Vec[Bool]] = Wire(Vec(param.memoryBankSize, Vec(param.lsuMSHRSize, Bool())))
  val ackReady:     IndexedSeq[Bool] = ackArbiter.map(_.asUInt.orR)

  val tryToWriteData:   Vec[UInt] = Wire(Vec(param.lsuMSHRSize, UInt(param.laneNumber.W)))
  val writeDataArbiter: Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))
  val writeDataFire:    Vec[Vec[Bool]] = Wire(Vec(param.lsuMSHRSize, Vec(param.laneNumber, Bool())))
  val getWritePort:     IndexedSeq[Bool] = writeDataFire.map(_.asUInt.orR)

  val writeQueueVec: Seq[Queue[LSUWriteQueueBundle]] =
    Seq.fill(param.lsuMSHRSize)(Module(new Queue(new LSUWriteQueueBundle(param), param.lsuVRFWriteQueueSize)))
  val mshrVec: Seq[MSHR] = Seq.tabulate(param.lsuMSHRSize) { index =>
    val mshr: MSHR = Module(new MSHR(param.mshrParam))

    mshr.req.valid := reqEnq(index)
    mshr.req.bits := req.bits

    tryToReadData(index) := Mux(mshr.readDataPort.valid, mshr.status.targetLane, 0.U)
    mshr.readDataPort.ready := getReadPort(index)
    mshr.readResult := Mux1H(RegNext(mshr.status.targetLane), readResults)

    // offset
    Seq.tabulate(param.laneNumber) { laneID =>
      mshr.offsetReadResult(laneID).valid := offsetReadResult(laneID).valid && offsetReadTag(
        laneID
      ) === mshr.status.instIndex
      mshr.offsetReadResult(laneID).bits := offsetReadResult(laneID).bits
    }

    // mask
    maskSelect(index) := Mux(mshr.maskSelect.valid, mshr.maskSelect.bits, 0.U)
    mshr.maskRegInput := maskRegInput(index)

    // tile link
    tryToAGet(index) := Mux(mshr.tlPort.a.valid, UIntToOH(mshr.tlPort.a.bits.address(param.bankPosition)), 0.U)
    mshr.tlPort.a.ready := getArbiter(index).asUInt.orR
    // d
    tryToAckData.map(_(index)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        ackArbiter(i)(index) := tryToUse && !occupied && tlPort(i).d.valid && writeQueueVec(index).io.enq.ready
        readyArbiter(i)(index) := !occupied
        occupied || (tryToUse && tlPort(i).d.valid)
    }

    val selectResp: TLChannelD = Mux(ackArbiter.head(index), tlPort.head.d.bits, tlPort.last.d.bits)
    mshr.tlPort.d.valid := VecInit(
      ackArbiter.map(_(index))
    ).asUInt.orR && (selectResp.opcode =/= 0.U || mshr.status.waitFirstResp)
    mshr.tlPort.d.bits := selectResp
    mshr.tlPort.d.bits.source := (selectResp.source >> 2).asUInt

    // 处理写寄存器的,由于mshr出来没有反,压需要一个队列
    writeQueueVec(index).io.enq.valid := mshr.vrfWritePort.valid
    writeQueueVec(index).io.enq.bits.data := mshr.vrfWritePort.bits
    writeQueueVec(index).io.enq.bits.targetLane := mshr.status.targetLane
    mshr.vrfWritePort.ready := writeQueueVec(index).io.enq.ready
    tryToWriteData(index) := Mux(writeQueueVec(index).io.deq.valid, writeQueueVec(index).io.deq.bits.targetLane, 0.U)
    writeQueueVec(index).io.deq.ready := getWritePort(index)

    mshr.csrInterface := csrInterface
    mshr
  }

  val idleMask:   UInt = VecInit(mshrVec.map(_.status.idle)).asUInt
  val idleSelect: UInt = ffo(idleMask)(param.lsuMSHRSize - 1, 0)
  reqEnq := VecInit(Mux(req.valid, idleSelect, 0.U).asBools)
  // todo: address conflict
  req.ready := idleMask.orR

  Seq.tabulate(param.laneNumber) { laneID =>
    // 处理读请求的仲裁
    tryToReadData.map(_(laneID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        readDataArbiter(i)(laneID) := tryToUse && !occupied
        readDataFire(i)(laneID) := tryToUse && !occupied && readDataPorts(laneID).ready
        occupied || tryToUse
    }
    // 连接读请求
    readDataPorts(laneID).valid := VecInit(readDataArbiter.map(_(laneID))).asUInt.orR
    readDataPorts(laneID).bits := Mux1H(readDataArbiter.map(_(laneID)), mshrVec.map(_.readDataPort.bits))

    // 处理写请求的仲裁
    tryToWriteData.map(_(laneID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        writeDataArbiter(i)(laneID) := tryToUse && !occupied
        writeDataFire(i)(laneID) := tryToUse && !occupied && vrfWritePort(laneID).ready
        occupied || tryToUse
    }
    // 连接写请求
    vrfWritePort(laneID).valid := VecInit(writeDataArbiter.map(_(laneID))).asUInt.orR
    vrfWritePort(laneID).bits := Mux1H(writeDataArbiter.map(_(laneID)), writeQueueVec.map(_.io.deq.bits.data))
  }

  val tlDSource:   IndexedSeq[UInt] = tlPort.map(_.d.bits.source(1, 0))
  val tlDSourceOH: IndexedSeq[UInt] = tlDSource.map(UIntToOH(_))

  Seq.tabulate(param.memoryBankSize) { bankID =>
    tryToAGet.map(_(bankID)).zipWithIndex.foldLeft(false.B) {
      case (occupied, (tryToUse, i)) =>
        getArbiter(i)(bankID) := tryToUse && !occupied && tlPort(bankID).a.ready
        occupied || tryToUse
    }
    // 连 a 通道
    val sourceExtend: UInt = OHToUInt(VecInit(getArbiter.map(_(bankID))).asUInt)
    tlPort(bankID).a.valid := VecInit(getArbiter.map(_(bankID))).asUInt.orR
    tlPort(bankID).a.bits := Mux1H(getArbiter.map(_(bankID)), mshrVec.map(_.tlPort.a.bits))
    tlPort(bankID).a.bits.source := Mux1H(
      getArbiter.map(_(bankID)),
      mshrVec.map(_.tlPort.a.bits.source)
    ) ## sourceExtend
    // d 试图回应
    tryToAckData(bankID) := tlDSourceOH(bankID)
    tlPort(bankID).d.ready := ackReady(bankID) || tlPort(bankID).d.bits.opcode === 0.U
  }
  // 处理last
  lastReport.valid := VecInit(mshrVec.map(_.status.last)).asUInt.orR
  lastReport.bits := Mux1H(mshrVec.map(_.status.last), mshrVec.map(_.status.instIndex))
  lsuOffsetReq := VecInit(mshrVec.map(_.status.indexGroupEnd)).asUInt.orR
}
