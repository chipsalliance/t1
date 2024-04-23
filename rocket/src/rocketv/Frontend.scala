// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, Instantiate}

case class FrontendParameter() extends SerializableModuleParameter {
  val icacheParameter: ICacheParameter = ???
  val tlbParameter: TLBParameter = ???
  val btbParameter: Option[BTBParameter] = ???
}

class FrontendInterface(parameter: FrontendParameter) extends Bundle {

}

class Frontend(val parameter: FrontendParameter)
  extends FixedIORawModule(new FrontendInterface(parameter))
    with SerializableModule[FrontendParameter] {
  val icache: Instance[ICache] = Instantiate(new ICache(parameter.icacheParameter))
  val tlb: Instance[TLB] = Instantiate(new TLB(parameter.tlbParameter))
  val btb: Option[Instance[BTB]] = parameter.btbParameter.map(p => Instantiate(new BTB(p)))
}
