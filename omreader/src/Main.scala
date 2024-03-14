// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreader

import mainargs._
import chisel3.panamalib.option._
import chisel3.panamaconverter.PanamaCIRCTConverter

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }

  @main
  def run(
    @arg(name = "mlirbc-file") mlirbc: os.Path,
  ) = {
    val cvt = PanamaCIRCTConverter.newWithMlirBc(os.read.bytes(mlirbc))

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
    val t1 = evaluator.instantiate("T1_1_Class", Seq(om.newBasePathEmpty))
    t1.foreachField((name, value) => println(s".$name => { ${value.toString} }"))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
