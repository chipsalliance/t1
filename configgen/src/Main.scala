// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.configgen

import chisel3.experimental.SerializableModuleGenerator
import chisel3.util.BitPat
import chisel3.util.experimental.BitSet
import mainargs._
import org.chipsalliance.t1.rtl._
import org.chipsalliance.t1.rtl.lsu.LSUInstantiateParameter
import org.chipsalliance.t1.rtl.vrf.RamType

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

  @main def listConfigs(): Unit = {
    val configs = Main
      .getClass()
      .getDeclaredMethods()
      .filter(m => m.getParameters().mkString.contains("os.Path targetDir"))
      .map(_.getName())
    println(configs.mkString(","))
  }

  @main def bulbasaur(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 32,
    extensions = Seq("Zve32x"),
    // banks = 1 dLen = 32
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("0", 16), 28))
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  @main def charmander(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 64,
    extensions = Seq("Zve32x"),
    // banks = 2 dLen=64
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("8", 16), 28)),
      BitSet(new BitPat(BigInt("8", 16), BigInt("8", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  @main def squirtle(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32x"),
    // banks = 2 dLen=256
    lsuBankParameters = Seq(
      new BitPat(BigInt("0", 16), BigInt("20", 16), 28),
      new BitPat(BigInt("20", 16), BigInt("20", 16), 28),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  // squirtle + fp => blastoise
  @main def blastoise(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32f"),
    // banks = 2 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("20", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("20", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  @main def seel(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32x"),
    // banks = 2 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("20", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("20", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 2,
    vrfRamType = RamType.p0rp1w,
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

  // seel + fp
  @main def dewgong(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32f"),
    // banks = 2 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("20", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("20", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 2,
    vrfRamType = RamType.p0rp1w,
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

  @main def horsea(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32x"),
    // banks = 2 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("20", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("20", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 4,
    vrfRamType = RamType.p0rw,
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

  // horsea + fp
  @main def seadra(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 1024,
    dLen = 256,
    extensions = Seq("Zve32f"),
    // banks = 2 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("20", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("20", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 4,
    vrfRamType = RamType.p0rw,
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

  @main def mankey(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 2048,
    dLen = 512,
    extensions = Seq("Zve32x"),
    // banks = 2 dLen=512
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("40", 16), 28)),
      BitSet(new BitPat(BigInt("40", 16), BigInt("40", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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
        (SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 4, 4, 0)), Seq(0, 1, 2, 3))
      ),
      floatModuleParameters = Seq()
    )
  ).emit(targetDir)

  // mankey + fp
  @main def primeape(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 512,
    extensions = Seq("Zve32f"),
    // banks = 2 dLen=512
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("40", 16), 28)),
      BitSet(new BitPat(BigInt("40", 16), BigInt("40", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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
        Seq((SerializableModuleGenerator(classOf[OtherUnit], OtherUnitParam(32, 11, 6, 4, 4, 0)), Seq(0, 1, 2, 3))),
      floatModuleParameters =
        Seq((SerializableModuleGenerator(classOf[LaneFloat], LaneFloatParam(32, 3)), Seq(0, 1, 2, 3)))
    )
  ).emit(targetDir)

  @main def psyduck(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 256,
    extensions = Seq("Zve32x"),
    // banks = 4 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("60", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("60", 16), 28)),
      BitSet(new BitPat(BigInt("40", 16), BigInt("60", 16), 28)),
      BitSet(new BitPat(BigInt("60", 16), BigInt("60", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  @main def golduck(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 256,
    extensions = Seq("Zve32f"),
    // banks = 4 dLen=256
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("60", 16), 28)),
      BitSet(new BitPat(BigInt("20", 16), BigInt("60", 16), 28)),
      BitSet(new BitPat(BigInt("40", 16), BigInt("60", 16), 28)),
      BitSet(new BitPat(BigInt("60", 16), BigInt("60", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  @main def magnemite(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 1024,
    extensions = Seq("Zve32x"),
    // banks = 4 dLen=1024
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("180", 16), 28)),
      BitSet(new BitPat(BigInt("80", 16), BigInt("180", 16), 28)),
      BitSet(new BitPat(BigInt("100", 16), BigInt("180", 16), 28)),
      BitSet(new BitPat(BigInt("180", 16), BigInt("180", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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

  @main def magneton(
    @arg(name = "target-dir", short = 't') targetDir: os.Path
  ): Unit = T1Parameter(
    vLen = 4096,
    dLen = 1024,
    extensions = Seq("Zve32f"),
    // banks = 4 dLen=1024
    lsuBankParameters = Seq(
      BitSet(new BitPat(BigInt("0", 16), BigInt("180", 16), 28)),
      BitSet(new BitPat(BigInt("80", 16), BigInt("180", 16), 28)),
      BitSet(new BitPat(BigInt("100", 16), BigInt("180", 16), 28)),
      BitSet(new BitPat(BigInt("180", 16), BigInt("180", 16), 28)),
    ).map(bs => LSUBankParameter(bs, false)),
    vrfBankSize = 1,
    vrfRamType = RamType.p0rwp1rw,
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
