package v

import chisel3._

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
