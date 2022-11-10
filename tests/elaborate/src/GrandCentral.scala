package sifive {
  package enterprise {
    package grandcentral {

      import firrtl.annotations._
      import firrtl.RenameMap

      case class ReferenceDataTapKey(source: ReferenceTarget, sink: ReferenceTarget)

      case class DataTapsAnnotation(keys: Seq[ReferenceDataTapKey]) extends NoTargetAnnotation with HasSerializationHints {
        override def serialize: String = super.serialize
        override def update(renames: RenameMap): Seq[DataTapsAnnotation] = Seq(this.copy(keys = keys.flatMap { case ReferenceDataTapKey(source, portName) =>
          (renames.get(source), renames.get(portName)) match {
            // both renamed
            case (Some(Seq(source: ReferenceTarget)), Some(Seq(portName: ReferenceTarget))) =>
              Seq(ReferenceDataTapKey(source, portName))
            // source renamed
            case (None, Some(Seq(portName: ReferenceTarget))) =>
              Seq(ReferenceDataTapKey(source, portName))
            // portName renamed
            case (Some(Seq(source: ReferenceTarget)), None) =>
              Seq(ReferenceDataTapKey(source, portName))
            // both not renamed
            case (None, None) =>
              Seq(ReferenceDataTapKey(source, portName))
            // got multiple renames
            case (_, _) => throw new Exception("WTF, I really don't how to deal with this")
          }
        }))
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
            override def toFirrtl = DataTapsAnnotation(dataTapKeys.map({ case (source, sink) =>
            ReferenceDataTapKey(source.toTarget, sink.toTarget)
          }))
        })
      }
    }
  }

}