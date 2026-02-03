// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.dwbb.wrapper.DW_pulse_sync

import chisel3.experimental.IntParam
import org.chipsalliance.dwbb.interface.DW_pulse_sync._
import org.chipsalliance.dwbb.wrapper.WrapperModule

import scala.collection.immutable.SeqMap

class DW_pulse_sync(parameter: Parameter)
    extends WrapperModule[Interface, Parameter](
      new Interface(parameter),
      parameter,
      p =>
        SeqMap(
          "reg_event" -> IntParam(if (p.regEvent) 1 else 0),
          "f_sync_type" -> IntParam(p.fSyncType match {
            case "single_clock" => 0
            case "neg_pos_sync" => 1
            case "pos_pos_sync" => 2
            case "3_pos_sync"   => 3
            case "4_pos_sync"   => 4
          }),
          "tst_mode" -> IntParam(p.tstMode match {
            case "no_latch" => 0
            case "ff"       => 1
            case "latch"    => 2
          }),
          "verif_en" -> IntParam(p.verifEn),
          "pulse_mode" -> IntParam(p.pulseMode match {
            case "single_src_domain_pulse"          => 0
            case "rising_dest_domain_pulse"         => 1
            case "falling_dest_domain_pulse"        => 2
            case "rising_falling_dest_domain_pulse" => 3
          })
        )
    )
