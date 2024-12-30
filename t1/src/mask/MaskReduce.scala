// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance, Instantiate}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.properties.{AnyClassType, Property}
import chisel3.util._
import org.chipsalliance.stdlib.GeneralOM

class ReduceInput(datapathWidth: Int, laneNumber: Int, fpuEnable: Boolean) extends Bundle {
  val maskType:      Bool         = Bool()
  val eew:           UInt         = UInt(2.W)
  val uop:           UInt         = UInt(3.W)
  val readVS1:       UInt         = UInt(datapathWidth.W)
  val source2:       UInt         = UInt((laneNumber * datapathWidth).W)
  val sourceValid:   UInt         = UInt(laneNumber.W)
  val lastGroup:     Bool         = Bool()
  val vxrm:          UInt         = UInt(3.W)
  val aluUop:        UInt         = UInt(4.W)
  val sign:          Bool         = Bool()
  // for fpu
  val fpSourceValid: Option[UInt] = Option.when(fpuEnable)(UInt(laneNumber.W))
}

class ReduceOutput(datapathWidth: Int) extends Bundle {
  val data: UInt = UInt(datapathWidth.W)
  val mask: UInt = UInt((datapathWidth / 8).W)
}

object MaskReduceParameter {
  implicit def rw: upickle.default.ReadWriter[MaskReduceParameter] = upickle.default.macroRW
}

case class MaskReduceParameter(datapathWidth: Int, laneNumber: Int, fpuEnable: Boolean)
    extends SerializableModuleParameter

class MaskReduceInterface(parameter: MaskReduceParameter) extends Bundle {
  val clock          = Input(Clock())
  val reset          = Input(Reset())
  val in             = Flipped(Decoupled(new ReduceInput(parameter.datapathWidth, parameter.laneNumber, parameter.fpuEnable)))
  val out            = Valid(new ReduceOutput(parameter.datapathWidth))
  val firstGroup     = Input(Bool())
  val newInstruction = Input(Bool())
  val validInst      = Input(Bool())
  val pop            = Input(Bool())
  val om             = Output(Property[AnyClassType]())
}

@instantiable
class MaskReduceOM(parameter: MaskReduceParameter) extends GeneralOM[MaskReduceParameter, MaskReduce](parameter) {
  @public
  val floatAdder   = Option.when(parameter.fpuEnable)(IO(Output(Property[AnyClassType]())))
  @public
  val floatAdderIn = Option.when(parameter.fpuEnable)(IO(Input(Property[AnyClassType]())))
  floatAdder.zip(floatAdderIn).foreach { case (l, r) => l := r }
}

class MaskReduce(val parameter: MaskReduceParameter)
    extends FixedIORawModule(new MaskReduceInterface(parameter))
    with SerializableModule[MaskReduceParameter]
    with ImplicitClock
    with ImplicitReset {
  protected def implicitClock = io.clock
  protected def implicitReset = io.reset
  val in                      = io.in
  val out                     = io.out
  val firstGroup              = io.firstGroup
  val newInstruction          = io.newInstruction
  val validInst               = io.validInst
  val pop                     = io.pop

  val omInstance: Instance[MaskReduceOM] = Instantiate(new MaskReduceOM(parameter))
  io.om := omInstance.getPropertyReference

  val floatAdderLatency: Int = 1
  val maskSize:          Int = parameter.laneNumber * parameter.datapathWidth / 8

  // todo: uop decode
  val order:    Bool = in.bits.uop === "b101".U
  val reqWiden: Bool = in.bits.uop === "b001".U || in.bits.uop(2, 1) === "b11".U

  val eew1H:         UInt = UIntToOH(in.bits.eew)(2, 0)
  val nextFoldCount: Bool = eew1H(0) && !reqWiden

  // reduce function unit
  val adder:       Instance[ReduceAdder]          = Instantiate(new ReduceAdder(ReduceAdderParameter(parameter.datapathWidth)))
  val logicUnit:   Instance[LaneLogic]            = Instantiate(new LaneLogic(LaneLogicParameter(parameter.datapathWidth)))
  // option unit for flot reduce
  val floatAdder:  Option[Instance[FloatAdder]]   =
    Option.when(parameter.fpuEnable)(Instantiate(new FloatAdder(FloatAdderParameter(8, 24, floatAdderLatency))))
  omInstance.floatAdderIn.zip(floatAdder).foreach { case (l, r) => l := r.io.om.asAnyClassType }
  val flotCompare: Option[Instance[FloatCompare]] =
    Option.when(parameter.fpuEnable)(Instantiate(new FloatCompare(FloatCompareParameter(8, 24))))

  // init reg
  val reduceInit:     UInt = RegInit(0.U(parameter.datapathWidth.W))
  val reduceResult:   UInt = Wire(UInt(parameter.datapathWidth.W))
  val crossFoldCount: UInt = RegInit(0.U(log2Ceil(parameter.laneNumber).W))
  val lastFoldCount:  Bool = RegInit(false.B)
  val updateResult:   Bool = Wire(Bool())
  val sourceValid:    Bool = Wire(Bool())

  val reqReg          = RegEnable(in.bits, 0.U.asTypeOf(in.bits), in.fire)
  // todo: handle reqReg.sourceValid
  val groupLastReduce = crossFoldCount.andR
  val lastFoldEnd     = !lastFoldCount
  val outValid:       Bool = WireDefault(false.B)
  // todo: skip float reduce
  val skipFlotReduce: Bool = WireDefault(false.B)

  val eew1HReg:   UInt = UIntToOH(reqReg.eew)(2, 0)
  val floatType:  Bool = reqReg.uop(2) || reqReg.uop(1, 0).andR
  val NotAdd:     Bool = reqReg.uop(1)
  val widen:      Bool = reqReg.uop === "b001".U || reqReg.uop(2, 1) === "b11".U
  val floatAdd:   Bool = floatType && !NotAdd
  // eew1HReg(0) || (eew1HReg(1) && !widen)
  val needFold:   Bool = false.B
  val writeEEW:   UInt = Mux(pop, 2.U, reqReg.eew + widen)
  val writeEEW1H: UInt = UIntToOH(writeEEW)(2, 0)
  val writeMask:  UInt = Fill(2, writeEEW1H(2)) ## !writeEEW1H(0) ## true.B

  // crossFold: reduce between lane
  // lastFold: reduce in data path
  // orderRed: order reduce
  val idle :: crossFold :: lastFold :: orderRed :: waitRes :: Nil = Enum(5)
  val state: UInt = RegInit(idle)

  val stateIdle:  Bool = state === idle
  val stateCross: Bool = state === crossFold
  val stateLast:  Bool = state === lastFold
  val stateOrder: Bool = state === orderRed
  val stateWait:  Bool = state === waitRes

  // wait float
  val waitCount: UInt = RegInit(0.U(log2Ceil(floatAdderLatency.max(2)).W))
  when(stateWait) { waitCount := waitCount + 1.U }
  val resFire:   Bool = stateWait && waitCount === (floatAdderLatency - 1).U
  updateResult :=
    stateLast || (stateCross && sourceValid && !floatAdd) || (resFire && sourceValid)
  val waiteDeq: Bool = stateWait && resFire

  // state update
  in.ready := stateIdle
  when(stateIdle) {
    when(in.valid) {
      state := Mux(order, orderRed, crossFold)
    }
  }

  when(stateCross) {
    when(floatAdd) {
      state     := waitRes
      waitCount := 0.U
    }.elsewhen(groupLastReduce) {
      state    := Mux(reqReg.lastGroup && needFold, lastFold, idle)
      outValid := reqReg.lastGroup && !needFold
    }
  }

  when(waiteDeq) {
    when(groupLastReduce) {
      state    := Mux(reqReg.lastGroup && needFold, lastFold, idle)
      outValid := reqReg.lastGroup && !needFold
    }.otherwise {
      state := Mux(order, orderRed, crossFold)
    }
  }

  when(stateOrder) {
    state     := waitRes
    waitCount := 0.U
  }

  when(stateLast) {
    when(lastFoldEnd) {
      state    := idle
      outValid := true.B
    }
  }

  val widenEnqMask:   UInt = Fill(2, in.bits.eew.orR) ## true.B ## true.B
  val normalMask:     UInt = Fill(2, in.bits.eew(1)) ## in.bits.eew.orR ## true.B
  val enqWriteMask:   UInt = Mux(reqWiden, widenEnqMask, normalMask)
  val updateInitMask: UInt = FillInterleaved(8, enqWriteMask)
  val updateMask:     UInt = FillInterleaved(8, writeMask)
  when(firstGroup || newInstruction) {
    reduceInit     := Mux(pop || newInstruction, 0.U, in.bits.readVS1 & updateInitMask)
    crossFoldCount := 0.U
    lastFoldCount  := nextFoldCount
  }

  // count update
  // todo: stateCross <=> stateOrder ??
  when((stateCross && !floatAdd) || waiteDeq || in.fire) {
    crossFoldCount := Mux(in.fire, 0.U, crossFoldCount + 1.U)
  }

  // result update
  when(updateResult) {
    reduceInit := reduceResult & updateMask
  }

  when(stateLast) {
    lastFoldCount := false.B
  }

  val selectLaneResult:     UInt = Mux1H(
    UIntToOH(crossFoldCount),
    cutUInt(reqReg.source2, parameter.datapathWidth)
  )
  val sourceValidCalculate: UInt =
    reqReg.fpSourceValid
      .map(fv => Mux(floatType, fv & reqReg.sourceValid, reqReg.sourceValid))
      .getOrElse(reqReg.sourceValid)
  sourceValid := Mux1H(
    UIntToOH(crossFoldCount),
    sourceValidCalculate.asBools
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
  adder.io.request.src    := VecInit(Seq(reduceInit, source2Select))
  adder.io.request.opcode := Mux(pop, 0.U, reqReg.aluUop)
  adder.io.request.sign   := reqReg.sign
  adder.io.request.vSew   := writeEEW

  floatAdder.foreach { fAdder =>
    fAdder.io.clock        := implicitClock
    fAdder.io.reset        := implicitReset
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

  logicUnit.io.req.src    := VecInit(Seq(reduceInit, source2Select))
  logicUnit.io.req.opcode := reqReg.aluUop

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
    flotReduceResult.getOrElse(adder.io.response.data),
    Mux(NotAdd, logicUnit.io.resp, adder.io.response.data)
  )

  out.valid     := outValid && !pop
  out.bits.data := Mux(updateResult, reduceResult, reduceInit)
  out.bits.mask := writeMask & Fill(4, validInst)
}
