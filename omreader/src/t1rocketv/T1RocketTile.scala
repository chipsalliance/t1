// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreader.t1rocketv

import org.chipsalliance.t1.omreader.*

import me.jiuyang.omlib.{*, given}

import upickle.default._
import mainargs.*

class T1RocketTile(mlirbc: Array[Byte]) extends OMReader(mlirbc, "T1RocketTile_Class") with T1OMReader:
  private val tile = top.obj("om")
  private val t1 = tile.obj("t1")

  given Writer[T1RocketTileOM] = macroW
  case class T1RocketTileOM(
    vlen:         OMValue,
    dlen:         OMValue,
    instructions: Seq[OMValue],
    extensions:   Seq[OMValue],
    march:        OMValue,
    sram:         Seq[OMValue],
    retime:       Seq[OMValue])

  val vlen = t1.obj("vlen")

  val dlen = t1.obj("dlen")

  val instructions = t1.obj("decoder").obj("instructions").list.toSeq

  val extensions = t1.obj("extensions").list.toSeq

  val march = t1.obj("march")

  val sram = tile.flatten.flatMap(_.objOpt).flatMap(_.get("srams")).flatMap(_.list)

  val retime = tile.flatten.flatMap(_.objOpt).flatMap(_.get("retime"))

  override def json = write(T1RocketTileOM(vlen, dlen, instructions, extensions, march, sram, retime))
  
end T1RocketTile

object T1RocketTile:
  given pathReader: TokensReader.Simple[os.Path]:
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))

  @main
  def dump(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(T1RocketTile(os.read.bytes(mlirbcFile)).json)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)

end T1RocketTile
