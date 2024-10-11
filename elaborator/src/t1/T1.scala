// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.elaborator.t1

import chisel3.experimental.util.SerializableModuleElaborator
import mainargs._
import org.chipsalliance.t1.rtl.vrf.RamType
import org.chipsalliance.t1.rtl.vrf.RamType.{p0rp1w, p0rw, p0rwp1rw}
import org.chipsalliance.t1.rtl.{T1, T1Parameter, VFUInstantiateParameter}

object T1 extends SerializableModuleElaborator {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  val className: String = getClass.getSimpleName.replace("$", "")
  type D = T1
  type P = T1Parameter
  type M = T1ParameterMain

  implicit object RamTypeRead extends TokensReader.Simple[RamType] {
    def shortName               = "ramtype"
    def read(strs: Seq[String]) = {
      Right(
        strs.head match {
          case "p0rw"     => p0rw
          case "p0rp1w"   => p0rp1w
          case "p0rwp1rw" => p0rwp1rw
        }
      )
    }
  }

  @main
  case class T1ParameterMain(
    @arg(name = "dLen") dLen:               Int,
    @arg(name = "extensions") extensions:   Seq[String],
    @arg(name = "vrfBankSize") vrfBankSize: Int,
    @arg(name = "vrfRamType") vrfRamType:   RamType,
    @arg(name = "vfuInstantiateParameter") vfuInstantiateParameter: String) {
    def convert: P = {
      val fp   = extensions.contains("zve32f")
      val zvbb = extensions.contains("zvbb")
      def vLen: Int = extensions.collectFirst { case s"zvl${vlen}b" =>
        vlen.toInt
      }.get

      T1Parameter(
        dLen,
        extensions,
        vrfBankSize,
        vrfRamType,
        VFUInstantiateParameter.parse(vLen = vLen, dLen = dLen, preset = vfuInstantiateParameter, fp = fp, zvbb = zvbb)
      )
    }
  }

  implicit def T1ParameterMainParser: ParserForClass[M] = ParserForClass[M]

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
