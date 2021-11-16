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
<%@ page import="com.devexperts.qd.QDFactory" %>
<!DOCTYPE html>
<html>
<head>
    <title>Welcome @ dxFeed Web Service</title>
    <link rel="stylesheet" href="css/style.css"/>
</head>
<body>
<h1>Welcome @ dxFeed Web Service</h1>

<div class="panel">
    <h2><%= QDFactory.getVersion() %></h2>
</div>

<div class="panel">
    <h2>Pages</h2>
    <div>
        <ul>
            <li><a href="debug-console.jsp">Debug Console</a>
                (<a href="debug-console.jsp?min.js">minified</a>)
            </li>
            <li><a href="qtable-demo.jsp">Quote Table Demo</a>
                (<a href="qtable-demo.jsp?min.js">minified</a>)
                (<a href="qtable-demo.jsp?mootools.js">with mootools mixins</a>)
            </li>
            <li><a href="chart-demo.jsp">Chart Demo</a>
                (<a href="chart-demo.jsp?min.js">minified</a>)
            </li>
            <li><a href="rest-demo.jsp">REST Services Demo</a>
            </li>
        </ul>
    </div>
</div>
</body>
</html>
