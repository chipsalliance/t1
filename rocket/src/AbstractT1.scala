package org.chipsalliance.t1.rockettile

import chisel3._
import chisel3.properties.{ClassType, Path, Property}
import chisel3.util._
import chisel3.util.experimental.BitSet
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tile.BaseTile
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config._
import org.chipsalliance.t1.rocketcore.RocketTileModuleImp

case object BuildT1 extends Field[Option[Parameters => AbstractLazyT1]](None)

/** Request from CPU to Vector.
  * aligned: core -> vector
  * flipped: vector -> core
  */
class VectorRequest(xLen: Int) extends Bundle {

  /** instruction fetched by scalar processor. */
  val instruction: UInt = UInt(32.W)

  /** data read from scalar RF RS1. */
  val rs1Data: UInt = UInt(xLen.W)

  /** data read from scalar RF RS2. */
  val rs2Data: UInt = UInt(xLen.W)
}

/** Response from Vector to CPU.
  * aligned: vector -> core
  * flipped: core -> vector
  */
class VectorResponse(xLen: Int) extends Bundle {

  /** data write to scalar rd. */
  val data: UInt = UInt(xLen.W)

  /** assert of [[rd.valid]] indicate vector need to write rd,
    * the [[rd.bits]] is the index of rd
    */
  val rd: Valid[UInt] = Valid(UInt(log2Ceil(32).W))

  val float: Bool = Bool()

  /** Vector Fixed-Point Saturation Flag, propagate to vcsr in CSR.
    * This is not maintained in the vector coprocessor since it is not used in the Vector processor.
    */
  val vxsat: Bool = Bool()

  val mem: Bool = Bool()
}

class CSRInterface(vlWidth: Int) extends Bundle {

  /** Vector Length Register `vl`,
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#35-vector-length-register-vl]]
    */
  val vl: UInt = UInt(vlWidth.W)

  /** Vector Start Index CSR `vstart`,
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#37-vector-start-index-csr-vstart]]
    * TODO: rename to `vstart`
    */
  val vStart: UInt = UInt(vlWidth.W)

  /** Vector Register Grouping `vlmul[2:0]`
    * subfield of `vtype`
    * see table in [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#342-vector-register-grouping-vlmul20]]
    */
  val vlmul: UInt = UInt(3.W)

  /** Vector Register Grouping (vlmul[2:0])
    * subfield of `vtype``
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#341-vector-selected-element-width-vsew20]]
    */
  val vSew: UInt = UInt(2.W)

  /** Rounding mode register
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm]]
    */
  val vxrm: UInt = UInt(2.W)

  /** Vector Tail Agnostic
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm]]
    *
    * we always keep the undisturbed behavior, since there is no rename here.
    */
  val vta: Bool = Bool()

  /** Vector Mask Agnostic
    * see [[https://github.com/riscv/riscv-v-spec/blob/8c8a53ccc70519755a25203e14c10068a814d4fd/v-spec.adoc#38-vector-fixed-point-rounding-mode-register-vxrm]]
    *
    * we always keep the undisturbed behavior, since there is no rename here.
    */
  val vma: Bool = Bool()
}

/** IO for maintaining the memory hazard between Scalar and Vector core.
  * aligned: core -> vector
  * flipped: vector -> core
  */
class VectorHazardControl extends Bundle {

  /** vector issue queue is empty, there are no pending vector instructions, scalar can handle interrupt if it is asserted. */
  val issueQueueFull: Bool = Flipped(Bool())

  /** vector issue queue is full, scalar core cannot issue any vector instructions,
    * should back pressure the vector issue datapath(but don't block the entire pipeline).
    */
  val issueQueueEmpty: Bool = Flipped(Bool())

  /** Scalar core store buffer is cleared. So Vector memory can start to issue to memory subsystem. */
  val storeBufferClear: Bool = Flipped(Bool())

  /** tile grant an token. */
  val loadStoreTokenGrant: Bool = Flipped(Bool())

  /** scalar release an token. */
  val loadStoreTokenRelease: Bool = Flipped(Bool())
}

case class T1LSUParameter(name: String, banks: Seq[Seq[AddressSet]], sourceIdSize: Int)

/** The hierarchy under [[BaseTile]]. */
abstract class AbstractLazyT1()(implicit p: Parameters) extends LazyModule {
  def module:          AbstractLazyT1ModuleImp
  def xLen:            Int
  def vlMax:           Int
  def uarchName:       String
  def t1LSUParameters: T1LSUParameter

  def bitsetToAddressSet(bitset: BitSet): Seq[AddressSet] = bitset.terms.map(bp => AddressSet(bp.value, bp.mask ^ ((BigInt(1) << bp.width) - 1))).toSeq

  val t1LSUNode = TLClientNode(
    t1LSUParameters.banks.zipWithIndex.map {
      case (addresses, bank) =>
        TLMasterPortParameters.v1(
          Seq(
            TLMasterParameters.v1(
              name = s"${uarchName}_bank$bank",
              sourceId = IdRange(0, (1 << t1LSUParameters.sourceIdSize) - 1),
              visibility = addresses
            )
          )
        )
    }
  )
  val requestSinkNode: BundleBridgeSink[DecoupledIO[VectorRequest]] =
    BundleBridgeSink[DecoupledIO[VectorRequest]]()
  val csrSinkNode: BundleBridgeSink[CSRInterface] =
    BundleBridgeSink[CSRInterface]()
  val responseNode: BundleBridgeSource[ValidIO[VectorResponse]] =
    BundleBridgeSource(() => Valid(new VectorResponse(xLen)))
  val hazardControlNode: BundleBridgeSource[VectorHazardControl] =
    BundleBridgeSource(() => Output(new VectorHazardControl))
  // Diplomacy is dirty and doesn't support Property yet, this is a dirty hack and will be bore from top
  val om = InModuleBody {
    // TODO: maybe affect by [[https://github.com/llvm/circt/issues/6866]]:
    //       Abstract T1 cannot view the classpath in T1OM.
    val t1OMType = ClassType.unsafeGetClassTypeByName("T1OM")
    IO(Output(Property[t1OMType.Type]())).suggestName("T1OM")
  }
}

/** This is a vector interface comply to chipsalliance/t1 project.
  * but is should be configurable module for fitting different vector architectures
  */
abstract class AbstractLazyT1ModuleImp(val outer: AbstractLazyT1)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val request:       DecoupledIO[VectorRequest] = outer.requestSinkNode.bundle
  val csr:           CSRInterface = outer.csrSinkNode.bundle
  val response:      ValidIO[VectorResponse] = outer.responseNode.bundle
  val hazardControl: VectorHazardControl = outer.hazardControlNode.bundle
  val om:            Property[ClassType] = outer.om
}

trait HasLazyT1 { this: BaseTile =>
  // TODO: We should manage the T1 PRCI under the T1's own PRCI Domain in the future.
  val t1: Option[AbstractLazyT1] = p(BuildT1).map(_(p))
  val requestNode: Option[BundleBridgeSource[DecoupledIO[VectorRequest]]] =
    t1.map(_ => BundleBridgeSource(() => Decoupled(new VectorRequest(xLen))))
  requestNode.zip(t1.map(_.requestSinkNode)).foreach { case (src, dst) => dst := src }
  val csrNode: Option[BundleBridgeSource[CSRInterface]] = t1.map(_ => BundleBridgeSource(() => new CSRInterface(11)))
  csrNode.zip(t1.map(_.csrSinkNode)).foreach { case (src, dst) => dst := src }
  val responseSinkNode:      Option[BundleBridgeSink[ValidIO[VectorResponse]]] = t1.map(_.responseNode.makeSink())
  val hazardControlSinkNode: Option[BundleBridgeSink[VectorHazardControl]] = t1.map(_.hazardControlNode.makeSink())
}

trait HasLazyT1Module { this: RocketTileModuleImp =>

  outer.t1.map(_.module).foreach { t1Module: AbstractLazyT1ModuleImp =>
    // TODO: make it configurable
    val maxCount: Int = 32
    val vlMax:    Int = outer.t1.get.vlMax
    val xLen:     Int = 32

    val instructionQueue: Option[Queue[VectorRequest]] =
      core.t1Request.map(req => Module(new Queue(chiselTypeOf(req.bits), maxCount)))
    val instructionDequeue: Option[DecoupledIO[VectorRequest]] =
      core.t1Request.map(_ => Wire(Decoupled(new VectorRequest(xLen))))

    val csr: Option[CSRInterface] = instructionQueue.map { queue =>
      queue.io.enq.valid := core.t1Request.get.valid
      queue.io.enq.bits := core.t1Request.get.bits
      assert(queue.io.enq.ready || !core.t1Request.get.valid, "t1 instruction queue exploded.")

      val csrReg: CSRInterface = RegInit(0.U.asTypeOf(new CSRInterface(log2Ceil(vlMax + 1))))
      val deqInst = queue.io.deq.bits.instruction
      val isSetVl: Bool = (deqInst(6, 0) === "b1010111".U) && (deqInst(14, 12) === 7.U)
      // set vl type
      val vsetvli = !deqInst(31)
      val vsetivli = deqInst(31, 30).andR
      val vsetvl = deqInst(31) && !deqInst(30)
      // v type set
      val newVType = Mux1H(
        Seq(
          (vsetvli || vsetivli) -> deqInst(27, 20),
          vsetvl -> queue.io.deq.bits.rs2Data(7, 0)
        )
      )

      // vlmax = vlen * lmul / sew
      val vlmax: UInt = (true.B << (log2Ceil(vlMax) - 6) << (newVType(2, 0) + 3.U) >> newVType(5, 3)).asUInt

      val rs1IsZero = deqInst(19, 15) === 0.U
      val rdIsZero = deqInst(11, 7) === 0.U
      // set vl
      val setVL = Mux1H(
        Seq(
          ((vsetvli || vsetvl) && !rs1IsZero) ->
            Mux(queue.io.deq.bits.rs1Data > vlmax, vlmax, queue.io.deq.bits.rs1Data),
          ((vsetvli || vsetvl) && rs1IsZero && !rdIsZero) -> vlmax,
          ((vsetvli || vsetvl) && rs1IsZero && rdIsZero) -> csrReg.vl,
          vsetivli -> deqInst(19, 15)
        )
      )
      // todo: vxrm
      when(queue.io.deq.fire && isSetVl) {
        csrReg.vl := setVL
        csrReg.vlmul := newVType(2, 0)
        csrReg.vSew := newVType(5, 3)
        csrReg.vta := newVType(6)
        csrReg.vma := newVType(7)
      }

      instructionDequeue.get <> queue.io.deq
      instructionDequeue.get.valid := queue.io.deq.valid && !isSetVl
      queue.io.deq.ready := instructionDequeue.get.ready || isSetVl
      core.t1IssueQueueRelease.foreach(_ := queue.io.deq.fire)
      csrReg
    }

    // pull bundle bridge from T1 here:
    // TODO: I wanna make RocketCore diplomatic...
    instructionDequeue.zip(outer.requestNode.map(_.bundle)).foreach {
      case (c, v) =>
        v <> c
    }
    csr.zip(outer.csrNode.map(_.bundle)).foreach {
      case (c, v) =>
        v := c
    }
    core.t1Response.zip(outer.responseSinkNode.map(_.bundle)).foreach {
      case (c, v) =>
        // todo: param entries
        val vectorCommitQueue = Module(new Queue(chiselTypeOf(v.bits), 16))
        vectorCommitQueue.io.enq.valid := v.valid
        vectorCommitQueue.io.enq.bits := v.bits
        assert(!v.valid || vectorCommitQueue.io.enq.ready, "vector commit queue exploded")
        c <> vectorCommitQueue.io.deq
    }
    outer.hazardControlSinkNode.map(_.bundle).foreach { c =>
      c.loadStoreTokenGrant := false.B
      c.issueQueueEmpty := false.B
      c.issueQueueFull := false.B
      // TODO: fixme
      c.storeBufferClear := true.B
    }
  }
}
