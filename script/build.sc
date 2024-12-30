import mill._
import mill.scalalib._
import mill.define.{Command, TaskModule}
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import mill.scalalib.TestModule.Utest
import mill.util.Jvm
import coursier.maven.MavenRepository

trait ScriptModule extends ScalaModule with ScalafmtModule {
  val scala3   = "3.3.3"
  val mainargs = ivy"com.lihaoyi::mainargs:0.5.0"
  val oslib    = ivy"com.lihaoyi::os-lib:0.10.0"
  val upickle  = ivy"com.lihaoyi::upickle:3.3.1"
  val requests = ivy"com.lihaoyi::requests:0.9.0"

  def scalaVersion     = scala3
  def scalacOptions    = Seq("-new-syntax", "-deprecation")
  override def ivyDeps = Agg(mainargs, oslib, upickle, requests)
}

object emu extends ScriptModule {}
object ci  extends ScriptModule {}
