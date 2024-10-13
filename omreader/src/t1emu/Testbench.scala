// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreader.t1emu

import mainargs._
import org.chipsalliance.t1.omreaderlib.t1emu.{TestBench => Lib}

object Testbench {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }
  @main
  def vlen(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(new Lib(os.read.bytes(mlirbcFile)).vlen)

  @main
  def dlen(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(new Lib(os.read.bytes(mlirbcFile)).dlen)

  @main
  def instructions(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    new Lib(os.read.bytes(mlirbcFile)).instructions.foreach(println)

  @main
  def extensions(
    @arg(name = "mlirbc-file") mlirbcFile: os.Path
  ) =
    println(new Lib(os.read.bytes(mlirbcFile)).extensions.mkString("_"))

  @main
  def march(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(new Lib(os.read.bytes(mlirbcFile)).march)

  @main
  def sram(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    new Lib(os.read.bytes(mlirbcFile)).sram.foreach(s => println(upickle.default.write(s)))

  @main
  def retime(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    new Lib(os.read.bytes(mlirbcFile)).retime.foreach(r => println(upickle.default.write(r)))

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
