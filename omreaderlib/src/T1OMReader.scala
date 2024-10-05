// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreaderlib

import chisel3.panamaom._
import ujson.Obj

/** OM API for [[org.chipsalliance.t1.rtl.T1OM]] */
class T1OMReader(val mlirbc: Array[Byte]) extends OMReader {
  val top:          String                            = "T1_Class"
  private val t1:   PanamaCIRCTOMEvaluatorValueObject = entry.field("om").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
  def vlen:         Int                               = t1.field("vlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt
  def dlen:         Int                               = t1.field("dlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt
  def instructions: Seq[Obj]                          = {
    val decoder      = t1.field("decoder").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    val instructions = decoder.field("instructions").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]

    instructions
      .elements()
      .map(instruction => {
        val instr      = instruction.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
        val attributes = instr.field("attributes").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]

        ujson.Obj(
          "attributes" -> attributes
            .elements()
            .map(attribute => {
              val attr        = attribute.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
              val description =
                attr.field("description").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
              val identifier  =
                attr.field("identifier").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
              val value       = attr.field("value").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
              ujson.Obj(
                "description" -> description,
                "identifier"  -> identifier,
                "value"       -> value
              )
            })
        )
      })
  }
  def extensions:   Seq[String]                       = {
    val extensions = t1.field("extensions").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
    extensions.elements().map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString)
  }
  def vrfs:         Seq[Obj]                          = {
    t1.field("lanes")
      .asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
      .elements()
      .map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject].field("vrf"))
      .flatMap { vrf =>
        val srams = vrf.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject].field("srams").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
        srams.elements().map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]).map { sram =>
          ujson.Obj(
          "depth" -> sram.field("depth").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          "width" -> sram.field("width").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          // "masked" -> sram.field("masked").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          "read" -> sram.field("read").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          "write" -> sram.field("write").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          "readwrite" -> sram.field("readwrite").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          "maskGranularity" -> sram.field("maskGranularity").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer,
          "hierarchy" -> sram.field("hierarchy").asInstanceOf[PanamaCIRCTOMEvaluatorValuePath].toString
          )
        }
      }
  }
  def march:        String                            = t1.field("march").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
}
