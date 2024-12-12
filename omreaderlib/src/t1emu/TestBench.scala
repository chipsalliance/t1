// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreaderlib.t1emu

import chisel3.panamaom._
import org.chipsalliance.t1.omreaderlib.{Instruction, InstructionAttributes, Retime, SRAM, T1OMReaderAPI}

/** OM API for [[org.chipsalliance.t1.rtl.T1OM]] */
class TestBench(val mlirbc: Array[Byte]) extends T1OMReaderAPI {
  val top:          String                            = "TestBench_Class"
  private val t1:   PanamaCIRCTOMEvaluatorValueObject = entry("om").obj("t1").obj
  def vlen:         Int                               = t1("vlen").int.integer.toInt
  def dlen:         Int                               = t1("dlen").int.integer.toInt
  def extensions:   Seq[String]                       = t1("extensions").list.elements().map(_.string.toString)
  def march:        String                            = t1("march").string.toString
  def instructions: Seq[Instruction]                  = t1("decoder").obj("instructions").list.elements().map(_.obj).map(getInstruction)
  def sram:         Seq[SRAM]                         = Nil
  def retime:       Seq[Retime]                       = Nil
}
