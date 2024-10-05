// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreader

import java.io.BufferedInputStream
import mainargs._
import chisel3.panamaom._
import org.chipsalliance.t1.omreaderlib._

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }

  @main
  def run(
    @arg(name = "mlirbc-file") mlirbcFile:   Option[os.Path],
    @arg(name = "dump-methods") dumpMethods: Flag,
    @arg(name = "eval") eval:                Option[String]
  ) = {
    val t1Reader = (mlirbcFile match {
      case Some(path) => OMReader.fromFile(path)
      case None       =>
        val stdin = new BufferedInputStream(System.in)
        val bytes = Stream.continually(stdin.read).takeWhile(_ != -1).map(_.toByte).toArray
        OMReader.fromBytes(bytes)
    }).t1Reader

    if (eval.nonEmpty) {
      println(SimpleInputEval(t1Reader.entry, eval.get))
    } else if (dumpMethods.value) {
      t1Reader.dumpMethods()
    } else {
      t1Reader.dumpAll()
    }
  }

  @main
  def vlen(@arg(name = "mlirbc-file") mlirbcFile: os.Path) = {
    println(simplyGetT1Reader(mlirbcFile).vlen)
  }

  @main
  def dlen(@arg(name = "mlirbc-file") mlirbcFile: os.Path) = {
    println(simplyGetT1Reader(mlirbcFile).dlen)
  }

  @main
  def march(@arg(name = "mlirbc-file") mlirbcFile: os.Path) = {
    println(simplyGetT1Reader(mlirbcFile).march)
  }

  @main
  def extensionsJson(@arg(name = "mlirbc-file") mlirbcFile: os.Path) = {
    println(simplyGetT1Reader(mlirbcFile).extensionsJson)
  }

  @main
  def decoderInstructionsJson(@arg(name = "mlirbc-file") mlirbcFile: os.Path) = {
    println(simplyGetT1Reader(mlirbcFile).decoderInstructionsJson)
  }

  @main
  def decoderInstructionsJsonPretty(@arg(name = "mlirbc-file") mlirbcFile: os.Path) = {
    println(simplyGetT1Reader(mlirbcFile).decoderInstructionsJsonPretty)
  }

  def simplyGetT1Reader(mlirbcFile: os.Path) = OMReader.fromFile(mlirbcFile).t1Reader

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}

object SimpleInputEval {
  def apply(entry: PanamaCIRCTOMEvaluatorValue, input: String): PanamaCIRCTOMEvaluatorValue = {
    input.split("\\.").foldLeft(entry) { case (obj, field) =>
      if (field.forall(_.isDigit)) {
        obj.asInstanceOf[PanamaCIRCTOMEvaluatorValueList].getElement(field.toLong)
      } else {
        obj.asInstanceOf[PanamaCIRCTOMEvaluatorValueObject].field(field)
      }
    }
  }
}
