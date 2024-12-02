// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreaderlib.t1rocketv

import chisel3.panamaom._
import org.chipsalliance.t1.omreaderlib.{Instruction, InstructionAttributes, Path, Retime, SRAM, T1OMReaderAPI}

/** OM API for [[org.chipsalliance.t1.rtl.T1OM]] */
class T1RocketTile(val mlirbc: Array[Byte]) extends T1OMReaderAPI {
  val top:          String                            = "T1RocketTile_Class"
  private val tile: PanamaCIRCTOMEvaluatorValueObject = entry
    .field("om")
    .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
  private val t1:   PanamaCIRCTOMEvaluatorValueObject = tile
    .field("t1")
    .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
  def vlen:         Int                               = t1.field("vlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt
  def dlen:         Int                               = t1.field("dlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt
  def instructions: Seq[Instruction]                  = {
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
  def extensions:   Seq[String]                       = {
    val extensions = t1.field("extensions").asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
    extensions.elements().map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString)
  }
  def march:        String                            = t1.field("march").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveString].toString
  def vrf:          Seq[SRAM]                         = t1
    .field("lanes")
    .asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
    .elements()
    .map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject].field("vrf"))
    .flatMap { vrf =>
      val srams = vrf
        .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
        .field("srams")
        .asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
      srams.elements().map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]).map { sram =>
        val hierarchy = Path.parse(sram.field("hierarchy").asInstanceOf[PanamaCIRCTOMEvaluatorValuePath].toString)
        SRAM(
          moduleName = hierarchy.module,
          instanceName = hierarchy.instanceName,
          depth = sram.field("depth").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
          width = sram.field("width").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
          read = sram.field("read").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
          write = sram.field("write").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
          readwrite = sram.field("readwrite").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
          maskGranularity = sram
            .field("maskGranularity")
            .asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger]
            .integer
            .toInt
        )
      }
    }
    .distinct

  def cache: Seq[SRAM] = Seq(
    tile
      .field("frontend")
      .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
      .field("icache")
      .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject],
    tile
      .field("hellaCache")
      .asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
  )
    .flatMap(
      _.field("srams")
        .asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
        .elements()
        .map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject])
        .map(sram => {
          val hierarchy = Path.parse(sram.field("hierarchy").asInstanceOf[PanamaCIRCTOMEvaluatorValuePath].toString)
          SRAM(
            moduleName = hierarchy.module,
            instanceName = hierarchy.instanceName,
            depth = sram.field("depth").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
            width = sram.field("width").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
            read = sram.field("read").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
            write = sram.field("write").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
            readwrite = sram.field("readwrite").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer.toInt,
            maskGranularity = sram
              .field("maskGranularity")
              .asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger]
              .integer
              .toInt
          )
        })
    )
    .distinct

  def sram = vrf ++ cache
  def vfu: Seq[Retime] = {
    t1
      .field("lanes")
      .asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
      .elements()
      .map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject].field("vfus"))
      .flatMap(
        _.asInstanceOf[PanamaCIRCTOMEvaluatorValueList]
          .elements()
          .map(_.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject])
          .filter(_.field("cycles").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer != 0)
      )
      .map(_.field("clock").asInstanceOf[PanamaCIRCTOMEvaluatorValuePath])
      .map(p => Retime(Path.parse(p.toString).module))
      .distinct
  }
  def retime = vfu
}
