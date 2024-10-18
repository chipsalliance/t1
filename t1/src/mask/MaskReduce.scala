// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.util._

class ReduceInput(parameter: T1Parameter) extends Bundle {
  val maskType:     Bool = Bool()
  val eew:          UInt = UInt(2.W)
  val uop:          UInt = UInt(3.W)
  val readVS1:      UInt = UInt(parameter.datapathWidth.W)
  val source2:      UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val sourceValid:  UInt = UInt(parameter.laneNumber.W)
  val groupCounter: UInt = UInt(parameter.laneParam.groupNumberBits.W)
  val lastGroup:    Bool = Bool()
  val vxrm:         UInt = UInt(3.W)
  val aluUop:       UInt = UInt(4.W)
  val sign:         Bool = Bool()
}

class ReduceOutput(parameter: T1Parameter) extends Bundle {
  val data: UInt = UInt(parameter.datapathWidth.W)
}

class MaskReduce(parameter: T1Parameter) extends Module {
  val in:             DecoupledIO[ReduceInput] = IO(Flipped(Decoupled(new ReduceInput(parameter))))
  val out:            ValidIO[ReduceOutput]    = IO(Valid(new ReduceOutput(parameter)))
  val newInstruction: Bool                     = IO(Input(Bool()))

  val maskSize: Int = parameter.laneNumber * parameter.datapathWidth / 8

  // todo: uop decode
  val order:    Bool = in.bits.uop === "b101".U
  val reqWiden: Bool = in.bits.uop === "b001".U

  val eew1H:         UInt = UIntToOH(in.bits.eew)(2, 0)
  val nextFoldCount: Bool = eew1H(0) && !reqWiden

  // reduce function unit
  val adder:       Instance[ReduceAdder]          = Instantiate(new ReduceAdder(parameter.datapathWidth))
  val logicUnit:   Instance[LaneLogic]            = Instantiate(new LaneLogic(parameter.datapathWidth))
  // option unit for flot reduce
  val floatAdder:  Option[Instance[FloatAdder]]   =
    Option.when(parameter.fpuEnable)(Instantiate(new FloatAdder(8, 24)))
  val flotCompare: Option[Instance[FloatCompare]] =
    Option.when(parameter.fpuEnable)(Instantiate(new FloatCompare(8, 24)))

  // init reg
  val reduceInit:     UInt = RegInit(0.U(parameter.datapathWidth.W))
  val reduceResult:   UInt = Wire(UInt(parameter.datapathWidth.W))
  val crossFoldCount: UInt = RegInit(0.U(log2Ceil(parameter.laneNumber).W))
  val lastFoldCount:  Bool = RegInit(false.B)

  val reqReg          = RegEnable(in.bits, 0.U.asTypeOf(in.bits), in.fire)
  // todo: handle reqReg.sourceValid
  val groupLastReduce = crossFoldCount.andR
  val lastFoldEnd     = !lastFoldCount
  val outValid:       Bool = WireDefault(false.B)
  // todo: skip float reduce
  val skipFlotReduce: Bool = WireDefault(false.B)

  val eew1HReg:  UInt = UIntToOH(reqReg.eew)(2, 0)
  val floatType: Bool = reqReg.uop(2)
  val NotAdd:    Bool = reqReg.uop(1)
  val widen:     Bool = reqReg.uop === "b001".U
  val needFold:  Bool = eew1HReg(0) || (eew1HReg(1) && !widen)

  // crossFold: reduce between lane
  // lastFold: reduce in data path
  // orderRed: order reduce
  val idle :: crossFold :: lastFold :: orderRed :: Nil = Enum(4)
  val state: UInt = RegInit(idle)

  val stateIdle:  Bool = state === idle
  val stateCross: Bool = state === crossFold
  val stateLast:  Bool = state === lastFold
  val stateOrder: Bool = state === orderRed

  // state update
  in.ready := stateIdle
  when(stateIdle) {
    when(in.valid) {
      state := Mux(order, orderRed, crossFold)
    }
  }

  when(stateCross) {
    when(groupLastReduce) {
      state    := Mux(reqReg.lastGroup && needFold, lastFold, idle)
      outValid := reqReg.lastGroup && !needFold
    }
  }

  when(stateOrder) {
    when(groupLastReduce) {
      state    := idle
      outValid := reqReg.lastGroup
    }
  }

  when(stateLast) {
    when(lastFoldEnd) {
      state    := idle
      outValid := true.B
    }
  }

  when(newInstruction) {
    // todo: update reduceInit when first in.fire
    reduceInit     := in.bits.readVS1
    crossFoldCount := 0.U
    lastFoldCount  := nextFoldCount
  }

  // count update
  // todo: stateCross <=> stateOrder ??
  when(stateCross || stateOrder || in.fire) {
    crossFoldCount := Mux(in.fire, 0.U, crossFoldCount + 1.U)
  }

  // result update
  when(!stateIdle) {
    reduceInit := reduceResult
  }

  when(stateLast) {
    lastFoldCount := false.B
  }

  val selectLaneResult: UInt = Mux1H(
    UIntToOH(crossFoldCount),
    cutUInt(reqReg.source2, parameter.datapathWidth)
  )
  val reduceDataVec = cutUInt(reduceInit, 8)
  // reduceFoldCount = false => abcd -> xxab | xxcd -> mask 0011
  // reduceFoldCount = true =>  abcd -> xaxc | xbxd -> mask 0101
  val lastFoldSource1: UInt = Mux(
    lastFoldCount,
    reduceDataVec(3) ## reduceDataVec(3) ## reduceDataVec(1),
    reduceDataVec(3) ## reduceDataVec(3) ## reduceDataVec(2)
  )
  val source2Select:   UInt = Mux(stateCross || stateOrder, selectLaneResult, lastFoldSource1)

  // popCount 在top视为reduce add
  adder.request.src    := VecInit(Seq(reduceInit, source2Select))
  // todo: pop
  adder.request.opcode := reqReg.aluUop(2)
  adder.request.sign   := reqReg.sign
  adder.request.vSew   := reqReg.eew

  floatAdder.foreach { fAdder =>
    fAdder.io.a            := reduceInit
    fAdder.io.b            := source2Select
    fAdder.io.roundingMode := reqReg.vxrm
  }

  flotCompare.foreach { fCompare =>
    fCompare.io.a     := reduceInit
    fCompare.io.b     := source2Select
    // max -> 12, min -> 8
    fCompare.io.isMax := reqReg.aluUop(2)
  }

  logicUnit.req.src    := VecInit(Seq(reduceInit, source2Select))
  logicUnit.req.opcode := reqReg.aluUop

  val flotReduceResult: Option[UInt] = Option.when(parameter.fpuEnable)(
    Mux(
      skipFlotReduce,
      reduceInit,
      Mux(NotAdd, flotCompare.get.io.out, floatAdder.get.io.out)
    )
  )
  // select result
  reduceResult := Mux(
    floatType,
    flotReduceResult.getOrElse(adder.response.data),
    Mux(NotAdd, logicUnit.resp, adder.response.data)
  )

  out.valid     := outValid
  out.bits.data := reduceResult
}
