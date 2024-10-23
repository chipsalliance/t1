// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._

class ExtendInput(parameter: T1Parameter) extends Bundle {
  val eew:          UInt = UInt(2.W)
  val uop:          UInt = UInt(3.W)
  val source2:      UInt = UInt((parameter.laneNumber * parameter.datapathWidth).W)
  val groupCounter: UInt = UInt(parameter.laneParam.groupNumberBits.W)
}

class MaskExtend(parameter: T1Parameter) extends Module {
  val in:  ExtendInput = IO(Input(new ExtendInput(parameter)))
  val out: UInt        = IO(Output(UInt((parameter.laneNumber * parameter.datapathWidth).W)))

  val eew1H: UInt = UIntToOH(in.eew)(2, 0)

  val isMaskDestination:     Bool = !in.uop(2, 0).orR
  val maskDestinationResult: UInt = Mux1H(
    eew1H,
    Seq(4, 2, 1).map { groupSize =>
      VecInit(
        cutUInt(in.source2, groupSize).grouped(parameter.laneNumber).toSeq.transpose.map(a => VecInit(a).asUInt)
      ).asUInt
    }
  )

  // extend
  val sign:        Bool = in.uop(0)
  // extend ratio
  // todo: Currently only vf2 and vf4
  val extendRatio: Bool = in.uop(1)

  // select source2
  // extendRatio: 0 -> vf2; 1-> vf4
  val source2: UInt = Mux(
    extendRatio,
    Mux1H(
      UIntToOH(in.groupCounter(1, 0)),
      cutUInt(in.source2, parameter.laneNumber * parameter.datapathWidth / 4)
    ),
    Mux1H(
      UIntToOH(in.groupCounter(0)),
      cutUInt(in.source2, parameter.laneNumber * parameter.datapathWidth / 2)
    )
  )

  val extendResult: UInt = Mux1H(
    eew1H(2, 1),
    Seq(2, 4).map { dataWidth =>
      Mux1H(
        UIntToOH(extendRatio),
        Seq(2, 4).map { ratio =>
          val resWidth    = dataWidth * 8
          val sourceWidth = resWidth / ratio
          VecInit(cutUInt(source2, sourceWidth).map { sourceData =>
            Fill(resWidth - sourceWidth, sourceData(sourceWidth - 1) && sign) ## sourceData
          }).asUInt
        }
      )
    }
  )

  out := Mux(isMaskDestination, maskDestinationResult, extendResult)
}
