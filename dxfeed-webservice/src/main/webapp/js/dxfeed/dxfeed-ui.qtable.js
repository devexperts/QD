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

(function($) {
    "use strict";

    // ensure dx namespace
    var dx = window.dx = window.dx || {};
    // ensure dx.widget namespace ==> all widgets
    var widget = dx.widget = dx.widget || {};
    // ensure dx.jqExtFn namespace ==> jQuery extensions
    var jqExtFn = dx.jqExtFn = dx.jqExtFn || {};
    // user column definitions { <name> : { deps: Array, fn: Function }}
    var userDef = {};

    // mark reserved userDef names (see https://developer.mozilla.org/en-US/docs/JavaScript/Reference/Reserved_Words)
    reserveUserDefs("dx", "undefined", "null", "true", "false", "break", "case", "catch", "continue", "debugger",
        "default", "delete", "do", "else", "finally", "for", "function", "if", "in", "instanceof", "new", "return",
        "switch", "this", "throw", "try", "typeof", "var", "void", "while", "with", "class", "enum", "export",
        "extends", "import", "super", "implements", "interface", "let", "package", "private", "protected",
        "public", "static", "yield", "const");

    // ===== private utility functions =====

    var hasOwnProperty = Object.prototype.hasOwnProperty;

    function inSet(key, obj) {
        return hasOwnProperty.call(obj, key);
    }

    function ensure(obj, key) {
        return inSet(key, obj) ? obj[key] : (obj[key] = {});
    }

    function reserveUserDefs() {
        forArray(arguments, function (name) {
            userDef[name] = false;
        });
    }

    function warn(msg) {
        if (console && console.warn)
            console.warn(msg);
    }

    function forArray(a, fn) {
        for (var i = 0, n = a.length; i < n; i++)
            fn(a[i], i);
    }

    function forMap(map, fn) {
        for (var key in map)
            if (inSet(key, map))
                fn(map[key], key);
    }

    function isEmptySet(obj) {
        for (var s in obj)
            if (inSet(s, obj))
                return false;
        return true;
    }

    function setToList(obj) {
        var list = [];
        forMap(obj, function (_, s) {
            list.push(s);
        });
        return list;
    }

    function listToSet(list) {
        var obj = {};
        forArray(list, function (s) {
            obj[s] = true;
        });
        return obj;
    }

    function addAllFromSet(obj, fromSet) {
        forMap(fromSet, function (_, s) {
            obj[s] = true;
        });
    }

    function isUserDefName(ref) {
        return /^[a-z]\w*$/.test(ref);
    }

    function isEventFieldRef(ref) {
        return /^[A-Z]\w*\.[a-z]\w*$/.test(ref);
    }

    function eventFieldRefParts(ref) {
        var i = ref.indexOf(".");
        return {
            type : ref.substr(0, i),
            field : ref.substr(i + 1)
        };
    }

    function isInlineCode(ref) {
        // simplified expression that does not ensure that tokens are either userDefNames or eventFieldRefs
        return /^[\s\w.]+\|/.test(ref);
    }

    function isInlineFormat(ref) {
        // word : string or number
        return /\|\s*\w+\s*:\s*("\[^"]*"|'[^']'|[+-]?\d*\.?\d*([eE][+-]?\d+)?)\s*$/.test(ref)
    }

    // ===== a way to call 'new Function(...)' with array of args =====

    function FunctionConstructor(args) {
        return Function.apply(this, args);
    }

    FunctionConstructor.prototype = Function.prototype;

    function createFunction(args) {
        return new FunctionConstructor(args);
    }

    // ===== qtable widget =====

    /**
     * Real-time quote table for a specified "table" DOM element and dxFeed instance.
     * @param tableDom DOM element.
     * @param feed dxFeed API object
     */
    widget.qtable = function (tableDom, feed) {
        var $table = $(tableDom);
        var eventEffects = {}; // type :-> field :-> { bindIs : [colIndexes], classIs : [colIndexes], affects: {useDef name :-> true} }
        var userDefEffects = {}; // name :-> { bindIs : [colIndexes], classIs : [colIndexes], affects: {userDef name :-> true} }
        var boundColClasses = {}; // colId :-> (name or EventType-field String)
        var boundEffects = {}; // colId ;-> Effect
        var rows = {}; // symbol :-> { cells: colIndex :-> { $obj : jQuery, lastBind : String, lastClass : String }, data: type :-> field :-> value }
        var symbolColIndex = null;
        var nCols = 0;
        var anonEffectIndex = 0;  // current index of anonymous effects
        var symbolInputs = []; // int :-> { $input: jQuery, row : as in rows, ... } // an array of all "input" elements with symbols
        var inputsBySymbol = {}; // Symbol :-> int :-> true // maps a symbol to set of symbol input indices from symbolInputs
        var sub;

        // ----- private QTable methods -----

        function newEffect(ref, dataFn, isAnonymous) {
            return {
                refName : ref,
                dataFn : dataFn,
                isAnonymous : isAnonymous,
                bindIs : [],
                classIs : [],
                affects : []
            };
        }

        function getOrCreateEffect(ref) {
            var effect, r, def, eventEffectsType;
            if (isUserDefName(ref) || /^_\d+$/.test(ref)) {
                // depends on other user-defined or on anonymous effect
                effect = userDefEffects[ref];
                if (!effect) {
                    def = userDef[ref];
                    // bind to user-defined data
                    if (def) {
                        effect = newEffect(ref, def.dataFn, false);
                        userDefEffects[ref] = effect;
                        forArray(def.deps, function (dep) {
                            getOrCreateEffect(dep).affects[ref] = true;
                        });
                    } else
                        warn("Data item '" + ref + "' is not defined");
                }
            } else if (isEventFieldRef(ref)) {
                // depends on event field
                r = eventFieldRefParts(ref);
                eventEffectsType = ensure(eventEffects, r.type);
                effect = eventEffectsType[r.field];
                if (!effect) {
                    effect = newEffect(ref, null, false);
                    eventEffectsType[r.field] = effect;
                }
            } else
                warn("Attribute must reference user-defined data item or EventType.field, but '" + ref + "' is found");
            if (!effect)
                effect = newEffect(ref, function() {}, true); // dummy for undefined refs
            return effect;
        }

        function createAnonEffect(deps, dataFn) {
            var ref = "_" + (anonEffectIndex++);
            var effect = newEffect(ref, dataFn, true);
            userDefEffects[ref] = effect;
            forArray(deps, function (dep) {
                getOrCreateEffect(dep).affects[ref] = true;
            });
            return effect;
        }

        function compileInlineFormatFn(fmtOptions, dataFn) {
            return function (data) {
                return dx.format(dataFn(data), fmtOptions);
            };
        }

        function parseInlineFormat(code) {
            if (!isInlineFormat(code))
                return { code: code };
            var fmtOptions = {};
            var i, fmtParts, key, value;
            do {
                i = code.lastIndexOf("|");
                fmtParts = code.substr(i + 1).split(":", 2);
                code = code.substr(0, i);
                key = $.trim(fmtParts[0]);
                value = $.trim(fmtParts[1]);
                if (dx.format.supports[key]) {
                    fmtOptions[key] = JSON.parse(value);
                } else
                    warn("Reference to unsupported format option '" + key + "' is found");
            } while (isInlineFormat(code));
            if (code.indexOf("|") < 0) {
                code = $.trim(code);
                if (!isUserDefName(code) && !isEventFieldRef(code)) {
                    warn("Inline format must reference one user-defined data item or EventType.field, but '" + code + "' is found");
                    code = code + "|undefined";
                } else
                    code = code + "|" + code;
            }
            return {
                code: code,
                fmtOptions: fmtOptions
            };
        }

        function compileInlineCodeEffect(code) {
            var parsedFmt = parseInlineFormat(code);
            var i = parsedFmt.code.indexOf("|");
            var deps = $.trim(parsedFmt.code.substr(0, i)).split(/\s+/);
            var expr = $.trim(parsedFmt.code.substr(i + 1));
            var userArgs = [];
            var bridgeArgs = [];
            var bridgeCode = "";
            var objs = {};
            var userFn, bridgeFn, dataFn;
            forArray(deps, function (dep) {
                var r;
                if (isEventFieldRef(dep)) {
                    r = eventFieldRefParts(dep);
                    if (!objs[r.type]) {
                        objs[r.type] = {};
                        userArgs.push(r.type);
                        bridgeArgs.push(r.type);
                    }
                    objs[r.type][r.field] = true;
                } else if (isUserDefName(dep)) {
                    userArgs.push(dep);
                    bridgeArgs.push("data." + dep);
                } else {
                    warn("Dependency must reference user-defined data item or EventType.field, but '" + dep + "' is found");
                }
            });
            forMap(objs, function (fields, type) {
                bridgeCode += "var " + type + "={";
                forMap(fields, function (_, field) {
                    bridgeCode += field + ":data." + type + "." + field + ",";
                });
                bridgeCode += "};";
            });
            bridgeCode += "return fn(" + bridgeArgs.join(",") + ")";
            bridgeFn = new Function("fn", "data", bridgeCode);
            userArgs.push("return " + expr);
            try {
                userFn = createFunction(userArgs);
            } catch (e) {
                warn("Failed to compile inline code expr '" + expr + "': " + e);
                return createAnonEffect([], function () {}); // return dummy
            }
            dataFn = function (data) {
                return bridgeFn(userFn, data);
            };
            if (parsedFmt.fmtOptions)
                dataFn = compileInlineFormatFn(parsedFmt.fmtOptions, dataFn);
            return createAnonEffect(deps, dataFn);
        }

        function applyCellChangeAttr(effect, i) {
            // create anonymous effect that depends on bound reference ('dx-bind') and this one
            createAnonEffect([effect.refName, boundEffects[i].refName], function (rowData, rowCells) {
                // invoke change dataFn with this set to dom of the particular cell
                effect.dataFn.call(rowCells[i].$obj[0], rowData);
            });
        }

        function applyCellAttr(attrName, attrVal, i) {
            var effect;
            if (!attrVal)
                return; // nothing to do
            switch (attrName) {
            case "dx-bind":
                if (isInlineCode(attrVal))
                    effect = compileInlineCodeEffect(attrVal);
                else
                    effect = getOrCreateEffect(attrVal);
                effect.bindIs.push(i);
                boundEffects[i] = effect; // store bound effect for 'dx-change'
                break;
            case "dx-class":
                if (isInlineCode(attrVal)) {
                    effect = compileInlineCodeEffect(attrVal);
                    effect.classIs.push(i);
                } else
                    forArray(attrVal.split(/\s+/), function (aVal) {
                        getOrCreateEffect(aVal).classIs.push(i);
                    });
                break;
            case "dx-change":
                if (!boundEffects[i])
                    break; // don't do dx-change without dx-bind (nothing changes!)
                if (isInlineCode(attrVal))
                    applyCellChangeAttr(compileInlineCodeEffect(attrVal), i);
                else
                    forArray(attrVal.split(/\s+/), function (aVal) {
                        applyCellChangeAttr(getOrCreateEffect(aVal), i);
                    });
                break;
            }
        }

        function initCellAttr(attrName, $cell, i) {
            var attrVal = $cell.attr(attrName);
            applyCellAttr(attrName, attrVal, i);
            if (attrVal && i >= nCols) {
                // this is new header column!
                nCols = i + 1;
                // apply attributes inherited from table to this after we apply attribute.
                // Note, that we apply 'dx-bind' first, so it is already bound when we are applying 'dx-change'
                applyCellAttr("dx-class", $table.attr("dx-class"), i);
                applyCellAttr("dx-change", $table.attr("dx-change"), i);
            }
        }

        function initHeaderRow(cells) {
            forArray(cells, function(cell, i) {
                var $cell = $(cell);
                var bindAttr = $cell.attr("dx-bind");
                var r;
                if (bindAttr === "eventSymbol") {
                    if (symbolColIndex !== null)
                        warn("Duplicate 'dx-bind' for 'symbol' column");
                    else
                        symbolColIndex = i;
                } else if (bindAttr) {
                    if (isEventFieldRef(bindAttr)) {
                        r = eventFieldRefParts(bindAttr);
                        boundColClasses[i] = "dx-bound dx-bound-" + r.type + "-" + r.field;
                    } else if (isUserDefName(bindAttr))
                        boundColClasses[i] = "dx-bound dx-bound-" + bindAttr;
                    else
                        boundColClasses[i] = "dx-bound";
                    // NOTE: must apply 'dx-bind' before 'dx-change'
                    initCellAttr("dx-bind", $cell, i);
                }
                initCellAttr("dx-class", $cell, i);
                initCellAttr("dx-change", $cell, i);
            });
        }

        function createEmptyEvent(type, symbol) {
            var event = {
                eventType : type,
                eventSymbol : symbol
            };
            forMap(eventEffects[type], function (_, field) {
                event[field] = undefined;
            });
            return event;
        }

        function updateSymbols() {
            var symbols = setToList(rows);
            // pseudo-symbol for symbol input
            forArray(symbolInputs, function (input) {
                var symbol = input.$input.val();
                if (symbol !== input.lastSymbol) {
                    if (input.lastSymbol) {
                        // clear old values
                        forMap(eventEffects, function (_, type) {
                            processEventInRow(createEmptyEvent(type, input.lastSymbol), input.row);
                        });
                        // now kill it from inputsBySymbol
                        delete inputsBySymbol[input.lastSymbol][input.index];
                        if (isEmptySet(inputsBySymbol[input.lastSymbol]))
                            delete inputsBySymbol[input.lastSymbol];
                    }
                    input.lastSymbol = symbol;
                    if (symbol) {
                        ensure(inputsBySymbol, symbol)[input.index] = true;
                    }
                }
                if (symbol)
                    symbols.push(symbol);
            });
            sub.setSymbols(symbols);
        }

        function initSymbolInput($cell, rowObj) {
            var $input = $cell.find("input[type=text]");
            var index = symbolInputs.length;
            if ($input.length !== 1)
                return false; // must be exactly one
            symbolInputs.push({
                $input: $input,
                index: index,
                lastSymbol : "",
                row: rowObj
            });
            $input.on("keyup.qtable", updateSymbols);
            return true;
        }

        function initRow() {
            var $row = $(this);
            var cells = $row.find("td,th");
            var rowObj = { // will store info about bound row
                cells : {},
                data : {}
            };
            var symbol, i, $cell, boundColClass;

            initHeaderRow(cells);

            // determine symbol
            if (symbolColIndex === null)
                return; // symbol is not bound -- skip row
            $cell = $(cells[symbolColIndex]);
            if (!$cell.is("td"))
                return; // only bind to rows with "td" symbol
            symbol = $cell.text();
            if (!symbol) {
                // maybe there's an input of type text in there
                if (!initSymbolInput($cell, rowObj))
                    return; // no symbol in this row at all
            } else if (rows[symbol]) {
                warn("Duplicated symbol '" + symbol + "'");
                return;
            } else {
                // initialize fresh object
                rows[symbol] = rowObj;
            }

            // add missing cells
            for (i = cells.length; i < nCols; i++)
                $row.append("<td></td>");
            // rebuild cell list
            cells = $row.find("td,th");
            // store cells and set their css classes
            forArray(cells, function(cell, i) {
                var $cell = $(cell);
                if (!$cell.is("td"))
                    return; // only bind data to "td" elements
                rowObj.cells[i] = {
                    $obj : $cell,
                    lastClasses : {} // map for for each dx-class ref
                };
                boundColClass = boundColClasses[i];
                if (boundColClass)
                    $cell.addClass(boundColClass);
            });
            // prepare empty data array
            forMap(eventEffects, function (_, type) {
                rowObj.data[type] = createEmptyEvent(type, symbol);
            });
        }

        // helper function to update value and fire change event if needed
        function applyEffect(row, effect, value) {
            forArray(effect.bindIs, function (i) {
                var cell = row.cells[i];
                var $cell = cell.$obj;
                var str;
                switch (typeof value) {
                case "number":
                    str = isNaN(value) ? "" : value.toString();
                    break;
                case "string":
                    str = value;
                    break;
                default: // empty string for everything else. that is our default
                    str = "";
                    break;
                }
                $cell.text(str);
            });
            forArray(effect.classIs, function (i) {
                var cell = row.cells[i];
                var $cell = cell.$obj;
                var oldClass = cell.lastClasses[effect.refName];
                var newClass = "" + value;
                if (newClass !== oldClass) {
                    cell.lastClasses[effect.refName] = newClass;
                    if (oldClass)
                        $cell.removeClass(oldClass);
                    if (newClass)
                        $cell.addClass(newClass);
                }
            });
        }

        function processEventInRow(event, row) {
            var type = event.eventType;
            var rowCells = row.cells;
            var rowData = row.data;
            var rowEvent = rowData[type];
            var fieldEffects = eventEffects[type];
            var updatedUserDefSet = {}; // name :-> true
            var changes = true;

            // see what event props have changed and process them
            forMap(event, function (value, field) {
                var oldValue = rowEvent[field];
                var effect;
                if (value !== oldValue) {
                    rowEvent[field] = value;
                    effect = fieldEffects[field];
                    if (effect) {
                        applyEffect(row, effect, value);
                        addAllFromSet(updatedUserDefSet, effect.affects);
                    }
                }
            });

            // loop while we have updatedUserDefSet to process
            while (changes) {
                changes = false;
                forMap(updatedUserDefSet, function (_, name) {
                    var value, effect;
                    changes = true;
                    delete updatedUserDefSet[name];
                    try {
                        effect = userDefEffects[name];
                        value = effect.dataFn.call(null, rowData, rowCells);  // rowCells are used internally
                        if (!effect.isAnonymous)
                            rowData[name] = value;
                        applyEffect(row, effect, value);
                        addAllFromSet(updatedUserDefSet, effect.affects);
                    } catch (e) {
                        warn("User defined function '" + name + "' threw exception: " + e);
                    }
                });
            }
        }

        function onEvent(event) {
            var symbol = event.eventSymbol;
            if (rows[symbol])
                processEventInRow(event, rows[symbol]);
            if (inputsBySymbol[symbol])
                forMap(inputsBySymbol[symbol], function (_, index) {
                    processEventInRow(event, symbolInputs[index].row);
                });
        }

        // ----- QTable init code -----

        // scan all table rows
        $table.children().children("tr").each(initRow);

        // create subscription
        sub = feed.createSubscription(setToList(eventEffects));
        sub.onEvent = onEvent;
        updateSymbols();

        // ----- define public qtable methods -----

        function detach() {
            sub.close();
            forArray(symbolInputs, function (input) {
                input.$input.off(".qtable"); // remove all our event handlers
            });
        }

        return {
            detach : detach
        };
    };

    // ===== jQuery extension =====
    // register $(...).dx('name', ...) call rerouting function
    // See http://docs.jquery.com/Plugins/Authoring
    $.fn.dx = function(name) {
        var fn = jqExtFn[name];
        if (fn)
            return fn.apply(this, Array.prototype.slice.call(arguments, 1));
        else {
            warn("'$(...).dx('" + name + "') is not found");
            return this;
        }
    };

    jqExtFn.init = function (feed) {
        if (!feed)
            feed = dx.feed;
        return this.each(function() {
            var $this = $(this);
            var widgetName = $this.attr("dx-widget");
            var widgetInstance = $this.data("dx-widget");
            if (widgetName && widget[widgetName] && !widgetInstance) {
                widgetInstance = widget[widgetName](this, feed);
                $this.data("dx-widget", widgetInstance);
            }
        });
    };

    jqExtFn.detach = function () {
        return this.each(function() {
            var $this = $(this);
            var widgetName = $this.attr("dx-widget");
            var widgetInstance = $this.data("dx-widget");
            if (widgetName && widget[widgetName] && widgetInstance) {
                widgetInstance.detach();
                $this.data("dx-widget", null);
            }
        });
    };

    // ===== public methods in dx namespace =====

    /**
     * Configurable formatter for numbers.
     * @param val      number to format.
     * @param options  object with format options {
     *    maximumFractionDigits : Number, // limits max fraction digits (does not coerce result to string)
     *    minimumFractionDigits : Number, // limits min fraction digits (coerces result to string)
     *    groupingSeparator : String      // use specified thousands separator (coerces result to string)
     * }
     */
    dx.format = function (val, options) {
        switch (typeof val) {
        case "undefined":
        case "null":
        case "object":
            return val;
        case "number":
            if (isNaN(val))
                return val;
        }
        // actually format
        var pow, sep, buf, i, n;
        var str = null;
        if (inSet("maxFractionDigits", options)) {
            pow = Math.pow(10, options["maxFractionDigits"]);
            val = Math.round(val * pow) / pow;
        }
        if (inSet("minFractionDigits", options)) {
            n = options["minFractionDigits"];
            if (str === null)
                str = val.toString();
            if (!/e/.test(str)) {
                i = str.indexOf(".");
                if (i < 0) {
                    i = str.length;
                    str = str + ".";
                }
                while (str.length < i + n + 1)
                    str = str + "0";
            }
        }
        if (inSet("groupingSeparator", options)) {
            sep = options["groupingSeparator"];
            if (str === null)
                str = val.toString();
            if (!/e/.test(str)) {
                buf = "";
                i = str.indexOf(".");
                if (i < 0)
                    i = str.length - 3;
                else {
                    buf = str.substr(i);
                    i -= 3;
                }
                for (; i > 0; i -= 3)
                    buf = sep + str.substr(i, 3) + buf;
                str = str.substr(0, i + 3) + buf;
            }
        }
        return str === null ? val : str;
    };

    dx.format.supports = listToSet(["maxFractionDigits", "minFractionDigits", "groupingSeparator"]);

    /**
     * Defines user column function
     * @param name column name.
     * @param deps array of dependency names.
     * @param fn   function that takes dependency values as arguments and produces value.
     */
    dx.define = function (name, deps, fn) {
        if (!isUserDefName(name)) {
            warn("User-defined data item name must start with a lowercase letter and constitute a word, but '" + name + "' is found");
            return;
        }
        // also check for reserved mappings to "false"
        // Note, that we explicitly use "in" operator here to also make sure that you cannot use a name of any
        // method defined in Object.prototype as a userDef name to avoid any confusion.
        if (name in userDef) {
            warn("Data item '" + name + "' is already defined or reserved, cannot redefine");
            return;
        }
        var args = "";
        forArray(deps, function (dep) {
            if (isUserDefName(dep)) {
                if (!userDef[dep]) {
                    warn("Data item '" + dep + "' is not defined, referenced from '" + name + "'");
                    args += ",undefined";
                } else
                    args += ",data." + dep;
            } else if (isEventFieldRef(dep)) {
                // Ok
                args += ",data." + dep;
            } else {
                warn("Dependency must reference user-defined data item or EventType.field, but '" + dep + "' is found");
                args += ",undefined";
            }
        });
        // Pass-through via all levels to support dx-change
        var applyFn = new Function("fn", "data", "return fn.call(this" + args + ")");
        // Bind to the user-defined function and store definition
        userDef[name] = {
            deps : deps,
            dataFn : function (data) {
                return applyFn.call(this, fn, data);
            }
        };
    };

    // Initialize all table elements with 'dx-widget' attribute on document ready
    $(function () {
        $("table[dx-widget]").dx("init");
    });
}(jQuery));
