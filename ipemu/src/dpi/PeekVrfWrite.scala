package elaborate.dpi

import chisel3._

import v.VRFWriteRequest

case class PeekVrfWriteParameter(regNumBits: Int,
                                 laneNumber: Int,
                                 vrfOffsetBits: Int,
                                 instructionIndexBits: Int,
                                 datapathWidth: Int,
                                 triggerDelay: Int)

class PeekVrfWrite(p: PeekVrfWriteParameter) extends DPIModule {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val landIdx = dpiIn("laneIdx", Input(UInt(32.W)))
  val valid = dpiIn("valid", Input(Bool()))
  val request = new VRFWriteRequest(
    p.regNumBits,
    p.vrfOffsetBits,
    p.instructionIndexBits,
    p.datapathWidth
  )
  val request_vd = dpiIn("request_vd", Input(request.vd))
  val request_offset = dpiIn("request_offset", Input(request.offset))
  val request_mask = dpiIn("request_mask", Input(request.mask))
  val request_data = dpiIn("request_data", Input(request.data))
  val request_instruction = dpiIn("request_instructionIndex", Input(request.instructionIndex))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
