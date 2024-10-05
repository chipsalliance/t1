// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.rocketv.{PMAChecker, PMACheckerParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object PMAChecker extends Elaborator {

  implicit object BitSetRead extends TokensReader.Simple[BitSet] {
    def shortName               = "bitset"
    def read(strs: Seq[String]) = {
      Right(
        strs.head
          .split(",")
          .map { opt =>
            if (opt.contains("-")) {
              val range = opt.split("-")
              require(range.size == 2)
              val from  = BigInt(range.head, 16)
              val to    = BigInt(range.last, 16) + 1
              BitSet.fromRange(from, to - from, range.head.length * 4)
            } else if (opt.contains("+")) {
              val range  = opt.split("\\+")
              require(range.size == 2)
              val from   = BigInt(range.head, 16)
              val length = BigInt(range.last, 16)
              BitSet.fromRange(from, length, range.head.length * 4)
            } else {
              BitPat(s"b$opt")
            }
          }
          .reduce(_.union(_))
      )
    }
  }
  @main
  case class PMACheckerParameterMain(
    paddrBits:  Int,
    legal:      Seq[BitSet],
    cacheable:  Seq[BitSet],
    read:       Seq[BitSet],
    write:      Seq[BitSet],
    putPartial: Seq[BitSet],
    logic:      Seq[BitSet],
    arithmetic: Seq[BitSet],
    exec:       Seq[BitSet],
    sideEffects: Seq[BitSet]) {
    def convert: PMACheckerParameter = PMACheckerParameter(
      paddrBits,
      legal.foldLeft(BitSet.empty)(_.union(_)),
      cacheable.foldLeft(BitSet.empty)(_.union(_)),
      read.foldLeft(BitSet.empty)(_.union(_)),
      write.foldLeft(BitSet.empty)(_.union(_)),
      putPartial.foldLeft(BitSet.empty)(_.union(_)),
      logic.foldLeft(BitSet.empty)(_.union(_)),
      arithmetic.foldLeft(BitSet.empty)(_.union(_)),
      exec.foldLeft(BitSet.empty)(_.union(_)),
      sideEffects.foldLeft(BitSet.empty)(_.union(_))
    )
  }

  implicit def PMACheckerParameterMainParser: ParserForClass[PMACheckerParameterMain] =
    ParserForClass[PMACheckerParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: PMACheckerParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[PMAChecker, PMACheckerParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
