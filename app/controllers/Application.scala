package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import scala.concurrent.Future

// Reactive Mongo imports
import reactivemongo.api._

// Reactive Mongo plugin, including the JSON-specialized collection
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection

/*
 * Example using ReactiveMongo + Play JSON library.
 *
 * There are two approaches demonstrated in this controller:
 * - using JsObjects directly
 * - using case classes that can be turned into Json using Reads and Writes.
 *
 * This controller uses case classes and their associated Reads/Writes
 * to read or write JSON structures.
 *
 * Instead of using the default Collection implementation (which interacts with
 * BSON structures + BSONReader/BSONWriter), we use a specialized
 * implementation that works with JsObject + Reads/Writes.
 *
 * Of course, you can still use the default Collection implementation
 * (BSONCollection.) See ReactiveMongo examples to learn how to use it.
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

  // ------------------------------------------ //
  // Using case classes + Json Writes and Reads //
  // ------------------------------------------ //
  import play.api.data.Form
  import models._
  import models.JsonFormats._

  def create = Action.async {
    val quality = Quality(10, 100, "Guichet", "Rapidité", None, Some("HdV"))
    val futureResult = collection.insert(quality)
    // when the insert is performed, send a OK 200 result
    futureResult.map(_ => Ok)
  }

  def createFromJson = Action.async(parse.json) { request =>
    /*
     * request.body is a JsValue.
     * There is an implicit Writes that turns this JsValue as a JsObject,
     * so you can call insert() with this JsValue.
     * (insert() takes a JsObject as parameter, or anything that can be
     * turned into a JsObject using a Writes.)
     */
    request.body.validate[Quality].map { user =>
      // `quality` is an instance of the case class `models.Quality`
      collection.insert(user).map { lastError =>
        Logger.debug(s"Successfully inserted with LastError: $lastError")
        Created
      }
    }.getOrElse(Future.successful(BadRequest("invalid json")))
  }

  def findByChannel(channel: String) = Action.async {
    // let's do our query
    val cursor: Cursor[Quality] = collection.
      // find all people with name `name`
      find(Json.obj("channel" -> channel)).
      // sort them by creation date
      sort(Json.obj("created" -> -1)).
      // perform the query and get a cursor of JsObject
      cursor[Quality]

    // gather all the JsObjects in a list
    val futureQualitiesList: Future[List[Quality]] = cursor.collect[List]()

    // everything's ok! Let's reply with the array
    futureQualitiesList.map { qualities =>
      Ok(qualities.toString)
    }
  }

  def findAll = Action.async {
    // let's do our query
    val cursor: Cursor[Quality] = collection.
      // find all people with name `name`
      find(Json.obj()).
      // sort them by creation date
      sort(Json.obj("created" -> -1)).
      // perform the query and get a cursor of JsObject
      cursor[Quality]

    // gather all the JsObjects in a list
    val futureQualitiesList: Future[List[Quality]] = cursor.collect[List]()

    // everything's ok! Let's reply with the array
    futureQualitiesList.map { qualities =>
      Ok(qualities.toString)
    }
  }
}