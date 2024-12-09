// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.util._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}

class BitLevelWriteRequest(parameter: T1Parameter) extends Bundle {
  val data:         UInt = UInt(parameter.datapathWidth.W)
  val pipeData:     UInt = UInt(parameter.datapathWidth.W)
  val bitMask:      UInt = UInt(parameter.datapathWidth.W)
  val mask:         UInt = UInt((parameter.datapathWidth / 8).W)
  val groupCounter: UInt = UInt(parameter.laneParam.groupNumberBits.W)
  val ffoByOther:   Bool = Bool()
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

  val readResult: Seq[ValidIO[UInt]] = Seq.tabulate(parameter.laneNumber) { _ =>
    IO(Flipped(Valid(UInt(parameter.datapathWidth.W))))
  }

  val stageClear: Bool = IO(Output(Bool()))

  val stageClearVec: Seq[Bool] = in.zipWithIndex.map { case (req, index) =>
    val reqQueue: QueueIO[BitLevelWriteRequest] = Queue.io(chiselTypeOf(req.bits), 4)
    val readPort = readChannel(index)
    val readData = readResult(index).bits
    val res      = out(index)

    val WaitReadQueue: QueueIO[BitLevelWriteRequest] = Queue.io(chiselTypeOf(req.bits), readVRFLatency)
    val readReady = !needWAR || readPort.ready

    reqQueue.enq <> req
    WaitReadQueue.enq.valid := reqQueue.deq.valid && readReady
    WaitReadQueue.enq.bits  := reqQueue.deq.bits
    reqQueue.deq.ready      := WaitReadQueue.enq.ready && readReady

    readPort.valid       := reqQueue.deq.valid && needWAR && WaitReadQueue.enq.ready
    readPort.bits        := DontCare
    readPort.bits.vs     := vd + (reqQueue.deq.bits.groupCounter >> readPort.bits.offset.getWidth).asUInt
    readPort.bits.offset := changeUIntSize(reqQueue.deq.bits.groupCounter, readPort.bits.offset.getWidth)

    val readValidPipe   = Pipe(readPort.fire, false.B, readVRFLatency).valid && readResult(index).valid
    val readResultValid = !needWAR || readValidPipe

    val WARData = (WaitReadQueue.deq.bits.data & WaitReadQueue.deq.bits.bitMask) |
      (readData & (~WaitReadQueue.deq.bits.bitMask).asUInt)

    res.valid                       := WaitReadQueue.deq.valid && readResultValid
    WaitReadQueue.deq.ready         := res.ready && readResultValid
    res.bits                        := DontCare
    res.bits.pipeData               := WaitReadQueue.deq.bits.pipeData
    res.bits.ffoByOther             := WaitReadQueue.deq.bits.ffoByOther
    res.bits.writeData.data         := Mux(needWAR, WARData, WaitReadQueue.deq.bits.data)
    res.bits.writeData.groupCounter := WaitReadQueue.deq.bits.groupCounter
    res.bits.writeData.mask         := maskEnable(!needWAR, WaitReadQueue.deq.bits.mask)

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
