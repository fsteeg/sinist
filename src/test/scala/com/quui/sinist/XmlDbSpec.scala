/**************************************************************************************************
 * Copyright (c) 2010 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 *************************************************************************************************/
package com.quui.sinist

import scala.xml._
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import java.io.File
/**
 * Sample usage and tests for the {@link XmlDb} class.
 * @author Fabian Steeg
 */
@RunWith(classOf[JUnitRunner])
class XmlDbSpec extends Spec with ShouldMatchers {

  describe("The XmlDb") {

    val location = "xmldb:exist://localhost:8080/exist/xmlrpc/"
    val db = XmlDb(location, "db", "test") // pass optional location, root and collection prefix
    val coll = "test-coll"
    val xml: Elem = <some><xml>element</xml></some>
    val xmlDocId = "test-xml-id"
    val binDocId = "test-bin-id"
    val pretty = new PrettyPrinter(200, 2)
    
    it("allows access to its parameters") {
      expect(location) {db.location}
      expect("db/") {db.root}
      expect("test/") {db.prefix}
      expect("XmlDb@xmldb:exist://localhost:8080/exist/xmlrpc/db/test/") { db.toString }
    }
    
    it("can be used to store and retrieve XML elements") {
      expect(pretty format xml) {
        db.putXml(xml, coll, xmlDocId)
        val res: Elem = db.getXml(coll, xmlDocId).get(0)
        pretty format res
      }
    }

    it("can be used to store and retrieve binary data") {
      val bin: Array[Byte] = "data".getBytes
      expect(new String(bin)) {
        db.putBin(bin, coll, binDocId)
        val res: Array[Byte] = db.getBin(coll, binDocId).get(0)
        new String(res)
      }
    }

    it("allows access to all ids of stored pages of a collection") {
      expect(2) { db.getIds(coll).get.size } // we added on XML, one BIN

    }

    it("returns all entries for a specific format if no ids are given") {
      expect(1) { db.getXml(coll).get.size } // we added one XML
      expect(1) { db.getBin(coll).get.size } // we added one BIN
    }

    it("returns optional lists of Elem or Array[Byte] objects") {
      expect(classOf[Elem]) { db.getXml(coll, xmlDocId).get(0).getClass }
      expect(classOf[Array[Byte]]) { db.getBin(coll, binDocId).get(0).getClass }
    }

    it("allows to retrieve XML for manipulation and store it back") {
      val oldXml = db.getXml(coll, xmlDocId).get(0)
      expect(pretty format <and>{ oldXml }</and>) {
        db.putXml(<and>{ oldXml }</and>, coll, xmlDocId)
        pretty format db.getXml(coll, xmlDocId).get(0)
      }
    }
    
    it("has an informative toString representation") {
      expect(true) { db.toString.contains(location) }
    }
    
    it("can be checked for availability") {
      expect(true) { db.isAvailable }
      expect(false) { XmlDb("xmldb:exist://localhost:1111/exist/xmlrpc").isAvailable }
    }
    
    it("equals another DB at the same location") {
      val location = "xmldb:exist://localhost:1111/exist/xmlrpc"
      expect(XmlDb(location)) { XmlDb(location) }
    }

  }
}