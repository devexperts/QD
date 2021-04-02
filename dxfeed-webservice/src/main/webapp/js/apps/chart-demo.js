/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
$(function() {
    "use strict";

    var loadingImg = $("#loadingImg");
    var symbolText = $("#symbolText");
    var periodSelect = $("#periodSelect");
    var eventSelect = $("#eventSelect");
    var propSelect = $("#propSelect");

    var plot = $.plot($("#plot"), [], {
        xaxis: {
            mode: "time",
            timeformat: "%Y%m%d"
        }
    });

    var sub = null;
    var eventMap = {}; // index -> event
    var dataSize = 0;
    var updateDataTimeout = null;

    symbolText.keydown(onSymbolKeyDown);
    symbolText.change(updateSub);
    periodSelect.change(updateSub);
    eventSelect.change(updateProps);
    propSelect.change(updateSub);

    updateSub();
    updateProps();

    function onSymbolKeyDown(e) {
        if (e.keyCode === 13) { // ENTER
            var input = $(this);
            var s = $.trim(input.val()); // trim input
            if (s === s.toLowerCase()) // uppercase on all lowercase
                s = s.toUpperCase();
            input.val(s); // update input val
        }
    }

    function updateSub() {
        setData([]);
        eventMap = {};
        var s = symbolText.val();
        var p = periodSelect.val();
        var backDays = p === "D" ? 365 : 5;
        if (sub !== null)
            sub.close();
        sub = dx.feed.createTimeSeriesSubscription(eventSelect.val());
        sub.setFromTime(new Date().getTime() - backDays * 24 * 3600 * 1000);
        sub.setSymbols(dx.symbols.changeAttribute(s, "", p));
        sub.onEvent = onEvent;
    }

    function updateProps() {
        var event = eventSelect.val();
        var props = $("#eventSelect option[value='" + event + "']").attr("dx-props").split(",");
        propSelect.empty();
        for (var i in props) {
            var prop = props[i];
            propSelect.append($("<option>", { value : prop, selected: prop === 'close' }).text(prop));
        }
        updateSub();
    }

    function onEvent(event) {
        eventMap[event.index] = event;
        if (updateDataTimeout === null)
            updateDataTimeout = setTimeout(updateData, 0);
    }

    function updateData() {
        updateDataTimeout = null;
        var series = [];
        var prop = propSelect.val();
        for (var index in eventMap) {
            var event = eventMap[index];
            series.push([event.time, event[prop]]);
        }
        series.sort(function(d1, d2) {
            return d1[0] - d2[0];
        });
        setData(series);
    }

    function setData(series) {
        var data = [series];
        plot.setData(data);
        if (dataSize !== series.length) {
            dataSize = series.length;
            plot.setupGrid();
        }
        plot.draw();
        if (dataSize === 0)
            loadingImg.show();
        else
            loadingImg.hide();
    }

});
