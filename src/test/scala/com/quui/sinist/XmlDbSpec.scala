/**
 * Copyright (c) 2010-2011 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 */
package com.quui.sinist

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import scala.xml._
import org.scalatest.FlatSpec

/**
 * Sample usage and tests for the {@link XmlDb} class.
 * @author Fabian Steeg
 */
@RunWith(classOf[JUnitRunner])
class XmlDbSpec extends FlatSpec with ShouldMatchers {

  val port = 7777 // change here to your port for running the tests
  val db = XmlDb(port = port, user = "admin") // defaults: server="localhost", port=8080, user="guest", pass="guest"
  val coll = "test-collection-1"
  val xml: Elem = <some><xml>element</xml></some>
  val xmlDocId = "test-xml-id"
  val binDocId = "test-bin-id"
  val pretty = new PrettyPrinter(200, 2)

  "The XmlDb" should "allow access to its locations" in {
    "xmldb:exist://localhost:%s/exist/xmlrpc/db/".format(port) should equal { db.rpcRoot }
    "XmlDb@" + "xmldb:exist://localhost:%s/exist/xmlrpc/db/".format(port) should equal { db.toString }
  }

  it can "be used to store and retrieve XML elements" in {
    pretty format xml should equal {
      db.putXml(xml, coll, xmlDocId)
      val res: Elem = db.getXml(coll, xmlDocId).get(0)
      pretty format res
    }
  }

  it can "be used to store and retrieve binary data" in {
    val bin: Array[Byte] = "data".getBytes
    new String(bin) should equal {
      db.putBin(bin, coll, binDocId)
      val res: Array[Byte] = db.getBin(coll, binDocId).get(0)
      new String(res)
    }
  }

  it should "allow access to all ids of stored pages of a collection" in {
    2 should equal { db.getIds(coll).get.size } // we added on XML, one BIN

  }

  it should "return all entries for a specific format if no ids are given" in {
    1 should equal { db.getXml(coll).get.size } // we added one XML
    1 should equal { db.getBin(coll).get.size } // we added one BIN
  }

  it should "return optional lists of Elem or Array[Byte] objects" in {
    classOf[Elem] should equal { db.getXml(coll, xmlDocId).get(0).getClass }
    classOf[Array[Byte]] should equal { db.getBin(coll, binDocId).get(0).getClass }
  }

  it should "allow to retrieve XML for manipulation and store it back" in {
    val oldXml = db.getXml(coll, xmlDocId).get(0)
    pretty format <and>{ oldXml }</and> should equal {
      db.putXml(<and>{ oldXml }</and>, coll, xmlDocId)
      pretty format db.getXml(coll, xmlDocId).get(0)
    }
  }

  it should "have an informative toString representation" in {
    true should equal { db.toString.contains("xmldb:exist://localhost:%s/exist/xmlrpc/db/".format(port)) }
  }

  it can "be checked for availability" in {
    true should equal { db.isAvailable }
    false should equal { XmlDb("xmldb:exist://localhost:1111/exist/xmlrpc").isAvailable }
  }

  it should "equal another DB at the same location" in {
    val location = "xmldb:exist://localhost:1111/exist/xmlrpc"
    XmlDb(location) should equal { XmlDb(location) }
  }

  it should "index new data automatically if an index is set up" in {
    initIndex()
    db.putXml(<p><note type="quick">text1</note></p>, "test", "query-test-1")
    db.putXml(<p><note type="quick">text2</note></p>, "test", "query-test-2")
  }

  it should "allow xqueries on attributes" in {
    val xquery = "for $m in //note/attribute::type[ft:query(., 'quick')]/ancestor::p return $m"
    val response = db.query("test", xquery)
    (response \ "p").size should equal(2)
  }

  it should "allow xqueries on text nodes" in {
    val xquery = "for $m in //note[ft:query(., 'text?')]/ancestor::p return $m"
    val response = db.query("test", xquery)
    (response \ "p").size should equal(2)
  }

  it should "allow xqueries with full exist config" in {
    val full =
      <query xmlns="http://exist.sourceforge.net/NS/exist" start="1" max="20">
        <text>
          <![CDATA[ 
				for $m in //note/attribute::type[ft:query(., 'quick')]/ancestor::p return $m
				]]>
        </text>
        <properties>
          <property name="indent" value="yes"/>
        </properties>
      </query>
    val response = db.query("test", full)
    (response \ "p").size should equal(2)
  }

  private def initIndex() = {
    val config =
      <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:atom="http://www.w3.org/2005/Atom" xmlns:html="http://www.w3.org/1999/xhtml" xmlns:wiki="http://exist-db.org/xquery/wiki">
          <fulltext default="none" attributes="no"/>
          <lucene>
            <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
            <analyzer id="ws" class="org.apache.lucene.analysis.WhitespaceAnalyzer"/>
            <text match="//note"/>
            <text match="//note/@type"/>
          </lucene>
        </index>
      </collection>
    db.putXml(config, "system/config/db/test", "collection.xconf")
    val response = db.get(db.restRoot + "system/config/db/test/collection.xconf")
    (response \ "lucene") should equal(config \ "lucene")
  }

}