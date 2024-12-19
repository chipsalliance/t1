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
  val source1:        UInt = UInt(parameter.datapathWidth.W)
  val mask:           UInt = UInt(parameter.datapathWidth.W)
  val source2:        UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val groupCounter:   UInt = UInt(parameter.laneParam.groupNumberBits.W)
  val ffoInput:       UInt = UInt(parameter.laneNumber.W)
  val validInput:     UInt = UInt(parameter.laneNumber.W)
  val lastCompress:   Bool = Bool()
}

class CompressOutput(parameter: T1Parameter) extends Bundle {
  val data:          UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val mask:          UInt = UInt((parameter.laneNumber * parameter.datapathWidth / 8).W)
  val groupCounter:  UInt = UInt(parameter.laneParam.groupNumberBits.W)
  val ffoOutput:     UInt = UInt(parameter.laneNumber.W)
  val compressValid: Bool = Bool()
}

class MaskCompress(parameter: T1Parameter) extends Module {
  val in:             ValidIO[CompressInput] = IO(Flipped(Valid(new CompressInput(parameter))))
  val out:            CompressOutput         = IO(Output(new CompressOutput(parameter)))
  val newInstruction: Bool                   = IO(Input(Bool()))
  val ffoInstruction: Bool                   = IO(Input(Bool()))
  val writeData:      UInt                   = IO(Output(UInt(parameter.xLen.W)))

  val maskSize: Int = parameter.laneNumber * parameter.datapathWidth / 8

  val compress = in.bits.uop === "b001".U
  val viota    = in.bits.uop === "b000".U
  val mv       = in.bits.uop === "b010".U
  val mvRd     = in.bits.uop === "b011".U
  val writeRD  = in.bits.uop === BitPat("b?11")
  val ffoType  = in.bits.uop === BitPat("b11?")

  val outWire: CompressOutput = Wire(new CompressOutput(parameter))

  val eew1H:           UInt      = UIntToOH(in.bits.eew)(2, 0)
  val compressInit:    UInt      = RegInit(0.U(log2Ceil(parameter.vLen).W))
  val compressVec:     Vec[UInt] = Wire(Vec(maskSize, UInt(compressInit.getWidth.W)))
  val compressMaskVec: Seq[Bool] = changeUIntSize(in.bits.source1 & in.bits.mask, maskSize).asBools
  val compressCount:   UInt      = compressMaskVec.zipWithIndex.foldLeft(compressInit) { case (pre, (mask, index)) =>
    compressVec(index) := pre
    pre + mask
  }

  // ffo
  val ffoIndex: UInt = RegInit(0.U(parameter.datapathWidth.W))
  val ffoValid: Bool = RegInit(false.B)
  writeData := ffoIndex

  when(newInstruction) {
    compressInit := 0.U
  }

  val countSplit: Seq[(Bool, UInt)] = Seq(0, 1, 2).map { sewInt =>
    val dataByte          = 1 << sewInt
    val elementSizePerSet = parameter.laneNumber * parameter.datapathWidth / 8 / dataByte
    val countWidth        = log2Ceil(elementSizePerSet)
    val compressDeqValid  = (compressCount >> countWidth).asUInt.orR
    val compressUpdate    = changeUIntSize(compressCount, countWidth)
    (compressDeqValid, compressUpdate)
  }

  val compressDeqValid:    Bool = Mux1H(eew1H, countSplit.map(_._1)) || !compress
  val compressCountSelect: UInt = Mux1H(eew1H, countSplit.map(_._2))

  when(in.fire) {
    when(viota) {
      compressInit := compressCount
    }.otherwise {
      // count update compress
      compressInit := compressCountSelect
    }
  }

  val viotaResult: UInt = Mux1H(
    eew1H,
    Seq(1, 2, 4).map { eew =>
      VecInit(Seq.tabulate(parameter.laneNumber) { index =>
        // data width: eew * 8, data path 32, need [4 / eew] element
        val dataSize = 4 / eew
        val res: Seq[UInt] = Seq.tabulate(dataSize) { i =>
          changeUIntSize(compressVec(dataSize * index + i), eew * 8)
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
          val maskIndex: Int = (parameter.datapathWidth - 1).min(dataSize * index + i)
          Fill(eew, in.bits.mask(maskIndex))
        }
        // 4 bit mask
        VecInit(res).asUInt
      }).asUInt
    }
  )

  val tailCount: UInt = {
    val minElementSizePerSet = parameter.laneNumber * parameter.datapathWidth / 8
    val maxCountWidth        = log2Ceil(minElementSizePerSet)
    changeUIntSize(compressInit, maxCountWidth)
  }

  val compressDataReg = RegInit(0.U((parameter.laneNumber * parameter.datapathWidth).W))
  val compressTailValid:       Bool = RegInit(false.B)
  val compressWriteGroupCount: UInt = RegInit(0.U(parameter.laneParam.groupNumberBits.W))
  val compressDataVec = Seq(1, 2, 4).map { dataByte =>
    val dataBit           = dataByte * 8
    val elementSizePerSet = parameter.laneNumber * parameter.datapathWidth / 8 / dataByte
    VecInit(Seq.tabulate(elementSizePerSet * 2) { index =>
      val hitReq        =
        Seq.tabulate(elementSizePerSet)(maskIndex => compressMaskVec(maskIndex) && compressVec(maskIndex) === index.U)
      val selectReqData = Mux1H(
        hitReq,
        cutUInt(in.bits.source2, dataBit)
      )
      if (index < elementSizePerSet) {
        val useTail  = index.U < tailCount
        val tailData = cutUInt(compressDataReg, dataBit)(index)
        Mux(useTail, tailData, selectReqData)
      } else {
        selectReqData
      }
    }).asUInt
  }
  val compressResult: UInt = Mux1H(eew1H, compressDataVec)
  val lastCompressEnq: Bool = in.fire && in.bits.lastCompress
  when(newInstruction || lastCompressEnq || outWire.compressValid) {
    compressTailValid := lastCompressEnq && compress
  }

  when(newInstruction || outWire.compressValid) {
    compressWriteGroupCount := Mux(newInstruction, 0.U, compressWriteGroupCount + 1.U)
  }

  val splitCompressResult: Vec[UInt] = cutUIntBySize(compressResult, 2)
  when(in.fire) {
    compressDataReg := Mux(compressDeqValid, splitCompressResult(1), splitCompressResult(0))
  }

  // todo: connect & update compressInit
  val compressMask = Wire(UInt(out.mask.getWidth.W))
  // todo: optimization
  val compressTailMask: UInt = Mux1H(
    eew1H,
    Seq(0, 1, 2).map { sewInt =>
      val dataByte          = 1 << sewInt
      val elementSizePerSet = parameter.laneNumber * parameter.datapathWidth / 8 / dataByte
      VecInit(Seq.tabulate(elementSizePerSet) { elementIndex =>
        val elementValid = elementIndex.U < tailCount
        val elementMask  = Fill(dataByte, elementValid)
        elementMask
      }).asUInt
    }
  )
  compressMask := Mux(compressTailValid, compressTailMask, (-1.S(out.mask.getWidth.W)).asUInt)

  val mvMask = Mux1H(eew1H, Seq(1.U, 3.U, 15.U))
  val mvData = in.bits.readFromScalar

  val ffoMask: UInt = FillInterleaved(parameter.datapathWidth / 8, in.bits.validInput)

  outWire.data := Mux1H(
    Seq(
      compress -> compressResult,
      viota    -> viotaResult,
      mv       -> mvData,
      ffoType  -> in.bits.source2
    )
  )

  // todo: compressMask
  outWire.mask := Mux1H(
    Seq(
      compress -> compressMask,
      viota    -> viotaMask,
      mv       -> mvMask,
      ffoType  -> ffoMask
    )
  )

  // todo
  outWire.compressValid := (compressTailValid || (compressDeqValid && in.fire)) && !writeRD
  outWire.groupCounter  := Mux(compress, compressWriteGroupCount, in.bits.groupCounter)

  when(newInstruction && ffoInstruction) {
    ffoIndex := -1.S(parameter.datapathWidth.W).asUInt
    ffoValid := false.B
  }
  val firstLane:      UInt = ffo(in.bits.ffoInput)
  val firstLaneIndex: UInt = OHToUInt(firstLane)(log2Ceil(parameter.laneNumber) - 1, 0)

  val source1SigExtend: UInt = Mux1H(
    eew1H,
    Seq(1, 2, 4).map { byteSize =>
      val dataBits = byteSize * 8
      if (parameter.xLen > dataBits) {
        Fill(parameter.xLen - dataBits, in.bits.source1(dataBits - 1)) ## in.bits.source1(dataBits - 1, 0)
      } else {
        in.bits.source1
      }
    }
  )

  /** for find first one, need to tell the lane with higher index `1` . */
  val completedLeftOr: UInt = (scanLeftOr(in.bits.ffoInput) << 1).asUInt(parameter.laneNumber - 1, 0)
  when(in.fire && in.bits.ffoInput.orR && ffoType) {
    ffoValid := true.B
    when(!ffoValid) {
      ffoIndex := Mux1H(
        firstLane,
        // 3: firstLaneIndex.width
        cutUInt(in.bits.source2, parameter.datapathWidth).map(i =>
          i(parameter.xLen - 1 - 3, 5) ## firstLaneIndex ## i(4, 0)
        )
      )
    }
  }.elsewhen(mvRd) {
    ffoIndex := source1SigExtend
  }
  outWire.ffoOutput := completedLeftOr | Fill(parameter.laneNumber, ffoValid)
  out := RegNext(outWire, 0.U.asTypeOf(out))
}
