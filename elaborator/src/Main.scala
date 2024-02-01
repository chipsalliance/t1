// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.elaborator

import mainargs._

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }

  @main
  case class ElaborateConfig(
    @arg(name = "target-dir", short = 't') targetDir: os.Path) {
    def elaborate(gen: () => chisel3.RawModule): Unit = {
      var topName: String = null
      val annos = Seq(
        new chisel3.stage.phases.Elaborate,
        new chisel3.stage.phases.Convert
      ).foldLeft(
        Seq(
          chisel3.stage.ChiselGeneratorAnnotation(gen)
        ): firrtl.AnnotationSeq
      ) { case (annos, stage) => stage.transform(annos) }
        .flatMap {
          case firrtl.stage.FirrtlCircuitAnnotation(circuit) =>
            topName = circuit.main
            os.write(targetDir / s"$topName.fir", circuit.serialize)
            None
          case _: chisel3.stage.DesignAnnotation[_]                       => None
          case _: chisel3.stage.ChiselCircuitAnnotation                   => None
          case _: freechips.rocketchip.util.ParamsAnnotation              => None
          case _: freechips.rocketchip.util.RegFieldDescMappingAnnotation => None
          case _: freechips.rocketchip.util.AddressMapAnnotation          => None
          case _: freechips.rocketchip.util.ParamsAnnotation              => None
          case _: freechips.rocketchip.util.SRAMAnnotation                => None
          case a => Some(a)
        }
      os.write(targetDir / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
    }
  }

  implicit def elaborateConfig: ParserForClass[ElaborateConfig] = ParserForClass[ElaborateConfig]

  case class IPConfig(
    @arg(name = "ip-config", short = 'c') ipConfig: os.Path) {
    def generator = upickle.default
      .read[chisel3.experimental.SerializableModuleGenerator[org.chipsalliance.t1.rtl.V, org.chipsalliance.t1.rtl.VParameter]](ujson.read(os.read(ipConfig)))
  }

  implicit def ipConfig: ParserForClass[IPConfig] = ParserForClass[IPConfig]

  @main
  case class SubsystemConfig(ipConfig: IPConfig) {
    def cdeParameter = (new org.chipsalliance.t1.subsystem.VerdesConfig).orElse(new org.chipsalliance.cde.config.Config((_, _, _) => {
      case org.chipsalliance.t1.subsystem.T1ConfigPath                              => ipConfig.ipConfig
    }))
  }
  implicit def subsystemConfig: ParserForClass[SubsystemConfig] = ParserForClass[SubsystemConfig]

  // format: off
  @main def ip(elaborateConfig: ElaborateConfig, ipConfig: IPConfig): Unit = elaborateConfig.elaborate(() =>
    ipConfig.generator.module()
  )
  @main def ipemu(elaborateConfig: ElaborateConfig, ipConfig: IPConfig): Unit = elaborateConfig.elaborate(() =>
    new org.chipsalliance.t1.ipemu.TestBench(ipConfig.generator)
  )
  @main def subsystem(elaborateConfig: ElaborateConfig, subsystemConfig: SubsystemConfig): Unit = elaborateConfig.elaborate(() =>
    freechips.rocketchip.diplomacy.LazyModule(new org.chipsalliance.t1.subsystem.VerdesSystem()(subsystemConfig.cdeParameter))(freechips.rocketchip.diplomacy.ValName("T1Subsystem"), chisel3.experimental.UnlocatableSourceInfo).module
  )
  @main def subsystememu(elaborateConfig: ElaborateConfig, subsystemConfig: SubsystemConfig): Unit = elaborateConfig.elaborate(() =>
    new org.chipsalliance.t1.subsystememu.TestHarness()(subsystemConfig.cdeParameter)
  )
  @main def fpga(elaborateConfig: ElaborateConfig, subsystemConfig: SubsystemConfig): Unit = elaborateConfig.elaborate(() =>
    new org.chipsalliance.t1.fpga.FPGAHarness()(subsystemConfig.cdeParameter)
  )
  // format: on

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
