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

(function($) {
    "use strict";

    var dx = window.dx = window.dx || {}; // window.dx namespace

    // ===== private utility functions =====

    var scriptPath = (function() {
        var path = "/";
        if (document.currentScript) {
            path = document.currentScript.src;
        } else {
            var scripts = document.getElementsByTagName('script');
            if (scripts != null && scripts.length > 0) {
                path = scripts[scripts.length-1].src;
            }
        }
        return path;
    })();

    var hasOwnProperty = Object.prototype.hasOwnProperty;

    function inSet(obj, key) {
        return hasOwnProperty.call(obj, key);
    }

    function removeFromArray(a, val) {
        var i = a.indexOf(val);
        return i < 0 ? a : a.slice(0, i).concat(a.slice(i + 1));
    }

    function forArray(a, fn) {
        for (var i = 0, n = a.length; i < n; i++)
            fn(a[i], i);
    }

    /**
     * @template K, V
     * @param {Object.<K,V>} map
     * @param {function(value: V, key: K)} fn
     */
    function forMap(map, fn) {
        for (var key in map)
            if (inSet(map, key))
                fn(map[key], key);
    }

    function isEmptySet(obj) {
        for (var s in obj)
            if (inSet(obj, s))
                return false;
        return true;
    }

    /**
     * Converts keys of set into a list.
     * @template K
     * @param {Object.<K,*>} obj set.
     * @return {Array.<K>}
     */
    function setToList(obj) {
        var list = [];
        forMap(obj, function (_, s) {
            list.push(s);
        });
        return list;
    }

    /**
     * Converts subscription map to a set of lists for transmission to server.
     * @template T
     * @param {Object.<string,Object.<string,T>>} sub subscription data.
     * @return {Object.<string,Array.<string>>}>>} subscription set of lists.
     */
    function subMapToSetOfLists(sub) { // sub : (type :-> symbol :-> subItem), returns (type :-> [symbol])
        var res = {};
        forMap(sub, function (obj, type) {
            res[type] = setToList(obj);
        });
        return res;
    }

    /**
     * Converts time series subscription set into a list.
     * @template T
     * @param {Object.<string,T>} obj subscription set
     * @return {Array.<{
     *    eventSymbol: string,
     *    fromTime: number
     * }>} subscription list
     */
    function timeSeriesSubSetToList(obj) {
        var list = [];
        forMap(obj, function (subItem, symbol) {
            list.push({
                eventSymbol : symbol,
                fromTime : subItem.fromTime
            });
        });
        return list;
    }

    /**
     * Converts time series subscription map to a set of lists for transmission to server.
     * @template T
     * @param {Object.<string,Object.<string,T>>} sub subscription data
     * @return {Object.<string,Array.<(string|{
     *    eventSymbol: string,
     *    fromTime: number
     * })>>}
     */
    function timeSeriesSubMapToSetOfLists(sub) { // sub : (type :-> symbol :-> subItem), returns (type :-> [symbol])
        var res = {};
        forMap(sub, function (obj, type) {
            res[type] = timeSeriesSubSetToList(obj);
        });
        return res;
    }

    /**
     * Returns element from subscription map.
     * @template T
     * @param {Object.<string,Object<string,T>>} sub subscription map.
     * @param {string} type event type.
     * @param {string} symbol symbol.
     * @return {T|undefined}
     */
    function mapGet(sub, type, symbol) {
        return inSet(sub, type) ? sub[type][symbol] : undefined;
    }

    /**
     * Adds element to subscription map.
     * @template T
     * @param {Object.<string,Object<string,T>>} sub subscription map.
     * @param {string} type event type.
     * @param {string} symbol symbol.
     * @param {T} val value to put.
     */
    function mapPut(sub, type, symbol, val) {
        if (!inSet(sub, type))
            sub[type] = {};
        sub[type][symbol] = val;
    }

    /**
     * Puts event to type symbol map.
     *
     * @template T
     * @param {Object.<string,Object<string,Object.<number,T>>>} sub maps type :-> symbol :-> index :-> Event.
     * @param {string} type
     * @param {string} symbol
     * @param {number} index
     * @param {T} event
     */
    function mapPutTimeSeriesEvent(sub, type, symbol, index, event) {
        if (!inSet(sub, type))
            sub[type] = {};
        if (!inSet(sub[type], symbol))
            sub[type][symbol] = {};
        sub[type][symbol][index] = event;
    }

    /**
     * Removes element to subscription map.
     * @template T
     * @param {Object.<string,Object<string,T>>} sub subscription map.
     * @param {string} type event type.
     * @param {string} symbol symbol.
     */
    function mapRemove(sub, type, symbol) {
        if (inSet(sub, type))
            delete sub[type][symbol];
    }

    function argsToArray(args) {
        var res = [];
        forArray(args, function(s) {
            if (typeof s === "string")
                res.push(s);
            else
                res = res.concat(s);
        });
        return res;
    }

    // ===== public utility functions =====

    var dxSymbols = dx.symbols = dx.symbols || {}; // window.dx.symbols namespace

    // note, that empty attribute lists {} at the end are not considered valid attributes
    // just like in MarketEventSymbols.hasAttributesInternal method
    function getLengthWithoutAttributesInternal(symbol) {
        if (symbol.length < 3 || symbol[symbol.length - 1] !== "}")
            return symbol.length;
        var i = symbol.lastIndexOf("{");
        return i < 0 || i >= symbol.length - 2 ? symbol.length : i;
    }

    // see MarketEventSymbols.getKeyInternal
    function getKeyInternal(symbol, i) {
        var val = symbol.indexOf("=", i);
        return val < 0 ? null : symbol.substring(i, val);
    }

    // see MarketEventSymbols.getNextKeyInternal
    function getNextKeyInternal(symbol, i) {
        var val = symbol.indexOf("=", i) + 1;
        var sep = symbol.indexOf(",", val);
        return sep < 0 ? symbol.length : sep + 1;
    }

    // see MarketEventSymbols.dropKeyAndValueInternal
    function dropKeyAndValueInternal(symbol, length, i, j) {
        return j == symbol.length ? i == length + 1 ? symbol.substring(0, length) :
            symbol.substring(0, i - 1) + symbol.substring(j - 1) :
            symbol.substring(0, i) + symbol.substring(j);
    }

    // see MarketEventSymbols.addAttributeInternal
    function addAttributeInternal(symbol, key, value) {
        var length = getLengthWithoutAttributesInternal(symbol);
        if (length === symbol.length)
            return symbol + "{" + key + "=" + value + "}";
        var i = length + 1;
        var added = false;
        while (i < symbol.length) {
            var cur = getKeyInternal(symbol, i);
            if (cur === null)
                break;
            var j = getNextKeyInternal(symbol, i);
            if (cur === key) {
                if (added) {
                    // drop, since we've already added this key
                    symbol = dropKeyAndValueInternal(symbol, length, i, j);
                } else {
                    // replace value
                    symbol = symbol.substring(0, i) + key + "=" + value + symbol.substring(j - 1);
                    added = true;
                    i += key.length + value.length + 2;
                }
            } else if (cur > key && !added) {
                // insert value here
                symbol = symbol.substring(0, i) + key + "=" + value + "," + symbol.substring(i);
                added = true;
                i += key.length + value.length + 2;
            } else
                i = j;
        }
        return symbol;
    }

    // see MarketEventSymbols.removeAttributeInternal
    function removeAttributeInternal(symbol, key) {
        var length = getLengthWithoutAttributesInternal(symbol);
        if (length == symbol.length)
            return symbol;
        var i = length + 1;
        while (i < symbol.length) {
            var cur = getKeyInternal(symbol, i);
            if (cur === null)
                break;
            var j = getNextKeyInternal(symbol, i);
            if (cur === key)
                symbol = dropKeyAndValueInternal(symbol, length, i, j);
            else
                i = j;
        }
        return symbol;
    }

    dxSymbols.changeAttribute = function(symbol, key, value) {
        return value === undefined || value === null ?
            removeAttributeInternal(symbol, key) :
            addAttributeInternal(symbol, key, value);
    };

    // ===== Class for private endpointImpl (transport layer) objects =====

    /**
     * @constructor
     */
    function EndpointImpl() {
        var endpointImpl = this;
        var config = {};
        var authToken = null;
        var cometd = null; // current cometd instance
        var connected = false;
        var connectCalled = false; // for automatic connect on first use

        // FeedImpl attachment points
        this.onStateChange = null;
        this.onData = null;

        // ----- private endpointImpl methods -----

        function debug(msg) {
            if (config.logLevel === "debug" && console && console.log)
                console.log(msg);
        }

        function info(msg) {
            if (console && console.info)
                console.info(msg);
        }

        function warn(msg) {
            if (console && console.warn)
                console.warn(msg);
        }

        function convertToAbsoluteURL(url) {
            if (/^https?:\/\//i.test(url))
                return url;
            if (/^\/\//.test(url))
                return location.protocol + url;
            if (/^\//.test(url))
                return location.protocol + "//" + location.host + url;
            return location.protocol + "//" + location.host + location.pathname + url;
        }

        function updateConnectedState(newConnected) {
            var wasConnected = connected;
            if (wasConnected !== newConnected) {
                info(wasConnected ? "Connection lost" : "Connection established");
                connected = newConnected;
                endpointImpl.onStateChange({ connected: newConnected });
            }
        }

        function onMetaHandshake(message) {
            if (cometd === null || cometd.isDisconnected()) {
                updateConnectedState(false);
                return;
            }
            if (!message.successful) {
                info("Authentication failed or no token provided");
            }
            updateConnectedState(message.successful === true);
        }

        // Function that manages the connection status with the Bayeux server
        function onMetaConnect(message) {
            if (cometd === null || cometd.isDisconnected()) {
                updateConnectedState(false);
                return;
            }
            updateConnectedState(message.successful === true);
        }

        function onMetaUnsuccessful() {
            updateConnectedState(false)
        }

        function onServiceState(message) {
            debug("Received state " + JSON.stringify(message));
            endpointImpl.onStateChange(message.data);
        }

        function onServiceData(message) {
            debug("Received data " + JSON.stringify(message));
            endpointImpl.onData(message.data, false);
        }

        function onServiceTimeSeriesData(message) {
            debug("Received time series data " + JSON.stringify(message));
            endpointImpl.onData(message.data, true);
        }

        function connect(url) {
            connectCalled = true;
            if (!$.CometD) {
                warn("No CometD, working without connection");
                return;
            }
            if (config.url === undefined) {
                if (dx.contextPath === undefined && scriptPath != null) {
                    // guess context path relative to this script - "../../.."
                    var path = scriptPath;
                    for (var i = 0; i < 3; i++)
                        path = path.replace(/\/[^/]*$/, "")

                    dx.contextPath = path;
                }
                // default webservice path
                config.url = dx.contextPath + "/cometd";
            }
            if (typeof url === "string") {
                config.url = url;
            } else if (typeof url === "object") {
                config = $.extend(config, url);
            }
            config.url = convertToAbsoluteURL(config.url);
            info("Connecting with url: " + config.url);
            if (cometd === null) {
                cometd = new $.CometD();
                cometd.addListener("/meta/handshake", onMetaHandshake);
                cometd.addListener("/meta/connect", onMetaConnect);
                cometd.addListener("/meta/unsuccessful", onMetaUnsuccessful);
                cometd.addListener("/service/state", onServiceState);
                cometd.addListener("/service/data", onServiceData);
                cometd.addListener("/service/timeSeriesData", onServiceTimeSeriesData);
            }
            cometd.configure(config);
            if (authToken === null) {
                cometd.handshake();
            } else {
                debug("Using auth token: " + authToken);
                cometd.handshake({ ext: { "com.devexperts.auth.AuthToken": authToken }});
            }
        }

        // ----- public endpointImpl methods -----

        this.logLevel = function (level) {
            config.logLevel = level;
        };

        this.maxSendMessageSize = function (size) {
            config.maxSendBayeuxMessageSize = size;
        };

        this.setAuthToken = function (token) {
            authToken = token;
        };

        this.isConnected = function () {
            return connected;
        };

        this.connect = connect;

        this.disconnect = function () {
            if (cometd !== null) {
                info("Disconnecting");
                cometd.disconnect(true);
                cometd = null;
                connected = false;
            }
        };

        this.publish = function (service, message) {
            debug("Publishing to " + service + ": " + JSON.stringify(message));
            cometd.publish("/service/" + service, message);
        };

        this.connectIfNeeded = function () {
            if (!connectCalled)
                connect();
        };
    }

    // ===== Class for public subscription objects =====

    /**
     * Constructor for subscription objects.
     * @constructor
     * @param {FeedImpl} feedImpl FeedImpl instance.
     * @param {Array.<string>} types array of event type strings.
     * @param {boolean} timeSeries true when time series sub is created.
     */
    function Sub(feedImpl, types, timeSeries) {
        /**
         * @type {Sub}
         */
        var sub = this;   // this subscription

        /**
         * @type {Object.<string,boolean>}
         */
        var subSet = {};  // symbol :-> true

        /**
         * @type {Object.<string,Object<string,Object>>}
         */
        var queue = {};   // type :-> symbol :-> Event or type :-> symbol :-> time :-> Event for timeSeries

        var notifyTimeout = null;
        var fromTime = Number.POSITIVE_INFINITY;

        // ----- public property -----

        this.onEvent = null;

        // ----- private subscription methods -----

        function forTypes(func) {
            forArray(types, func);
        }

        function notify() {
            var onEvent = sub.onEvent;
            notifyTimeout = null;
            if (typeof onEvent === "function") {
                forMap(queue, function (val, type) {
                    if (timeSeries) {
                        forMap(val, function(val2) {
                            forMap(val2, onEvent);
                        });
                    } else
                        forMap(val, onEvent);
                    delete queue[type];
                });
            }
        }

        function notifyLater() {
            if (notifyTimeout === null)
                notifyTimeout = setTimeout(notify, 0);
        }

        function processEvent(event) {
            if (timeSeries) {
                if (event.time >= fromTime)
                    mapPutTimeSeriesEvent(queue, event.eventType, event.eventSymbol, event.index, event);
            } else
                mapPut(queue, event.eventType, event.eventSymbol, event);
            notifyLater();
        }

        function addSymbolsImpl(symbols) {
            var totalSub = timeSeries ? feedImpl.totalTimeSeriesSub : feedImpl.totalSub;
            var addSub = timeSeries ? feedImpl.addTimeSeriesSub : feedImpl.addSub;
            var removeSub = timeSeries ? feedImpl.removeTimeSeriesSub : feedImpl.removeSub;
            var added = false;
            var fire = false;
            forArray(symbols, function (symbol) {
                if (inSet(subSet, symbol))
                    return;
                subSet[symbol] = true;
                forTypes(function (type) {
                    var updated = false;
                    var item = mapGet(totalSub, type, symbol);
                    if (!item) {
                        item = timeSeries ?
                            { listeners: [], events: {}, fromTime: Number.POSITIVE_INFINITY, fromTimes: [] } :
                            { listeners: [], event: null };
                        mapPut(totalSub, type, symbol, item);
                        updated = true;
                    }
                    item.listeners.push(processEvent);
                    if (timeSeries) {
                        item.fromTimes.push(fromTime);
                        if (fromTime < item.fromTime) {
                            item.fromTime = fromTime;
                            updated = true;
                        }
                        forMap(item.events, function(event) {
                            mapPutTimeSeriesEvent(queue, type, symbol, event.index, event);
                            fire = true;
                        });
                    } else {
                        if (item.event) {
                            mapPut(queue, type, symbol, item.event);
                            fire = true;
                        }
                    }
                    if (updated) {
                        mapPut(addSub, type, symbol, timeSeries ? {fromTime: fromTime} : true);
                        mapRemove(removeSub, type, symbol);
                        added = true;
                    }
                });
            });
            if (added)
                feedImpl.sendSubLater();
            if (fire)
                notifyLater();
        }

        function removeSymbolsImpl(symbols) {
            var totalSub = timeSeries ? feedImpl.totalTimeSeriesSub : feedImpl.totalSub;
            var addSub = timeSeries ? feedImpl.addTimeSeriesSub : feedImpl.addSub;
            var removeSub = timeSeries ? feedImpl.removeTimeSeriesSub : feedImpl.removeSub;
            var removed = false;
            forArray(symbols, function (symbol) {
                if (!inSet(subSet, symbol))
                    return;
                delete subSet[symbol];
                forTypes(function (type) {
                    mapRemove(queue, type, symbol);
                    var item = mapGet(totalSub, type, symbol);
                    if (item) {
                        item.listeners = removeFromArray(item.listeners, processEvent);
                        if (item.listeners.length === 0) {
                            mapRemove(totalSub, type, symbol);
                            mapRemove(addSub, type, symbol);
                            mapPut(removeSub, type, symbol, true);
                            removed = true;
                        } else if (timeSeries) {
                            item.fromTimes = removeFromArray(item.fromTimes, fromTime);
                            var newFromTime = Number.POSITIVE_INFINITY;
                            forArray(item.fromTimes, function(time) {
                                if (time < newFromTime)
                                    newFromTime = time;
                            });
                            if (item.fromTime !== newFromTime) {
                                item.fromTime = newFromTime;
                                mapPut(addSub, type, symbol, {fromTime: newFromTime});
                                mapRemove(removeSub, type, symbol);
                                removed = true;
                                forMap(item.events, function(event) {
                                    if (event.time < newFromTime)
                                        delete event.time[event.time];
                                });
                            }
                        }
                    }
                });
            });
            if (removed)
                feedImpl.sendSubLater();
        }

        function setSymbolsImpl(symbols) {
            var addList = [];
            var removeList = [];
            var newSet = {};
            forArray(symbols, function (symbol) {
                if (inSet(newSet, symbol))
                    return;
                newSet[symbol] = true;
                if (!inSet(subSet, symbol))
                    addList.push(symbol);
            });
            forMap(subSet, function (_, symbol) {
                if (!inSet(newSet, symbol))
                    removeList.push(symbol);
            });
            removeSymbolsImpl(removeList);
            addSymbolsImpl(addList);
        }

        function setFromTimeImpl(time) {
            if (typeof time === "string")
                time = new Date(time).getTime();
            if (typeof time === "object")
                time = time.getTime();
            if (typeof time !== "number" || isNaN(time)) {
                feedImpl.endpointImpl.warn("setFromTime is ignored because of invalid time " + time);
                return;
            }
            var subList = setToList(subSet);
            removeSymbolsImpl(subList);
            fromTime = time;
            addSymbolsImpl(subList);
        }

        function nop() {
        } // for closed subs

        // ----- public subscription methods -----

        this.addSymbols = function () {
            addSymbolsImpl(argsToArray(arguments));
        };

        this.removeSymbols = function () {
            removeSymbolsImpl(argsToArray(arguments));
        };

        this.setSymbols = function () {
            setSymbolsImpl(argsToArray(arguments));
        };

        this.close = function () {
            removeSymbolsImpl(setToList(subSet));
            sub.addSymbols = nop;
            sub.removeSymbols = nop;
            sub.setSymbols = nop;
            sub.close = nop;
        };

        if (timeSeries) {
            this.setFromTime = setFromTimeImpl;
        }
    }

    // ===== Class for private feedImpl objects =====

    /**
     * Constructor for private feedImpl object
     * @constructor
     * @param {EndpointImpl} endpointImpl private EndpointImpl (transport layer) instance
     * @param state public object to monitor state
     */
    function FeedImpl(endpointImpl) {
        var feedImpl = this; // this feedImpl
        var resetSub = false;
        var sendSubTimeout = null;
        var dataScheme = {}; // eventType :-> Array of event property names

        // publicly visible state
        this.state = {
            onChange : null,
            connected : false,
            replaySupported : undefined, // will determined after connection
            replay : false,
            clear : false,
            time : 0,
            speed : 0
        };

        /**
         * Total subscription and saved events.
         * @type {Object.<string,Object.<string,{
         *    listeners: Array.<function(event: Object)>
         *    event: Object,
         * }>>}
         */
        this.totalSub = {};  // type :-> symbol :-> subItem

        /**
         * Subscription to be added.
         * @type {Object.<string,Object.<string,boolean>>}
         */
        this.addSub = {};    // type :-> symbol :-> boolean

        /**
         * Subscription to be removed.
         * @type {Object.<string,Object.<string,boolean>>}
         */
        this.removeSub = {}; // type :-> symbol :-> boolean

        /**
         * Total time series subscription and saved events.
         * @type {Object.<string,Object.<string,{
         *    listeners: Array.<function(event: Object)>
         *    events: Object.<number,Object>,
         *    fromTime: number,
         *    fromTimes: Array.<number>
         * }>>}
         */
        this.totalTimeSeriesSub = {};

        /**
         * Time series subscription to be added.
         * @type {Object.<string,Object.<string,{fromTime: number}>>}
         */
        this.addTimeSeriesSub = {};

        /**
         * Time series subscription to be removed.
         * @type {Object.<string,Object.<string,boolean>>}
         */
        this.removeTimeSeriesSub = {};

        this.endpointImpl = endpointImpl;

        // ----- proxy for onDemand service control methods -----

        var onDemand = {};

        forArray(["replay", "setSpeed", "stopAndResume", "stopAndClear"],
            function(method) {
                onDemand[method] = function() {
                    var args = Array.prototype.slice.call(arguments, 0); // covert args to regular array
                    endpointImpl.connectIfNeeded();
                    endpointImpl.publish("onDemand", {
                        op: method,
                        args: args
                    });
                };
            }
        );

        // ----- private feedImpl methods -----

        function onStateChange(stateChange) {
            if (stateChange.connected) {
                resetSub = true;
                sendSubLater();
            }
            forMap(stateChange, function(val, key) {
                feedImpl.state[key] = val;
            });
            if (typeof feedImpl.state.onChange === "function")
                feedImpl.state.onChange(stateChange);
        }

        function stateChangeAction(stateChange, shouldSignalResume) {
            if (feedImpl.state.clear)
                onDemand.stopAndClear();
            else if (feedImpl.state.replay) {
                if (typeof stateChange.time !== "undefined")
                    onDemand.replay(feedImpl.state.time, feedImpl.state.speed);
                else
                    onDemand.setSpeed(feedImpl.state.speed);
            } else if (shouldSignalResume)
                onDemand.stopAndResume();
        }

        function changeState(stateChange) {
            onStateChange(stateChange);
            stateChangeAction(stateChange, true);
        }

        function sendSub() {
            sendSubTimeout = null;
            endpointImpl.connectIfNeeded();
            if (endpointImpl.isConnected()) {
                var subMessage = {};
                var addSub = feedImpl.addSub;
                var removeSub = feedImpl.removeSub;
                var addTimeSeriesSub = feedImpl.addTimeSeriesSub;
                var removeTimeSeriesSub = feedImpl.removeTimeSeriesSub;
                feedImpl.addSub = {};
                feedImpl.removeSub = {};
                feedImpl.addTimeSeriesSub = {};
                feedImpl.removeTimeSeriesSub = {};
                if (resetSub) {
                    stateChangeAction(feedImpl.state, false); // restore onDemand state if needed
                    subMessage.reset = true;
                    addSub = feedImpl.totalSub;
                    removeSub = {};
                    addTimeSeriesSub = feedImpl.totalTimeSeriesSub;
                    removeTimeSeriesSub = {};
                    resetSub = false;
                }
                if (!isEmptySet(addSub))
                    subMessage.add = subMapToSetOfLists(addSub);
                if (!isEmptySet(removeSub))
                    subMessage.remove = subMapToSetOfLists(removeSub);
                if (!isEmptySet(addTimeSeriesSub))
                    subMessage.addTimeSeries = timeSeriesSubMapToSetOfLists(addTimeSeriesSub);
                if (!isEmptySet(removeTimeSeriesSub))
                    subMessage.removeTimeSeries = subMapToSetOfLists(removeTimeSeriesSub);
                if (!isEmptySet(subMessage))
                    endpointImpl.publish("sub", subMessage);
            }
        }

        function sendSubLater() {
            if (sendSubTimeout === null)
                sendSubTimeout = setTimeout(sendSub, 0);
        }

        /**
         * @param {Array} data -- packed data array
         * @param {boolean} timeSeries
         */
        function onData(data, timeSeries) {
            var totalSub = timeSeries ? feedImpl.totalTimeSeriesSub : feedImpl.totalSub;
            var type = data[0];
            var props;
            if (typeof type === "string")
                props = dataScheme[type];
            else {
                props = type[1];
                type = type[0];
                dataScheme[type] = props;
            }
            if (!inSet(totalSub, type))
                return;
            var values = data[1];
            var nProps = props.length;
            var tsub = totalSub[type];
            for (var i = 0; i < values.length; i += nProps) {
                var event = {};
                for (var j = 0; j < nProps; j++)
                    event[props[j]] = values[i + j];
                var symbol = event.eventSymbol;
                var item;
                if (inSet(tsub, symbol)) {
                    item = tsub[symbol];
                    event.eventType = type;
                    if (timeSeries) {
                        item.events[event.index] = event;
                    } else
                        item.event = event;
                    forArray(item.listeners, function(listener) {
                        listener(event);
                    });
                }
            }
        }

        // ----- public feedImpl methods -----

        this.changeState = changeState;
        this.sendSubLater = sendSubLater;
        this.createSubscription = function () { return new Sub(feedImpl, argsToArray(arguments), false); };
        this.createTimeSeriesSubscription = function () { return new Sub(feedImpl, argsToArray(arguments), true); };


        // ----- attach to endpoint -----

        endpointImpl.onStateChange = onStateChange;
        endpointImpl.onData = onData;
    }

    // ===== Class for public feed objects =====

    dx.createFeed = function() {
        var endpointImpl = new EndpointImpl();
        var feedImpl = new FeedImpl(endpointImpl);

        // ----- public feed methods -----

        var feed = {
            state : feedImpl.state,

            logLevel : function (level) {
                endpointImpl.logLevel(level);
                return feed;
            },

            maxSendMessageSize : function (size) {
                endpointImpl.maxSendMessageSize(size);
                return feed;
            },

            setAuthToken : function (token) {
                endpointImpl.setAuthToken(token);
                return feed;
            },

            connect : function(url) {
                endpointImpl.connect(url);
                return feed;
            },

            disconnect : function() {
                endpointImpl.disconnect();
                return feed;
            },

            createSubscription : feedImpl.createSubscription,
            createTimeSeriesSubscription : feedImpl.createTimeSeriesSubscription,

            replay : function(time, speed) {
                feedImpl.changeState({
                    replay : true,
                    clear : false,
                    time : time,
                    speed : typeof speed === "undefined" ? 1 : speed
                });
                return feed;
            },

            pause : function() {
                return feed.setSpeed(0);
            },

            setSpeed : function(speed) {
                feedImpl.changeState({
                    replay : true,
                    clear : false,
                    speed : speed
                });
                return feed;
            },

            stopAndResume : function() {
                feedImpl.changeState({
                    replay : false,
                    clear : false,
                    speed : 0
                });
                return feed;
            },

            stopAndClear : function() {
                feedImpl.changeState({
                    replay : false,
                    clear : true,
                    speed : 0
                });
                return feed;
            }
        };

        return feed;
    };

    // default Feed instance
    dx.feed = dx.createFeed();

    // Disconnect default instance when the page unloads

    $(window).unload(function() {
        dx.feed.disconnect();
    });

}(jQuery));
