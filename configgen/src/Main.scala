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

    os.write.over(
      projectDir / "configgen" / "all-configs.json",
      upickle.default.write(declaredMethods.map(_.getName()).sorted)
    )
  }

  // DLEN256 VLEN256;   FP; VRF p0rw,p1rw bank1; LSU bank8  beatbyte 8
  @main def blastoise(
    @arg(name = "target-dir", short = 't') targetDir: os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true,
  ): T1Parameter = {
    val param = T1Parameter(
      vLen = 256,
      dLen = 256,
      extensions = Seq("Zve32f"),
      // banks=8 dLen=256
      lsuBankParameters = Seq(
        BitSet(BitPat("b?????????????????????000??????")),
        BitSet(BitPat("b?????????????????????001??????")),
        BitSet(BitPat("b?????????????????????010??????")),
        BitSet(BitPat("b?????????????????????011??????")),
        BitSet(BitPat("b?????????????????????100??????")),
        BitSet(BitPat("b?????????????????????101??????")),
        BitSet(BitPat("b?????????????????????110??????")),
        BitSet(BitPat("b?????????????????????111??????"))
      ).map(bs => LSUBankParameter(bs, 8, false)),
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
    )
    if (doEmit) param.emit(targetDir)
    param
  }

  // DLEN512 VLEN1K ; NOFP; VRF p0r,p1w   bank2; LSU bank8  beatbyte 16
  @main def machamp(
    @arg(name = "target-dir", short = 't') targetDir: os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true,
  ): T1Parameter = {
    val param = T1Parameter(
      vLen = 1024,
      dLen = 512,
      extensions = Seq("Zve32x"),
      // banks=8 dLen=512 beatbyte16
      // TODO: fix bitpat @liqinjun
      lsuBankParameters = Seq(
        BitSet(BitPat("b???????????????????????0??????")),
        BitSet(BitPat("b???????????????????????1??????"))
      ).map(bs => LSUBankParameter(bs, 16, false)),
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
    )
    if (doEmit) param.emit(targetDir)
    param
  }

  // DLEN1K  VLEN4K ; NOFP; VRF p0rw       bank4; LSU bank16 beatbyte 16
  @main def sandslash(
    @arg(name = "target-dir", short = 't') targetDir: os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true,
  ): T1Parameter = {
    val param = T1Parameter(
      vLen = 4096,
      dLen = 1024,
      extensions = Seq("Zve32x"),
      // banks=16 dLen=1024
      // TODO: fix LSU param @liqinjun
      lsuBankParameters = Seq(
        BitSet(BitPat("b?????????????????????000??????")),
        BitSet(BitPat("b?????????????????????001??????")),
        BitSet(BitPat("b?????????????????????010??????")),
        BitSet(BitPat("b?????????????????????011??????")),
        BitSet(BitPat("b?????????????????????100??????")),
        BitSet(BitPat("b?????????????????????101??????")),
        BitSet(BitPat("b?????????????????????110??????")),
        BitSet(BitPat("b?????????????????????111??????"))
      ).map(bs => LSUBankParameter(bs, 16, false)),
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
    )
    if (doEmit) param.emit(targetDir)
    param
  }

  // DLEN2K  VLEN16K; NOFP; VRF p0rw      bank8; LSU bank8  beatbyte 64
  @main def alakazam(
    @arg(name = "target-dir", short = 't') targetDir: os.Path,
    @arg(name = "emit", short = 'e', doc = "emit config") doEmit: Boolean = true,
  ): T1Parameter = {
    val param = T1Parameter(
      vLen = 16384,
      dLen = 2048,
      extensions = Seq("Zve32x"),
      // banks=8 dLen=2048
      // TODO: fix LSU param @liqinjun
      lsuBankParameters = Seq(
        BitSet(BitPat("b?????????????????????000??????")),
        BitSet(BitPat("b?????????????????????001??????")),
        BitSet(BitPat("b?????????????????????010??????")),
        BitSet(BitPat("b?????????????????????011??????")),
        BitSet(BitPat("b?????????????????????100??????")),
        BitSet(BitPat("b?????????????????????101??????")),
        BitSet(BitPat("b?????????????????????110??????")),
        BitSet(BitPat("b?????????????????????111??????"))
      ).map(bs => LSUBankParameter(bs, 64, false)),
      vrfBankSize = 8,
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
    )
    if (doEmit) param.emit(targetDir)
    param
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
