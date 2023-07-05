import mill._
import mill.scalalib._
import mill.scalalib.publish._

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion

trait VectorModule extends ScalaModule with PublishModule {
  def chiselModule: Option[PublishModule] = None
  def chiselPluginJar: T[Option[PathRef]] = T(None)
  def arithmeticModule: Option[PublishModule] = None
  def tilelinkModule: Option[PublishModule] = None

  override def moduleDeps = Seq() ++ chiselModule ++ arithmeticModule ++ tilelinkModule
  override def scalacPluginClasspath = T(super.scalacPluginClasspath() ++ chiselPluginJar())
  override def scalacOptions = T(super.scalacOptions() ++ chiselPluginJar().map(path => s"-Xplugin:${path.path}"))
  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "me.jiuyang",
    url = "https://jiuyang.me",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("sequencer", "vector"),
    developers = Seq(
      Developer("sequencer", "Jiuyang Liu", "https://jiuyang.me/")
    )
  )
}
