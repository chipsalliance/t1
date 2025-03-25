// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreader

import org.llvm.circt.scalalib.emit.capi.given_DialectHandleApi
import org.llvm.circt.scalalib.firrtl.capi.given_DialectHandleApi
import org.llvm.circt.scalalib.om.capi.{*, given}
import org.llvm.mlir.scalalib.{Module as MlirModule, ModuleApi as MlirModuleApi, *, given}

import scala.util.chaining.*
import java.lang.foreign.Arena

trait OMReader(val mlirbc: Array[Byte], val topName: String):
  protected val arena = Arena.ofConfined()
  given Arena         = arena

  protected val context = summon[ContextApi].contextCreate.tap(ctx =>
    ctx.loadOmDialect()
    ctx.loadFirrtlDialect()
    ctx.loadEmitDialect()
    ctx.allowUnregisteredDialects(true)
  )
  given Context         = context

  protected val module    = summon[MlirModuleApi].moduleCreateParse(mlirbc)
  protected val evaluator = summon[EvaluatorApi].evaluatorNew(module)
  protected val topClass  = evaluator.instantiate(topName, summon[EvaluatorValueApi].basePathGetEmpty)

end OMReader
