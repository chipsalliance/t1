// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import chisel3.stage.{IncludeUtilMetadata, UseSRAMBlackbox}
import mainargs._
import org.chipsalliance.rocketv.{BHTParameter, Frontend, FrontendParameter}

object Frontend extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = Frontend
  type P = FrontendParameter
  type M = FrontendParameterMain

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
  case class FrontendParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:                 Boolean,
    @arg(name = "clockGate") clockGate:                         Boolean,
    @arg(name = "xLen") xLen:                                   Int,
    @arg(name = "usingAtomics") usingAtomics:                   Boolean,
    @arg(name = "usingDataScratchpad") usingDataScratchpad:     Boolean,
    @arg(name = "usingVM") usingVM:                             Boolean,
    @arg(name = "usingCompressed") usingCompressed:             Boolean,
    @arg(name = "usingBTB") usingBTB:                           Boolean,
    @arg(name = "itlbNSets") itlbNSets:                         Int,
    @arg(name = "itlbNWays") itlbNWays:                         Int,
    @arg(name = "itlbNSectors") itlbNSectors:                   Int,
    @arg(name = "itlbNSuperpageEntries") itlbNSuperpageEntries: Int,
    @arg(name = "blockBytes") blockBytes:                       Int,
    @arg(name = "iCacheNSets") iCacheNSets:                     Int,
    @arg(name = "iCacheNWays") iCacheNWays:                     Int,
    @arg(name = "iCachePrefetch") iCachePrefetch:               Boolean,
    @arg(name = "btbEntries") btbEntries:                       Int,
    @arg(name = "btbNMatchBits") btbNMatchBits:                 Int,
    @arg(name = "btbUpdatesOutOfOrder") btbUpdatesOutOfOrder:   Boolean,
    @arg(name = "nPages") nPages:                               Int,
    @arg(name = "nRAS") nRAS:                                   Int,
    @arg(name = "nPMPs") nPMPs:                                 Int,
    @arg(name = "paddrBits") paddrBits:                         Int,
    @arg(name = "pgLevels") pgLevels:                           Int,
    @arg(name = "asidBits") asidBits:                           Int,
    @arg(name = "bhtNEntries") bhtNEntries:                     Option[Int],
    @arg(name = "bhtCounterLength") bhtCounterLength:           Option[Int],
    @arg(name = "bhtHistoryLength") bhtHistoryLength:           Option[Int],
    @arg(name = "bhtHistoryBits") bhtHistoryBits:               Option[Int],
    @arg(name = "legal") legal:                                 Seq[BitSet],
    @arg(name = "cacheable") cacheable:                         Seq[BitSet],
    @arg(name = "read") read:                                   Seq[BitSet],
    @arg(name = "write") write:                                 Seq[BitSet],
    @arg(name = "putPartial") putPartial:                       Seq[BitSet],
    @arg(name = "logic") logic:                                 Seq[BitSet],
    @arg(name = "arithmetic") arithmetic:                       Seq[BitSet],
    @arg(name = "exec") exec:                                   Seq[BitSet],
    @arg(name = "sideEffects") sideEffects: Seq[BitSet]) {
    def convert: FrontendParameter = FrontendParameter(
      useAsyncReset:         Boolean,
      clockGate:             Boolean,
      xLen:                  Int,
      usingAtomics:          Boolean,
      usingDataScratchpad:   Boolean,
      usingVM:               Boolean,
      usingCompressed:       Boolean,
      usingBTB:              Boolean,
      itlbNSets:             Int,
      itlbNWays:             Int,
      itlbNSectors:          Int,
      itlbNSuperpageEntries: Int,
      blockBytes:            Int,
      iCacheNSets:           Int,
      iCacheNWays:           Int,
      iCachePrefetch:        Boolean,
      btbEntries:            Int,
      btbNMatchBits:         Int,
      btbUpdatesOutOfOrder:  Boolean,
      nPages:                Int,
      nRAS:                  Int,
      nPMPs:                 Int,
      paddrBits:             Int,
      pgLevels:              Int,
      asidBits:              Int,
      bhtNEntries
        .lazyZip(bhtCounterLength)
        .lazyZip(bhtHistoryLength)
        .lazyZip(bhtHistoryBits)
        .map { case (bhtNEntries, bhtCounterLength, bhtHistoryLength, bhtHistoryBits) =>
          BHTParameter(bhtNEntries, bhtCounterLength, bhtHistoryLength, bhtHistoryBits)
        }
        .headOption,
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

  implicit def FrontendParameterMainParser: ParserForClass[FrontendParameterMain] =
    ParserForClass[FrontendParameterMain]

  override def additionalAnnotations = Seq(IncludeUtilMetadata, UseSRAMBlackbox)

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
