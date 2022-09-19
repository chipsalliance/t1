package v

import chisel3._
import chisel3.util._
import tilelink.{TLBundleParameter, TLChannelAParameter, TLChannelDParameter}

case class LSUParam(dataWidth: Int, ELEN: Int = 32, VLEN: Int = 1024, lane: Int = 8, vaWidth: Int = 32) {
  val dataBits:          Int = log2Ceil(dataWidth)
  val mshrSize:          Int = 3
  val maskGroupWidth:    Int = 32
  val maskGroupSize:     Int = VLEN / 32
  val maskGroupSizeBits: Int = log2Ceil(maskGroupSize)
  val VLMaxBits:         Int = log2Ceil(VLEN)
  val laneGroupSize:     Int = VLEN / lane
  // 一次完全的offset读会最多分成多少个offset
  val lsuGroupLength:   Int = ELEN * lane / 8
  val lsuGroupSize:     Int = VLEN / lane
  val lsuGroupSizeBits: Int = log2Ceil(lsuGroupSize)
  val sourceWidth = 8
  val tlParam: TLBundleParameter = TLBundleParameter(
    a = TLChannelAParameter(vaWidth, sourceWidth, ELEN, 2, 4),
    b = None,
    c = None,
    d = TLChannelDParameter(sourceWidth, sourceWidth, ELEN, 2),
    e = None
  )
  def vrfParam: VRFParam = VRFParam(VLEN, lane, laneGroupSize, ELEN)
}

class LSUInstInformation(param: LSUParam) extends Bundle {

  /** nf + 1 */
  val nf: UInt = UInt(3.W)

  /** mew = 1 reserved */
  val mew: Bool = Bool()

  /** unit-stride index-uo stride index-o */
  val mop: UInt = UInt(2.W)

  /** vs2 | rs2 | umop
    * 0         ->  unit stride
    * 0b01000   ->  whole register
    * 0b01011   ->  mask, eew = 8
    * 0b10000   ->  fault only first (load)
    */
  val vs2: UInt = UInt(5.W)
  val vs1: UInt = UInt(5.W)

  /** 0 -> 8
    * size(0) -> 16
    * size(1) -> 32
    */
  val eew: UInt = UInt(3.W)
  val vs3: UInt = UInt(5.W)
  val st:  Bool = Bool()
}

class LSUReq(param: LSUParam) extends Bundle {
  val instInf:   LSUInstInformation = new LSUInstInformation(param)
  val rs1Data:   UInt = UInt(param.dataWidth.W)
  val rs2Data:   UInt = UInt(param.dataWidth.W)
  val instIndex: UInt = UInt(3.W)
}

class LSU(param: LSUParam) extends Module {
  val req:          DecoupledIO[LSUReq] = IO(Flipped(Decoupled(new LSUReq(param))))
  val maskRegInput: UInt = IO(Input(UInt(param.maskGroupWidth.W)))
  val maskSelect:   UInt = IO(Output(UInt(param.maskGroupSizeBits.W)))
  val csrInterface: LaneCsrInterface = IO(Input(new LaneCsrInterface(param.VLMaxBits)))

}
