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
<!DOCTYPE html>
<html>
<head>
    <title>Debug Console @ dxFeed Web Service</title>

    <% if (request.getParameter("min.js") == null) { %>
    <!-- dxFeed API dependencies -->
    <script src="js/jquery/jquery-1.9.0.js"></script>
    <!-- The following scripts can be used from a merged minified JS file -->
    <script src="js/cometd/cometd.js"></script>
    <script src="js/jquery/jquery.cometd.js"></script>
    <script src="js/dxfeed/dxfeed.cometd.js"></script>
<% } else { %>
    <!-- dxFeed API dependencies minified -->
    <script src="js/jquery/jquery-1.9.0.min.js"></script>
    <script src="js/min/dxfeed.cometd.all.min.js"></script>
<% } %>

    <!-- Debug console dependencies -->
    <script src="js/apps/debug-console.js"></script>
    <link rel="stylesheet" href="css/style.css"/>
</head>
<body>
<h1>Debug Console @ dxFeed Web Service</h1>

<div class="panel">
    <h2>OnDemand</h2>
    <div>
        <p>State: <span id="state"></span></p>
        <button id="replayButton" type="button">Replay "Flashcrash"</button>
        <button id="stopAndResumeButton" type="button">Stop&amp;Resume</button>
        <button id="stopAndClearButton" type="button">Stop&amp;Clear</button>
        <label for="setSpeedSelect">Set speed</label>
        <select id="setSpeedSelect">
            <option>0.0</option>
            <option>0.1</option>
            <option>0.5</option>
            <option selected>1.0</option>
            <option>2.0</option>
            <option>3.0</option>
            <option>5.0</option>
            <option>10.0</option>
        </select>
        <button id="pauseButton" type="button">Pause</button>
    </div>
</div>

<div class="panel">
    <h2>Event types</h2>
    <div>
        <%@include file="jsp-include/event-types.jsp" %>
        <p>
        <button id="createButton" type="button">Create subscription</button>
        <button id="createTimeSeriesButton" type="button">Create time series subscription</button>
        <label>
            from time: <input id="fromTimeText" type="text" size="25">
        </label>
    </div>
</div>
<div id="contentPanel"></div>
</body>
</html>
