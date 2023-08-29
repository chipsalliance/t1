// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package tests.elaborate

import chisel3._
import v.V
class CoverModule(dut: V) extends Module with TapModule {
  dut.laneVec.zipWithIndex.foreach { case (lane, index) =>
    cover(tap(lane.vrf.write.valid)).suggestName(s"LANE${index}_VRF_WRITE_VALID")
  }
  done()
}
