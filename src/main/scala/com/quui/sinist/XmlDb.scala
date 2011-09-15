/**
 * Copyright (c) 2010-2011 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 */
package com.quui.sinist

import java.net.URL
import org.xmldb.api.base.ErrorCodes
import scala.xml.Elem
import scala.xml.XML
import java.io.File
import org.xmldb.api.base.XMLDBException
import org.xmldb.api.modules.BinaryResource
import org.xmldb.api.modules.XMLResource
import org.xmldb.api.modules.CollectionManagementService
import org.xmldb.api.DatabaseManager
import org.xmldb.api.base.Collection
import org.exist.xmldb.DatabaseImpl
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods._
import org.apache.commons.httpclient._

/**
 * Simple wrapper to the eXist XML DB using RPC and REST.
 * @author Fabian Steeg (fsteeg)
 */
object XmlDb {
  object Format extends Enumeration {
    type Format = Value
    val XML = Value(classOf[XMLResource].getSimpleName)
    val BIN = Value(classOf[BinaryResource].getSimpleName)
  }
}

case class XmlDb(
  server: String = "localhost",
  port: Int = 8080,
  user: String = "guest",
  pass: String = "guest") {

  val rpcRoot = insert("xmldb:exist://%s:%s/exist/xmlrpc/db/")
  val restRoot = insert("http://%s:%s/exist/rest/db/")

  private def insert(s: String) = s.format(server, port)

  DatabaseManager.registerDatabase(new DatabaseImpl()) // XML:DB implementation

  /**
   * @param file The file to store in the DB
   * @param kind The file format, a value of [[com.quui.sinist.XmlDb.Format]]
   * @param coll The collection to add the file to (uses the file's parent name if none is given)
   * @param id The id to use for the stored file (uses the file name if none is given)
   */
  def put(file: File, kind: XmlDb.Format.Value, coll:String = "", id:String = ""): Unit = {
    put(file, if(coll == "") new File(file.getParent).getName else coll, if(id == "") file.getName else id, kind)
  }

  /**
   * @param xml The XML element to store in the DB
   * @param coll The collection to add the element to
   * @param id The id to use for the stored element
   */
  def putXml(xml: Elem, coll: String, id: String): Unit = put(xml.toString, coll, id, XmlDb.Format.XML)

  /**
   * @param bin The binary data to store in the DB
   * @param coll The collection to add the data to
   * @param id The id to use for the stored data
   */
  def putBin(bin: Array[Byte], coll: String, id: String): Unit = put(bin, coll, id, XmlDb.Format.BIN)

  /**
   * @param coll The collection to get XML elements from
   * @param ids The ids of the elements to get (pass none to get all elements in the collection)
   * @return Some list of the retrieved XML elements, or none 
   */
  def getXml(coll: String, ids: String*): Option[List[Elem]] = collection(coll) match {
    case None => None
    case Some(c) => {
      val entryIds = if (ids.size > 0) ids else getIds(coll).get
      val entries = for (id <- entryIds; obj = c.getResource(id).getContent; if obj.isInstanceOf[String])
        yield XML.loadString(obj.asInstanceOf[String])
      Some(entries.toList)
    }
  }

  /**
   * @param coll The collection to get binary elements from
   * @param ids The ids of the elements to get (pass none to get all elements in the collection)
   * @return Some list of the retrieved binary data, or none 
   */
  def getBin(coll: String, ids: String*): Option[List[Array[Byte]]] = collection(coll) match {
    case None => None
    case Some(c) => {
      val entryIds = if (ids.size > 0) ids else getIds(coll).get
      val entries = for (id <- entryIds; obj = c.getResource(id).getContent; if obj.isInstanceOf[Array[Byte]])
        yield obj.asInstanceOf[Array[Byte]]
      Some(entries.toList)
    }
  }

  /**
   * @param coll The collection to get available ids for
   * @return Some list of ids in the collection, or none
   */
  def getIds(coll: String): Option[List[String]] = collection(coll) match {
    case None => None
    case Some(c) => Some(List() ++ c.listResources)
  }

  /**
   * @return True, if this DB is available for storing and retrieving data
   */
  def isAvailable: Boolean =
    try {
      DatabaseManager.getCollection(rpcRoot).listResources; true
    } catch {
      case _ => false
    }

  /**
   * @param coll The Lucene-indexed collection to query
   * @param query The query, in existdb XML query syntax
   * @return The DB response to the query
   */
  def query(coll: String, query: Elem): Elem = {
    post(restRoot + coll, query.toString)
  }

  /**
   * @param coll The Lucene-indexed collection to query
   * @param query The query, in XQuery syntax
   * @return The DB response to the query
   */
  def query(coll: String, query: String): Elem = {
    val cdata = "<![CDATA[%s]]>".format(query)
    val full =
      <query xmlns="http://exist.sourceforge.net/NS/exist">
        <text>
          { scala.xml.Unparsed(cdata) }
        </text>
        <properties>
          <property name="indent" value="yes"/>
        </properties>
      </query>
    this.query(coll, full)
  }  
    
  override def toString = "%s@%s".format(getClass.getSimpleName, rpcRoot)

  private def put(content: Object, collectionId: String, id: String, kind: XmlDb.Format.Value): Unit = {
    val collectionName = collectionId
    var collection = DatabaseManager.getCollection(rpcRoot + collectionName, user, pass)
    if (collection == null) collection = createCollection(collectionName)
    val resource = collection.createResource(id, kind.toString)
    resource.setContent(content)
    collection.storeResource(resource)
  }

  private def collection(name: String): Option[Collection] = {
    val collection = DatabaseManager.getCollection(rpcRoot + name, user, pass)
    if (collection == null) None else Some(collection)
  }

  private def createCollection(name: String): Collection = {
    try {
      DatabaseManager.getCollection(rpcRoot, user, pass)
        .getService(classOf[CollectionManagementService].getSimpleName, "1.0")
        .asInstanceOf[CollectionManagementService]
        .createCollection(name)
    } catch {
      case x: XMLDBException => throw new IllegalStateException(
        "Could not create collection in DB at %s (%s)".format(rpcRoot, x.getMessage), x)
    }
  }

  private val Http = new HttpClient

  private[sinist] def post(url: String, body: String) = {
    val method = new PostMethod(url)
    method.setRequestEntity(new StringRequestEntity(body, "text/xml", "utf-8"))
    execute(method)
  }

  private[sinist] def get(url: String) = execute(new GetMethod(url))

  private def execute(m: HttpMethodBase) = {
    try { Http.executeMethod(m) } catch { case e => e.printStackTrace() }
    XML.loadString(new String(m.getResponseBody))
  }

}