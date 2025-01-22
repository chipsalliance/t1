package org.chipsalliance.rvdecoderdb.parser
import org.chipsalliance.rvdecoderdb.parser.ArgLUT

object SameValue {
  def unapply(str: String): Option[ArgLUT] = str match {
    case s"${attr1}=${attr2}" => ArgLUT.all.get(attr1)
    case _                    => None
  }
}

class SameValue(val attr1: String, val attr2: String) extends Token {
  override def toString: String = s"$attr1=$attr2"
}
