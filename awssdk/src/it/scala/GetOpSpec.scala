package meteor

import cats.effect.{ContextShift, IO, Resource, Timer}
import meteor.Util.resource
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

class GetOpSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks {
  implicit val timer: Timer[IO] = IO.timer(global)
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  behavior.of("get operation")

  it should "return inserted item using partition key and range key" in forAll {
    test: TestData =>
      val tableName = Table("test_primary_keys")
      val result = for {
        client <- Client.resource[IO]
        _ <- resource[IO, cats.Id, TestData, Unit](
          test,
          t => client.put[TestData](t, tableName).void,
          _ => client.delete(test.id, test.range, tableName)
        )
        get = client.get[TestData, Id, Range](
          test.id,
          test.range,
          tableName,
          consistentRead = false
        )
        r <- Resource.liftF(Util.retryOf[IO, Option[TestData]](
          get,
          1.second,
          10
        )(
          _.isDefined
        ))
      } yield r
      result.use[IO, Option[TestData]](
        r => IO(r)
      ).unsafeRunSync() shouldEqual Some(
        test
      )
  }

  it should "return inserted item without sort key" in forAll {
    test: TestDataSimple =>
      val tableName = Table("test_partition_key_only")
      val result = for {
        client <- Client.resource[IO]
        _ <- resource[IO, cats.Id, TestDataSimple, Unit](
          test,
          t => client.put[TestDataSimple](t, tableName).void,
          _ => client.delete(test.id, EmptySortKey, tableName)
        )
        get = client.get[TestDataSimple, Id, EmptySortKey.type](
          test.id,
          EmptySortKey,
          tableName,
          consistentRead = false
        )
        r <- Resource.liftF(Util.retryOf[IO, Option[TestDataSimple]](
          get,
          1.second,
          10
        )(
          _.isDefined
        ))
      } yield r
      result.use[IO, Option[TestDataSimple]](
        r => IO(r)
      ).unsafeRunSync() shouldEqual Some(
        test
      )
  }

  it should "return None if both keys does not exist" in {
    val tableName = Table("test_primary_keys")
    val result = Client.resource[IO].use { client =>
      client.get[TestData, Id, Range](
        Id("doesnt-exists"),
        Range("doesnt-exists"),
        tableName,
        consistentRead = false
      )
    }.unsafeRunSync()
    result shouldEqual None
  }

  it should "return None if partition key does not exist, range key is not used" in {
    val tableName = Table("test_partition_key_only")
    val result = Client.resource[IO].use { client =>
      client.get[TestData, Id, EmptySortKey.type](
        Id("doesnt-exists"),
        EmptySortKey,
        tableName,
        consistentRead = false
      )
    }.unsafeRunSync()
    result shouldEqual None
  }
}
