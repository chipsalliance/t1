// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper

import chisel3.experimental.{
  Param,
  SerializableModule,
  SerializableModuleParameter
}
import chisel3.{Data, FixedIOExtModule}

abstract class WrapperModule[I <: Data, P <: SerializableModuleParameter](
    ioGenerator: I,
    final val parameter: P,
    final val parameterMap: P => Map[String, Param]
) extends FixedIOExtModule[I](ioGenerator, parameterMap(parameter))
    with SerializableModule[P]
