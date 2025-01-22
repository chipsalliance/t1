// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Jiuyang Liu <liu@jiuyang.me>

object printall extends App {
  org.chipsalliance.rvdecoderdb.instructions(os.pwd / "rvdecoderdbtest" / "jvm" / "riscv-opcodes").foreach(println)
}

object json extends App {
  org.chipsalliance.rvdecoderdb
    .instructions(os.pwd / "rvdecoderdbtest" / "jvm" / "riscv-opcodes")
    .foreach(i => println(upickle.default.write(i)))
}

object fromResource extends App {
  org.chipsalliance.rvdecoderdb
    .instructions(org.chipsalliance.rvdecoderdb.extractResource(getClass.getClassLoader))
    .foreach(println(_))
}
