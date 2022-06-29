package v
/**
  * @param ELEN 执行单元数据的位宽
  * @param VLEN 向量寄存器的宽度
  * @param lane lane的个数
  */
case class VectorParameters(ELEN: Int = 32, VLEN: Int = 128, lane: Int = 4) {
}
