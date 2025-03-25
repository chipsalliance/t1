// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.t1.omreader

import me.jiuyang.omlib.{*, given}

trait T1OMReader extends OMReader:

  val top = topClass.toScala

  def json: String

end T1OMReader
