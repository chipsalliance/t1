package org.chipsalliance.t1.ipemu.dpi

import chisel3._
import org.chipsalliance.t1.rtl.{CSRInterface, VRequest, VResponse}

case class PokeInstParameter(xLen: Int,
                             vlMaxBits: Int,
                             triggerDelay: Int)

class PokeInst(p: PokeInstParameter) extends DPIModuleLegacy {
  val isImport: Boolean = true
  val clock = dpiTrigger("clock", Input(Bool()))

  val request = new VRequest(p.xLen)
  val request_instruction = dpiOut("request_instruction", Output(request.instruction))
  val request_src1Data = dpiOut("request_src1Data", Output(request.src1Data))
  val request_src2Data = dpiOut("request_src2Data", Output(request.src2Data))

  val instructionValid = dpiOut("instructionValid", Output(Bool()))

  val csrInterface = new CSRInterface(p.vlMaxBits)
  val csrInterface_vl = dpiOut("csrInterface_vl", Output(csrInterface.vl))
  val csrInterface_vStart = dpiOut("csrInterface_vStart", Output(csrInterface.vStart))
  val csrInterface_vlMul = dpiOut("csrInterface_vlMul", Output(csrInterface.vlmul))
  val csrInterface_vSew = dpiOut("csrInterface_vSew", Output(csrInterface.vSew))
  val csrInterface_vxrm = dpiOut("csrInterface_vxrm", Output(csrInterface.vxrm))
  val csrInterface_vta = dpiOut("csrInterface_vta", Output(csrInterface.vta))
  val csrInterface_vma = dpiOut("csrInterface_vma", Output(csrInterface.vma))
  val csrInterface_ignoreException = dpiOut("csrInterface_ignoreException", Output(csrInterface.ignoreException))

  val respValid = dpiIn("respValid", Input(Bool()))
  val response = new VResponse(p.xLen)
  val response_data = dpiIn("response_data", Input(response.data))
  val response_vxsat = dpiIn("response_vxsat", Input(response.vxsat))
  val response_rd_valid = dpiIn("response_rd_valid", Input(response.rd.valid))
  val response_rd_bits = dpiIn("response_rd_bits", Input(response.rd.bits))
  val response_mem = dpiIn("response_mem", Input(response.mem))

  override val trigger = s"always @(posedge ${clock.name}) #(${p.triggerDelay})"
}
