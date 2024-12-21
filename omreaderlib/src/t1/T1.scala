// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreaderlib.t1

import chisel3.panamaom._
import org.chipsalliance.t1.omreaderlib.{Instruction, InstructionAttributes, Path, Retime, SRAM, T1OMReaderAPI}

/** OM API for [[org.chipsalliance.t1.rtl.T1OM]] */
class T1(val mlirbc: Array[Byte]) extends T1OMReaderAPI {
  val top: String = "T1_Class"

  private val t1:   PanamaCIRCTOMEvaluatorValueObject = entry("om").obj
  def vlen:         Int                               = t1("vlen").int.integer.toInt
  def dlen:         Int                               = t1("dlen").int.integer.toInt
  def extensions:   Seq[String]                       = t1("extensions").list.elements().map(_.string.toString)
  def march:        String                            = t1("march").string.toString
  def instructions: Seq[Instruction]                  = t1("decoder").obj("instructions").list.elements().map(_.obj).map(getInstruction)
  def sram:         Seq[SRAM]                         =
    t1("lanes").list.elements().map(_.obj("vrf").obj).flatMap(getSRAM)

  def permutation: Seq[Retime] = {
    val permutation = t1("permutation")
    val reduceUnit = permutation.obj("reduceUnit").obj
    val compressUnit = permutation.obj("compress").obj
    // TODO: need fieldOpt(name: String)
    val floatAdder =
      Option.when(reduceUnit.fieldNames().contains("floatAdder"))(reduceUnit("floatAdder").obj)

    (Seq(compressUnit) ++ floatAdder).flatMap(getRetime)
  }

  def vfus: Seq[Retime] =
    t1("lanes").list.elements().map(_.obj("vfus")).flatMap(_.list.elements().map(_.obj)).flatMap(getRetime)

  def retime = (vfus ++ permutation).distinct
}
