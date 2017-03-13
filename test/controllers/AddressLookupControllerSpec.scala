
package controllers

import com.gu.scalatest.JsoupShouldMatchers
import model.{JourneyData, LookupPage, ProposedAddress}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AddressService, JourneyRepository}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AddressLookupControllerSpec
  extends PlaySpec
    with OneAppPerSuite
    with JsoupShouldMatchers
    with ScalaFutures {

  implicit lazy val materializer = app.materializer

  class Scenario(journeyConfig: Map[String, JourneyData] = Map.empty,
                 var journeyData: Map[String, JourneyData] = Map.empty,
                 proposals: Seq[ProposedAddress] = Seq.empty,
                 id: Option[String] = None) {

    val req = FakeRequest()

    val endpoint = "http://localhost:9000"

    val journeyRepository = new JourneyRepository {

      override def init(journeyName: String): JourneyData = {
        journeyConfig
          .get(journeyName)
          .getOrElse(throw new IllegalArgumentException(s"Invalid journey name: $journeyName"))
      }

      override def get(id: String)(implicit hc: HeaderCarrier): Future[Option[JourneyData]] = {
        Future.successful(journeyData.get(id))
      }

      override def put(id: String, data: JourneyData)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
        journeyData = journeyData ++ Map((id -> data))
        Future.successful(true)
      }
    }

    val addressService = new AddressService {
      override def find(postcode: String, filter: Option[String]) = {
        Future.successful(proposals)
      }
    }

    val controller = new AddressLookupController(journeyRepository, addressService) {
      override val addressLookupEndpoint = endpoint
      override protected def uuid: String = id.getOrElse("random-id")
    }

  }

  "initializing a journey" should {

    "fail given an invalid journey name" in new Scenario {
      val res = call(controller.init("foo"), req)
      status(res) must be (404)
    }

    "return the 'on-ramp' URL given a legit journey name" in new Scenario(
      journeyConfig = Map("foo" -> basicJourney),
      id = Some("bar")
    ) {
      val res = call(controller.init("foo"), req)
      contentAsString(res) must be (s"$endpoint/lookup-address/bar/lookup")
    }

  }

  "no journey" should {

    "return a 'no journey' view" in new Scenario {
      val res = call(controller.noJourney(), req)
      contentAsString(res).asBodyFragment should include element withName("title").withValue("No Journey")
    }

  }

  "lookup" should {

    "return a form which permits input of building name/number and postcode" in new Scenario(
      journeyData = Map("foo" -> basicJourney)
    ) {
      val res = call(controller.lookup("foo"), req)
      val html = contentAsString(res).asBodyFragment
      html should include element withName("title").withValue("Lookup Address")
      html should include element withName("h1").withValue("Your Address")
      html should include element withName("form").withAttrValue("action", routes.AddressLookupController.select("foo").url)
      html should include element withName("label").withAttrValue("for", "filter").withValue("Building name or number")
      html should include element withName("input").withAttrValue("name", "filter")
      html should include element withName("label").withAttrValue("for", "postcode").withValue("Postcode")
      html should include element withName("input").withAttrValue("name", "postcode")
      html should include element withName("button").withAttrValue("type", "submit").withValue("Find my address")
    }

    "allow page title to be configured" in new Scenario(
      journeyData = Map("foo" -> JourneyData("continue", lookupPage = LookupPage(title = Some("Hello!"))))
    ) {
      val res = call(controller.lookup("foo"), req)
      val html = contentAsString(res).asBodyFragment
      html should include element withName("title").withValue("Hello!")
    }

    "allow page heading to be configured" in new Scenario(
      journeyData = Map("foo" -> JourneyData("continue", lookupPage = LookupPage(heading = Some("World!"))))
    ) {
      val res = call(controller.lookup("foo"), req)
      val html = contentAsString(res).asBodyFragment
      html should include element withName("h1").withValue("World!")
    }

    "allow filter label to be configured" in new Scenario(
      journeyData = Map("foo" -> JourneyData("continue", lookupPage = LookupPage(filterLabel = Some("Your digs no."))))
    ) {
      val res = call(controller.lookup("foo"), req)
      val html = contentAsString(res).asBodyFragment
      html should include element withName("label").withAttrValue("for", "filter").withValue("Your digs no.")
    }

    "allow postcode label to be configured" in new Scenario(
      journeyData = Map("foo" -> JourneyData("continue", lookupPage = LookupPage(postcodeLabel = Some("Your PO, bro"))))
    ) {
      val res = call(controller.lookup("foo"), req)
      val html = contentAsString(res).asBodyFragment
      html should include element withName("label").withAttrValue("for", "postcode").withValue("Your PO, bro")
    }

    "allow submit label to be configured" in new Scenario(
      journeyData = Map("foo" -> JourneyData("continue", lookupPage = LookupPage(submitLabel = Some("Make it so"))))
    ) {
      val res = call(controller.lookup("foo"), req)
      val html = contentAsString(res).asBodyFragment
      html should include element withName("button").withAttrValue("type", "submit").withValue("Make it so")
    }

  }

  "select" should {

    "display a list of proposals given postcode and filter parameters" in new Scenario(
      proposals = Seq(ProposedAddress("GB1234567890", "AA1 BB2"))
    ) {
    }

  }

  private def basicJourney: JourneyData = JourneyData("continue")

}