import mill._
import mill.scalalib._
import mill.scalalib.publish._
import coursier.maven.MavenRepository

import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.1.4`
import de.tobiasroeser.mill.vcs.version.VcsVersion

trait VectorModule extends ScalaModule with PublishModule {
  // SNAPSHOT of Chisel is published to the SONATYPE
  override def repositoriesTask = T.task { super.repositoriesTask() ++ Seq(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    MavenRepository("https://oss.sonatype.org/content/repositories/releases")
  ) }


  // override to build from source, see the usage of chipsalliance/playground
  def chisel3Module: Option[PublishModule] = None

  // override to build from source, see the usage of chipsalliance/playground
  def chisel3PluginJar: T[Option[PathRef]] = T {
    None
  }

  // override to build from source, see the usage of chipsalliance/playground
  def chiseltestModule: Option[PublishModule] = None

  // override to build from source, see the usage of chipsalliance/playground
  def arithmeticModule: Option[PublishModule] = None

  // override to build from source, see the usage of chipsalliance/playground
  def tilelinkModule: Option[PublishModule] = None

  // override to build from source, see the usage of chipsalliance/playground
  def hardfloatModule: Option[PublishModule] = None


  // Use SNAPSHOT chisel by default, downstream users should override this for their own project versions.
  def chisel3IvyDep: T[Option[Dep]] = None

  def chisel3PluginIvyDep: T[Option[Dep]] = None

  def chiseltestIvyDep: T[Option[Dep]] = None

  override def moduleDeps = Seq() ++ chisel3Module ++ chiseltestModule ++ arithmeticModule ++ tilelinkModule ++ hardfloatModule

  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ chisel3PluginJar()
  }

  override def scalacPluginIvyDeps = T {
    Agg() ++ chisel3PluginIvyDep()
  }

  override def scalacOptions = T {
    super.scalacOptions() ++ chisel3PluginJar().map(path => s"-Xplugin:${path.path}")
  }

  override def ivyDeps = T {
    Agg() ++ chisel3IvyDep()
  }

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

// maintain a hardfloat module for us to reduce the upstreaming overhead
trait HardfloatModule extends ScalaModule with PublishModule {
  def chisel3Module: Option[PublishModule]
  def chisel3PluginJar: T[Option[PathRef]]

  // remove test dep
  override def allSourceFiles = T(super.allSourceFiles().filterNot(_.path.last.contains("Tester")).filterNot(_.path.segments.contains("test")))

  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ chisel3PluginJar()
  }

  override def moduleDeps = Seq() ++ chisel3Module

  override def scalacOptions = T {
    super.scalacOptions() ++ chisel3PluginJar().map(path => s"-Xplugin:${path.path}")
  }

  def publishVersion = de.tobiasroeser.mill.vcs.version.VcsVersion.vcsState().format()

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "edu.berkeley.cs",
    url = "http://chisel.eecs.berkeley.edu",
    licenses = Seq(License.`BSD-3-Clause`),
    versionControl = VersionControl.github("sequencer", "vector"),
    developers = Seq(
      Developer("jhauser-ucberkeley", "John Hauser", "https://www.colorado.edu/faculty/hauser/about/"),
      Developer("aswaterman", "Andrew Waterman", "https://aspire.eecs.berkeley.edu/author/waterman/"),
      Developer("yunsup", "Yunsup Lee", "https://aspire.eecs.berkeley.edu/author/yunsup/")
    )
  )
}
