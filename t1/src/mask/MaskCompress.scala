// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._

class CompressInput(parameter: T1Parameter) extends Bundle {
  val maskType:       Bool = Bool()
  val eew:            UInt = UInt(2.W)
  val uop:            UInt = UInt(3.W)
  val readFromScalar: UInt = UInt(parameter.datapathWidth.W)
  val source1:        UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val source2:        UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val groupCounter:   UInt = UInt(parameter.laneParam.groupNumberBits.W)
  val lastCompress:   Bool = Bool()
}

class CompressOutput(parameter: T1Parameter) extends Bundle {
  val data:          UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val mask:          UInt = UInt((parameter.laneNumber * parameter.datapathWidth / 8).W)
  val compressValid: Bool = Bool()
}

class MaskCompress(parameter: T1Parameter) extends Module {
  val in:             ValidIO[CompressInput] = IO(Flipped(Valid(new CompressInput(parameter))))
  val out:            CompressOutput         = IO(Output(new CompressOutput(parameter)))
  val newInstruction: Bool                   = IO(Input(Bool()))

  val maskSize: Int = parameter.laneNumber * parameter.datapathWidth / 8

  // Source1 alignment
  val source1Aligned: UInt = Wire(UInt(maskSize.W))
  // TODO: Align and align in advance
  source1Aligned := in.bits.source1
  val compress = in.bits.uop === "b001".U
  val viota    = in.bits.uop === "b000".U
  val mv       = in.bits.uop === "b101".U

  val eew1H:           UInt      = UIntToOH(in.bits.eew)(2, 0)
  val compressInit:    UInt      = RegInit(0.U(log2Ceil(parameter.vLen).W))
  val compressVec:     Vec[UInt] = Wire(Vec(maskSize, UInt(compressInit.getWidth.W)))
  val compressMaskVec: Seq[Bool] = source1Aligned.asBools
  val compressCount:   UInt      = compressMaskVec.zipWithIndex.foldLeft(compressInit) { case (pre, (mask, index)) =>
    compressVec(index) := pre
    pre + mask
  }
  // todo: compress update
  compressInit := Mux(newInstruction, 0.U, compressCount)

  val viotaResult: UInt = Mux1H(
    eew1H,
    Seq(1, 2, 4).map { eew =>
      VecInit(Seq.tabulate(parameter.laneNumber) { index =>
        // data width: eew * 8, data path 32, need [4 / eew] element
        val dataSize = 4 / eew
        val res: Seq[UInt] = Seq.tabulate(dataSize) { i =>
          UIntWithSize(compressVec(dataSize * index + i), eew * 8)
        }
        // each data path
        VecInit(res).asUInt
      }).asUInt
    }
  )
  val viotaMask:   UInt = Mux1H(
    eew1H,
    Seq(1, 2, 4).map { eew =>
      VecInit(Seq.tabulate(parameter.laneNumber) { index =>
        val dataSize = 4 / eew
        val res: Seq[UInt] = Seq.tabulate(dataSize) { i =>
          Fill(eew, compressMaskVec(dataSize * index + i))
        }
        // 4 bit mask
        VecInit(res).asUInt
      }).asUInt
    }
  )

  val tailCount       = compressInit
  val compressDataReg = RegInit(0.U((parameter.laneNumber * parameter.datapathWidth).W))
  val compressDataVec = Seq(1, 2, 4).map { eew =>
    VecInit(Seq.tabulate(parameter.laneNumber * 2) { index =>
      val useTail       = index.U < tailCount
      val tailData      = cutUInt(compressDataReg, eew)(index)
      val maskSize      = 4 * parameter.laneNumber / eew
      val hitReq        = Seq.tabulate(maskSize)(maskIndex => compressVec(maskIndex) === index.U)
      val selectReqData = Mux1H(
        hitReq,
        cutUInt(in.bits.source2, eew)
      )
      Mux(useTail, tailData, selectReqData)
    }).asUInt
  }
  val compressResult: UInt = Mux1H(eew1H, compressDataVec)

  // todo: connect & update compressInit
  val compressTailMask = Wire(UInt(out.mask.getWidth.W))
  compressTailMask := DontCare

  val mvMask = Mux1H(eew1H, Seq(1.U, 3.U, 15.U))
  val mvData = in.bits.readFromScalar

  out.data := Mux1H(
    Seq(
      compress -> compressResult,
      viota    -> viotaResult,
      mv       -> mvData
    )
  )

  // todo: compressMask
  out.mask := Mux1H(
    Seq(
      compress -> compressTailMask,
      viota    -> viotaMask,
      mv       -> mvMask
    )
  )

  // todo
  out.compressValid := false.B
}
