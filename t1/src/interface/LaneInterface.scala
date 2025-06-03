// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl

import chisel3._
import chisel3.experimental._
import chisel3.util._
import org.chipsalliance.dwbb.stdlib.queue.{Queue, QueueIO}
import org.chipsalliance.t1.rtl.decoder.DecoderParam

case class LaneIFParameter(
                          vLen:                             Int,
                          eLen:                             Int,
                          datapathWidth:                    Int,
                          laneNumber:                       Int,
                          chainingSize:                     Int,
                          fpuEnable:                        Boolean,
                          decoderParam:                     DecoderParam)
  extends SerializableModuleParameter {

  val laneScale: Int = datapathWidth / eLen

  val chaining1HBits: Int = 2 << log2Ceil(chainingSize)

  /** 1 in MSB for instruction order. */
  val instructionIndexBits: Int = log2Ceil(chainingSize) + 1

  /** maximum of lmul, defined in spec. */
  val lmulMax: Int = 8

  /** see [[T1Parameter.sewMin]] */
  val sewMin: Int = 8

  /** The datapath is divided into groups based on SEW. The current implement is calculating them in multiple cycles.
   * TODO: we must parallelize it as much as possible since it highly affects the computation bandwidth.
   */
  val dataPathByteWidth: Int = datapathWidth / sewMin

  val dataPathByteBits: Int = log2Ceil(dataPathByteWidth)

  /** maximum of vl. */
  val vlMax: Int = vLen * lmulMax / sewMin

  /** width of [[vlMax]] `+1` is for vl being 0 to vlMax(not vlMax - 1). we use less than for comparing, rather than
   * less equal.
   */
  val vlMaxBits: Int = log2Ceil(vlMax) + 1

  /** how many group does a single register have.
   *
   * in each lane, for one vector register, it is divided into groups with size of [[datapathWidth]]
   */
  val singleGroupSize: Int = vLen / datapathWidth / laneNumber

  /** for each instruction, the maximum number of groups to execute. */
  val groupNumberMax: Int = singleGroupSize * lmulMax

  /** used as the LSB index of VRF access
   *
   * TODO: uarch doc the arrangement of VRF: {reg index, offset}
   *
   * for each number in table below, it represent a [[datapathWidth]]
   * {{{
   *         lane0 | lane1 | ...                                   | lane8
   * offset0    0  |    1  |    2  |    3  |    4  |    5  |    6  |    7
   * offset1    8  |    9  |   10  |   11  |   12  |   13  |   14  |   15
   * offset2   16  |   17  |   18  |   19  |   20  |   21  |   22  |   23
   * offset3   24  |   25  |   26  |   27  |   28  |   29  |   30  |   31
   * }}}
   */
  val vrfOffsetBits: Int = log2Ceil(singleGroupSize)

  /** +1 in MSB for comparing to next group number. we don't use `vl-1` is because if the mask of last group is all 0,
   * it should be jumped through. so we directly compare the next group number with the MSB of `vl`.
   */
  val groupNumberBits: Int = log2Ceil(groupNumberMax + 1)

  /** hardware width of [[laneNumber]]. */
  val laneNumberBits: Int = 1.max(log2Ceil(laneNumber))

  /** hardware width of [[datapathWidth]]. */
  val datapathWidthBits: Int = log2Ceil(datapathWidth)

  /** see [[T1Parameter.maskGroupWidth]] */
  val maskGroupWidth: Int = datapathWidth

  /** see [[T1Parameter.maskGroupSize]] */
  val maskGroupSize: Int = vLen / maskGroupWidth

  /** hardware width of [[maskGroupSize]]. */
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)

  /** Size of the queue for storing execution information todo: Determined by the longest execution unit
   */
  val executionQueueSize: Int = 4

  // outstanding of MaskExchangeUnit.maskReq
  val maskRequestQueueSize: Int = 8

  /** VRF index number is 32, defined in spec. */
  val regNum: Int = 32

  /** The hardware width of [[regNum]] */
  val regNumBits:           Int = log2Ceil(regNum)

  val dataWidth: Int = new LaneRequest(
    instructionIndexBits,
    decoderParam,
    datapathWidth,
    vlMaxBits,
    laneNumber,
    dataPathByteWidth
  ).getWidth
  val lsuSize = 1

  // lane + lsu + top + mask unit
  val idWidth: Int = log2Ceil(laneNumber + lsuSize + 1 + 1)
  // todo
  val opcodeWidth: Int = log2Ceil(7)
}

class LaneInterface (val parameter: LaneIFParameter)
  extends FixedIORawModule(new LaneInterfaceIO(parameter))
    with SerializableModule[LaneIFParameter]
    with ImplicitClock
    with ImplicitReset {

  protected def implicitClock = io.clock
  protected def implicitReset = io.reset

  val sourceQueueSize = 8

  val readVrfSourceQueue: QueueIO[UInt] = Queue.io(UInt(parameter.idWidth.W), sourceQueueSize)
  readVrfSourceQueue.enq.valid := io.vrfReadRequest.fire
  readVrfSourceQueue.enq.bits := io.inputVirtualChannelVec(1).bits.sourceID
  assert(!readVrfSourceQueue.enq.valid || readVrfSourceQueue.enq.ready)
  readVrfSourceQueue.deq.ready := io.readVrfAck.fire

  val physicalChannelFromLane: Seq[DecoupledIO[Data]] = Seq(
    io.maskRequest, io.readVrfAck, io.readBusDeq,
    io.maskUnitRequest, io.writeBusDeq, io.v0Update, io.laneResponse
  )

  // lane             [0, laneNumber - 1]
  // lsu              laneNumber
  // top & mask unit  laneNumber + 1
  val sinkIDVec = Seq(
    (parameter.laneNumber + 1).U, readVrfSourceQueue.deq.bits, io.readBusDeq.bits.sink,
    (parameter.laneNumber + 1).U, io.writeBusDeq.bits.sink, (parameter.laneNumber + 1).U, (parameter.laneNumber + 1).U
  )

  val physicalChannelToLane: Seq[DecoupledIO[Data]] = Seq(
    io.laneRequest, io.vrfReadRequest, io.maskRequestAck,
    io.readBusEnq, io.writeBusEnq, io.lsuReport, io.vrfWriteRequest
  )

  physicalChannelFromLane.zipWithIndex.foreach {case (req, index) =>
    io.outputVirtualChannelVec(index).valid := req.valid
    req.ready := io.outputVirtualChannelVec(index).ready
    io.outputVirtualChannelVec(index).bits.data := req.bits.asUInt
    require(req.bits.getWidth <= parameter.dataWidth, "channel width error.")
    io.outputVirtualChannelVec(index).bits.opcode := index.U
    io.outputVirtualChannelVec(index).bits.sourceID := io.laneIndex
    io.outputVirtualChannelVec(index).bits.sinkID := sinkIDVec(index)
    io.outputVirtualChannelVec(index).bits.last := true.B
  }

  physicalChannelToLane.zipWithIndex.foreach {case (req, index) =>
    req.valid := io.inputVirtualChannelVec(index).valid
    io.inputVirtualChannelVec(index).ready := req.ready
    req.bits := io.inputVirtualChannelVec(index).bits.data(req.bits.getWidth - 1, 0).asTypeOf(req.bits)
    when(io.inputVirtualChannelVec(index).fire) {
      assert(io.inputVirtualChannelVec(index).bits.sinkID === io.laneIndex)
      assert(io.inputVirtualChannelVec(index).bits.opcode === index.U)
    }
  }
}
