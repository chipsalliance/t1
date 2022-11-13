package tests.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.experimental.hierarchy._
import chisel3.util.{Decoupled, DecoupledIO, HasExtModuleInline, Valid, ValidIO}
import tilelink.{TLBundle, TLChannelA}
import v.{LaneCsrInterface, V, VReq, VResp}

class VerificationModule(dut: V) extends TapModule {
  override val desiredName = "VerificationModule"
  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))

  val verbatim = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "Verbatim"
    val clock = IO(Output(Clock()))
    val reset = IO(Output(Bool()))
    setInline("verbatim.sv",
      """module Verbatim(
        |output clock,
        |output reset
        |);
        |reg _clock = 1'b0;
        |always #(0.5) _clock = ~_clock;
        |reg _reset = 1'b1;
        |initial #(1.1) _reset = 0;
        |
        |assign clock = _clock;
        |assign reset = _reset;
        |
        |import "DPI-C" function void dpiInitCosim();
        |initial dpiInitCosim();
        |
        |import "DPI-C" function void dpiTimeoutCheck();
        |always #(1.1) dpiTimeoutCheck();
        |
        |endmodule
        |""".stripMargin)
  })
  clock := verbatim.clock
  reset := verbatim.reset

  // clone IO from V(I need types)
  val req: DecoupledIO[VReq] = IO(Decoupled(new VReq(dut.param)))
  val resp: ValidIO[VResp] = IO(Flipped(Valid(new VResp(dut.param))))
  val csrInterface: LaneCsrInterface = IO(Output(new LaneCsrInterface(dut.param.laneParam.VLMaxWidth)))
  val storeBufferClear: Bool = IO(Output(Bool()))
  val tlPort: Vec[TLBundle] = IO(Vec(dut.param.tlBank, Flipped(dut.param.tlParam.bundle())))
  storeBufferClear := true.B

  // XMR
  val laneWriteReadySeq = dut.laneVec.map(_.vrf.write.ready).map(tap)
  val laneWriteValidSeq = dut.laneVec.map(_.vrf.write.valid).map(tap)
  val laneWriteBitsSeq = dut.laneVec.map(_.vrf.write.bits).map(tap)
  val lsuReqEnqDbg = withClockAndReset(clock, reset)(RegNext(tap(dut.lsu.reqEnq)))

  // DPI

  val dpiPokeInstruction = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPokeInstruction"
    val clock = IO(Input(Clock()))
    val request = IO(Output(new VReq(dut.param)))
    val valid = IO(Output(Bool()))
    setInline("dpiPokeInstruction.sv",
      s"""module dpiPokeInstruction(
         |input clock,
         |output [31:0] request_inst,
         |output [${dut.param.XLEN - 1}:0] request_src1Data,
         |output [${dut.param.XLEN - 1}:0] request_src2Data,
         |output valid
         |);
         |import "DPI-C" function void dpiPokeInstruction(
         |output bit[31:0] request_inst,
         |output bit[${dut.param.XLEN - 1}:0] request_src1Data,
         |output bit[${dut.param.XLEN - 1}:0] request_src2Data,
         |output bit valid
         |);
         |always @ (negedge clock) #(0.1) dpiPokeInstruction(request_inst, request_src1Data, request_src2Data, valid);
         |endmodule
         |""".stripMargin
    )
  })
  dpiPokeInstruction.clock := clock
  req.bits := dpiPokeInstruction.request
  req.valid := dpiPokeInstruction.valid

  val dpiInstructionFire = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiInstructionFire"
    val clock = IO(Input(Clock()))
    val cond = IO(Input(Bool()))
    val issueIndex = IO(Input(UInt(32.W)))
    setInline("dpiInstructionFire.sv",
      """module dpiInstructionFire(
        |input clock,
        |input bit cond,
        |input bit[31:0] issueIndex
        |);
        |import "DPI-C" function void dpiInstructionFire(input ready, input bit[31:0] idx);
        |always @ (negedge clock) #(0.2) dpiInstructionFire(cond, issueIndex);
        |endmodule
        |""".stripMargin
    )
  })
  dpiInstructionFire.clock := clock
  dpiInstructionFire.cond := tap(dut.req.valid) && tap(dut.req.ready) && !reset
  dpiInstructionFire.issueIndex := tap(dut.instCount)

  val dpiPokeCSR = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPokeCSR"
    val clock = IO(Input(Clock()))
    val csrInterface = IO(Output(dut.csrInterface.cloneType))
    setInline("dpiPokeCSR.sv",
      s"""module dpiPokeCSR(
         |input clock,
         |output bit[${csrInterface.vl.getWidth - 1}:0] csrInterface_vl,
         |output bit[${csrInterface.vStart.getWidth - 1}:0] csrInterface_vStart,
         |output bit[${csrInterface.vlmul.getWidth - 1}:0] csrInterface_vlmul,
         |output bit[${csrInterface.vSew.getWidth - 1}:0] csrInterface_vSew,
         |output bit[${csrInterface.vxrm.getWidth - 1}:0] csrInterface_vxrm,
         |output bit csrInterface_vta,
         |output bit csrInterface_vma,
         |output bit csrInterface_ignoreException);
         |import "DPI-C" function void dpiPokeCSR(
         |output bit[${csrInterface.vl.getWidth - 1}:0] vl,
         |output bit[${csrInterface.vStart.getWidth - 1}:0] vStart,
         |output bit[${csrInterface.vlmul.getWidth - 1}:0] vlmul,
         |output bit[${csrInterface.vSew.getWidth - 1}:0] vSew,
         |output bit[${csrInterface.vxrm.getWidth - 1}:0] vxrm,
         |output bit vta,
         |output bit vma,
         |output bit ignoreException
         |);
         |always @ (negedge clock) #(0.1) dpiPokeCSR(csrInterface_vl, csrInterface_vStart, csrInterface_vlmul, csrInterface_vSew, csrInterface_vxrm, csrInterface_vta, csrInterface_vma, csrInterface_ignoreException);
         |endmodule
         |""".stripMargin
    )
  })
  dpiPokeCSR.clock := clock
  csrInterface := dpiPokeCSR.csrInterface

  val dpiPeekResponse = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPeekResponse"
    val clock = IO(Input(Clock()))
    val cond = IO(Input(Bool()))
    val response = IO(Input(dut.resp.bits.cloneType))
    setInline("dpiPeekResponse.sv",
      s"""module dpiPeekResponse(
         |input clock,
         |input bit cond,
         |input bit[${response.getWidth - 1}:0] response_data
         |);
         |import "DPI-C" function void dpiPeekResponse(
         |input bit valid,
         |input bit[${response.getWidth - 1}:0] bits
         |);
         |always @ (posedge clock) #(0.1) dpiPeekResponse(cond, response_data);
         |endmodule
         |""".stripMargin
    )
  })
  dpiPeekResponse.clock := clock
  dpiPeekResponse.cond := resp.valid
  dpiPeekResponse.response := resp.bits

  @instantiable
  class PeekTL(bundle: TLBundle) extends ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPeekTL"
    @public val clock = IO(Input(Clock()))
    @public val channel = IO(Input(UInt(32.W)))
    @public val aBits: TLChannelA = IO(Input(bundle.a.bits))
    @public val aValid: Bool = IO(Input(bundle.a.valid))
    @public val dReady: Bool = IO(Input(bundle.d.ready))
    setInline("dpiPeekTL.sv",
      s"""module dpiPeekTL(
         |input clock,
         |input int channel,
         |input bit[${aBits.opcode.getWidth - 1}:0] aBits_opcode,
         |input bit[${aBits.param.getWidth - 1}:0] aBits_param,
         |input bit[${aBits.size.getWidth - 1}:0] aBits_size,
         |input bit[${aBits.source.getWidth - 1}:0] aBits_source,
         |input bit[${aBits.address.getWidth - 1}:0] aBits_address,
         |input bit[${aBits.mask.getWidth - 1}:0] aBits_mask,
         |input bit[${aBits.data.getWidth - 1}:0] aBits_data,
         |input bit aBits_corrupt,
         |input bit aValid,
         |input bit dReady
         |);
         |import "DPI-C" function void dpiPeekTL(
         |input int channel_id,
         |input bit[${aBits.opcode.getWidth-1}:0] a_opcode,
         |input bit[${aBits.param.getWidth-1}:0] a_param,
         |input bit[${aBits.size.getWidth-1}:0] a_size,
         |input bit[${aBits.source.getWidth-1}:0] a_source,
         |input bit[${aBits.address.getWidth-1}:0] a_address,
         |input bit[${aBits.mask.getWidth-1}:0] a_mask,
         |input bit[${aBits.data.getWidth-1}:0] a_data,
         |input bit a_corrupt,
         |input bit a_valid,
         |input bit d_ready
         |);
         |always @ (posedge clock) #(0.1) dpiPeekTL(
         |channel,
         |aBits_opcode,
         |aBits_param,
         |aBits_size,
         |aBits_source,
         |aBits_address,
         |aBits_mask,
         |aBits_data,
         |aBits_corrupt,
         |aValid,
         |dReady
         |);
         |endmodule
         |""".stripMargin
    )
  }

  @instantiable
  class PokeTL(bundle: TLBundle) extends ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPokeTL"
    @public val clock = IO(Input(Clock()))
    @public val channel = IO(Input(UInt(32.W)))
    @public val dBits = IO(Output(bundle.d.bits))
    @public val dValid = IO(Output(bundle.d.valid))
    @public val aReady = IO(Output(bundle.a.ready))
    setInline("dpiPokeTL.sv",
      s"""module dpiPokeTL(
         |input clock,
         |input int channel,
         |output bit[${dBits.opcode.getWidth-1}:0] dBits_opcode,
         |output bit[${dBits.param.getWidth-1}:0] dBits_param,
         |output bit[${dBits.size.getWidth-1}:0] dBits_size,
         |output bit[${dBits.source.getWidth-1}:0] dBits_source,
         |output bit[${dBits.sink.getWidth-1}:0] dBits_sink,
         |output bit[${dBits.denied.getWidth-1}:0] dBits_denied,
         |output bit[${dBits.data.getWidth-1}:0] dBits_data,
         |output bit dBits_corrupt,
         |output bit dValid,
         |output bit aReady
         |);
         |import "DPI-C" function void dpiPokeTL(
         |input int channel_id,
         |output bit[${dBits.opcode.getWidth-1}:0] d_opcode,
         |output bit[${dBits.param.getWidth-1}:0] d_param,
         |output bit[${dBits.size.getWidth-1}:0] d_size,
         |output bit[${dBits.source.getWidth-1}:0] d_source,
         |output bit[${dBits.sink.getWidth-1}:0] d_sink,
         |output bit[${dBits.denied.getWidth-1}:0] d_denied,
         |output bit[${dBits.data.getWidth-1}:0] d_data,
         |output bit d_corrupt,
         |output bit d_valid,
         |output bit a_ready
         |);
         |always @ (negedge clock) #(0.1) dpiPokeTL(
         |channel,
         |dBits_opcode,
         |dBits_param,
         |dBits_size,
         |dBits_source,
         |dBits_sink,
         |dBits_denied,
         |dBits_data,
         |dBits_corrupt,
         |dValid,
         |aReady
         |);
         |endmodule
         |""".stripMargin
    )
  }

  val peekTlDef = Definition(new PeekTL(dut.param.tlParam.bundle()))
  val pokeTlDef = Definition(new PokeTL(dut.param.tlParam.bundle()))
  tlPort.zipWithIndex.foreach { case (bundle, idx) =>
    val peek = Instance(peekTlDef)
    peek.clock := clock
    peek.channel := idx.U
    peek.aBits := bundle.a.bits
    peek.aValid := bundle.a.valid
    peek.dReady := bundle.d.ready

    val poke = Instance(pokeTlDef)
    poke.clock := clock
    poke.channel := idx.U
    bundle.d.bits := poke.dBits
    bundle.d.valid := poke.dValid
    bundle.a.ready := poke.aReady
  }

  done()
}
