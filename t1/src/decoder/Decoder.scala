// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode.{DecodeField, BoolDecodeField, DecodeTable, DecodeBundle, DecodePattern}
import org.chipsalliance.rvdecoderdb
import org.chipsalliance.rvdecoderdb.{Encoding, Instruction, InstructionSet}

trait FieldName {
  def name: String = this.getClass.getSimpleName.replace("$", "")
}

trait UopField extends DecodeField[Op, UInt] with FieldName {
  def chiselType: UInt = UInt(4.W)
}

trait FloatType extends DecodeField[Op, UInt] with FieldName {
  def chiselType: UInt = UInt(2.W)
}

trait TopUopField extends DecodeField[Op, UInt] with FieldName {
  def chiselType: UInt = UInt(3.W)
}

trait BoolField extends BoolDecodeField[Op] with FieldName {
  def dontCareCase(op: Op): Boolean = false
  // 如果包含lsu, 那么value不会被纠正, 否则value只在不是lsu的情况下被视为1
  def containsLSU: Boolean = false
  def value(op:    Op): Boolean
  def genTable(op: Op): BitPat = if (dontCareCase(op)) dc else if (value(op) && (containsLSU || op.notLSU)) y else n
}

case class SpecialAux(name: String, vs: Int, value: String)
case class SpecialMap(name: String, vs: Int, data: Map[String, String])
case class SpecialAuxInstr(instrName: String, vs: Int, value: String, name: String)
case class Op(tpe: String, funct6: String, tpeOp2: String, funct3: String,
              name: String, special: Option[SpecialAux], notLSU: Boolean, vd: String, opcode: String) extends DecodePattern {
  // include 32 bits: funct6 + vm + vs2 + vs1 + funct3 + vd + opcode
  def bitPat: BitPat = BitPat(
    "b" +
      // funct6
      funct6 +
      // ? for vm
      "?" +
      // vs2
      (if (special.isEmpty || special.get.vs == 1) "?????" else special.get.value) +
      // vs1
      (if (special.isEmpty || special.get.vs == 2) "?????" else special.get.value) +
      // funct3
      funct3 +
      vd +
      opcode
  )
}

object Decoder {
  // Opcode: instruction[6:0]
  // refer to [https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#vector-instruction-formats]
  private val opcodeV      = "1010111"
  private val opcodeLoadF  = "0000111"
  private val opcodeStoreF = "0100111"

  // Funct3: instruction[14:12]
  // refer to [https://github.com/riscv/riscv-v-spec/blob/master/v-spec.adoc#101-vector-arithmetic-instruction-encoding]
  private val funct3IVV = "000"
  private val funct3IVI = "011"
  private val funct3IVX = "100"
  private val funct3MVV = "010"
  private val funct3MVX = "110"
  private val funct3FVV = "001"
  private val funct3FVF = "101"
  private val funct3CFG = "111" // TODO: need implementations

  // type of rs1
  private val op1iFunct3 = Seq(funct3IVV, funct3IVI, funct3IVX)
  private val op1mFunct3 = Seq(funct3MVV, funct3MVX)
  private val op1fFunct3 = Seq(funct3FVV, funct3FVF)
  private val op1cFunct3 = Seq(funct3CFG)
  // type of rs2
  private val op2vFunct3 = Seq(funct3IVV, funct3MVV, funct3FVV)
  private val op2xFunct3 = Seq(funct3IVX, funct3MVX)
  private val op2iFunct3 = Seq(funct3IVI)
  private val op2fFunct3 = Seq(funct3FVF)

  // special instrctions
  // refer to [https://github.com/riscv/riscv-v-spec/blob/master/inst-table.adoc]
  private val insnVRXUNARY0 = SpecialMap("VRXUNARY0", 2, Map("vmv.s.x" -> "00000"))
  private val insnVWXUNARY0 = SpecialMap("VWXUNARY0", 1, Map(
      "vmv.x.s" -> "00000",
      "vcpop"   -> "10000",
      "vfirst"  -> "10001",
    )
  )
  private val insnVXUNARY0 = SpecialMap("VXUNARY0", 1, Map(
      "vzext.vf8" -> "00010",
      "vsext.vf8" -> "00011",
      "vzext.vf4" -> "00100",
      "vsext.vf4" -> "00101",
      "vzext.vf2" -> "00110",
      "vsext.vf2" -> "00111"
    )
  )
  private val insnVRFUNARY0 = SpecialMap("VRFUNARY0", 2, Map("vfmv.s.f" -> "00000"))
  private val insnVWFUNARY0 = SpecialMap("VWFUNARY0", 1, Map("vfmv.f.s" -> "00000"))
  private val insnVFUNARY0 = SpecialMap("VFUNARY0", 1, Map(
      // single-width converts
      "vfcvt.xu.f.v"      -> "00000",
      "vfcvt.x.f.v"       -> "00001",
      "vfcvt.f.xu.v"      -> "00010",
      "vfcvt.f.x.v"       -> "00011",
      "vfcvt.rtz.xu.f.v"  -> "00110",
      "vfcvt.rtz.x.f.v"   -> "00111",
      // widening converts
      "vfwcvt.xu.f.v"     -> "01000",
      "vfwcvt.x.f.v"      -> "01001",
      "vfwcvt.f.xu.v"     -> "01010",
      "vfwcvt.f.x.v"      -> "01011",
      "vfwcvt.f.f.v"      -> "01100",
      "vfwcvt.rtz.xu.f.v" -> "01110",
      "vfwcvt.rtz.x.f.v"  -> "01111",
      // narrowing converts
      "vfncvt.xu.f.w"     -> "10000",
      "vfncvt.x.f.w"      -> "10001",
      "vfncvt.f.xu.w"     -> "10010",
      "vfncvt.f.x.w"      -> "10011",
      "vfncvt.f.f.w"      -> "10100",
      "vfncvt.rod.f.f.w"  -> "10101",
      "vfncvt.rtz.xu.f.w" -> "10110",
      "vfncvt.rtz.x.f.w"  -> "10111",
    )
  )
  private val insnVFUNARY1 = SpecialMap("VFUNARY1", 1, Map(
      "vfsqrt.v"   -> "00000",
      "vfrsqrt7.v" -> "00100",
      "vfrec7.v"   -> "00101",
      "vfclass.v"  -> "10000",
    )
  )
  private val insnVMUNARY0 = SpecialMap("VMUNARY0", 1, Map(
      "vmsbf" -> "00001",
      "vmsof" -> "00010",
      "vmsif" -> "00011",
      "viota" -> "10000",
      "vid"   -> "10001",
    )
  )
  def insnVToSpecialAux(insns: SpecialMap): Seq[SpecialAuxInstr] = {
    val vs = insns.vs
    val name = insns.name
    insns.data.map { case (instrName, value) =>
      SpecialAuxInstr(instrName, vs, value, name)
    }.toSeq
  }
  private val insnSpec: Seq[SpecialAuxInstr] = insnVToSpecialAux(insnVRXUNARY0) ++ insnVToSpecialAux(insnVWXUNARY0) ++ insnVToSpecialAux(insnVXUNARY0)  ++ insnVToSpecialAux(insnVRFUNARY0) ++ insnVToSpecialAux(insnVWFUNARY0) ++ insnVToSpecialAux(insnVFUNARY0) ++ insnVToSpecialAux(insnVFUNARY1)  ++ insnVToSpecialAux(insnVMUNARY0)

  def ops(fpuEnable: Boolean): Array[Op] = {
    val instructions: Seq[Instruction] = (org.chipsalliance.rvdecoderdb.instructions(org.chipsalliance.rvdecoderdb.extractResource(getClass.getClassLoader))).filter { i =>
        i.instructionSets.map(_.name) match {
          case s if s.contains("rv_v") => true
          case _ => false
        }
    }.filter { i =>
      i.name match {
          // csr instructions
          case s if Seq("vsetivli", "vsetvli", "vsetvl").contains(s) => false
          // instrctions `vmv` and `vmerge` share the same opcode, as defined in [https://github.com/riscv/riscv-v-spec/blob/master/inst-table.adoc]
          case s if s.contains("vmv.v") => false
          // instructions `vfmv.v.f` and `vfmerge.vfm` share the same opcode, as defined in [https://github.com/riscv/riscv-v-spec/blob/master/inst-table.adoc]
          case s if s.contains("vfmv.v.f") => false
          case _ => true
      }
    }.toSeq.distinct
    val expandedOps: Array[Op] =
      // case of notLSU instructions
      (instructions.filter(_.encoding.toString.substring(32-6-1, 32-0) == opcodeV).map{ insn =>
        val funct3 = insn.encoding.toString.substring(32-14-1, 32-12)

        val tpe = if (op1iFunct3.contains(funct3)) "I" else if (op1mFunct3.contains(funct3)) "M" else if (op1fFunct3.contains(funct3)) "F" else "" // TODO: OPCFG
        val tpeOp2 = if (op2vFunct3.contains(funct3)) "V" else if (op2xFunct3.contains(funct3)) "X" else if (op2iFunct3.contains(funct3)) "I" else if (op2fFunct3.contains(funct3)) "F" else "" // TODO: OPCFG
        val funct6 = insn.encoding.toString.substring(32-31-1, 32-26)
        val special = insnSpec.collectFirst { case s if (insn.name.contains(s.instrName)) => SpecialAux(s.name, s.vs, s.value) }
        val vd = insn.encoding.toString.substring(32-11-1, 32-7)
        val opcode = insn.encoding.toString.substring(32-6-1, 32-0)
        Op(tpe, funct6, tpeOp2, funct3, insn.name, special, notLSU=true, vd, opcode)
      }
      // case of LSU instructions: `opcodeLoadF` and `opcodeStoreF`
      ++ Seq("1", "0").map(fun6End =>
        Op(
          "I",               // tpe
          "?????" + fun6End,
          "?",               // tpeOp2
          "???",             // funct3
          "lsu",
          None,
          notLSU = false,
          "?????",           // vd
          opcodeLoadF
        )
      )
      ++ Seq("1", "0").map(fun6End =>
        Op(
          "I",               // tpe
          "?????" + fun6End,
          "?",               // tpeOp2
          "???",             // funct3
          "lsu",
          None,
          notLSU = false,
          "?????",           // vd
          opcodeStoreF
        )
      )
    ).toArray

    expandedOps.filter(_.tpe != "F" || fpuEnable)
  }

  /** Instruction should use [[org.chipsalliance.t1.rtl.decoder.TableGenerator.LaneDecodeTable.LogicUnit]].
    * "vand.vi"
    * "vand.vv"
    * "vand.vx"
    * "vmand.mm"
    * "vmandn.mm"
    * "vmnand.mm"
    * "vredand.vs"
    * "vmnor.mm"
    * "vmor.mm"
    * "vmorn.mm"
    * "vmxnor.mm"
    * "vmxor.mm"
    * "vor.vi"
    * "vor.vv"
    * "vor.vx"
    * "vredor.vs"
    * "vredxor.vs"
    * "vxor.vi"
    * "vxor.vv"
    * "vxor.vx"
    */
  object logic extends BoolField {
    val subs: Seq[String] = Seq("and", "or")
    // 执行单元现在不做dc,因为会在top判断是否可以chain
    def value(op: Op): Boolean = {
      val out = subs.exists(op.name.contains) && op.tpe != "F"
      if (out) println("logic", op.name)
      out
    }
  }

  /** goes to [[org.chipsalliance.t1.rtl.LaneAdder]]. */
  object adder extends BoolField {
    val subs: Seq[String] = Seq(
      "add",
      "sub",
      "slt",
      "sle",
      "sgt",
      "sge",
      "max",
      "min",
      "seq",
      "sne",
      "adc",
      "sbc",
      "sum"
    )
    def value(op: Op): Boolean = {
      val out = subs.exists(op.name.contains) &&
      !(op.tpe == "M" && Seq("vm", "vnm").exists(op.name.startsWith)) && op.tpe != "F"
      if (out) println("adder", op.name)
      out
    }
  }

  /** goes to [[org.chipsalliance.t1.rtl.LaneShifter]]. */
  object shift extends BoolField {
    val subs: Seq[String] = Seq(
      "srl",
      "sll",
      "sra"
    )
    def value(op: Op): Boolean = {
      val out = subs.exists(op.name.contains) && op.tpe != "F"
      if (out) println("shift", op.name)
      out
    }
  }

  /** goes to [[org.chipsalliance.t1.rtl.LaneMul]].
    * only apply to int mul
    */
  object multiplier extends BoolField {
    val subs: Seq[String] = Seq(
      "mul",
      "madd",
      "macc",
      "msub",
      "msac"
    )
    def value(op: Op): Boolean = {
      val out = subs.exists(op.name.contains) && op.tpe != "F"
      if (out) println("multiplier", op.name)
      out
    }
  }

  /** goes to [[org.chipsalliance.t1.rtl.LaneDiv]] or [[org.chipsalliance.t1.rtl.LaneDivFP]].
    * if FP exist, all div goes to [[org.chipsalliance.t1.rtl.LaneDivFP]]
    */
  object divider extends BoolField {
    val intDiv: Seq[String] = Seq(
      "div",
      "rem"
    )
    val floatDiv: Seq[String] = Seq(
      "fdiv",
      "fsqrt",
      "frdiv"
    )
    val subs: Seq[String] = intDiv ++ floatDiv
    def value(op: Op): Boolean = {
      val out = subs.exists(op.name.contains)
      if (out) println("divider", op.name)
      out
    }
  }

  /** TODO: remove? only Div or customer */
  object multiCycle extends BoolField {
    def value(op: Op): Boolean = {
      val out = divider.value(op) || float.value(op)
      if (out) println("multiCycle", op.name)
      out
    }
  }

  /** goes to [[org.chipsalliance.t1.rtl.OtherUnit]] */
  object other extends BoolField {
    val subs: Seq[String] = Seq(
      "rgather",
      "merge",
      "mv",
      // TODO: move to shift
      "clip"
    )
    def getType(op: Op): (Boolean, Int) = {
      // todo: vType gather -> mv
      val isGather = op.name.contains("rgather")
      val isMerge = op.name.contains("merge")
      val isClip = op.name.contains("clip")
      val isFFO = ffo.value(op)
      // extend read only
      val extendType = Seq(mv, popCount, id)
      val isMVtoFP = op.special.isDefined && op.special.get.name == "VWFUNARY0"
      val isOtherType: Boolean =
        !isMVtoFP && (Seq(isGather, isMerge, isClip, isFFO) ++ extendType.map(_.value(op))).reduce(_ || _)
      // ++ffo
      val otherType = Seq(isGather, isMerge, isClip) ++ extendType.map(_.value(op))
      val typeIndex = if (otherType.contains(true)) 4 + otherType.indexOf(true) else 0
      // ffo 占据 0, 1, 2, 3 作为字编码
      val otherUop = if (isFFO) ffo.subs.indexOf(op.name) else typeIndex
      (isOtherType, otherUop)
    }

    def value(op: Op): Boolean = {
      val out = getType(op)._1
      if (out) println("other", op.name)
      out
    }
  }

  /** is a float type.
    * TODO: remove it.
    */
  object floatType extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.tpe == "F"
      if (out) println("floatType", op.name)
      out
    }
  }

  /** goes to [[org.chipsalliance.t1.rtl.LaneFloat]]. */
  object float extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.tpe == "F" &&
      !(
        other.value(op) ||
          dontNeedExecuteInLane.value(op) ||
          slid.value(op) || divider.value(op))
      if (out) println("float", op.name)
      out
    }
  }

  /** TODO: remove it. */
  object floatConvertUnsigned extends BoolField {
    override def dontCareCase(op: Op): Boolean = !float.value(op)
    def value(op: Op): Boolean = {
      val out = op.name.contains("fcvt") && op.name.contains(".xu.")
      if (out) println("floatConvertUnsigned", op.name)
      out
    }
  }

  /** uop of FMA,
    * goes to [[org.chipsalliance.t1.rtl.LaneFloat]] FMA unit.
    */
  object FMA extends BoolField {
    val adderSubsMap: Seq[(String, Int)] = Seq(
      "vfadd" -> 0,
      "vfsub" -> 1,
      "vfrsub" -> 5,
    )

    // need read vd
    val maMap: Seq[(String, Int)] = Seq(
      "vfmacc" -> 0,
      "vfnmacc" -> 3,
      "vfmsac" -> 1,
      "vfnmsac" -> 2,
      "vfmadd" -> 4,
      "vfnmadd" -> 7,
      "vfmsub" -> 5,
      "vfnmsub" -> 6,
    )

    val subsMap: Seq[(String, Int)] = Seq(
      "vfmul" -> 0,
      "vfredosum" -> 0,
      "vfredusum" -> 0,
    ) ++ adderSubsMap ++ maMap

    def value(op: Op): Boolean = {
      val out = subsMap.exists(a => op.name.contains(a._1))
      if (out) println("FMA", op.name)
      out
    }

    def uop(op: Op): Int = {
      val isAdder = adderSubsMap.exists(a => op.name.contains(a._1)) || op.name.contains("sum")
      val msbCode = if (isAdder) 8 else 0
      // vfwadd 暂时不支持,所以没处理, 所有的widen narrow 会被解成 fma-0
      val mapFilter: Seq[(String, Int)] = subsMap.filter(a => op.name.contains(a._1))
      val lsbCode: Int = if (mapFilter.isEmpty) 0 else mapFilter.head._2
      msbCode + lsbCode
    }
  }

  /** TODO: add op. */
  object floatMul extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("vfmul")
      if (out) println("floatMul", op.name)
      out
    }
  }

  /** don't use it, it's slow, lane read all elements from VRF, send to Top.
    */
  object orderReduce extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("vfredosum")
      if (out) println("orderReduce", op.name)
      out
    }
  }

  object FDiv extends BoolField {
    // todo: remove FDiv
    val subsMap: Seq[(String, Int)] = Seq.empty[(String, Int)]
    def value(op: Op): Boolean = {
      val out = subsMap.exists(a => op.name.contains(a._1))
      if (out) println("FDiv", op.name)
      out
    }

    def uop(op: Op): Int = {
      val mapFilter = subsMap.filter(a => op.name.contains(a._1))
      if (mapFilter.isEmpty) 0 else mapFilter.head._2
    }
  }

  /** TODO: remove it, but remains attribute. */
  object FCompare extends BoolField {
    val subsMap = Seq(
      "vmfeq" -> 1,
      "vmfge" -> 5,
      "vmfgt" -> 4,
      "vmfle" -> 3,
      "vmflt" -> 2,
      "vmfne" -> 0,
      "vfmin" -> 8,
      "vfmax" -> 12,
      "vfredmin" -> 8,
      "vfredmax" -> 12,
    )

    def value(op: Op): Boolean = {
      val out = subsMap.exists(a => op.name.contains(a._1))
      if (out) println("FCompare", op.name)
      out
    }

    def uop(op: Op): Int = {
      val mapFilter = subsMap.filter(a => op.name.contains(a._1))
      if (mapFilter.isEmpty) 0 else mapFilter.head._2
    }
  }

  /** designed for Other Unit in FP.
    * goes to [[org.chipsalliance.t1.rtl.LaneFloat]] OtherUnit.
    * TODO: perf it.
    */
  object FOther extends BoolField {
    val unsignedMap = Seq(
      "vfcvt.f.xu.v" -> 8,
      "vfcvt.rtz.xu.f.v" -> 13,
    )
    val subsMap = Seq(
      "vfcvt.f.x.v" -> 8,
      "vfcvt.rtz.x.f.v" -> 14,
      "vfcvt.x.f.v" -> 10,
      "vfcvt.xu.f.v" -> 9,
      "vfsgnjn" -> 2,
      "vfsgnjx" -> 3,
      "vfsgnj" -> 1,
      "vfclass" -> 4,
      "vfrsqrt7" -> 7,
      "vfrec7" -> 6,
    ) ++ unsignedMap

    def value(op: Op): Boolean = {
      val out = subsMap.exists(a => op.name.contains(a._1))
      if (out) println("FOther", op.name)
      out
    }

    def uop(op: Op): Int = {
      val mapFilter = subsMap.filter(a => op.name.contains(a._1))
      if (mapFilter.isEmpty) 0 else mapFilter.head._2
    }
  }

  /** float uop, goes to [[org.chipsalliance.t1.rtl.LaneFloatRequest.unitSelet]]
    * TODO: remove FDiv. move to VFU uop
    */
  object fpExecutionType extends FloatType {
    def genTable(op: Op): BitPat = {
      val code: Int = if (FDiv.value(op)) 1 else if (FCompare.value(op)) 2 else if (FOther.value(op)) 3 else 0 /* FMA */
      BitPat("b" + ("00" + code.toBinaryString).takeRight(2))
    }
  }

  /** There are two types of widen:
    * - vd -> widen.
    * - vs2, vd -> widen.
    *
    * This op will widen vs2.
    * TODO: remove it as attribute.
    */
  object firstWiden extends BoolField {
    def value(op: Op): Boolean = {
      val nameWoW = op.name.replace(".wf", ".w").replace(".wx", ".w").replace(".wv", ".w")
      val out = nameWoW.endsWith(".w") || vwmacc.value(op)
      if (out) println("firstWiden", op.name)
      out
    }
  }

  /** for vmvnr, move vreg group to another vreg group.
    * it will ignore lmul, use from instr.
    * chainable
    */
  object nr extends BoolField {
    // for instructions `vmv1r.v`,`vmv2r.v`, `vmv4r.v`, `vmv8r.v`
    def value(op: Op): Boolean = {
      val out = Seq("vmv1r.v","vmv2r.v", "vmv4r.v", "vmv8r.v").contains(op.name)
      if (out) println("nr", op.name)
      out
    }
  }

  /** do reduce in each lane.
    * each element will sequentially executed in each lanes.
    * after finishing, pop it to Top, and use ALU at top to get the final result and send to element0
    * TODO: better name.
    */
  object red extends BoolField {
    // reduce 会影响special, special会极大地影响性能, 所以不能dc
    def value(op: Op): Boolean = {
      val out = op.name.contains("red") || op.name.contains("pop")
      if (out) println("red", op.name)
      out
    }
  }

  // TODO: remove this.
  object maskOp extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.startsWith("vm") &&
      ((adder.value(op) && !Seq("min", "max").exists(op.name.contains)) || logic.value(op))
      if (out) println("maskOp", op.name)
      out
    }
  }

  /** only instruction will switch src.
    * TODO: send it to uop.
    */
  object reverse extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("vrsub")
      if (out) println("reverse", op.name)
      out
    }
  }

  /** dual width of src will be convert to single width to dst.
    * narrow can be the src of chain.
    * as the dst of chain, only can be fed with Load.
    *
    * TODO: remove it as attribute.
    */
  object narrow extends BoolField {
    val subs: Seq[String] = Seq(
      "vnsrl",
      "vnsra",
      "vnclip"
    )
    // todo: 确认是否可以dc
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = {
      val out = subs.exists(op.name.contains)
      if (out) println("narrow", op.name)
      out
    }
  }

  /** lane should write to another lane
    */
  object crossWrite extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = {
      val out = op.name.startsWith("vw") && !op.name.startsWith("vwred")
      if (out) println("crossWrite", op.name)
      out
    }
  }

  /** a special widen, only write dual vd from Top to element0
    * it doesn't cross.
    * TODO: better name.
    */
  object widenReduce extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = {
      val out = op.name.startsWith("vwred")
      if (out) println("widenReduce", op.name)
      out
    }
  }

  /** For adder, does it need to take care of saturate.
    * TODO: add to uop
    */
  object saturate extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = {
      val out = Seq("vsa", "vss", "vsm").exists(op.name.startsWith)
      if (out) println("saturate", op.name)
      out
    }
  }

  /** For adder, does it need to take care of saturate.
    * TODO: add to uop
    */
  object average extends BoolField {
    val subs: Seq[String] = Seq(
      "vaa",
      "vas"
    )
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = {
      val out = subs.exists(op.name.startsWith)
      if (out) println("average", op.name)
      out
    }
  }

  /** is src0 unsigned?
    * used everywhere in Lane and VFU.
    */
  object unsigned0 extends BoolField {
    def value(op: Op): Boolean = {
      val nameWoW = op.name.replace(".vv", "").replace(".vi", "").replace(".vx", "").replace(".vs", "").replace(".wi", "").replace(".wx", "").replace(".wv", "")
      val logicShift = shift.genTable(op) == y && nameWoW.endsWith("l")
      val UIntOperation = nameWoW.endsWith("u") && !nameWoW.endsWith("su")
      val mul = op.name.contains("mulhsu") || op.name.contains("wmulsu") || op.name.contains("vwmaccus")
      val madc = Seq("adc", "sbc").exists(op.name.contains) && op.name.startsWith("vm")
      val out = op.special.nonEmpty || logicShift || UIntOperation || mul || madc
      if (out) println("unsigned0", op.name)
      out
    }
  }

  /** is src1 unsigned?
    * used everywhere in Lane and VFU.
    */
  object unsigned1 extends BoolField {
    def value(op: Op): Boolean = {
      val nameWoW = op.name.replace(".vv", "").replace(".vi", "").replace(".vx", "").replace(".vs", "").replace(".wi", "").replace(".wx", "").replace(".wv", "")
      val logicShift = shift.genTable(op) == y && nameWoW.endsWith("l")
      val UIntOperation = nameWoW.endsWith("u") && !nameWoW.endsWith("su")
      val madc = Seq("adc", "sbc").exists(op.name.contains) && op.name.startsWith("vm")
      val vwmaccsu = op.name.contains("vwmaccsu")
      if (floatType.value(op)) {
        val out = FOther.unsignedMap.exists(a => op.name.contains(a._1))
        if (out) println("unsigned1_f", op.name)
        out
      } else {
        val out = op.special.nonEmpty || logicShift || UIntOperation || madc || vwmaccsu
        if (out) println("unsigned1_nf", op.name)
        out
      }
    }
  }

  /** src1 is vtype. */
  object vtype extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.tpeOp2 == "V"
      if (out) println("vtype", op.name)
      out
    }
  }

  /** src is imm. */
  object itype extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.tpeOp2 == "I"
      if (out) println("itype", op.name)
      out
    }
  }

  /** write rd/fd at scalar core. */
  object targetRd extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.special.isDefined &&
      (op.special.get.name == "VWXUNARY0" || op.special.get.name == "VWFUNARY0")
      if (out) println("targetRd", op.name)
      out
    }
  }

  /** send element to MaskUnit at top, extend and broadcast to multiple Lanes. */
  object extend extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.special.isDefined && op.special.get.name == "VXUNARY0"
      if (out) println("extend", op.name)
      out
    }
  }

  /** move instruction, v->v s->v x->v,
    * single element move.
    * TODO: split them into multiple op since datapath differs
    */
  object mv extends BoolField {
    def value(op: Op): Boolean = {
      val out = (op.name.startsWith("vmv") || op.name.startsWith("vfmv")) && !nr.value(op)
      if (out) println("mv", op.name)
      out
    }
  }

  /** find first one,
    * lane will report if 1 is found, Sequencer should decide which is exactly the first 1 in lanes.
    * after 1 is found, tell each lane, 1 has been found at which the corresponding location.
    * lane will stale at stage2.
    * TODO: should split into lane control uop
    */
  object ffo extends BoolField {
    val subs: Seq[String] = Seq(
      "vfirst.m",
      "vmsbf.m",
      "vmsof.m",
      "vmsif.m"
    )

    def value(op: Op): Boolean = {
      val out = subs.exists(op.name.contains)
      if (out) println("ffo", op.name)
      out
    }
  }

  /** used in Sequencer mask unit, decide which vrf should be read.
    * send read request to corresponding lane, lane will respond data to Sequencer.
    * Sequencer will write data to VD.
    * mask unit is work as the router here.
    *
    * TODO: opimize mask unit.
    */
  object slid extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("slid")
      if (out) println("slid", op.name)
      out
    }
  }

  /** lane will read index from vs1, send to Sequencer.
    * mask unit will calculate vrf address based on the vs1 from lane, and send read request to lanes,
    * lanes should read it and send vs2 to Sequencer.
    * Sequencer will write vd at last.
    * address: 0 -> vlmax(sew decide address width.)
    */
  object gather extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("rgather")
      if (out) println("gather", op.name)
      out
    }
  }

  /** same with [[gather]]
    * ignore sew, address width is fixed to 16.
    *
    * @note
    * When SEW=8, vrgather.vv can only reference vector elements 0-255.
    * The vrgatherei16 form can index 64K elements,
    * and can also be used to reduce the register capacity needed to hold indices when SEW > 16.
    */
  object gather16 extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("rgatherei16")
      if (out) println("gather16", op.name)
      out
    }
  }

  /** lane will read data from vs2, send to Sequencer.
    * then Sequencer will read vs1 for mask.
    * use mask to compress data in vs2.
    * and write to vd at last.
    */
  object compress extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("compress")
      if (out) println("compress", op.name)
      out
    }
  }

  /** lane read only instructions.
    * for these instructions lane will only read vrf and send data back to Sequencer.
    */
  object readOnly extends BoolField {
    def value(op: Op): Boolean = {
      val vGather:  Boolean = op.name.contains("gather") && vtype.genTable(op) == y
      val compress: Boolean = op.name.contains("compress")
      val iota:     Boolean = op.name.contains("iota")
      val extend:   Boolean = op.name.contains("ext.vf")
      val out = vGather || compress || iota || extend
      if (out) println("readOnly", op.name)
      out
    }
  }

  /** count how many 1s in VS2.
    * lane will use [[org.chipsalliance.t1.rtl.OtherUnit]] to how many 1s locally;
    * use reduce datapath to accumulate,
    * send total result to top
    * top will send result to vd.
    */
  object popCount extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("vcpop")
      if (out) println("popCount", op.name)
      out
    }
  }

  /** lane will read vs2 from VRF, send to Top.
    * Top read v0(at register) calculate the result and write back to VRFs
    */
  object iota extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("viota")
      if (out) println("iota", op.name)
      out
    }
  }

  /** write 0...vlmax to VRF.
    * Lane other unit should handle it.
    * TODO: remove it, it's a uop.
    */
  object id extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("vid")
      if (out) println("id", op.name)
      out
    }
  }

  /** special MAC instruction, MAC use vd as source, it cross read vd.
    * TODO: cross read vd + mac uop.
    */
  object vwmacc extends BoolField {
    def value(op: Op): Boolean = {
      val out = op.name.contains("vwmacc")
      if (out) println("vwmacc", op.name)
      out
    }
  }

  /** unmanaged write for VRF. these instructions cannot be chain as source.
    *
    * TODO: add an attribute these instruction cannot be the source of chaining.
    */
  object unOrderWrite extends BoolField {
    def value(op: Op): Boolean = {
      val out = slid.value(op) || iota.value(op) || mv.value(op) || orderReduce.value(op)
      if (out) println("unOrderWrite", op.name)
      out
    }
  }

  /** VFU uop:
    * TODO: need full documentation on how to encode it.
    */
  object uop extends UopField {
    val mul: Seq[String] = Seq(
      "mul",
      "ma",
      "ms"
    )

    def y: BitPat = BitPat.Y(1)
    def genTable(op: Op): BitPat = {
      val firstIndexContains = (xs: Iterable[String], s: String) =>
        xs.map(s.indexOf).zipWithIndex.filter(_._1 != -1).head._2
      val opcode: Int = if(float.value(op)) {
        if (FMA.value(op)) {
          FMA.uop(op)
        } else if (FDiv.value(op)) {
          FDiv.uop(op)
        } else if (FCompare.value(op)) {
          FCompare.uop(op)
        } else {
          FOther.uop(op)
        }
      } else if (multiplier.value(op)) {
        val nameWoW = op.name.replace(".vv", "").replace(".vi", "").replace(".vx", "").replace(".vs", "")
        val high = nameWoW.contains("mulh")
        // 0b1000
        val negative = if (nameWoW.startsWith("vn")) 8 else 0
        // 0b100
        val asAddend = if (Seq("c", "cu", "cus", "csu").exists(nameWoW.endsWith)) 4 else 0
        val n = if (high) 3 else firstIndexContains(mul, nameWoW)
        negative + asAddend + n
      } else if (divider.value(op)) {
        if (op.tpe != "F") {
          firstIndexContains(divider.intDiv, op.name)
        } else {
          8 + firstIndexContains(divider.floatDiv, op.name)
        }
      } else if (adder.value(op)) {
        if (op.name.contains("sum")) 0 else firstIndexContains(adder.subs, op.name)
      } else if (logic.value(op)) {
        val nameWoW = op.name.replace(".mm", "")
        val isXnor = op.name.contains("vmxnor")
        val isXor = op.name.contains("xor")
        val notX = if (op.name.startsWith("vmn")) 8 else 0
        val xNot = if (isXnor || nameWoW.endsWith("n")) 4 else 0
        val subOp = if (isXnor || isXor) 2 else firstIndexContains(logic.subs, op.name)
        notX + xNot + subOp
      } else if (shift.value(op)) {
        val subOp = firstIndexContains(shift.subs, op.name)
        require(subOp < 4)
        val ssr = if (op.name.contains("ssr")) 4 else 0
        subOp + ssr
      } else if (other.value(op)) {
        other.getType(op)._2
      } else 0
      if (!op.notLSU) {
        BitPat("b" + "????")
      } else {
        BitPat("b" + ("0000" + opcode.toBinaryString).takeRight(4))
      }
    }
  }


  /** uop for mask unit. */
  object topUop extends TopUopField {
    def genTable(op: Op): BitPat = {
      val isSlide = slid.value(op)
      val isExtend = extend.value(op)
      val log2 = (x: Int) => (math.log10(x) / math.log10(2)).toInt
      val opcode = if (isSlide) {
        val up = if (op.name.contains("up")) 2 else 0
        val slid1 = if (op.name.contains("slide1")) 1 else 0
        up + slid1
      } else if (isExtend) {
        val signExtend = if (op.name.startsWith("vs")) 4 else 0
        val extUop = log2(op.name.last.toString.toInt)
        signExtend + extUop
      } else 0
      BitPat("b" + ("000" + opcode.toBinaryString).takeRight(3))
    }
  }

  /** only one or two operators
    * src is mask format(one element one bit).
    * vl should align with src.
    * if datapath is unaligned, need to take care the tail.
    */
  object maskLogic extends BoolField {
    def value(op: Op): Boolean = {
      // todo: rename maskLogic -> maskOperation
      val otherMaskOperation = Seq("sbf", "sif", "sof", "first", "cpop", "viota").exists(op.name.contains)
      val logicMaskOperation = op.name.startsWith("vm") && logic.genTable(op) == y
      val out = logicMaskOperation || otherMaskOperation
      if (out) println("maskLogic", op.name)
      out
    }
  }

  /** vd is mask format.
    * execute at lane, send result to Sequencer, regroup it and write to vd.
    * if datapath is unaligned, need to take care the tail.
    */
  object maskDestination extends BoolField {
    def value(op: Op): Boolean = {
      val out = (op.name.startsWith("vm") && adder.value(op) && !Seq("min", "max").exists(op.name.contains)) ||
        (op.name.startsWith("vm") && floatType.value(op))
      if (out) println("maskDestination", op.name)
      out
    }
  }

  /** three ops. these ops don't use mask. use v0 as third op, read it from duplicate V0.
    * it will read
    * use mask(v0) in mask format as source.
    */
  object maskSource extends BoolField {
    def value(op: Op): Boolean = {
      val out = Seq("vadc", "vsbc", "vmadc", "vmsbc", "vmerge", "vfmerge").exists(op.name.startsWith)
      if (out) println("maskSource", op.name)
      out
    }
  }


  /** TODO: remove it. */
  object indexType extends BoolField {
    override def containsLSU: Boolean = true
    // funct6 的最低位是mop(0)
    def value(op: Op): Boolean = {
      val out =  !op.notLSU && op.funct6.endsWith("1")
      if (out) println("indexType", op.name)
      out
    }
  }

  /** if Sequencer is the router for data from Lane to LSU or Sequencer mask unit.
    * special -> maskUnit || index type load store
    */
  object special extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean = {
      val out = Seq(indexType, maskUnit).map(_.value(op)).reduce(_ || _)
      if (out) println("special", op.name)
      out
    }
  }

  // mask unit -> red || compress || viota || ffo || slid || maskDestination || gather(v) || mv || popCount || extend
  /** all instruction in Sequencer mask unit. */
  object maskUnit extends BoolField {
    def value(op: Op): Boolean = {
      val out = Seq(red, compress, iota, ffo, slid, maskDestination, mv, popCount, extend)
        .map(_.value(op))
        .reduce(_ || _) || (gather.value(op) && vtype.value(op))
      if (out) println("maskUnit", op.name)
      out
    }
  }

  /** Read vs2 or vd with cross read channel. */
  // crossRead -> narrow || firstWiden
  object crossRead extends BoolField {
    def value(op: Op): Boolean = {
      val out = Seq(narrow, firstWiden).map(_.value(op)).reduce(_ || _)
      if (out) println("crossRead", op.name)
      out
    }
  }

  //sWrite -> targetRd || readOnly || crossWrite || maskDestination || reduce || loadStore
  /** instruction will write vd or rd(scalar) from outside of lane.
    * It will request vrf wait, and lane will not write.
    */
  object sWrite extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean = {
      val out = Seq(targetRd, readOnly, crossWrite, maskDestination, red).map(_.value(op)).reduce(_ || _) || !op.notLSU
      if (out) println("sWrite", op.name)
      out
    }
  }

  // decodeResult(Decoder.multiplier) && decodeResult(Decoder.uop)(1, 0).xorR && !decodeResult(Decoder.vwmacc)
  /** TODO: remove it. */
  object ma extends BoolField {
    def value(op: Op): Boolean = {
      val out = (multiplier.value(op) && Seq(BitPat("b??01"), BitPat("b??10")).exists(_.cover(uop.genTable(op))) &&
      !vwmacc.value(op)) || (floatType.value(op) && FMA.maMap.exists(a => op.name.contains(a._1)))
      if (out) println("ma", op.name)
      out
    }
  }

  // sReadVD -> !(ma || maskLogic)
  /** instruction need to read vd as operator. */
  object sReadVD extends BoolField {
    def value(op: Op): Boolean = {
      val out = !Seq(ma, maskLogic).map(_.value(op)).reduce(_ || _)
      if (out) println("sReadVD", op.name)
      out
    }
  }

  // wScheduler 原来与 sScheduler 如果出错了需要检查一下,真不一样需要说明记录
  //sScheduler -> maskDestination || red || readOnly || ffo || popCount || loadStore
  /** lane will send request to Sequencer and wait ack from Sequencer. */
  object scheduler extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean = {
      val out = !(Seq(maskDestination, red, readOnly, ffo, popCount).map(_.value(op)).reduce(_ || _) || !op.notLSU)
      if (out) println("scheduler", op.name)
      out
    }
  }

  // sExecute 与 wExecuteRes 也不一样,需要校验
  // sExecute -> readOnly || nr || loadStore
  /** Lane doesn't need VFU to execute the instruction.
    * datapath will directly goes to VRF.
    */
  object dontNeedExecuteInLane extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean = {
      val out = Seq(readOnly, nr).map(_.value(op)).reduce(_ || _) || !op.notLSU
      if (out) println("dontNeedExecuteInLane", op.name)
      out
    }
  }

  // lane中只能在slot0中执行的指令
  // specialSlot -> crossRead || crossWrite || maskLogic || maskDestination || maskSource
  /** instructions schedule to slot0. */
  object specialSlot extends BoolField {
    def value(op: Op): Boolean = {
      val out = Seq(crossRead, crossWrite, maskLogic, maskDestination, maskSource).map(_.value(op)).reduce(_ || _)
      if (out) println("specialSlot", op.name)
      out
    }
  }

  // TODO: how to add custom decoder for niche customer: linking.
  def all(fpuEnable: Boolean): Seq[DecodeField[Op, _ >: Bool <: UInt]] = {
    Seq(
      logic,
      adder,
      shift,
      multiplier,
      divider,
      multiCycle,
      other,
      unsigned0,
      unsigned1,
      itype,
      nr,
      red,
      // top only
      widenReduce,
      targetRd,
      slid,
      gather,
      gather16,
      compress,
      unOrderWrite,
      // top uop
      extend, // top uop
      mv, // top uop
      iota, // top uop
      uop,
      maskLogic,
      maskDestination,
      maskSource,
      readOnly,
      vwmacc,
      saturate,
      special,
      maskUnit,
      crossWrite,
      crossRead,
      // state
      sWrite,
      //sRead1 -> vType
      vtype,
      sReadVD,
      scheduler,
      dontNeedExecuteInLane,
      reverse, // uop
      average, // uop
      ffo, // todo: add mask select -> top uop
      popCount, // top uop add, red, uop popCount
      topUop,
      specialSlot
    ) ++ {
      if (fpuEnable)
        Seq(
          float,
          fpExecutionType,
          floatMul,
          orderReduce
        )
      else Seq()
    }
  }

  def decodeTable(fpuEnable: Boolean): DecodeTable[Op] = {
    // new DecodeTable[Op](ops(fpuEnable), all(fpuEnable))
    val newOps = ops(fpuEnable)
    val newAll = all(fpuEnable)
    val out = new DecodeTable[Op](newOps, all(fpuEnable))
    // println("testing")
    // newOps.map{ 
    //   op => 
    //     print(op.bitPat, op.tpe, op.funct6, op.tpeOp2,  op.funct3, op.name,  op.special, op.notLSU) 
    //     // print(op.bitPat.toString().substring(0,27), op.tpe, op.funct6, op.tpeOp2,  op.funct3, op.name,  op.special, op.notLSU) 
    //     out.table.table.map {
    //       tb => 
    //         if(tb._1 == op.bitPat)
    //           println(tb._2)  
    //     }
    // }
    // println("testingend")
    out
  }
  def decode(fpuEnable: Boolean): UInt => DecodeBundle = decodeTable(fpuEnable).decode
  def bundle(fpuEnable: Boolean): DecodeBundle = decodeTable(fpuEnable).bundle
}
