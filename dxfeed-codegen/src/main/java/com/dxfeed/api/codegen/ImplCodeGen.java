/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

import com.devexperts.qd.QDContract;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleEventDelegateImpl;
import com.dxfeed.event.candle.DailyCandle;
import com.dxfeed.event.candle.impl.CandleEventMapping;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.MarketEventDelegateImpl;
import com.dxfeed.event.market.MarketMaker;
import com.dxfeed.event.market.OptionSale;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderBase;
import com.dxfeed.event.market.OrderBaseDelegateImpl;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.SpreadOrder;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.market.TradeETH;
import com.dxfeed.event.market.impl.MarketEventMapping;
import com.dxfeed.event.market.impl.OrderBaseMapping;
import com.dxfeed.event.misc.Configuration;
import com.dxfeed.event.misc.Message;
import com.dxfeed.event.misc.TextMessage;
import com.dxfeed.event.option.Greeks;
import com.dxfeed.event.option.Series;
import com.dxfeed.event.option.TheoPrice;
import com.dxfeed.event.option.Underlying;

import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Main class to generate all implementation-related code for dxFeed.
 *
 * <p>You can run this with this maven command from the root of the project:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.dxfeed.api.codegen.ImplCodeGen" -pl :dxfeed-codegen
 * </pre>
 */
// CHECKSTYLE:OFF
public class ImplCodeGen {

    private static final ClassName MARKET_EVENT_DELEGATE = new ClassName(MarketEventDelegateImpl.class);
    private static final ClassName CANDLE_EVENT_DELEGATE = new ClassName(CandleEventDelegateImpl.class);
    private static final ClassName ORDER_BASE_DELEGATE = new ClassName(OrderBaseDelegateImpl.class);

    private static final ClassName MARKET_EVENT_MAPPING = new ClassName(MarketEventMapping.class);
    private static final ClassName CANDLE_EVENT_MAPPING = new ClassName(CandleEventMapping.class);
    private static final ClassName ORDER_BASE_MAPPING = new ClassName(OrderBaseMapping.class);

    private static final String TRADE_RECORD_SUFFIXES = "" +
        "133ticks|144ticks|233ticks|333ticks|400ticks|512ticks|1600ticks|3200ticks|" +
        "1min|2min|3min|4min|5min|6min|10min|12min|15min|20min|30min|" +
        "1hour|2hour|3hour|4hour|6hour|8hour|12hour|Day|2Day|3Day|4Day|Week|Month|OptExp";
    private static final String BID_ASK_VOLUME_SUFFIXES = ".*[{,]price=(bid|ask|mark|s)[,}].*";

    private static final String DXSCHEME_FOB = "dxscheme.fob";
    private static final String FOB_SUFFIX_PROPERTY = "com.dxfeed.event.market.impl.Order.fob.suffixes";
    private static final String FOB_SUFFIX_DEFAULT = getFullOrderBookSuffixes();

    public static void main(String[] args) throws IOException {
        new ImplCodeGen("", false).run();
    }

    // ------------ instance ------------

    private final String rootDir;
    private final boolean verifyOnly;

    public ImplCodeGen(String rootDir, boolean verifyOnly) {
        this.rootDir = rootDir;
        this.verifyOnly = verifyOnly;
    }

    public void run() throws IOException {
        runForDxfeedImpl();
    }

    public void runForDxfeedImpl() throws IOException {
        Config config = new Config(rootDir, Config.DXFEED_IMPL, verifyOnly);
        CodeGenContext ctx = new CodeGenContext(new ExecutableEnvironment(config));

        ctx.delegate("Quote", Quote.class, "Quote&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            map("Sequence", FieldType.SEQUENCE).optional().disabledByDefault().internal(). // assign after bid/ask time
            map("TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("BidTime", "Bid.Time", FieldType.BID_ASK_TIME).optional().
            map("BidExchangeCode", "Bid.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("BidPrice", "Bid.Price", FieldType.PRICE).
            map("BidSize", "Bid.Size", FieldType.SIZE).
            map("AskTime", "Ask.Time", FieldType.BID_ASK_TIME).optional().
            map("AskExchangeCode", "Ask.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("AskPrice", "Ask.Price", FieldType.PRICE).
            map("AskSize", "Ask.Size", FieldType.SIZE).
            // assign of TimeMillisSequence must go after bid/ask time
            assign("TimeMillisSequence", "#Sequence#").
            injectPutEventCode("#Sequence=event.getTimeMillisSequence()#;").
            publishable();

        ctx.record("com.dxfeed.event.market", Quote.class, "Quote2"). // scheme record only -- no delegate
            phantom("reuters.phantom"). // phantom record -- see QD-503
            field("BidPrice", "Bid.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("BidSize", "Bid.Size", FieldType.DECIMAL_AS_DOUBLE).
            field("AskPrice", "Ask.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("AskSize", "Ask.Size", FieldType.DECIMAL_AS_DOUBLE).
            field("BidPriceTimestamp", "Bid.Price.Timestamp", FieldType.TIME_SECONDS).
            field("BidSizeTimestamp", "Bid.Size.Timestamp", FieldType.TIME_SECONDS).
            field("AskPriceTimestamp", "Ask.Price.Timestamp", FieldType.TIME_SECONDS).
            field("AskSizeTimestamp", "Ask.Size.Timestamp", FieldType.TIME_SECONDS);

        ctx.delegate("Trade", Trade.class, "Trade&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            mapTimeAndSequence("Last.Time", "Last.Sequence").optional().prevOptional().
            map("TimeNanoPart", "Last.TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", "Last.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("Price", "Last.Price", FieldType.PRICE).
            map("Size", "Last.Size", FieldType.SIZE).
            field("Tick", "Last.Tick", FieldType.FLAGS).optional().
            map("Change", "Last.Change", FieldType.PRICE).optional().
            map("DayId", "DayId", FieldType.DATE).optional().
            map("DayVolume", "Volume", FieldType.VOLUME).optional().
            map("DayTurnover", "DayTurnover", FieldType.TURNOVER).optional().
            map("Flags", "Last.Flags", FieldType.FLAGS).optional().
            injectGetEventCode(
                "if (event.getTickDirection() == Direction.UNDEFINED) {",
                "    // if direction is not provided via flags field - compute it from tick field if provided",
                "    int tick = m.getTick(cursor);",
                "    if (tick == 1)",
                "        event.setTickDirection(Direction.ZERO_UP);",
                "    else if (tick == 2)",
                "        event.setTickDirection(Direction.ZERO_DOWN);",
                "}"
            ).
            injectPutEventCode(
                "Direction d = event.getTickDirection();",
                "m.setTick(cursor, d == Direction.UP || d == Direction.ZERO_UP ? 1 : d == Direction.DOWN || d == Direction.ZERO_DOWN ? 2 : 0);"
            ).
            field("Date", "Date", FieldType.INT).compositeOnly().phantom("reuters.phantom"). // phantom field -- see QD-503
            field("Operation", "Operation", FieldType.INT).compositeOnly().phantom("reuters.phantom"). // phantom field -- see QD-503
            publishable();

        ctx.delegate("TradeETH", TradeETH.class, "TradeETH&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            mapTimeAndSequence("ETHLast.Time", "ETHLast.Sequence").optional().prevOptional().
            map("TimeNanoPart", "Last.TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", "ETHLast.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("Price", "ETHLast.Price", FieldType.PRICE).
            map("Size", "ETHLast.Size", FieldType.SIZE).
            map("Change", "ETHLast.Change", FieldType.PRICE).optional().
            map("DayId", "DayId", FieldType.DATE).optional().
            map("DayVolume", "ETHVolume", FieldType.VOLUME).optional().
            map("DayTurnover", "ETHDayTurnover", FieldType.TURNOVER).optional().
            map("Flags", "ETHLast.Flags", FieldType.FLAGS).
            publishable();

        ctx.delegate("Summary", Summary.class, "Summary&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            map("DayId", "DayId", FieldType.DATE).
            map("DayOpenPrice", "DayOpen.Price", FieldType.PRICE).
            map("DayHighPrice", "DayHigh.Price", FieldType.PRICE).
            map("DayLowPrice", "DayLow.Price", FieldType.PRICE).
            map("DayClosePrice", "DayClose.Price", FieldType.PRICE).optional().
            map("PrevDayId", "PrevDayId", FieldType.DATE).
            map("PrevDayClosePrice", "PrevDayClose.Price", FieldType.PRICE).
            map("PrevDayVolume", "PrevDayVolume", FieldType.VOLUME).optional().
            map("OpenInterest", "OpenInterest", FieldType.INT_DECIMAL).compositeOnly().optional().
            map("Flags", "Flags", FieldType.FLAGS).optional().
            publishable();

        ctx.record("com.dxfeed.event.market", Summary.class, "Fundamental&"). // scheme record only -- no delegate
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            field("Open", "Open.Price", FieldType.PRICE).
            field("High", "High.Price", FieldType.PRICE).
            field("Low", "Low.Price", FieldType.PRICE).
            field("Close", "Close.Price", FieldType.PRICE).
            field("OpenInterest", FieldType.INT_DECIMAL).compositeOnly().optional();

        // NOTE: It will be replaced by SpreadOrder record that mimics Order records, but adds SpreadSymbol and may drop MMID
        ctx.record("com.dxfeed.event.market", null, "Book&"). // scheme record only -- no delegate
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            exchanges("I", true). // generate only Book&I by default
            field("Id", "ID", FieldType.INDEX).time(0).
            field("Sequence", FieldType.VOID).time(1).
            field("Time", FieldType.TIME_SECONDS).
            field("Type", FieldType.CHAR).
            field("Price", FieldType.PRICE).
            field("Size", FieldType.SIZE).
            field("TimeInForce", FieldType.CHAR).
            field("Symbol", FieldType.STRING);

        ctx.delegate("Profile", Profile.class, "Profile").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            map("Beta", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("EarningsPerShare", "Eps", "Eps", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("DividendFrequency", "DivFreq", "DivFreq", FieldType.DIV_FREQUENCY).optional().
            map("ExDividendAmount", "ExdDivAmount", "ExdDiv.Amount", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("ExDividendDayId", "ExdDivDate", "ExdDiv.Date", FieldType.DATE).optional().
            map("High52WeekPrice", "HighPrice52", "52High.Price", FieldType.PRICE).optional().
            map("Low52WeekPrice", "LowPrice52", "52Low.Price", FieldType.PRICE).optional().
            map("Shares", FieldType.SHARES).optional().
            map("FreeFloat", FieldType.SHARES).optional().
            map("HighLimitPrice", "HighLimitPrice", FieldType.PRICE).optional().
            map("LowLimitPrice", "LowLimitPrice", FieldType.PRICE).optional().
            map("HaltStartTime", "Halt.StartTime", FieldType.TIME_SECONDS).optional().
            map("HaltEndTime", "Halt.EndTime", FieldType.TIME_SECONDS).optional().
            map("Flags", "Flags", FieldType.FLAGS).optional().
            map("Description", "Description", FieldType.STRING).
            map("StatusReason", "StatusReason", FieldType.STRING).optional().
            publishable();

        ctx.delegate("Order", Order.class, "Order").
            suffixes(getOrderSuffixes(Order.class)).
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(ORDER_BASE_MAPPING).
            source("m.getRecordSource()").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).internal().
            map("Index", "Index", FieldType.INDEX).time(1).internal().
            assign("Index", "((long) getSource().id() << 32) | (#Index# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "int index = (int) event.getIndex();",
                "#Index=index#;"
            ).
            mapTimeAndSequence().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ActionTime", "ActionTime", FieldType.TIME_MILLIS).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("OrderId", "OrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("AuxOrderId", "AuxOrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Price", "Price", FieldType.PRICE).
            map("Size", "Size", FieldType.SIZE).
            map("ExecutedSize", "ExecutedSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Count", "Count", FieldType.INT_DECIMAL).onlySuffixes("com.dxfeed.event.order.impl.Order.suffixes.count", "").
            map("Flags", "Flags", FieldType.FLAGS).
            map("TradeId", "TradeId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradePrice", "TradePrice", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradeSize", "TradeSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).onlySuffixes(
                "com.dxfeed.event.order.impl.Order.suffixes.mmid", "|#NTV|#BATE|#CHIX|#CEUX|#BXTR|#pink").
            field("IcebergPeakSize", "IcebergPeakSize", FieldType.DECIMAL_AS_DOUBLE).optional().disabledByDefault().
            field("IcebergHiddenSize", "IcebergHiddenSize", FieldType.DECIMAL_AS_DOUBLE).optional().disabledByDefault().
            field("IcebergExecutedSize", "IcebergExecutedSize", FieldType.DECIMAL_AS_DOUBLE).optional().disabledByDefault().
            field("IcebergFlags", "IcebergFlags", FieldType.FLAGS).optional().disabledByDefault().
            injectPutEventCode(
                "if (index < 0)",
                "    throw new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & (OrderBase.SNAPSHOT_END | OrderBase.SNAPSHOT_SNIP)) != 0 && index != 0)",
                "    throw new IllegalArgumentException(\"SNAPSHOT_END and SNAPSHOT_SNIP orders must have index == 0\");",
                "if (event.getOrderSide() == Side.UNDEFINED && event.hasSize())",
                "    throw new IllegalArgumentException(\"only empty orders can have side == UNDEFINED\");"
            ).
            publishable();

        ctx.delegate("AnalyticOrder", AnalyticOrder.class, "AnalyticOrder").
            suffixes(getOrderSuffixes(AnalyticOrder.class)).
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(ORDER_BASE_MAPPING).
            source("m.getRecordSource()").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).internal().
            map("Index", "Index", FieldType.INDEX).time(1).internal().
            assign("Index", "((long) getSource().id() << 32) | (#Index# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "int index = (int) event.getIndex();",
                "#Index=index#;"
            ).
            mapTimeAndSequence().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ActionTime", "ActionTime", FieldType.TIME_MILLIS).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("OrderId", "OrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("AuxOrderId", "AuxOrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Price", "Price", FieldType.PRICE).
            map("Size", "Size", FieldType.SIZE).
            map("ExecutedSize", "ExecutedSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Count", "Count", FieldType.INT_DECIMAL).onlySuffixes("com.dxfeed.event.order.impl.AnalyticOrder.suffixes.count", "").
            map("Flags", "Flags", FieldType.FLAGS).
            map("TradeId", "TradeId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradePrice", "TradePrice", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradeSize", "TradeSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).onlySuffixes(
                "com.dxfeed.event.order.impl.AnalyticOrder.suffixes.mmid", "|#NTV|#BATE|#CHIX|#CEUX|#BXTR").
            map("IcebergPeakSize", "IcebergPeakSize", FieldType.DECIMAL_AS_DOUBLE).optional().disabledByDefault().
            map("IcebergHiddenSize", "IcebergHiddenSize", FieldType.DECIMAL_AS_DOUBLE).optional().disabledByDefault().
            map("IcebergExecutedSize", "IcebergExecutedSize", FieldType.DECIMAL_AS_DOUBLE).optional().disabledByDefault().
            map("IcebergFlags", "IcebergFlags", FieldType.FLAGS).optional().disabledByDefault().
            injectPutEventCode(
                "if (index < 0)",
                "    throw new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & (OrderBase.SNAPSHOT_END | OrderBase.SNAPSHOT_SNIP)) != 0 && index != 0)",
                "    throw new IllegalArgumentException(\"SNAPSHOT_END and SNAPSHOT_SNIP orders must have index == 0\");",
                "if (event.getOrderSide() == Side.UNDEFINED && event.hasSize())",
                "    throw new IllegalArgumentException(\"only empty orders can have side == UNDEFINED\");"
            ).
            publishable();

        ctx.delegate("OtcMarketsOrder", OtcMarketsOrder.class, "OtcMarketsOrder").
            suffixes(getOrderSuffixes(OtcMarketsOrder.class)).
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(ORDER_BASE_MAPPING).
            source("m.getRecordSource()").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).internal().
            map("Index", "Index", FieldType.INDEX).time(1).internal().
            assign("Index", "((long) getSource().id() << 32) | (#Index# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "int index = (int) event.getIndex();",
                "#Index=index#;"
            ).
            mapTimeAndSequence().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ActionTime", "ActionTime", FieldType.TIME_MILLIS).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("OrderId", "OrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("AuxOrderId", "AuxOrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Price", "Price", FieldType.PRICE).
            map("Size", "Size", FieldType.SIZE).
            map("ExecutedSize", "ExecutedSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Count", "Count", FieldType.INT_DECIMAL).onlySuffixes("com.dxfeed.event.order.impl.OtcMarketsOrder.suffixes.count", "").
            map("Flags", "Flags", FieldType.FLAGS).
            map("TradeId", "TradeId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradePrice", "TradePrice", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradeSize", "TradeSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).onlySuffixes(
                "com.dxfeed.event.order.impl.OtcMarketsOrder.suffixes.mmid", "|#pink").
            map("QuoteAccessPayment", "QuoteAccessPayment", FieldType.INT).
            map("OtcMarketsFlags", "OtcMarketsFlags", FieldType.FLAGS).
            injectPutEventCode(
                "if (index < 0)",
                "    throw new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & (OrderBase.SNAPSHOT_END | OrderBase.SNAPSHOT_SNIP)) != 0 && index != 0)",
                "    throw new IllegalArgumentException(\"SNAPSHOT_END and SNAPSHOT_SNIP orders must have index == 0\");",
                "if (event.getOrderSide() == Side.UNDEFINED && event.hasSize())",
                "    throw new IllegalArgumentException(\"only empty orders can have side == UNDEFINED\");"
            ).
            publishable();

        ctx.delegate("SpreadOrder", SpreadOrder.class, "SpreadOrder").
            suffixes(getOrderSuffixes(SpreadOrder.class)).
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(ORDER_BASE_MAPPING).
            source("m.getRecordSource()").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).internal().
            map("Index", "Index", FieldType.INDEX).time(1).internal().
            assign("Index", "((long) getSource().id() << 32) | (#Index# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "int index = (int) event.getIndex();",
                "#Index=index#;"
            ).
            mapTimeAndSequence().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ActionTime", "ActionTime", FieldType.TIME_MILLIS).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("OrderId", "OrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("AuxOrderId", "AuxOrderId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Price", "Price", FieldType.PRICE).
            map("Size", "Size", FieldType.SIZE).
            map("ExecutedSize", "ExecutedSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("Count", "Count", FieldType.INT_DECIMAL).onlySuffixes("com.dxfeed.event.order.impl.SpreadOrder.suffixes.count", "").
            map("Flags", "Flags", FieldType.FLAGS).
            map("TradeId", "TradeId", FieldType.LONG).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradePrice", "TradePrice", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("TradeSize", "TradeSize", FieldType.DECIMAL_AS_DOUBLE).onlyIf(DXSCHEME_FOB).onlySuffixes(FOB_SUFFIX_PROPERTY, FOB_SUFFIX_DEFAULT).
            map("SpreadSymbol", "SpreadSymbol", FieldType.STRING).
            injectPutEventCode(
                "if (index < 0)",
                "    throw new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & (OrderBase.SNAPSHOT_END | OrderBase.SNAPSHOT_SNIP)) != 0 && index != 0)",
                "    throw new IllegalArgumentException(\"SNAPSHOT_END and SNAPSHOT_SNIP orders must have index == 0\");",
                "if (event.getOrderSide() == Side.UNDEFINED && event.hasSize())",
                "    throw new IllegalArgumentException(\"only empty orders can have side == UNDEFINED\");"
            ).
            publishable();

        ctx.delegate("OrderByQuoteBid", Order.class, "Quote&").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            subContract(QDContract.TICKER).
            source("m.getRecordExchange() == 0 ? OrderSource.COMPOSITE_BID : OrderSource.REGIONAL_BID").
            assign("Index", "((long) getSource().id() << 48) | ((long) m.getRecordExchange() << 32)").
            map("Time", "BidTime", "Bid.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "BidPrice", "Bid.Price", FieldType.PRICE).
            map("Size", "BidSize", "Bid.Size", FieldType.SIZE).
            map("ExchangeCode", "BidExchangeCode", "Bid.Exchange", FieldType.CHAR).internal().
            assign("ExchangeCode", "m.getRecordExchange() == 0 ? #ExchangeCode# : m.getRecordExchange()").
            assign("OrderSide", "Side.BUY").
            assign("Scope", "m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL").
            assign("MarketMaker", "null");

        ctx.delegate("OrderByQuoteAsk", Order.class, "Quote&").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            subContract(QDContract.TICKER).
            source("m.getRecordExchange() == 0 ? OrderSource.COMPOSITE_ASK : OrderSource.REGIONAL_ASK").
            assign("Index", "((long) getSource().id() << 48) | ((long) m.getRecordExchange() << 32)").
            map("Time", "AskTime", "Ask.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "AskPrice", "Ask.Price", FieldType.PRICE).
            map("Size", "AskSize", "Ask.Size", FieldType.SIZE).
            map("ExchangeCode", "AskExchangeCode", "Ask.Exchange", FieldType.CHAR).internal().
            assign("ExchangeCode", "m.getRecordExchange() == 0 ? #ExchangeCode# : m.getRecordExchange()").
            assign("OrderSide", "Side.SELL").
            assign("Scope", "m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL").
            assign("MarketMaker", "null");

        // DO NOT change order - OrderByQuoteBidUnitary delegate should be before OrderByQuoteAskUnitary
        ctx.delegate("OrderByQuoteBidUnitary", Order.class, "Quote&").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            subContract(QDContract.TICKER).
            source("m.getRecordExchange() == 0 ? OrderSource.COMPOSITE : OrderSource.REGIONAL").
            // unitary sources always represent both bid and ask sides combined in a single transaction,
            // to do this, the order of delegate calls must be preserved, first bid and then ask.
            injectGetEventCode("event.setEventFlags((cursor.getEventFlags() & ~(Order.SNAPSHOT_END | Order.SNAPSHOT_SNIP)) | Order.TX_PENDING);").
            // set 47 bits, indicates the bid side.
            assign("Index", "((long) getSource().id() << 48) | 1L << 47 | ((long) (m.getRecordExchange() & 0x7FFF) << 32)").
            map("Time", "BidTime", "Bid.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "BidPrice", "Bid.Price", FieldType.PRICE).
            map("Size", "BidSize", "Bid.Size", FieldType.SIZE).
            map("ExchangeCode", "BidExchangeCode", "Bid.Exchange", FieldType.CHAR).internal().
            assign("ExchangeCode", "m.getRecordExchange() == 0 ? #ExchangeCode# : m.getRecordExchange()").
            assign("OrderSide", "Side.BUY").
            assign("Scope", "m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL").
            assign("MarketMaker", "null");

        ctx.delegate("OrderByQuoteAskUnitary", Order.class, "Quote&").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            subContract(QDContract.TICKER).
            source("m.getRecordExchange() == 0 ? OrderSource.COMPOSITE : OrderSource.REGIONAL").
            // unitary sources always represent both bid and ask sides combined in a single transaction,
            // to do this, the order of delegate calls must be preserved, first bid and then ask.
            injectGetEventCode("event.setEventFlags(cursor.getEventFlags() & ~Order.SNAPSHOT_BEGIN);").
            // clear 47 bits, indicates the ask side.
            assign("Index", "((long) getSource().id() << 48) | 0L << 47 | ((long) (m.getRecordExchange() & 0x7FFF) << 32)").
            map("Time", "AskTime", "Ask.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "AskPrice", "Ask.Price", FieldType.PRICE).
            map("Size", "AskSize", "Ask.Size", FieldType.SIZE).
            map("ExchangeCode", "AskExchangeCode", "Ask.Exchange", FieldType.CHAR).internal().
            assign("ExchangeCode", "m.getRecordExchange() == 0 ? #ExchangeCode# : m.getRecordExchange()").
            assign("OrderSide", "Side.SELL").
            assign("Scope", "m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL").
            assign("MarketMaker", "null");

        ctx.delegate("OrderByMarketMakerBid", Order.class, "MarketMaker").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            source("OrderSource.AGGREGATE_BID").
            withPlainEventFlags().
            assign("Index", "((long) getSource().id() << 48) | ((long) #ExchangeCode# << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            map("ExchangeCode", "MMExchange", FieldType.CHAR).time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).time(1).
            map("Time", "BidTime", "MMBid.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "BidPrice", "MMBid.Price", FieldType.PRICE).
            map("Size", "BidSize", "MMBid.Size", FieldType.SIZE).
            map("Count", "BidCount", "MMBid.Count", FieldType.INT_DECIMAL).optional().
            assign("OrderSide", "Side.BUY").
            assign("Scope", "Scope.AGGREGATE");

        ctx.delegate("OrderByMarketMakerAsk", Order.class, "MarketMaker").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            source("OrderSource.AGGREGATE_ASK").
            withPlainEventFlags().
            assign("Index", "((long) getSource().id() << 48) | ((long) #ExchangeCode# << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            map("ExchangeCode", "MMExchange", FieldType.CHAR).time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).time(1).
            map("Time", "AskTime", "MMAsk.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "AskPrice", "MMAsk.Price", FieldType.PRICE).
            map("Size", "AskSize", "MMAsk.Size", FieldType.SIZE).
            map("Count", "AskCount", "MMAsk.Count", FieldType.INT_DECIMAL).optional().
            assign("OrderSide", "Side.SELL").
            assign("Scope", "Scope.AGGREGATE");

        // DO NOT change order - OrderByMarketMakerBidUnitary delegate should be before OrderByMarketMakerAskUnitary
        ctx.delegate("OrderByMarketMakerBidUnitary", Order.class, "MarketMaker").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            source("OrderSource.AGGREGATE").
            // unitary sources always represent both bid and ask sides combined in a single transaction,
            // to do this, the order of delegate calls must be preserved, first bid and then ask.
            injectGetEventCode("event.setEventFlags((cursor.getEventFlags() & ~(Order.SNAPSHOT_END | Order.SNAPSHOT_SNIP)) | Order.TX_PENDING);").
            // set 47 bits, indicates the bid side.
            assign("Index", "((long) getSource().id() << 48) | 1L << 47 | ((#ExchangeCode# & 0x7FFFL) << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            map("ExchangeCode", "MMExchange", FieldType.CHAR).time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).time(1).
            map("Time", "BidTime", "MMBid.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "BidPrice", "MMBid.Price", FieldType.PRICE).
            map("Size", "BidSize", "MMBid.Size", FieldType.SIZE).
            map("Count", "BidCount", "MMBid.Count", FieldType.INT_DECIMAL).optional().
            assign("OrderSide", "Side.BUY").
            assign("Scope", "Scope.AGGREGATE");

        ctx.delegate("OrderByMarketMakerAskUnitary", Order.class, "MarketMaker").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            source("OrderSource.AGGREGATE").
            // unitary sources always represent both bid and ask sides combined in a single transaction,
            // to do this, the order of delegate calls must be preserved, first bid and then ask.
            injectGetEventCode("event.setEventFlags(cursor.getEventFlags() & ~Order.SNAPSHOT_BEGIN);").
            // clear 47 bits, indicates the ask side.
            assign("Index", "((long) getSource().id() << 48) | 0L << 47 | ((#ExchangeCode# & 0x7FFFL) << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            map("ExchangeCode", "MMExchange", FieldType.CHAR).time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).time(1).
            map("Time", "AskTime", "MMAsk.Time", FieldType.BID_ASK_TIME).optional().
            assign("Sequence", "0").
            map("Price", "AskPrice", "MMAsk.Price", FieldType.PRICE).
            map("Size", "AskSize", "MMAsk.Size", FieldType.SIZE).
            map("Count", "AskCount", "MMAsk.Count", FieldType.INT_DECIMAL).optional().
            assign("OrderSide", "Side.SELL").
            assign("Scope", "Scope.AGGREGATE");

        ctx.delegate("MarketMaker", MarketMaker.class, "MarketMaker").
            withPlainEventFlags().
            map("ExchangeCode", "MMExchange", FieldType.CHAR).internal().time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).internal().time(1).
            assign("Index", "((long) #ExchangeCode# << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "long index = event.getIndex();",
                "#ExchangeCode=(char) (index >>> 32)#;",
                "#MarketMaker=(int) index#;"
            ).
            map("BidTime", "BidTime", "MMBid.Time", FieldType.BID_ASK_TIME).optional().
            map("BidPrice", "BidPrice", "MMBid.Price", FieldType.PRICE).
            map("BidSize", "BidSize", "MMBid.Size", FieldType.SIZE).
            map("BidCount", "BidCount", "MMBid.Count", FieldType.INT_DECIMAL).optional().
            map("AskTime", "AskTime", "MMAsk.Time", FieldType.BID_ASK_TIME).optional().
            map("AskPrice", "AskPrice", "MMAsk.Price", FieldType.PRICE).
            map("AskSize", "AskSize", "MMAsk.Size", FieldType.SIZE).
            map("AskCount", "AskCount", "MMAsk.Count", FieldType.INT_DECIMAL).optional().
            injectPutEventCode(
                "if (index < 0)",
                "    throw new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & (MarketMaker.SNAPSHOT_END | MarketMaker.SNAPSHOT_SNIP)) != 0 && index != 0)",
                "    throw new IllegalArgumentException(\"SNAPSHOT_END and SNAPSHOT_SNIP MarketMaker event must have index == 0\");"
            ).
            publishable();

        ctx.delegate("TimeAndSale", TimeAndSale.class, "TimeAndSale&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            subContract(QDContract.STREAM).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", "Exchange", FieldType.CHAR).
            map("Price", "Price", FieldType.PRICE).
            map("Size", "Size", FieldType.SIZE).
            map("BidPrice", "Bid.Price", FieldType.PRICE).
            map("AskPrice", "Ask.Price", FieldType.PRICE).
            map("ExchangeSaleConditions", "SaleConditions", "ExchangeSaleConditions", FieldType.SHORT_STRING).
            map("Flags", "Flags", FieldType.FLAGS).
            map("Buyer", "Buyer", FieldType.STRING).optional().disabledByDefault().
            map("Seller", "Seller", FieldType.STRING).optional().disabledByDefault().
            publishable();

        ctx.delegate("OptionSale", OptionSale.class, "OptionSale").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            withPlainEventFlags().
            map("Void", FieldType.VOID).time(0).internal().
            map("Index", FieldType.INDEX).time(1).internal().
            assign("Index", "((long) #Index#)").
            injectPutEventCode(
                "int index = (int) event.getIndex();",
                "#Index=index#;"
            ).
            mapTimeAndSequence().
            map("TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", FieldType.CHAR).
            map("Price", FieldType.PRICE).
            map("Size", FieldType.VOLUME).
            map("BidPrice", FieldType.PRICE).
            map("AskPrice", FieldType.PRICE).
            map("ExchangeSaleConditions", FieldType.SHORT_STRING).
            map("Flags", FieldType.FLAGS).
            map("UnderlyingPrice", FieldType.PRICE).
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).
            map("Delta", FieldType.DECIMAL_AS_DOUBLE).
            map("OptionSymbol", FieldType.STRING).
            publishable();

        // This is just a temporary implementation over legacy TradeHistory record. Separate new TimeAndSale record will be used later.
        ctx.delegate("CandleByTradeHistory", Candle.class, "TradeHistory").
            inheritDelegateFrom(CANDLE_EVENT_DELEGATE).
            inheritMappingFrom(CANDLE_EVENT_MAPPING).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().
            field("ExchangeCode", "Exchange", FieldType.CHAR).optional().          // exists in scheme, but currently unmapped
            assign("Count", "1").
            map("Close", "Price", FieldType.PRICE).
            map("Volume", "Size", FieldType.SIZE).
            field("BidPrice", "Bid", FieldType.PRICE).optional().  // exists in scheme, but currently unmapped
            field("AskPrice", "Ask", FieldType.PRICE).optional().  // exists in scheme, but currently unmapped
            assign("Open", "event.getClose()").
            assign("High", "event.getClose()").
            assign("Low", "event.getClose()").
            publishable();

        ctx.delegate("Candle", Candle.class, "Candle").
            suffixes("").
            inheritDelegateFrom(CANDLE_EVENT_DELEGATE).
            inheritMappingFrom(CANDLE_EVENT_MAPPING).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().
            map("Count", "Count", FieldType.DECIMAL_AS_LONG).optional().
            map("Open", "Open", FieldType.PRICE).
            map("High", "High", FieldType.PRICE).
            map("Low", "Low", FieldType.PRICE).
            map("Close", "Close", FieldType.PRICE).
            map("Volume", "Volume", FieldType.VOLUME).optional().
            map("VWAP", "VWAP", FieldType.PRICE).optional().
            map("BidVolume", "Bid.Volume", FieldType.VOLUME).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("AskVolume", "Ask.Volume", FieldType.VOLUME).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("ImpVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("OpenInterest", FieldType.OPEN_INTEREST).optional().
            publishable();

        // use common mapping for "Candle" record, just generate Trade records and bind them to Candle delegate
        ctx.record("com.dxfeed.event.candle", Candle.class, "Candle", "Trade.").
            suffixes(TRADE_RECORD_SUFFIXES).
            field("Time", FieldType.TIME_SECONDS).time(0).
            field("Sequence", FieldType.SEQUENCE).time(1).
            field("Count", FieldType.DECIMAL_AS_LONG).optional().
            field("Open", FieldType.PRICE).
            field("High", FieldType.PRICE).
            field("Low", FieldType.PRICE).
            field("Close", FieldType.PRICE).
            field("Volume", FieldType.VOLUME).optional().
            field("VWAP", FieldType.PRICE).optional().
            field("Bid.Volume", FieldType.VOLUME).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            field("Ask.Volume", FieldType.VOLUME).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            field("ImpVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("OpenInterest", FieldType.OPEN_INTEREST).optional();

        ctx.delegate("DailyCandle", DailyCandle.class, "Candle", "Trade."). // use common mapping for "Candle" record
            suffixes(TRADE_RECORD_SUFFIXES).
            inheritDelegateFrom(CANDLE_EVENT_DELEGATE).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().
            map("Count", FieldType.DECIMAL_AS_LONG).optional().
            map("Open", FieldType.PRICE).
            map("High", FieldType.PRICE).
            map("Low", FieldType.PRICE).
            map("Close", FieldType.PRICE).
            map("Volume", FieldType.VOLUME).optional().
            map("VWAP", FieldType.PRICE).optional().
            map("BidVolume", "Bid.Volume", FieldType.VOLUME).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("AskVolume", "Ask.Volume", FieldType.VOLUME).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("ImpVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("OpenInterest", FieldType.OPEN_INTEREST).optional().
            publishable();

        ctx.delegate("Message", Message.class, "Message").
            map("MarshalledAttachment", "Message", "Message", FieldType.MARSHALLED).
            publishable();

        ctx.delegate("TextMessage", TextMessage.class, "TextMessage").
            mapTimeAndSequence().
            map("Text", "Text", FieldType.STRING).
            publishable();

        ctx.delegate("Configuration", Configuration.class, "Configuration").
            map("Version", "Version", FieldType.INDEX).optional().
            map("MarshalledAttachment", "Configuration", "Configuration", FieldType.MARSHALLED).
            publishable();

        ctx.delegate("Greeks", Greeks.class, "Greeks").
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().optional().prevOptional().
            map("Price", "Greeks.Price", FieldType.PRICE).
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).
            map("Delta", FieldType.DECIMAL_AS_DOUBLE).
            map("Gamma", FieldType.DECIMAL_AS_DOUBLE).
            map("Theta", FieldType.DECIMAL_AS_DOUBLE).
            map("Rho", FieldType.DECIMAL_AS_DOUBLE).
            map("Vega", FieldType.DECIMAL_AS_DOUBLE).
            publishable();

        ctx.delegate("TheoPrice", TheoPrice.class, "TheoPrice").
            withPlainEventFlags().
            mapTimeAndSequenceToIndex("Theo.Time", "Theo.Sequence").optional().
            map("Price", "Theo.Price", FieldType.PRICE).
            map("UnderlyingPrice", "Theo.UnderlyingPrice", FieldType.PRICE).
            map("Delta", "Theo.Delta", FieldType.DECIMAL_AS_DOUBLE).
            map("Gamma", "Theo.Gamma", FieldType.DECIMAL_AS_DOUBLE).
            map("Dividend", "Theo.Dividend", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("Interest", "Theo.Interest", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.delegate("Underlying", Underlying.class, "Underlying").
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().optional().prevOptional().
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("FrontVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("BackVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("CallVolume", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("PutVolume", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("PutCallRatio", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.delegate("Series", Series.class, "Series").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).optional().internal().
            map("Index", "Index", FieldType.INDEX).time(1).optional().internal().
            assign("Index", "((long) #Index#)").
            injectPutEventCode(
                "int index = (int) event.getIndex();",
                "#Index=index#;"
            ).
            mapTimeAndSequence().optional().prevOptional().
            map("Expiration", "Expiration", FieldType.DATE).
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).
            map("CallVolume", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("PutVolume", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("PutCallRatio", FieldType.DECIMAL_AS_DOUBLE).
            map("ForwardPrice", FieldType.DECIMAL_AS_DOUBLE).
            map("Dividend", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("Interest", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.generateSources();
    }

    /**
     * Get record suffixes for publishable order sources of specified type as a string delimited by '|' (pipe) symbol.
     *
     * @param eventType eventType with possible values <code>{@link Order}.<b>class</b></code>,
     *     <code>{@link AnalyticOrder}.<b>class</b></code>, <code>{@link OtcMarketsOrder}.<b>class</b></code>
     *     or <code>{@link SpreadOrder}.<b>class</b></code>.
     * @return a list of publishable record suffixes delimited by '|' symbol.
     * @see OrderSource#publishable(Class)
     */
    private String getOrderSuffixes(Class<? extends OrderBase> eventType) {
        return OrderSource.publishable(eventType).stream().
            filter(os -> !OrderSource.DEFAULT.equals(os) && !OrderSource.isSpecialSourceId(os.id())).
            map(orderSource -> "|#" + orderSource.name()).
            collect(Collectors.joining());
    }

    private static String getFullOrderBookSuffixes() {
        return OrderSource.fullOrderBook().stream().
            filter(os -> !OrderSource.DEFAULT.equals(os) && !OrderSource.isSpecialSourceId(os.id())).
            map(orderSource -> "|#" + orderSource.name()).
            collect(Collectors.joining());
    }
}
