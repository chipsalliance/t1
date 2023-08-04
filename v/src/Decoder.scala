package v

import chisel3._
import chisel3.util.BitPat
import chisel3.util.experimental.decode._

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

object Decoder {
  object logic extends BoolField {
    val subs: Seq[String] = Seq("and", "or")
    // 执行单元现在不做dc,因为会在top判断是否可以chain
    def value(op: Op): Boolean = subs.exists(op.name.contains) && op.tpe != "F"
  }

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
    def value(op: Op): Boolean = subs.exists(op.name.contains) &&
      !(op.tpe == "M" && Seq("vm", "vnm").exists(op.name.startsWith)) && op.tpe != "F"
  }

  object shift extends BoolField {
    val subs: Seq[String] = Seq(
      "srl",
      "sll",
      "sra"
    )
    def value(op: Op): Boolean = subs.exists(op.name.contains) && op.tpe != "F"
  }

  object multiplier extends BoolField {
    val subs: Seq[String] = Seq(
      "mul",
      "madd",
      "macc",
      "msub",
      "msac"
    )
    def value(op: Op): Boolean = subs.exists(op.name.contains) && op.tpe != "F"
  }

  object divider extends BoolField {
    val subs: Seq[String] = Seq(
      "div",
      "rem",
      "sqrt",
      "rec7"
    )
    // todo: delete `&& op.tpe != "F"`
    def value(op: Op): Boolean = subs.exists(op.name.contains) && op.tpe != "F"
  }

  object multiCycle extends BoolField {
    def value(op: Op): Boolean = divider.value(op) || float.value(op)
  }

  object other extends BoolField {
    val subs: Seq[String] = Seq(
      "rgather",
      "merge",
      "mv",
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
      val isOtherType: Boolean =
        (Seq(isGather, isMerge, isClip, isFFO) ++ extendType.map(_.value(op))).reduce(_ || _)
      // ++ffo
      val otherType = Seq(isGather, isMerge, isClip) ++ extendType.map(_.value(op))
      val typeIndex = if (otherType.contains(true)) 4 + otherType.indexOf(true) else 0
      // ffo 占据 0, 1, 2, 3 作为字编码
      val otherUop = if (isFFO) ffo.subs.indexOf(op.name) else typeIndex
      (isOtherType, otherUop)
    }

    def value(op: Op): Boolean = getType(op)._1
  }

  object floatType extends BoolField {
    def value(op: Op): Boolean = op.tpe == "F"
  }

  object float extends BoolField {
    // todo: div 不解析成浮点
    def value(op: Op): Boolean = op.tpe == "F" &&
      !(
        other.value(op) ||
          dontNeedExecuteInLane.value(op) ||
          slid.value(op) ||
          mv.value(op) /*|| divider.value(op)*/)
  }

  object floatConvertUnsigned extends BoolField {
    override def dontCareCase(op: Op): Boolean = !float.value(op)
    def value(op: Op): Boolean = {
      op.name.contains("fcvt") && op.name.contains(".xu.")
    }
  }

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

    def value(op: Op): Boolean = subsMap.exists(a => op.name.contains(a._1))

    def uop(op: Op): Int = {
      val isAdder = adderSubsMap.exists(a => op.name.contains(a._1))
      val msbCode = if (isAdder) 8 else 0
      // vfwadd 暂时不支持,所以没处理, 所有的widen narrow 会被解成 fma-0
      val mapFilter: Seq[(String, Int)] = subsMap.filter(a => op.name.contains(a._1))
      val lsbCode: Int = if (mapFilter.isEmpty) 0 else mapFilter.head._2
      msbCode + lsbCode
    }
  }

  object floatMul extends BoolField {
    def value(op: Op): Boolean = op.name.contains("vfmul")
  }

  object FDiv extends BoolField {
    val subsMap = Seq(
      "vfdiv" -> 1,
      "vfrdiv" -> 2,
      "vfsqrt" -> 8
    )
    def value(op: Op): Boolean = subsMap.exists(a => op.name.contains(a._1))

    def uop(op: Op): Int = {
      val mapFilter = subsMap.filter(a => op.name.contains(a._1))
      if (mapFilter.isEmpty) 0 else mapFilter.head._2
    }
  }

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

    def value(op: Op): Boolean = subsMap.exists(a => op.name.contains(a._1))

    def uop(op: Op): Int = {
      val mapFilter = subsMap.filter(a => op.name.contains(a._1))
      if (mapFilter.isEmpty) 0 else mapFilter.head._2
    }
  }

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

    def value(op: Op): Boolean = subsMap.exists(a => op.name.contains(a._1))

    def uop(op: Op): Int = {
      val mapFilter = subsMap.filter(a => op.name.contains(a._1))
      if (mapFilter.isEmpty) 0 else mapFilter.head._2
    }
  }

  object fpExecutionType extends FloatType {
    def genTable(op: Op): BitPat = {
      val code: Int = if (FDiv.value(op)) 1 else if (FCompare.value(op)) 2 else if (FOther.value(op)) 3 else 0
      BitPat("b" + ("00" + code.toBinaryString).takeRight(2))
    }
  }

  object firstWiden extends BoolField {
    def value(op: Op): Boolean = op.name.endsWith(".w") || vwmacc.value(op)
  }

  object nr extends BoolField {
    def value(op: Op): Boolean = op.name.contains("<nr>")
  }

  object red extends BoolField {
    // reduce 会影响special, special会极大地影响性能, 所以不能dc
    def value(op: Op): Boolean = op.name.contains("red") || op.name.contains("pop")
  }

  // TODO: remove this.
  object maskOp extends BoolField {
    def value(op: Op): Boolean = op.name.startsWith("vm") &&
      ((adder.value(op) && !Seq("min", "max").exists(op.name.contains)) || logic.value(op))
  }

  object reverse extends BoolField {
    def value(op: Op): Boolean = op.name == "vrsub"
  }

  object narrow extends BoolField {
    val subs: Seq[String] = Seq(
      "vnsrl",
      "vnsra",
      "vnclip"
    )
    // todo: 确认是否可以dc
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = subs.exists(op.name.contains)
  }

  object crossWrite extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = op.name.startsWith("vw") && !op.name.startsWith("vwred")
  }

  object widenReduce extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = op.name.startsWith("vwred")
  }

  object saturate extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = Seq("vsa", "vss", "vsm").exists(op.name.startsWith)
  }

  object average extends BoolField {
    val subs: Seq[String] = Seq(
      "vaa",
      "vas"
    )
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op:                 Op): Boolean = subs.exists(op.name.startsWith)
  }

  object unsigned0 extends BoolField {
    def value(op: Op): Boolean = {
      val nameWoW = op.name.replace(".w", "")
      val logicShift = shift.genTable(op) == y && op.name.endsWith("l")
      val UIntOperation = nameWoW.endsWith("u") && !nameWoW.endsWith("su")
      val mul = op.name.contains("mulhsu") || op.name.contains("wmulsu") || op.name.contains("vwmaccus")
      val madc = Seq("adc", "sbc").exists(op.name.contains) && op.name.startsWith("vm")
      op.special.nonEmpty || logicShift || UIntOperation || mul || madc
    }
  }

  object unsigned1 extends BoolField {
    def value(op: Op): Boolean = {
      val nameWoW = op.name.replace(".w", "")
      val logicShift = shift.genTable(op) == y && op.name.endsWith("l")
      val UIntOperation = nameWoW.endsWith("u") && !nameWoW.endsWith("su")
      val madc = Seq("adc", "sbc").exists(op.name.contains) && op.name.startsWith("vm")
      val vwmaccsu = op.name.contains("vwmaccsu")
      if (floatType.value(op)) {
        FOther.unsignedMap.exists(a => op.name.contains(a._1))
      } else {
        op.special.nonEmpty || logicShift || UIntOperation || madc || vwmaccsu
      }
    }
  }

  object vtype extends BoolField {
    def value(op: Op): Boolean = op.funct3 == "V"
  }

  object itype extends BoolField {
    def value(op: Op): Boolean = op.funct3 == "I"
  }

  object targetRd extends BoolField {
    def value(op: Op): Boolean = op.special.isDefined &&
      (op.special.get.name == "VWXUNARY0" || op.special.get.name == "VWFUNARY0")
  }

  object extend extends BoolField {
    def value(op: Op): Boolean = op.special.isDefined && op.special.get.name == "VXUNARY0"
  }

  object mv extends BoolField {
    def value(op: Op): Boolean = (op.name.startsWith("vmv") || op.name.startsWith("vfmv")) && !op.name.contains("nr")
  }

  object ffo extends BoolField {
    val subs: Seq[String] = Seq(
      "vfirst",
      "vmsbf",
      "vmsof",
      "vmsif"
    )

    def value(op: Op): Boolean = subs.exists(op.name.contains)
  }

  object slid extends BoolField {
    def value(op: Op): Boolean = op.name.contains("slid")
  }

  object gather extends BoolField {
    def value(op: Op): Boolean = op.name.contains("rgather")
  }

  object gather16 extends BoolField {
    def value(op: Op): Boolean = op.name.contains("rgatherei16")
  }

  object compress extends BoolField {
    def value(op: Op): Boolean = op.name.contains("compress")
  }

  object readOnly extends BoolField {
    def value(op: Op): Boolean = {
      val vGather:  Boolean = op.name.contains("gather") && vtype.genTable(op) == y
      val compress: Boolean = op.name.contains("compress")
      val iota:     Boolean = op.name.contains("iota")
      val extend:   Boolean = op.name.contains("ext.vf")
      vGather || compress || iota || extend
    }
  }

  object popCount extends BoolField {
    def value(op: Op): Boolean = op.name == "vcpop"
  }

  object iota extends BoolField {
    def value(op: Op): Boolean = op.name == "viota"
  }

  object id extends BoolField {
    def value(op: Op): Boolean = op.name == "vid"
  }

  object vwmacc extends BoolField {
    def value(op: Op): Boolean = op.name.contains("vwmacc")
  }

  object unOrderWrite extends BoolField {
    def value(op: Op): Boolean = slid.value(op) || iota.value(op)
  }

  // TODO[2]: uop should be well documented
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
        val high = op.name.contains("mulh")
        // 0b1000
        val negative = if (op.name.startsWith("vn")) 8 else 0
        // 0b100
        val asAddend = if (Seq("c", "cu", "cus", "csu").exists(op.name.endsWith)) 4 else 0
        val n = if (high) 3 else firstIndexContains(mul, op.name)
        negative + asAddend + n
      } else if (divider.value(op)) {
        firstIndexContains(divider.subs, op.name)
      } else if (adder.value(op)) {
        if (op.name.contains("sum")) 0 else firstIndexContains(adder.subs, op.name)
      } else if (logic.value(op)) {
        val isXnor = op.name == "vmxnor"
        val isXor = op.name.contains("xor")
        val notX = if (op.name.startsWith("vmn")) 8 else 0
        val xNot = if (isXnor || op.name.endsWith("n")) 4 else 0
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

  object maskLogic extends BoolField {
    def value(op: Op): Boolean = {
      // todo: rename maskLogic -> maskOperation
      val otherMaskOperation = Seq("sbf", "sif", "sof", "first", "cpop", "viota").exists(op.name.contains)
      val logicMaskOperation = op.name.startsWith("vm") && logic.genTable(op) == y
      logicMaskOperation || otherMaskOperation
    }
  }

  object maskDestination extends BoolField {
    def value(op: Op): Boolean =
      (op.name.startsWith("vm") && adder.value(op) && !Seq("min", "max").exists(op.name.contains)) ||
        (op.name.startsWith("vm") && floatType.value(op))
  }

  object maskSource extends BoolField {
    def value(op: Op): Boolean = Seq("vadc", "vsbc", "vmadc", "vmsbc", "vmerge", "vfmerge").exists(op.name.startsWith)
  }

  object indexType extends BoolField {
    override def containsLSU: Boolean = true
    // funct6 的最低位是mop(0)
    def value(op: Op): Boolean = !op.notLSU && op.funct6.endsWith("1")
  }

  // special -> maskUnit || index type load store
  object special extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean = {
      Seq(indexType, maskUnit).map(_.value(op)).reduce(_ || _)
    }
  }

  // mask unit -> red || compress || viota || ffo || slid || maskDestination || gather(v) || mv || popCount || extend
  object maskUnit extends BoolField {
    def value(op: Op): Boolean = {
      Seq(red, compress, iota, ffo, slid, maskDestination, mv, popCount, extend)
        .map(_.value(op))
        .reduce(_ || _) || (gather.value(op) && vtype.value(op))
    }
  }

  // crossRead -> narrow || firstWiden
  object crossRead extends BoolField {
    def value(op: Op): Boolean = Seq(narrow, firstWiden).map(_.value(op)).reduce(_ || _)
  }

  //sWrite -> targetRd || readOnly || crossWrite || maskDestination || reduce || loadStore
  object sWrite extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean =
      Seq(targetRd, readOnly, crossWrite, maskDestination, red).map(_.value(op)).reduce(_ || _) || !op.notLSU
  }

  // decodeResult(Decoder.multiplier) && decodeResult(Decoder.uop)(1, 0).xorR && !decodeResult(Decoder.vwmacc)
  object ma extends BoolField {
    def value(op: Op): Boolean = {
      (multiplier.value(op) && Seq(BitPat("b??01"), BitPat("b??10")).exists(_.cover(uop.genTable(op))) &&
      !vwmacc.value(op)) || (floatType.value(op) && FMA.maMap.exists(a => op.name.contains(a._1)))
    }
  }

  // sReadVD -> !(ma || maskLogic)
  object sReadVD extends BoolField {
    def value(op: Op): Boolean = !Seq(ma, maskLogic).map(_.value(op)).reduce(_ || _)
  }

  // wScheduler 原来与 sScheduler 如果出错了需要检查一下,真不一样需要说明记录
  //sScheduler -> maskDestination || red || readOnly || ffo || popCount || loadStore
  object scheduler extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean =
      !(Seq(maskDestination, red, readOnly, ffo, popCount).map(_.value(op)).reduce(_ || _) || !op.notLSU)
  }

  // sExecute 与 wExecuteRes 也不一样,需要校验
  // sExecute -> readOnly || nr || loadStore
  object dontNeedExecuteInLane extends BoolField {
    override def containsLSU: Boolean = true
    def value(op: Op): Boolean =
      Seq(readOnly, nr).map(_.value(op)).reduce(_ || _) || !op.notLSU
  }

  // lane中只能在slot0中执行的指令
  // specialSlot -> crossRead || crossWrite || maskLogic || maskDestination || maskSource
  object specialSlot extends BoolField {
    def value(op: Op): Boolean =
      Seq(crossRead, crossWrite, maskLogic, maskDestination, maskSource).map(_.value(op)).reduce(_ || _)
  }

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
          floatMul
        )
      else Seq()
    }
  }

  def decodeTable(fpuEnable: Boolean): DecodeTable[Op] =
    new DecodeTable[Op](SpecInstTableParser.ops(fpuEnable), all(fpuEnable))
  def decode(fpuEnable: Boolean): UInt => DecodeBundle = decodeTable(fpuEnable).decode
  def bundle(fpuEnable: Boolean): DecodeBundle = decodeTable(fpuEnable).bundle
}
