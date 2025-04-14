// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.rocketv.{TLB, TLBParameter}

object TLB extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = TLB
  type P = TLBParameter
  type M = TLBParameterMain

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
  case class TLBParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:                 Boolean,
    @arg(name = "xLen") xLen:                                   Int,
    @arg(name = "nSets") nSets:                                 Int,
    @arg(name = "nWays") nWays:                                 Int,
    @arg(name = "nSectors") nSectors:                           Int,
    @arg(name = "nSuperpageEntries") nSuperpageEntries:         Int,
    @arg(name = "asidBits") asidBits:                           Int,
    @arg(name = "pgLevels") pgLevels:                           Int,
    @arg(name = "usingHypervisor") usingHypervisor:             Boolean,
    @arg(name = "usingAtomics") usingAtomics:                   Boolean,
    @arg(name = "usingDataScratchpad") usingDataScratchpad:     Boolean,
    @arg(name = "usingAtomicsOnlyForIO") usingAtomicsOnlyForIO: Boolean,
    @arg(name = "usingVM") usingVM:                             Boolean,
    @arg(name = "usingAtomicsInCache") usingAtomicsInCache:     Boolean,
    @arg(name = "nPMPs") nPMPs:                                 Int,
    @arg(name = "paddrBits") paddrBits:                         Int,
    @arg(name = "legal") legal:                                 Seq[BitSet],
    @arg(name = "cacheable") cacheable:                         Seq[BitSet],
    @arg(name = "read") read:                                   Seq[BitSet],
    @arg(name = "write") write:                                 Seq[BitSet],
    @arg(name = "putPartial") putPartial:                       Seq[BitSet],
    @arg(name = "logic") logic:                                 Seq[BitSet],
    @arg(name = "arithmetic") arithmetic:                       Seq[BitSet],
    @arg(name = "exec") exec:                                   Seq[BitSet],
    @arg(name = "sideEffects") sideEffects:                     Seq[BitSet],
    @arg(name = "isITLB") isITLB: Boolean) {
    def convert: TLBParameter = TLBParameter(
      useAsyncReset,
      xLen,
      nSets,
      nWays,
      nSectors,
      nSuperpageEntries,
      asidBits,
      pgLevels,
      usingHypervisor,
      usingAtomics,
      usingDataScratchpad,
      usingAtomicsOnlyForIO,
      usingVM,
      usingAtomicsInCache,
      nPMPs,
      PMAChecker
        .PMACheckerParameterMain(
          paddrBits,
          legal,
          cacheable,
          read,
          write,
          putPartial,
          logic,
          arithmetic,
          exec,
          sideEffects
        )
        .convert,
      paddrBits,
      isITLB
    )
  }

  implicit def TLBParameterMainParser: ParserForClass[TLBParameterMain] = ParserForClass[TLBParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: M) =
    os.write.over(os.pwd / s"${className}.json", configImpl(parameter.convert))

  @main
  def design(@arg(name = "parameter") parameter: os.Path) = {
    val (firrtl, annos) = designImpl[D, P](os.read.stream(parameter))
    os.write.over(os.pwd / s"$className.fir", firrtl)
    os.write.over(os.pwd / s"$className.json", annos)
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
