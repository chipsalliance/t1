package v

import chisel3._
import chisel3.util._

class MSHRStatus extends Bundle {
  val instIndex: UInt = UInt(3.W)
  val idle: Bool = Bool()
  val groupEnd: Bool = Bool()
}

class MSHR(param: LSUParam) extends Module {
  val req: ValidIO[LSUReq] = IO(Flipped(Valid(new LSUReq(param))))
  val readDataPort: DecoupledIO[VRFReadRequest] = IO(Decoupled(new VRFReadRequest(param.vrfParam)))
  val readResult: UInt = IO(Input(UInt(param.dataWidth.W)))
  val offsetReadResult: Vec[ValidIO[UInt]] = IO(Vec(param.lane, Flipped(Valid(UInt(param.dataWidth.W)))))
  val maskRegInput: UInt = IO(Input(UInt(param.maskGroupWidth.W)))
  val maskSelect: DecoupledIO[UInt] = IO(Decoupled(UInt(param.maskGroupSizeBits.W)))
  val sinkA: DecoupledIO[UInt] = IO(Decoupled(UInt(param.maskGroupSizeBits.W)))
  val sourceD: ValidIO[UInt] = IO(Flipped(Valid(UInt(param.maskGroupSizeBits.W))))
  val vefWritePort: ValidIO[VRFWriteRequest] = IO(Valid(new VRFWriteRequest(param.vrfParam)))

//  val addressBaseVec: UInt = RegInit(0.U(param.dataWidth.W))
  val strideOffset: Vec[ValidIO[UInt]] = RegInit(VecInit(Seq.fill(param.lane)(0.U.asTypeOf(Valid(UInt(param.dataWidth.W))))))

  // 进请求
  val requestReg: LSUReq = RegEnable(req.bits, 0.U.asTypeOf(req.bits), req.valid)

  // 处理offset的寄存器
  val offsetUsed: Vec[Bool] = Wire(Vec(param.lane, Bool()))
  strideOffset.zipWithIndex.foreach {case (offset, index) =>
    offset.valid := offsetReadResult(index).valid || (offset.valid && !offsetUsed(index))
    offset.bits := Mux(offsetReadResult(index).valid, offsetReadResult(index).bits, offset.bits)
  }

  // data 存储, 暂时不 bypass 给 tile link
  val dataReg: UInt = RegEnable(readResult, 0.U, readDataPort.fire)

  // 缓存 mask
  val maskReg: UInt = RegEnable(maskRegInput, 0.U, maskSelect.fire || req.valid)

  // 标志哪些做完了
  val reqDone: UInt = RegInit(0.U(param.lsuGroupSize.W))
  val segMask: UInt = RegInit(0.U(8.W))
  // todo
  val respDone: UInt = RegInit(0.U(param.lsuGroupSize.W))

  // 状态控制 Seq(sReadData, sRequest)
  val stateReg: Vec[Bool] = RegInit(VecInit(Seq.fill(2)(true.B)))
  val initStateWire = WireInit(VecInit(Seq(!requestReg.instInf.st, false.B)))

  val segType: Bool = requestReg.instInf.nf.orR
  val maskType: Bool = requestReg.instInf.mop === 0.U && requestReg.instInf.vs2(0)
  val segNext: UInt = (segMask ## true.B) & (~segMask).asUInt
  val segEnd: Bool = OHToUInt(segNext) === requestReg.instInf.nf

  // 更新 segMask
  when((segType && sinkA.fire) || req.valid) {
    segMask := Mux(segEnd || req.valid, 0.U, segMask | segNext)
  }
  // 更新 reqDone
}
