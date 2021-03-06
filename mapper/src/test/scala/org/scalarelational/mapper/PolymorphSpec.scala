package org.scalarelational.mapper

import java.sql.Types

import org.scalarelational.SelectExpression
import org.scalarelational.column.ColumnLike
import org.scalarelational.column.property.{AutoIncrement, Polymorphic, PrimaryKey}
import org.scalarelational.datatype.{DataType, SQLConversion, SQLType}
import org.scalarelational.h2.{H2Datastore, H2Memory}
import org.scalarelational.instruction.Query
import org.scalatest.{Matchers, WordSpec}

class PolymorphSpec extends WordSpec with Matchers {
  import PolymorphDatastore._

  val insertUsers = Seq(
    UserGuest("guest"),
    UserAdmin("admin", canDelete = true)
  )

  val insertContent = Seq(
    ContentString("hello"),
    ContentList(List("a", "b", "c"))
  )

  "Users" should {
    "create tables" in {
      withSession { implicit session =>
        create(users)
      }
    }
    "insert users" in {
      withSession { implicit session =>
        insertUsers.zipWithIndex.foreach {
          case (usr, index) => {
            val result = usr.insert.result
            result.id should equal (index + 1)
          }
        }
      }
    }
    "query users" in {
      withSession { implicit session =>
        val query = users.query
        insertUsers should equal (query.converted.toList.map(_.withoutId))
      }
    }
  }

  "Content" should {
    "create tables" in {
      withSession { implicit session =>
        create(content)
      }
    }
    "insert content" in {
      withSession { implicit session =>
        insertContent.zipWithIndex.foreach {
          case (c, index) => {
            val result = c.insert.result
            result.id should equal (index + 1)
          }
        }
      }
    }
    "query content" in {
      withSession { implicit session =>
        val query = content.query
        insertContent should equal (query.converted.toList.map(_.withoutId))
      }
    }
  }
}

trait User extends BaseEntity[User] {
  def name: String
  def id: Option[Int]
  def withoutId: User
}

case class UserGuest(name: String, id: Option[Int] = None)
  extends User with Entity[UserGuest] {
  def columns = mapTo[UserGuest](PolymorphDatastore.users)

  val isGuest = true
  def withoutId = copy(id = None)
}

case class UserAdmin(name: String, canDelete: Boolean, id: Option[Int] = None)
  extends User with Entity[UserAdmin] {
  def columns = mapTo[UserAdmin](PolymorphDatastore.users)

  val isGuest = false
  def withoutId = copy(id = None)
}

// ---

trait Content extends BaseEntity[Content] {
  def id: Option[Int]
  def withoutId: Content
}

case class ContentString(string: String, id: Option[Int] = None)
  extends Content with Entity[ContentString] {
  def columns = mapTo[ContentString](PolymorphDatastore.content)

  val isString = true
  def withoutId = copy(id = None)
}

case class ContentList(entries: List[String], id: Option[Int] = None)
  extends Content with Entity[ContentList] {
  def columns = mapTo[ContentList](PolymorphDatastore.content)

  val isString = false
  def withoutId = copy(id = None)
}

// ---

object PolymorphDatastore extends H2Datastore(mode = H2Memory("polymorph_test")) {
  object users extends MappedTable[User]("users") {
    val id = column[Option[Int], Int]("id", PrimaryKey, AutoIncrement)
    val name = column[String]("name")
    val canDelete = column[Boolean]("canDelete", Polymorphic)
    val isGuest = column[Boolean]("isGuest")

    override def query: Query[scala.Vector[SelectExpression[_]], User] = {
      q.poly[User](qr => if (qr(users.isGuest)) converter[UserGuest](users) else converter[UserAdmin](users))
    }
  }

  object content extends MappedTable[Content]("content") {
    object ListConverter extends SQLConversion[List[String], String] {
      override def toSQL(value: List[String]): String = value.mkString("|")
      override def fromSQL(value: String): List[String] = value.split('|').toList
    }
    implicit def listDataType: DataType[List[String], String] = new DataType[List[String], String](
      Types.VARCHAR,
      SQLType("VARCHAR(1024)"),
      ListConverter
    )

    val id = column[Option[Int], Int]("id", PrimaryKey, AutoIncrement)
    val string = column[String]("string", Polymorphic)
    val entries = column[List[String], String]("entries", Polymorphic)
    val isString = column[Boolean]("isString")

    override def query: Query[scala.Vector[SelectExpression[_]], Content] = {
      q.poly[Content](qr => if (qr(content.isString)) converter[ContentString](content) else converter[ContentList](content))
    }
  }
}
