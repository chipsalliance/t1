// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreaderlib.t1rocketv

import chisel3.panamaom._
import org.chipsalliance.t1.omreaderlib.{Instruction, InstructionAttributes, Path, Retime, SRAM, T1OMReaderAPI}

/** OM API for [[org.chipsalliance.t1.rtl.T1OM]] */
class T1RocketTile(val mlirbc: Array[Byte]) extends T1OMReaderAPI {
  val top:          String                            = "T1RocketTile_Class"
  private val tile: PanamaCIRCTOMEvaluatorValueObject = entry("om").obj
  private val t1:   PanamaCIRCTOMEvaluatorValueObject = tile("t1").obj
  def vlen:         Int                               = t1("vlen").int.integer.toInt
  def dlen:         Int                               = t1("dlen").int.integer.toInt
  def instructions: Seq[Instruction]                  = t1("decoder").obj("instructions").list.elements().map(_.obj).map(getInstruction)
  def extensions:   Seq[String]                       = t1("extensions").list.elements().map(_.string.toString)
  def march:        String                            = t1("march").string.toString
  def vrf:          Seq[SRAM]                         =
    t1("lanes").list.elements().map(_.obj("vrf").obj).flatMap(getSRAM)
  def cache:        Seq[SRAM]                         =
    Seq(tile("frontend").obj("icache").obj, tile("hellaCache").obj).flatMap(getSRAM)
  def vfu:          Seq[Retime]                       =
    t1("lanes").list.elements().map(_.obj("vfus")).flatMap(_.list.elements().map(_.obj)).flatMap(getRetime)
  def floatAdder = {
    val reduceUnit = t1("permutatuon").obj("reduceUnit").obj
    // TODO: need fieldOpt(name: String)
    Option.when(reduceUnit.fieldNames().contains("floatAdder"))(reduceUnit("floatAdder").obj).flatMap(getRetime)
  }

  def retime = (vfu ++ floatAdder).distinct
  def sram   = vrf ++ cache
}
