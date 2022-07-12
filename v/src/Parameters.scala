package v
import chisel3.util.log2Ceil


case class DataPathParam(dataWidth: Int) {
  val dataBits: Int = log2Ceil(dataWidth)
}

/**
  * @param ELEN 执行单元数据的位宽
  * @param VLEN 向量寄存器的宽度
  * @param lane lane的个数
  */
case class LaneParameters(ELEN: Int = 64, VLEN: Int = 128, lane: Int = 4) {
  // lane param
  val addRespWidth: Int = ELEN + 1
  val mulRespWidth: Int = ELEN * 2
  val elenBits: Int = log2Ceil(ELEN)
  // VLEN * lMulMax / sewMin
  val VLMax: Int = VLEN
  val groupSize: Int = VLMax / lane
  val groupSizeBits: Int = log2Ceil(groupSize)
  val idBits: Int = log2Ceil(lane)

  def datePathParam: DataPathParam = DataPathParam(ELEN)
  def lanePopCountParameter: LanePopCountParameter = LanePopCountParameter(ELEN, elenBits)
  def shifterParameter: LaneShifterParameter = LaneShifterParameter(ELEN, elenBits)
  def mulParam: LaneMulParam = LaneMulParam(ELEN)
  def indexParam: LaneIndexCalculatorParameter = LaneIndexCalculatorParameter(groupSizeBits, idBits)
}
