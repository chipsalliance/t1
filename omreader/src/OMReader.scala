// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreader

import org.llvm.circt.scalalib.capi.dialect.emit.{given_DialectApi, DialectApi as EmitDialectApi}
import org.llvm.circt.scalalib.capi.dialect.firrtl.{given_DialectApi, DialectApi as FirrtlDialectApi}
import org.llvm.circt.scalalib.capi.dialect.hw.{given_AttributeApi, given_DialectApi, DialectApi as HWDialectApi}
import org.llvm.circt.scalalib.capi.dialect.om.{*, given}
import org.llvm.mlir.scalalib.capi.ir.{Module as MlirModule, ModuleApi as MlirModuleApi, *, given}

import scala.util.chaining.*
import java.lang.foreign.Arena

trait OMReader(val mlirbc: Array[Byte], val topName: String):
  protected val arena = Arena.ofConfined()
  given Arena         = arena

  protected val context = summon[ContextApi].contextCreate
  given Context         = context
  context.allowUnregisteredDialects(true)
  summon[EmitDialectApi].loadDialect
  summon[FirrtlDialectApi].loadDialect
  summon[HWDialectApi].loadDialect

  protected val module    = summon[MlirModuleApi].moduleCreateParse(mlirbc)
  protected val evaluator = summon[EvaluatorApi].evaluatorNew(module)
  protected val topClass  = evaluator.instantiate(topName, summon[EvaluatorApi].basePathGetEmpty)

end OMReader
