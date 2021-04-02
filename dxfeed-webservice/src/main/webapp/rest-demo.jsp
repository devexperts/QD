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
    <title>REST Services Demo @ dxFeed Web Service</title>
    <script src="js/jquery/jquery-1.9.0.min.js"></script>
    <script src="js/apps/rest-demo.js"></script>
    <link rel="stylesheet" href="css/style.css"/>
</head>
<body>
<h1>REST Services Demo @ dxFeed Web Service</h1>

<div class="left"><div> <!-- left column container -->

<div class="panel" style="width: 100%">
    <h2>Request</h2>
    <div>
        <form id="eventsForm" action="#">
            <%@include file="jsp-include/event-types.jsp" %>
            <table border="0">
            <tr><td>Symbols</td><td><input type="text" id="symbols" value="IBM,MSFT"></td></tr>
            <tr><td>Sources</td><td><input type="text" id="sources" value=""></td></tr>
            <tr><td>From time</td><td><input type="text" id="fromTime" value=""></td></tr>
            <tr><td>To time</td><td><input type="text" id="toTime" value=""></td></tr>
            </table>
            Format
            <label><input type="radio" name="format" value="json" checked>JSON</label>
            <label><input type="radio" name="format" value="xml">XML</label>
            <label><input type="radio" name="format" value="default">Default (by Accept Header)</label>
            <p>
            Symbols
            <label><input type="radio" name="symbols" value="csv" checked>Comma separated value</label>
            <label><input type="radio" name="symbols" value="dxScript" >dxScript</label>
            <p>
            <label><input type="checkbox" id="indent" checked>Indent result for readability</label>
            <p>
            Lists
            <label><input type="radio" name="lists" value="comma" checked>Comma separated</label>
            <label><input type="radio" name="lists" value="rep">Repeated params</label>
            <p>
            Operation:<br>
            <label><input type="radio" name="operation" value="events" checked>Get last events snapshots</label><br>
            <label><input type="radio" name="operation" value="eventSource">Subscribe to events stream via Server-Sent Events EventSource</label><br>
            <label><input type="radio" name="operation" value="eventSourceSession">As above + create session to modify subscription later</label><br>
            <label><input type="radio" name="operation" value="eventSourceSessionReconnect">Reconnect to the previously created session</label><br>
            <label><input type="radio" name="operation" value="addSubscription">Add subscription to the created session</label><br>
            <label><input type="radio" name="operation" value="removeSubscription">Remove subscription from the created session</label><br>
            <p>
            Method:
            <label><input type="radio" name="method" value="GET" checked>GET</label>
            <label><input type="radio" name="method" value="POST">POST</label>
            <p>
            <button type="submit">Go</button>
            <span id="goComment"></span>
        </form>
        <p>Web service request URL is:<br>
        <div style="overflow: auto">
            <a href="#" id="reqURL"></a><br>
            <span style="display: none" id="reqPostData"></span>
        </div>
    </div>
</div>

</div></div> <!-- end left column container -->

<div class="right"><div> <!-- right column container -->

<div class="panel" style="width: 100%; display: none" id="popupPanel">
    <h2><a href="#" class="close"></a><span id="popupHeader"></span></h2>
    <div><pre id="popupContent" class="frame"></pre></div>
</div>

<div class="panel" style="width: 100%" id="resultPanel">
    <h2><a href="#" class="close"></a><span id="resultHeader">Help</span></h2>
    <div>
        <iframe id="resultIFrame" src="rest/help" width="100%" height="800px"></iframe>
    </div>
</div>

</div></div> <!-- end right column container -->

</body>
</html>
