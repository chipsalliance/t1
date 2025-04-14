// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

// magic to hack into generateComponent
package chisel3

import chisel3.experimental.ExtModule
import chisel3.probe._
import chisel3.internal.firrtl.{KnownWidth, UnknownWidth}
import chisel3.util.HasExtModuleInline

import scala.collection.mutable.ArrayBuffer

case class DPIElementLegacy[T <: Data](name: String, output: Boolean, data: T)

case class DPIReferenceLegacy[T <: Data](name: String, ref: T)

abstract class DPIModuleLegacy extends ExtModule with HasExtModuleInline {

  // C Style
  override def desiredName: String = "[A-Z\\d]".r.replaceAllIn(
    super.desiredName,
    { m =>
      (if (m.end(0) == 1) "" else "_") + m.group(0).toLowerCase()
    }
  )

  def dpiIn[T <: Element](name: String, data: T)           = bind(name, true, Input(data))
  def dpiIn[T <: Element](name: String, data: Iterable[T]) = {
    data.zipWithIndex.map { case (d, i) => bind(s"$name$i", true, Input(d)) }
  }
  def dpiIn[T <: Element](name: String, data: Bundle)      = {
    data.elements.map { case (s, d) => s -> bind(s"${name}_$s", true, Input(d)) }
  }

  def dpiOut[T <: Element](name: String, data: T) = bind(name, true, Output(data))

  def dpiTrigger[T <: Element](name: String, data: T) = bind(name, false, Input(data))

  val isImport: Boolean
  val references:    ArrayBuffer[DPIElementLegacy[_]] = scala.collection.mutable.ArrayBuffer.empty[DPIElementLegacy[_]]
  val dpiReferences: ArrayBuffer[DPIElementLegacy[_]] = scala.collection.mutable.ArrayBuffer.empty[DPIElementLegacy[_]]

  def bind[T <: Data](name: String, isDPIArg: Boolean, data: T) = {
    val ref = IO(data).suggestName(name)

    val ele = DPIElementLegacy(
      name,
      chisel3.reflect.DataMirror.directionOf(ref) match {
        case ActualDirection.Empty              => false
        case ActualDirection.Unspecified        => false
        case ActualDirection.Output             => true
        case ActualDirection.Input              => false
        case ActualDirection.Bidirectional(dir) => false
      },
      ref
    )
    require(!references.exists(ele => ele.name == name), s"$name already added.")
    references += ele
    if (isDPIArg) {
      dpiReferences += ele
    }
    DPIReferenceLegacy(name, ref)
  }

  val trigger:    String = ""
  val guard:      String = ""
  val exportBody: String = ""
  // Magic to execute post-hook
  private[chisel3] override def generateComponent() = {
    // return binding function and probe signals
    val localDefinition = "(" + references.map { case DPIElementLegacy(name, _, element) =>
      val output = chisel3.reflect.DataMirror.directionOf(element) match {
        case ActualDirection.Empty              => false
        case ActualDirection.Unspecified        => false
        case ActualDirection.Output             => true
        case ActualDirection.Input              => false
        case ActualDirection.Bidirectional(dir) => false
      }
      val width  = chisel3.reflect.DataMirror.widthOf(element) match {
        case UnknownWidth()    => throw new Exception(s"$desiredName.$name width unknown")
        case KnownWidth(value) => value
      }
      val tpe    = if (width != 1) s"bit[${width - 1}:0] " else ""
      s"${if (output) "output" else "input"} $tpe$name"
    }.mkString(", ") + ")"

    val dpiArg = dpiReferences.map { case DPIElementLegacy(name, output, element) =>
      val direction = if (output) "output " else "input "
      val width     = chisel3.reflect.DataMirror.widthOf(element) match {
        case UnknownWidth()    => throw new Exception(s"$desiredName.$name width unknown")
        case KnownWidth(value) => value
      }
      val tpe       = if (width != 1) s"bit[${width - 1}:0] " else ""
      s"$direction$tpe$name"
    }.mkString(", ")

    setInline(
      s"$desiredName.sv",
      s"""module $desiredName$localDefinition;
         |${if (isImport) s"""import "DPI-C" function void $desiredName($dpiArg);"""
        else s"""export "DPI-C" function $desiredName;"""}
         |${if (isImport) s"""$trigger ${if (guard.nonEmpty) s"if($guard)" else ""} $desiredName(${dpiReferences
            .map(_.name)
            .mkString(", ")});"""
        else ""}
         |${if (!isImport) exportBody else ""}
         |endmodule
         |""".stripMargin.lines().filter(_.nonEmpty).toArray.mkString("\n")
    )
    super.generateComponent()
  }
}
