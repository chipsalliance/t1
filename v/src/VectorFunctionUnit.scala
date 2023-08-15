package v

import chisel3._
import chisel3.experimental.SerializableModuleGenerator
import chisel3.util.experimental.decode.DecodeBundle
import chisel3.util._
import chisel3.experimental.AutoCloneType

import scala.collection.immutable.SeqMap

trait VFUParameter {
  val decodeField: BoolField
  val inputBundle: Data
  val outputBundle: Bundle
  val singleCycle: Boolean = true
}

abstract class VFUModule(p: VFUParameter) extends Module {
  val requestIO: DecoupledIO[Data] = IO(Flipped(Decoupled(p.inputBundle)))
  val responseIO: DecoupledIO[Bundle] = IO(Decoupled(p.outputBundle))

  if (p.singleCycle) {
    requestIO.ready := true.B
    responseIO.valid := requestIO.valid
  }

  def connectIO(response: Data): Data = {
    responseIO.bits := response.asTypeOf(responseIO.bits)
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

}

class SlotExecuteRequest(slotIndex: Int, parameter: VFUInstantiateParameter) extends Record with AutoCloneType {
  val elements: SeqMap[String, Data] = SeqMap.from(
    parameter.genVec.filter(_._2.contains(slotIndex)).map { case (p, _) =>
      p.parameter.decodeField.name -> Decoupled(p.parameter.inputBundle)
    }
  )

  val parameterMap: Map[String, VFUParameter] = SeqMap.from(
    parameter.genVec.filter(_._2.contains(slotIndex)).map { case (p, _) =>
      p.parameter.decodeField.name -> p.parameter
    }
  )
}

object vfu {
  def vfuConnect(parameter: VFUInstantiateParameter)(
    requestVec: Vec[SlotRequestToVFU],
    requestValid: Vec[Bool],
    decodeResult: Seq[DecodeBundle],
    executeEnqueueFire: Vec[Bool],
    responseVec: Vec[ValidIO[VFUResponseToSlot]],
    executeOccupied: Vec[Bool],
    VFUNotClear: Bool
  ): Unit = {

    // 声明 vfu 的入口
    val requestVecFromSlot: Seq[SlotExecuteRequest] = Seq.tabulate(parameter.slotCount) { index =>
      Wire(new SlotExecuteRequest(index, parameter))
    }

    // 连接 requestVecFromSlot 根据从lane里来的握手信息
    requestVecFromSlot.zipWithIndex.foreach { case (request, slotIndex) =>
      val requestFire = request.elements.map { case (name: String, reqForVfu: DecoupledIO[Bundle]) =>
        // 检测类型
        val requestParameter: VFUParameter = request.parameterMap(name)
        val typeCheck = decodeResult(slotIndex)(requestParameter.decodeField)
        // 连接 valid
        reqForVfu.valid := requestValid(slotIndex) && typeCheck

        // 连接bits
        reqForVfu.bits.elements.foreach {case (s, d) =>
          d match {
            // src 不等长,所以要特别连
            case src: Vec[Data] => src.zipWithIndex.foreach {case(sd, si) => sd := requestVec(slotIndex).src(si)}
            case _ => d := requestVec(slotIndex).elements(s)
          }
        }

        // 返回 fire
        reqForVfu.fire
      }.reduce(_ || _)
      executeEnqueueFire(slotIndex) := requestFire
    }

    val vrfIsBusy = Wire(Vec(parameter.genVec.size, Bool()))
    // 处理vfu
    val vfuResponse: Seq[ValidIO[VFUResponseToSlot]] = parameter.genVec.zipWithIndex.map { case ((gen, slotVec), vfuIndex) =>
      // vfu 模块
      val vfu = Module(gen.module()).suggestName(gen.parameter.decodeField.name)
      // 访问仲裁
      val requestArbiter: Arbiter[Data] = Module(
        new Arbiter(
          chiselTypeOf(vfu.requestIO.bits),
          slotVec.size
        )
      ).suggestName(s"${gen.parameter.decodeField.name}Arbiter")

      requestArbiter.io.in.zip(slotVec).foreach { case (arbiterInput, slotIndex) =>
        arbiterInput <> requestVecFromSlot(slotIndex).elements(gen.parameter.decodeField.name)
      }
      vfu.requestIO <> requestArbiter.io.out
      if (vfu.responseIO.bits.elements.contains("busy")) {
        vrfIsBusy(vfuIndex) := vfu.responseIO.bits.elements("busy").asInstanceOf[Bool] // || !vfu.requestIO.ready
      } else {
        vrfIsBusy(vfuIndex) := false.B // !vfu.requestIO.ready
      }

      // 处理 output
      val responseBundle: ValidIO[VFUResponseToSlot] = WireDefault(0.U.asTypeOf(responseVec.head))

      // 暂时不会有 response 握手
      responseBundle.valid := vfu.responseIO.valid
      vfu.responseIO.ready := true.B

      // 把 vfu的 response 类型转换过来
      responseBundle.bits.elements.foreach { case (name, data) =>
        if (vfu.responseIO.bits.elements.contains(name)) {
          data := vfu.responseIO.bits.elements(name)
        }
      }
      executeOccupied(vfuIndex) := vfu.requestIO.fire
      VFUNotClear := vrfIsBusy.asUInt.orR
      responseBundle
    }

    // 把response丢给slot
    responseVec.zipWithIndex.foreach{ case (data, slotIndex) =>
      // 筛选 response
      val responseFilter: Seq[(Bool, ValidIO[VFUResponseToSlot])] = vfuResponse.zip(parameter.genVec).filter(_._2._2.contains(slotIndex)).map {
        case (resp, (gen, _)) =>
          (decodeResult(slotIndex)(gen.parameter.decodeField), resp)
      }
      // 多个slot同类长延时型访问需要自己带tag
      data <> Mux1H(
        responseFilter.map(_._1),
        responseFilter.map(_._2),
      )
    }
  }
}