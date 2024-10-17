// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreaderlib

import chisel3.panamaconverter.PanamaCIRCTConverter
import chisel3.panamaom._

trait OMReader {
  val mlirbc: Array[Byte]
  val top:    String
  protected lazy val cvt:       PanamaCIRCTConverter                = PanamaCIRCTConverter.newWithMlirBc(mlirbc)
  protected lazy val om:        PanamaCIRCTOM                       = cvt.om()
  protected lazy val evaluator: PanamaCIRCTOMEvaluator              = om.evaluator()
  protected lazy val basePath:  PanamaCIRCTOMEvaluatorValueBasePath = om.newBasePathEmpty()
  protected lazy val entry:     PanamaCIRCTOMEvaluatorValueObject   = evaluator.instantiate(top, Seq(basePath)).get
}
