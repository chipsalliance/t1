// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package v

import chisel3._
import chisel3.util._

case class RFParam(depth: Int, readPort: Int = 2, width: Int = 32) {
  val indexBits: Int = log2Ceil(depth)
  // todo: 4 bit for ecc
  val memoryWidth: Int = width + 4
}

class RegFileReadPort(param: RFParam) extends Bundle {
  val addr: UInt = Input(UInt(param.indexBits.W))
  val data: UInt = Output(UInt(param.width.W))
}
class RegFileWritePort(param: RFParam) extends Bundle {
  val addr: UInt = UInt(param.indexBits.W)
  val data: UInt = UInt(param.width.W)
}

/** Memory wrapper.
  * It's a dual port SRAM: port0 has 1RW, port1 has 1R.
  * TODO: After [[https://github.com/chipsalliance/chisel/pull/3131]] is merged,
  *       switch to it and get rid of this module.
  */
class RegFile(param: RFParam) extends Module {
  // TODO: change port for matching different SRAM types.
  val readPorts: Vec[RegFileReadPort] = IO(Vec(param.readPort, new RegFileReadPort(param)))
  val writePort: ValidIO[RegFileWritePort] = IO(Flipped(Valid(new RegFileWritePort(param))))

  // in TSN28, we use dual port memory, in the future, we can switch to other ports
  val rf: SRAMInterface[UInt] = SRAM(param.depth, UInt(param.memoryWidth.W), 0, 0, 2)

  rf.readwritePorts.zipWithIndex.foreach { case (memPort, index) =>
    readPorts(index).data := memPort.readData
    memPort.writeData := writePort.bits.data
    // always read
    memPort.enable := true.B
    // only write at last port
    if (index == readPorts.size - 1) {
      memPort.address := Mux(writePort.valid, writePort.bits.addr, readPorts(index).addr)
      memPort.isWrite := writePort.valid
    } else {
      memPort.address := readPorts(index).addr
      memPort.isWrite := false.B
    }
  }
}
