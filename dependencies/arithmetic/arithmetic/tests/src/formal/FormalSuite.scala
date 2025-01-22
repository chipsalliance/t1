package formal

import chisel3.Module
import chiseltest.HasTestName
import chiseltest.formal.Formal
import firrtl.AnnotationSeq
import utest._

trait FormalSuite extends TestSuite {
  def verify[T <: Module](dutGen: => T, annos: AnnotationSeq)(implicit testPath: utest.framework.TestPath) =
    new Formal with HasTestName {
      def getTestName: String = s"${testPath.value.reduce(_ + _)}"
    }.verify(dutGen, annos)
}
