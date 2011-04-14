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

  def put(file: File, kind: XmlDb.Format.Value, collection:String = "", id:String = ""): Unit = {
    put(file, if(collection == "") new File(file.getParent).getName else collection, if(id == "") file.getName else id, kind)
  }

  def putXml(xml: Elem, coll: String, id: String): Unit = put(xml.toString, coll, id, XmlDb.Format.XML)

  def putBin(bin: Array[Byte], coll: String, id: String): Unit = put(bin, coll, id, XmlDb.Format.BIN)

  def getXml(name: String, ids: String*): Option[List[Elem]] = collection(name) match {
    case None => None
    case Some(coll) => {
      val entryIds = if (ids.size > 0) ids else getIds(name).get
      val entries = for (id <- entryIds; obj = coll.getResource(id).getContent; if obj.isInstanceOf[String])
        yield XML.loadString(obj.asInstanceOf[String])
      Some(entries.toList)
    }
  }

  def getBin(name: String, ids: String*): Option[List[Array[Byte]]] = collection(name) match {
    case None => None
    case Some(coll) => {
      val entryIds = if (ids.size > 0) ids else getIds(name).get
      val entries = for (id <- entryIds; obj = coll.getResource(id).getContent; if obj.isInstanceOf[Array[Byte]])
        yield obj.asInstanceOf[Array[Byte]]
      Some(entries.toList)
    }
  }

  def getIds(name: String): Option[List[String]] = collection(name) match {
    case None => None
    case Some(coll) => Some(List() ++ coll.listResources)
  }

  def isAvailable: Boolean =
    try {
      DatabaseManager.getCollection(rpcRoot).listResources; true
    } catch {
      case _ => false
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

  def query(collectionId: String, full: Elem): Elem = {
    post(restRoot + collectionId, full.toString)
  }

  def query(collectionId: String, q: String): Elem = {
    val cdata = "<![CDATA[%s]]>".format(q)
    val full =
      <query xmlns="http://exist.sourceforge.net/NS/exist">
        <text>
          { scala.xml.Unparsed(cdata) }
        </text>
        <properties>
          <property name="indent" value="yes"/>
        </properties>
      </query>
    query(collectionId, full)
  }

  private val Http = new HttpClient

  def post(url: String, body: String) = {
    val method = new PostMethod(url)
    method.setRequestEntity(new StringRequestEntity(body, "text/xml", "utf-8"))
    execute(method)
  }

  def get(url: String) = execute(new GetMethod(url))

  private def execute(m: HttpMethodBase) = {
    try { Http.executeMethod(m) } catch { case e => e.printStackTrace() }
    XML.loadString(new String(m.getResponseBody))
  }

}