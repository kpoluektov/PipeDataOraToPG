package org.pipeoratopg.pg

import scala.util.matching.Regex

case class PGName(sourceNam: String, private var targetName: String) {
  val pgNamePattern: Regex = "^[a-zA-Z0-9_]+$".r
  targetName = pgNamePattern.findFirstIn(sourceNam) match {
    case None => '"' + sourceNam + '"'
    case Some(_) => sourceNam
  }
  def get: String = targetName
}

object PGName{
  def apply(name: String): PGName = PGName(name, null)
}
