// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.t1rocketv

import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.t1.elaborator.Elaborator
import org.chipsalliance.t1.rtl.vrf.RamType
import org.chipsalliance.t1.rtl.vrf.RamType.{p0rp1w, p0rw, p0rwp1rw}
import org.chipsalliance.t1.tile.{T1RocketTile, T1RocketTileParameter}

// --instructionSets rv32_i --instructionSets rv_a --instructionSets rv_c --instructionSets rv_v --instructionSets Zve32x --instructionSets zvl1024b --cacheBlockBytes 32 --nPMPs 8 --cacheable 80000000-ffffffff --sideEffects 00000000-1fffffff --dcacheNSets 64 --dcacheNWays 4 --dcacheRowBits 32 --iCacheNSets 32 --iCacheNWays 4 --iCachePrefetch false --dLen 256 --vrfBankSize 2 --vrfRamType p0rp1w
object T1RocketTile extends Elaborator {
  implicit object BitSetRead extends TokensReader.Simple[BitSet] {
    def shortName = "bitset"
    def read(strs: Seq[String]) = {
      Right(
        strs.head
          .split(",")
          .map { opt =>
            if (opt.contains("-")) {
              val range = opt.split("-")
              require(range.size == 2)
              val from = BigInt(range.head, 16)
              val to = BigInt(range.last, 16) + 1
              BitSet.fromRange(from, to - from, range.head.length * 4)
            } else if (opt.contains("+")) {
              val range = opt.split("\\+")
              require(range.size == 2)
              val from = BigInt(range.head, 16)
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

  implicit object RamTypeRead extends TokensReader.Simple[RamType] {
    def shortName = "ramtype"
    def read(strs: Seq[String]) = {
      Right(
        strs.head match {
          case "p0rw" => p0rw
          case "p0rp1w" => p0rp1w
          case "p0rwp1rw" => p0rwp1rw
        }
      )
    }
  }

  @main
  case class T1RocketTileParameterMain(
                                        @arg(name = "instructionSets") instructionSets: Seq[String],
                                        @arg(name = "cacheBlockBytes") cacheBlockBytes: Int,
                                        @arg(name = "nPMPs") nPMPs: Int,
                                        @arg(name = "cacheable") cacheable: BitSet,
                                        @arg(name = "sideEffects") sideEffects: BitSet,
                                        @arg(name = "dcacheNSets") dcacheNSets: Int,
                                        @arg(name = "dcacheNWays") dcacheNWays: Int,
                                        @arg(name = "dcacheRowBits") dcacheRowBits: Int,
                                        @arg(name = "iCacheNSets") iCacheNSets: Int,
                                        @arg(name = "iCacheNWays") iCacheNWays: Int,
                                        @arg(name = "iCachePrefetch") iCachePrefetch: Boolean,
                                        @arg(name = "dLen") dLen: Int,
                                        @arg(name = "vrfBankSize") vrfBankSize: Int,
                                        @arg(name = "vrfRamType") vrfRamType: RamType
                                      ) {
    def convert: T1RocketTileParameter = T1RocketTileParameter(
      instructionSets: Seq[String],
      cacheBlockBytes: Int,
      nPMPs: Int,
      cacheable: BitSet,
      sideEffects: BitSet,
      dcacheNSets: Int,
      dcacheNWays: Int,
      dcacheRowBits: Int,
      iCacheNSets: Int,
      iCacheNWays: Int,
      iCachePrefetch: Boolean,
      dLen: Int,
      vrfBankSize: Int,
      vrfRamType: RamType
    )
  }

  implicit def T1RocketTileParameterMainParser: ParserForClass[T1RocketTileParameterMain] =
    ParserForClass[T1RocketTileParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: T1RocketTileParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[T1RocketTile, T1RocketTileParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
