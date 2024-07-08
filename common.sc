// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

import mill._
import mill.scalalib._

trait HasChisel
  extends ScalaModule {
  // Define these for building chisel from source
  def chiselModule: Option[ScalaModule]

  override def moduleDeps = super.moduleDeps ++ chiselModule

  def chiselPluginJar: T[Option[PathRef]]

  override def scalacOptions = T(super.scalacOptions() ++ chiselPluginJar().map(path => s"-Xplugin:${path.path}") ++ Seq("-Ymacro-annotations"))

  override def scalacPluginClasspath: T[Agg[PathRef]] = T(super.scalacPluginClasspath() ++ chiselPluginJar())

  // Define these for building chisel from ivy
  def chiselIvy: Option[Dep]

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)

  def chiselPluginIvy: Option[Dep]

  override def scalacPluginIvyDeps: T[Agg[Dep]] = T(super.scalacPluginIvyDeps() ++ chiselPluginIvy.map(Agg(_)).getOrElse(Agg.empty[Dep]))
}

trait HasRVDecoderDB extends ScalaModule {
  def rvdecoderdbModule: ScalaModule
  def riscvOpcodesPath: T[PathRef]
  def moduleDeps = super.moduleDeps ++ Seq(rvdecoderdbModule)
  def riscvOpcodesTar: T[PathRef] = T {
    val tmpDir = os.temp.dir()
    os.makeDir(tmpDir / "unratified")
    os.walk(riscvOpcodesPath().path)
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
  override def resources: T[Seq[PathRef]] = super.resources() ++ Some(riscvOpcodesTar())
}

// Local definitions
trait T1Module
  extends ScalaModule
    with HasChisel
    with HasRVDecoderDB {
  def arithmeticModule: ScalaModule
  def hardfloatModule: ScalaModule
  def axi4Module: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(arithmeticModule, hardfloatModule, axi4Module)
}

trait ConfigGenModule
  extends ScalaModule {
  def t1Module: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(t1Module)
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))
}

// T1 forked version of RocketCore
trait RocketModule
  extends ScalaModule
    with HasChisel
    with HasRVDecoderDB {
  def rocketchipModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(rocketchipModule)
}

// The next generation of purely standalone Rocket Core w/ AXI/CHI.
trait RocketVModule
  extends ScalaModule
    with HasChisel
    with HasRVDecoderDB {
  def axi4Module: ScalaModule
  def hardfloatModule: ScalaModule

  def moduleDeps = super.moduleDeps ++ Seq(axi4Module, hardfloatModule)
}

trait EmuHelperModule
  extends ScalaModule
    with HasChisel

trait IPEmulatorModule
  extends ScalaModule
    with HasChisel {
  def t1Module: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(t1Module)
}

trait ElaboratorModule
  extends ScalaModule
    with HasChisel {
  def generators: Seq[ScalaModule]
  def panamaconverterModule: ScalaModule
  def circtInstallPath: T[PathRef]
  override def moduleDeps = super.moduleDeps ++ Seq(panamaconverterModule) ++ generators
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))
  override def javacOptions = T(super.javacOptions() ++ Seq("--enable-preview", "--release", "21"))
  override def forkArgs: T[Seq[String]] = T(
    super.forkArgs() ++ Seq("--enable-native-access=ALL-UNNAMED", "--enable-preview", s"-Djava.library.path=${ circtInstallPath().path / "lib"}")
  )
}

trait OMReaderLibModule
  extends ScalaModule
    with HasChisel {
  def panamaconverterModule: ScalaModule
  def circtInstallPath: T[PathRef]
  override def moduleDeps = super.moduleDeps ++ Seq(panamaconverterModule)
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))
  override def javacOptions = T(super.javacOptions() ++ Seq("--enable-preview", "--release", "21"))
  override def forkArgs: T[Seq[String]] = T(
    super.forkArgs() ++ Seq("--enable-native-access=ALL-UNNAMED", "--enable-preview", s"-Djava.library.path=${ circtInstallPath().path / "lib"}")
  )
}

trait OMReaderModule
  extends ScalaModule
    with HasChisel {
  def panamaconverterModule: ScalaModule
  def omreaderlibModule: ScalaModule
  def circtInstallPath: T[PathRef]
  override def moduleDeps = super.moduleDeps ++ Seq(panamaconverterModule, omreaderlibModule)
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Seq(mainargsIvy))
  override def javacOptions = T(super.javacOptions() ++ Seq("--enable-preview", "--release", "21"))
  override def forkArgs: T[Seq[String]] = T(
    super.forkArgs() ++ Seq("--enable-native-access=ALL-UNNAMED", "--enable-preview", s"-Djava.library.path=${ circtInstallPath().path / "lib"}")
  )
}

trait RocketEmulatorModule
  extends ScalaModule
    with HasChisel {
  def rocketVModule: ScalaModule
  def moduleDeps = super.moduleDeps ++ Seq(rocketVModule)
}
