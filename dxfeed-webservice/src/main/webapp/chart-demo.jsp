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
<%@ page import="com.dxfeed.event.EventType" %>
<%@ page import="com.dxfeed.event.TimeSeriesEvent" %>
<%@ page import="com.dxfeed.webservice.DXFeedContext" %>
<%@ page import="com.dxfeed.webservice.DXFeedJson" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<html>
<head>
    <title>Chart Demo @ dxFeed Web Service</title>

    <% if (request.getParameter("min.js") == null) { %>
    <!-- dxFeed API dependencies -->
    <script src="js/jquery/jquery-1.9.0.js"></script>
    <!-- The following scripts can be used from a merged minified JS file -->
    <script src="js/cometd/cometd.js"></script>
    <script src="js/jquery/jquery.cometd.js"></script>
    <script src="js/dxfeed/dxfeed.cometd.js"></script>
    <!-- flot -->
    <script src="js/flot/jquery.flot.js"></script>
    <script src="js/flot/jquery.flot.time.js"></script>
<% } else { %>
    <!-- dxFeed API dependencies minified -->
    <script src="js/jquery/jquery-1.9.0.min.js"></script>
    <script src="js/min/dxfeed.cometd.all.min.js"></script>
    <!-- flot minified -->
    <script src="js/flot/jquery.flot.min.js"></script>
    <script src="js/flot/jquery.flot.time.min.js"></script>
<% } %>

    <!-- Chart demo dependencies -->
    <script src="js/apps/chart-demo.js"></script>
    <link rel="stylesheet" href="css/style.css"/>
</head>
<body>
<h1>Chart demo @ dxFeed Web Service</h1>

<div class="panel">
    <h2>Chart</h2>
    <div>
        <div style="height: 28px">
            <span class="v-helper"></span>
            <span style="float: left; width: 30px; height: 100%">
                <span class="v-helper"></span>
                <img id="loadingImg" style="vertical-align: middle"src="img/ajax-loader.gif" alt="Loading...">
                &nbsp;
            </span>
            <span style="vertical-align: middle">
                <label>Symbol: <input id="symbolText" type="text" size="10" value="SPX"></label>
                <label>Period:
                    <select id="periodSelect">
                        <option value="D" selected>D</option>
                        <option value="15m">15m</option>
                    </select>
                </label>
                <label>
                    Event:
                    <select id="eventSelect">
                    <%
                        for (Map.Entry<String, Class<? extends EventType<?>>> entry : DXFeedContext.INSTANCE.getEventTypes().entrySet()) {
                            String typeName = entry.getKey();
                            Class<?> typeClass = entry.getValue();
                            if (!TimeSeriesEvent.class.isAssignableFrom(typeClass) )
                                continue;
                            List<String> propsList = DXFeedJson.getProperties(typeClass);
                            if (!propsList.contains("close"))
                                continue;
                            StringBuilder props = new StringBuilder();
                            for (String s : propsList) {
                                if (s.equals("eventSymbol"))
                                    continue;
                                if (props.length() > 0)
                                    props.append(",");
                                props.append(s);
                            }
                    %>
                        <option value="<%= typeName %>"<%= typeName.equals("Candle") ? " selected" : ""%>
                                dx-props="<%= props %>"><%= typeName %></option>
                    <%
                        }
                    %>
                    </select>
                </label>
                <label>
                    Property:
                    <select id="propSelect">

                    </select>
                </label>
            </span>
        </div>
        <div id="plot" style="width: 800px; height:300px"></div>
    </div>
</div>
</body>
</html>
