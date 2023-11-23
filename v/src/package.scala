// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

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
    // 只给cache line 用
    val bustSizeForData: UInt = -1.S(log2Ceil(burstSize).W).asUInt
    val counter = RegInit(0.U(log2Up(burstSize).W))
    val counter1 = counter + 1.U
    val first = counter === 0.U
    val last = counter === bustSizeForData
    val done = last && fire
    when(fire) {
      counter := Mux(last, 0.U, counter1)
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
      data(groupIndex * width + width - 1, groupIndex * width)
    })
  }

  def calculateSegmentWriteMask(datapath: Int, laneNumber: Int, elementSizeForOneRegister: Int)
                               (seg1H: UInt, mul1H: UInt, lastWriteOH: UInt): UInt = {
    // not access for register -> na
    val notAccessForRegister = Fill(elementSizeForOneRegister, true.B)
    val writeOHGroup = cutUInt(lastWriteOH, elementSizeForOneRegister)
    require(datapath==32 && laneNumber == 8, "If the parameters are modified, you need to modify them here.")
    // writeOHGroup: d7 ## d6 ## d5 ## d4 ## d3 ## d2 ## d1 ## d0
    // seg1: 2 reg group
    //  mul0    na ## na ## na ## na ## na ## na ## d0 ## d0
    //  mul1    na ## na ## na ## na ## d1 ## d0 ## d1 ## d0
    //  mul2    d3 ## d2 ## d1 ## d0 ## d3 ## d2 ## d1 ## d0
    // seg2: 3 reg group
    //  mul0    na ## na ## na ## na ## na ## d0 ## d0 ## d0
    //  mul1    na ## na ## d1 ## d0 ## d1 ## d0 ## d1 ## d0
    // seg3: 4 reg group
    //  mul0    na ## na ## na ## na ## d0 ## d0 ## d0 ## d0
    //  mul1    d1 ## d0 ## d1 ## d0 ## d1 ## d0 ## d1 ## d0
    // seg4: 5 reg group
    //  mul0    na ## na ## na ## d0 ## d0 ## d0 ## d0 ## d0
    // seg5: 6 reg group
    //  mul0    na ## na ## d0 ## d0 ## d0 ## d0 ## d0 ## d0
    // seg6: 7 reg group
    //  mul0    na ## d0 ## d0 ## d0 ## d0 ## d0 ## d0 ## d0
    // seg7: 8 reg group
    //  mul0    d0 ## d0 ## d0 ## d0 ## d0 ## d0 ## d0 ## d0
    val segMask0 = writeOHGroup(0)
    val segMask1 = Mux(
      ((mul1H(2) || mul1H(1)) && seg1H(1)) || (mul1H(1) && (seg1H(2) || seg1H(3))),
      writeOHGroup(1),
      writeOHGroup(0)
    )
    val segMask2: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        mul1H(1) && seg1H(1),
        true.B
      ),
      Seq(
        writeOHGroup(2),
        notAccessForRegister,
        writeOHGroup(0)
      )
    )
    val segMask3: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        mul1H(1) && (seg1H(1) || seg1H(2) || seg1H(3)),
        (mul1H(0) && seg1H(3)) || (seg1H(7) || seg1H(6) || seg1H(5) || seg1H(4)),
        true.B
      ),
      Seq(
        writeOHGroup(3),
        writeOHGroup(1),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    val segMask4: UInt = Mux(
      (mul1H(2) && seg1H(1)) || (mul1H(1) && (seg1H(2) || seg1H(3))) ||
        (seg1H(7) || seg1H(6) || seg1H(5) || seg1H(4)),
      writeOHGroup(0),
      notAccessForRegister
    )
    val segMask5: UInt = PriorityMux(
      Seq(
        (mul1H(2) && seg1H(1)) || (mul1H(1) && (seg1H(2) || seg1H(3))),
        mul1H(0) && (seg1H(7) || seg1H(6) || seg1H(5)),
        true.B
      ),
      Seq(
        writeOHGroup(1),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    val segMask6: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        (mul1H(1) && seg1H(3)) || (mul1H(0) && (seg1H(7) || seg1H(6))),
        true.B
      ),
      Seq(
        writeOHGroup(2),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    val segMask7: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        mul1H(1) && seg1H(3),
        mul1H(0) && seg1H(7),
        true.B
      ),
      Seq(
        writeOHGroup(3),
        writeOHGroup(1),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    segMask7 ## segMask6 ## segMask5 ## segMask4 ## segMask3 ## segMask2 ## segMask1 ## segMask0
  }
}
