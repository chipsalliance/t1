// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.{SerializableModuleGenerator}
import chisel3.properties.Property
import org.chipsalliance.t1.rtl.decoder.BoolField
import chisel3.util._

import scala.collection.immutable.SeqMap

trait VFUParameter {
  val decodeField: BoolField
  val inputBundle: Data
  val outputBundle: Bundle
  val singleCycle: Boolean = true
  val NeedSplit: Boolean = false
  val latency: Int
}

abstract class VFUModule(p: VFUParameter) extends Module {
  val requestIO: DecoupledIO[Data] = IO(Flipped(Decoupled(p.inputBundle)))
  val responseIO: DecoupledIO[Bundle] = IO(Decoupled(p.outputBundle))
  // FFUModule is a behavior Module which should be retimed to [[latency]] cycles.
  val retime: Option[Property[Int]] = Option.when(p.latency > 0)(IO(Property[Int]()))
  retime.foreach(_ := Property(p.latency))

  if (p.singleCycle) {
    requestIO.ready := true.B
    responseIO.valid := requestIO.valid
  }

  def connectIO(response: Data, responseValid: Bool = true.B): Data = {
    if (p.singleCycle) {
      responseIO.bits := response.asTypeOf(responseIO.bits)
    } else {
      // todo: Confirm the function of 'Pipe'
      val pipeResponse: ValidIO[Bundle] = Pipe(responseValid, response.asTypeOf(responseIO.bits), p.latency)
      responseIO.valid := pipeResponse.valid
      responseIO.bits := pipeResponse.bits
    }
    requestIO.bits
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