// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import chisel3.stage.IncludeUtilMetadata
import mainargs._
import org.chipsalliance.rocketv.{BHTParameter, RocketTile, RocketTileParameter}

//  --useAsyncReset true --clockGate true --instructionSets rv32_i --priv m --hartIdLen 4 --useBPWatch false --mcontextWidth 0 --scontextWidth 0 --asidBits 0 --resetVectorBits 32 --nBreakpoints 0 --dtlbNWays 0 --dtlbNSets 0 --itlbNSets 0 --itlbNWays 0 --itlbNSectors 0 --itlbNSuperpageEntries 0 --nPTECacheEntries 0 --nL2TLBWays 0 --nL2TLBEntries 0 --paddrBits 32 --cacheBlockBytes 32 --nPMPs 8 --legal 00000000-ffffffff --cacheable 80000000-ffffffff --read 00000000-ffffffff --write 00000000-ffffffff --putPartial 00000000-ffffffff --logic 0 --arithmetic 0 --exec 80000000-ffffffff --sideEffects 00000000-3fffffff --btbEntries 28 --btbNMatchBits 14 --btbUpdatesOutOfOrder false --nPages 6 --nRAS 6 --bhtNEntries 512 --bhtCounterLength 1 --bhtHistoryLength 8 --bhtHistoryBits 3 --mulDivLatency 2 --divUnroll 1 --divEarlyOut false --divEarlyOutGranularity 0 --mulUnroll 1 --mulEarlyOut false --sfmaLatency 3 --dfmaLatency 3 --divSqrt true --flushOnFenceI true --fastLoadByte false --fastLoadWord false --dcacheNSets 64 --dcacheNWays 4 --dcacheRowBits 32 --maxUncachedInFlight 1 --separateUncachedResp false --iCacheNSets 64 --iCacheNWays 4 --iCachePrefetch false

object RocketTile extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = RocketTile
  type P = RocketTileParameter
  type M = RocketTileParameterMain

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
  case class RocketTileParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:                   Boolean,
    @arg(name = "clockGate") clockGate:                           Boolean,
    @arg(name = "instructionSets") instructionSets:               Set[String],
    @arg(name = "priv") priv:                                     String,
    @arg(name = "hartIdLen") hartIdLen:                           Int,
    @arg(name = "useBPWatch") useBPWatch:                         Boolean,
    @arg(name = "mcontextWidth") mcontextWidth:                   Int,
    @arg(name = "scontextWidth") scontextWidth:                   Int,
    @arg(name = "asidBits") asidBits:                             Int,
    @arg(name = "resetVectorBits") resetVectorBits:               Int,
    @arg(name = "nBreakpoints") nBreakpoints:                     Int,
    @arg(name = "dtlbNWays") dtlbNWays:                           Int,
    @arg(name = "dtlbNSets") dtlbNSets:                           Int,
    @arg(name = "itlbNSets") itlbNSets:                           Int,
    @arg(name = "itlbNWays") itlbNWays:                           Int,
    @arg(name = "itlbNSectors") itlbNSectors:                     Int,
    @arg(name = "itlbNSuperpageEntries") itlbNSuperpageEntries:   Int,
    @arg(name = "nPTECacheEntries") nPTECacheEntries:             Int,
    @arg(name = "nL2TLBWays") nL2TLBWays:                         Int,
    @arg(name = "nL2TLBEntries") nL2TLBEntries:                   Int,
    @arg(name = "paddrBits") paddrBits:                           Int,
    @arg(name = "cacheBlockBytes") cacheBlockBytes:               Int,
    @arg(name = "nPMPs") nPMPs:                                   Int,
    @arg(name = "legal") legal:                                   BitSet,
    @arg(name = "cacheable") cacheable:                           BitSet,
    @arg(name = "read") read:                                     BitSet,
    @arg(name = "write") write:                                   BitSet,
    @arg(name = "putPartial") putPartial:                         BitSet,
    @arg(name = "logic") logic:                                   BitSet,
    @arg(name = "arithmetic") arithmetic:                         BitSet,
    @arg(name = "exec") exec:                                     BitSet,
    @arg(name = "sideEffects") sideEffects:                       BitSet,
    @arg(name = "btbEntries") btbEntries:                         Int,
    @arg(name = "btbNMatchBits") btbNMatchBits:                   Int,
    @arg(name = "btbUpdatesOutOfOrder") btbUpdatesOutOfOrder:     Boolean,
    @arg(name = "nPages") nPages:                                 Int,
    @arg(name = "nRAS") nRAS:                                     Int,
    @arg(name = "bhtNEntries") bhtNEntries:                       Option[Int],
    @arg(name = "bhtCounterLength") bhtCounterLength:             Option[Int],
    @arg(name = "bhtHistoryLength") bhtHistoryLength:             Option[Int],
    @arg(name = "bhtHistoryBits") bhtHistoryBits:                 Option[Int],
    @arg(name = "mulDivLatency") mulDivLatency:                   Int,
    @arg(name = "divUnroll") divUnroll:                           Int,
    @arg(name = "divEarlyOut") divEarlyOut:                       Boolean,
    @arg(name = "divEarlyOutGranularity") divEarlyOutGranularity: Int,
    @arg(name = "mulUnroll") mulUnroll:                           Int,
    @arg(name = "mulEarlyOut") mulEarlyOut:                       Boolean,
    @arg(name = "sfmaLatency") sfmaLatency:                       Int,
    @arg(name = "dfmaLatency") dfmaLatency:                       Int,
    @arg(name = "divSqrt") divSqrt:                               Boolean,
    @arg(name = "flushOnFenceI") flushOnFenceI:                   Boolean,
    @arg(name = "fastLoadByte") fastLoadByte:                     Boolean,
    @arg(name = "fastLoadWord") fastLoadWord:                     Boolean,
    @arg(name = "dcacheNSets") dcacheNSets:                       Int,
    @arg(name = "dcacheNWays") dcacheNWays:                       Int,
    @arg(name = "dcacheRowBits") dcacheRowBits:                   Int,
    @arg(name = "maxUncachedInFlight") maxUncachedInFlight:       Int,
    @arg(name = "separateUncachedResp") separateUncachedResp:     Boolean,
    @arg(name = "iCacheNSets") iCacheNSets:                       Int,
    @arg(name = "iCacheNWays") iCacheNWays:                       Int,
    @arg(name = "iCachePrefetch") iCachePrefetch: Boolean) {
    def convert: RocketTileParameter = RocketTileParameter(
      useAsyncReset:          Boolean,
      clockGate:              Boolean,
      instructionSets:        Set[String],
      priv:                   String,
      hartIdLen:              Int,
      useBPWatch:             Boolean,
      mcontextWidth:          Int,
      scontextWidth:          Int,
      asidBits:               Int,
      resetVectorBits:        Int,
      nBreakpoints:           Int,
      dtlbNWays:              Int,
      dtlbNSets:              Int,
      itlbNSets:              Int,
      itlbNWays:              Int,
      itlbNSectors:           Int,
      itlbNSuperpageEntries:  Int,
      nPTECacheEntries:       Int,
      nL2TLBWays:             Int,
      nL2TLBEntries:          Int,
      paddrBits:              Int,
      cacheBlockBytes:        Int,
      nPMPs:                  Int,
      legal:                  BitSet,
      cacheable:              BitSet,
      read:                   BitSet,
      write:                  BitSet,
      putPartial:             BitSet,
      logic:                  BitSet,
      arithmetic:             BitSet,
      exec:                   BitSet,
      sideEffects:            BitSet,
      btbEntries:             Int,
      btbNMatchBits:          Int,
      btbUpdatesOutOfOrder:   Boolean,
      nPages:                 Int,
      nRAS:                   Int,
      bhtNEntries
        .lazyZip(bhtCounterLength)
        .lazyZip(bhtHistoryLength)
        .lazyZip(bhtHistoryBits)
        .map {
          case (
                bhtNEntries,
                bhtCounterLength,
                bhtHistoryLength,
                bhtHistoryBits
              ) =>
            BHTParameter(bhtNEntries, bhtCounterLength, bhtHistoryLength, bhtHistoryBits)
        }
        .headOption:          Option[BHTParameter],
      mulDivLatency:          Int,
      divUnroll:              Int,
      divEarlyOut:            Boolean,
      divEarlyOutGranularity: Int,
      mulUnroll:              Int,
      mulEarlyOut:            Boolean,
      sfmaLatency:            Int,
      dfmaLatency:            Int,
      divSqrt:                Boolean,
      flushOnFenceI:          Boolean,
      fastLoadByte:           Boolean,
      fastLoadWord:           Boolean,
      dcacheNSets:            Int,
      dcacheNWays:            Int,
      dcacheRowBits:          Int,
      maxUncachedInFlight:    Int,
      separateUncachedResp:   Boolean,
      iCacheNSets:            Int,
      iCacheNWays:            Int,
      iCachePrefetch:         Boolean
    )
  }

  implicit def RocketTileParameterMainParser: ParserForClass[RocketTileParameterMain] =
    ParserForClass[RocketTileParameterMain]

  override def additionalAnnotations = Seq(IncludeUtilMetadata)

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
