package v

trait VFUParameter

case class VFUInstantiateParameter(slotCount: Int, vfuParameters: Seq[(VFUParameter, Seq[Int])]) {
  vfuParameters.foreach {
    case (_, connect) =>
      connect.foreach(connectIndex => require(connectIndex < slotCount))
  }
}

object slotConnect {

}