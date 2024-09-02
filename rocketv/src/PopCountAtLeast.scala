// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.util.PopCount

// TODO: upstream these utilities
object PopCountAtLeast {
  private def two(x: UInt):   (Bool, Bool) = x.getWidth match {
    case 1 => (x.asBool, false.B)
    case n =>
      val half                 = x.getWidth / 2
      val (leftOne, leftTwo)   = two(x(half - 1, 0))
      val (rightOne, rightTwo) = two(x(x.getWidth - 1, half))
      (leftOne || rightOne, leftTwo || rightTwo || (leftOne && rightOne))
  }
  def apply(x: UInt, n: Int): Bool         = n match {
    case 0 => true.B
    case 1 => x.orR
    case 2 => two(x)._2
    case 3 => PopCount(x) >= n.U
  }
}
