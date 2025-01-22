package addition

import addition.csa.common.{CSACompressor2_2, CSACompressor3_2, CSACompressor5_3}
import chisel3._

package object csa {
  def apply[T <: Data](csa: CSACompressor, width: Option[Int] = None)(in: Vec[T]) = {
    val m = Module(new CarrySaveAdder(csa, width.getOrElse(in.flatMap(_.widthOption).max)))
    m.in := VecInit(in.map(_.asUInt))
    m.out
  }

  def c22[T <: Data](in: Vec[T]): Vec[UInt] = apply(CSACompressor2_2)(in)
  def c32[T <: Data](in: Vec[T]): Vec[UInt] = apply(CSACompressor3_2)(in)
  def c53[T <: Data](in: Vec[T]): Vec[UInt] = apply(CSACompressor5_3)(in)
}
