// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._

class MaskUnitReadCrossBar(parameter: T1Parameter) extends Module {
  val input: Seq[DecoupledIO[MaskUnitReadReq]] = Seq.tabulate(parameter.laneNumber) (_ => IO(Flipped(Decoupled(
    new MaskUnitReadReq(parameter)
  ))))
  val output: Seq[DecoupledIO[MaskUnitReadQueue]] = Seq.tabulate(parameter.laneNumber) (_ => IO(Decoupled(
    new MaskUnitReadQueue(parameter)
  )))

  val inputUseLane1H: Vec[UInt] = Wire(Vec(parameter.laneNumber, UInt(parameter.laneNumber.W)))

  input.zipWithIndex.foldLeft(VecInit(output.map(!_.ready)).asUInt) { case (laneOccupied, (req, index)) =>
    val requestReadLane = UIntToOH(req.bits.readLane)
    // read lane free
    val free: Bool = (requestReadLane & (~laneOccupied).asUInt).orR
    req.ready := free
    inputUseLane1H(index) := Mux(req.fire, requestReadLane, 0.U(parameter.laneNumber.W))
    laneOccupied | inputUseLane1H(index)
  }

  output.zipWithIndex.foreach { case (req, index) =>
    val tryToRead: UInt = VecInit(inputUseLane1H.map(_(index))).asUInt
    req.valid := tryToRead.orR
    val selectReq: DecoupledIO[MaskUnitReadReq] = Mux1H(tryToRead, input)
    req.bits.vs := selectReq.bits.vs
    req.bits.offset := selectReq.bits.offset
    req.bits.writeIndex := selectReq.bits.requestIndex
    req.bits.dataOffset := selectReq.bits.dataOffset
  }
}
