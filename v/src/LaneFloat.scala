package v

import chisel3.{UInt, _}
import chisel3.experimental.{SerializableModule, SerializableModuleParameter}
import chisel3.util.experimental.decode._
import chisel3.util._
import hardfloat._

object LaneFloatParam {
  implicit def rw: upickle.default.ReadWriter[LaneFloatParam] = upickle.default.macroRW
}

case class LaneFloatParam(datapathWidth: Int) extends VFUParameter with SerializableModuleParameter {
  val decodeField: BoolField = Decoder.float
  val inputBundle = new LaneFloatRequest(datapathWidth)
  val outputBundle = new LaneFloatResponse(datapathWidth)
  override val singleCycle = false
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
  val floatMul = Bool()
  val roundingMode = UInt(3.W)
  val executeIndex: UInt = UInt(2.W)
}

class LaneFloatResponse(datapathWidth: Int)  extends Bundle{
  val data = UInt(datapathWidth.W)
  val adderMaskResp: Bool = Bool()
  val exceptionFlags = UInt(5.W)
  val executeIndex: UInt = UInt(2.W)
}

class LaneFloat(val parameter: LaneFloatParam) extends VFUModule(parameter) with SerializableModule[LaneFloatParam]{
  val response: LaneFloatResponse = Wire(new LaneFloatResponse(parameter.datapathWidth))
  val request : LaneFloatRequest  = connectIO(response).asTypeOf(parameter.inputBundle)

  val recIn0 = recFNFromFN(8, 24, request.src(0))
  val recIn1 = recFNFromFN(8, 24, request.src(1))
  val recIn2 = recFNFromFN(8, 24, request.src(2))

  val uop = request.opcode
  val indexReg: UInt = RegEnable(request.executeIndex, 0.U, requestIO.fire)

  val unitSeleOH = UIntToOH(request.unitSelet)
  val fmaEn     = unitSeleOH(0)
  val divEn     = unitSeleOH(1)
  val compareEn = unitSeleOH(2)
  val otherEn   = unitSeleOH(3)

  /** divEn insn responds after  muticycles
    * NonDiv insn responds in next cycle
    */

  val divOccupied    = RegInit(false.B)
  val fastWorking = RegInit(false.B)

  val fastResultNext = Wire(UInt(32.W))
  val fastFlagsNext = Wire(UInt(5.W))
  val fastResultReg = RegNext(fastResultNext)
  val fastFlagsReg  = RegNext(fastFlagsNext)

  fastWorking := requestIO.fire && !divEn
  response.executeIndex := indexReg

  /** fmaEn
    *
    * {{{
    *   addsub                sub          mul     maf     rmaf
    * a  src(0)               src(1)      src(0)  src(0)  src(0)
    * b  1                         1      src(1)  src(1)  src(2)
    * c  src(1)               src(0)      0       src(2)  src(1)
    * }}}
    */

  /** MAF logic */
  val sub    = fmaEn && uop === 9.U
  val addsub = fmaEn && request.opcode(3)
  val maf    = fmaEn && (uop(3,2) === 0.U)
  val rmaf   = fmaEn && (uop(3,2) === 1.U)

  val mulAddRecFN = Module(new MulAddRecFN(8, 24))
  val fmaIn0 = Mux(sub, recIn1, recIn0)
  val fmaIn1 = Mux(addsub, (BigInt(1)<<(parameter.datapathWidth - 1)).U, Mux(rmaf, recIn2, recIn1))
  val fmaIn2 = Mux(sub, recIn0,
    Mux(maf && !request.floatMul, recIn2,
      Mux(maf && request.floatMul, ((request.src(0) ^ request.src(1)) & (BigInt(1) << (parameter.datapathWidth - 1)).U) << 1, recIn1)))

  mulAddRecFN.io.op := request.opcode(1,0)
  mulAddRecFN.io.a := fmaIn0
  mulAddRecFN.io.b := fmaIn1
  mulAddRecFN.io.c := fmaIn2
  mulAddRecFN.io.roundingMode := request.roundingMode //todo decode it
  mulAddRecFN.io.detectTininess := false.B

  val fmaResult = fNFromRecFN(8, 24, mulAddRecFN.io.out)

  /** DivSqrtModule
    * {{{
    * 0001 for div
    * 0010 for rdiv
    * 1000 for sqrt
    * 1001 for sqrt7
    * 1010 for rec7
    *
    *     div       rdiv    sqrt
    * a   in1       in0     in0
    * b   in0       in1     Dontcare
    * }}}
    */
  val div      = divEn && (uop === "b0001".U)
  val rdiv     = divEn && (uop === "b0010".U)
  val sqrt     = divEn && (uop === "b1000".U)

  val divSqrt = Module(new DivSqrtRecFN_small(8, 24,0))
  val divIn0 = Mux(rdiv, recIn0, recIn1)
  val divIn1 = Mux(rdiv, recIn1, recIn0)

  divSqrt.io.a := divIn0
  divSqrt.io.b := divIn1
  // todo: need re-decode?
  divSqrt.io.roundingMode := request.roundingMode
  divSqrt.io.detectTininess := 0.U
  divSqrt.io.sqrtOp := sqrt
  divSqrt.io.inValid := (requestIO.fire && divEn)

  val divsqrtValid = divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt
  val divsqrtResult = fNFromRecFN(8, 24, divSqrt.io.out)

  divOccupied := Mux(divOccupied, !divsqrtValid, requestIO.fire && divEn)

  /** CompareModule
    *
    * {{{
    * 0001 EQ
    * 0000 NQ
    * 0010 LT
    * 0011 LE
    * 0100 GT
    * 0101 GE
    *
    * 1000 min
    * 1100 max
    * }}}
    */
  val compareModule = Module(new CompareRecFN(8, 24))
  compareModule.io.a := recIn1
  compareModule.io.b := recIn0
  compareModule.io.signaling := false.B
  val compareResult = Wire(UInt(32.W))
  val compareflags = Wire(UInt(5.W))

  assert(!unitSeleOH(2) || (uop === "b0001".U || uop === "b0000".U || uop === "b0010".U || uop === "b0011".U || uop === "b0100".U || uop === "b0101".U || uop === "b1000".U || uop === "b1100".U))
  compareResult := Mux(uop === "b1000".U , Mux(compareModule.io.lt, request.src(1), request.src(0)),
    Mux(uop === "b1100".U, Mux(compareModule.io.gt, request.src(1), request.src(0)),
     Mux(uop === "b0011".U, compareModule.io.lt || compareModule.io.eq,
       Mux(uop === "b0101".U, compareModule.io.gt || compareModule.io.eq,
         Mux(uop === "b0010".U, compareModule.io.lt,
           Mux(uop === "b0100".U, compareModule.io.gt,
             Mux(uop === "b0000".U, !compareModule.io.eq,
               compareModule.io.eq)))))))
  compareflags := compareModule.io.exceptionFlags

  /** Other Unit
    * {{{
    * uop(3,2) =
    * 1x => convert
    * 00 => sgnj
    * 01 => clasify,merge,rec7,sqrt7
    *
    * 0001 SGNJ
    * 0010 SGNJN
    * 0011 SGNJX
    *
    * 0100 classify
    * 0101 merge
    * 0110 rec7
    * 0111 sqrt7
    *
    * 1000 to float
    * 1010 to sint
    * 1001 to uint
    * 1110 to sint tr
    * 1101 to uint tr
    * }}}
    * */
  val intToFn = Module(new INToRecFN(32, 8, 24))
  intToFn.io.in := request.src(1)
  intToFn.io.signedIn := request.sign
  intToFn.io.roundingMode := request.roundingMode
  intToFn.io.detectTininess := false.B

  /** io.signedOut define output sign
    *
    * false for toUInt
    * true for toInt
    */
  val fnToInt = Module(new RecFNToIN(8, 24, 32))
  val convertRtz = (uop(3,2) === 3.U) && compareEn
  fnToInt.io.in := recIn1
  fnToInt.io.roundingMode := Mux(convertRtz, "b001".U(3.W), request.roundingMode)
  fnToInt.io.signedOut := !(uop(3) && uop(0))

  val convertResult = Wire(UInt(32.W))
  val convertFlags = Wire(UInt(5.W))
  convertResult := Mux(uop === "b1000".U, fNFromRecFN(8, 24, intToFn.io.out), fnToInt.io.out)
  convertFlags  := Mux(uop === "b1000".U, intToFn.io.exceptionFlags, fnToInt.io.intExceptionFlags)

  /** sgnj
    * {{{
    * 0001 SGNJ
    * 0010 SGNJN
    * 0011 SGNJX
    * }}}
    */
  val sgnjresult = Wire(UInt(32.W))
  val sgnjSign = Mux(otherEn && uop === "b0001".U, request.src(0)(31),
    Mux(otherEn && uop === "b0010".U, !request.src(0)(31),
      Mux(otherEn && uop ==="b0011".U, request.src(0)(31) ^ request.src(1)(31), false.B)))
  sgnjresult := Cat(sgnjSign, request.src(1)(30,0))

  val in1classify = classifyRecFN(8, 24, recIn1)

  /** rec7 and rsqrt7 */
  val rec7En = (uop === "b0110".U) && otherEn
  val rsqrt7En = uop==="b0111".U && otherEn

  val rec7Module = Module(new Rec7Fn)
  rec7Module.in.data := request.src(1)
  rec7Module.in.classifyIn := in1classify
  rec7Module.in.roundingMode := request.roundingMode

  val rsqrt7Module = Module(new Rsqrt7Fn)
  rsqrt7Module.in.data := request.src(1)
  rsqrt7Module.in.classifyIn := in1classify

  val otherResult = Wire(UInt(32.W))
  val otherFlags = Wire(UInt(5.W))
  otherResult := Mux(uop(3), convertResult,
    Mux(uop(3,2) === "b00".U, sgnjresult,
      Mux(uop === "b0100".U, in1classify,
        Mux(uop==="b0110".U, rec7Module.out.data,
          Mux(uop==="b0111".U, rsqrt7Module.out.data, 0.U)))))

  otherFlags := Mux(rec7En, rec7Module.out.exceptionFlags,
    Mux(rsqrt7En,rsqrt7Module.out.exceptionFlags, convertFlags))

  /** Output */
  fastResultNext := Mux1H(Seq(
    unitSeleOH(0) -> fmaResult,
    unitSeleOH(2) -> compareResult,
    unitSeleOH(3) -> otherResult
  ))//todo: cannot select div output

  fastFlagsNext := Mux1H(Seq(
    unitSeleOH(0) -> mulAddRecFN.io.exceptionFlags,
    unitSeleOH(2) -> compareflags,
    unitSeleOH(3) -> otherFlags
  ))

  response.adderMaskResp := fastResultReg(0)
  response.data := Mux(divsqrtValid, divsqrtResult, fastResultReg)
  response.exceptionFlags := Mux(divsqrtValid, divSqrt.io.exceptionFlags, fastFlagsReg)
  requestIO.ready := !divOccupied
  responseIO.valid := divsqrtValid || fastWorking
}

class Rec7Fn extends Module {
  val in = IO(Input(new Bundle {
    val data = UInt(32.W)
    val classifyIn = UInt(10.W)
    val roundingMode = UInt(3.W)
  }))
  val out = IO(Output(new Bundle {
    val data = UInt(32.W)
    val exceptionFlags = UInt(5.W)
  }))

  val sign = in.data(31)
  val expIn = in.data(30, 23)
  val fractIn = in.data(22, 0)

  val inIsPositiveInf = in.classifyIn(7)
  val inIsNegativeInf = in.classifyIn(0)
  val inIsNegativeZero = in.classifyIn(3)
  val inIsPositveZero = in.classifyIn(4)
  val inIsSNaN = in.classifyIn(8)
  val inIsQNaN = in.classifyIn(9)
  val inIsSub = in.classifyIn(2) || in.classifyIn(5)
  val inIsSubMayberound = inIsSub && !in.data(22, 21).orR
  val maybeRoundToMax = in.roundingMode === 1.U ||
    ((in.roundingMode === 2.U) && !sign) ||
    ((in.roundingMode === 3.U) && sign)
  val maybyRoundToNegaInf = in.roundingMode === 0.U ||
    in.roundingMode === 4.U ||
    ((in.roundingMode === 2.U) && sign)
  val maybyRoundToPosInf = in.roundingMode === 0.U ||
    in.roundingMode === 4.U ||
    ((in.roundingMode === 3.U) && !sign)
  val roundAbnormalToMax     = inIsSubMayberound && maybeRoundToMax
  val roundAbnormalToNegaInf = inIsSubMayberound && maybyRoundToNegaInf
  val roundAbnormalToPosInf  = inIsSubMayberound && maybyRoundToPosInf
  val roundAbnormal = roundAbnormalToPosInf || roundAbnormalToNegaInf || roundAbnormalToMax

  val normDist = Wire(UInt(8.W))
  val normExpIn = Wire(UInt(8.W))
  val normSigIn = Wire(UInt(23.W))
  val normExpOut = Wire(UInt(8.W))
  val normSigOut = Wire(UInt(23.W))
  val sigOut = Wire(UInt(23.W))
  val expOut = Wire(UInt(8.W))

  normDist := countLeadingZeros(fractIn)
  normExpIn := Mux(inIsSub, -normDist, expIn)

  // todo timing issue
  normSigIn := Mux(inIsSub, fractIn << (1.U - normExpIn), fractIn)

  val rec7Decoder = Module(new Rec7LUT)
  rec7Decoder.in := normSigIn(22, 16)

  normSigOut := Cat(rec7Decoder.out, 0.U(16.W))
  normExpOut := 253.U - normExpIn

  val outIsSub = normExpOut === 0.U || normExpOut.andR
  expOut := Mux(outIsSub, 0.U, normExpOut)
  val outSubShift = Mux(normExpOut === 0.U, 1.U, 2.U)
  sigOut := Mux(outIsSub, Cat(1.U(1.W), normSigOut) >> outSubShift, normSigOut)

  out.data := Mux(inIsNegativeInf, "x80000000".U,
    Mux(inIsPositiveInf, 0.U,
      Mux(inIsNegativeZero || roundAbnormalToNegaInf, "xff800000".U,
        Mux(inIsPositveZero || roundAbnormalToPosInf , "x7f800000".U,
          Mux(inIsQNaN || inIsSNaN, "x7FC00000".U,
            Mux(roundAbnormalToMax, Cat(sign, "x7f7fffff".U),
              Cat(sign, expOut,sigOut)))))))
  out.exceptionFlags := Mux(inIsSNaN, 16.U,
    Mux(inIsPositveZero || inIsNegativeZero, 8.U,
    Mux(roundAbnormal, 5.U, 0.U)))
}

class Rsqrt7Fn extends Module {
  val in = IO(Input(new Bundle{
    val data = UInt(32.W)
    val classifyIn = UInt(10.W)
  }))
  val out = IO(Output(new Bundle {
    val data = UInt(32.W)
    val exceptionFlags = UInt(5.W)
  }))

  val sign = in.data(31)
  val expIn = in.data(30, 23)
  val fractIn = in.data(22, 0)
  val inIsSub = !expIn.orR

  val outNaN = Cat(in.classifyIn(2,0), in.classifyIn(9,8)).orR
  val outInf = in.classifyIn(3)|| in.classifyIn(4)

  val normDist = Wire(UInt(8.W))
  val normExpIn = Wire(UInt(8.W))
  val normSigIn = Wire(UInt(23.W))
  val sigOut = Wire(UInt(23.W))
  val expOut = Wire(UInt(8.W))

  normDist := countLeadingZeros(fractIn)
  normExpIn := Mux(inIsSub, -normDist, expIn)
  // todo timing issue
  normSigIn := Mux(inIsSub, fractIn << (1.U - normExpIn), fractIn)

  val rsqrt7Decoder = Module(new Rsqrt7LUT)
  rsqrt7Decoder.in := Cat(normExpIn(0), normSigIn(22,17))

  sigOut := Cat(rsqrt7Decoder.out, 0.U(16.W))
  expOut := (380.U - normExpIn) >> 1

  out.data := Mux(outNaN, "x7FC00000".U, Cat(sign, expOut, sigOut))
  out.exceptionFlags := Mux(outNaN, 16.U,
    Mux(outInf, 8.U, 0.U))
}

class LUT(table: Seq[Int], inputWidth: Int, outputWidth: Int) extends Module {
  val in = IO(Input(UInt(inputWidth.W)))
  val out = IO(Output(UInt(outputWidth.W)))
  out := chisel3.util.experimental.decode.decoder.espresso(in, TruthTable(table.zipWithIndex.map {
    case (data, addr) => (BitPat(addr.U(inputWidth.W)), BitPat(data.U(outputWidth.W)))
  }, BitPat.N(outputWidth)))
}

class Rec7LUT extends LUT(
  Seq(127, 125, 123, 121, 119, 117, 116, 114,
    112, 110, 109, 107, 105, 104, 102, 100, 99, 97,
    96, 94, 93, 91, 90, 88, 87, 85, 84, 83, 81, 80,
    79, 77, 76, 75, 74, 72, 71, 70, 69, 68, 66, 65,
    64, 63, 62, 61, 60, 59, 58, 57, 56, 55, 54, 53,
    52, 51, 50, 49, 48, 47, 46, 45, 44, 43, 42, 41,
    40, 40, 39, 38, 37, 36, 35, 35, 34, 33, 32, 31,
    31, 30, 29, 28, 28, 27, 26, 25, 25, 24, 23, 23,
    22, 21, 21, 20, 19, 19, 18, 17, 17, 16, 15, 15,
    14, 14, 13, 12, 12, 11, 11, 10, 9, 9, 8, 8, 7,
    7, 6, 5, 5, 4, 4, 3, 3, 2, 2, 1, 1, 0),7,7)

class Rsqrt7LUT extends LUT(
  Seq(52, 51, 50, 48, 47, 46, 44, 43,
    42, 41, 40, 39, 38, 36, 35, 34,
    33, 32, 31, 30, 30, 29, 28, 27,
    26, 25, 24, 23, 23, 22, 21, 20,
    19, 19, 18, 17, 16, 16, 15, 14,
    14, 13, 12, 12, 11, 10, 10, 9,
    9, 8, 7, 7, 6, 6, 5, 4,
    4, 3, 3, 2, 2, 1, 1, 0,
    127, 125, 123, 121, 119, 118, 116, 114,
    113, 111, 109, 108, 106, 105, 103, 102,
    100, 99, 97, 96, 95, 93, 92, 91,
    90, 88, 87, 86, 85, 84, 83, 82,
    80, 79, 78, 77, 76, 75, 74, 73,
    72, 71, 70, 70, 69, 68, 67, 66,
    65, 64, 63, 63, 62, 61, 60, 59,
    59, 58, 57, 56, 56, 55, 54, 53),7,7)

