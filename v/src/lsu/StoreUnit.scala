package v

import chisel3._
import chisel3.util._
import lsu.StoreStatus
import tilelink.TLChannelA

class cacheLineEnqueueBundle(param: MSHRParam) extends Bundle {
  val data: UInt = UInt((param.cacheLineSize * 8).W)
  val mask: UInt = UInt(param.cacheLineSize.W)
  val index: UInt = UInt(param.cacheLineIndexBits.W)
}

class StoreUnit(param: MSHRParam) extends StrideBase(param) with LSUPublic {
  val tlPortA: Vec[DecoupledIO[TLChannelA]] = IO(Vec(param.memoryBankSize, param.tlParam.bundle().a))

  val status: StoreStatus = IO(Output(new StoreStatus(param.memoryBankSize)))

  /** write channel to [[V]], which will redirect it to [[Lane.vrf]].
   * see [[LSU.vrfWritePort]]
   */
  val vrfReadDataPorts: Vec[DecoupledIO[VRFReadRequest]] = IO(
    Vec(
      param.laneNumber,
      Decoupled(new VRFReadRequest(param.regNumBits, param.vrfOffsetBits, param.instructionIndexBits))
    )
  )

  /** hard wire form Top.
   * see [[LSU.vrfReadResults]]
   */
  val vrfReadResults: Vec[UInt] = IO(Input(Vec(param.laneNumber, UInt(param.datapathWidth.W))))

  // stage 0, 处理 vl, mask ...
  val changeReadGroup: Bool = Wire(Bool())
  val dataGroupByteSize: Int = param.datapathWidth * param.laneNumber / 8
  val dataByteSize: UInt = (csrInterface.vl << lsuRequest.bits.instructionInformation.eew).asUInt
  val lastDataGroupForInstruction: UInt = (dataByteSize >> log2Ceil(dataGroupByteSize)).asUInt -
    !dataByteSize(log2Ceil(dataGroupByteSize) - 1, 0).orR
  val lastDataGroupReg: UInt = RegEnable(lastDataGroupForInstruction, 0.U, lsuRequest.valid)
  val nextDataGroup: UInt = Mux(lsuRequest.valid, -1.S(dataGroup.getWidth.W).asUInt, dataGroup + 1.U)
  val isLastRead: Bool = nextDataGroup === lastDataGroupReg
  val lastGroupAndNeedAlign: Bool = initOffset.orR && isLastRead
  val stage0Idle: Bool = RegEnable(
    isLastRead && !lsuRequest.valid,
    true.B,
    changeReadGroup || lsuRequest.valid
  )
  val readStageEnqueueReady: Bool = Wire(Bool())
  changeReadGroup := readStageEnqueueReady && !stage0Idle

  when(changeReadGroup || lsuRequest.valid) {
    when((!lastDataGroupForInstruction) || lsuRequest.valid) {
      maskCounterInGroup := Mux(isLastDataGroup || lsuRequest.valid, 0.U, nextMaskCount)
    }
    when(isLastDataGroup && !isLastMaskGroup) {
      maskSelect.valid := true.B
    }
    when((isLastDataGroup && !isLastMaskGroup) || lsuRequest.valid) {
      maskGroupCounter := Mux(lsuRequest.valid, 0.U, nextMaskGroup)
    }
    dataGroup := nextDataGroup
  }

  // stage1, 读vrf
  val readStageValid: Bool = RegInit(false.B)
  val readData: Vec[UInt] = RegInit(VecInit(Seq.fill(param.laneNumber)(0.U(param.datapathWidth.W))))
  val readMask: UInt = RegInit(0.U(param.maskGroupWidth.W))
  val tailLeft1: Bool = RegInit(false.B)
  // 从vrf里面读数据
  Seq.tabulate(param.laneNumber) { laneIndex =>
    val readPort: DecoupledIO[VRFReadRequest] = vrfReadDataPorts(laneIndex)
    readPort.valid := accessState(laneIndex) && readStageValid
    readPort.bits.vs :=
      lsuRequestReg.instructionInformation.vs3 + accessPtr + (dataGroup >> readPort.bits.offset.getWidth).asUInt
    readPort.bits.offset := dataGroup
    readPort.bits.instructionIndex := lsuRequestReg.instructionIndex
    when(readPort.fire) {
      accessState(laneIndex) := false.B
    }
    when(RegNext(readPort.fire, false.B)) {
      readData(laneIndex) := vrfReadResults(laneIndex)
    }
  }

  // 需要等待 sram 的结果返回
  val readResponseCheck: Bool = RegNext(accessStateCheck, true.B)
  val lastPtr: Bool = accessPtr === 0.U
  val readStateCheck: Bool = accessStateCheck && readResponseCheck
  val readStateValid: Bool = lastPtr && readStateCheck
  val accessBufferDequeueReady: Bool = Wire(Bool())
  val accessBufferDequeueFire: Bool = readStateValid && accessBufferDequeueReady && readStageValid
  readStageEnqueueReady := !readStageValid || accessBufferDequeueFire
  when(changeReadGroup ^ accessBufferDequeueFire) {
    readStageValid := changeReadGroup
  }

  when(changeReadGroup) {
    readMask := maskForGroupWire
    tailLeft1 := lastGroupAndNeedAlign
  }

  when(changeReadGroup || (readStateCheck && !lastPtr)) {
    accessPtr := Mux(
      changeReadGroup,
      lsuRequestReg.instructionInformation.nf,
      accessPtr - 1.U
    )
    // 在更新ptr的时候把数据推进 [[accessData]] 里面
    accessData := VecInit(readData.asUInt +: accessData.init)
    // 更新access state
    accessState := initSendState
  }

  // stage2, 用一个buffer来存转成cache line 的数据
  val bufferValid: Bool = RegInit(false.B)
  val maskForBufferData: UInt = RegInit(0.U(param.maskGroupWidth.W))
  val tailLeft2: Bool = RegInit(false.B)
  val alignedDequeue: DecoupledIO[cacheLineEnqueueBundle] = Wire(Decoupled(new cacheLineEnqueueBundle(param)))
  val alignedDequeueFire: Bool = alignedDequeue.fire
  // cache 不对齐的时候的上一条残留
  val cacheLineTemp: UInt = RegEnable(dataBuffer.head, 0.U((param.cacheLineSize * 8).W), alignedDequeueFire)
  val maskTemp: UInt = RegInit(0.U(param.cacheLineSize.W))
  val tailValid: Bool = RegInit(false.B)
  val isLastCacheLineInBuffer: Bool = cacheLineIndexInBuffer === lsuRequestReg.instructionInformation.nf
  accessBufferDequeueReady := !bufferValid
  val bufferStageEnqueueData: UInt = VecInit(readData.asUInt +: accessData.init).asUInt
  // 把数据regroup, 然后放去 [[dataBuffer]]
  when(accessBufferDequeueFire) {
    maskForBufferData := readMask
    tailLeft2 := tailLeft1
    // todo: 只是因为参数恰好是一个方形的, 需要写一个反的
    dataBuffer := Mux1H(dataEEWOH, Seq.tabulate(3) { sewSize =>
      Mux1H(UIntToOH(lsuRequestReg.instructionInformation.nf), Seq.tabulate(8) { segSize =>
        // 32 byte
        // 每个数据块 2 ** sew byte
        val dataBlockSize = 1 << sewSize
        // 总共需要32byte数据, 会有 32/dataBlockSize 个数据块
        val blockSize = 32 / dataBlockSize
        val nFiled = segSize + 1
        // 一次element会用掉多少 byte 数据
        val elementSize = dataBlockSize * nFiled
        VecInit(Seq.tabulate(8) { segIndex =>
          val res = Wire(UInt((param.cacheLineSize * 8).W))
          if (segIndex > segSize) {
            // todo: 优化这个 DontCare
            res := DontCare
          } else {
            val dataGroup: Seq[UInt] = Seq.tabulate(blockSize) { elementIndex =>
              val basePtr = elementSize * elementIndex + dataBlockSize * segIndex
              bufferStageEnqueueData.asUInt((basePtr + dataBlockSize) * 8 - 1, basePtr * 8)
            }
            res := VecInit(dataGroup).asUInt
          }
          res
        }).asUInt.suggestName(s"regroupLoadData_${sewSize}_$segSize")
      })
    }).asTypeOf(dataBuffer)
  }
  when(alignedDequeueFire) {
    dataBuffer := VecInit(dataBuffer.tail :+ 0.U.asTypeOf(dataBuffer.head))
  }

  when(accessBufferDequeueFire || alignedDequeueFire) {
    cacheLineIndexInBuffer := Mux(accessBufferDequeueFire, 0.U, cacheLineIndexInBuffer + 1.U)
  }

  when(accessBufferDequeueFire || (alignedDequeueFire && isLastCacheLineInBuffer)) {
    bufferValid := accessBufferDequeueFire
  }

  when(lsuRequest.valid || alignedDequeueFire) {
    bufferBaseCacheLineIndex := Mux(lsuRequest.valid, 0.U, bufferBaseCacheLineIndex + 1.U)
  }

  when(lsuRequest.valid || alignedDequeueFire) {
    maskTemp := Mux(lsuRequest.valid, 0.U, maskForBufferData)
    tailValid := Mux(lsuRequest.valid, false.B, bufferValid && tailLeft2 && isLastCacheLineInBuffer)
  }

  // 连接 alignedDequeue
  alignedDequeue.valid := bufferValid || tailValid
  // aligned
  alignedDequeue.bits.data :=
    multiShifter(right = false, multiSize = 8)(dataBuffer.head ## cacheLineTemp, initOffset) >> cacheLineTemp.getWidth
  val selectMaskForTail: UInt = Mux(bufferValid, maskForBufferData, 0.U(maskTemp.getWidth.W))
  alignedDequeue.bits.mask := ((selectMaskForTail ## maskTemp) << initOffset) >> maskTemp.getWidth
  alignedDequeue.bits.index := bufferBaseCacheLineIndex

  val alignedPortSelect: UInt =
    lsuRequestReg.rs1Data(param.bankPosition + log2Ceil(param.memoryBankSize) - 1, param.bankPosition) +
      bufferBaseCacheLineIndex(log2Ceil(param.memoryBankSize) - 1, 0)
  val selectOH: UInt = UIntToOH(alignedPortSelect)
  // tl 发送单元
  val readyVec: Vec[Bool] = VecInit(Seq.tabulate(param.memoryBankSize) { portIndex =>
    val dataToSend: ValidIO[cacheLineEnqueueBundle] = RegInit(0.U.asTypeOf(Valid(new cacheLineEnqueueBundle(param))))
    val port: DecoupledIO[TLChannelA] = tlPortA(portIndex)
    val portFire: Bool = port.fire
    val burstIndex: UInt = RegInit(0.U(log2Ceil(burstSize).W))
    val burstOH: UInt = UIntToOH(burstIndex)
    val last = burstIndex.andR
    val enqueueReady: Bool = !dataToSend.valid
    val enqueueFire: Bool = enqueueReady && alignedDequeue.valid && selectOH(portIndex)
    status.releasePort(portIndex) := burstIndex === 0.U
    when(enqueueFire) {
      dataToSend.bits := alignedDequeue.bits
    }

    when(enqueueFire || (portFire && last)) {
      dataToSend.valid := enqueueFire
    }

    when(enqueueFire || portFire) {
      burstIndex := Mux(enqueueFire, 0.U, burstIndex + 1.U)
    }

    port.valid := dataToSend.valid
    port.bits.opcode := 0.U
    port.bits.param := 0.U
    port.bits.size := param.cacheLineBits.U
    port.bits.source := dataToSend.bits.index
    port.bits.address := ((lsuRequestReg.rs1Data >> param.cacheLineBits).asUInt + dataToSend.bits.index) ##
      0.U(param.cacheLineBits.W)
    port.bits.mask := Mux1H(
      burstOH,
      Seq.tabulate(burstSize)(burstIndex => dataToSend.bits.mask(
        (burstIndex + 1) * param.tlParam.a.maskWidth - 1,
        burstIndex * param.tlParam.a.maskWidth,
      ))
    )
    port.bits.data := Mux1H(
      burstOH,
      Seq.tabulate(burstSize)(burstIndex => dataToSend.bits.data(
        (burstIndex + 1) * param.tlParam.a.dataWidth - 1,
        burstIndex * param.tlParam.a.dataWidth,
      ))
    )
    port.bits.corrupt := false.B

    enqueueReady
  })

  val sendStageClear: Bool = readyVec.asUInt.andR
  alignedDequeue.ready := (readyVec.asUInt & selectOH).orR

  status.idle := sendStageClear && !bufferValid && !readStageValid && stage0Idle
  val idleNext: Bool = RegNext(status.idle, true.B)
  status.last := !idleNext && status.idle
  status.changeMaskGroup := maskSelect.valid
  status.instructionIndex := lsuRequestReg.instructionIndex

}
