package v

import chisel3._
import chisel3.util._
import chisel3.util.experimental.decode.{decoder, TruthTable}
import tilelink.{TLBundle, TLBundleParameter, TLChannelAParameter, TLChannelDParameter}

case class VParam(ELEN: Int = 32, VLEN: Int = 1024, lane: Int = 8, vaWidth: Int = 32) {
  val tlBank:         Int = 2
  val sourceWidth:    Int = 10
  val maskGroupWidth: Int = 32
  val maskGroupSize:  Int = VLEN / 32
  val chainingSize:   Int = 4
  val instIndexSize:  Int = log2Ceil(chainingSize) + 1
  val laneGroupSize:  Int = VLEN / lane
  val tlParam: TLBundleParameter = TLBundleParameter(
    a = TLChannelAParameter(vaWidth, sourceWidth, ELEN, 2, 4),
    b = None,
    c = None,
    d = TLChannelDParameter(sourceWidth, sourceWidth, ELEN, 2),
    e = None
  )
  def laneParam: LaneParameters = LaneParameters(ELEN)
  def lsuParma:  LSUParam = LSUParam(ELEN)
  def vrfParam:  VRFParam = VRFParam(VLEN, lane, laneGroupSize, ELEN)
}

class VReq(param: VParam) extends Bundle {
  val inst:     UInt = UInt(32.W)
  val src1Data: UInt = UInt(param.ELEN.W)
  val src2Data: UInt = UInt(param.ELEN.W)
}

class VResp(param: VParam) extends Bundle {
  val data: UInt = UInt(param.ELEN.W)
}

class InstRecord(param: VParam) extends Bundle {
  val instIndex: UInt = UInt(param.instIndexSize.W)
  val vrfWrite:  Bool = Bool()
  val w:         Bool = Bool()
  val n:         Bool = Bool()
  val ls:        Bool = Bool()
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
}

class V(param: VParam) extends Module {
  val req:              DecoupledIO[VReq] = IO(Flipped(Decoupled(new VReq(param))))
  val resp:             ValidIO[VResp] = IO(Valid(new VResp(param)))
  val csrInterface:     LaneCsrInterface = IO(Input(new LaneCsrInterface(param.laneParam.VLMaxBits)))
  val storeBufferClear: Bool = IO(Input(Bool()))
  val tlPort:           Vec[TLBundle] = IO(Vec(param.tlBank, param.tlParam.bundle()))

  val lsu: LSU = Module(new LSU(param.lsuParma))
  // 给指令打一个tag用来分新老
  val instCount:     UInt = RegInit(0.U(param.instIndexSize.W))
  val nextInstCount: UInt = instCount + 1.U
  when(req.fire) { instCount := nextInstCount }
  // 提交需要按顺序
  val respCount:     UInt = RegInit(0.U(param.instIndexSize.W))
  val nextRespCount: UInt = respCount + 1.U
  when(resp.fire) { respCount := nextRespCount }

  // 对进来的指令decode
  val decodeResult: UInt =
    decoder.qmc((req.bits.inst >> 12).asUInt, TruthTable(InstructionDecodeTable.table, BitPat.dontCare(25)))
  val decodeResFormat:    InstructionDecodeResult = decodeResult.asTypeOf(new InstructionDecodeResult)
  val decodeResFormatExt: ExtendInstructionDecodeResult = decodeResult.asTypeOf(new ExtendInstructionDecodeResult)

  val isLSType: Bool = !req.bits.inst(6)
  val isST:     Bool = !req.bits.inst(6) && req.bits.inst(5)
  val isLD:     Bool = !req.bits.inst(6) && !req.bits.inst(5)
  val noReadLD: Bool = isLD && (!req.bits.inst(26))

  val v0: Vec[UInt] = RegInit(VecInit(Seq.fill(param.maskGroupSize)(0.U(param.maskGroupWidth.W))))

  val instEnq:      Vec[Bool] = Wire(Vec(param.chainingSize, Bool()))
  val completion:   Vec[Bool] = Wire(Vec(param.lane, Bool()))
  val next:         Vec[Bool] = Wire(Vec(param.lane, Bool()))
  val synchronize:  Bool = Wire(Bool())
  val respValid:    Bool = Wire(Bool()) // resp to lane
  val instType:     specialInstructionType = RegInit(0.U.asTypeOf(new specialInstructionType))
  val nextInstType: specialInstructionType = RegInit(0.U.asTypeOf(new specialInstructionType))

  nextInstType.compress := decodeResFormat.otherUnit && decodeResFormat.uop === 5.U
  nextInstType.viota := decodeResFormat.otherUnit && decodeResFormat.uop(3) && decodeResFormatExt.viota
  nextInstType.red := !decodeResFormat.otherUnit && decodeResFormat.red
  nextInstType.other := DontCare

  // 指令的状态维护
  val instStateVec: Seq[InstControl] = Seq.tabulate(param.chainingSize) { index =>
    val control = RegInit((-1).S(10.W).asTypeOf(new InstControl(param)))
    // 指令进来
    when(req.fire && instEnq(index)) {
      control.record.instIndex := nextInstCount
      control.record.ls := isLSType
      control.state.idle := false.B
      control.state.wLast := false.B
      control.state.sCommit := false.B
    }
    when(completion(index)) {
      control.state.wLast := true.B
    }
    when(nextRespCount === control.record.instIndex && resp.fire) {
      control.state.sCommit := true.B
    }
    when(control.state.sCommit && control.state.sExecute) {
      control.state.idle := true.B
    }
    // 把有数据交换的指令放在特定的位置,因为会ffo填充,所以放最后面
    if (index == 3) {
      val feedBack: UInt = RegInit(0.U(param.lane.W))
      when(req.fire && instEnq(index)) {
        control.state.sExecute := !nextInstType.asUInt.orR
        instType := nextInstType
      }
      when(next.asUInt.orR) {
        feedBack := feedBack | next.asUInt
      }
      synchronize := feedBack.andR
      when(respValid) {
        feedBack := 0.U
        when(control.state.wLast) {
          control.state.sExecute := true.B
        }
      }
    }
    control
  }

  // 处理数据
  val data:      Vec[ValidIO[UInt]] = RegInit(VecInit(Seq.fill(param.lane)(0.U.asTypeOf(Valid(UInt(param.lane.W))))))
  val useData:   Vec[Bool] = Wire(Vec(param.lane, Bool()))
  val resultRes: ValidIO[UInt] = RegInit(0.U.asTypeOf(Valid(UInt(param.lane.W))))
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
    lane.laneReq.bits.instIndex := nextInstCount
    lane.laneReq.bits.decodeResult := decodeResult
    lane.laneReq.bits.vs1 := req.bits.inst(19, 15)
    lane.laneReq.bits.vs2 := req.bits.inst(24, 20)
    lane.laneReq.bits.vd := req.bits.inst(11, 7)
    lane.laneReq.bits.readFromScala := req.bits.src1Data
    lane.laneReq.bits.ld := isLD
    lane.laneReq.bits.st := isST
    laneReady(index) := lane.laneReq.ready

    lane.csrInterface := csrInterface
    lane.laneIndex := index.U

    lane.feedback.valid := DontCare //todo
    lane.feedback.bits.complete := DontCare //todo
    lane.feedback.bits.instIndex := instStateVec.last.record.instIndex

    lane.readDataPort <> lsu.readDataPort(index)
    lsu.readResult(index) := lane.readResult
    lane.vrfWritePort <> vrfWrite(index)

    lsu.offsetReadResult(index).valid := lane.dataToScheduler.valid && lane.dataToScheduler.bits.toLSU
    lsu.offsetReadResult(index).bits := lane.dataToScheduler.bits.data
    lsu.offsetReadTag(index) := lane.dataToScheduler.bits.instIndex

    lane
  }

  // 连lsu
  lsu.req.valid := scheduleReady && req.valid && isLSType
  lsu.req.bits.instIndex := nextInstCount
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
}
