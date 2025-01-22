package crypto.modmul

import chisel3._
import chiseltest._
import utest._

object BarrettSpec extends TestSuite with ChiselUtestTester {
  def tests: Tests = Tests {
    test("Barrett should pass") {
      val u = new Utility()
      val length = scala.util.Random.nextInt(30) + 2 // avoid 0 and 1
      val p = u.randPrime(length)
      var a = scala.util.Random.nextInt(p)
      var b = scala.util.Random.nextInt(p)
      val res = BigInt(a) * BigInt(b) % BigInt(p)
      var addPipe = scala.util.Random.nextInt(10) + 1
      var mulPipe = scala.util.Random.nextInt(10) + 1

      testCircuit(new Barrett(p, addPipe, mulPipe), Seq(chiseltest.internal.NoThreadingAnnotation, chiseltest.simulator.WriteVcdAnnotation)){dut: Barrett =>
        dut.clock.setTimeout(0)
        dut.input.bits.a.poke(a.U)
        dut.input.bits.b.poke(b.U)
        dut.input.valid.poke(true.B)
        var flag = false
        for(a <- 1 to 100) {
          dut.clock.step()
          if(dut.z.valid.peek().litValue == 1) {
            flag = true
            utest.assert(dut.z.bits.peek().litValue == res)
          }
        }
        utest.assert(flag)
      }
    }
  }
}
