package v

import chisel3._
import chisel3.util._

case class RFParam(depth: Int, readPort: Int = 2, width: Int = 8) {
  val indexBits: Int = log2Ceil(depth)
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
  // TODO: add read enable?
  val readPorts: Vec[RegFileReadPort] = IO(Vec(param.readPort, new RegFileReadPort(param)))
  val writePort: ValidIO[RegFileWritePort] = IO(Flipped(Valid(new RegFileWritePort(param))))

  val rf: SyncReadMem[UInt] = SyncReadMem(param.depth, UInt(param.width.W))

  readPorts.foreach(p => p.data := rf(p.addr))

  when(writePort.valid) {
    rf(writePort.bits.addr) := writePort.bits.data
  }
}
