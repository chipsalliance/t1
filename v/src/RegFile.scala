package v

import chisel3._
import chisel3.util._

case class RFParam(depth: Int, readPort: Int = 2, width: Int=8) {
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

class RegFile(param: RFParam) extends Module {
  val readPorts: Vec[RegFileReadPort] = IO(Vec(param.readPort, new RegFileReadPort(param)))
  val writePort: ValidIO[RegFileWritePort] = IO(Flipped(Valid(new RegFileWritePort(param))))

  val rf: Mem[UInt] = Mem(param.depth, UInt(param.width.W))

  for (i <- 0 until param.readPort) {
    readPorts(i).data := rf(readPorts(i).addr)
  }

  when(writePort.valid) {
    rf(writePort.bits.addr) := writePort.bits.data
  }
}
