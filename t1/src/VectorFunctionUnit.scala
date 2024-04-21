// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.properties.Property
import org.chipsalliance.t1.rtl.decoder.BoolField
import chisel3.util._

import scala.collection.immutable.SeqMap

trait VFUParameter {
  val decodeField: BoolField
  val inputBundle: VFUPipeBundle
  val outputBundle: VFUPipeBundle
  val piped: Boolean
  val useDistributor: Boolean
  val latency: Int
  require(latency > 0, "every unit should have a least one cycle.")
}

class VFUPipeBundle extends Bundle {
  val tag: UInt = UInt(2.W)
}

@instantiable
abstract class VFUModule(p: VFUParameter) extends Module {
  @public
  val requestIO: DecoupledIO[VFUPipeBundle] = IO(Flipped(Decoupled(p.inputBundle)))
  @public
  val responseIO: DecoupledIO[VFUPipeBundle] = IO(Decoupled(p.outputBundle))
  // FFUModule is a behavior Module which should be retimed to [[latency]] cycles.
  @public
  val retime: Option[Property[Int]] = Option.when(p.latency > 1)(IO(Property[Int]()))
  retime.foreach(_ := Property(p.latency))

  val vfuRequestReady: Option[Bool] = Option.when(!p.piped)(Wire(Bool()))
  val requestReg: VFUPipeBundle = RegEnable(requestIO.bits, 0.U.asTypeOf(requestIO.bits), requestIO.fire)
  val requestRegValid: Bool = RegInit(false.B)
  val vfuRequestFire: Bool = vfuRequestReady.getOrElse(true.B) && requestRegValid

  def connectIO(response: VFUPipeBundle, responseValid: Bool = true.B): Data = {
    response.tag := DontCare
    if (p.piped) {
      // This implementation consumes too much power, need token+shifter with enable.
      val responseWire = Wire(Valid(chiselTypeOf(response)))
      responseWire.valid := requestRegValid
      responseWire.bits := response
      responseWire.bits.tag := requestReg.tag
      val pipeResponse: Seq[ValidIO[VFUPipeBundle]] = ShiftRegisters(responseWire, p.latency)
      pipeResponse.zipWithIndex.foreach(r => r._1.suggestName(s"retimeShifterBits${r._2}"))
      responseIO.valid := pipeResponse.last.valid
      responseIO.bits := pipeResponse.last.bits
    } else {
      responseIO.valid := responseValid
      responseIO.bits := response
      responseIO.bits.tag := RegEnable(requestReg.tag, 0.U, vfuRequestFire)
    }
    requestReg
  }

  // update requestRegValid
  if (p.piped) {
    requestIO.ready := true.B
    requestRegValid := requestIO.fire
  } else {
    when(vfuRequestFire ^ requestIO.fire) {
      requestRegValid := requestIO.fire
    }
    requestIO.ready := !requestRegValid || vfuRequestReady.get
  }
}

object VFUInstantiateParameter {
  implicit def rw: upickle.default.ReadWriter[VFUInstantiateParameter] = upickle.default.macroRW
}

case class VFUInstantiateParameter(
                                    slotCount: Int,
                                    logicModuleParameters: Seq[(SerializableModuleGenerator[MaskedLogic, LogicParam], Seq[Int])],
                                    aluModuleParameters: Seq[(SerializableModuleGenerator[LaneAdder, LaneAdderParam], Seq[Int])],
                                    shifterModuleParameters: Seq[(SerializableModuleGenerator[LaneShifter, LaneShifterParameter], Seq[Int])],
                                    mulModuleParameters: Seq[(SerializableModuleGenerator[LaneMul, LaneMulParam], Seq[Int])],
                                    divModuleParameters: Seq[(SerializableModuleGenerator[LaneDiv, LaneDivParam], Seq[Int])],
                                    divfpModuleParameters: Seq[(SerializableModuleGenerator[LaneDivFP, LaneDivFPParam], Seq[Int])],
                                    otherModuleParameters: Seq[(SerializableModuleGenerator[OtherUnit, OtherUnitParam], Seq[Int])],
                                    floatModuleParameters: Seq[(SerializableModuleGenerator[LaneFloat, LaneFloatParam], Seq[Int])]
                                  ) {
  val genVec: Seq[(SerializableModuleGenerator[_ <: VFUModule, _ <: VFUParameter], Seq[Int])] =
    logicModuleParameters ++
      aluModuleParameters ++
      shifterModuleParameters ++
      mulModuleParameters ++
      divModuleParameters ++
      divfpModuleParameters ++
      otherModuleParameters ++
      floatModuleParameters
  genVec.foreach {
    case (_, connect) =>
      connect.foreach(connectIndex => require(connectIndex < slotCount))
  }
  val maxLatency: Int = genVec.map(_._1.parameter.latency).max
}

class SlotExecuteRequest[T <: SlotRequestToVFU](requestFromSlot: T)(slotIndex: Int, parameter: VFUInstantiateParameter)
  extends Record {
  val elements: SeqMap[String, DecoupledIO[SlotRequestToVFU]] = SeqMap.from(
    parameter.genVec.filter(_._2.contains(slotIndex)).map { case (p, _) =>
      p.parameter.decodeField.name -> Decoupled(requestFromSlot)
    }
  )

  val parameterMap: Map[String, VFUParameter] = SeqMap.from(
    parameter.genVec.filter(_._2.contains(slotIndex)).map { case (p, _) =>
      p.parameter.decodeField.name -> p.parameter
    }
  )
}
