/**************************************************************************************************
 * Copyright (c) 2010 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 *************************************************************************************************/
import sbt._

class SinistProject(info: ProjectInfo) extends DefaultProject(info) {
  val junit = "junit" % "junit" % "4.8.1"
  val log4j = "log4j" % "log4j" % "1.2.16"
  val scalaTest = "org.scalatest" % "scalatest" % "1.2"
  val xerces = "xerces" % "xercesImpl" % "2.9.1"
  val wsCommonsUtil = "org.apache.ws.commons.util" % "ws-commons-util" % "1.0.2"
  val xmlrpcClient = "org.apache.xmlrpc" % "xmlrpc-client" % "3.1.3"
  val xmlrpcCommon = "org.apache.xmlrpc" % "xmlrpc-common" % "3.1.3"
}