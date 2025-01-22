package utils.shifter

import chisel3._
import chisel3.util.{Mux1H, UIntToOH}

object barrel {

  /** A Barrel Shifter implementation for Vec type.
    *
    * @param inputs           input signal to be shifted, should be a [[Vec]] type.
    * @param shiftInput       input signal to indicate the shift number, encoded in UInt.
    * @param shiftType        [[ShiftType]] to indicate the type of shifter.
    * @param shiftGranularity how many bits will be resolved in each layer.
    *                         For a smaller `shiftGranularity`, latency will be high, but area is smaller.
    *                         For a large `shiftGranularity`, latency will be low, but area is higher.
    */
  def apply[T <: Data](inputs: Vec[T], shiftInput: UInt, shiftType: ShiftType, shiftGranularity: Int = 1): Vec[T] = {
    val elementType: T = chiselTypeOf(inputs.head)
    shiftInput
      .asBools
      .grouped(shiftGranularity)
      .map(VecInit(_).asUInt)
      .zipWithIndex
      .foldLeft(inputs) {
        case (prev, (shiftBits, layer)) =>
          Mux1H(
            UIntToOH(shiftBits),
            Seq.tabulate(1 << shiftBits.getWidth)(i => {
              // shift no more than inputs length
              // prev.drop will not warn about overflow!
              val layerShift: Int = (i * (1 << (layer * shiftGranularity))).min(prev.length)
              VecInit(shiftType match {
                case LeftRotate =>
                  prev.drop(layerShift) ++ prev.take(layerShift)
                case LeftShift =>
                  prev.drop(layerShift) ++ Seq.fill(layerShift)(0.U.asTypeOf(elementType))
                case RightRotate =>
                  prev.takeRight(layerShift) ++ prev.dropRight(layerShift)
                case RightShift =>
                  Seq.fill(layerShift)(0.U.asTypeOf(elementType)) ++ prev.dropRight(layerShift)
              })
            })
          )
      }
  }

  def leftShift[T <: Data](inputs: Vec[T], shift: UInt, layerSize: Int = 1): Vec[T] =
    apply(inputs, shift, LeftShift, layerSize)

  def rightShift[T <: Data](inputs: Vec[T], shift: UInt, layerSize: Int = 1): Vec[T] =
    apply(inputs, shift, RightShift, layerSize)

  def leftRotate[T <: Data](inputs: Vec[T], shift: UInt, layerSize: Int = 1): Vec[T] =
    apply(inputs, shift, LeftRotate, layerSize)

  def rightRotate[T <: Data](inputs: Vec[T], shift: UInt, layerSize: Int = 1): Vec[T] =
    apply(inputs, shift, RightRotate, layerSize)

}
