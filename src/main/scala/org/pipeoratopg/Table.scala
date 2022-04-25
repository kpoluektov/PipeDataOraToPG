package org.pipeoratopg

import org.pipeoratopg.pg.PGName

case class Table(owner: String, name : String, pgName: PGName){
  override def toString: String = owner + '.' + name
  def toEscapedString: String = s""""$owner"."$name""""
}
object Table {
  def apply(owner: String, name : String) = new Table(owner, name, PGName(name))
}