// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreaderlib.t1rocketv

import chisel3.panamaom._
import org.chipsalliance.t1.omreaderlib.{Instruction, InstructionAttributes, Retime, SRAM, T1OMReaderAPI}

/** OM API for [[org.chipsalliance.t1.rtl.T1OM]] */
class Testbench(val mlirbc: Array[Byte]) extends T1OMReaderAPI {
  val top:             String                            = "Testbench_Class"
  private val t1:      PanamaCIRCTOMEvaluatorValueObject = entry
    .field("om")
    .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    .field("t1")
    .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
  def vlen:            Int                               = t1.field("vlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt
  def dlen:            Int                               = t1.field("dlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt
  def extensions:      Seq[String]                       = {
    val extensions = t1.field("extensions").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
    extensions.elements().map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString)
  }
  def march:           String                            = t1.field("march").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
  def instructions:    Seq[Instruction]                  = {
    val decoder      = t1.field("decoder").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    val instructions = decoder.field("instructions").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
    instructions
      .elements()
      .map(instruction => {
        val instr      = instruction.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
        val attributes = instr.field("attributes").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
        instr.field("attributes").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
        Instruction(
          instr.field("instructionName").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString,
          instr.field("documentation").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString,
          instr.field("bitPat").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString,
          attributes
            .elements()
            .map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject])
            .map { attr =>
              InstructionAttributes(
                attr.field("identifier").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString,
                attr.field("description").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString,
                attr.field("value").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
              )
            }
        )
      })
  }
  override def sram:   Seq[SRAM]                         = Nil
  override def retime: Seq[Retime]                       = Nil
}
