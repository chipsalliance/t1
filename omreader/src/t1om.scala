// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.omreader

import mainargs._
import org.chipsalliance.t1.omreaderlib._

object t1om {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }
  @main
  def vlen(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(new T1OMReader(os.read.bytes(mlirbcFile)).vlen)

  @main
  def dlen(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(new T1OMReader(os.read.bytes(mlirbcFile)).dlen)

  @main
  def instructions(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(upickle.default.write(new T1OMReader(os.read.bytes(mlirbcFile)).instructions))

  @main
  def extensions(
    @arg(name = "mlirbc-file") mlirbcFile: os.Path
  ) =
    println(new T1OMReader(os.read.bytes(mlirbcFile)).extensions.mkString(""))

  @main
  def march(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(new T1OMReader(os.read.bytes(mlirbcFile)).march)

  @main
  def vrfs(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(upickle.default.write(new T1OMReader(os.read.bytes(mlirbcFile)).vrfs))

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
