package org.scalarelational.instruction

import enumeratum._
import org.scalarelational.op.Condition

case class Join(joinable: Joinable, joinType: JoinType = JoinType.Join, condition: Condition)

// Used for DSL before the actual Join instance is created
case class PartialJoin[E, R](query: Query[E, R], joinable: Joinable, joinType: JoinType) {
  def on(condition: Condition): Query[E, R] = {
    query.copy[E, R](joins = (Join(joinable, joinType, condition) :: query.joins.reverse).reverse)
  }
}

sealed abstract class JoinType extends EnumEntry

object JoinType extends Enum[JoinType] {
  case object Join extends JoinType
  case object Left extends JoinType
  case object LeftOuter extends JoinType
  case object Inner extends JoinType
  case object Outer extends JoinType

  val values = findValues.toVector
}