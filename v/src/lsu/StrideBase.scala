package v
import chisel3._
import chisel3.util._
import lsu.LSUBaseStatus

trait LSUPublic {
  val lsuRequest: ValidIO[LSURequest]
  val csrInterface: CSRInterface
  val maskInput: UInt
  val maskSelect: ValidIO[UInt]
  val status: LSUBaseStatus
}

abstract class StrideBase(param: MSHRParam) extends Module {
  val bufferSize: Int = param.datapathWidth * param.laneNumber * 8 / (param.cacheLineSize * 8)
  val burstSize: Int = param.cacheLineSize * 8 / param.tlParam.d.dataWidth

  // 直接维护data group吧
  // (vl * 8) / (datapath * laneNumber)
  val dataGroupBits: Int = log2Ceil((param.vLen * 8) / (param.datapathWidth * param.laneNumber))

  // pipe request
  /** [[LSURequest]] from LSU
   * see [[LSU.request]]
   */
  val lsuRequest: ValidIO[LSURequest] = IO(Flipped(Valid(new LSURequest(param.datapathWidth))))

  /** request from LSU. */
  val lsuRequestReg: LSURequest = RegEnable(lsuRequest.bits, 0.U.asTypeOf(lsuRequest.bits), lsuRequest.valid)

  val nFiled: UInt = lsuRequest.bits.instructionInformation.nf +& 1.U
  val nFiledReg: UInt = RegEnable(nFiled, 0.U, lsuRequest.valid)

  // pipe csr
  /** the CSR interface from [[V]], latch them here.
   * TODO: merge to [[LSURequest]]
   */
  val csrInterface: CSRInterface = IO(Input(new CSRInterface(param.vlMaxBits)))

  /** latch CSR.
   * TODO: merge to [[lsuRequestReg]]
   */
  val csrInterfaceReg: CSRInterface = RegEnable(csrInterface, 0.U.asTypeOf(csrInterface), lsuRequest.valid)

  // handle mask

  /** mask from [[V]]
   * see [[LSU.maskInput]]
   */
  val maskInput: UInt = IO(Input(UInt(param.maskGroupWidth.W)))

  /** the address of the mask group in the [[V]].
   * see [[LSU.maskSelect]]
   */
  val maskSelect: ValidIO[UInt] = IO(Valid(UInt(param.maskGroupSizeBits.W)))

  // always use intermediate from instruction for unit stride.
  val dataEEW: UInt = RegEnable(lsuRequest.bits.instructionInformation.eew, 0.U, lsuRequest.valid)

  /** 1H version for [[dataEEW]] */
  val dataEEWOH: UInt = UIntToOH(dataEEW)(2, 0)

  val isMaskType: Bool = Mux(
    lsuRequest.valid,
    lsuRequest.bits.instructionInformation.maskedLoadStore,
    lsuRequestReg.instructionInformation.maskedLoadStore
  )
  val maskAmend: UInt = Mux(isMaskType, maskInput, -1.S(maskInput.getWidth.W).asUInt)
  /** register to latch mask */
  val maskReg: UInt = RegEnable(maskAmend, 0.U(maskInput.getWidth.W), maskSelect.fire || lsuRequest.valid)

  val lastMaskAmend: UInt =
    (scanRightOr(UIntToOH(csrInterface.vl(log2Ceil(param.maskGroupWidth) - 1, 0))) >> 1).asUInt
  val needAmend: Bool = RegEnable(csrInterface.vl(log2Ceil(param.maskGroupWidth) - 1, 0).orR, false.B, lsuRequest.valid)
  val lastMaskAmendReg: UInt = RegEnable(lastMaskAmend, 0.U.asTypeOf(lastMaskAmend), lsuRequest.valid)

  // 维护mask
  val countEndForGroup: UInt = Mux1H(dataEEWOH, Seq(0.U, 1.U, 3.U))
  val maskGroupCounter: UInt = RegInit(0.U(log2Ceil(param.vLen / param.maskGroupWidth).W))
  val nextMaskGroup: UInt = maskGroupCounter + 1.U
  val maskCounterInGroup: UInt = RegInit(0.U(log2Ceil(param.maskGroupWidth / ((param.cacheLineSize * 8) / 32)).W))
  val nextMaskCount: UInt = maskCounterInGroup + 1.U
  // is last data group for mask group
  val isLastDataGroup: Bool = maskCounterInGroup === countEndForGroup

  maskSelect.valid := false.B
  maskSelect.bits := Mux(lsuRequest.valid, 0.U, nextMaskGroup)

  val maskForGroupWire: UInt = Wire(UInt((param.datapathWidth * param.laneNumber / 8).W))
  val maskForGroup: UInt = RegInit(0.U((param.datapathWidth * param.laneNumber / 8).W))

  // 在边界上被vl修正
  val isLastMaskGroup: Bool = RegEnable(
    Mux(
      lsuRequest.valid,
      (csrInterface.vl >> log2Ceil(param.maskGroupWidth)) === 0.U,
      maskSelect.bits === (csrInterfaceReg.vl >> log2Ceil(param.maskGroupWidth))
  ),
    false.B,
    maskSelect.valid || lsuRequest.valid
  )
  val maskWire: UInt = Wire(UInt(param.maskGroupWidth.W))
  maskWire := maskReg & Mux(needAmend && isLastMaskGroup, lastMaskAmendReg, -1.S(param.maskGroupWidth.W).asUInt)
  maskForGroupWire := Mux1H(dataEEWOH, Seq(
    maskWire,
    Mux(maskCounterInGroup(0), FillInterleaved(2, maskWire) >> param.maskGroupWidth, FillInterleaved(2, maskWire)),
    Mux1H(UIntToOH(maskCounterInGroup), Seq.tabulate(4) { maskIndex =>
      FillInterleaved(4, maskWire)(
        maskIndex * param.maskGroupWidth + param.maskGroupWidth - 1, maskIndex * param.maskGroupWidth
      )
    })
  ))

  val initSendState: Vec[Bool] =
    VecInit(maskForGroupWire.asBools.grouped(param.datapathWidth / 8).map(VecInit(_).asUInt.orR).toSeq)

  // 缓存的与vrf交互的数据
  val  accessData: Vec[UInt] = RegInit(VecInit(Seq.fill(bufferSize)(0.U((param.datapathWidth * param.laneNumber).W))))

  // access vrf state
  // which segment index
  val accessPtr: UInt = RegInit(0.U(3.W))
  // true -> need send data to vrf
  val accessState: Vec[Bool] = RegInit(VecInit(Seq.fill(param.laneNumber)(false.B)))
  val accessStateCheck: Bool = !accessState.asUInt.orR
  val dataGroup: UInt = RegInit(0.U(dataGroupBits.W))

  // 把 nFiled 个cache line 分成一组
  val bufferCounterBits: Int = log2Ceil(bufferSize)
  val dataBuffer: Vec[UInt] = RegInit(VecInit(Seq.fill(bufferSize)(0.U((param.cacheLineSize * 8).W))))
  val bufferBaseCacheLineIndex: UInt = RegInit(0.U(param.cacheLineIndexBits.W))
  val cacheLineIndexInBuffer: UInt = RegInit(0.U(bufferCounterBits.W))

  //初始偏移
  val initOffset: UInt = lsuRequestReg.rs1Data(param.cacheLineBits - 1, 0)
}
