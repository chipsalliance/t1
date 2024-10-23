// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._

class BitLevelWriteRequest(parameter: T1Parameter) extends Bundle {
  val data:         UInt = UInt(parameter.datapathWidth.W)
  val bitMask:      UInt = UInt(parameter.datapathWidth.W)
  val mask:         UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.laneParam.groupNumberBits.W)
}

class BitLevelMaskWrite(parameter: T1Parameter) extends Module {
  // todo
  val readVRFLatency: Int = 2

  val needWAR: Bool = IO(Input(Bool()))
  val vd:      UInt = IO(Input(UInt(5.W)))

  val in: Seq[DecoupledIO[BitLevelWriteRequest]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Flipped(Decoupled(new BitLevelWriteRequest(parameter))))
  }

  val out: Seq[DecoupledIO[MaskUnitExeResponse]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Decoupled(new MaskUnitExeResponse(parameter.laneParam)))
  }

  val readChannel: Seq[DecoupledIO[VRFReadRequest]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(
      Decoupled(
        new VRFReadRequest(
          parameter.vrfParam.regNumBits,
          parameter.laneParam.vrfOffsetBits,
          parameter.instructionIndexBits
        )
      )
    )
  }

  val readResult: Seq[UInt] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Input(UInt(parameter.datapathWidth.W)))
  }

  val stageClear: Bool = IO(Output(Bool()))

  val stageClearVec: Seq[Bool] = in.zipWithIndex.map { case (req, index) =>
    val reqQueue = Queue(req, 4)
    val readPort = readChannel(index)
    val readData = readResult(index)
    val res      = out(index)

    val WaitReadQueue: Queue[BitLevelWriteRequest] = Module(new Queue(chiselTypeOf(req.bits), readVRFLatency))
    val readReady = !needWAR || readPort.ready
    WaitReadQueue.io.enq.valid := reqQueue.valid && readReady
    WaitReadQueue.io.enq.bits  := reqQueue.bits
    reqQueue.ready             := WaitReadQueue.io.enq.ready && readReady

    readPort.valid       := reqQueue.valid && needWAR
    readPort.bits        := DontCare
    readPort.bits.vs     := vd + (reqQueue.bits.groupCounter >> readPort.bits.offset.getWidth).asUInt
    readPort.bits.offset := changeUIntSize(reqQueue.bits.groupCounter, readPort.bits.offset.getWidth)

    val readValidPipe   = Pipe(readPort.fire, false.B, readVRFLatency).valid
    val readResultValid = !needWAR || readValidPipe

    val WARData = (WaitReadQueue.io.deq.bits.data & WaitReadQueue.io.deq.bits.bitMask) |
      (readData & (~WaitReadQueue.io.deq.bits.bitMask).asUInt)

    res.valid                       := WaitReadQueue.io.deq.valid && readResultValid
    WaitReadQueue.io.deq.ready      := res.ready && readResultValid
    res.bits                        := DontCare
    res.bits.writeData.data         := Mux(needWAR, WARData, WaitReadQueue.io.deq.bits.data)
    res.bits.writeData.mask         := maskEnable(!needWAR, WaitReadQueue.io.deq.bits.mask)
    res.bits.writeData.groupCounter := WaitReadQueue.io.deq.bits.groupCounter

    // valid token
    val counter       = RegInit(0.U(3.W))
    val counterChange = Mux(req.fire, 1.U(3.W), 7.U(3.W))
    when(req.fire ^ res.fire) {
      counter := counter + counterChange
    }
    counter === 0.U
  }
  stageClear := stageClearVec.reduce(_ && _)
}
