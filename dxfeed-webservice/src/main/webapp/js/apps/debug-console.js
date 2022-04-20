/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
$(function() {
    "use strict";

    // set debug log level
    dx.feed.logLevel("debug");
    // configure send message size
    dx.feed.maxSendMessageSize(8192);

    // install onDemand control listeners
    $("#replayButton").click(function () { dx.feed.replay(new Date("2010-05-06T14:47:48-0500")); });
    $("#stopAndResumeButton").click(function () { dx.feed.stopAndResume(); });
    $("#stopAndClearButton").click(function () { dx.feed.stopAndClear(); });
    $("#setSpeedSelect").click(function () { dx.feed.setSpeed($(this).val()); });
    $("#pauseButton").click(function () { dx.feed.pause(); });

    // update state on dxfeed state change
    var state = $("#state");
    function updateState() { state.text(JSON.stringify(dx.feed.state)); }
    dx.feed.state.onChange = updateState;
    updateState();

    // bind to other UI in debug.html
    var createButton = $("#createButton");
    var createTimeSeriesButton = $("#createTimeSeriesButton");
    var fromTimeText = $("#fromTimeText");
    var contentPanel = $("#contentPanel");

    fromTimeText.val(new Date().toISOString());
    createButton.click(function() { createSub(false); });
    createTimeSeriesButton.click(function() { createSub(true); });

    var subCounter = 0;

    function createSub(timeSeries) {
        var subId = "sub" + (++subCounter);
        console.log("Creating " + subId);

        contentPanel.append("<div class='panel' id='" + subId + "'>" +
            "<h2><a href='#' class='close'></a>Subscription " + subId + "</h2>" +
            "<div class='content'>" +
            (timeSeries ? "<label>time series from time: <input class='fromTimeText' type='text' size='25'></label>" : "") +
            "<table class='dataTable'>" +
            "<tr class='r1'><th rowspan='2'>Symbol</th></tr>" +
            "<tr class='r2'></tr>" +
            "</table>" +
            "</div>" +
            "</div>");

        var subPanel = contentPanel.children("#" + subId);
        var dataTable = subPanel.find(".dataTable");
        var subFromTimeText = subPanel.find(".fromTimeText");
        var r1 = subPanel.find(".r1");
        var r2 = subPanel.find(".r2");
        var closeButton = subPanel.find("a.close");

        var types = [];
        var typePos = {};
        var nc = 0;

        $(".type :checkbox:checked").each(function() {
            var checkBox = $(this);
            var name = checkBox.attr("name");
            var cols = checkBox.attr("dx-props").split(",");
            var colPos = {};
            typePos[name] = colPos;
            types.push(name);
            r1.append("<th colspan='" + cols.length + "'>" + name + "</th>");
            for (var i = 0; i < cols.length; i++) {
                var col = cols[i];
                r2.append("<th>" + col + "</th>");
                colPos[col] = nc++;
            }
            checkBox.attr("checked", false);
        });

        // create subscription for a specific event types
        var sub = timeSeries ?
            dx.feed.createTimeSeriesSubscription(types) :
            dx.feed.createSubscription(types);

        // set time for time series
        if (timeSeries) {
            sub.setFromTime(new Date(fromTimeText.val()));
            subFromTimeText.val(fromTimeText.val());
            subFromTimeText.change(function() {
                sub.setFromTime(new Date(subFromTimeText.val()));
            });
        }

        // define listener for events
        sub.onEvent = onEvent;

        // attach UI listeners
        closeButton.click(onCloseClick);

        // initialize UI
        addRow();

        // -------------- functions --------------

        function addRow() {
            var emptyCols = "";
            for (var i = 0; i < nc; i++)
                emptyCols += "<td class='v'></td>";
            dataTable.append("<tr><td><input type='text'></td>" + emptyCols + "</tr>");
            var input = dataTable.find("input").filter(":last");
            input.keydown(onInputKeyDown);
            input.focus();
        }

        function onInputKeyDown(e) {
            var input = $(this);
            var row = input.parents("tr");
            var prev = row.prev().find("input");
            var next = row.next().find("input");
            switch (e.keyCode) {
            case 13: // ENTER
                var s = $.trim(input.val()); // trim input
                if (!s && dataTable.find("input").length > 1) {
                    row.remove();
                    if (next.length)
                        next.focus();
                    else
                        prev.focus();
                } else {
                    if (s === s.toLowerCase()) // uppercase on all lowercase
                        s = s.toUpperCase();
                    input.val(s); // update input val
                }
                updateSubscriptionSymbols();
                break;
            case 38: // UP
                prev.focus();
                break;
            case 40: // DOWN
                next.focus();
                break;
            }
        }

        function onButtonKeydown(e) {
            if (e.keyCode == 38) { // UP
                dataTable.find("input").filter(":last").focus();
            }
        }

        function onCloseClick() {
            console.log("Removing " + subId);
            sub.close();
            subPanel.remove();
        }

        function updateSubscriptionSymbols() {
            var symbols = [];
            var lastSymbol = null;
            dataTable.find("tr").each(function() {
                var row = $(this);
                var symbol = row.find("input").val();
                if (symbol)
                    symbols.push(symbol);
                lastSymbol = symbol;
            });
            sub.setSymbols(symbols);
            if (lastSymbol)
                addRow();
        }

        function onEvent(event) {
            var colPos = typePos[event.eventType];
            dataTable.find("input").each(function() {
                var input = $(this);
                var symbol = input.val();
                if (symbol == event.eventSymbol) {
                    var row = input.parents("tr");
                    var cols = row.children(".v");
                    for (var col in colPos) {
                        var index = colPos[col];
                        cols.eq(index).text(event[col]);
                    }
                }
            });
        }
    }
});
