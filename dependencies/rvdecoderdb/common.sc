// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._
import mill.scalalib._
import mill.scalajslib._

trait RVDecoderDBJVMModule extends ScalaModule {
  override def sources: T[Seq[PathRef]] = T.sources { super.sources() ++ Some(PathRef(millSourcePath / "jvm" / "src"))  }
  def osLibIvy: Dep
  def upickleIvy: Dep
  override def ivyDeps = super.ivyDeps() ++ Some(osLibIvy) ++ Some(upickleIvy)
}

trait HasRVDecoderDBResource extends ScalaModule {
  def riscvOpcodesPath: T[Option[PathRef]] = T(None)
  def riscvOpcodesTar: T[Option[PathRef]] = T {
    riscvOpcodesPath().map { riscvOpcodesPath =>
      val tmpDir = os.temp.dir()
      os.makeDir(tmpDir / "unratified")
      os.walk(riscvOpcodesPath.path)
        .filter(f =>
          f.baseName.startsWith("rv128_") ||
            f.baseName.startsWith("rv64_") ||
            f.baseName.startsWith("rv32_") ||
            f.baseName.startsWith("rv_") ||
            f.ext == "csv"
        ).groupBy(_.segments.contains("unratified")).map {
            case (true, fs) => fs.map(os.copy.into(_, tmpDir / "unratified"))
            case (false, fs) => fs.map(os.copy.into(_, tmpDir))
          }
      os.proc("tar", "cf", T.dest / "riscv-opcodes.tar", ".").call(tmpDir)
      PathRef(T.dest)
    }
  }
  override def resources: T[Seq[PathRef]] = super.resources() ++ riscvOpcodesTar()
}

trait RVDecoderDBJVMTestModule extends HasRVDecoderDBResource with ScalaModule {
  override def sources: T[Seq[PathRef]] = T.sources { super.sources() ++ Some(PathRef(millSourcePath / "jvm" / "src"))  }
  def dut: RVDecoderDBJVMModule
  override def moduleDeps = super.moduleDeps ++ Some(dut)
}

trait RVDecoderDBJSModule extends ScalaJSModule {
  override def sources: T[Seq[PathRef]] = T.sources { super.sources() ++ Some(PathRef(millSourcePath / "js" / "src"))  }
  def upickleIvy: Dep
  override def ivyDeps = super.ivyDeps() ++ Some(upickleIvy)
}

trait RVDecoderDBTestJSModule extends ScalaJSModule {
  override def sources: T[Seq[PathRef]] = T.sources { super.sources() ++ Some(PathRef(millSourcePath / "js" / "src"))  }
  def dut: RVDecoderDBJSModule
  override def moduleDeps = super.moduleDeps ++ Some(dut)
}
