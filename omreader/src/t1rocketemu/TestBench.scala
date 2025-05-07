// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreader.t1rocketemu

import org.chipsalliance.t1.omreader.*

import me.jiuyang.omlib.{*, given}

import upickle.default._
import mainargs.*

class TestBench(mlirbc: Array[Byte]) extends OMReader(mlirbc, "TestBench_Class") with T1OMReader:
  private val tile = top.obj("om").obj("t1RocketTile")
  private val t1   = tile.obj("t1")

  given Writer[TestBenchOM] = macroW
  case class TestBenchOM(
    vlen:         OMValue,
    dlen:         OMValue,
    elen:         OMValue,
    laneScale:    OMValue,
    instructions: Seq[OMValue],
    extensions:   Seq[OMValue],
    march:        OMValue,
    sram:         Seq[OMValue],
    retime:       Seq[OMValue])

  val vlen = t1.obj("vlen")

  val dlen = t1.obj("dlen")

  val elen = t1.obj("elen")

  val laneScale = t1.obj("laneScale")

  val instructions = t1.obj("decoder").obj("instructions").list.toSeq

  val extensions = t1.obj("extensions").list.toSeq

  val march = t1.obj("march")

  val sram = tile.flatten.flatMap(_.objOpt).flatMap(_.get("srams")).flatMap(_.list)

  val retime = tile.flatten.flatMap(_.objOpt).flatMap(_.get("retime"))

  override def json = write(TestBenchOM(vlen, dlen, elen, laneScale, instructions, extensions, march, sram, retime))

end TestBench

object TestBench:
  given pathReader: TokensReader.Simple[os.Path]:
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))

  @main
  def dump(@arg(name = "mlirbc-file") mlirbcFile: os.Path) =
    println(TestBench(os.read.bytes(mlirbcFile)).json)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)

end TestBench
