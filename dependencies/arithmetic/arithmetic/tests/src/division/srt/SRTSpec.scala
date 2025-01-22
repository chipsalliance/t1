package division.srt

import utest._
import chisel3._
import utils.extend
object SRTSpec extends TestSuite{
  override def tests: Tests = Tests {
    test("SRT should draw PD") {
      val srt = SRTTable(8,5,5,5)
//      println(srt.tables)
//      println(srt.tablesToQDS)
      srt.dumpGraph(srt.pd, os.root / "tmp" / "srt8-5-5-5.png")
    }
  }
}
