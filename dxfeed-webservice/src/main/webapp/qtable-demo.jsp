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
    <title>QTable Demo @ dxFeed Web Service</title>

<% if (request.getParameter("mootools.js") != null) { %>
    <!-- Mixin mootools.jar to make sure we can operate in an environment with hacked Object.prototype -->
    <script src="js/mootools/mootools-core-1.4.5-full-nocompat.js"></script>
<% } %>
<% if (request.getParameter("min.js") == null) { %>
    <!-- dxFeed API & UI QTable dependencies -->
    <script src="js/jquery/jquery-1.9.0.js"></script>
    <!-- The following scripts can be used from a merged minified JS file -->
    <script src="js/cometd/cometd.js"></script>
    <script src="js/jquery/jquery.cometd.js"></script>
    <script src="js/dxfeed/dxfeed.cometd.js"></script>
    <script src="js/dxfeed/dxfeed-ui.qtable.js"></script>
<% } else { %>
    <!-- dxFeed API & UI QTable dependencies minified -->
    <script src="js/jquery/jquery-1.9.0.min.js"></script>
    <script src="js/min/dxfeed-ui.cometd.all.min.js"></script>
<% } %>

    <!-- this demo dependencies -->
    <link rel="stylesheet" href="css/style.css"/>
    <script>
        // define custom data items _before_ UI is ready
        dx.define("change", ["Trade.price", "Summary.prevDayClosePrice"], function(price, prev) {
            return dx.format(price - prev, { maxFractionDigits : 2 });
        });
        dx.define("volume", ["Trade.dayVolume"], function(volume) {
            return dx.format(volume, { groupingSeparator : ","});
        });
        dx.define("upDown", ["change"], function(change) {
            return change < 0 ? "down" : "up";
        });
        dx.define("upDownArrow", ["upDown"], function(upDown) {
            return upDown + "-arrow";
        });

        dx.define("blink", [], function () {
            var $this = $(this);
            if (!$this.hasClass("highlight")) {
                $this.addClass("highlight");
                setTimeout(function () {
                    $this.removeClass("highlight");
                }, 300);
            }
        });

        // now, when UI is ready attach handlers to test init & detach functions
        $(function() {
            $("#test-detach").click(function () {
                $("#test").dx("detach");
            });

            $("#test-init").click(function () {
                $("#test").dx("init");
            });
        });
    </script>
</head>
<body>
<h1>QTable Demo @ dxFeed Web Service</h1>

<div class="panel">
    <h2>Simple binding</h2>
    <div>
        <table dx-widget="qtable" class="dataTable">
            <tr>
                <th dx-bind="eventSymbol">Symbol</th>
                <th dx-bind="Trade.price">Last</th>
                <th dx-bind="Quote.bidPrice">Bid</th>
                <th dx-bind="Quote.askPrice">Ask</th>
            </tr>
            <tr><td>SPX</td></tr>
            <tr><td>IBM</td></tr>
            <tr><td>MSFT</td></tr>
            <tr><td>GOOG</td></tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>Indices</h2>
    <div>
        <table dx-widget="qtable" dx-change="blink" class="dataTable">
            <tr dx-change="blink">
                <th dx-bind="eventSymbol">Symbol</th>
                <th dx-bind="Trade.price" dx-class="upDown">Last</th>
                <th dx-bind="change" dx-class="upDownArrow">Change</th>
                <th dx-bind="Summary.dayHighPrice">High</th>
                <th dx-bind="Summary.dayLowPrice">Low</th>
            </tr>
            <tr><td>SPX</td></tr>
            <tr><td>OEX</td></tr>
            <tr><td>DJX</td></tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>Indices change with full row highlight</h2>
    <div>
        <table dx-widget="qtable" dx-change="blink" dx-class="upDown" class="dataTable">
            <tr>
                <th dx-bind="eventSymbol">Symbol</th>
                <th dx-bind="Trade.price" dx-class="upDown">Last</th>
                <th dx-bind="change" dx-class="upDownArrow">Change</th>
            </tr>
            <tr><td>SPX</td></tr>
            <tr><td>DJX</td></tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>Stocks with symbol highlight and multiclass</h2>
    <div>
        <table dx-widget="qtable" dx-change="blink" class="dataTable">
            <tr>
                <th dx-bind="eventSymbol" dx-class="upDown">Symbol</th>
                <th dx-bind="Trade.price" dx-class="upDown upDownArrow">Price</th>
                <th dx-bind="volume">Volume</th>
            </tr>
            <tr><td>CSCO</td></tr>
            <tr><td>INTC</td></tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>Single row editable with in-line code</h2>
    <div>
        <table dx-widget="qtable" dx-change="blink" class="dataTable">
            <tr>
                <td dx-bind="eventSymbol"><input type="text" placeholder="symbol"></td>
                <td dx-bind="Trade.price" dx-class="upDownArrow"></td>
                <td dx-bind="Quote.bidPrice Quote.askPrice | (Quote.bidPrice + Quote.askPrice) / 2 | minFractionDigits: 2 | maxFractionDigits: 3"></td>
            </tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>More in-line code</h2>
    <div>
        <table dx-widget="qtable" dx-change="blink" class="dataTable">
            <tr>
                <th dx-bind="eventSymbol">Symbol</th>
                <th dx-bind="Trade.price | minFractionDigits: 4">Price(4)</th>
                <th dx-bind="Trade.size | Trade.size * 100">Size(x100)</th>
                <th dx-bind="Summary.dayHighPrice Summary.dayLowPrice | Summary.dayHighPrice - Summary.dayLowPrice | maxFractionDigits: 4">Day Range</th>
            </tr>
            <tr><td>GOOG</td></tr>
            <tr><td>MSFT</td></tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>Technology Quotes</h2>
    <div>
        <table dx-widget="qtable" dx-change="blink" class="dataTable">
            <tr>
                <th dx-bind="eventSymbol">Symbol</th>
                <th dx-bind="Profile.description">Description</th>
                <th dx-bind="Quote.bidPrice">Bid</th>
                <th dx-bind="Quote.askPrice">Ask</th>
                <th dx-bind="Trade.price">Last</th>
                <th dx-bind="Trade.size">Size</th>
                <th dx-bind="volume">Volume</th>
            </tr>
            <tr><td>IBM</td></tr>
            <tr><td>MSFT</td></tr>
            <tr><td>GOOG</td></tr>
            <tr><td>AAPL</td></tr>
        </table>
    </div>
</div>

<div class="panel">
    <h2>Technology (change with arrow only)</h2>
    <div>
        <table id="test" dx-widget="qtable" dx-change="blink" class="dataTable">
            <tr>
                <th dx-bind="eventSymbol">Symbol</th>
                <th dx-bind="change" dx-class="upDownArrow">Change</th>
            </tr>
            <tr><td>IBM</td></tr>
            <tr><td>MSFT</td></tr>
            <tr><td>GOOG</td></tr>
            <tr><td>AAPL</td></tr>
        </table>
        <button id="test-detach" type="button">Detach</button>
        <button id="test-init" type>Init</button>
    </div>
</div>

</body>
</html>
