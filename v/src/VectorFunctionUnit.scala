package v

import chisel3._
import chisel3.util.{Arbiter, Decoupled, DecoupledIO}

import scala.collection.immutable.SeqMap

trait VFUParameter {
  val decodeField: BoolField
  val inputBundle: Data
}

case class VFUInstantiateParameter(slotCount: Int, vfuParameters: Seq[(VFUParameter, Seq[Int])]) {
  vfuParameters.foreach {
    case (_, connect) =>
      connect.foreach(connectIndex => require(connectIndex < slotCount))
  }

}

class SlotExecuteRequest(slotIndex: Int, parameter: VFUInstantiateParameter) extends Record with experimental.AutoCloneType {
  val elements: SeqMap[String, Data] = SeqMap.from(
    parameter.vfuParameters.filter(_._2.contains(slotIndex)).map { case (p, _) =>
      p.decodeField.name -> Decoupled(p.inputBundle)
    }
  )
}

object slotConnect {
  def connectGen(parameter: VFUInstantiateParameter): (Vec[SlotExecuteRequest], Vec[DecoupledIO[Data]]) = {
    // 声明入口
    val requestVecFromSlot: Vec[SlotExecuteRequest] = Wire(VecInit(Seq.tabulate(parameter.slotCount) { index =>
      new SlotExecuteRequest(index, parameter)
    }))

    // 声明出口
    val requestVecToVFU: Vec[DecoupledIO[Data]] = Wire(
      VecInit(parameter.vfuParameters.map {case (p, _) => Decoupled(p.inputBundle)})
    )
    // 开始连接
    // slot 最多只会访问一个同类型的 vfu
    requestVecToVFU.zip(parameter.vfuParameters).foreach { case (data, (p, slotVec)) =>
      val requestArbiter = new Arbiter(data, slotVec.size).suggestName(s"${p.decodeField.name}Arbiter")
      requestArbiter.io.in.zip(slotVec).foreach { case (arbiterInput, slotIndex) =>
        arbiterInput <> requestVecFromSlot(slotIndex).elements(p.decodeField.name)
      }
      data <> requestArbiter.io.out
    }

    (requestVecFromSlot, requestVecToVFU)
  }
}