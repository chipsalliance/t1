// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1

import chisel3._
import chisel3.experimental.hierarchy.{Instance, Instantiate}
import chisel3.util._
import chisel3.util.experimental.decode.DecodeBundle
import org.chipsalliance.t1.rtl.decoder.TableGenerator
import org.chipsalliance.t1.rtl.lane.Distributor
import tilelink.{TLBundleParameter, TLChannelD}

package object rtl {
  def csa32(s: UInt, c: UInt, a: UInt): (UInt, UInt) = {
    val xor = s ^ c
    val so = xor ^ a
    val co = (xor & a) | (s & c)
    (so, co)
  }

  def bankSelect(vs: UInt, eew: UInt, groupIndex: UInt, readValid: Bool): UInt = {
    chisel3.util.experimental.decode.decoder.qmc(readValid ## eew(1, 0) ## vs(1, 0) ## groupIndex(1, 0), TableGenerator.BankEnableTable.res)
  }

  def instIndexL(a: UInt, b: UInt): Bool = {
    require(a.getWidth == b.getWidth)
    a === b || ((a(a.getWidth - 2, 0) < b(b.getWidth - 2, 0)) ^ a(a.getWidth - 1) ^ b(b.getWidth - 1))
  }

  def ffo(input: UInt): UInt = {
    ((~(scanLeftOr(input) << 1)).asUInt & input)(input.getWidth - 1, 0)
  }

  def maskAnd(mask: Bool, data: Data): Data = {
    Mux(mask, data, 0.U.asTypeOf(data))
  }

  def indexToOH(index: UInt, chainingSize: Int): UInt = {
    UIntToOH(index(log2Ceil(chainingSize) - 1, 0))
  }

  def ohCheck(lastReport: UInt, index: UInt, chainingSize: Int): Bool = {
    (indexToOH(index, chainingSize) & lastReport).orR
  }

  def multiShifter(right: Boolean, multiSize: Int)(data: UInt, shifterSize: UInt): UInt = {
    VecInit(data.asBools.grouped(multiSize).toSeq.transpose.map { dataGroup =>
      if (right) {
        (VecInit(dataGroup).asUInt >> shifterSize).asBools
      } else {
        (VecInit(dataGroup).asUInt << shifterSize).asBools
      }
    }.transpose.map(VecInit(_).asUInt)).asUInt
  }

  def cutUInt(data: UInt, width: Int): Vec[UInt] = {
    require(data.getWidth % width == 0)
    VecInit(Seq.tabulate(data.getWidth / width) { groupIndex =>
      data(groupIndex * width + width - 1, groupIndex * width)
    })
  }

  def calculateSegmentWriteMask(datapath: Int, laneNumber: Int, elementSizeForOneRegister: Int)
                               (seg1H: UInt, mul1H: UInt, lastWriteOH: UInt): UInt = {
    // not access for register -> na
    val notAccessForRegister = Fill(elementSizeForOneRegister, true.B)
    val writeOHGroup = cutUInt(lastWriteOH, elementSizeForOneRegister)
    // writeOHGroup: d7 ## d6 ## d5 ## d4 ## d3 ## d2 ## d1 ## d0
    // seg1: 2 reg group
    //  mul0    na ## na ## na ## na ## na ## na ## d0 ## d0
    //  mul1    na ## na ## na ## na ## d1 ## d0 ## d1 ## d0
    //  mul2    d3 ## d2 ## d1 ## d0 ## d3 ## d2 ## d1 ## d0
    // seg2: 3 reg group
    //  mul0    na ## na ## na ## na ## na ## d0 ## d0 ## d0
    //  mul1    na ## na ## d1 ## d0 ## d1 ## d0 ## d1 ## d0
    // seg3: 4 reg group
    //  mul0    na ## na ## na ## na ## d0 ## d0 ## d0 ## d0
    //  mul1    d1 ## d0 ## d1 ## d0 ## d1 ## d0 ## d1 ## d0
    // seg4: 5 reg group
    //  mul0    na ## na ## na ## d0 ## d0 ## d0 ## d0 ## d0
    // seg5: 6 reg group
    //  mul0    na ## na ## d0 ## d0 ## d0 ## d0 ## d0 ## d0
    // seg6: 7 reg group
    //  mul0    na ## d0 ## d0 ## d0 ## d0 ## d0 ## d0 ## d0
    // seg7: 8 reg group
    //  mul0    d0 ## d0 ## d0 ## d0 ## d0 ## d0 ## d0 ## d0
    val segMask0 = writeOHGroup(0)
    val segMask1 = Mux(
      ((mul1H(2) || mul1H(1)) && seg1H(1)) || (mul1H(1) && (seg1H(2) || seg1H(3))),
      writeOHGroup(1),
      writeOHGroup(0)
    )
    val segMask2: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        mul1H(1) && seg1H(1),
        true.B
      ),
      Seq(
        writeOHGroup(2),
        notAccessForRegister,
        writeOHGroup(0)
      )
    )
    val segMask3: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        mul1H(1) && (seg1H(1) || seg1H(2) || seg1H(3)),
        (mul1H(0) && seg1H(3)) || (seg1H(7) || seg1H(6) || seg1H(5) || seg1H(4)),
        true.B
      ),
      Seq(
        writeOHGroup(3),
        writeOHGroup(1),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    val segMask4: UInt = Mux(
      (mul1H(2) && seg1H(1)) || (mul1H(1) && (seg1H(2) || seg1H(3))) ||
        (seg1H(7) || seg1H(6) || seg1H(5) || seg1H(4)),
      writeOHGroup(0),
      notAccessForRegister
    )
    val segMask5: UInt = PriorityMux(
      Seq(
        (mul1H(2) && seg1H(1)) || (mul1H(1) && (seg1H(2) || seg1H(3))),
        mul1H(0) && (seg1H(7) || seg1H(6) || seg1H(5)),
        true.B
      ),
      Seq(
        writeOHGroup(1),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    val segMask6: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        (mul1H(1) && seg1H(3)) || (mul1H(0) && (seg1H(7) || seg1H(6))),
        true.B
      ),
      Seq(
        writeOHGroup(2),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    val segMask7: UInt = PriorityMux(
      Seq(
        mul1H(2) && seg1H(1),
        mul1H(1) && seg1H(3),
        mul1H(0) && seg1H(7),
        true.B
      ),
      Seq(
        writeOHGroup(3),
        writeOHGroup(1),
        writeOHGroup(0),
        notAccessForRegister
      )
    )
    segMask7 ## segMask6 ## segMask5 ## segMask4 ## segMask3 ## segMask2 ## segMask1 ## segMask0
  }

  def connectWithShifter[T <: Data](latency: Int, id: Option[T => UInt] = None)(source: Valid[T], sink: Valid[T]): Option[UInt] = {
    val tpe = Vec(latency, chiselTypeOf(source))
    val shifterReg: Vec[ValidIO[T]] = RegInit(0.U.asTypeOf(tpe))
    val shifterValid: Bool = (shifterReg.map(_.valid) :+ source.valid).reduce(_ || _)
    when(shifterValid) {
      shifterReg.zipWithIndex.foreach {case (d, i) =>
        i match {
          case 0 => d := source
          case _ => d := shifterReg(i - 1)
        }
      }
    }
    sink := shifterReg.last
    id.map(f => (shifterReg :+ source).map(p => Mux(p.valid, indexToOH(f(p.bits), 4), 0.U)).reduce(_ | _))
  }

  def vfuConnect(parameter: VFUInstantiateParameter)(
    requestVec: Vec[SlotRequestToVFU],
    requestValid: Vec[Bool],
    decodeResult: Seq[DecodeBundle],
    executeEnqueueFire: Vec[Bool],
    responseVec: Vec[ValidIO[VFUResponseToSlot]],
    executeOccupied: Vec[Bool],
    VFUNotClear: Bool
  ): Unit = {

    // 声明 vfu 的入口
    val requestVecFromSlot: Seq[SlotExecuteRequest[SlotRequestToVFU]] = Seq.tabulate(parameter.slotCount) { index =>
      Wire(new SlotExecuteRequest(chiselTypeOf(requestVec.head))(index, parameter))
    }

    // 连接 requestVecFromSlot 根据从lane里来的握手信息
    requestVecFromSlot.zipWithIndex.foreach { case (request, slotIndex) =>
      val requestFire = request.elements.map { case (name: String, reqForVfu: DecoupledIO[SlotRequestToVFU]) =>
        // 检测类型
        val requestParameter: VFUParameter = request.parameterMap(name)
        val typeCheck = decodeResult(slotIndex)(requestParameter.decodeField)
        // 连接 valid
        reqForVfu.valid := requestValid(slotIndex) && typeCheck

        // 连接bits
        reqForVfu.bits := requestVec(slotIndex)

        // 返回 fire
        reqForVfu.fire
      }.reduce(_ || _)
      executeEnqueueFire(slotIndex) := requestFire
    }

    val vrfIsBusy = Wire(Vec(parameter.genVec.size, Bool()))
    // 处理vfu
    val vfuResponse: Seq[ValidIO[VFUResponseToSlot]] = parameter.genVec.zipWithIndex.map { case ((gen, slotVec), vfuIndex) =>
      // vfu 模块
      val vfu: Instance[VFUModule] = gen.instance()
      vfu.suggestName(gen.parameter.decodeField.name)
      // vfu request distributor
      val distributor: Option[Instance[Distributor[SlotRequestToVFU, VFUResponseToSlot]]] = Option.when(gen.parameter.NeedSplit)(
        Instantiate(new Distributor(
          chiselTypeOf(requestVec.head),
          chiselTypeOf(responseVec.head.bits)
        )(gen.parameter.latency > 0))
      )
      distributor.foreach(_.suggestName(s"${gen.parameter.decodeField.name}Distributor"))
      // 访问仲裁
      val requestArbiter: Arbiter[SlotRequestToVFU] = Module(
        new Arbiter(
          chiselTypeOf(requestVecFromSlot(slotVec.head).elements(gen.parameter.decodeField.name).bits),
          slotVec.size
        )
      ).suggestName(s"${gen.parameter.decodeField.name}Arbiter")

      requestArbiter.io.in.zip(slotVec).foreach { case (arbiterInput, slotIndex) =>
        arbiterInput <> requestVecFromSlot(slotIndex).elements(gen.parameter.decodeField.name)
      }
      val vfuInput: DecoupledIO[SlotRequestToVFU] = if (gen.parameter.NeedSplit) {
        distributor.get.requestFromSlot <> requestArbiter.io.out
        distributor.get.requestToVfu
      } else {
        requestArbiter.io.out
      }
      vfu.requestIO.valid := vfuInput.valid
      vfuInput.ready := vfu.requestIO.ready
      vfu.requestIO.bits match {
        case req: Bundle =>
          req.elements.foreach { case (s, d) =>
            d match {
              // src 不等长,所以要特别连
              case src: Vec[Data] =>
                src.zipWithIndex.foreach { case (sd, si) => sd := vfuInput.bits.src(si) }
              case _ => d := vfuInput.bits.elements(s)
            }
          }
      }

      if (vfu.responseIO.bits.elements.contains("busy")) {
        vrfIsBusy(vfuIndex) := vfu.responseIO.bits.elements("busy").asInstanceOf[Bool] // || !vfu.requestIO.ready
      } else {
        vrfIsBusy(vfuIndex) := false.B // !vfu.requestIO.ready
      }

      // 处理 output
      val responseBundle: ValidIO[VFUResponseToSlot] = WireDefault(0.U.asTypeOf(responseVec.head))

      // 暂时不会有 response 握手
      responseBundle.valid := vfu.responseIO.valid
      vfu.responseIO.ready := true.B
      responseBundle.bits.tag := vfuInput.bits.tag

      // 把 vfu的 response 类型转换过来
      responseBundle.bits.elements.foreach { case (name, data) =>
        if (vfu.responseIO.bits.elements.contains(name)) {
          data := vfu.responseIO.bits.elements(name)
        }
      }
      executeOccupied(vfuIndex) := vfu.requestIO.fire
      VFUNotClear := vrfIsBusy.asUInt.orR
      if (gen.parameter.NeedSplit) {
        distributor.get.responseFromVfu := responseBundle
        distributor.get.responseToSlot
      } else {
        responseBundle
      }
    }

    // 把response丢给slot
    responseVec.zipWithIndex.foreach{ case (data, slotIndex) =>
      // 筛选 response
      val responseFilter: Seq[(Bool, ValidIO[VFUResponseToSlot])] = vfuResponse.zip(parameter.genVec).filter(_._2._2.contains(slotIndex)).map {
        case (resp, (gen, _)) =>
          (decodeResult(slotIndex)(gen.parameter.decodeField), resp)
      }
      val selectResponse: ValidIO[VFUResponseToSlot] = Mux1H(
        responseFilter.map(_._1),
        responseFilter.map(_._2),
      )
      data.valid := selectResponse.valid && (selectResponse.bits.tag === slotIndex.U)
      data.bits := selectResponse.bits
    }
  }

  def UIntToOH1(x: UInt, width: Int): UInt = (~((-1).S(width.W).asUInt << x)).asUInt(width-1, 0)
  def numBeats1(maxTransferSize: Int, beatBytes: Int)(size: UInt, hasData: Bool): UInt = {
    val decode = (UIntToOH1(size, log2Ceil(maxTransferSize)) >> log2Ceil(beatBytes)).asUInt
    Mux(hasData, decode, 0.U)
  }

  def firstlastHelper(maxTransferSize: Int, beatBytes: Int)(size: UInt, hasData: Bool, fire: Bool): (Bool, Bool, Bool, UInt) = {
    val beats1   = numBeats1(maxTransferSize, beatBytes)(size, hasData)
    val counter  = RegInit(0.U(log2Up(maxTransferSize / beatBytes).W))
    val counter1 = counter - 1.U
    val first = counter === 0.U
    val last  = counter === 1.U || beats1 === 0.U
    val done  = last && fire
    val count = beats1 & (~counter1).asUInt
    when (fire) {
      counter := Mux(first, beats1, counter1)
    }
    (first, last, done, count)
  }
}
