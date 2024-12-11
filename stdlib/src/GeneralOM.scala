package org.chipsalliance.stdlib

import chisel3.experimental.hierarchy.{instantiable, public}
import chisel3.{Input, Output}
import chisel3.experimental.{SerializableModule, SerializableModuleGenerator, SerializableModuleParameter}
import chisel3.properties.{AnyClassType, Class, ClassType, Path, Property}

import scala.reflect.runtime.universe._

@instantiable
abstract class GeneralOM[P <: SerializableModuleParameter, M <: SerializableModule[P]](
  val parameter:        P
)(
  implicit parameterRW: upickle.default.ReadWriter[P],
  mTag:                 TypeTag[M],
  pTag:                 TypeTag[P])
    extends Class {
  def hasSram:   Boolean = false
  def hasRetime: Boolean = false

  /** SerializableModuleGenerator in base64. */
  val generator: Property[String]                 = IO(Output(Property[String]))
  val retime:    Option[Property[Path]]           = Option.when(hasRetime)(IO(Output(Property[Path])))
  val srams:     Option[Property[Seq[ClassType]]] = Option.when(hasSram)(IO(Output(Property[Seq[AnyClassType]]())))

  /** Usage:
    * {{{
    *   omInstance.retimeIn.get := Property(Path(clock))
    * }}}
    */
  @public
  val retimeIn: Option[Property[Path]] = Option.when(hasRetime)(IO(Input(Property[Path])))

  /** Usage:
    * {{{
    *   omInstance.sramIn.get := Property((srams: Seq[SRAMInterface[_]]).map(_.description.get.asAnyClassType))
    * }}}
    */
  @public
  val sramsIn: Option[Property[Seq[ClassType]]] = Option.when(hasSram)(IO(Input(Property[Seq[AnyClassType]]())))

  generator := Property(
    java.util.Base64.getEncoder.encodeToString(
      upickle.default
        .write(
          SerializableModuleGenerator(
            runtimeMirror(getClass.getClassLoader)
              .runtimeClass(typeOf[M].typeSymbol.asClass)
              .asInstanceOf[Predef.Class[M]],
            parameter
          )
        )(SerializableModuleGenerator.rw[P, M])
        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
    )
  )

  srams.zip(sramsIn).foreach { case (l, r) => l := r }
  retime.zip(retimeIn).foreach { case (l, r) => l := r }
}
