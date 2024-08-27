// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2012-2014 The Regents of the University of California
// SPDX-FileCopyrightText: 2016-2017 SiFive, Inc
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.rocketv

import chisel3._
import chisel3.experimental.hierarchy.{instantiable, Instance, Instantiate}
import chisel3.experimental.{BaseModule, SerializableModule, SerializableModuleParameter}
import chisel3.probe.{define, Probe, ProbeValue}
import chisel3.util._
import chisel3.util.circt.ClockGate

class FPUWrite(param: FPUParameter) extends Bundle {
  val rfWen:      Bool = Bool()
  val rfWaddr:    UInt = UInt(5.W)
  val rfWdata:    UInt = UInt((param.fLen + 1).W)
  val rfWtypeTag: UInt = UInt(2.W)
}

class FPUProbe(param: FPUParameter) extends Bundle {
  val pipeWrite = new FPUWrite(param)
  val loadOrVectorWrite = new FPUWrite(param)
}

object FPUParameter {
  implicit def rwP: upickle.default.ReadWriter[FPUParameter] = upickle.default.macroRW[FPUParameter]
}

case class FPUParameter(
  useAsyncReset:  Boolean,
  useClockGating: Boolean,
  xLen:           Int,
  fLen:           Int,
  minFLen:        Int,
  sfmaLatency:    Int,
  dfmaLatency:    Int,
  divSqrt:        Boolean,
  hartIdLen:      Int)
    extends SerializableModuleParameter

class FPUInterface(parameter: FPUParameter) extends Bundle {
  val clock = Input(Clock())
  val reset = Input(if (parameter.useAsyncReset) AsyncReset() else Bool())
  val core = new FPUCoreIO(parameter.hartIdLen, parameter.xLen, parameter.fLen)
  val cp_req = Flipped(Decoupled(new FPInput(parameter.fLen))) //cp doesn't pay attn to kill sigs
  val cp_resp = Decoupled(new FPResult(parameter.fLen))
  val fpuProbe = Output(Probe(new FPUProbe(parameter), layers.Verification))
}

// TODO: all hardfloat module can be replaced by DWBB?
@instantiable
class FPU(val parameter: FPUParameter)
    extends FixedIORawModule(new FPUInterface(parameter))
    with SerializableModule[FPUParameter]
    with Public
    with ImplicitClock
    with ImplicitReset {
  override protected def implicitClock: Clock = io.clock
  override protected def implicitReset: Reset = io.reset

  val helper = new FPUHelper(parameter.minFLen, parameter.minFLen, parameter.xLen)
  val typeTagWbOffset = helper.typeTagWbOffset
  def recode(x:      UInt, tag: UInt): UInt = helper.recode(x, tag)
  def consistent(x:  UInt): Bool = helper.consistent(x)
  def unbox(x:       UInt, tag: UInt, exactType: Option[FType]): UInt = helper.unbox(x, tag, exactType)
  def box(x:         UInt, tag: UInt) = helper.box(x, tag)
  def typeTag(t:     FType) = helper.typeTag(t)
  def sanitizeNaN(x: UInt, t:   FType) = helper.sanitizeNaN(x, t)
  def maxType = helper.maxType
  val fLen = parameter.fLen
  val minFLen = parameter.minFLen
  val floatTypes = helper.floatTypes
  val S = helper.S
  val D = helper.D
  val H = helper.H
  object cfg {
    val sfmaLatency = parameter.sfmaLatency
    val dfmaLatency = parameter.dfmaLatency
    val divSqrt = parameter.divSqrt
  }

  val useClockGating = parameter.useClockGating
  val clock_en_reg = Reg(Bool())
  val clock_en = clock_en_reg || io.cp_req.valid
  val gated_clock =
    if (!useClockGating) io.clock
    else ClockGate(io.clock, clock_en)

  val id_ctrl = io.core.dec

  val ex_reg_valid = RegNext(io.core.valid, false.B)
  val ex_reg_inst = RegEnable(io.core.inst, io.core.valid)
  val ex_reg_ctrl = RegEnable(id_ctrl, io.core.valid)
  val ex_ra = List.fill(3)(Reg(UInt()))

  // load response
  val load_wb = RegNext(io.core.dmem_resp_val)
  val load_wb_typeTag = RegEnable(io.core.dmem_resp_type(1, 0) - typeTagWbOffset, io.core.dmem_resp_val)
  val load_wb_data = RegEnable(io.core.dmem_resp_data, io.core.dmem_resp_val)
  val load_wb_tag = RegEnable(io.core.dmem_resp_tag, io.core.dmem_resp_val)

  class FPUImpl { // entering gated-clock domain

    val req_valid = ex_reg_valid || io.cp_req.valid
    val ex_cp_valid = io.cp_req.fire
    val mem_cp_valid = RegNext(ex_cp_valid, false.B)
    val wb_cp_valid = RegNext(mem_cp_valid, false.B)
    val mem_reg_valid = RegInit(false.B)
    val killm = (io.core.killm || io.core.nack_mem) && !mem_cp_valid
    // Kill X-stage instruction if M-stage is killed.  This prevents it from
    // speculatively being sent to the div-sqrt unit, which can cause priority
    // inversion for two back-to-back divides, the first of which is killed.
    val killx = io.core.killx || mem_reg_valid && killm
    mem_reg_valid := ex_reg_valid && !killx || ex_cp_valid
    val mem_reg_inst = RegEnable(ex_reg_inst, ex_reg_valid)
    val wb_reg_valid = RegNext(mem_reg_valid && (!killm || mem_cp_valid), false.B)

    val cp_ctrl = Wire(new FPUCtrlSigs)
    cp_ctrl :<>= io.cp_req.bits.fpuControl
    io.cp_resp.valid := false.B
    io.cp_resp.bits.data := 0.U
    io.cp_resp.bits.exc := DontCare

    val ex_ctrl = Mux(ex_cp_valid, cp_ctrl, ex_reg_ctrl)
    val mem_ctrl = RegEnable(ex_ctrl, req_valid)
    val wb_ctrl = RegEnable(mem_ctrl, mem_reg_valid)

    // regfile
    val regfile = Mem(32, Bits((fLen + 1).W))
    when(load_wb) {
      val wdata = recode(load_wb_data, load_wb_typeTag)
      regfile(load_wb_tag) := wdata
      assert(consistent(wdata))
    }

    val ex_rs = ex_ra.map(a => regfile(a))
    when(io.core.valid) {
      when(id_ctrl.ren1) {
        when(!id_ctrl.swap12) { ex_ra(0) := io.core.inst(19, 15) }
        when(id_ctrl.swap12) { ex_ra(1) := io.core.inst(19, 15) }
      }
      when(id_ctrl.ren2) {
        when(id_ctrl.swap12) { ex_ra(0) := io.core.inst(24, 20) }
        when(id_ctrl.swap23) { ex_ra(2) := io.core.inst(24, 20) }
        when(!id_ctrl.swap12 && !id_ctrl.swap23) { ex_ra(1) := io.core.inst(24, 20) }
      }
      when(id_ctrl.ren3) { ex_ra(2) := io.core.inst(31, 27) }
    }
    val ex_rm = Mux(ex_reg_inst(14, 12) === 7.U, io.core.fcsr_rm, ex_reg_inst(14, 12))

    def fuInput(minT: Option[FType]): FPInput = {
      val req = Wire(new FPInput(fLen))
      val tag = ex_ctrl.typeTagIn
      req.fpuControl :#= ex_ctrl
      req.rm := ex_rm
      req.in1 := unbox(ex_rs(0), tag, minT)
      req.in2 := unbox(ex_rs(1), tag, minT)
      req.in3 := unbox(ex_rs(2), tag, minT)
      req.typ := ex_reg_inst(21, 20)
      req.fmt := ex_reg_inst(26, 25)
      req.fmaCmd := ex_reg_inst(3, 2) | (!ex_ctrl.ren3 && ex_reg_inst(27))
      when(ex_cp_valid) {
        req := io.cp_req.bits
        when(io.cp_req.bits.fpuControl.swap23) {
          req.in2 := io.cp_req.bits.in3
          req.in3 := io.cp_req.bits.in2
        }
      }
      req
    }

    val sfma = Instantiate(
      new FPUFMAPipe(
        FPUFMAPipeParameter(
          parameter.useAsyncReset,
          parameter.sfmaLatency,
          parameter.xLen,
          parameter.fLen,
          parameter.minFLen,
          FType.S
        )
      )
    )
    sfma.io.clock := io.clock
    sfma.io.reset := io.reset
    sfma.io.in.valid := req_valid && ex_ctrl.fma && ex_ctrl.typeTagOut === S
    sfma.io.in.bits := fuInput(Some(FType.S /*sfma.t*/ ))

    val fpiu = Instantiate(
      new FPToInt(
        FPToIntParameter(
          parameter.useAsyncReset,
          parameter.xLen,
          parameter.fLen,
          parameter.minFLen
        )
      )
    )
    fpiu.io.clock := io.clock
    fpiu.io.reset := io.reset
    fpiu.io.in.valid := req_valid && (ex_ctrl.toint || ex_ctrl.div || ex_ctrl.sqrt || (ex_ctrl.fastpipe && ex_ctrl.wflags))
    fpiu.io.in.bits := fuInput(None)
    io.core.store_data := fpiu.io.out.bits.store
    io.core.toint_data := fpiu.io.out.bits.toint
    when(fpiu.io.out.valid && mem_cp_valid && mem_ctrl.toint) {
      io.cp_resp.bits.data := fpiu.io.out.bits.toint
      io.cp_resp.valid := true.B
    }

    val ifpu = Instantiate(
      new IntToFP(
        IntToFPParameter(
          parameter.useAsyncReset,
          2,
          parameter.fLen,
          parameter.xLen,
          parameter.minFLen
        )
      )
    )
    ifpu.io.clock := io.clock
    ifpu.io.reset := io.reset
    ifpu.io.in.valid := req_valid && ex_ctrl.fromint
    ifpu.io.in.bits := fpiu.io.in.bits
    ifpu.io.in.bits.in1 := Mux(ex_cp_valid, io.cp_req.bits.in1, io.core.fromint_data)

    val fpmu = Instantiate(
      new FPToFP(
        FPToFPParameter(
          parameter.useAsyncReset,
          2,
          parameter.xLen,
          parameter.fLen,
          parameter.minFLen
        )
      )
    )
    fpmu.io.clock := io.clock
    fpmu.io.reset := io.reset
    fpmu.io.in.valid := req_valid && ex_ctrl.fastpipe
    fpmu.io.in.bits := fpiu.io.in.bits
    fpmu.io.lt := fpiu.io.out.bits.lt

    val divSqrt_wen = WireDefault(false.B)
    val divSqrt_inFlight = WireDefault(false.B)
    val divSqrt_waddr = Reg(UInt(5.W))
    val divSqrt_typeTag = Wire(UInt(log2Ceil(floatTypes.size).W))
    val divSqrt_wdata = Wire(UInt((parameter.fLen + 1).W))
    val divSqrt_flags = Wire(UInt(FPConstants.FLAGS_SZ.W))
    divSqrt_typeTag := DontCare
    divSqrt_wdata := DontCare
    divSqrt_flags := DontCare
    // writeback arbitration
    case class Pipe[T <: BaseModule](p: Instance[T], lat: Int, cond: (FPUCtrlSigs) => Bool, res: FPResult)
    val dfma = Option.when(fLen > 32)(
      Instantiate(
        new FPUFMAPipe(
          FPUFMAPipeParameter(
            parameter.useAsyncReset,
            parameter.dfmaLatency,
            parameter.xLen,
            parameter.fLen,
            parameter.minFLen,
            FType.D
          )
        )
      )
    )
    val hfma = Option.when(minFLen == 16)(
      Instantiate(
        new FPUFMAPipe(
          FPUFMAPipeParameter(
            parameter.useAsyncReset,
            parameter.sfmaLatency,
            parameter.xLen,
            parameter.fLen,
            parameter.minFLen,
            FType.H
          )
        )
      )
    )
    dfma.foreach { dfma =>
      dfma.io.clock := io.clock
      dfma.io.reset := io.reset
      dfma.io.in.valid := req_valid && ex_ctrl.fma && ex_ctrl.typeTagOut === D
      dfma.io.in.bits := fuInput(Some(FType.D /*dfma.t*/ ))
    }
    hfma.foreach { hfma =>
      hfma.io.clock := io.clock
      hfma.io.reset := io.reset
      hfma.io.in.valid := req_valid && ex_ctrl.fma && ex_ctrl.typeTagOut === H
      hfma.io.in.bits := fuInput(Some(FType.H /*hfma.t*/ ))
    }
    val pipes = List(
      Pipe(fpmu, 2, (c: FPUCtrlSigs) => c.fastpipe, fpmu.io.out.bits),
      Pipe(ifpu, 2, (c: FPUCtrlSigs) => c.fromint, ifpu.io.out.bits),
      Pipe(sfma, cfg.sfmaLatency, (c: FPUCtrlSigs) => c.fma && c.typeTagOut === S, sfma.io.out.bits)
    ) ++
      dfma.map(dfma =>
        Pipe(dfma, cfg.dfmaLatency, (c: FPUCtrlSigs) => c.fma && c.typeTagOut === D, dfma.io.out.bits)
      ) ++
      hfma.map(hfma => Pipe(hfma, cfg.sfmaLatency, (c: FPUCtrlSigs) => c.fma && c.typeTagOut === H, hfma.io.out.bits))
    def latencyMask(c: FPUCtrlSigs, offset: Int) = {
      require(pipes.forall(_.lat >= offset))
      pipes.map(p => Mux(p.cond(c), (1 << p.lat - offset).U, 0.U)).reduce(_ | _)
    }
    def pipeid(c: FPUCtrlSigs) = pipes.zipWithIndex.map(p => Mux(p._1.cond(c), p._2.U, 0.U)).reduce(_ | _)
    val maxLatency = pipes.map(_.lat).max
    val memLatencyMask = latencyMask(mem_ctrl, 2)

    class WBInfo extends Bundle {
      val rd = UInt(5.W)
      val typeTag = UInt(log2Ceil(floatTypes.size).W)
      val cp = Bool()
      val pipeid = UInt(log2Ceil(pipes.size).W)
    }

    val wen = RegInit(0.U((maxLatency - 1).W))
    val wbInfo = Reg(Vec(maxLatency - 1, new WBInfo))
    val mem_wen = mem_reg_valid && (mem_ctrl.fma || mem_ctrl.fastpipe || mem_ctrl.fromint)
    val write_port_busy = RegEnable(
      mem_wen && (memLatencyMask & latencyMask(ex_ctrl, 1)).orR || (wen & latencyMask(ex_ctrl, 0)).orR,
      req_valid
    )

    for (i <- 0 until maxLatency - 2) {
      when(wen(i + 1)) { wbInfo(i) := wbInfo(i + 1) }
    }
    wen := wen >> 1
    when(mem_wen) {
      when(!killm) {
        wen := wen >> 1 | memLatencyMask
      }
      for (i <- 0 until maxLatency - 1) {
        when(!write_port_busy && memLatencyMask(i)) {
          wbInfo(i).cp := mem_cp_valid
          wbInfo(i).typeTag := mem_ctrl.typeTagOut
          wbInfo(i).pipeid := pipeid(mem_ctrl)
          wbInfo(i).rd := mem_reg_inst(11, 7)
        }
      }
    }

    val waddr = Mux(divSqrt_wen, divSqrt_waddr, wbInfo(0).rd)
    val wtypeTag = Mux(divSqrt_wen, divSqrt_typeTag, wbInfo(0).typeTag)
    val wdata = box(Mux(divSqrt_wen, divSqrt_wdata, VecInit(pipes.map(_.res.data))(wbInfo(0).pipeid)), wtypeTag)
    val wexc = VecInit(pipes.map(_.res.exc))(wbInfo(0).pipeid)
    when((!wbInfo(0).cp && wen(0)) || divSqrt_wen) {
      assert(consistent(wdata))
      regfile(waddr) := wdata
    }

    when(wbInfo(0).cp && wen(0)) {
      io.cp_resp.bits.data := wdata
      io.cp_resp.valid := true.B
    }
    io.cp_req.ready := !ex_reg_valid

    val wb_toint_valid = wb_reg_valid && wb_ctrl.toint
    val wb_toint_exc = RegEnable(fpiu.io.out.bits.exc, mem_ctrl.toint)
    io.core.fcsr_flags.valid := wb_toint_valid || divSqrt_wen || wen(0)
    io.core.fcsr_flags.bits :=
      Mux(wb_toint_valid, wb_toint_exc, 0.U) |
        Mux(divSqrt_wen, divSqrt_flags, 0.U) |
        Mux(wen(0), wexc, 0.U)

    val divSqrt_write_port_busy = (mem_ctrl.div || mem_ctrl.sqrt) && wen.orR
    io.core.fcsr_rdy := !(ex_reg_valid && ex_ctrl.wflags || mem_reg_valid && mem_ctrl.wflags || wb_reg_valid && wb_ctrl.toint || wen.orR || divSqrt_inFlight)
    io.core.nack_mem := write_port_busy || divSqrt_write_port_busy || divSqrt_inFlight
    def useScoreboard(f: ((Pipe[_], Int)) => Bool) =
      pipes.zipWithIndex.filter(_._1.lat > 3).map(x => f(x)).fold(false.B)(_ || _)
    io.core.sboard_set := wb_reg_valid && !wb_cp_valid && RegNext(
      useScoreboard(_._1.cond(mem_ctrl)) || mem_ctrl.div || mem_ctrl.sqrt
    )
    io.core.sboard_clr := !wb_cp_valid && (divSqrt_wen || (wen(0) && useScoreboard(x => wbInfo(0).pipeid === x._2.U)))
    io.core.sboard_clra := waddr

    def isOneOf(x: UInt, s: Seq[UInt]): Bool = VecInit(s.map(x === _)).asUInt.orR
    // we don't currently support round-max-magnitude (rm=4)
    io.core.illegal_rm := isOneOf(io.core.inst(14, 12), Seq(5.U, 6.U)) || io.core.inst(
      14,
      12
    ) === 7.U && io.core.fcsr_rm >= 5.U

    if (cfg.divSqrt) {
      val divSqrt_inValid = mem_reg_valid && (mem_ctrl.div || mem_ctrl.sqrt) && !divSqrt_inFlight
      val divSqrt_killed = RegNext(divSqrt_inValid && killm, true.B)
      when(divSqrt_inValid) {
        divSqrt_waddr := mem_reg_inst(11, 7)
      }

      for (t <- floatTypes) {
        val tag = mem_ctrl.typeTagOut
        val divSqrt = withReset(divSqrt_killed) { Module(new hardfloat.DivSqrtRecFN_small(t.exp, t.sig, 0)) }
        divSqrt.io.inValid := divSqrt_inValid && tag === typeTag(t).U
        divSqrt.io.sqrtOp := mem_ctrl.sqrt
        divSqrt.io.a := maxType.unsafeConvert(fpiu.io.out.bits.in.in1, t)
        divSqrt.io.b := maxType.unsafeConvert(fpiu.io.out.bits.in.in2, t)
        divSqrt.io.roundingMode := fpiu.io.out.bits.in.rm
        divSqrt.io.detectTininess := hardfloat.consts.tininess_afterRounding

        when(!divSqrt.io.inReady) { divSqrt_inFlight := true.B } // only 1 in flight

        when(divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt) {
          divSqrt_wen := !divSqrt_killed
          divSqrt_wdata := sanitizeNaN(divSqrt.io.out, t)
          divSqrt_flags := divSqrt.io.exceptionFlags
          divSqrt_typeTag := typeTag(t).U
        }
      }

      when(divSqrt_killed) { divSqrt_inFlight := false.B }
    } else {
      when(id_ctrl.div || id_ctrl.sqrt) { io.core.illegal_rm := true.B }
    }

    // gate the clock
    clock_en_reg := !useClockGating.B ||
      io.core.keep_clock_enabled || // chicken bit
      io.core.valid || // ID stage
      req_valid || // EX stage
      mem_reg_valid || mem_cp_valid || // MEM stage
      wb_reg_valid || wb_cp_valid || // WB stage
      wen.orR || divSqrt_inFlight || // post-WB stage
      io.core.dmem_resp_val // load writeback

    /* Probes */
    layer.block(layers.Verification) {
      val probeWire = Wire(new FPUProbe(parameter))
      define(io.fpuProbe, ProbeValue(probeWire))

      probeWire.loadOrVectorWrite.rfWen := load_wb
      probeWire.loadOrVectorWrite.rfWaddr := load_wb_tag
      probeWire.loadOrVectorWrite.rfWdata := recode(load_wb_data, load_wb_typeTag)
      probeWire.loadOrVectorWrite.rfWtypeTag := load_wb_typeTag
      probeWire.pipeWrite.rfWen := (!wbInfo(0).cp && wen(0)) || divSqrt_wen
      probeWire.pipeWrite.rfWaddr := waddr
      probeWire.pipeWrite.rfWdata := wdata
      probeWire.pipeWrite.rfWtypeTag := wtypeTag
    }
  } // leaving gated-clock domain
  val fpuImpl = withClockAndReset(gated_clock, io.reset) { new FPUImpl }
}


object FType {
  implicit def rwP: upickle.default.ReadWriter[FType] = upickle.default.macroRW[FType]

  val H = FType(5, 11)
  val S = FType(8, 24)
  val D = FType(11, 53)

  val all = List(H, S, D)
}

case class FType(exp: Int, sig: Int) {
  def ieeeWidth = exp + sig
  def recodedWidth = ieeeWidth + 1

  def ieeeQNaN = ((BigInt(1) << (ieeeWidth - 1)) - (BigInt(1) << (sig - 2))).U(ieeeWidth.W)
  def qNaN = ((BigInt(7) << (exp + sig - 3)) + (BigInt(1) << (sig - 2))).U(recodedWidth.W)
  def isNaN(x:  UInt) = x(sig + exp - 1, sig + exp - 3).andR
  def isSNaN(x: UInt) = isNaN(x) && !x(sig - 2)

  def classify(x: UInt) = {
    val sign = x(sig + exp)
    val code = x(exp + sig - 1, exp + sig - 3)
    val codeHi = code(2, 1)
    val isSpecial = codeHi === 3.U

    val isHighSubnormalIn = x(exp + sig - 3, sig - 1) < 2.U
    val isSubnormal = code === 1.U || codeHi === 1.U && isHighSubnormalIn
    val isNormal = codeHi === 1.U && !isHighSubnormalIn || codeHi === 2.U
    val isZero = code === 0.U
    val isInf = isSpecial && !code(0)
    val isNaN = code.andR
    val isSNaN = isNaN && !x(sig - 2)
    val isQNaN = isNaN && x(sig - 2)

    Cat(
      isQNaN,
      isSNaN,
      isInf && !sign,
      isNormal && !sign,
      isSubnormal && !sign,
      isZero && !sign,
      isZero && sign,
      isSubnormal && sign,
      isNormal && sign,
      isInf && sign
    )
  }

  // convert between formats, ignoring rounding, range, NaN
  def unsafeConvert(x: UInt, to: FType) = if (this == to) x
  else {
    val sign = x(sig + exp)
    val fractIn = x(sig - 2, 0)
    val expIn = x(sig + exp - 1, sig - 1)
    val fractOut = fractIn << to.sig >> sig
    val expOut = {
      val expCode = expIn(exp, exp - 2)
      val commonCase = (expIn + (1 << to.exp).U) - (1 << exp).U
      Mux(expCode === 0.U || expCode >= 6.U, Cat(expCode, commonCase(to.exp - 3, 0)), commonCase(to.exp, 0))
    }
    Cat(sign, expOut, fractOut)
  }

  private def ieeeBundle = {
    val expWidth = exp
    class IEEEBundle extends Bundle {
      val sign = Bool()
      val exp = UInt(expWidth.W)
      val sig = UInt((ieeeWidth - expWidth - 1).W)
    }
    new IEEEBundle
  }

  def unpackIEEE(x: UInt) = x.asTypeOf(ieeeBundle)

  def recode(x: UInt) = hardfloat.recFNFromFN(exp, sig, x)
  def ieee(x:   UInt) = hardfloat.fNFromRecFN(exp, sig, x)
}

// TODO: migrate into FPUParameter
class FPUHelper(minFLen: Int, fLen: Int, xLen: Int) {
  require(fLen == 0 || FType.all.exists(_.ieeeWidth == fLen))
  val minXLen = 32
  val nIntTypes = log2Ceil(xLen / minXLen) + 1
  def floatTypes = FType.all.filter(t => minFLen <= t.ieeeWidth && t.ieeeWidth <= fLen)
  def minType = floatTypes.head
  def maxType = floatTypes.last
  def prevType(t: FType) = floatTypes(typeTag(t) - 1)
  def maxExpWidth = maxType.exp
  def maxSigWidth = maxType.sig
  def typeTag(t: FType) = floatTypes.indexOf(t)
  def typeTagWbOffset = (FType.all.indexOf(minType) + 1).U
  def typeTagGroup(t: FType) = (if (floatTypes.contains(t)) typeTag(t) else typeTag(maxType)).U
  // typeTag
  def H = typeTagGroup(FType.H)
  def S = typeTagGroup(FType.S)
  def D = typeTagGroup(FType.D)
  def I = typeTag(maxType).U

  private def isBox(x: UInt, t: FType): Bool = x(t.sig + t.exp, t.sig + t.exp - 4).andR

  private def box(x: UInt, xt: FType, y: UInt, yt: FType): UInt = {
    require(xt.ieeeWidth == 2 * yt.ieeeWidth)
    val swizzledNaN = Cat(
      x(xt.sig + xt.exp, xt.sig + xt.exp - 3),
      x(xt.sig - 2, yt.recodedWidth - 1).andR,
      x(xt.sig + xt.exp - 5, xt.sig),
      y(yt.recodedWidth - 2),
      x(xt.sig - 2, yt.recodedWidth - 1),
      y(yt.recodedWidth - 1),
      y(yt.recodedWidth - 3, 0)
    )
    Mux(xt.isNaN(x), swizzledNaN, x)
  }

  // implement NaN unboxing for FU inputs
  def unbox(x: UInt, tag: UInt, exactType: Option[FType]): UInt = {
    val outType = exactType.getOrElse(maxType)
    def helper(x: UInt, t: FType): Seq[(Bool, UInt)] = {
      val prev =
        if (t == minType) {
          Seq()
        } else {
          val prevT = prevType(t)
          val unswizzled = Cat(x(prevT.sig + prevT.exp - 1), x(t.sig - 1), x(prevT.sig + prevT.exp - 2, 0))
          val prev = helper(unswizzled, prevT)
          val isbox = isBox(x, t)
          prev.map(p => (isbox && p._1, p._2))
        }
      prev :+ (true.B, t.unsafeConvert(x, outType))
    }

    val (oks, floats) = helper(x, maxType).unzip
    if (exactType.isEmpty || floatTypes.size == 1) {
      Mux(VecInit(oks)(tag), VecInit(floats)(tag), maxType.qNaN)
    } else {
      val t = exactType.get
      floats(typeTag(t)) | Mux(oks(typeTag(t)), 0.U, t.qNaN)
    }
  }

  // make sure that the redundant bits in the NaN-boxed encoding are consistent
  def consistent(x: UInt): Bool = {
    def helper(x: UInt, t: FType): Bool = if (typeTag(t) == 0) true.B
    else {
      val prevT = prevType(t)
      val unswizzled = Cat(x(prevT.sig + prevT.exp - 1), x(t.sig - 1), x(prevT.sig + prevT.exp - 2, 0))
      val prevOK = !isBox(x, t) || helper(unswizzled, prevT)
      val curOK = !t.isNaN(x) || x(t.sig + t.exp - 4) === x(t.sig - 2, prevT.recodedWidth - 1).andR
      prevOK && curOK
    }
    helper(x, maxType)
  }

  // generate a NaN box from an FU result
  def box(x: UInt, t: FType): UInt = {
    if (t == maxType) {
      x
    } else {
      val nt = floatTypes(typeTag(t) + 1)
      val bigger = box(((BigInt(1) << nt.recodedWidth) - 1).U, nt, x, t)
      bigger | ((BigInt(1) << maxType.recodedWidth) - (BigInt(1) << nt.recodedWidth)).U
    }
  }

  // generate a NaN box from an FU result
  def box(x: UInt, tag: UInt): UInt = {
    val opts = floatTypes.map(t => box(x, t))
    VecInit(opts)(tag)
  }

  // zap bits that hardfloat thinks are don't-cares, but we do care about
  def sanitizeNaN(x: UInt, t: FType): UInt = {
    if (typeTag(t) == 0) {
      x
    } else {
      val maskedNaN = x & ~((BigInt(1) << (t.sig - 1)) | (BigInt(1) << (t.sig + t.exp - 4))).U(t.recodedWidth.W)
      Mux(t.isNaN(x), maskedNaN, x)
    }
  }

  // implement NaN boxing and recoding for FL*/fmv.*.x
  def recode(x: UInt, tag: UInt): UInt = {
    def helper(x: UInt, t: FType): UInt = {
      if (typeTag(t) == 0) {
        t.recode(x)
      } else {
        val prevT = prevType(t)
        box(t.recode(x), t, helper(x, prevT), prevT)
      }
    }

    // fill MSBs of subword loads to emulate a wider load of a NaN-boxed value
    val boxes = floatTypes.map(t => ((BigInt(1) << maxType.ieeeWidth) - (BigInt(1) << t.ieeeWidth)).U)
    helper(VecInit(boxes)(tag) | x, maxType)
  }

  // implement NaN unboxing and un-recoding for FS*/fmv.x.*
  def ieee(x: UInt, t: FType = maxType): UInt = {
    if (typeTag(t) == 0) {
      t.ieee(x)
    } else {
      val unrecoded = t.ieee(x)
      val prevT = prevType(t)
      val prevRecoded = Cat(x(prevT.recodedWidth - 2), x(t.sig - 1), x(prevT.recodedWidth - 3, 0))
      val prevUnrecoded = ieee(prevRecoded, prevT)
      Cat(unrecoded >> prevT.ieeeWidth, Mux(t.isNaN(x), prevUnrecoded, unrecoded(prevT.ieeeWidth - 1, 0)))
    }
  }
}
