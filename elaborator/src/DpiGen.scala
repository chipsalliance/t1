package tests.elaborate

import chisel3.SpecifiedDirection
import chisel3.experimental.ExtModule
import chisel3.internal.firrtl.{KnownWidth, UnknownWidth}
import chisel3.probe.HasExtModuleDefine
import chisel3.util.HasExtModuleInline

import scala.collection.immutable.SeqMap

case class DPIModuleParameter(
                               isImport: Boolean,
                               dpiName: String,
                               references: SeqMap[String, chisel3.Element] = SeqMap.empty
                             )

trait DPIModule
  extends ExtModule
    with HasExtModuleInline
    with HasExtModuleDefine {
  val dpiModuleParameter: DPIModuleParameter
  val body: String
  override def desiredName = dpiModuleParameter.dpiName
  // return binding function and probe signals
  val r = dpiModuleParameter.references.map {
    case (name, element) =>
      val direction = chisel3.reflect.DataMirror.specifiedDirectionOf(element) match {
        case SpecifiedDirection.Unspecified => throw new Exception(s"$desiredName.$name direction unknown")
        case SpecifiedDirection.Output => "output"
        case SpecifiedDirection.Input => "input"
        case SpecifiedDirection.Flip => throw new Exception(s"$desiredName.$name direction flip")
      }
      val width = chisel3.reflect.DataMirror.widthOf(element) match {
        case UnknownWidth() => throw new Exception(s"$desiredName.$name width unknown")
        case KnownWidth(value) => value
      }
      val tpe = s"bit[${width - 1}:0]"
      val localDefinition = s"logic $tpe $name"
      val functionParameter = s"$direction $tpe $name"
      name -> (localDefinition, functionParameter)
  }
  val localDefinition: String = r.view.mapValues(_._1).mkString(";\n")
  val functionParameter: String = r.view.mapValues(_._2).mkString(",\n")
  val defines = dpiModuleParameter.references.map{ case (k, v) => k -> define(v, Seq(dpiModuleParameter.dpiName, dpiModuleParameter.dpiName, name)) }
  setInline(
      s"${dpiModuleParameter.dpiName}.sv",
    s"""module ${dpiModuleParameter.dpiName};
       |$localDefinition
       |${if (dpiModuleParameter.isImport) "import \"DPI-C\" function void" else "export \"DPI-C\" function"} ${dpiModuleParameter.dpiName}($functionParameter);
       |$body
       |endmodule
       |""".stripMargin
  )
}
