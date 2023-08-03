import chisel3._
import chisel3.util.experimental.decode.decoder
import chisel3.util._
import tilelink.{TLBundleParameter, TLChannelD}

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

  def bankSelect(vs: UInt, eew: UInt, groupIndex: UInt, readValid: Bool): UInt = {
    decoder.qmc(readValid ## eew(1, 0) ## vs(1, 0) ## groupIndex(1, 0), TableGenerator.BankEnableTable.res)
  }

  def instIndexL(a: UInt, b: UInt): Bool = {
    require(a.getWidth == b.getWidth)
    a === b || ((a(a.getWidth - 2, 0) < b(b.getWidth - 2, 0)) ^ a(a.getWidth - 1) ^ b(b.getWidth - 1))
  }

  def ffo(input: UInt): UInt = {
    ((~(scanLeftOr(input) << 1)).asUInt & input)(input.getWidth - 1, 0)
  }

  def maskAnd(mask: Bool, data: Data): Data = {
    Mux(mask, data, 0.U.asTypeOf(data))
  }

  def indexToOH(index: UInt, chainingSize: Int): UInt = {
    UIntToOH(index(log2Ceil(chainingSize) - 1, 0))
  }

  def ohCheck(lastReport: UInt, index: UInt, chainingSize: Int): Bool = {
    (indexToOH(index, chainingSize) & lastReport).orR
  }

  def firstlastHelper(burstSize: Int, param: TLBundleParameter)
                     (bits: TLChannelD, fire: Bool): (Bool, Bool, Bool, UInt) = {
    val bustSizeForData: UInt = (~(-1.S(log2Up(burstSize).W) << bits.size >> log2Ceil(param.d.dataWidth / 8))).asUInt
    val maxBustIndex: UInt = Mux(bits.opcode(0), bustSizeForData, 0.U)
    val counter = RegInit(0.U(log2Up(burstSize).W))
    val counter1 = counter + 1.U
    val first = counter === 0.U
    val last = counter === maxBustIndex
    val done = last && fire
    when(fire) {
      counter := Mux(first, 0.U, counter1)
    }
    (first, last, done, counter)
  }

  def multiShifter(right: Boolean, multiSize: Int)(data: UInt, shifterSize: UInt): UInt = {
    VecInit(data.asBools.grouped(multiSize).toSeq.transpose.map { dataGroup =>
      if (right) {
        (VecInit(dataGroup).asUInt >> shifterSize).asBools
      } else {
        (VecInit(dataGroup).asUInt << shifterSize).asBools
      }
    }.transpose.map(VecInit(_).asUInt)).asUInt
  }

  def cutUInt(data: UInt, width: Int): Vec[UInt] = {
    require(data.getWidth % width == 0)
    VecInit(Seq.tabulate(data.getWidth / width) { groupIndex =>
      data(groupIndex * width, groupIndex * width + width - 1)
    })
  }
}
