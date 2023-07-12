package v

import chisel3._
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util._
import hardfloat._

object LaneFloatParam {
  implicit def rw: upickle.default.ReadWriter[LaneFloatParam] = upickle.default.macroRW
}

case class LaneFloatParam(datapathWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.float
  val inputBundle = new LaneFloatRequest(datapathWidth)
  val outputBundle = new LaneFloatResponse(datapathWidth)
}

/** UOP encoding
  *
  * for fmaEn
  * up[3] = 1 for add/sub
  * up[2] = 1 for reverse in rsub and reversed fmaEn
  * up[1:0] for fmaEn sign, 0 for positive, 1 for negative
  *
  * For divEn
  * 0001 for div
  * 0010 for rdiv
  * 1000 for sqrt
  *
  *
  * For compareModule
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
  val sign = Bool()
  val src   = Vec(3, UInt(datapathWidth.W))
  val opcode = UInt(4.W)
  val unitSelet = UInt(2.W)
  val roundingMode = UInt(3.W)
}

class LaneFloatResponse(datapathWidth: Int)  extends Bundle{
  val data = UInt(datapathWidth.W)
  val exceptionFlags = UInt(5.W)
}

class LaneFloat(val parameter: LaneFloatParam) extends VFUModule(parameter) with SerializableModule[LaneFloatParam]{
  val response: LaneFloatResponse = Wire(new LaneFloatResponse(parameter.datapathWidth))
  val request : LaneFloatRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val recIn0 = recFNFromFN(8, 24, request.src(0))
  val recIn1 = recFNFromFN(8, 24, request.src(1))
  val recIn2 = recFNFromFN(8, 24, request.src(2))

  val uop = RegEnable(request.opcode, 0.U, requestIO.fire)

  val unitSeleOH = UIntToOH(request.unitSelet)

  val fmaEn     = unitSeleOH(0)
  val divEn     = unitSeleOH(1)
  val compareEn = unitSeleOH(2)
  val otherEn   = unitSeleOH(3)

  /** divEn insn response after  muticycles
    * Not Div insn respond at next cycle
    */

  val unitSelectReg = RegEnable(unitSeleOH, 0.U, requestIO.fire)

  val divOccupied    = RegInit(false.B)
  val fastWorking = RegInit(false.B)
  val fastWorkingValid = RegNext(fastWorking, false.B)

  val laneFloatResultNext = Wire(UInt(32.W))
  val laneFloatFlagsNext = Wire(UInt(5.W))
  val laneFloatResultReg = RegNext(laneFloatResultNext)
  val laneFloatFlagsReg  = RegNext(laneFloatFlagsNext)

  fastWorking := requestIO.fire && !divEn

  /** fmaEn
    *
    * addsub(with rsub) rsub mul  maf  rmaf
    * a  src(0)               src(1)  src(0)  src(0)  src(0)
    * b  1                 1    src(1)  src(1)  src(2)
    * c  src(1)               src(0)  0    src(2)  src(1)
    *
    * */

  /** MAF control */
  val rsub   = fmaEn && uop(3,2) === 3.U
  val addsub = fmaEn && request.opcode(3)
  val maf    = fmaEn && (uop(3,2) === 0.U)
  val rmaf   = fmaEn && (uop(3,2) === 1.U)

  val fmaIn0 = Mux(rsub, recIn1, recIn0)
  val fmaIn1 = Mux(addsub, 1.U, Mux(rmaf, recIn2, recIn1))
  val fmaIn2 = Mux(rsub, recIn0,
    Mux(maf, recIn2,
      recIn1) )

  val mulAddRecFN = Module(new MulAddRecFN(8, 24))
  mulAddRecFN.io.op := request.opcode(1,0)
  mulAddRecFN.io.a := fmaIn0
  mulAddRecFN.io.b := fmaIn1
  mulAddRecFN.io.c := fmaIn2
  mulAddRecFN.io.roundingMode := request.roundingMode //todo decode it
  mulAddRecFN.io.detectTininess := false.B

  /** divEn
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

  val div      = divEn && (uop === "b0001".U)
  val rdiv     = divEn && (uop === "b0010".U)
  val sqrt     = divEn && (uop === "b1000".U)
  val sqrt7    = divEn && (uop === "b1001".U)
  val rec7     = divEn && (uop === "b1001".U)

  val sqrt7Select = RegNext(requestIO.fire && divEn && sqrt7 , false.B)
  val rec7Select  = RegNext(requestIO.fire && divEn && rec7  , false.B)

  val sqrt7Phase1Next = Wire(Bool())
  val sqrt7Phase1 = RegNext(sqrt7Phase1Next, false.B)
  val sqrt7Phase1Valid = RegInit(false.B)
  val divSqrt = Module(new DivSqrtRecFN_small(8, 24,0))
  val divsqrtFnOut = fNFromRecFN(8, 24, divSqrt.io.out)

  val divIn0 = Mux(div, recIn0,
    Mux(rdiv, recIn1,
      Mux(rec7 || sqrt7Phase1, 1.U, DontCare)))
  val divIn1 = Mux(div, recIn1,
    Mux(sqrt7Phase1, divsqrtFnOut, recIn0))

  val divsqrtResult = Wire(UInt(32.W))


  divSqrt.io.a := divIn0
  divSqrt.io.b := divIn1
  divSqrt.io.roundingMode := request.roundingMode //todo decode it
  divSqrt.io.detectTininess := 0.U
  divSqrt.io.sqrtOp := uop === "b1000".U
  divSqrt.io.inValid := (requestIO.fire && divEn) || sqrt7Phase1Valid

  val divsqrtValid = Mux(sqrt7Select, divSqrt.io.outValid_sqrt && sqrt7Phase1, divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt)
  divsqrtResult := Mux(rec7Select, divsqrtFnOut(23,17),
    Mux(sqrt7Select && sqrt7Phase1, divsqrtFnOut(23,17), divsqrtFnOut))
  sqrt7Phase1Next := sqrt7Phase1Valid || (!divsqrtValid &&  sqrt7Phase1)
  sqrt7Phase1Valid := sqrt7Select  && divSqrt.io.outValid_sqrt

  divOccupied := Mux(divOccupied, !divsqrtValid, requestIO.fire && divEn)

  /** compareModule
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
  val compareModule = Module(new CompareRecFN(8, 24))
  compareModule.io.a := recIn0
  compareModule.io.b := recIn1
  compareModule.io.signaling := false.B
  val compareResult = Wire(UInt(32.W))
  val compareflags = Wire(UInt(5.W))

  assert(!unitSeleOH(2) || (uop === "b0001".U || uop === "b0000".U || uop === "b0010".U || uop === "b0011".U || uop === "b0100".U || uop === "b0101".U || uop === "b1000".U || uop === "b1100".U))
  compareResult := Mux(uop === "b1000".U , Mux(compareModule.io.lt, request.src(0), request.src(1)),
    Mux(uop === "b1100".U, Mux(compareModule.io.gt, request.src(0), request.src(1)),
     Mux(uop === "b0011".U, compareModule.io.lt || compareModule.io.eq,
       Mux(uop === "b0101".U, compareModule.io.gt || compareModule.io.eq,
         Mux(uop === "b0010".U, compareModule.io.lt,
           Mux(uop === "b0100".U, compareModule.io.gt,
             Mux(uop === "b0000".U, !compareModule.io.eq,
               compareModule.io.eq)))))))
  compareflags := compareModule.io.exceptionFlags




  /** otherEn
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
  val intToFn = Module(new INToRecFN(32, 8, 24))
  intToFn.io.in := request.src(0)
  intToFn.io.signedIn := !request.sign
  intToFn.io.roundingMode := request.roundingMode
  intToFn.io.detectTininess := false.B

  /** io.signedOut define output sign
    *
    * false for toUInt
    * true for toInt
    */
  val fnToInt = Module(new RecFNToIN(8, 24, 32))
  val convertRtz = (uop(3,2) === 3.U) && compareEn
  fnToInt.io.in := recIn0
  fnToInt.io.roundingMode := Mux(convertRtz, "b001".U(3.W), request.roundingMode)
  fnToInt.io.signedOut := !(uop(3) && uop(0))

  val convertResult = Wire(UInt(32.W))
  val convertFlags = Wire(UInt(5.W))
  convertResult := Mux(uop === "b1000".U, fNFromRecFN(8, 24, intToFn.io.out), fnToInt.io.out)
  convertFlags  := Mux(uop === "b1000".U, intToFn.io.exceptionFlags, fnToInt.io.intExceptionFlags)

  /** sgnj
    *
    * 0001 SGNJ
    * 0010 SGNJN
    * 0011 SGNJX
    */
  val sgnjresult = Wire(UInt(32.W))
  val sgnjSign = Mux(otherEn && uop === 1.U, request.src(1)(31),
    Mux(otherEn && uop === 2.U, !request.src(1)(31),
      Mux(otherEn && uop ===3.U, request.src(1)(31) ^ request.src(0)(31), false.B)))
  sgnjresult := Cat(sgnjSign, request.src(0)(30,0))

  val otherResult = Wire(UInt(32.W))
  otherResult := Mux(uop(3),convertResult,
    Mux(uop(3,2) === "b00".U, sgnjresult,
      Mux(uop === "b0100".U, classifyRecFN(8, 24, recFNFromFN(8, 24, request.src(0))), 0.U)))


  /** Output */
  requestIO.ready := !divOccupied
  responseIO.valid := divsqrtValid || fastWorkingValid

  laneFloatResultNext := Mux1H(Seq(
    unitSelectReg(0) -> fNFromRecFN(8, 24, mulAddRecFN.io.out),
    unitSelectReg(1) -> divsqrtResult,
    unitSelectReg(2) -> compareResult,
    unitSelectReg(3) -> otherResult
  ))

  laneFloatFlagsNext := Mux1H(Seq(
    unitSelectReg(0) -> mulAddRecFN.io.exceptionFlags,
    unitSelectReg(1) -> divSqrt.io.exceptionFlags,
    unitSelectReg(2) -> compareflags,
    unitSelectReg(3) -> convertFlags
  ))

  response.data := laneFloatResultReg
  response.exceptionFlags := laneFloatFlagsReg
}
