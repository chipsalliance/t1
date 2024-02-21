// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.configgen

import chisel3.experimental.SerializableModuleGenerator
import mainargs._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.lsu.LSUInstantiateParameter

object Main {
  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName = "path"
    def read(strs: Seq[String]): Either[String, os.Path] = Right(os.Path(strs.head, os.pwd))
  }
  implicit class EmitVParameter(p: T1Parameter) {
    def emit(targetDir: os.Path) = os.write(
      targetDir / "config.json",
      upickle.default.write(SerializableModuleGenerator(classOf[T1], p), indent = 2)
    )
  }

  @main def listConfigs(
    @arg(name = "out", short = 'o') outputPath: os.Path
  ): Unit = {
    val configs = Main
      .getClass()
      .getDeclaredMethods()
      .filter(m => m.getParameters().mkString.contains("os.Path targetDir"))
      .map(m => {
        val cfg = """(v\d+)(l\d+)(b\d+)(.*)""".r
        m.getName() match {
          case cfg(v, l, b, fp) => Seq(v, l, b, fp).filter(_.size > 0).mkString("-")
        }
      })
    os.write(outputPath, upickle.default.write(configs))
  }

  @main def v1024l1b2(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 32,
    extensions = Seq("Zve32x"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 1,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divfpModuleParameters = Seq(),
      otherModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 3, 4, 0)), Seq(0, 1, 2, 3))
      ),
      floatModuleParameters = Seq()
    )
  ).emit(targetDir)

  @main def v1024l2b2(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 64,
    extensions = Seq("Zve32x"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 2,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divfpModuleParameters = Seq(),
      otherModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 3, 4, 0)), Seq(0, 1, 2, 3))
      ),
      floatModuleParameters = Seq()
    )
  ).emit(targetDir)

  @main def v1024l8b2(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32x"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 2,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divfpModuleParameters = Seq(),
      otherModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 3, 4, 0)), Seq(0, 1, 2, 3))
      ),
      floatModuleParameters = Seq()
    )
  ).emit(targetDir)

  @main def v1024l8b2fp(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32f"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 2,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(),
      divfpModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 0)), Seq(0, 1, 2, 3))),
      otherModuleParameters =
        Seq((SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 3, 4, 0)), Seq(0, 1, 2, 3))),
      floatModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3)))
    )
  ).emit(targetDir)

  @main def v4096l8b4(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 256,
    extensions = Seq("Zve32x"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 4,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divfpModuleParameters = Seq(),
      // todo: 8 = ?
      otherModuleParameters =
        Seq((SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 8, 3, 4, 0)), Seq(0, 1, 2, 3))),
      floatModuleParameters = Seq()
    )
  ).emit(targetDir)

  @main def v4096l8b4fp(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 256,
    extensions = Seq("Zve32f"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 4,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(),
      divfpModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 0)), Seq(0, 1, 2, 3))),
      otherModuleParameters =
        Seq((SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 8, 3, 4, 0)), Seq(0, 1, 2, 3))),
      floatModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3)))
    )
  ).emit(targetDir)

  @main def v4096l32b4(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 1024,
    extensions = Seq("Zve32x"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 4,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneDiv], LaneDivParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divfpModuleParameters = Seq(),
      otherModuleParameters =
        Seq((SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 3, 4, 0)), Seq(0, 1, 2, 3))),
      floatModuleParameters = Seq()
    )
  ).emit(targetDir)

  @main def v4096l32b4fp(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 1024,
    extensions = Seq("Zve32f"),
    lsuInstantiateParameters = Seq(
      LSUInstantiateParameter(
        name = "main",
        base = 0,
        size = BigInt("0x8000000"),
        banks = 4,
        bankAtBit = 6
      )
    ),
    vrfBankSize = 1,
    vfuInstantiateParameter = VFUInstantiateParameter(
      slotCount = 4,
      logicModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[MaskedLogic], LogicParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      aluModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(0)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(1)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(2)),
        (SerializableModuleGenerator(classOf[LaneAdder], LaneAdderParam(32, 0)), Seq(3))
      ),
      shifterModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneShifter], LaneShifterParameter(32, 0)), Seq(0, 1, 2, 3))
      ),
      mulModuleParameters = Seq(
        (SerializableModuleGenerator(classOf[LaneMul], LaneMulParam(32, 0)), Seq(0, 1, 2, 3))
      ),
      divModuleParameters = Seq(),
      divfpModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneDivFP], LaneDivFPParam(32, 0)), Seq(0, 1, 2, 3))),
      otherModuleParameters =
        Seq((SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 3, 4, 0)), Seq(0, 1, 2, 3))),
      floatModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3)))
    )
  ).emit(targetDir)

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
