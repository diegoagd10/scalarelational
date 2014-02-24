package com.outr.query.orm

import org.specs2.mutable._
import com.outr.query.h2.H2Datastore
import com.outr.query.Table
import com.outr.query.column.property._
import scala.Some
import com.outr.query.h2.H2Memory
import org.specs2.main.ArgumentsShortcuts
import com.outr.query.orm.convert._
import java.sql.Blob
import java.io.File
import org.powerscala.IO
import com.outr.query.column.FileBlob
import com.outr.query.table.property.Linking

/**
 * @author Matt Hicks <matt@outr.com>
 */
class ORMSpec extends Specification with ArgumentsShortcuts with ArgumentsArgs {
  addArguments(fullStackTrace)

  import TestDatastore._

  private var bill: Person = _

  "Person" should {
    "create the tables" in {
      create() mustNotEqual null
    }
    "insert 'John Doe' into the table" in {
      val john = Person("John Doe")
      val updated = person.insert(john)
      updated.id mustEqual Some(1)
    }
    "insert 'Jane Doe' into the table" in {
      val jane = Person("Jane Doe")
      val updated = person.insert(jane)
      updated.id mustEqual Some(2)
    }
    "query back all records" in {
      val results = person.query(select(person.*) from person).toList
      results must have size 2
    }
    "query back 'John Doe' with only 'name'" in {
      val results = person.query(select(person.name) from person where person.name === "John Doe").toList
      results must have size 1
      val john = results.head
      john.id mustEqual None
      john.name mustEqual "John Doe"
    }
    "query back 'Jane Doe' with all fields" in {
      val results = person.query(select(person.*) from person where person.name === "Jane Doe").toList
      results must have size 1
      val jane = results.head
      jane.id mustEqual Some(2)
      jane.name mustEqual "Jane Doe"
    }
    "update 'Jane Doe' to 'Janie Doe'" in {
      val jane = person.query(select(person.*) from person where person.name === "Jane Doe").toList.head
      person.update(jane.copy(name = "Janie Doe"))
      val janie = person.query(select(person.*) from person where person.name === "Janie Doe").toList.head
      janie.name mustEqual "Janie Doe"
    }
    "delete 'Janie Doe' from the database" in {
      val janie = person.query(select(person.*) from person where person.name === "Janie Doe").toList.head
      person.delete(janie) must not(throwA[Throwable])
    }
    "insert person into database" in {
      bill = person.insert(Person("Bill Gates"))
      bill.id.get mustNotEqual 0
    }
    "insert company into database" in {
      val microsoft = company.insert(Company("Microsoft", Lazy(bill)))
      microsoft.id.get mustNotEqual 0
      microsoft.owner().id.get mustEqual bill.id.get
    }
    "query back company and lazy load the owner" in {
      val companies = company.query(select(company.*) from company).toList
      companies must have size 1
      val microsoft = companies.head
      microsoft.name mustEqual "Microsoft"
      microsoft.owner.loaded mustEqual false
      val bill = microsoft.owner()
      bill.name mustEqual "Bill Gates"
      microsoft.owner.loaded mustEqual true
    }
    "insert company into database with a new owner" in {
      val steve = Person("Steve Jobs")
      val apple = company.insert(Company("Apple", Lazy(steve)))
      apple.id.get mustNotEqual 0
      apple.owner().id.get mustNotEqual 0
    }
    "query new person back out of database" in {
      val people = person.query(select(person.*) from person where person.name === "Steve Jobs").toList
      people must have size 1
      val steve = people.head
      steve.name mustEqual "Steve Jobs"
    }
    "access company via LazyList in person" in {
      val people = person.query(select(person.*) from person where person.name === "Steve Jobs").toList
      people must have size 1
      val steve = people.head
      val companies = steve.companies()
      companies must have size 1
      val apple = companies.head
      apple.name mustEqual "Apple"
    }
    "insert some corporate domains" in {
      val apple = company.query(company.q where company.name === "Apple").toList.head
      apple.name mustEqual "Apple"
      val appleDomain = domain.insert(CorporateDomain("apple.com", apple))
      appleDomain.id mustNotEqual None
    }
    "query back the corporate domain with the company" in {
      val appleDomain = domain.query(domain.q).toList.head
      appleDomain.url mustEqual "apple.com"
      appleDomain.id.get must be > 0
      val apple = appleDomain.company
      apple mustNotEqual null
      apple.name mustEqual "Apple"
      apple.id.get must be > 0
    }
    "query partial and only persist partial" in {
      val steve = person.query(select(person.id, person.date) from person where person.name === "Steve Jobs").toList.head
      val newDate = System.currentTimeMillis()
      person.persist(steve.copy(date = newDate))
      val updated = person.query(person.q where person.name === "Steve Jobs").toList.head
      updated.name mustEqual "Steve Jobs"
      updated.date mustEqual newDate
    }
  }
  "SimpleInstance" should {
    "insert a single record without a 'modified' value and get a 'modified' value back" in {
      val original = SimpleInstance("test")
      original.modified mustEqual 0L
      val current = System.currentTimeMillis()
      val record = simple.persist(original)
      record.modified must beGreaterThanOrEqualTo(current)
    }
    "query the record back out with a modified value" in {
      val record = simple.query(simple.q).toList.head
      record.modified mustNotEqual 0L
    }
  }
  "Orders and Items" should {
    "insert a few items and create some orders" in {
      val elmo = item.insert(Item("Tickle Me Elmo"))
      val district9 = item.insert(Item("District 9 DVD"))
      val batarang = item.insert(Item("Batarang"))

      elmo.id mustNotEqual None
      district9.id mustNotEqual None
      batarang.id mustNotEqual None

      order.insert(Order(LazyList(elmo))) mustNotEqual null
      order.insert(Order(LazyList(district9, batarang))) mustNotEqual null
      order.insert(Order(LazyList(district9, batarang, elmo))) mustNotEqual null
    }
    "simple query items are correct" in {
      val results = exec(item.q).toList
      results must have size 3
    }
    "simple query orders are correct" in {
      val results = exec(order.q).toList
      results must have size 3
    }
    "simple query orderItem entries are correct" in {
      val results = exec(select(orderItem.*) from orderItem).toList
      results must have size 6
    }
    "query back the orders and verify the correct data" in {
      val orders = order.query(order.q).toList
      orders must have size 3
      val o1 = orders.head
      o1.items().length mustEqual 1
      o1.items().head.name mustEqual "Tickle Me Elmo"
      val o2 = orders.tail.head
      o2.items().length mustEqual 2
      o2.items().head.name mustEqual "District 9 DVD"
      o2.items().tail.head.name mustEqual "Batarang"
      val o3 = orders.tail.tail.head
      o3.items().length mustEqual 3
      o3.items().head.name mustEqual "District 9 DVD"
      o3.items().tail.head.name mustEqual "Batarang"
      o3.items().tail.tail.head.name mustEqual "Tickle Me Elmo"
    }
  }
  "Countries" should {
    val PopulationIn2011 = 311600000
    val PopulationIn2012 = 313900000

    "insert one country" in {
      country.merge(Country("USA", PopulationIn2011)) must not(throwA[Throwable])
    }
    "query the country back" in {
      val results = country.query(country.q).toList
      results must have size 1
      val usa = results.head
      usa.name mustEqual "USA"
      usa.population mustEqual PopulationIn2011
    }
    "merge a population change" in {
      country.merge(Country("USA", PopulationIn2012)) must not(throwA[Throwable])
    }
    "query only one country back" in {
      val results = country.query(country.q).toList
      results must have size 1
      val usa = results.head
      usa.name mustEqual "USA"
      usa.population mustEqual PopulationIn2012
    }
  }
  "Content" should {
    val testContent = "Testing content"

    "insert content and data" in {
      val file = File.createTempFile("tmp", ".txt")
      IO.copy(testContent, file)
      val data = contentData.persist(ContentData(new FileBlob(file)))
      data.id mustNotEqual None
      val c = content.persist(Content("Test", Transient(data)))
      c.id mustNotEqual None
    }
    "query back content and access data" in {
      val results = content.query(content.q).toList
      results must have size 1
      val c = results.head
      c.name mustEqual "Test"
      c.data.use {
        case Some(data) => {
          val s = new String(data.content.getBytes(0, data.content.length().toInt), "UTF-8")
          s mustEqual testContent
        }
        case None => throw new RuntimeException("No result found!")
      }
    }
    "remove data" in {
      val results = content.query(content.q).toList
      results must have size 1
      val c = results.head
      c.name mustEqual "Test"
      val updated = content.persist(c.copy(name = "new name", data = Transient.None))
      updated.data mustEqual Transient.None
    }
    "set data back" in {
      val results = content.query(content.q).toList
      results must have size 1
      val c = results.head
      c.name mustEqual "new name"
      c.data.use {
        case Some(data) => {
          val s = new String(data.content.getBytes(0, data.content.length().toInt), "UTF-8")
          s mustEqual testContent
        }
        case None => throw new RuntimeException("No result found!")
      }
    }
  }
  "User" should {
    "insert an Administrator" in {
      user.persist(Administrator("Super User")) mustNotEqual null
    }
    "query back the Administrator by name" in {
      val results = user.query(user.q where user.name === "Super User").toList
      results must have length 1
      val result = results.head
      result.getClass mustEqual classOf[Administrator]
      result.name mustEqual "Super User"
    }
    "insert a Developer" in {
      user.persist(Developer("Superman", "Scala")) mustNotEqual null
    }
    "query back the Developer by name" in {
      val results = user.query(user.q where user.name === "Superman").toList
      results must have length 1
      val result = results.head
      result.getClass mustEqual classOf[Developer]
      result.name mustEqual "Superman"
      result.asInstanceOf[Developer].language mustEqual "Scala"
    }
    "query back all users" in {
      val results = user.query(user.q).toList
      results must have length 2
      val admin = results.collectFirst {
        case u: Administrator => u
      }.get
      admin.name mustEqual "Super User"
      val dev = results.collectFirst {
        case u: Developer => u
      }.get
      dev.name mustEqual "Superman"
      dev.language mustEqual "Scala"
    }
  }
}

object TestDatastore extends H2Datastore(mode = H2Memory("test")) {
  def person = Person
  def company = Company
  def domain = CorporateDomain
  def simple = SimpleInstance
  def order = Order
  def item = Item
  def orderItem = OrderItem
  def country = Country
  def contentData = ContentData
  def content = Content
  def user = User

  LazyList.connect[Person, Company, Int](person, "companies", company.ownerId)
  LazyList.connect[Order, Item, Int](order, "items", orderItem.itemId, item, "orders", orderItem.orderId)
}

case class Person(name: String, date: Long = System.currentTimeMillis(), companies: LazyList[Company] = LazyList.Empty, id: Option[Int] = None)

object Person extends ORMTable[Person](TestDatastore) {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val name = orm[String]("name", Unique)
  val date = orm[Long]("date")
}

case class Company(name: String, owner: Lazy[Person] = Lazy.None, id: Option[Int] = None)

object Company extends ORMTable[Company](TestDatastore) {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val name = orm[String]("name", Unique)
  val ownerId = orm[Int, Lazy[Person]]("ownerId", "owner", new LazyConverter[Person], new ForeignKey(Person.id))
}

case class CorporateDomain(url: String, company: Company, id: Option[Int] = None)

object CorporateDomain extends ORMTable[CorporateDomain](TestDatastore) {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val url = orm[String]("url", Unique)
  val companyId = orm[Int, Company]("companyId", "company", new ObjectConverter[Company], new ForeignKey(Company.id))
}

case class SimpleInstance(name: String, modified: Long = 0L, id: Option[Int] = None)

object SimpleInstance extends ORMTable[SimpleInstance](TestDatastore) with ModifiedSupport[SimpleInstance] {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val name = orm[String]("name")
  val modified = orm[Long]("modified", NotNull)
}

case class Order(items: LazyList[Item] = LazyList.Empty, date: Long = System.currentTimeMillis(), id: Option[Int] = None)

object Order extends ORMTable[Order](TestDatastore, "orders") {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val date = orm[Long]("date", NotNull)
}

case class Item(name: String, orders: LazyList[Order] = LazyList.Empty, id: Option[Int] = None)

object Item extends ORMTable[Item](TestDatastore) {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val name = orm[String]("name", NotNull)
}

object OrderItem extends Table(TestDatastore, Linking) {
  val id = column[Int]("id", PrimaryKey, AutoIncrement)
  val orderId = column[Int]("orderId", new ForeignKey(Order.id))
  val itemId = column[Int]("itemId", new ForeignKey(Item.id))
}

case class Country(name: String, population: Int)

object Country extends ORMTable[Country](TestDatastore) {
  val name = orm[String]("name", Unique, NotNull, PrimaryKey)
  val population = orm[Int]("population", NotNull)
}

case class ContentData(content: Blob, id: Option[Int] = None)

object ContentData extends ORMTable[ContentData](TestDatastore) {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val content = orm[Blob]("content", NotNull)
}

case class Content(name: String, data: Transient[ContentData] = Transient.None, id: Option[Int] = None)

object Content extends ORMTable[Content](TestDatastore) {
  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val name = orm[String]("name", NotNull)
  val data = orm[Int, Transient[ContentData]]("dataId", "data", new TransientConverter[ContentData], new ForeignKey(ContentData.id))
}

trait User {
  def id: Option[Int]
  def name: String
}

object User extends PolymorphicORMTable[User](TestDatastore) {
  val Administrator = 1
  val Developer = 2
  val Employee = 3

  val id = orm[Int, Option[Int]]("id", PrimaryKey, AutoIncrement)
  val name = orm[String]("name", NotNull)
  val language = orm[String]("language", MappingOptional)
  val userType = column[Int]("userType", NotNull)

  override val caseClasses = Vector(classOf[Administrator], classOf[Developer], classOf[Employee])
  def typeColumn = userType
}

case class Administrator(name: String, id: Option[Int] = None) extends User

case class Developer(name: String, language: String, id: Option[Int] = None) extends User

case class Employee(name: String, id: Option[Int] = None) extends User