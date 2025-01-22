import chisel3._
import chisel3.util._
import chiseltest.formal._
import formal.FormalSuite
import utest.{test, Tests}
import utils.shifter.barrel

class VecLeftRotater[T <: Data](len: Int, gen: T, layerSize: Int = 1) extends Module {
  require(len > 1)
  val inputs:  Vec[T] = IO(Input(Vec(len, gen)))
  val shift:   UInt = IO(Input(UInt((log2Ceil(len)).W))) // rotate at most len - 1
  val outputs: Vec[T] = IO(Output(Vec(len, gen)))

  outputs := barrel.leftRotate(inputs, shift, layerSize)

  // Note: see note in VecLeftShifter
  assert(inputs.asUInt().rotateRight(shift * gen.getWidth.U) === outputs.asUInt())
}

class VecLeftShifter[T <: Data](len: Int, gen: T, layerSize: Int = 1) extends Module {
  require(len > 1)
  val inputs:  Vec[T] = IO(Input(Vec(len, gen)))
  val shift:   UInt = IO(Input(UInt((log2Ceil(len)).W))) // shift at most len - 1
  val outputs: Vec[T] = IO(Output(Vec(len, gen)))

  outputs := barrel.leftShift(inputs, shift, layerSize)

  // Note: left shift for Vec is right shift for UInt
  assert((inputs.asUInt() >> (shift * gen.getWidth.U)).pad(inputs.getWidth) === outputs.asUInt())
}

class VecRightRotater[T <: Data](len: Int, gen: T, layerSize: Int = 1) extends Module {
  require(len > 1)
  val inputs:  Vec[T] = IO(Input(Vec(len, gen)))
  val shift:   UInt = IO(Input(UInt((log2Ceil(len)).W))) // rotate at most len - 1
  val outputs: Vec[T] = IO(Output(Vec(len, gen)))

  outputs := barrel.rightRotate(inputs, shift, layerSize)

  // Note: see note in VecRightShifter
  assert(inputs.asUInt().rotateLeft(shift * gen.getWidth.U) === outputs.asUInt())
}

class VecRightShifter[T <: Data](len: Int, gen: T, layerSize: Int = 1) extends Module {
  require(len > 1)
  val inputs:  Vec[T] = IO(Input(Vec(len, gen)))
  val shift:   UInt = IO(Input(UInt((log2Ceil(len)).W))) // shift at most len - 1
  val outputs: Vec[T] = IO(Output(Vec(len, gen)))

  outputs := barrel.rightShift(inputs, shift, layerSize)

  // Note: right shift for Vec is left shift for UInt
  // on << shift width: UInt(this.width + that)
  assert(((inputs.asUInt() << (shift * gen.getWidth.U))(inputs.getWidth - 1, 0)) === outputs.asUInt())
}

object VecShifterTester extends FormalSuite {
  def tests: Tests = Tests {
    test("vec left shifter") {
      Seq(2, 3, 13).foreach(len => {
        Seq(1, 2, 7).foreach(width => {
          Seq(1, 2, 3).foreach(layerSize => {
            println(s"$len, $width, $layerSize")
            verify(new VecLeftShifter(len, UInt(width.W), layerSize), Seq(BoundedCheck(1)))
          })
        })
      })
    }
    test("vec left rotater") {
      Seq(2, 3, 13).foreach(len => {
        Seq(1, 2, 7).foreach(width => {
          Seq(1, 2, 3).foreach(layerSize => {
            println(s"$len, $width, $layerSize")
            verify(new VecLeftRotater(len, UInt(width.W), layerSize), Seq(BoundedCheck(1)))
          })
        })
      })
    }
    test("vec right shifter") {
      Seq(2, 3, 13).foreach(len => {
        Seq(1, 2, 7).foreach(width => {
          Seq(1, 2, 3).foreach(layerSize => {
            println(s"$len, $width, $layerSize")
            verify(new VecRightShifter(len, UInt(width.W), layerSize), Seq(BoundedCheck(1)))
          })
        })
      })
    }
    test("vec right rotater") {
      Seq(2, 3, 13).foreach(len => {
        Seq(1, 2, 7).foreach(width => {
          Seq(1, 2, 3).foreach(layerSize => {
            println(s"$len, $width, $layerSize")
            verify(new VecRightRotater(len, UInt(width.W), layerSize), Seq(BoundedCheck(1)))
          })
        })
      })
    }
  }
}
