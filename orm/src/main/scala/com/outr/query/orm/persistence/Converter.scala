package com.outr.query.orm.persistence

import com.outr.query.QueryResult

/**
 * @author Matt Hicks <matt@outr.com>
 */
trait Converter {
  def convert2SQL(persistence: Persistence, value: Any): Conversion

  def convert2Value(persistence: Persistence, sql: Any, args: Map[String, Any], result: QueryResult): Option[Any]
}

sealed trait Conversion

case class ConversionResponse(value: Any, updated: Option[Any]) extends Conversion

object EmptyConversion extends Conversion