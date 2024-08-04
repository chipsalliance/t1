// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.elaborator

import mainargs._
import org.chipsalliance.t1.rtl.T1Parameter
import org.chipsalliance.rocketv.RocketTileParameter
import chisel3.panamalib.option._
import org.chipsalliance.t1.tile.T1RocketTileParameter

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }

  @main
  case class ElaborateConfig(
    @arg(name = "target-dir", short = 't') targetDir: os.Path,
    @arg(name = "binder-mlirbc-out") binderMlirbcOut: Option[String] = None) {
    def elaborate(gen: () => chisel3.RawModule): Unit = {
      var fir:                  firrtl.ir.Circuit = null
      var panamaCIRCTConverter: chisel3.panamaconverter.PanamaCIRCTConverter = null

      val annos = Seq(
        new chisel3.stage.phases.Elaborate,
        if (binderMlirbcOut.isEmpty) new chisel3.stage.phases.Convert else chisel3.panamaconverter.stage.Convert
      ).foldLeft(
        Seq(
          chisel3.stage.ChiselGeneratorAnnotation(gen),
          chisel3.panamaconverter.stage.FirtoolOptionsAnnotation(FirtoolOptions(Set(
            BuildMode(BuildModeDebug),
            PreserveValues(PreserveValuesModeNamed),
            DisableUnknownAnnotations(true)
          ))),
        ): firrtl.AnnotationSeq
      ) { case (annos, stage) => stage.transform(annos) }
        .flatMap {
          case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
            if (binderMlirbcOut.isEmpty) fir = circuit
            None
          case chisel3.panamaconverter.stage.PanamaCIRCTConverterAnnotation(converter) =>
            if (binderMlirbcOut.nonEmpty) panamaCIRCTConverter = converter
            None
          case _: chisel3.panamaconverter.stage.FirtoolOptionsAnnotation  => None
          case _: chisel3.stage.DesignAnnotation[_]                       => None
          case _: chisel3.stage.ChiselCircuitAnnotation                   => None
          case a => Some(a)
        }

      binderMlirbcOut match {
        case Some(outFile) =>
          os.write(targetDir / s"$outFile.mlirbc", panamaCIRCTConverter.mlirBytecodeStream)
        case None =>
          os.write(targetDir / s"${fir.main}.fir", fir.serialize)
          os.write(targetDir / s"${fir.main}.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
      }
    }
  }

  implicit def elaborateConfig: ParserForClass[ElaborateConfig] = ParserForClass[ElaborateConfig]

  case class IPConfig(
    @arg(name = "ip-config", short = 'c') ipConfig: os.Path) {
    def generator = upickle.default
      .read[chisel3.experimental.SerializableModuleGenerator[org.chipsalliance.t1.rtl.T1, org.chipsalliance.t1.rtl.T1Parameter]](ujson.read(os.read(ipConfig)))
    def parameter: T1Parameter = generator.parameter
  }

  case class RocketConfig(
    @arg(name = "rocket-config", short = 'c') rocketConfig: os.Path) {
    def generator = upickle.default
      .read[chisel3.experimental.SerializableModuleGenerator[org.chipsalliance.rocketv.RocketTile, org.chipsalliance.rocketv.RocketTileParameter]](ujson.read(os.read(rocketConfig)))
    def parameter: RocketTileParameter = generator.parameter
  }

  case class T1RocketConfig(
                           @arg(name = "t1rocket-config", short = 'c') rocketConfig: os.Path) {
    def generator = upickle.default
      .read[chisel3.experimental.SerializableModuleGenerator[org.chipsalliance.t1.tile.T1RocketTile, org.chipsalliance.t1.tile.T1RocketTileParameter]](ujson.read(os.read(rocketConfig)))
    def parameter: T1RocketTileParameter = generator.parameter
  }

  implicit def ipConfig: ParserForClass[IPConfig] = ParserForClass[IPConfig]
  implicit def rocketConfig: ParserForClass[RocketConfig] = ParserForClass[RocketConfig]
  implicit def t1RocketConfig: ParserForClass[T1RocketConfig] = ParserForClass[T1RocketConfig]

  // format: off
  @main def ip(elaborateConfig: ElaborateConfig, ipConfig: IPConfig): Unit = elaborateConfig.elaborate(() =>
    ipConfig.generator.module()
  )
  @main def ipemu(elaborateConfig: ElaborateConfig, ipConfig: IPConfig): Unit = elaborateConfig.elaborate(() =>
    new org.chipsalliance.t1.ipemu.TestBench(ipConfig.generator)
  )
  @main def rocketemu(elaborateConfig: ElaborateConfig, rocketConfig: RocketConfig): Unit = elaborateConfig.elaborate(() =>
    new org.chipsalliance.t1.rocketv.TestBench(rocketConfig.generator)
  )
  @main def t1rocketemu(elaborateConfig: ElaborateConfig, t1rocketConfig: T1RocketConfig): Unit = elaborateConfig.elaborate(() =>
    new org.chipsalliance.t1.t1rocketemu.TestBench(t1rocketConfig.generator)
  )
  // format: on

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
