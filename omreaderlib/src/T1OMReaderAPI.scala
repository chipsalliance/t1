// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreaderlib

import upickle.default.{macroRW, ReadWriter}

object SRAM {
  implicit val rw: ReadWriter[SRAM] = macroRW
}

/** The SRAM Module to be replaced. */
case class SRAM(
  moduleName:      String,
  instanceName:    String,
  depth:           Int,
  width:           Int,
  read:            Int,
  write:           Int,
  readwrite:       Int,
  maskGranularity: Int)

object Retime {
  implicit val rw: ReadWriter[Retime] = macroRW
}

/** Module to be retimed. */
case class Retime(moduleName: String)

object InstructionAttributes {
  implicit val rw: ReadWriter[InstructionAttributes] = macroRW
}

case class InstructionAttributes(
  identifier:  String,
  description: String,
  value:       String)

object Instruction {
  implicit val rw: ReadWriter[Instruction] = macroRW
}

case class Instruction(
  instructionName: String,
  documentation:   String,
  bitPat:          String,
  attributes: Seq[InstructionAttributes]) {
  override def toString: String =
    s"${instructionName} -> ${attributes.map(a => s"${a.identifier}:${a.value}").mkString(",")}"
}

object Path {
  implicit val rw:        ReadWriter[Instruction] = macroRW
  def parse(str: String): Path                    =
    str match {
      case s"OMInstanceTarget:~${top}|${hier}>${local}" =>
        Path(
          top,
          hier
            .split("/")
            .map(i => {
              val s = i.split(":")
              (s.head, s.last)
            }),
          Some(local)
        )
      case s"OMInstanceTarget:~${top}|${hier}"          =>
        Path(
          top,
          hier
            .split("/")
            .map(i => {
              val s = i.split(":")
              (s.head, s.last)
            }),
          None
        )
    }
}

case class Path(top: String, hierarchy: Seq[(String, String)], local: Option[String]) {
  def module:       String = hierarchy.last._2
  def path:         String = hierarchy.map(_._1).mkString(".")
  def instanceName: String = hierarchy.last._1
}

/** Public Module under T1 should implement Modules below. */
trait T1OMReaderAPI extends OMReader {

  def vlen: Int

  def dlen: Int

  /** all supported RISC-V extensions */
  def extensions: Seq[String]

  /** the march needed by compiler */
  def march: String

  /** All SRAMs with its metadata */
  def sram: Seq[SRAM]

  /** All Modules that need to be retimed */
  def retime: Seq[Retime]

  /** All Instructions with all metadata */
  def instructions: Seq[Instruction]
}
