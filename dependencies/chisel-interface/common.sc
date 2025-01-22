import mill._
import mill.scalalib._

trait HasChisel extends ScalaModule {
  // Define these for building chisel from source
  def chiselModule: Option[ScalaModule] = None

  override def moduleDeps = super.moduleDeps ++ chiselModule

  def chiselPluginJar: T[Option[PathRef]] = None

  override def scalacOptions = T(
    super.scalacOptions() ++ chiselPluginJar().map(path =>
      s"-Xplugin:${path.path}"
    ) ++ Seq("-Ymacro-annotations")
  )

  override def scalacPluginClasspath: T[Agg[PathRef]] = T(
    super.scalacPluginClasspath() ++ chiselPluginJar()
  )

  // Define these for building chisel from ivy
  def chiselIvy: Option[Dep] = None

  override def ivyDeps = T(super.ivyDeps() ++ chiselIvy)

  def chiselPluginIvy: Option[Dep] = None

  override def scalacPluginIvyDeps: T[Agg[Dep]] = T(
    super.scalacPluginIvyDeps() ++ chiselPluginIvy
      .map(Agg(_))
      .getOrElse(Agg.empty[Dep])
  )
}

trait ChiselInterfaceModule extends HasChisel {
  // Use for elaboration.
  def mainargsIvy: Dep
  override def ivyDeps = T(super.ivyDeps() ++ Some(mainargsIvy))
}

trait DWBBModule extends ChiselInterfaceModule

trait AXI4Module extends ChiselInterfaceModule

trait JTAGModule extends ChiselInterfaceModule
