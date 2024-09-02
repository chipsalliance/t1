// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.configgen

import chisel3.experimental.SerializableModuleGenerator
import chisel3.util.{log2Ceil, BitPat}
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.decoder.T1CustomInstruction
import org.chipsalliance.t1.rtl.vrf.RamType

import java.util.LinkedHashMap

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }
  implicit class EmitVParameter(p: T1Parameter) {
    def emit(targetFile: os.Path) = os.write(
      targetFile,
      upickle.default.write(SerializableModuleGenerator(classOf[T1], p), indent = 2)
    )
  }

  @main def listConfigs(
    @arg(name = "project-dir", short = 't') projectDir: os.Path = os.pwd
  ): Unit = {
    val declaredMethods =
      Main.getClass().getDeclaredMethods().filter(m => m.getParameterTypes().mkString(", ") == "class os.Path, boolean")

    import scala.io.AnsiColor._

    declaredMethods.foreach(configgen => {
      val param = configgen.invoke(Main, os.root / "dev" / "null", false)
      println(s"""${BOLD}${MAGENTA_B} ${configgen.getName()} ${RESET}
                 |   ${param.toString()}""".stripMargin)
    })
  }

  @main def updateConfigs(
    @arg(name = "project-dir", short = 't') projectDir: os.Path = os.pwd
  ): Unit = {
    val declaredMethods =
      Main.getClass().getDeclaredMethods().filter(m => m.getParameterTypes().mkString(", ") == "class os.Path, boolean")

    import scala.io.AnsiColor._

    val generatedDir = projectDir / "configgen" / "generated"
    os.list(generatedDir).foreach(f => os.remove(f))

    declaredMethods.foreach(configgen => {
      val configName = configgen.getName()
      configgen.invoke(Main, generatedDir / s"$configName.json", true)
    })
  }

  // DLEN256 VLEN256;   FP; VRF p0rw,p1rw bank1; LSU bank8  beatbyte 8
  @main def blastoise(
    @arg(name = "target-file", short = 't') targetFile:           os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen  = 512
    val dLen  = 256
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32f"),
      t1customInstructions = Nil,
      vrfBankSize = 1,
      vrfRamType = RamType.p0rwp1rw,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(),
        divfpModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
        otherModuleParameters = Seq(
          (
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3)
          )
        ),
        floatModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
        zvbbModuleParameters = Seq()
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  // DLEN256 VLEN256;   FP; VRF p0rw,p1rw bank1; LSU bank8  beatbyte 8; Zvbb
  @main def psyduck(
    @arg(name = "target-file", short = 't') targetFile:           os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen  = 512
    val dLen  = 256
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32f", "Zvbb"),
      t1customInstructions = Nil,
      vrfBankSize = 1,
      vrfRamType = RamType.p0rwp1rw,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(),
        divfpModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 1)), Seq(0, 1, 2, 3))),
        otherModuleParameters = Seq(
          (
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3)
          )
        ),
        floatModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3))),
        zvbbModuleParameters =
          Seq((SerializableModuleGenerator(classOf[LaneZvbb], LaneZvbbParam(32, 3)), Seq(0, 1, 2, 3)))
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  // DLEN512 VLEN1K ; NOFP; VRF p0r,p1w   bank2; LSU bank8  beatbyte 16
  @main def machamp(
    @arg(name = "target-file", short = 't') targetFile:           os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen  = 1024
    val dLen  = 512
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32x"),
      t1customInstructions = Nil,
      vrfBankSize = 2,
      vrfRamType = RamType.p0rp1w,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        divfpModuleParameters = Seq(),
        otherModuleParameters = Seq(
          (
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3)
          )
        ),
        floatModuleParameters = Seq(),
        zvbbModuleParameters = Seq() // TODO
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  // DLEN1K  VLEN4K ; NOFP; VRF p0rw       bank4; LSU bank16 beatbyte 16
  @main def sandslash(
    @arg(name = "target-file", short = 't') targetFile:           os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true
  ): T1Parameter = {
    val vLen  = 4096
    val dLen  = 1024
    val param = T1Parameter(
      vLen,
      dLen,
      extensions = Seq("Zve32x"),
      t1customInstructions = Nil,
      vrfBankSize = 4,
      vrfRamType = RamType.p0rw,
      vfuInstantiateParameter = VFUInstantiateParameter(
        slotCount = 4,
        logicModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        aluModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(0)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(1)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(2)),
          (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 1)), Seq(3))
        ),
        shifterModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 1)), Seq(0, 1, 2, 3))
        ),
        mulModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 2)), Seq(0, 1, 2, 3))
        ),
        divModuleParameters = Seq(
          (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 1)), Seq(0, 1, 2, 3))
        ),
        divfpModuleParameters = Seq(),
        otherModuleParameters = Seq(
          (
            SerializableModuleGenerator(
              classOf[OtherUnit],
              OtherUnitParam(32, log2Ceil(vLen) + 1, log2Ceil(vLen * 8 / dLen), log2Ceil(dLen / 32), 4, 1)
            ),
            Seq(0, 1, 2, 3)
          )
        ),
        floatModuleParameters = Seq(),
        zvbbModuleParameters = Seq() // TODO
      )
    )
    if (doEmit) param.emit(targetFile)
    param
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
