// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import org.chipsalliance.t1.rtl.vfu.{VectorAdder, VectorAdderParameter}
import chisel3.properties.{AnyClassType, Class, Property}

class ReduceAdderReq(datapathWidth: Int) extends Bundle {
  val src:    Vec[UInt] = Vec(2, UInt(datapathWidth.W))
  val opcode: UInt      = UInt(4.W)
  val vSew:   UInt      = UInt(2.W)
  val sign:   Bool      = Bool()
}

class ReduceAdderResponse(datapathWidth: Int) extends Bundle {
  val data: UInt = UInt(datapathWidth.W)
}

case class ReduceAdderParameter(datapathWidth: Int) extends SerializableModuleParameter

class ReduceAdderInterface(parameter: ReduceAdderParameter) extends Bundle {
  val request  = Input(new ReduceAdderReq(parameter.datapathWidth))
  val response = Output(new ReduceAdderResponse(parameter.datapathWidth))
  val om       = Output(Property[AnyClassType]())
}

class ReduceAdderOM extends Class {}

class ReduceAdder(val parameter: ReduceAdderParameter)
    extends FixedIORawModule(new ReduceAdderInterface(parameter))
    with SerializableModule[ReduceAdderParameter] {
  val omInstance: Instance[ReduceAdderOM] = Instantiate(new ReduceAdderOM)
  io.om := omInstance.getPropertyReference

  val request  = io.request
  val response = io.response

  // ["add", "sub", "slt", "sle", "sgt", "sge", "max", "min", "seq", "sne", "adc", "sbc"]
  val uopOH:         UInt = UIntToOH(request.opcode)(11, 0)
  val isSub:         Bool = !(uopOH(0) || uopOH(10))
  // sub -> src(1) - src(0)
  val subOperation0: UInt = Mux(isSub, (~request.src.head).asUInt, request.src.head)
  val subOperation1: UInt = request.src.last
  // sub + 1 || carry || borrow
  val operation2 = Fill(4, isSub)
  val vSew1H     = UIntToOH(request.vSew)(2, 0)

  val adder: Instance[VectorAdder] = Instantiate(new VectorAdder(VectorAdderParameter(parameter.datapathWidth)))

  adder.io.a   := subOperation0
  adder.io.b   := subOperation1
  adder.io.cin := Mux1H(
    vSew1H,
    Seq(
      operation2,
      operation2(1) ## operation2(1) ## operation2(0) ## operation2(0),
      Fill(4, operation2(0))
    )
  )
  adder.io.sew := UIntToOH(request.vSew)

  val adderResult = adder.io.z
  // sew = 0 -> 3210
  // sew = 1 -> 1?0?
  // sew = 2 -> 0???
  val adderCarryOut: UInt =
    Mux1H(vSew1H, Seq(adder.io.cout(3), adder.io.cout(1), adder.io.cout(0))) ## adder.io.cout(2) ##
      Mux(vSew1H(0), adder.io.cout(1), adder.io.cout(0)) ## adder.io.cout(0)

  // 8 bit / element
  val adderResultVec: Vec[UInt] = cutUInt(adderResult, 8)

  val attributeVec: Seq[Bool] = adderResultVec.zipWithIndex.map { case (data, index) =>
    val sourceSign0    = cutUInt(request.src.head, 8)(index)(7)
    val sourceSign1    = cutUInt(request.src.last, 8)(index)(7)
    val operation0Sign = (sourceSign0 && request.sign) ^ isSub
    val operation1Sign = sourceSign1 && request.sign
    val resultSign     = data.asBools.last
    val uIntLess       = Mux(sourceSign0 ^ sourceSign1, sourceSign0, resultSign)

    /** 下溢条件:
      *   1. 两操作数都是负的,结果符号位变成0了 eg: 0x80000000 + 0xffffffff(+ -1 or - 1)
      *   1. isSub & U, 结果符号位是1 eg: 1 - 3
      */
    val lowerOverflow: Bool =
      (operation0Sign && operation1Sign && !resultSign) ||
        // todo
        (isSub && !request.sign && uIntLess)

    /** 上溢条件：
      *   1. S: 两正的加出了符号位
      *   1. U: + && 溢出位有值
      */
    val upperOverflow: Bool = Mux(
      request.sign,
      !operation0Sign && !operation1Sign && resultSign,
      adderCarryOut(index) && !isSub
    )
    val less = Mux(
      request.sign,
      (data(7) && !upperOverflow) || lowerOverflow,
      uIntLess
    )
    less
  }

  val lessVec: Vec[Bool] = Mux1H(
    vSew1H,
    Seq(
      VecInit(attributeVec),
      VecInit(attributeVec(1), attributeVec(1), attributeVec(3), attributeVec(3)),
      VecInit(attributeVec(3), attributeVec(3), attributeVec(3), attributeVec(3))
    )
  )

  val responseSelect: Vec[UInt] = VecInit(adderResultVec.zipWithIndex.map { case (data, index) =>
    val less = lessVec(index)
    Mux1H(
      Seq(
        (uopOH(1, 0) ## uopOH(11, 10)).orR,
        (uopOH(6) && !less) || (uopOH(7) && less),
        (uopOH(6) && less) || (uopOH(7) && !less)
      ),
      Seq(
        data,
        cutUInt(request.src.last, 8)(index),
        cutUInt(request.src.head, 8)(index)
      )
    )
  })

  response.data := responseSelect.asUInt
}
