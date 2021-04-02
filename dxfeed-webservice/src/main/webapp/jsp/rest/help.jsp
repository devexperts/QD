<%--
  !++
  QDS - Quick Data Signalling Library
  !-
  Copyright (C) 2002 - 2021 Devexperts LLC
  !-
  This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
  If a copy of the MPL was not distributed with this file, You can obtain one at
  http://mozilla.org/MPL/2.0/.
  !__
  --%>
<%@ page import="com.devexperts.annotation.Description" %>
<%@ page import="com.dxfeed.event.EventType" %>
<%@ page import="com.dxfeed.webservice.DXFeedContext" %>
<%@ page import="com.dxfeed.webservice.rest.EventsResource" %>
<%@ page import="com.dxfeed.webservice.rest.Format" %>
<%@ page import="com.dxfeed.webservice.rest.ParamInfo" %>
<%@ page import="com.dxfeed.webservice.rest.ParamType" %>
<%@ page import="com.dxfeed.webservice.rest.PathInfo" %>
<%@ page import="java.util.Map" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
  <title>dxFeed REST API Help</title>
  <link rel="stylesheet" href="../css/style.css"/>
</head>
<body>
<h1>dxFeed REST API Help</h1>
dxFeed REST API contains the following resources:

<ul><%
  for (PathInfo pathInfo : EventsResource.PATHS.values()) {
    String path = pathInfo.path.substring(1);
%><li><a href="<%= path %>"><%= path %></a> - <%= pathInfo.description %><%
  }
%></ul>

Resources are available via GET and POST HTTP methods and recognize the following common request parameters which
can be provided in URL or in request body of POST with "text/x-www-form-urlencoded" MIME type:

<ul><%
  for (ParamInfo paramInfo : EventsResource.PARAMS.values()) {
%><li><b><%= paramInfo.name %></b> : <%= paramInfo.type %> - <%= paramInfo.description %><%
  }
%></ul>

Additional parameters for specific resources are:

<ul><%
    for (PathInfo pathInfo : EventsResource.PATHS.values()) {
      if (pathInfo.nArgs == 0)
        continue;
      String path = pathInfo.path.substring(1);
%><li><a href="<%= path %>"><%= path %></a><ul><%
      for (ParamInfo paramInfo : pathInfo.args) {
%><li><b><%= paramInfo.name %></b> : <%= paramInfo.type %> - <%= paramInfo.description %><%
      }
%></ul><%
  }
%></ul>

Parameter types are explained below:

<ul><%
  for (ParamType paramType : ParamType.values()) {
%><li><b><%= paramType %></b> - <%= paramType.description %><%
  }
%></ul>

Resources support multiple response formats with the exception of "help". The format can be specified as
MIME type in HTTP "Accept" header or by adding the corresponding extension to the resource name.
The later approach takes precedence. The formats are listed below:

<ul><%
  for (Format format : Format.values()) {
%><li><b><%= format %></b> - MIME type is "<%= format.mediaType %>", extension is "<%= format.extension %>".<%
  }
%></ul>

All available event types are listed below. The corresponding links lead to their full documentation.

<%
  for (Map.Entry<DXFeedContext.Group, Map<String, Class<? extends EventType<?>>>> gEntry : DXFeedContext.INSTANCE.getGroupedEventTypes().entrySet()) {
    DXFeedContext.Group group = gEntry.getKey();
%><p><span style="font-weight: bold"><%= group.title %></span> (see <a href="../<%= group.seeHRef %>"><%= group.seeName %></a>)<ul><%
    for (Class<? extends EventType<?>> typeClass : gEntry.getValue().values()) {
      String typeName = typeClass.getSimpleName();
      Description descriptionAnnotation = typeClass.getAnnotation(Description.class);
      String description = descriptionAnnotation == null ? "" : descriptionAnnotation.value();
%><li><a href="../javadoc/<%= typeClass.getName().replace('.', '/') %>.html"><%= typeName %></a> - <%= description %><%
    }
%></ul><%
  }
%>

The following additional reference documents are available:

<ul>
  <li><a href="../xsd/dxfeed-event.xsd">xsd/dxfeed-event.xsd</a> - The XML schema for all event types.
  <li><a href="../xsd/dxfeed-service.xsd">xsd/dxfeed-service.xsd</a> - The XML schema for the responses of REST API resources.
</ul>

</body>
</html>
