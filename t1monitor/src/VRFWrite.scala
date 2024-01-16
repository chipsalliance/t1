// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.montior

import chisel3._
import chisel3.util.experimental.BoringUtils.tapAndRead
import org.chipsalliance.t1.rtl.V

class VRFWrite(dut: V) {
  // VRFs viewed as SEW=datapath width, lmul = 1.
  // each VRF has VLEN bits, group them into 32 bits level elements.
  val vrf = Seq.fill(32)(Seq.fill(dut.parameter.vLen / dut.parameter.datapathWidth)(Wire(UInt(dut.parameter.datapathWidth.W))))
  val valids = Seq.fill(32)(Seq.fill(dut.parameter.vLen / dut.parameter.datapathWidth)(Wire(Bool())))
  // statically extract VRF write via comparing addresses.
  // addr -> reg mapping
  // vlen total elements
  // row = lane.size * vrf.banks
  // memory depth = vlen / row

  val memoryDepth = dut.parameter.vLen / dut.parameter.laneNumber / dut.parameter.portFactor
  dut.laneVec.zipWithIndex.map { case (lane, laneIdx) =>
    lane.vrf.rfVec.zipWithIndex.map { case (bank, bankIdx) =>
      Seq.tabulate(memoryDepth)(memIdx => {
        val elementIdxForInst = memIdx * dut.parameter.laneNumber * dut.parameter.portFactor + laneIdx * dut.parameter.portFactor + bankIdx
        val blockSizePerRegister = dut.parameter.vLen / dut.parameter.datapathWidth
        val vrfIdx = elementIdxForInst / blockSizePerRegister
        val elementIdx = elementIdxForInst % blockSizePerRegister
        vrf(vrfIdx)(elementIdx) := tapAndRead(bank.writePort.bits.data)
        valids(vrfIdx)(elementIdx) := tapAndRead(bank.writePort.fire) && (tapAndRead(bank.writePort.bits.addr) === memIdx.U)
      })
    }
  }
}
