// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.rocketv.{HellaCache, HellaCacheParameter}
import org.chipsalliance.t1.elaborator.Elaborator

object DCache extends Elaborator {
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
  case class DCacheParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset:               Boolean,
    @arg(name = "clockGate") clockGate:                       Boolean,
    @arg(name = "xLen") xLen:                                 Int,
    @arg(name = "fLen") fLen:                                 Int,
    @arg(name = "usingVM") usingVM:                           Boolean,
    @arg(name = "paddrBits") paddrBits:                       Int,
    @arg(name = "cacheBlockBytes") cacheBlockBytes:           Int,
    @arg(name = "nWays") nWays:                               Int,
    @arg(name = "nSets") nSets:                               Int,
    @arg(name = "rowBits") rowBits:                           Int,
    @arg(name = "nTLBSets") nTLBSets:                         Int,
    @arg(name = "nTLBWays") nTLBWays:                         Int,
    @arg(name = "tagECC") tagECC:                             Option[String],
    @arg(name = "dataECC") dataECC:                           Option[String],
    @arg(name = "maxUncachedInFlight") maxUncachedInFlight:   Int,
    @arg(name = "separateUncachedResp") separateUncachedResp: Boolean,
    @arg(name = "legal") legal:                               BitSet,
    @arg(name = "cacheable") cacheable:                       BitSet,
    @arg(name = "read") read:                                 BitSet,
    @arg(name = "write") write:                               BitSet,
    @arg(name = "putPartial") putPartial:                     BitSet,
    @arg(name = "logic") logic:                               BitSet,
    @arg(name = "arithmetic") arithmetic:                     BitSet,
    @arg(name = "exec") exec:                                 BitSet,
    @arg(name = "sideEffects") sideEffects: BitSet) {
    def convert: HellaCacheParameter = HellaCacheParameter(
      useAsyncReset,
      clockGate,
      xLen,
      fLen,
      usingVM,
      paddrBits,
      cacheBlockBytes,
      nWays,
      nSets,
      rowBits,
      nTLBSets,
      nTLBWays,
      tagECC,
      dataECC,
      maxUncachedInFlight,
      separateUncachedResp,
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
  }

  implicit def DCacheParameterMainParser: ParserForClass[DCacheParameterMain] = ParserForClass[DCacheParameterMain]

  @main
  def config(@arg(name = "parameter") parameter: DCacheParameterMain) = configImpl(parameter.convert)

  @main
  def design(@arg(name = "parameter") parameter: os.Path, @arg(name = "run-firtool") runFirtool: mainargs.Flag) =
    designImpl[HellaCache, HellaCacheParameter](parameter, runFirtool.value)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
