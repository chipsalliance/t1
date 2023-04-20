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

trait BoolField extends BoolDecodeField[Op] with FieldName {
  def dontCareCase(op: Op): Boolean = false
  // 如果包含lsu, 那么value不会被纠正, 否则value只在不是lsu的情况下被视为1
  def containsLSU: Boolean = false
  def value(op: Op):Boolean
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
      "slide",
      "rgather",
      "merge",
      "mv",
      "clip",
      "compress"
    )
    // todo: special is other?
    def value(op: Op): Boolean = op.special.nonEmpty || subs.exists(op.name.contains)
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
    def value(op: Op): Boolean = subs.exists(op.name.contains)
  }

  object widen extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op: Op): Boolean = op.name.startsWith("vw") && !op.name.startsWith("vwred")
  }

  object widenReduce extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op: Op): Boolean = op.name.startsWith("vwred")
  }

  object saturate extends BoolField {
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op: Op): Boolean = Seq("vsa", "vss", "vsm").exists(op.name.startsWith)
  }

  object average extends BoolField {
    val subs: Seq[String] = Seq(
      "vaa",
      "vas"
    )
    override def dontCareCase(op: Op): Boolean = op.special.nonEmpty
    def value(op: Op): Boolean = subs.exists(op.name.startsWith)
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

  object xtype extends BoolField {
    def value(op: Op): Boolean = op.funct3 == "X"
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
      val vGather: Boolean = op.name.contains("gather") && vtype.genTable(op) == y
      val compress: Boolean = op.name.contains("compress")
      val iota: Boolean = op.name.contains("iota")
      val extend: Boolean = op.name.contains("ext.vf")
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
      val b2s = (b: Boolean) => if (b) "1" else "0"
      val firstIndexContains = (xs: Iterable[String], s: String) =>
        xs.map(s.indexOf).zipWithIndex.filter(_._1 != -1).head._2

      val table = if (op.special.nonEmpty) {
        "1???"
      } else if (multiplier.value(op)) {
        val high = op.name.contains("mulh")
        val n = if (high) 3 else firstIndexContains(mul, op.name)
        require(n < 4)
        b2s(op.name.startsWith("vn")) + b2s(Seq("c", "cu", "cus", "csu").exists(op.name.endsWith)) +
          (("00" + n.toBinaryString).takeRight(2))
      } else if (divider.value(op)) {
        val n = firstIndexContains(divider.subs, op.name)
        require(n < 2)
        "?" * 3 + n.toBinaryString
      } else if (adder.value(op)) {
        val n = if (op.name.contains("sum")) 0 else firstIndexContains(adder.subs, op.name)
        require(n < 16)
        (("0000" + n.toBinaryString).takeRight(4))
      } else if (logic.value(op)) {
        val isXnor = op.name == "vmxnor"
        val isXor = op.name.contains("xor")
        val n = if (isXnor || isXor) 2 else firstIndexContains(logic.subs, op.name)
        require(n < 4)
        b2s(op.name.startsWith("vmn")) +
          b2s(isXnor || op.name.endsWith("n")) +
          (("00" + n.toBinaryString).takeRight(2))
      } else if (shift.value(op)) {
        val n = firstIndexContains(shift.subs, op.name)
        require(n < 4)
        val ssr = op.name.contains("ssr")
        "?" * 1 + b2s(ssr) + ("00" + n.toBinaryString).takeRight(2)
      } else if (other.value(op)) {
        val n = if (slid.genTable(op) == y) {
          val up = if (op.name.contains("up")) 2 else 0
          val slid1 = if (op.name.contains("slide1")) 1 else 0
          up + slid1
        } else {
          firstIndexContains(other.subs, op.name)
        }
        require(n < 8)
        "0" + (("000" + n.toBinaryString).takeRight(3))
      } else {
        // unreachable
        println(op)
        require(false)
        "?" * 4
      }

      BitPat("b" + table)
    }
  }

  object specialUop extends UopField {
    def y: BitPat = BitPat.Y(1)
    def genTable(op: Op): BitPat = {
      val b2s = (b: Boolean) => if (b) "1" else "0"
      val table =
        if (op.special.isEmpty) "????"
        else
          "1" + (
            if (ffo.genTable(op) == y)
              "?" +
                (("00" + ffo.subs.indexOf(op.name).toBinaryString).takeRight(2))
            else if (op.special.get.name == "VXUNARY0") {
              val log2 = (x: Int) => (math.log10(x) / math.log10(2)).toInt
              b2s(op.name.startsWith("vs")) +
                (("00" + log2(op.name.last.toString.toInt).toBinaryString).takeRight(2))
            } else
              "?" * 3
          )
      BitPat("b" + table)
    }
  }

  object maskLogic extends BoolField {
    def value(op: Op): Boolean = {
      // todo: raname maskLogic -> maskOperation
      val otherMaskOperation = Seq("sbf", "sif", "sof", "first", "cpop", "viota").exists(op.name.contains)
      val logicMaskOperation = op.name.startsWith("vm") && logic.genTable(op) == y
      logicMaskOperation || otherMaskOperation
    }
  }

  object maskDestination extends BoolField {
    def value(op: Op): Boolean = op.name.startsWith("vm") && adder.value(op) && !Seq("min", "max").exists(op.name.contains)
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
    def value(op: Op): Boolean = {
      Seq(indexType, maskUnit).map(_.value(op)).reduce(_ || _)
    }
  }

  // mask unit -> red || compress || viota || ffo || slid || maskDestination || gather(v) || mv || popCount || extend
  object maskUnit extends BoolField {
    def value(op: Op): Boolean = {
      Seq(red, compress, iota, ffo, slid, maskDestination, mv, popCount, extend)
        .map(_.value(op)).reduce(_ || _) || (gather.value(op) && vtype.value(op))
    }
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

    vtype,
    xtype, // -> iType

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

    uop,

    maskLogic,
    maskDestination,
    maskSource,

    readOnly,
    vwmacc,
    saturate,
    special,
    maskUnit,

    firstWiden, // cross read
    reverse, // uop
    narrow, // cross read
    widen, // cross write
    average, // uop
    extend, // top uop
    mv, // uop
    ffo, // uop
    popCount, // top uop add, red, uop popCount
    iota, // top uop
    id, // delete
    specialUop, // uop
    maskOp, // 细分 mask destination, maskLogic, source

    //sWrite -> targetRd || readOnly || crossWrite || maskDestination || reduce || loadStore
    //crossWrite -> widen
    //sRead1 -> vType
    //sReadVD -> ma || maskLogic
    //crossRead -> narrow || firstWiden
    // wScheduler 原来与 sScheduler 如果出错了需要检查一下,真不一样需要说明记录
    //sScheduler -> maskDestination || red || readOnly || ffo || popCount || loadStore

    // sExecute 与 wExecuteRes 也不一样,需要校验
    // sExecute -> readOnly || nr || loadStore

    // unOrder -> slid
    // specialSlot -> crossRead || crossWrite || maskLogic || maskDestination || maskSource
  )

  private val decodeTable: DecodeTable[Op] = new DecodeTable[Op](SpecInstTableParser.ops, all)
  def decode:              UInt => DecodeBundle = decodeTable.decode
  def bundle:              DecodeBundle = decodeTable.bundle
}
