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
    def value(op: Op): Boolean = subs.exists(op.name.contains)
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
      !(op.tpe == "M" && Seq("vm", "vnm").exists(op.name.startsWith))
  }

  object shift extends BoolField {
    val subs: Seq[String] = Seq(
      "srl",
      "sll",
      "sra"
    )
    def value(op: Op): Boolean = subs.exists(op.name.contains)
  }

  object multiplier extends BoolField {
    val subs: Seq[String] = Seq(
      "mul",
      "madd",
      "macc",
      "msub",
      "msac"
    )
    def value(op: Op): Boolean = subs.exists(op.name.contains)
  }

  object divider extends BoolField {
    val subs: Seq[String] = Seq(
      "div",
      "rem"
    )
    def value(op: Op): Boolean = subs.exists(op.name.contains)
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
      op.special.nonEmpty || logicShift || UIntOperation || madc || vwmaccsu
    }
  }

  object vtype extends BoolField {
    def value(op: Op): Boolean = op.funct3 == "V"
  }

  object itype extends BoolField {
    def value(op: Op): Boolean = op.funct3 == "I"
  }

  object targetRd extends BoolField {
    def value(op: Op): Boolean = op.special.isDefined && op.special.get.name == "VWXUNARY0"
  }

  object extend extends BoolField {
    def value(op: Op): Boolean = op.special.isDefined && op.special.get.name == "VXUNARY0"
  }

  object mv extends BoolField {
    def value(op: Op): Boolean = op.name.startsWith("vmv") && !op.name.contains("nr")
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
    def value(op: Op): Boolean = slid.value(op)
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
      val opcode: Int = if (multiplier.value(op)) {
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

  object topUop extends UopField {
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
      op.name.startsWith("vm") && adder.value(op) && !Seq("min", "max").exists(op.name.contains)
  }

  object maskSource extends BoolField {
    def value(op: Op): Boolean = Seq("vadc", "vsbc", "vmadc", "vmsbc", "vmerge").exists(op.name.startsWith)
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
      multiplier.value(op) && Seq(BitPat("b??01"), BitPat("b??10")).exists(_.cover(uop.genTable(op))) &&
      !vwmacc.value(op)
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
  object execute extends BoolField {
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

  val all: Seq[DecodeField[Op, _ >: Bool <: UInt]] = Seq(
    logic,
    adder,
    shift,
    multiplier,
    divider,
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
    execute,
    reverse, // uop
    average, // uop


    ffo, // todo: add mask select -> top uop
    popCount, // top uop add, red, uop popCount
    topUop,
    specialSlot
  )

  private val decodeTable: DecodeTable[Op] = new DecodeTable[Op](SpecInstTableParser.ops, all)
  def decode:              UInt => DecodeBundle = decodeTable.decode
  def bundle:              DecodeBundle = decodeTable.bundle
}
