package org.chipsalliance.t1.omreaderlib

import scala.reflect.runtime.universe._
import chisel3.panamalib.option._
import chisel3.panamaom._
import chisel3.panamaconverter.PanamaCIRCTConverter

object OMReader {
  def fromFile(mlirbcFile: os.Path): OMReader = {
    new OMReader(os.read.bytes(mlirbcFile))
  }

  def fromBytes(mlirbc: Array[Byte]): OMReader = {
    new OMReader(mlirbc)
  }
}

class OMReader private(mlirbc: Array[Byte]) {
  private val cvt = PanamaCIRCTConverter.newWithMlirBc(mlirbc)
  private val om = cvt.om()
  private val evaluator = om.evaluator()

  def t1Reader: T1Reader = new T1Reader(evaluator, om.newBasePathEmpty)
}

class T1Reader private[omreaderlib](evaluator: PanamaCIRCTOMEvaluator, basePath: PanamaCIRCTOMEvaluatorValueBasePath) {
  val (entry, isSubsystem) = {
    evaluator.instantiate("T1Subsystem_Class", Seq(basePath)) match {
      case Some(subsystem) => (subsystem, true)
      case None => (evaluator.instantiate("T1_Class", Seq(basePath)).get, false)
    }
  }
  private val t1 = {
    if (isSubsystem) {
      entry
        .field("om").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
        .field("t1").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    }
    else {
      entry
        .field("om").asInstanceOf[PanamaCIRCTOMEvaluatorValueObject]
    }
  }

  def vlen: Long = t1.field("vlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer
  def dlen: Long = t1.field("dlen").asInstanceOf[PanamaCIRCTOMEvaluatorValuePrimitiveInteger].integer

  def dumpMethods(): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader).reflect(this)
    val methods = typeOf[T1Reader].decls.toList.filter(
        m => m.isPublic && m.isMethod && !m.isConstructor && !m.asMethod.isGetter
    )
    methods.foreach(method => {
      if (!method.name.toString.startsWith("dump")) {
        val value = mirror.reflectMethod(method.asMethod)()
        println(s"${method.name} = $value")
      }
    })
  }

  def dumpAll(): Unit = {
    entry.foreachField((name, value) => println(s".$name => $value"))
  }
}
