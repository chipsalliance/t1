// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2024 Jiuyang Liu <liu@jiuyang.me>
package org.chipsalliance.amba.axi4

import chisel3.{
  fromBooleanToLiteral,
  fromIntToLiteral,
  fromIntToWidth,
  fromStringToLiteral
}

package object bundle {
  object verilog {
    object irrevocable {
      def apply(parameter: AXI4BundleParameter): AXI4VerilogBundle = {
        if (parameter.isRW) new AXI4RWIrrevocableVerilog(parameter)
        else if (parameter.isRO) new AXI4ROIrrevocableVerilog(parameter)
        else new AXI4WOIrrevocableVerilog(parameter)
      }
    }
  }
  object chisel {
    object irrevocable {
      def apply(parameter: AXI4BundleParameter): AXI4ChiselBundle = {
        if (parameter.isRW) new AXI4RWIrrevocable(parameter)
        else if (parameter.isRO) new AXI4ROIrrevocable(parameter)
        else new AXI4WOIrrevocable(parameter)
      }
    }
  }

  object enum {

    /** Table A3-3 Burst type encoding */
    object burst {

      /** In a fixed burst:
        *   - The address is the same for every transfer in the burst.
        *   - The byte lanes that are valid are constant for all beats in the
        *     burst. However, within those byte lanes, the actual bytes that
        *     have WSTRB asserted can differ for each beat in the burst.
        *
        * This burst type is used for repeated accesses to the same location
        * such as when loading or emptying a FIFO.
        */
      val FIXED = 0.U(2.W)

      /** Incrementing. In an incrementing burst, the address for each transfer
        * in the burst is an increment of the address for the previous transfer.
        * The increment value depends on the size of the transfer. For example,
        * for an aligned start address, the address for each transfer in a burst
        * with a size of 4 bytes is the previous address plus four. This burst
        * type is used for accesses to normal sequential memory.
        */
      val INCR = 1.U(2.W)

      /** A wrapping burst is similar to an incrementing burst, except that the
        * address wraps around to a lower address if an upper address limit is
        * reached. The following restrictions apply to wrapping bursts:
        *   - The start address must be aligned to the size of each transfer.
        *   - The length of the burst must be 2, 4, 8, or 16 transfers. The
        * behavior of a wrapping burst is:
        *
        *   - The lowest address that is used by the burst is aligned to the
        * total size of the data to be transferred, that is, to ((size of each
        * transfer in the burst) × (number of transfers in the burst)). This
        * address is defined as the wrap boundary.
        *   - After each transfer, the address increments in the same way as for
        * an INCR burst. However, if this incremented address is ((wrap
        * boundary) + (total size of data to be transferred)), then the address
        * wraps round to the wrap boundary.
        *   - The first transfer in the burst can use an address that is higher
        * than the wrap boundary, subject to the restrictions that apply to
        * wrapping bursts. The address wraps for any WRAP burst when the first
        * address is higher than the wrap boundary.
        *
        * This burst type is used for cache line accesses.
        */
      val WRAP = 2.U(2.W)
    }

    /** Table A7-2 AXI4 atomic access encoding */
    object lock {
      val NormalAccess = 0.U(1.W)
      val ExclusiveAccess = 0.U(1.W)
    }

    /** Table A4-5 Memory type encoding */
    object cache {
      val DeviceNonbufferable = "0b0000".U(4.W)
      val DeviceBufferable = "0b0001".U(4.W)
      val NormalNoncacheableNonbufferable = "0b0010".U(4.W)
      val NormalNoncacheableBufferable = "0b0011".U(4.W)
      val WriteThroughReadWriteAllocate = "0b1110".U(4.W)
      val WriteBackReadWriteAllocate = "0b1111".U(4.W)
      object ar {
        val DeviceNonbufferable = cache.DeviceNonbufferable
        val DeviceBufferable = cache.DeviceBufferable
        val NormalNoncacheableNonbufferable =
          cache.NormalNoncacheableNonbufferable
        val NormalNoncacheableBufferable = cache.NormalNoncacheableBufferable
        val WriteThroughNoAllocate = "0b1010".U(4.W)
        val WriteThroughReadAllocate = "0b1110".U(4.W)
        val WriteThroughWriteAllocate = "0b1010".U(4.W)
        val WriteThroughReadWriteAllocate = cache.WriteThroughReadWriteAllocate
        val WriteBackNoAllocate = "0b1011".U(4.W)
        val WriteBackReadAllocate = "0b1111".U(4.W)
        val WriteBackWriteAllocate = "0b1011".U(4.W)
        val WriteBackReadWriteAllocate = cache.WriteBackReadWriteAllocate
      }
      object aw {
        val DeviceNonbufferable = cache.DeviceNonbufferable
        val DeviceBufferable = cache.DeviceBufferable
        val NormalNoncacheableNonbufferable =
          cache.NormalNoncacheableNonbufferable
        val NormalNoncacheableBufferable = cache.NormalNoncacheableBufferable
        val WriteThroughNoAllocate = "0b0110".U(4.W)
        val WriteThroughReadAllocate = "0b0110".U(4.W)
        val WriteThroughWriteAllocate = "0b1110".U(4.W)
        val WriteThroughReadWriteAllocate = cache.WriteThroughReadWriteAllocate
        val WriteBackNoAllocate = "0b0111".U(4.W)
        val WriteBackReadAllocate = "0b0111".U(4.W)
        val WriteBackWriteAllocate = "0b1111".U(4.W)
        val WriteBackReadWriteAllocate = cache.WriteBackReadWriteAllocate
      }
    }

    /** Table A4-6 Protection encoding
      * @note
      *   the chisel type of prot should be Vec(3, Bool()) for better
      *   assignment.
      */
    object prot {
      val UnprivilegedAccess = false.B
      val PrivilegedAccess = true.B
      val SecureAccess = false.B
      val NonSecureAccess = true.B
      val DataAccess = false.B
      val InstructionAccess = true.B
    }
  }

  object AXI4BundleParameter {
    implicit def rw: upickle.default.ReadWriter[AXI4BundleParameter] =
      upickle.default.macroRW[AXI4BundleParameter]
  }

  /** All physical information to construct any [[AXI4Bundle]]. TODO: I'm
    * wondering how to express the `user` field in Chisel Type: This is not easy
    * because we don't have a serializable Chisel Type, Neither firrtl type can
    * be convert to Chisel Type. To keep the ABI stable. Users must convert
    * their chisel type to bare UInt.
    */
  case class AXI4BundleParameter(
      idWidth: Int,
      dataWidth: Int,
      addrWidth: Int,
      userReqWidth: Int,
      userDataWidth: Int,
      userRespWidth: Int,
      hasAW: Boolean,
      hasW: Boolean,
      hasB: Boolean,
      hasAR: Boolean,
      hasR: Boolean,
      supportId: Boolean,
      supportRegion: Boolean,
      supportLen: Boolean,
      supportSize: Boolean,
      supportBurst: Boolean,
      supportLock: Boolean,
      supportCache: Boolean,
      supportQos: Boolean,
      supportStrb: Boolean,
      supportResp: Boolean,
      supportProt: Boolean
  ) {
    val isRW: Boolean = hasAW && hasW && hasB && hasAR && hasR
    val isRO: Boolean = !isRW && hasAR && hasR
    val isWO: Boolean = !isRW && hasAW && hasW && hasB

    override def toString: String = Seq(
      Some(s"ID$idWidth"),
      Some(s"DATA$dataWidth"),
      Some(s"ADDR$addrWidth"),
      Option.when(userReqWidth != 0)(s"USER_REQ$userReqWidth"),
      Option.when(userDataWidth != 0)(s"USER_DATA$userDataWidth"),
      Option.when(userRespWidth != 0)(s"USER_RESP$userRespWidth")
    ).flatten.mkString("_")

    val awUserWidth: Int = userReqWidth
    val wUserWidth: Int = userDataWidth
    val bUserWidth: Int = userRespWidth
    val arUserWidth: Int = userReqWidth
    val rUserWidth: Int = userDataWidth + userRespWidth
    require(
      Seq(8, 16, 32, 64, 128, 256, 512, 1024).contains(dataWidth),
      "A1.2.1: The data bus, which can be 8, 16, 32, 64, 128, 256, 512, or 1024 bits wide. A read response signal indicating the completion status of the read transaction."
    )
    require(
      (0 <= userReqWidth && userReqWidth <= 128) &&
        (0 <= userReqWidth && userReqWidth <= 128) &&
        (0 <= userReqWidth && userReqWidth <= 128),
      "The presence and width of User signals is specified by the properties in Table A8-1"
    )
  }

  /** Generic bundle for AXI4, only used in this repo. TODO: Do we need to
    * support absent signals? The current is no, since PnR flow has a good
    * support to tie0/tie1. But we need some default signals support like
    * RocketChip did in the BundleMap. In the future, we may leverage
    * chipsalliance/chisel#3978 for better connect handing.
    */
  trait AXI4Bundle extends chisel3.Bundle {
    val parameter: AXI4BundleParameter
    override def typeName: String = super.typeName + "_" + parameter.toString
    val idWidth: Int = parameter.idWidth
    val dataWidth: Int = parameter.dataWidth
    val addrWidth: Int = parameter.addrWidth
    val awUserWidth: Int = parameter.awUserWidth
    val wUserWidth: Int = parameter.wUserWidth
    val bUserWidth: Int = parameter.bUserWidth
    val arUserWidth: Int = parameter.arUserWidth
    val rUserWidth: Int = parameter.rUserWidth
    // @todo these are parameters for [[HasCustomConnectable]] API
    val supportId: Boolean = parameter.supportId
    val supportRegion: Boolean = parameter.supportRegion
    val supportLen: Boolean = parameter.supportLen
    val supportSize: Boolean = parameter.supportSize
    val supportBurst: Boolean = parameter.supportBurst
    val supportLock: Boolean = parameter.supportLock
    val supportCache: Boolean = parameter.supportCache
    val supportQos: Boolean = parameter.supportQos
    val supportStrb: Boolean = parameter.supportStrb
    val supportResp: Boolean = parameter.supportResp
    val supportProt: Boolean = parameter.supportProt
  }

  implicit val rwV2C: chisel3.experimental.dataview.DataView[
    AXI4RWIrrevocableVerilog,
    AXI4RWIrrevocable
  ] = chisel3.experimental.dataview
    .DataView[AXI4RWIrrevocableVerilog, AXI4RWIrrevocable](
      v => new AXI4RWIrrevocable(v.parameter),
      _.AWID -> _.aw.bits.id,
      _.AWADDR -> _.aw.bits.addr,
      _.AWLEN -> _.aw.bits.len,
      _.AWSIZE -> _.aw.bits.size,
      _.AWBURST -> _.aw.bits.burst,
      _.AWLOCK -> _.aw.bits.lock,
      _.AWCACHE -> _.aw.bits.cache,
      _.AWPROT -> _.aw.bits.prot,
      _.AWQOS -> _.aw.bits.qos,
      _.AWREGION -> _.aw.bits.region,
      _.AWUSER -> _.aw.bits.user,
      _.AWVALID -> _.aw.valid,
      _.AWREADY -> _.aw.ready,
      _.WDATA -> _.w.bits.data,
      _.WSTRB -> _.w.bits.strb,
      _.WLAST -> _.w.bits.last,
      _.WUSER -> _.w.bits.user,
      _.WVALID -> _.w.valid,
      _.WREADY -> _.w.ready,
      _.BID -> _.b.bits.id,
      _.BRESP -> _.b.bits.resp,
      _.BUSER -> _.b.bits.user,
      _.BVALID -> _.b.valid,
      _.BREADY -> _.b.ready,
      _.ARID -> _.ar.bits.id,
      _.ARADDR -> _.ar.bits.addr,
      _.ARLEN -> _.ar.bits.len,
      _.ARSIZE -> _.ar.bits.size,
      _.ARBURST -> _.ar.bits.burst,
      _.ARLOCK -> _.ar.bits.lock,
      _.ARCACHE -> _.ar.bits.cache,
      _.ARPROT -> _.ar.bits.prot,
      _.ARQOS -> _.ar.bits.qos,
      _.ARREGION -> _.ar.bits.region,
      _.ARUSER -> _.ar.bits.user,
      _.ARVALID -> _.ar.valid,
      _.ARREADY -> _.ar.ready,
      _.RID -> _.r.bits.id,
      _.RDATA -> _.r.bits.data,
      _.RRESP -> _.r.bits.resp,
      _.RLAST -> _.r.bits.last,
      _.RUSER -> _.r.bits.user,
      _.RVALID -> _.r.valid,
      _.RREADY -> _.r.ready
    )
  implicit val rwC2V: chisel3.experimental.dataview.DataView[
    AXI4RWIrrevocable,
    AXI4RWIrrevocableVerilog
  ] = rwV2C.invert(c => new AXI4RWIrrevocableVerilog(c.parameter))
  implicit val roV2C: chisel3.experimental.dataview.DataView[
    AXI4ROIrrevocableVerilog,
    AXI4ROIrrevocable
  ] = chisel3.experimental.dataview
    .DataView[AXI4ROIrrevocableVerilog, AXI4ROIrrevocable](
      v => new AXI4ROIrrevocable(v.parameter),
      _.ARID -> _.ar.bits.id,
      _.ARADDR -> _.ar.bits.addr,
      _.ARLEN -> _.ar.bits.len,
      _.ARSIZE -> _.ar.bits.size,
      _.ARBURST -> _.ar.bits.burst,
      _.ARLOCK -> _.ar.bits.lock,
      _.ARCACHE -> _.ar.bits.cache,
      _.ARPROT -> _.ar.bits.prot,
      _.ARQOS -> _.ar.bits.qos,
      _.ARREGION -> _.ar.bits.region,
      _.ARUSER -> _.ar.bits.user,
      _.ARVALID -> _.ar.valid,
      _.ARREADY -> _.ar.ready,
      _.RID -> _.r.bits.id,
      _.RDATA -> _.r.bits.data,
      _.RRESP -> _.r.bits.resp,
      _.RLAST -> _.r.bits.last,
      _.RUSER -> _.r.bits.user,
      _.RVALID -> _.r.valid,
      _.RREADY -> _.r.ready
    )
  implicit val roC2V: chisel3.experimental.dataview.DataView[
    AXI4ROIrrevocable,
    AXI4ROIrrevocableVerilog
  ] = roV2C.invert(c => new AXI4ROIrrevocableVerilog(c.parameter))
  implicit val woV2C: chisel3.experimental.dataview.DataView[
    AXI4WOIrrevocableVerilog,
    AXI4WOIrrevocable
  ] = chisel3.experimental.dataview
    .DataView[AXI4WOIrrevocableVerilog, AXI4WOIrrevocable](
      v => new AXI4WOIrrevocable(v.parameter),
      _.AWID -> _.aw.bits.id,
      _.AWADDR -> _.aw.bits.addr,
      _.AWLEN -> _.aw.bits.len,
      _.AWSIZE -> _.aw.bits.size,
      _.AWBURST -> _.aw.bits.burst,
      _.AWLOCK -> _.aw.bits.lock,
      _.AWCACHE -> _.aw.bits.cache,
      _.AWPROT -> _.aw.bits.prot,
      _.AWQOS -> _.aw.bits.qos,
      _.AWREGION -> _.aw.bits.region,
      _.AWUSER -> _.aw.bits.user,
      _.AWVALID -> _.aw.valid,
      _.AWREADY -> _.aw.ready,
      _.WDATA -> _.w.bits.data,
      _.WSTRB -> _.w.bits.strb,
      _.WLAST -> _.w.bits.last,
      _.WUSER -> _.w.bits.user,
      _.WVALID -> _.w.valid,
      _.WREADY -> _.w.ready,
      _.BID -> _.b.bits.id,
      _.BRESP -> _.b.bits.resp,
      _.BUSER -> _.b.bits.user,
      _.BVALID -> _.b.valid,
      _.BREADY -> _.b.ready
    )
  implicit val woC2V: chisel3.experimental.dataview.DataView[
    AXI4WOIrrevocable,
    AXI4WOIrrevocableVerilog
  ] = woV2C.invert(c => new AXI4WOIrrevocableVerilog(c.parameter))
}
