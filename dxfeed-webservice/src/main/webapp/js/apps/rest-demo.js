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

    var model = {};

    updateView();

    $("input").keyup(updateView).click(updateView);
    $("#popupPanel").find(".close").click(closePopup);
    $("#resultPanel").find(".close").click(closeResult);

    $("#eventsForm").submit(function() {
        updateModel();
        var subscriptionChange = endsWith(model.operation, "Subscription");
        var useAjax = model.method === "POST" || subscriptionChange;

        if (useAjax) {
            if (!subscriptionChange)
                closeResult();
            var title = "Result from " + model.method + " " + model.url;
            $.ajax({
                type: model.method,
                url: model.url,
                data: model.data,
                success: function(data) { showPopup(title, data.replace(/\r\n/g, "\n")); },
                error:function(req, status, error) { showPopup(title, "ERROR: " + status + " " + error); },
                dataType: "text"
            });
        } else {
            closeResult();
            if (model.format === "json") {
                // open JSON right here in result iframe
                showResult("Result from GET " + model.url, model.url);
            } else {
                // open XML or default in a new page (does not work in iframe)
                window.open(model.url, '_blank').focus();
            }
        }
        return false; // prevent default action
    });

    function showResult(title, url) {
        $("#resultPanel").show();
        $("#resultHeader").text(title);
        $("#resultIFrame").prop("src", url);
    }

    function closeResult() {
        $("#resultIFrame").prop("src", "about:blank");
        $("#resultPanel").hide();
    }

    function showPopup(title, content) {
        $("#popupPanel").show();
        $("#popupHeader").text(title);
        $("#popupContent").text(content);
    }

    function closePopup() {
        $("#popupPanel").hide();
    }

    function getList(name) {
        var s = $("#" + name).val().trim();
        return s === "" ? [] : s.split(/,/);
    }

    function updateModel() {
        // --- process state ---
        var format = $("input[name=format]:checked").val();
        var events = [];
        $(".type :checkbox:checked").each(function () {
            var checkBox = $(this);
            var name = checkBox.attr("name");
            events.push(name);
        });
        var rawSymbols = $("#symbols").val().trim();
        var sources = getList("sources");
        var fromTime = $("#fromTime").val();
        var toTime = $("#toTime").val();
        var indent = $("#indent").prop('checked');
        var symbolType = $("input[name=symbols]:checked").val();
        var csv = symbolType === "csv";
        var dxScript = symbolType === "dxScript";
        var comma = $("input[name=lists]:checked").val() === "comma";
        var method = $("input[name=method]:checked").val();
        var operation = $("input[" + "name=operation]:checked").val();

        if (dxScript && rawSymbols.length > 0)
            rawSymbols = encodeURIComponent(rawSymbols);
        var res = operation;
        var reconnect = endsWith(res, "Reconnect");
        if (reconnect)
            res = res.substr(0, res.length - "Reconnect".length);
        var session = endsWith(res, "Session");
        if (session)
            res = res.substr(0, res.length - "Session".length);

        var url = "rest/" + res + (format === "default" ? "" : "." + format);
        var data = null;
        if (method === "POST") {
            data = {};
            if (comma) {
                if (events.length > 0)
                    data.events = events.join(",");
                if (rawSymbols.length > 0)
                    data.symbols = rawSymbols;
                if (sources.length > 0)
                    data.sources = sources.join(",");
            } else {
                if (events.length > 0)
                    data.events = events;
                if (rawSymbols.length > 0)
                    data.symbols = rawSymbols.split(/,/);
                if (sources.length > 0)
                    data.sources = sources;
            }
            if (fromTime !== "")
                data.fromTime = fromTime;
            if (toTime !== "")
                data.toTime = toTime;
            if (session)
                data.session = "";
            if (reconnect)
                data.reconnect = "";
            if (indent)
                data.indent = "";
        } else {
            var q = "";
            if (events.length > 0)
                q += "&" + (comma ? "events=" + events.join(",") : "event=" + events.join("&event="));
            if (rawSymbols.length > 0)
                q += "&" + ((comma && csv) ? "symbols=" + rawSymbols : "symbol=" + (dxScript ? rawSymbols : rawSymbols.replace(/,/, "&symbol=")));
            if (sources.length > 0)
                q += "&" + (comma ? "sources=" + sources.join(",") : "source=" + sources.join("&source="));
            if (fromTime !== "")
                q += "&fromTime=" + fromTime;
            if (toTime !== "")
                q += "&toTime=" + toTime;
            if (session)
                q += "&session";
            if (reconnect)
                q += "&reconnect";
            if (indent)
                q += "&indent";
            if (q.length > 0)
                url += "?" + q.substr(1);
        }
        // --- actually update model ---
        model.method = method;
        model.operation = operation;
        model.format = format;
        model.url = url;
        model.data = data;
    }

    function updateView() {
        updateModel();
        var $submit = $(":submit");
        var $goComment = $("#goComment");
        if (startsWith(model.operation, "eventSource") && model.method === "POST") {
            $goComment.text("POST is not supported with streaming operations by this demo page, but is actually supported by the service.");
            $submit.attr("disabled", true);
        } else {
            $goComment.text("");
            $submit.removeAttr("disabled");
        }
        $("#reqURL").text(model.url).prop("href", model.url);
        if (model.data == null) {
            $("#reqPostData").hide();
        } else {
            $("#reqPostData").text("POST Data: " + $.param(model.data)).show();
        }
    }

    function startsWith(str, suffix) {
        return str.indexOf(suffix) === 0;
    }

    function endsWith(str, suffix) {
        return str.indexOf(suffix, str.length - suffix.length) !== -1;
    }
});
