// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{Path, Property}
import chisel3.util._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder._
import org.chipsalliance.t1.rtl.vfu.{VectorAdder, VectorAdderParameter}
object LaneAdderParam                {
  implicit def rw: upickle.default.ReadWriter[LaneAdderParam] = upickle.default.macroRW
}
case class LaneAdderParam(eLen: Int, latency: Int, laneScale: Int)
    extends VFUParameter
    with SerializableModuleParameter {
  val datapathWidth: Int       = eLen * laneScale
  val decodeField:   BoolField = Decoder.adder
  val inputBundle  = new LaneAdderReq(datapathWidth)
  val outputBundle = new LaneAdderResp(datapathWidth)
}

class LaneAdderReq(datapathWidth: Int) extends VFUPipeBundle {
  val src:          Vec[UInt] = Vec(2, UInt(datapathWidth.W))
  // mask for carry or borrow
  val mask:         UInt      = UInt((datapathWidth / 8).W)
  val opcode:       UInt      = UInt(4.W)
  val sign:         Bool      = Bool()
  val reverse:      Bool      = Bool()
  val average:      Bool      = Bool()
  val saturate:     Bool      = Bool()
  val vxrm:         UInt      = UInt(2.W)
  val vSew:         UInt      = UInt(2.W)
  val executeIndex: UInt      = UInt(2.W)
}

class LaneAdderResp(datapathWidth: Int) extends VFUPipeBundle {
  val data:          UInt = UInt(datapathWidth.W)
  val adderMaskResp: UInt = UInt((datapathWidth / 8).W)
  val vxsat:         UInt = UInt(4.W)
  val executeIndex:  UInt = UInt(2.W)
}

class LaneAdderOM(parameter: LaneAdderParam) extends GeneralOM[LaneAdderParam, LaneAdder](parameter) {
  override def hasRetime: Boolean = true
}

/** 加法器的输出有两个大类： 输出一整个结果：
  *   1. 上下溢出的最近值
  *   1. 比大小的两个元操作数中的一个
  *   1. 正常的加法结果
  * 输出一个bool值:
  *   1. carry || borrow
  *   1. 判断大小的结果
  */
@instantiable
class LaneAdder(val parameter: LaneAdderParam) extends VFUModule with SerializableModule[LaneAdderParam] {
  override val omInstance: Instance[LaneAdderOM] = Instantiate(new LaneAdderOM(parameter))
  omInstance.retimeIn.foreach(_ := Property(Path(clock)))

  val response: LaneAdderResp = Wire(new LaneAdderResp(parameter.datapathWidth))
  val request:  LaneAdderReq  = connectIO(response).asTypeOf(parameter.inputBundle)

  val responseVec: Seq[(UInt, UInt, UInt)] = Seq.tabulate(parameter.laneScale) { index =>
    val subRequest = Wire(new LaneAdderReq(parameter.eLen))
    subRequest.elements.foreach { case (k, v) => v := request.elements(k) }
    subRequest.src.zip(request.src).foreach { case (sink, source) =>
      sink := cutUIntBySize(source, parameter.laneScale)(index)
    }
    subRequest.mask := cutUIntBySize(request.mask, parameter.laneScale)(index)

    // todo: decode
    // ["add", "sub", "slt", "sle", "sgt", "sge", "max", "min", "seq", "sne", "adc", "sbc"]
    val uopOH:         UInt = UIntToOH(subRequest.opcode)(11, 0)
    val isSub:         Bool = !(uopOH(0) || uopOH(10))
    // sub -> src(1) - src(0)
    val subOperation0: UInt = Mux(isSub && !subRequest.reverse, (~subRequest.src.head).asUInt, subRequest.src.head)
    val subOperation1: UInt = Mux(isSub && subRequest.reverse, (~subRequest.src.last).asUInt, subRequest.src.last)
    // sub + 1 || carry || borrow
    val operation2 = Fill(4, isSub) ^ subRequest.mask
    val vSewOrR: Bool = subRequest.vSew.orR
    val vSew1H = UIntToOH(subRequest.vSew)(2, 0)

    val roundingBitsVec: Seq[Bool] = Seq.tabulate(4) { i =>
      val roundingTail: UInt =
        (subOperation0(i * 8 + 7, i * 8) + subOperation1(i * 8 + 7, i * 8) + operation2(i))(1, 0)

      val vxrmOH: UInt = UIntToOH(subRequest.vxrm)
      Mux1H(
        vxrmOH(3) ## vxrmOH(1, 0),
        Seq(
          roundingTail(0),
          roundingTail(0) && roundingTail(1),
          roundingTail(0) && !roundingTail(1)
        )
      )
    }

    // sew = 0 -> 1H: 001 -> 1111
    // sew = 1 -> 1H: 010 -> 0101
    // sew = 2 -> 1H: 100 -> 0001
    val operation2ForAverage: UInt =
      (0.U(6.W) ## Mux(vSew1H(0), roundingBitsVec(3) ## operation2(3), 0.U(2.W))) ##
        (0.U(6.W) ## Mux(!vSew1H(2), roundingBitsVec(2) ## operation2(2), 0.U(2.W))) ##
        (0.U(6.W) ## Mux(vSew1H(0), roundingBitsVec(1) ## operation2(1), 0.U(2.W))) ##
        (0.U(6.W) ## roundingBitsVec.head ## operation2(0))

    val (s, c) = csa32(subOperation0, subOperation1, operation2ForAverage)
    val operation0ForAverage: UInt = c
    val averageSum:           UInt = (s >> 1).asUInt
    // For different sew, some need to be removed
    val operation1ForAverage =
      averageSum(30, 24) ##
        (!vSew1H(0) && averageSum(23)) ## averageSum(22, 16) ##
        (vSew1H(2) && averageSum(15)) ## averageSum(14, 8) ##
        (!vSew1H(0) && averageSum(7)) ## averageSum(6, 0)

    val adder: Instance[VectorAdder] = Instantiate(new VectorAdder(VectorAdderParameter(parameter.eLen)))

    adder.io.a   := Mux(subRequest.average, operation0ForAverage, subOperation0)
    adder.io.b   := Mux(subRequest.average, operation1ForAverage, subOperation1)
    adder.io.cin := Mux(
      subRequest.average,
      0.U(4.W),
      Mux1H(
        vSew1H,
        Seq(
          operation2,
          operation2(1) ## operation2(1) ## operation2(0) ## operation2(0),
          Fill(4, operation2(0))
        )
      )
    )
    adder.io.sew := UIntToOH(subRequest.vSew)

    val adderResult = adder.io.z
    // sew = 0 -> 3210
    // sew = 1 -> 1?0?
    // sew = 2 -> 0???
    val adderCarryOut: UInt =
      Mux1H(vSew1H, Seq(adder.io.cout(3), adder.io.cout(1), adder.io.cout(0))) ## adder.io.cout(2) ##
        Mux(vSew1H(0), adder.io.cout(1), adder.io.cout(0)) ## adder.io.cout(0)

    // is first block
    // sew = 0 -> 1H: 001 -> 1111
    // sew = 1 -> 1H: 010 -> 1010
    // sew = 2 -> 1H: 100 -> 1000
    val isFirstBlock: UInt = true.B ## vSew1H(0) ## !vSew1H(2) ## vSew1H(0)

    // 8 bit / element
    val adderResultVec: Vec[UInt] = cutUInt(adderResult, 8)
    val isZero:         Seq[Bool] = adderResultVec.map(_ === 0.U)
    val isZero01:       Bool      = isZero.head && isZero(1)
    val isZero23:       Bool      = isZero(2) && isZero(3)
    val allIsZero:      Bool      = isZero01 && isZero23
    val equalVec:       Vec[Bool] = Mux1H(
      vSew1H,
      Seq(
        VecInit(isZero),
        VecInit(isZero01, isZero01, isZero23, isZero23),
        VecInit(Seq.fill(4)(allIsZero))
      )
    )

    val attributeVec: Seq[Vec[Bool]] = adderResultVec.zipWithIndex.map { case (data, index) =>
      val sourceSign0    = cutUInt(subRequest.src.head, 8)(index)(7)
      val sourceSign1    = cutUInt(subRequest.src.last, 8)(index)(7)
      val operation0Sign = (sourceSign0 && subRequest.sign) ^ (isSub && !subRequest.reverse)
      val operation1Sign = (sourceSign1 && subRequest.sign) ^ (isSub && subRequest.reverse)
      val resultSign     = data.asBools.last
      val uIntLess       = Mux(sourceSign0 ^ sourceSign1, sourceSign0, resultSign)

      /** 下溢条件:
        *   1. 两操作数都是负的,结果符号位变成0了 eg: 0x80000000 + 0xffffffff(+ -1 or - 1)
        *   1. isSub & U, 结果符号位是1 eg: 1 - 3
        */
      val lowerOverflow: Bool =
        (operation0Sign && operation1Sign && !resultSign) ||
          // todo
          (isSub && !subRequest.sign && uIntLess)

      /** 上溢条件：
        *   1. S: 两正的加出了符号位
        *   1. U: + && 溢出位有值
        */
      val upperOverflow: Bool = Mux(
        subRequest.sign,
        !operation0Sign && !operation1Sign && resultSign,
        adderCarryOut(index) && !isSub
      )
      val less = Mux(
        subRequest.sign,
        (data(7) && !upperOverflow) || lowerOverflow,
        uIntLess
      )

      val overflow    = (upperOverflow || lowerOverflow) && subRequest.saturate
      val equal       = equalVec(index)
      val maskSelect  = Mux1H(
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
      val anyNegative = operation0Sign || operation1Sign
      val allNegative = operation0Sign && operation1Sign
      VecInit(Seq(lowerOverflow, upperOverflow, overflow, less, maskSelect, anyNegative, allNegative))
    }

    val attributeSelect: Vec[Vec[Bool]] = Mux1H(
      vSew1H,
      Seq(
        VecInit(attributeVec),
        VecInit(attributeVec(1), attributeVec(1), attributeVec(3), attributeVec(3)),
        VecInit(attributeVec(3), attributeVec(3), attributeVec(3), attributeVec(3))
      )
    )

    /** 往下溢出的值 vSew = 0: U: 0x0 S: 0x80 vSew = 1: U: 0x0 S: 0x8000 vSew = 2: U: 0x0 S：0x80000000
      */
    val lowerOverflowVec: Seq[Bool] = attributeSelect.map(_.head)

    /** 往上溢出的值 vSew = 0: U: 0xff S: 0x7f vSew = 1: U: 0xffff S: 0x7fff vSew = 2: U: 0xffffffff S：0x7fffffff
      */
    val upperOverflowVec: Seq[Bool] = attributeSelect.map(_(1))

    val overflowVec:    Vec[Bool] = VecInit(attributeSelect.map(_(2)))
    val lessVec:        Vec[Bool] = VecInit(attributeSelect.map(_(3)))
    val anyNegativeVec: Vec[Bool] = VecInit(attributeSelect.map(_(5)))
    val allNegativeVec: Vec[Bool] = VecInit(attributeSelect.map(_(6)))

    val responseSelect: Vec[UInt] = VecInit(adderResultVec.zipWithIndex.map { case (data, index) =>
      val overflow: Bool = overflowVec(index)
      val less = lessVec(index)
      Mux1H(
        Seq(
          (uopOH(1, 0) ## uopOH(11, 10)).orR && !overflow,
          (uopOH(6) && !less) || (uopOH(7) && less),
          (uopOH(6) && less) || (uopOH(7) && !less),
          upperOverflowVec(index) && subRequest.saturate,
          lowerOverflowVec(index) && subRequest.saturate
        ),
        Seq(
          Mux(
            subRequest.sign,
            // data(6) && anyNegativeVec(index) -> 任意操作数是负的,需要做符号位扩展, 否则占据data(6)的是+的上溢出
            // eg： 7f + 7 = 86 -average-> 43(不符号扩展)
            //      f8 + 0 = f8 -average-> fc(符号扩展)
            // !isSub || !data(7)
            //      7f - 0 = 7f + ff + 1 + 1 = 180 -average-> c0(需要去掉最高位) -> 40
            // allNegativeVec(index) && data(7):
            // 包含特殊的下溢出： average(0xfff8 - 0x7fff) = 0x10ffd >> 1
            Mux(
              subRequest.average && isFirstBlock(index),
              (allNegativeVec(index) && data(7)) || (data(6) && anyNegativeVec(index) && (!isSub || !data(7))),
              data(7)
            ),
            (subRequest.average && isFirstBlock(index) && isSub) ^ data(7)
          ) ## data(6, 0),
          cutUInt(subRequest.src.last, 8)(index),
          cutUInt(subRequest.src.head, 8)(index),
          !(isFirstBlock(index) && subRequest.sign) ## Fill(7, true.B),
          (isFirstBlock(index) && subRequest.sign) ## Fill(7, false.B)
        )
      )
    })

    val maskResponseVec = attributeVec.map(_(4))
    val maskResponseSelect: Vec[Bool] = Mux1H(
      vSew1H,
      Seq(
        VecInit(maskResponseVec),
        VecInit(maskResponseVec(1), maskResponseVec(3), maskResponseVec(3), maskResponseVec(3)),
        VecInit(maskResponseVec(3), maskResponseVec(1), maskResponseVec(2), maskResponseVec(3))
      )
    )
    (responseSelect.asUInt, maskResponseSelect.asUInt, overflowVec.asUInt)
  }

  response.data          := VecInit(responseVec.map(_._1)).asUInt
  response.adderMaskResp := VecInit(responseVec.map(_._2)).asUInt
  response.vxsat         := VecInit(responseVec.map(_._3)).reduce(_ | _)
  response.executeIndex  := request.executeIndex
}
