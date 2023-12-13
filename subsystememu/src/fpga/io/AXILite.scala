package verdes.fpga.io

import chisel3._
import chisel3.util._
case class AXILiteBundleParameters(addrWidth: Int = 32, dataWidth: Int = 32, hasProt: Boolean = false);

abstract class AXILiteBundleBase(val params: AXILiteBundleParameters) extends Bundle
abstract class AXILiteA(params: AXILiteBundleParameters) extends AXILiteBundleBase(params) {
  val addr = UInt(params.addrWidth.W)
  val prot = UInt(if (params.hasProt) 3.W else 0.W)
}

class AXILiteAR(params: AXILiteBundleParameters) extends AXILiteA(params)
class AXILiteAW(params: AXILiteBundleParameters) extends AXILiteA(params)


class AXILiteR(params: AXILiteBundleParameters) extends AXILiteBundleBase(params) {
  val data = UInt(params.dataWidth.W)
  val resp = UInt(2.W)
}

class AXILiteW(params: AXILiteBundleParameters) extends AXILiteBundleBase(params) {
  val data = UInt(params.dataWidth.W)
  val strb = UInt((params.dataWidth/8).W)
}

class AXILiteB(params: AXILiteBundleParameters) extends AXILiteBundleBase(params) {
  val resp = UInt(2.W)
}

class AXILiteBundle(params: AXILiteBundleParameters) extends AXILiteBundleBase(params) {
  val aw = Irrevocable(new AXILiteAW(params))
  val w  = Irrevocable(new AXILiteW(params))
  val b  = Flipped(Irrevocable(new AXILiteB(params)))
  val ar = Irrevocable(new AXILiteAR(params))
  val r  = Flipped(Irrevocable(new AXILiteR(params)))
}