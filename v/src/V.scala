package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{decoder, TruthTable}
import tilelink.{TLBundle, TLBundleParameter, TLChannelAParameter, TLChannelDParameter}

case class VParam(XLEN: Int = 32, dataPathWidth: Int = 32, VLEN: Int = 1024, lane: Int = 8, vaWidth: Int = 32) {
  val tlBank:         Int = 2
  val sourceWidth:    Int = 10
  val maskGroupWidth: Int = 32
  val maskGroupSize:  Int = VLEN / 32
  val chainingSize:   Int = 4
  val instIndexSize:  Int = log2Ceil(chainingSize) + 1
  val laneGroupSize:  Int = VLEN / lane
  val tlParam: TLBundleParameter = TLBundleParameter(
    a = TLChannelAParameter(vaWidth, sourceWidth, dataPathWidth, 2, 4),
    b = None,
    c = None,
    d = TLChannelDParameter(sourceWidth, sourceWidth, dataPathWidth, 2),
    e = None
  )
  def laneParam: LaneParameters = LaneParameters(dataPathWidth)
  def lsuParma:  LSUParam = LSUParam(dataPathWidth)
  def vrfParam:  VRFParam = VRFParam(VLEN, lane, laneGroupSize, dataPathWidth)
  require(XLEN == dataPathWidth)
}

class VReq(param: VParam) extends Bundle {
  val inst:     UInt = UInt(32.W)
  val src1Data: UInt = UInt(param.XLEN.W)
  val src2Data: UInt = UInt(param.XLEN.W)
}

class VResp(param: VParam) extends Bundle {
  // todo: vector解出来是否需要写rd？
  val data: UInt = UInt(param.XLEN.W)
}

class InstRecord(param: VParam) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val vrfWrite:  Bool = Bool()

  /** Whether operation is widen */
  val w: Bool = Bool()

  /** Whether operation is narrowing */
  val n: Bool = Bool()
  // load | store
  val ls: Bool = Bool()
}

class InstState extends Bundle {
  val wLast:    Bool = Bool()
  val idle:     Bool = Bool()
  val sExecute: Bool = Bool()
  val sCommit:  Bool = Bool()
}

class specialInstructionType extends Bundle {
  val red:      Bool = Bool()
  val compress: Bool = Bool()
  val viota:    Bool = Bool()
  // 其他的需要对齐的指令
  val other: Bool = Bool()
}

class InstControl(param: VParam) extends Bundle {
  val record: InstRecord = new InstRecord(param)
  val state:  InstState = new InstState
  val endTag: Vec[Bool] = Vec(param.lane + 1, Bool())
}

class V(param: VParam) extends Module {
  val req:              DecoupledIO[VReq] = IO(Flipped(Decoupled(new VReq(param))))
  val resp:             ValidIO[VResp] = IO(Valid(new VResp(param)))
  val csrInterface:     LaneCsrInterface = IO(Input(new LaneCsrInterface(param.laneParam.VLMaxWidth)))
  val storeBufferClear: Bool = IO(Input(Bool()))
  val tlPort:           Vec[TLBundle] = IO(Vec(param.tlBank, param.tlParam.bundle()))

  val lsu: LSU = Module(new LSU(param.lsuParma))
  // 给指令打一个tag用来分新老
  val instCount:     UInt = RegInit(0.U(param.instIndexSize.W))
  val nextInstCount: UInt = instCount + 1.U
  when(req.fire) { instCount := nextInstCount }
  // 提交需要按顺序
  // todo: 处理 waw
  val respCount:     UInt = RegInit(0.U(param.instIndexSize.W))
  val nextRespCount: UInt = respCount + 1.U
  when(resp.fire) { respCount := nextRespCount }

  // 对进来的指令decode
  val decodeResult: UInt =
    decoder.espresso((req.bits.inst >> 12).asUInt, TruthTable(InstructionDecodeTable.table, BitPat.dontCare(25)))
  // todo: 可能会增大decode的组合逻辑
  val decodeResFormat:    InstructionDecodeResult = decodeResult.asTypeOf(new InstructionDecodeResult)
  val decodeResFormatExt: ExtendInstructionDecodeResult = decodeResult.asTypeOf(new ExtendInstructionDecodeResult)

  val isLSType: Bool = !req.bits.inst(6)
  val isST:     Bool = !req.bits.inst(6) && req.bits.inst(5)
  val isLD:     Bool = !req.bits.inst(6) && !req.bits.inst(5)
  // todo：noReadST
  val noReadLD: Bool = isLD && (!req.bits.inst(26))

  // todo: 是否广播给所有单元
  val v0: Vec[UInt] = RegInit(VecInit(Seq.fill(param.maskGroupSize)(0.U(param.maskGroupWidth.W))))

  val instEnq:    UInt = Wire(UInt(param.chainingSize.W))
  // 最后一个位置的指令，是否来了一组反馈
  val next:        Vec[Bool] = Wire(Vec(param.lane, Bool()))
  val synchronize: Bool = Wire(Bool())
  // todo
  val respValid:    Bool = Wire(Bool()) // resp to rc
  val instType:     specialInstructionType = RegInit(0.U.asTypeOf(new specialInstructionType))
  val nextInstType: specialInstructionType = Wire(new specialInstructionType)
  val lastVec:      Vec[Vec[Bool]] = Wire(Vec(param.lane, Vec(param.chainingSize, Bool())))

  nextInstType.compress := decodeResFormat.otherUnit && decodeResFormat.uop === 5.U
  nextInstType.viota := decodeResFormat.otherUnit && decodeResFormat.uop(3) && decodeResFormatExt.viota
  nextInstType.red := !decodeResFormat.otherUnit && decodeResFormat.red
  nextInstType.other := DontCare
  // 是否在lane与schedule之间有数据交换,todo: decode
  val specialInst: Bool = nextInstType.asUInt.orR

  // 指令的状态维护
  val instStateVec: Seq[InstControl] = Seq.tabulate(param.chainingSize) { index =>
    val control = RegInit((-1).S(new InstControl(param).getWidth.W).asTypeOf(new InstControl(param)))
    val lsuLast: Bool = lsu.vrfWritePort.head.valid && lsu.vrfWritePort.head.bits.last &&
      lsu.vrfWritePort.head.bits.instIndex === control.record.instIndex
    // 指令进来
    when(req.fire && instEnq(index)) {
      control.record.instIndex := instCount
      control.record.ls := isLSType
      control.state.idle := false.B
      control.state.wLast := false.B
      control.state.sCommit := false.B
      control.endTag := VecInit(Seq.fill(param.lane)(noReadLD) :+ !isLSType)
    }.otherwise {
      when(control.endTag.asUInt.andR) {
        control.state.wLast := true.B
      }
      when(respCount === control.record.instIndex && resp.fire) {
        control.state.sCommit := true.B
      }
      when(control.state.sCommit && control.state.sExecute) {
        control.state.idle := true.B
      }
      control.endTag.zip(lastVec.map(_ (index)) :+ lsuLast).foreach {
        case (d, c) => d := d || c
      }
    }
    // 把有数据交换的指令放在特定的位置,因为会ffo填充,所以放最后面
    if (index == (param.chainingSize - 1)) {
      val feedBack: UInt = RegInit(0.U(param.lane.W))
      when(req.fire && instEnq(index)) {
        control.state.sExecute := !specialInst
        instType := nextInstType
      }.elsewhen(respValid && control.state.wLast) {
        control.state.sExecute := true.B
      }
      when(next.asUInt.orR) {
        feedBack := feedBack | next.asUInt
      }.elsewhen(respValid) {
        feedBack := 0.U
      }
      synchronize := feedBack.andR
    }
    control
  }

  // 处理数据
  val data: Vec[ValidIO[UInt]] = RegInit(VecInit(Seq.fill(param.lane)(0.U.asTypeOf(Valid(UInt(param.lane.W))))))
//  val useData:   Vec[Bool] = Wire(Vec(param.lane, Bool()))
  val resultRes: ValidIO[UInt] = RegInit(0.U.asTypeOf(Valid(UInt(param.dataPathWidth.W))))
  // todo: viota & compress & reduce

  val scheduleReady: Bool = VecInit(instStateVec.map(_.state.idle)).asUInt.orR
  val laneReady:     Vec[Bool] = Wire(Vec(param.lane, Bool()))
  //  需要等待所有的的准备好,免得先ready的会塞进去多个一样的指令
  val allLaneReady: Bool = laneReady.asUInt.andR

  // lsu的写有限级更高
  val vrfWrite: Vec[ValidIO[VRFWriteRequest]] = Wire(Vec(param.lane, Valid(new VRFWriteRequest(param.vrfParam))))
  // 以lane的角度去连线
  val laneVec: Seq[Lane] = Seq.tabulate(param.lane) { index =>
    val lane: Lane = Module(new Lane(param.laneParam))
    // 请求,
    lane.laneReq.valid := scheduleReady && req.valid && !noReadLD && allLaneReady
    lane.laneReq.bits.instIndex := instCount
    lane.laneReq.bits.decodeResult := decodeResult
    lane.laneReq.bits.vs1 := req.bits.inst(19, 15)
    lane.laneReq.bits.vs2 := req.bits.inst(24, 20)
    lane.laneReq.bits.vd := req.bits.inst(11, 7)
    lane.laneReq.bits.readFromScalar := req.bits.src1Data
    lane.laneReq.bits.ls := isLSType
//    lane.laneReq.bits.st := isST
    laneReady(index) := lane.laneReq.ready

    lane.csrInterface := csrInterface
    lane.laneIndex := index.U

    lane.feedback.valid := DontCare //todo
    lane.feedback.bits.complete := DontCare //todo
    lane.feedback.bits.instIndex := instStateVec.last.record.instIndex

    lane.readDataPort <> lsu.readDataPorts(index)
    lsu.readResults(index) := lane.readResult
    lane.vrfWritePort <> vrfWrite(index)

    lsu.offsetReadResult(index).valid := lane.dataToScheduler.valid && lane.dataToScheduler.bits.toLSU
    lsu.offsetReadResult(index).bits := lane.dataToScheduler.bits.data
    lsu.offsetReadTag(index) := lane.dataToScheduler.bits.instIndex

    lastVec(index).zip(instStateVec.map(_.record.instIndex)).foreach {
      case (d, f) => d := lane.dataToScheduler.valid && lane.dataToScheduler.bits.last &&
        f === lane.dataToScheduler.bits.instIndex
    }

    lane
  }
  laneVec.map(_.dataToScheduler).zip(next).foreach { case (source, sink) => sink := source.valid && !source.bits.toLSU }

  // 连lsu
  lsu.req.valid := scheduleReady && req.valid && isLSType
  lsu.req.bits.instIndex := instCount
  lsu.req.bits.rs1Data := req.bits.src1Data
  lsu.req.bits.rs2Data := req.bits.src2Data
  lsu.req.bits.instInf.nf := req.bits.inst(31, 29)
  lsu.req.bits.instInf.mew := req.bits.inst(28)
  lsu.req.bits.instInf.mop := req.bits.inst(27, 26)
  lsu.req.bits.instInf.vs1 := req.bits.inst(19, 15)
  lsu.req.bits.instInf.vs2 := req.bits.inst(24, 20)
  lsu.req.bits.instInf.vs3 := req.bits.inst(11, 7)
  lsu.req.bits.instInf.eew := req.bits.inst(14, 12)
  lsu.req.bits.instInf.st := isST

  lsu.maskRegInput.zip(lsu.maskSelect).foreach { case (data, index) => data := v0(index) }
  lsu.csrInterface := csrInterface

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

  // 连 tile link
  tlPort.zip(lsu.tlPort).foreach { case (source, sink) =>
    val dBuffer = Queue(source.d, 1, flow = true)
    sink <> source
    sink.d <> dBuffer
  }
  // 暂时直接连lsu的写,后续需要处理scheduler的写
  vrfWrite.zip(lsu.vrfWritePort).foreach { case (sink, source) => sink <> source }

  // 处理 enq
  {
    val free = VecInit(instStateVec.map(_.state.idle)).asUInt
    val freeOR = free.orR
    val free1H = ffo(free)
    // 类型信息：isLSType noReadLD specialInst
    val tryToEnq = Mux(specialInst, true.B ## 0.U((param.chainingSize - 1).W), free1H)
    // 有一个空闲的本地坑
    val localReady = Mux(specialInst, instStateVec.map(_.state.idle).last, freeOR)
    // 远程坑就绪
    val executionReady = (!isLSType || lsu.req.ready) && (noReadLD || allLaneReady)
    req.ready := executionReady && localReady
    instEnq := Mux(req.ready, tryToEnq, 0.U)
  }

  // 处理deq
  {
    val deq: Vec[Bool] = VecInit(instStateVec.map { inst =>
      inst.state.sExecute && inst.state.wLast && !inst.state.sCommit && inst.record.instIndex === respCount
    })
    resp.valid := deq.asUInt.orR
    respValid := deq.last
    resp.bits.data := resultRes.bits
  }
}
