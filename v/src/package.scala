import chisel3.{Bool, UInt}
import chisel3.util.experimental.decode.decoder

package object v {
  def csa32(s: UInt, c: UInt, a: UInt): (UInt, UInt) = {
    val xor = s ^ c
    val so = xor ^ a
    val co = (xor & a) | (s & c)
    (so, co)
  }

  def toBinary(i: Int, digits: Int = 3): String = {
    String.format("b%" + digits + "s", i.toBinaryString).replace(' ', '0')
  }

  def bankSelect(vs: UInt, eew:UInt, groupIndex: UInt, readValid: Bool): UInt = {
    decoder.qmc(readValid ## eew(1,0) ## vs(1,0) ## groupIndex(1,0), TableGenerator.BankEnableTable.res)
  }
}
