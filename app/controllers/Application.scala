package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.Play.current
import scala.concurrent.Future

import reactivemongo.api._

// Reactive Mongo plugin, including the JSON-specialized collection
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection

/*
 * This controller uses case classes and their associated Reads/Writes
 * to read or write JSON structures.
 *
 * Instead of using the default Collection implementation (which interacts with
 * BSON structures + BSONReader/BSONWriter), we use a specialized
 * implementation that works with JsObject + Reads/Writes.
 */
object Application extends Controller with MongoController {
  /*
   * Get a JSONCollection (a Collection implementation that is designed to work
   * with JsObject, Reads and Writes.)
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: JSONCollection = db.collection[JSONCollection]("qualities")

  import play.api.data.Form
  import models._
  import models.JsonFormats._

  def bootstrap = Action {
    val data = io.Source.fromURL(Play.resource("/public/csv/VilleMTP_MTP_QVEnquete_2013_Services.csv").get).getLines

    // get channels from the first row
    // fill empty values with last known from the row
    val channels = data.next().drop(1).split(",").toList.foldLeft(List[String] ())((b,a) =>
      a match {
        case "" => b.head :: b
        case value => value :: b
      }).reverse.toArray

    // get criterias from the second row
    val criterias = data.next().split(",")

    data.drop(2).foreach { line =>
      val splittedLine = line.split(",")
      // guess value type from what is contained within label in the first cell
      val valueType = splittedLine(0).contains("Total") match {
        case true => "Total"
        case false => "Insatisfied"
      }
      // year and domain are the first two values in the first cell
      val year = splittedLine(0).split(" ")(0)
      val domain = splittedLine(0).split(" ")(1)

      // then go on through all non empty values in the row
      for (i <- 1 to splittedLine.length - 1 if !splittedLine(i).isEmpty) {
        // FIXME : try to have channels filled until the end of the row
        //         for now, just take the last value if we are over the last one
        val channel = if (channels.isDefinedAt(i - 1)) channels(i - 1) else channels.last
        val quality = Quality(splittedLine(i).replace("%","").toInt, valueType, channel, criterias(i), year.toInt, Some(domain), None)
        collection.insert(quality)
      }
    }
    Ok
  }

  def findByChannel(channel: String) = Action.async {
    val cursor: Cursor[JsObject] = collection.
      find(Json.obj("channel" -> channel)).
      sort(Json.obj("created" -> -1)).
      cursor[JsObject]

    val futureQualitiesList: Future[List[JsObject]] = cursor.collect[List]()
    val futureQualitiesArray: Future[JsArray] = futureQualitiesList.map { qualities =>
      Json.arr(qualities) 
    }
    futureQualitiesArray.map { quality =>
      Ok(quality)
    }
  }

  def findAll = Action.async {
    val cursor: Cursor[JsObject] = 
      collection.find(Json.obj()).sort(Json.obj("created" -> -1)).cursor[JsObject]
    // gather all the JsObjects in a list
    val futureQualitiesList: Future[List[JsObject]] = cursor.collect[List]()
    // everything's ok! Let's reply with the array
    val futureQualitiesArray: Future[JsArray] = futureQualitiesList.map { qualities =>
      Json.arr(qualities)
    }
    futureQualitiesArray.map { qualities =>
      Ok(qualities)
    }
  }

  def testBarChart = Action { implicit request =>
    Ok(views.html.testbarchart())
  }

  def testBarChartJson = Action.async {
    val cursor: Cursor[JsObject] = collection.
      find(Json.obj("year" -> 2013, "domain" -> "DUOP", "valueType" -> "Insatisfied")).
      sort(Json.obj("channel" -> 1)).
      cursor[JsObject]

    val futureQualitiesList: Future[List[JsObject]] = cursor.collect[List]()
    val futureQualitiesArray: Future[JsArray] = futureQualitiesList.map { qualities =>
      Json.arr(qualities) 
    }
    futureQualitiesArray.map { quality =>
      Ok(quality)
    }
  }

}