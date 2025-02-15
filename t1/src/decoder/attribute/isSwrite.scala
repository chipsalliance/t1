// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package org.chipsalliance.t1.rtl.decoder.attribute

import org.chipsalliance.t1.rtl.decoder.T1DecodePattern

object isSwrite {
  def apply(t1DecodePattern: T1DecodePattern): isSwrite =
    Seq(
      y _  -> Y,
      n _  -> N,
      dc _ -> DC
    ).collectFirst {
      case (fn, tri) if fn(t1DecodePattern) => isSwrite(tri)
    }.get

  def y(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = Seq(
      "vcpop.m",
      "vfirst.m",
      "vfmv.f.s",
      "vl1re16.v",
      "vl1re32.v",
      "vl1re64.v",
      "vl1re8.v",
      "vl2re16.v",
      "vl2re32.v",
      "vl2re64.v",
      "vl2re8.v",
      "vl4re16.v",
      "vl4re32.v",
      "vl4re64.v",
      "vl4re8.v",
      "vl8re16.v",
      "vl8re32.v",
      "vl8re64.v",
      "vl8re8.v",
      "vle1024.v",
      "vle1024ff.v",
      "vle128.v",
      "vle128ff.v",
      "vle16.v",
      "vle16ff.v",
      "vle256.v",
      "vle256ff.v",
      "vle32.v",
      "vle32ff.v",
      "vle512.v",
      "vle512ff.v",
      "vle64.v",
      "vle64ff.v",
      "vle8.v",
      "vle8ff.v",
      "vlm.v",
      "vloxei1024.v",
      "vloxei128.v",
      "vloxei16.v",
      "vloxei256.v",
      "vloxei32.v",
      "vloxei512.v",
      "vloxei64.v",
      "vloxei8.v",
      "vlse1024.v",
      "vlse128.v",
      "vlse16.v",
      "vlse256.v",
      "vlse32.v",
      "vlse512.v",
      "vlse64.v",
      "vlse8.v",
      "vluxei1024.v",
      "vluxei128.v",
      "vluxei16.v",
      "vluxei256.v",
      "vluxei32.v",
      "vluxei512.v",
      "vluxei64.v",
      "vluxei8.v",
      "vmv.x.s",
      "vs1r.v",
      "vs2r.v",
      "vs4r.v",
      "vs8r.v",
      "vse1024.v",
      "vse128.v",
      "vse16.v",
      "vse256.v",
      "vse32.v",
      "vse512.v",
      "vse64.v",
      "vse8.v",
      "vsm.v",
      "vsoxei1024.v",
      "vsoxei128.v",
      "vsoxei16.v",
      "vsoxei256.v",
      "vsoxei32.v",
      "vsoxei512.v",
      "vsoxei64.v",
      "vsoxei8.v",
      "vsse1024.v",
      "vsse128.v",
      "vsse16.v",
      "vsse256.v",
      "vsse32.v",
      "vsse512.v",
      "vsse64.v",
      "vsse8.v",
      "vsuxei1024.v",
      "vsuxei128.v",
      "vsuxei16.v",
      "vsuxei256.v",
      "vsuxei32.v",
      "vsuxei512.v",
      "vsuxei64.v",
      "vsuxei8.v",
      "vwadd.vv",
      "vwadd.vx",
      "vwadd.wv",
      "vwadd.wx",
      "vwaddu.vv",
      "vwaddu.vx",
      "vwaddu.wv",
      "vwaddu.wx",
      "vwmacc.vv",
      "vwmacc.vx",
      "vwmaccsu.vv",
      "vwmaccsu.vx",
      "vwmaccu.vv",
      "vwmaccu.vx",
      "vwmaccus.vx",
      "vwmul.vv",
      "vwmul.vx",
      "vwmulsu.vv",
      "vwmulsu.vx",
      "vwmulu.vv",
      "vwmulu.vx",
      "vwredsum.vs",
      "vwredsumu.vs",
      "vwsub.vv",
      "vwsub.vx",
      "vwsub.wv",
      "vwsub.wx",
      "vwsubu.vv",
      "vwsubu.vx",
      "vwsubu.wv",
      "vwsubu.wx",
      // rv_zvbb
      "vwsll.vv",
      "vwsll.vx",
      "vwsll.vi"
    )
    allMatched.contains(t1DecodePattern.instruction.name)
  }
  def n(t1DecodePattern: T1DecodePattern): Boolean = {
    val allMatched = t1DecodePattern.param.allInstructions.filter(i => !(y(t1DecodePattern) || dc(t1DecodePattern)))
    allMatched.contains(t1DecodePattern.instruction)
  }

  def dc(t1DecodePattern: T1DecodePattern): Boolean = false
}

case class isSwrite(value: TriState) extends BooleanDecodeAttribute {
  override val description: String =
    "sWrite -> targetRd || readOnly || crossWrite || maskDestination || reduce || loadStore instruction will write vd or rd(scalar) from outside of lane. It will request vrf wait, and lane will not write. No write to vd when isSwrite is True!!!"
}
