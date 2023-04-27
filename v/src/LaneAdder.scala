package v

import chisel3._
import chisel3.util._

class LaneAdderReq(datapathWidth: Int) extends Bundle {
  val src: Vec[UInt] = Vec(2, UInt((datapathWidth + 1).W))
  // mask for carry or borrow
  val mask:     Bool = Bool()
  val opcode:   UInt = UInt(4.W)
  val sign:     Bool = Bool()
  val reverse:  Bool = Bool()
  val average:  Bool = Bool()
  val saturate: Bool = Bool()
  val vxrm:     UInt = UInt(2.W)
  val vSew:     UInt = UInt(2.W)
}

class LaneAdderResp(datapathWidth: Int) extends Bundle {
  val data:         UInt = UInt(datapathWidth.W)
  val singleResult: Bool = Bool()
  val vxsat:        Bool = Bool()
}

/** 加法器的输出有两个大类：
  *   输出一整个结果：
  *     1. 上下溢出的最近值
  *     1. 比大小的两个元操作数中的一个
  *     1. 正常的加法结果
  *   输出一个bool值:
  *     1. carry || borrow
  *     1. 判断大小的结果
  */
class LaneAdder(datapathWidth: Int) extends Module {
  val req:  LaneAdderReq = IO(Input(new LaneAdderReq(datapathWidth)))
  val resp: LaneAdderResp = IO(Output(new LaneAdderResp(datapathWidth)))
  // todo: decode
  // ["add", "sub", "slt", "sle", "sgt", "sge", "max", "min", "seq", "sne", "adc", "sbc"]
  val uopOH: UInt = UIntToOH(req.opcode)(11, 0)
  val isSub: Bool = !(uopOH(0) || uopOH(10))
  // sub -> src(1) - src(0)
  val subOperation0: UInt = Mux(isSub && !req.reverse, (~req.src.head).asUInt, req.src.head)
  val subOperation1: UInt = Mux(isSub && req.reverse, (~req.src.last).asUInt, req.src.last)
  // sub + 1 || carry || borrow
  val operation2: UInt = isSub ^ req.mask
  val vSewOrR:    Bool = req.vSew.orR
  val maskForSew: UInt = Fill(16, req.vSew(1)) ## Fill(8, vSewOrR) ## Fill(8, true.B)
  val signForSew: UInt = req.vSew(1) ## 0.U(15.W) ## req.vSew(0) ## 0.U(7.W) ## !vSewOrR ## 0.U(7.W)
  // 计算最近值
  /** 往下溢出的值
    * vSew = 0:
    *   U: 0x0  S: 0x80
    * vSew = 1:
    *   U: 0x0  S: 0x8000
    * vSew = 2:
    *   U: 0x0  S：0x80000000
    */
  val lowerOverflowApproximation: UInt = Mux(req.sign, signForSew, 0.U)

  /** 往上溢出的值
    * vSew = 0:
    *   U: 0xff         S: 0x7f
    * vSew = 1:
    *   U: 0xffff       S: 0x7fff
    * vSew = 2:
    *   U: 0xffffffff   S：0x7fffffff
    */
  val upperOverflowApproximation: UInt = maskForSew ^ lowerOverflowApproximation

  //todo: decode(req) -> roundingTail
  val roundingTail:   UInt = (subOperation0 + subOperation1 + operation2)(1, 0)
  val vxrmCorrection: UInt = Mux(req.average, req.vxrm, 2.U)
  val roundingBits: Bool = Mux1H(
    UIntToOH(vxrmCorrection),
    Seq(
      roundingTail(0),
      roundingTail(0) && roundingTail(1),
      false.B,
      roundingTail(0) && !roundingTail(1)
    )
  )
  // TODO: adder
  val (s, c) = csa32(subOperation0, subOperation1, roundingBits ## operation2)
  val addResult: UInt = s + (c ## false.B)
  val addResultSignBit: Bool = Mux1H(
    Seq(!vSewOrR, req.vSew(0), req.vSew(1)),
    Seq(addResult(7), addResult(15), addResult(31))
  )
  val overflowBit: Bool = Mux1H(
    Seq(!vSewOrR, req.vSew(0), req.vSew(1)),
    Seq(addResult(8), addResult(16), addResult(32))
  )
  // 计算溢出的条件
  /** 下溢条件:
    *   1. 两操作数都是负的,结果符号位变成0了   eg: 0x80000000 + 0xffffffff
    *   1. isSub & U, 结果符号位是1         eg: 1 - 3
    */
  val lowerOverflow: Bool =
    (subOperation0(datapathWidth) && subOperation1(datapathWidth) && !addResultSignBit) ||
      (isSub && !req.sign && addResult(datapathWidth))

  /** 上溢条件：
    *   1. S: 两正的加出了符号位
    *   1. U: + && 溢出位有值
    */
  val upperOverflow: Bool = Mux(
    req.sign,
    !subOperation0(datapathWidth) && !subOperation1(datapathWidth) && addResultSignBit,
    overflowBit && !isSub
  )

  // 开始比较
  val equal: Bool = addResult(datapathWidth - 1, 0) === 0.U
  val less:  Bool = addResult(datapathWidth)
  resp.singleResult := Mux1H(
    uopOH(11, 8) ## uopOH(5, 2),
    Seq(
      less,
      less || equal,
      !(less || equal),
      !less,
      equal,
      !equal,
      upperOverflow,
      lowerOverflow
    )
  )
  // 修正 average
  val addResultCorrect: UInt = Mux(req.average, addResult(datapathWidth, 1), addResult)
  val overflow:         Bool = (upperOverflow || lowerOverflow) && req.saturate
  //选结果
  resp.data := Mux1H(
    Seq(
      (uopOH(1, 0) ## uopOH(11, 10)).orR && !overflow,
      (uopOH(6) && !less) || (uopOH(7) && less),
      (uopOH(6) && less) || (uopOH(7) && !less),
      upperOverflow && req.saturate,
      lowerOverflow && req.saturate
    ),
    Seq(
      addResultCorrect,
      req.src.last,
      req.src.head,
      upperOverflowApproximation,
      lowerOverflowApproximation
    )
  )
  resp.vxsat := overflow
}
