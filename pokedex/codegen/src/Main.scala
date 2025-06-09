package org.chipsalliance.t1.pokedex.codegen

import mainargs._

object Main {
  @main
  case class Params(
    @arg(short = 'i', name = "model-dir", doc = "Path to Sail model implementation")
    sailModelDir: os.Path,
    @arg(short = 'o', name = "output-dir", doc = "Output directory path to generate sail sources")
    outputDir:    os.Path,
    @arg(short = 'r', name = "riscv-opcodes-path", doc = "Path to riscv-opcodes path")
    riscvOpCodesPath: os.Path) {}

  implicit object PathRead extends TokensReader.Simple[os.Path] {
    def shortName               = "path"
    def read(strs: Seq[String]) = Right(os.Path(strs.head, os.pwd))
  }

  def main(args: Array[String]): Unit = {
    val params = ParserForClass[Params].constructOrExit(args)
    println("Hello World")
  }
}
