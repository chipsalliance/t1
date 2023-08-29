// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2022 Jiuyang Liu <liu@jiuyang.me>

package sifive {
  package enterprise {
    package grandcentral {

      import firrtl.annotations._

      case class ReferenceDataTapKey(source: ReferenceTarget, sink: ReferenceTarget)

      case class DataTapsAnnotation(keys: Seq[ReferenceDataTapKey])
          extends NoTargetAnnotation
          with HasSerializationHints {
        override def typeHints: Seq[Class[_]] = Seq(classOf[ReferenceDataTapKey])
      }
    }

  }

}

package tests {
  package elaborate {

    import chisel3._
    import chisel3.experimental.ChiselAnnotation
    import sifive.enterprise.grandcentral._
    trait TapModule extends RawModule { t =>
      private val dataTapKeys = scala.collection.mutable.ArrayBuffer[(Data, Data)]()
      def tap[T <: Data](source: T): T = {
        val sink = Wire(chiselTypeOf(source))
        dontTouch(sink)
        dataTapKeys.append((source, sink))
        sink
      }
      // wait for https://github.com/chipsalliance/chisel3/pull/1943
      def done(): Unit = {
        chisel3.experimental.annotate(new ChiselAnnotation {
          override def toFirrtl = DataTapsAnnotation(dataTapKeys.toSeq.map({
            case (source, sink) =>
              ReferenceDataTapKey(source.toTarget, sink.toTarget)
          }))
        })
      }
    }
  }

}
