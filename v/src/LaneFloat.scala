package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import hardfloat._

case class LaneFloatParam(datapathWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.divider //todo switch to float
  val inputBundle = new LaneFloatRequest(datapathWidth)
  val outputBundle = new LaneFloatResponse(datapathWidth)
}

/** UOP encoding
  *
  * for FMA
  * up[3] = 1 for add/sub
  * up[2] = 1 for reverse in rsub and reversed FMA
  * up[1:0] for FMA sign, 0 for positive, 1 for negative
  *
  * For DIV
  * 0001 for div
  * 0010 for rdiv
  * 1000 for sqrt
  *
  *
  * For CompareModule
  * 0001 EQ
  * 0000 NQ
  * 0010 LT
  * 0011 LE
  * 0100 GT
  * 0101 GE
  *
  * 1000 min
  * 1100 max
  *
  *
  * none of these 3
  *
  * 0001 SGNJ
  * 0010 SGNJN
  * 0011 SGNJX

  * 0100 classify
  * 0101 merge
  * 0110 sqrt7
  * 0111 rec7
  *
  * 1000 to float
  * 1010 to sint
  * 1001 to uint
  * 1110 to sint tr
  * 1101 to uint tr
  *
  */
class LaneFloatRequest(datapathWidth: Int) extends Bundle{
  val unsigned = Bool()
  val src   = Vec(3, UInt(datapathWidth.W))
  val uop = UInt(4.W)
  val UnitSelet = UInt(2.W)
  val roundingMode = UInt(2.W)
}

class LaneFloatResponse(datapathWidth: Int)  extends Bundle{
  val output = UInt(datapathWidth.W)
  val exceptionFlags = UInt(5.W)
}

class LaneFloat(val parameter: LaneFloatParam) extends VFUModule(parameter) with SerializableModule[LaneFloatParam]{
  val response: LaneFloatResponse = Wire(new LaneFloatResponse(parameter.datapathWidth))
  val request : LaneFloatRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val recIn0 = recFNFromFN(8, 24, request.src(0))
  val recIn1 = recFNFromFN(8, 24, request.src(1))
  val recIn2 = recFNFromFN(8, 24, request.src(2))

  val uop = RegEnable(request.uop, 0.U, requestIO.fire)

  val FMA = request.UnitSelet === 0.U
  val DIV = request.UnitSelet === 1.U
  val Compare = request.UnitSelet === 2.U
  val Other = request.UnitSelet === 3.U

  /** DIV insn response after  muticycles
    * Not Div insn respond at next cycle
    */

  val DivSqrtSelect = RegEnable(DIV     , false.B, requestIO.fire)
  val FMASelect     = RegEnable(FMA     , false.B, requestIO.fire)
  val CompareSelect = RegEnable(Compare , false.B, requestIO.fire)
  val OtherSelect   = RegEnable(Other   , false.B, requestIO.fire)

  val DivsqrtOccupied    = RegInit(false.B)
  val FastWorking = RegInit(false.B)

  val VFPUresult = Wire(UInt(32.W))
  val VFPUflags = Wire(UInt(5.W))

  FastWorking := requestIO.fire && !DIV

  /** FMA
    *
    * addsub(with rsub) rsub mul  maf  rmaf
    * a  src(0)               src(1)  src(0)  src(0)  src(0)
    * b  1                 1    src(1)  src(1)  src(2)
    * c  src(1)               src(0)  0    src(2)  src(1)
    *
    * */

  /** MAF control */
  val rsub   = FMA && uop(3,2) === 3.U
  val addsub = FMA && request.uop(3)
  val maf    = FMA && (uop(3,2) === 0.U)
  val rmaf   = FMA && (uop(3,2) === 1.U)

  val fmaIn0 = Mux(rsub, recIn1, recIn0)
  val fmaIn1 = Mux(addsub, 1.U, Mux(rmaf, recIn2, recIn1))
  val fmaIn2 = Mux(rsub, recIn0,
      Mux(maf, recIn2,
        recIn1) )

  val mulAddRecFN = Module(new MulAddRecFN(8, 24))
  mulAddRecFN.io.op := request.uop(1,0)
  mulAddRecFN.io.a := fmaIn0
  mulAddRecFN.io.b := fmaIn1
  mulAddRecFN.io.c := fmaIn2
  mulAddRecFN.io.roundingMode := request.roundingMode //todo decode it
  mulAddRecFN.io.detectTininess := false.B

  /** DIV
    *
    * 0001 for div
    * 0010 for rdiv
    * 1000 for sqrt
    * 1001 for sqrt7
    * 1010 for rec7
    *
    *   div rdiv  sqrt      rec7   sqrt7phase0  sqrt7phase1
    * a src(0)  src(1)  dontcare  1.U    dontcare      1.U
    * b src(1)  src(0)  src(0)       src(0)    src(0)           result
    *
    */

  val div      = DIV && (uop === "b0001".U)
  val rdiv     = DIV && (uop === "b0010".U)
  val sqrt     = DIV && (uop === "b1000".U)
  val sqrt7    = DIV && (uop === "b1001".U)
  val rec7     = DIV && (uop === "b1001".U)

  val sqrt7Select = RegNext(requestIO.fire && DIV && sqrt7 , false.B)
  val rec7Select  = RegNext(requestIO.fire && DIV && rec7  , false.B)

  val sqrt7Phase1Next = Wire(Bool())
  val sqrt7Phase1 = RegNext(sqrt7Phase1Next, false.B)
  val sqrt7Phase1Valid = RegInit(false.B)

  val divIn0 = Mux(div, recIn0,
    Mux(rdiv, recIn1,
      Mux(rec7 || sqrt7Phase1, 1.U, DontCare)))
  val divIn1 = Mux(div, recIn1,
    Mux(sqrt7Phase1, DivsqrtFnOut, recIn0))

  val DivsqrtResult = Wire(UInt(32.W))
  val divSqrt = Module(new DivSqrtRecFN_small(8, 24,0))

  divSqrt.io.a := divIn0
  divSqrt.io.b := divIn1
  divSqrt.io.roundingMode := request.roundingMode //todo decode it
  divSqrt.io.detectTininess := 0.U
  divSqrt.io.sqrtOp := uop === "b1000".U
  divSqrt.io.inValid := (requestIO.fire && DIV) || sqrt7Phase1Valid

  val DivsqrtFnOut = fNFromRecFN(8, 24, divSqrt.io.out)
  val DivsqrtValid = Mux(sqrt7Select, divSqrt.io.outValid_sqrt && sqrt7Phase1, divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt)
  DivsqrtResult := Mux(rec7Select, DivsqrtFnOut(23,17),
    Mux(sqrt7Select && sqrt7Phase1, DivsqrtFnOut(23,17), DivsqrtFnOut))
  sqrt7Phase1Next := sqrt7Phase1Valid || (!DivsqrtValid &&  sqrt7Phase1)
  sqrt7Phase1Valid := sqrt7Select  && divSqrt.io.outValid_sqrt

  /** CompareModule
    *
    * 0001 EQ
    * 0000 NQ
    * 0010 LT
    * 0011 LE
    * 0100 GT
    * 0101 GE
    *
    * 1000 min
    * 1100 max
    *
    */
  val CompareModule = Module(new CompareRecFN(8, 24))
  CompareModule.io.a := recIn0
  CompareModule.io.b := recIn1
  CompareModule.io.signaling := false.B
  val CompareResult = Wire(UInt(32.W))
  val Compareflags = Wire(UInt(5.W))
  CompareResult := Mux(uop === "b0001".U, CompareModule.io.eq,
    Mux(uop === "b0000".U, !CompareModule.io.eq,
      Mux(uop === "b0010".U, CompareModule.io.lt,
        Mux(uop === "b0011".U, CompareModule.io.lt || CompareModule.io.eq,
          Mux(uop === "b0100".U, CompareModule.io.gt,
            Mux(uop === "b0101".U, CompareModule.io.gt || CompareModule.io.eq,
              Mux(uop === "b1000".U , Mux(CompareModule.io.lt, request.src(0), request.src(1)),
                Mux(uop === "b1100".U,Mux(CompareModule.io.gt, request.src(0), request.src(1)),
                  false.B))))))))// todo false.B for illegal
  Compareflags := CompareModule.io.exceptionFlags


  /** Other
    *
    * uop(3,2) =
    * 1x => convert
    * 00 => sgnj
    * 01 => clasify,merge
    *
    *
    * 0001 SGNJ
    * 0010 SGNJN
    * 0011 SGNJX
    *
    * 0100 classify
    * 0101 merge
    *
    * 1000 to float
    * 1010 to sint
    * 1001 to uint
    * 1110 to sint tr
    * 1101 to uint tr
    *
    * */
  val IntToFn = Module(new INToRecFN(32, 8, 24))
  IntToFn.io.in := request.src(0)
  IntToFn.io.signedIn := request.unsigned
  IntToFn.io.roundingMode := request.roundingMode
  IntToFn.io.detectTininess := false.B

  /** io.signedOut define output sign
    *
    * false for toUInt
    * true for toInt
    */
  val FnToInt = Module(new RecFNToIN(8, 24, 32))
  val ConvertRtz = (uop(3,2) === 3.U) && Compare
  FnToInt.io.in := recIn0
  FnToInt.io.roundingMode := Mux(ConvertRtz, "b001".U(3.W), request.roundingMode)
  FnToInt.io.signedOut := !(uop(3) && uop(0))

  val ConvertResult = Wire(UInt(32.W))
  val ConvertFlags = Wire(UInt(5.W))
  ConvertResult := Mux(uop === "b1000".U, fNFromRecFN(8, 24, IntToFn.io.out), FnToInt.io.out)
  ConvertFlags  := Mux(uop === "b1000".U, IntToFn.io.exceptionFlags, FnToInt.io.intExceptionFlags)

  /** sgnj
    *
    * 0001 SGNJ
    * 0010 SGNJN
    * 0011 SGNJX
    */
  val sgnjresult = Wire(UInt(32.W))
  val sgnjSign = Mux(Other && uop === 1.U, request.src(1)(31),
    Mux(Other && uop === 2.U, !request.src(1)(31),
      Mux(Other && uop ===3.U, request.src(1)(31) ^ request.src(0)(31), false.B)))
  sgnjresult := Cat(sgnjSign, request.src(0)(30,0))

  val OtherResult = Wire(UInt(32.W))
  OtherResult := Mux(uop(3),ConvertResult,
    Mux(uop(3,2) === "b00".U, sgnjresult,
      Mux(uop === "b0100".U, classifyRecFN(8, 24, recFNFromFN(8, 24, request.src(0))), 0.U)))


  /** stateMachine */
  switch(DivsqrtOccupied) {
    is(false.B) {
      DivsqrtOccupied := requestIO.fire && DIV
    }
    is(true.B) {
      DivsqrtOccupied := DivsqrtValid
    }
  }

  /** Output */
  responseIO.ready := !DivsqrtOccupied
  responseIO.valid := DivsqrtValid || FastWorking

  VFPUresult := Mux(DivSqrtSelect, DivsqrtResult,
    Mux(FMASelect, fNFromRecFN(8, 23, mulAddRecFN.io.out),
      Mux(CompareSelect , CompareResult,
        Mux(OtherSelect , OtherResult,
          0.U))))// todo 0.U for illegal

  VFPUflags := Mux(DivSqrtSelect, divSqrt.io.exceptionFlags,
    Mux(FMASelect, fNFromRecFN(8, 23, mulAddRecFN.io.exceptionFlags),
      Mux(CompareSelect , Compareflags,
        Mux(OtherSelect && uop(3), ConvertFlags,
          0.U))))

  response.output := VFPUresult
  response.exceptionFlags := VFPUflags
}
