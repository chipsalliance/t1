package v
import chisel3.util.log2Ceil
/**
  * @param ELEN 执行单元数据的位宽
  * @param VLEN 向量寄存器的宽度
  * @param lane lane的个数
  */
case class VectorParameters(ELEN: Int = 32, VLEN: Int = 128, lane: Int = 4) {
  // lane param
  val addRespWidth: Int = ELEN + 1
  val mulRespWidth: Int = ELEN * 2
  val elenBits: Int = log2Ceil(ELEN)
  // VLEN * lMulMax / sewMin
  val VLMax: Int = VLEN
  val groupSize: Int = VLMax / lane
  val groupSizeBits: Int = log2Ceil(groupSize)

  def lanePopCountParameter: LanePopCountParameter = LanePopCountParameter(ELEN, elenBits)
  def laneFFOParameter: LaneFFOParameter = LaneFFOParameter(ELEN, ELEN)
}
