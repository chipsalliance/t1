// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.rocketv

import chisel3.experimental.util.SerializableModuleElaborator
import chisel3.stage.{IncludeUtilMetadata, UseSRAMBlackbox}
import mainargs._
import org.chipsalliance.rocketv.{ICache, ICacheParameter}

object ICache extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = ICache
  type P = ICacheParameter
  type M = ICacheParameterMain

  @main
  case class ICacheParameterMain(
    @arg(name = "useAsyncReset") useAsyncReset: Boolean,
    @arg(name = "prefetch") prefetch:           Boolean,
    @arg(name = "nSets") nSets:                 Int,
    @arg(name = "nWays") nWays:                 Int,
    @arg(name = "blockBytes") blockBytes:       Int,
    @arg(name = "usingVM") usingVM:             Boolean,
    @arg(name = "vaddrBits") vaddrBits:         Int,
    @arg(name = "paddrBits") paddrBits: Int) {
    def convert: ICacheParameter = ICacheParameter(
      useAsyncReset,
      prefetch,
      nSets,
      nWays,
      blockBytes,
      usingVM,
      vaddrBits,
      paddrBits
    )
  }

  implicit def ICacheParameterMainParser: ParserForClass[ICacheParameterMain] = ParserForClass[ICacheParameterMain]

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
