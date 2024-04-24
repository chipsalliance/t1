// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreader

import java.io.BufferedInputStream

import mainargs._
import chisel3.panamalib.option._
import chisel3.panamaom._
import chisel3.panamaconverter.PanamaCIRCTConverter
import org.chipsalliance.t1.omreaderlib

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }

  @main
  def run(
    @arg(name = "mlirbc-file") mlirbcFile: Option[os.Path],
    @arg(name = "class-name") className: String,
    @arg(name = "dump") dump: Flag,
    @arg(name = "eval") eval: Option[String],
  ) = {
    omreaderlib.Hello()

    val inputs = mlirbcFile match {
      case Some(path) => os.read.bytes(path)
      case None =>
        val stdin = new BufferedInputStream(System.in)
        Stream.continually(stdin.read).takeWhile(_ != -1).map(_.toByte).toArray
    }
    val cvt = PanamaCIRCTConverter.newWithMlirBc(inputs)

    val pm = cvt.passManager()
    assert(pm.populatePreprocessTransforms())
    assert(pm.populateCHIRRTLToLowFIRRTL())
    assert(pm.populateLowFIRRTLToHW())
    assert(pm.populateLowHWToSV())
    assert(pm.populateExportVerilog(_ => ()))
    assert(pm.populateFinalizeIR())
    assert(pm.run())

    val om = cvt.om()
    val evaluator = om.evaluator()
    val entry = evaluator.instantiate(className, Seq(om.newBasePathEmpty))

    if (dump.value) {
      entry.foreachField((name, value) => println(s".$name => $value"))
    }
    else if (eval.nonEmpty) {
      val result = SimpleInputEval(entry, eval.get)
      println(result)
    }
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}

object SimpleInputEval {
  def apply(entry: PanamaCIRCTOMEvaluatorValue, input: String): PanamaCIRCTOMEvaluatorValue = {
    input.split("\\.").foldLeft(entry) {
      case (obj, field) =>
        if (field.forall(_.isDigit)) {
          obj.asInstanceOf[PanamaCIRCTOMEvaluatorValueList].getElement(field.toLong)
        } else {
          obj.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject].field(field)
        }
    }
  }
}

