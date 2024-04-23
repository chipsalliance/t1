// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.experimental.hierarchy.{Instance, Instantiate}

case class HellaCacheParameter() extends SerializableModuleParameter {
  val tlbParameter: TLBParameter = ???
  val pmaCheckerParameter: PMACheckerParameter = ???
  val arbiterParameter: HellaCacheArbiterParameter = ???
  val dataArrayParameter: DCacheDataArrayParameter = ???
  val amoaluParameter: AMOALUParameter = ???
}

class HellaCacheInterface(parameter: HellaCacheParameter) extends Bundle {

}

class HellaCache(val parameter: HellaCacheParameter)
  extends FixedIORawModule(new HellaCacheInterface(parameter))
    with SerializableModule[HellaCacheParameter] {
  val tlb: Instance[TLB] = Instantiate(new TLB(parameter.tlbParameter))
  val pmaChecker: Instance[PMAChecker] = Instantiate(new PMAChecker(parameter.pmaCheckerParameter))
  val arbiter: Instance[HellaCacheArbiter] = Instantiate(new HellaCacheArbiter(parameter.arbiterParameter))
  val dataArray: Instance[DCacheDataArray] = Instantiate(new DCacheDataArray(parameter.dataArrayParameter))
  val amoalu: Instance[AMOALU] = Instantiate(new AMOALU(parameter.amoaluParameter))
}
