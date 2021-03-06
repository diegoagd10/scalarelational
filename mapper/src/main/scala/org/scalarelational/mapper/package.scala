package org.scalarelational

import java.sql.ResultSet

import org.scalarelational.compiletime.QueryMacros
import org.scalarelational.instruction.{Query, ResultConverter}
import org.scalarelational.result.QueryResult
import org.scalarelational.table.Table

import scala.language.experimental.macros

package object mapper {
  implicit class MapperQuery[Expressions, Result](query: Query[Expressions, Result]) {
    def to[R](table: Table): Query[Expressions, R] =
      macro QueryMacros.to1[Expressions, R]

    def to[R1, R2](table1: Table, table2: Table): Query[Expressions, (R1, R2)] =
      macro QueryMacros.to2[Expressions, R1, R2]

    def to[R1, R2, R3](table1: Table, table2: Table, table3: Table): Query[Expressions, (R1, R2, R3)] =
      macro QueryMacros.to3[Expressions, R1, R2, R3]

    def poly[R](result2Converter: QueryResult => ResultConverter[_ <: R]): Query[Expressions, R] = {
      val polyConverter = new ResultConverter[R] {
        override def apply(result: QueryResult): R = result2Converter(result).apply(result)
      }
      query.convert(polyConverter)
    }

    def loose[R]: Query[Expressions, R] =
      macro QueryMacros.loose[Expressions, R]
  }

  def converter[R](table: Table): ResultConverter[R] = macro QueryMacros.converter1[R]

  def rsConverter[R]: ResultSet => R = macro QueryMacros.resultSetToR[R]
}