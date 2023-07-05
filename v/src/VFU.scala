import chisel3._
import chisel3.util._
import hardfloat._

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
  * 1100 for sqrt7
  *
  * For Compare/Convert
  * 0001 EQ
  * 0000 NQ
  * 0010 LT
  * 0011 LE
  * 0100 GT
  * 0101 GE
  * 1000 to float
  * 1010 to sint
  * 1001 to uint
  * 1110 to sint tr
  * 1101 to uint tr
  *
  * none of these 3
  *
  * 0000 SGNJ
  * 0001 SGNJN
  * 0010 SGNJX
  * 0101 min
  * 0110 max
  * 1000 classify
  * 1001 merge
  *
  *
  * */
class VFUInput extends Bundle{
  val in0 = UInt(32.W)
  val in1 = UInt(32.W)
  val in2 = UInt(32.W)
  val in2en = Bool()
  val uop = UInt(4.W)
  val FMA = Bool()
  val DIV = Bool()
  val CompareConvert = Bool()
  val reduction = Bool()
  val roundingMode = UInt(3.W)
}
class VFU extends Module{
  val req  = IO(Flipped(Decoupled(new VFUInput)))
  val resp = IO(Decoupled(new Bundle(){
    val output = UInt(32.W)
  }))
  val uop = req.bits.uop
  val in2en = req.bits.in2en

  val reverse12 = (req.bits.FMA && (req.bits.uop === 13.U)) || (req.bits.DIV && (req.bits.uop === 2.U))
  val reverse23 = req.bits.FMA && req.bits.in2en && req.bits.uop(2)
  val addsub = req.bits.FMA && req.bits.uop(3)

  //FMA

  val fmaIn0 = Wire(UInt(32.W))
  val fmaIn1 = Wire(UInt(32.W))
  val fmaIn2 = Wire(UInt(32.W))
  fmaIn0 := Mux(reverse12, req.bits.in1, req.bits.in0)
  /**
    * uop(3)==1 => addsub + rsub
    * in2en && (uop(3,2) === 1.U => MAF
    *
    * */
  fmaIn1 := Mux(uop(3), 1.U, Mux(in2en && (uop(3,2) === 1.U), req.bits.in2, req.bits.in1))



  val mulAddRecFN = Module(new MulAddRecFN(5, 11))
  mulAddRecFN.io.op := req.bits.uop(1,0)
  mulAddRecFN.io.a := recFNFromFN(5, 11, fmaIn0)
  mulAddRecFN.io.b := recFNFromFN(5, 11, fmaIn1)
  mulAddRecFN.io.c := recFNFromFN(5, 11, fmaIn2)
  mulAddRecFN.io.roundingMode := req.bits.roundingMode
  mulAddRecFN.io.detectTininess := false.B






}