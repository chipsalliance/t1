// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, public, Instance}
import chisel3.experimental.{SerializableModuleGenerator, SerializableModuleParameter}
import chisel3.properties.{AnyClassType, ClassType, Property}
import chisel3.util._
import org.chipsalliance.stdlib.GeneralOM
import org.chipsalliance.t1.rtl.decoder.BoolField

import scala.collection.immutable.SeqMap

trait VFUParameter extends SerializableModuleParameter {
  val decodeField:  BoolField
  val inputBundle:  VFUPipeBundle
  val outputBundle: VFUPipeBundle
  // The execution cycle will not change
  val singleCycle: Boolean = true
  val NeedSplit:   Boolean = false
  val latency: Int
}

class VFUPipeBundle extends Bundle {
  val tag: UInt = UInt(2.W)
}

@instantiable
abstract class VFUModule extends Module {
  val parameter:  VFUParameter
  val omInstance: Instance[GeneralOM[_, _]]
  @public
  val om:         Property[ClassType]        = IO(Output(Property[AnyClassType]()))
  @public
  val requestIO:  DecoupledIO[VFUPipeBundle] = IO(Flipped(Decoupled(parameter.inputBundle)))
  @public
  val responseIO: DecoupledIO[VFUPipeBundle] = IO(Decoupled(parameter.outputBundle))
  atModuleBodyEnd {
    om := omInstance.getPropertyReference.asAnyClassType
  }

  val vfuRequestReady: Option[Bool]  = Option.when(!parameter.singleCycle)(Wire(Bool()))
  val requestReg:      VFUPipeBundle = RegEnable(requestIO.bits, 0.U.asTypeOf(requestIO.bits), requestIO.fire)
  val requestRegValid: Bool          = RegInit(false.B)
  val vfuRequestFire:  Bool          = vfuRequestReady.getOrElse(true.B) && requestRegValid

  def connectIO(response: VFUPipeBundle, responseValid: Bool = true.B): Data = {
    response.tag := DontCare
    if (parameter.singleCycle && parameter.latency == 0) {
      responseIO.bits  := response.asTypeOf(responseIO.bits)
      responseIO.valid := requestRegValid
    } else {
      val responseWire = WireDefault(response.asTypeOf(responseIO.bits))
      val responseValidWire: Bool            = Wire(Bool())
      // connect tag
      if (parameter.singleCycle) {
        responseWire.tag  := requestReg.tag
        responseValidWire := requestRegValid
      } else {
        // for div...
        responseWire.tag  := RegEnable(requestReg.tag, 0.U, vfuRequestFire)
        responseValidWire := responseValid
      }
      // todo: Confirm the function of 'Pipe'
      val pipeResponse:      ValidIO[Bundle] = Pipe(responseValidWire, responseWire, parameter.latency)
      responseIO.valid := pipeResponse.valid
      responseIO.bits  := pipeResponse.bits
    }
    requestReg
  }

  // update requestRegValid
  if (parameter.singleCycle) {
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

  // generics templates
  def parse(vLen: Int, dLen: Int, preset: String, fp: Boolean, zvbb: Boolean) = preset match {
    case "minimal" =>
      (fp, zvbb) match {
        case (false, false) => VFUInstantiateParameter.minimalInt(vLen, dLen)
        case (true, false)  => VFUInstantiateParameter.minimalFP(vLen, dLen)
        case (false, true)  => VFUInstantiateParameter.zvbb(vLen, dLen)
        case (true, true)   => VFUInstantiateParameter.zvbbFP(vLen, dLen)
      }
    case "small"   =>
      (fp, zvbb) match {
        case (false, false) => VFUInstantiateParameter.smallInt(vLen, dLen)
        case (true, false)  => VFUInstantiateParameter.smallFP(vLen, dLen)
        case (false, true)  => VFUInstantiateParameter.zvbb(vLen, dLen)
        case (true, true)   => VFUInstantiateParameter.zvbbFP(vLen, dLen)
      }
    case "medium"  =>
      (fp, zvbb) match {
        case (false, false) => VFUInstantiateParameter.smallInt(vLen, dLen)
        case (true, false)  => VFUInstantiateParameter.mediumFP(vLen, dLen)
        case (false, true)  => VFUInstantiateParameter.zvbb(vLen, dLen)
        case (true, true)   => VFUInstantiateParameter.zvbbFP(vLen, dLen)
      }
    case "large"   =>
      (fp, zvbb) match {
        case (false, false) => VFUInstantiateParameter.smallInt(vLen, dLen)
        case (true, false)  => VFUInstantiateParameter.largeFP(vLen, dLen)
        case (false, true)  => VFUInstantiateParameter.zvbb(vLen, dLen)
        case (true, true)   => VFUInstantiateParameter.zvbbFP(vLen, dLen)
      }
    case "huge"    =>
      (fp, zvbb) match {
        case (false, false) => VFUInstantiateParameter.smallInt(vLen, dLen)
        case (true, false)  => VFUInstantiateParameter.hugeFP(vLen, dLen)
        case (false, true)  => VFUInstantiateParameter.zvbb(vLen, dLen)
        case (true, true)   => VFUInstantiateParameter.zvbbFP(vLen, dLen)
      }
  }

  // instantiate each module and connect to all scoreboards
  def minimalFP(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(),
    divfpModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0, 1, 2, 3)
      )
    ),
    floatModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
    zvbbModuleParameters = Seq()
  )

  // standalone ALU for all scoreboards
  def smallFP(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(),
    divfpModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0, 1, 2, 3)
      )
    ),
    floatModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
    zvbbModuleParameters = Seq()
  )

  // standalone VFU(except MUL and DIV) for all scoreboards
  def mediumFP(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(),
    divfpModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(1)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(2)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(3)
      )
    ),
    floatModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
    zvbbModuleParameters = Seq()
  )

  // standalone VFU(except DIV) for all scoreboards
  def largeFP(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(),
    divfpModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(1)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(2)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(3)
      )
    ),
    floatModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(3))
    ),
    zvbbModuleParameters = Seq()
  )

  // standalone VFU for all scoreboards
  def hugeFP(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(),
    divfpModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(1)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(2)
      ),
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(3)
      )
    ),
    floatModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(3))
    ),
    zvbbModuleParameters = Seq()
  )

  def minimalInt(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    divfpModuleParameters = Seq(),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0, 1, 2, 3)
      )
    ),
    floatModuleParameters = Seq(),
    zvbbModuleParameters = Seq() // TODO
  )

  def smallInt(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    divfpModuleParameters = Seq(),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0, 1, 2, 3)
      )
    ),
    floatModuleParameters = Seq(),
    zvbbModuleParameters = Seq() // TODO
  )

  // experimental
  def zvbb(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    divfpModuleParameters = Seq(),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0, 1, 2, 3)
      )
    ),
    floatModuleParameters = Seq(),
    zvbbModuleParameters = Seq((SerializableModuleGenerator(classOf[LaneZvbb], LaneZvbbParam(32, 3)), Seq(0, 1, 2, 3)))
  )

  def zvbbFP(vLen: Int, dLen: Int) = VFUInstantiateParameter(
    slotCount = 4,
    logicModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
    ),
    aluModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
      (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
    ),
    shifterModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
    ),
    mulModuleParameters = Seq(
      (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
    ),
    divModuleParameters = Seq(),
    divfpModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
    otherModuleParameters = Seq(
      (
        SerializableModuleGenerator(
          classOf[OtherUnit],
          OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
        ),
        Seq(0, 1, 2, 3)
      )
    ),
    floatModuleParameters =
      Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
    zvbbModuleParameters = Seq((SerializableModuleGenerator(classOf[LaneZvbb], LaneZvbbParam(32, 3)), Seq(0, 1, 2, 3)))
  )
}

case class VFUInstantiateParameter(
  slotCount:               Int,
  logicModuleParameters:   Seq[(SerializableModuleGenerator[MaskedLogic, LogicParam], Seq[Int])],
  aluModuleParameters:     Seq[(SerializableModuleGenerator[LaneAdder, LaneAdderParam], Seq[Int])],
  shifterModuleParameters: Seq[(SerializableModuleGenerator[LaneShifter, LaneShifterParameter], Seq[Int])],
  mulModuleParameters:     Seq[(SerializableModuleGenerator[LaneMul, LaneMulParam], Seq[Int])],
  divModuleParameters:     Seq[(SerializableModuleGenerator[LaneDiv, LaneDivParam], Seq[Int])],
  divfpModuleParameters:   Seq[(SerializableModuleGenerator[LaneDivFP, LaneDivFPParam], Seq[Int])],
  otherModuleParameters:   Seq[(SerializableModuleGenerator[OtherUnit, OtherUnitParam], Seq[Int])],
  floatModuleParameters:   Seq[(SerializableModuleGenerator[LaneFloat, LaneFloatParam], Seq[Int])],
  zvbbModuleParameters: Seq[(SerializableModuleGenerator[LaneZvbb, LaneZvbbParam], Seq[Int])]) {
  val genVec =
    logicModuleParameters ++
      aluModuleParameters ++
      shifterModuleParameters ++
      mulModuleParameters ++
      divModuleParameters ++
      divfpModuleParameters ++
      otherModuleParameters ++
      floatModuleParameters ++
      zvbbModuleParameters
  genVec.foreach { case (_, connect) =>
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
