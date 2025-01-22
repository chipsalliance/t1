// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance

/** Parse instructions from riscv/riscv-opcodes */
package object rvdecoderdb {
  def instructions(riscvOpcodes: os.Path, custom: Iterable[os.Path] = Seq.empty): Iterable[Instruction] = {
    parser.parse(
      os
        .walk(riscvOpcodes)
        .filter(f =>
          f.baseName.startsWith("rv128_") ||
            f.baseName.startsWith("rv64_") ||
            f.baseName.startsWith("rv32_") ||
            f.baseName.startsWith("rv_")
        )
        .filter(os.isFile)
        .map(f => (f.baseName, os.read(f), !f.segments.contains("unratified"), false)) ++
        custom
          .map(f => (f.baseName, os.read(f), false, true)),
      argLut(riscvOpcodes).view.mapValues(a => (a.lsb, a.msb)).toMap
    )
  }

  def argLut(riscvOpcodes: os.Path): Map[String, Arg] = os
    .read(riscvOpcodes / "arg_lut.csv")
    .split("\n")
    .map { str =>
      val l = str
        .replace(", ", ",")
        .replace("\"", "")
        .split(",")
      l(0) -> Arg(l(0), l(1).toInt, l(2).toInt)
    }
    .toMap

  def causes(riscvOpcodes: os.Path): Map[String, Int] = os
    .read(riscvOpcodes / "causes.csv")
    .split("\n")
    .map { str =>
      val l = str
        .replace(", ", ",")
        .replace("\"", "")
        .split(",")
      l(1) -> java.lang.Long.decode(l(0)).toInt
    }
    .toMap

  def csrs(riscvOpcodes: os.Path): Seq[(String, Int)] =
    Seq(os.read(riscvOpcodes / "csrs.csv"), os.read(riscvOpcodes / "csrs32.csv")).flatMap(
      _.split("\n").map { str =>
        val l = str
          .replace(" ", "")
          .replace("\"", "")
          .replace("\'", "")
          .split(",")
        l(1) -> java.lang.Long.decode(l(0)).toInt
      }.toMap
    )

  def extractResource(cl: ClassLoader): os.Path = {
    val rvdecoderdbPath = os.temp.dir()
    val rvdecoderdbTar = os.temp(os.read(os.resource(cl) / "riscv-opcodes.tar"))
    os.proc("tar", "xf", rvdecoderdbTar).call(rvdecoderdbPath)
    rvdecoderdbPath
  }

  @deprecated("remove fromFile")
  object fromFile {
    def instructions(riscvOpcodes: os.Path, custom: Iterable[os.Path] = Seq.empty): Iterable[Instruction] =
      rvdecoderdb.instructions(riscvOpcodes, custom)
    def argLut(riscvOpcodes: os.Path): Map[String, Arg] = rvdecoderdb.argLut(riscvOpcodes)
    def causes(riscvOpcodes: os.Path): Map[String, Int] = rvdecoderdb.causes(riscvOpcodes)
    def csrs(riscvOpcodes:   os.Path): Seq[(String, Int)] = rvdecoderdb.csrs(riscvOpcodes)
  }
}
