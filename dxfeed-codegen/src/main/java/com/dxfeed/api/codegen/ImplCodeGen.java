/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
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
import com.dxfeed.event.market.MarketEventDelegateImpl;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderBaseDelegateImpl;
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
import com.dxfeed.event.option.Greeks;
import com.dxfeed.event.option.Series;
import com.dxfeed.event.option.TheoPrice;
import com.dxfeed.event.option.Underlying;

import java.io.IOException;

/**
 * Main class to generate all implementation-related code for dxFeed.
 *
 * <p>You can run this with this maven command from the root of the project:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.dxfeed.api.codegen.ImplCodeGen" -pl :dxfeed-codegen
 * </pre>
 */
public class ImplCodeGen {

    private static final ClassName MARKET_EVENT_DELEGATE = new ClassName(MarketEventDelegateImpl.class);
    private static final ClassName CANDLE_EVENT_DELEGATE = new ClassName(CandleEventDelegateImpl.class);
    private static final ClassName ORDER_BASE_DELEGATE = new ClassName(OrderBaseDelegateImpl.class);

    private static final ClassName MARKET_EVENT_MAPPING = new ClassName(MarketEventMapping.class);
    private static final ClassName CANDLE_EVENT_MAPPING = new ClassName(CandleEventMapping.class);
    private static final ClassName ORDER_BASE_MAPPING = new ClassName(OrderBaseMapping.class);

    private static final String TRADE_RECORD_SUFFIXES =
        "133ticks|144ticks|233ticks|333ticks|400ticks|512ticks|1600ticks|3200ticks|" +
        "1min|2min|3min|4min|5min|6min|10min|12min|15min|20min|30min|" +
        "1hour|2hour|3hour|4hour|6hour|8hour|12hour|Day|2Day|3Day|4Day|Week|Month|OptExp";
    private static final String BID_ASK_VOLUME_SUFFIXES = ".*[{,]price=(bid|ask|mark|s)[,}].*";
    private static final String TRADE_SEQUENCE_VOID_FOR_SUFFIXES = ".*min|.*hour|.*Day|Week|Month|OptExp";
    private static final String TRADE_DAILY_ONLY_SUFFIXES = ".*Day|Week|Month|OptExp";

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
            map("BidTime", "Bid.Time", FieldType.TIME).optional().
            map("BidExchangeCode", "Bid.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("BidPrice", "Bid.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("BidSize", "Bid.Size", FieldType.INT).
            map("AskTime", "Ask.Time", FieldType.TIME).optional().
            map("AskExchangeCode", "Ask.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("AskPrice", "Ask.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("AskSize", "Ask.Size", FieldType.INT).
            // assign of TimeMillisSequence must go after bid/ask time
            assign("TimeMillisSequence", "#Sequence#").
            injectPutEventCode("#Sequence=event.getTimeMillisSequence()#;").
            publishable().
            generate();

        ctx.record("com.dxfeed.event.market", "Quote2"). // scheme record only -- no delegate
            phantom("reuters.phantom"). // phantom record -- see QD-503
            field("BidPrice", "Bid.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("BidSize", "Bid.Size", FieldType.DECIMAL_AS_DOUBLE).
            field("AskPrice", "Ask.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("AskSize", "Ask.Size", FieldType.DECIMAL_AS_DOUBLE).
            field("BidPriceTimestamp", "Bid.Price.Timestamp", FieldType.TIME).
            field("BidSizeTimestamp", "Bid.Size.Timestamp", FieldType.TIME).
            field("AskPriceTimestamp", "Ask.Price.Timestamp", FieldType.TIME).
            field("AskSizeTimestamp", "Ask.Size.Timestamp", FieldType.TIME);

        ctx.delegate("Trade", Trade.class, "Trade&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            map("Time", "Last.Time", FieldType.TIME).optional().internal().
            map("Sequence", "Last.Sequence", FieldType.SEQUENCE).optional().internal().
            assign("TimeSequence", "(((long)#Time.Seconds#) << 32) | (#Sequence# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "#Time.Seconds=(int)(event.getTimeSequence() >>> 32)#;",
                "#Sequence=(int)event.getTimeSequence()#;"
            ).
            map("TimeNanoPart", "Last.TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", "Last.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("Price", "Last.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "Last.Size", FieldType.INT).
            field("Tick", "Last.Tick", FieldType.INT).optional().
            field("Change", "Last.Change", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("Flags", "Last.Flags", FieldType.INT).optional().
            injectGetEventCode(
                "if (event.getTickDirection() == Direction.UNDEFINED) {",
                "\t// if direction is not provided via flags field - compute it from tick field if provided",
                "\tint tick = m.getTick(cursor);",
                "\tif (tick == 1)",
                "\t\tevent.setTickDirection(Direction.ZERO_UP);",
                "\telse if (tick == 2)",
                "\t\tevent.setTickDirection(Direction.ZERO_DOWN);",
                "}"
            ).
            injectPutEventCode(
                "Direction d = event.getTickDirection();",
                "m.setTick(cursor, d == Direction.UP || d == Direction.ZERO_UP ? 1 : d == Direction.DOWN || d == Direction.ZERO_DOWN ? 2 : 0);"
            ).
            map("DayVolume", "Volume", FieldType.DECIMAL_AS_LONG).optional().
            map("DayTurnover", "DayTurnover", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("Date", "Date", FieldType.INT).compositeOnly().phantom("reuters.phantom"). // phantom field -- see QD-503
            field("Operation", "Operation", FieldType.INT).compositeOnly().phantom("reuters.phantom"). // phantom field -- see QD-503
            publishable();

        ctx.delegate("TradeETH", TradeETH.class, "TradeETH&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            map("Time", "ETHLast.Time", FieldType.TIME).optional().internal().
            map("Sequence", "ETHLast.Sequence", FieldType.SEQUENCE).optional().internal().
            assign("TimeSequence", "(((long)#Time.Seconds#) << 32) | (#Sequence# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "#Time.Seconds=(int)(event.getTimeSequence() >>> 32)#;",
                "#Sequence=(int)event.getTimeSequence()#;"
            ).
            map("TimeNanoPart", "Last.TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", "ETHLast.Exchange", FieldType.CHAR).alt("recordExchange").compositeOnly().optional().
            map("Price", "ETHLast.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "ETHLast.Size", FieldType.INT).
            map("Flags", "ETHLast.Flags", FieldType.INT).
            map("DayVolume", "ETHVolume", FieldType.DECIMAL_AS_LONG).optional().
            map("DayTurnover", "ETHDayTurnover", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.delegate("Summary", Summary.class, "Summary&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            map("DayId", "DayId", FieldType.DATE).
            map("DayOpenPrice", "DayOpen.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("DayHighPrice", "DayHigh.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("DayLowPrice", "DayLow.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("DayClosePrice", "DayClose.Price", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("PrevDayId", "PrevDayId", FieldType.DATE).
            map("PrevDayClosePrice", "PrevDayClose.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("PrevDayVolume", "PrevDayVolume", FieldType.DECIMAL_AS_DOUBLE).compositeOnly().optional().
            map("OpenInterest", "OpenInterest", FieldType.INT).compositeOnly().optional().
            map("Flags", "Flags", FieldType.INT).optional().
            publishable();

        ctx.record("com.dxfeed.event.market", "Fundamental&"). // scheme record only -- no delegate
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            field("Open", "Open.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("High", "High.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("Low", "Low.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("Close", "Close.Price", FieldType.DECIMAL_AS_DOUBLE).
            field("OpenInterest", FieldType.INT).compositeOnly().optional();

        // NOTE: It will be replaced by SpreadOrder record that mimics Order records, but adds SpreadSymbol and may drop MMID
        ctx.record("com.dxfeed.event.market", "Book&"). // scheme record only -- no delegate
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            exchanges("I", true). // generate only Book&I by default
            field("Id", "ID", FieldType.INT).time(0).
            field("Sequence", FieldType.VOID).time(1).
            field("Time", FieldType.TIME).
            field("Type", FieldType.CHAR).
            field("Price", FieldType.DECIMAL_AS_DOUBLE).
            field("Size", FieldType.INT).
            field("TimeInForce", FieldType.CHAR).
            field("Symbol", FieldType.STRING);

        ctx.delegate("Profile", Profile.class, "Profile").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            field("Beta", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("Eps", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("DivFreq", FieldType.INT).optional().
            field("ExdDivAmount", "ExdDiv.Amount", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("ExdDivDate", "ExdDiv.Date", FieldType.DATE).optional().
            field("HighPrice52", "52High.Price", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("LowPrice52", "52Low.Price", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("Shares", FieldType.DECIMAL_AS_SHARES).optional().
            field("FreeFloat", FieldType.DECIMAL_AS_LONG).optional().
            map("HighLimitPrice", "HighLimitPrice", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("LowLimitPrice", "LowLimitPrice", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("HaltStartTime", "Halt.StartTime", FieldType.TIME).optional().
            map("HaltEndTime", "Halt.EndTime", FieldType.TIME).optional().
            map("Flags", "Flags", FieldType.INT).optional().
            map("Description", "Description", FieldType.STRING).
            map("StatusReason", "StatusReason", FieldType.STRING).optional().
            publishable();

        ctx.delegate("Order", Order.class, "Order").
            suffixes("|#NTV|#NFX|#ESPD|#DEA|#DEX|#BYX|#BZX|#IST|#ISE|#BATE|#CHIX|#BXTR|#GLBX|#XEUR|#ICE").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(ORDER_BASE_MAPPING).
            source("m.getRecordSource()").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).internal().
            map("Index", "Index", FieldType.INT).time(1).internal().
            map("Time", "Time", FieldType.TIME).internal().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("Sequence", "Sequence", FieldType.SEQUENCE).internal().
            assign("Index", "((long)getSource().id() << 32) | (#Index# & 0xFFFFFFFFL)").
            assign("TimeSequence", "(((long)#Time.Seconds#) << 32) | (#Sequence# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "int index = (int)event.getIndex();",
                "#Index=index#;",
                "#Time.Seconds=(int)(event.getTimeSequence() >>> 32)#;",
                "#Sequence=(int)event.getTimeSequence()#;"
            ).
            map("Price", "Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "Size", FieldType.INT).
            map("Count", "Count", FieldType.INT).onlySuffixes("com.dxfeed.event.order.impl.Order.suffixes.count", "").
            map("Flags", "Flags", FieldType.INT).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).onlySuffixes(
                "com.dxfeed.event.order.impl.Order.suffixes.mmid", "|#NTV|#BATE|#CHIX|#BXTR").
            injectPutEventCode(
                "if (index < 0)",
                "\tthrow new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & OrderBase.SNAPSHOT_END) != 0 && index != 0)",
                "\tthrow new IllegalArgumentException(\"SNAPSHOT_END event must have index == 0\");",
                "if ((event.getEventFlags() & OrderBase.REMOVE_EVENT) == 0 && event.getOrderSide() == Side.UNDEFINED)",
                "\tthrow new IllegalArgumentException(\"only REMOVE_EVENT event can have side == UNDEFINED\");"
            ).
            publishable();

        ctx.delegate("SpreadOrder", SpreadOrder.class, "SpreadOrder").
            suffixes("|#ISE").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            inheritMappingFrom(ORDER_BASE_MAPPING).
            source("m.getRecordSource()").
            withPlainEventFlags().
            map("Void", "Void", FieldType.VOID).time(0).internal().
            map("Index", "Index", FieldType.INT).time(1).internal().
            map("Time", "Time", FieldType.TIME).internal().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("Sequence", "Sequence", FieldType.SEQUENCE).internal().
            assign("Index", "((long)getSource().id() << 32) | (#Index# & 0xFFFFFFFFL)").
            assign("TimeSequence", "(((long)#Time.Seconds#) << 32) | (#Sequence# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "int index = (int)event.getIndex();",
                "#Index=index#;",
                "#Time.Seconds=(int)(event.getTimeSequence() >>> 32)#;",
                "#Sequence=(int)event.getTimeSequence()#;"
            ).
            map("Price", "Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "Size", FieldType.INT).
            map("Count", "Count", FieldType.INT).onlySuffixes("com.dxfeed.event.order.impl.SpreadOrder.suffixes.count", "").
            map("Flags", "Flags", FieldType.INT).
            map("SpreadSymbol", "SpreadSymbol", FieldType.STRING).
            injectPutEventCode(
                "if (index < 0)",
                "\tthrow new IllegalArgumentException(\"Invalid index to publish\");",
                "if ((event.getEventFlags() & OrderBase.SNAPSHOT_END) != 0 && index != 0)",
                "\tthrow new IllegalArgumentException(\"SNAPSHOT_END event must have index == 0\");",
                "if ((event.getEventFlags() & OrderBase.REMOVE_EVENT) == 0 && event.getOrderSide() == Side.UNDEFINED)",
                "\tthrow new IllegalArgumentException(\"only REMOVE_EVENT event can have side == UNDEFINED\");"
            ).
            publishable();

        ctx.delegate("OrderByQuoteBid", Order.class, "Quote&").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            subContract(QDContract.TICKER).
            source("m.getRecordExchange() == 0 ? OrderSource.COMPOSITE_BID : OrderSource.REGIONAL_BID").
            assign("Index", "((long)getSource().id() << 48) | ((long)m.getRecordExchange() << 32)").
            map("Time", "BidTime", "Bid.Time", FieldType.TIME).optional().
            assign("Sequence", "0").
            map("Price", "BidPrice", "Bid.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "BidSize", "Bid.Size", FieldType.INT).
            map("ExchangeCode", "BidExchangeCode", "Bid.Exchange", FieldType.CHAR).internal().
            assign("ExchangeCode", "m.getRecordExchange() == 0 ? #ExchangeCode# : m.getRecordExchange()").
            assign("OrderSide", "Side.BUY").
            assign("Scope", "m.getRecordExchange() == 0 ? Scope.COMPOSITE : Scope.REGIONAL").
            assign("MarketMaker", "null");

        ctx.delegate("OrderByQuoteAsk", Order.class, "Quote&").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            subContract(QDContract.TICKER).
            source("m.getRecordExchange() == 0 ? OrderSource.COMPOSITE_ASK : OrderSource.REGIONAL_ASK").
            assign("Index", "((long)getSource().id() << 48) | ((long)m.getRecordExchange() << 32)").
            map("Time", "AskTime", "Ask.Time", FieldType.TIME).optional().
            assign("Sequence", "0").
            map("Price", "AskPrice", "Ask.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "AskSize", "Ask.Size", FieldType.INT).
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
            assign("Index", "((long)getSource().id() << 48) | ((long)#ExchangeCode# << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            map("ExchangeCode", "MMExchange", FieldType.CHAR).time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).time(1).
            map("Time", "BidTime", "MMBid.Time", FieldType.TIME).optional().
            assign("Sequence", "0").
            map("Price", "BidPrice", "MMBid.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "BidSize", "MMBid.Size", FieldType.INT).
            map("Count", "BidCount", "MMBid.Count", FieldType.INT).optional().
            assign("OrderSide", "Side.BUY").
            assign("Scope", "Scope.AGGREGATE");

        ctx.delegate("OrderByMarketMakerAsk", Order.class, "MarketMaker").
            inheritDelegateFrom(ORDER_BASE_DELEGATE).
            source("OrderSource.AGGREGATE_ASK").
            withPlainEventFlags().
            assign("Index", "((long)getSource().id() << 48) | ((long)#ExchangeCode# << 32) | (#MarketMaker# & 0xFFFFFFFFL)").
            map("ExchangeCode", "MMExchange", FieldType.CHAR).time(0).
            map("MarketMaker", "MMID", FieldType.SHORT_STRING).time(1).
            map("Time", "AskTime", "MMAsk.Time", FieldType.TIME).optional().
            assign("Sequence", "0").
            map("Price", "AskPrice", "MMAsk.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "AskSize", "MMAsk.Size", FieldType.INT).
            map("Count", "AskCount", "MMAsk.Count", FieldType.INT).optional().
            assign("OrderSide", "Side.SELL").
            assign("Scope", "Scope.AGGREGATE");

        ctx.delegate("TimeAndSale", TimeAndSale.class, "TimeAndSale&").
            inheritDelegateFrom(MARKET_EVENT_DELEGATE).
            inheritMappingFrom(MARKET_EVENT_MAPPING).
            subContract(QDContract.STREAM).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().
            map("TimeNanoPart", "TimeNanoPart", FieldType.TIME_NANO_PART).optional().disabledByDefault().
            map("ExchangeCode", "Exchange", FieldType.CHAR).
            map("Price", "Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Size", "Size", FieldType.INT).
            map("BidPrice", "Bid.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("AskPrice", "Ask.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("ExchangeSaleConditions", "SaleConditions", "ExchangeSaleConditions", FieldType.SHORT_STRING).
            map("Flags", "Flags", FieldType.INT).
            map("Buyer", "Buyer", FieldType.STRING).optional().disabledByDefault().
            map("Seller", "Seller", FieldType.STRING).optional().disabledByDefault().
            publishable();

        // This is just a temporary implementation over legacy TradeHistory record. Separate new TimeAndSale record will be used later.
        ctx.delegate("CandleByTradeHistory", Candle.class, "TradeHistory").
            inheritDelegateFrom(CANDLE_EVENT_DELEGATE).
            inheritMappingFrom(CANDLE_EVENT_MAPPING).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().
            field("ExchangeCode", "Exchange", FieldType.CHAR).optional().          // exists in scheme, but currently unmapped
            assign("Count", "1").
            map("Close", "Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Volume", "Size", FieldType.INT).
            field("BidPrice", "Bid", FieldType.DECIMAL_AS_DOUBLE).optional().  // exists in scheme, but currently unmapped
            field("AskPrice", "Ask", FieldType.DECIMAL_AS_DOUBLE).optional().  // exists in scheme, but currently unmapped
            assign("Open", "event.getClose()").
            assign("High", "event.getClose()").
            assign("Low", "event.getClose()").
            publishable();

        ctx.delegate("Candle", Candle.class, "Candle").
            suffixes("").
            inheritDelegateFrom(CANDLE_EVENT_DELEGATE).
            inheritMappingFrom(CANDLE_EVENT_MAPPING).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().voidForSuffixes(".+").
            map("Count", "Count", FieldType.DECIMAL_OR_INT_AS_LONG).optional().
            map("Open", "Open", FieldType.DECIMAL_AS_DOUBLE).
            map("High", "High", FieldType.DECIMAL_AS_DOUBLE).
            map("Low", "Low", FieldType.DECIMAL_AS_DOUBLE).
            map("Close", "Close", FieldType.DECIMAL_AS_DOUBLE).
            map("Volume", "Volume", FieldType.DECIMAL_AS_LONG).optional().
            map("VWAP", "VWAP", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("BidVolume", "Bid.Volume", FieldType.DECIMAL_AS_LONG).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("AskVolume", "Ask.Volume", FieldType.DECIMAL_AS_LONG).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            publishable();

        // use common mapping for "Candle" record, just generate Trade records and bind them to Candle delegate
        ctx.record("com.dxfeed.event.candle", "Candle", "Trade.").
            suffixes(TRADE_RECORD_SUFFIXES).
            field("Time", FieldType.TIME).time(0).
            field("Sequence", FieldType.SEQUENCE).time(1).voidForSuffixes(TRADE_SEQUENCE_VOID_FOR_SUFFIXES).
            field("Count", FieldType.DECIMAL_AS_LONG).optional().
            field("Open", FieldType.DECIMAL_AS_DOUBLE).
            field("High", FieldType.DECIMAL_AS_DOUBLE).
            field("Low", FieldType.DECIMAL_AS_DOUBLE).
            field("Close", FieldType.DECIMAL_AS_DOUBLE).
            field("Volume", FieldType.DECIMAL_AS_LONG).optional().
            field("VWAP", FieldType.DECIMAL_AS_DOUBLE).optional().
            field("Bid.Volume", FieldType.DECIMAL_AS_LONG).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            field("Ask.Volume", FieldType.DECIMAL_AS_LONG).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional();

        ctx.delegate("DailyCandle", DailyCandle.class, "Candle", "Trade."). // use common mapping for "Candle" record
            suffixes(TRADE_RECORD_SUFFIXES).
            inheritDelegateFrom(CANDLE_EVENT_DELEGATE).
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().voidForSuffixes(".+").
            map("Count", FieldType.DECIMAL_AS_LONG).optional().
            map("Open", FieldType.DECIMAL_AS_DOUBLE).
            map("High", FieldType.DECIMAL_AS_DOUBLE).
            map("Low", FieldType.DECIMAL_AS_DOUBLE).
            map("Close", FieldType.DECIMAL_AS_DOUBLE).
            map("Volume", FieldType.DECIMAL_AS_LONG).optional().
            map("VWAP", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("BidVolume", "Bid.Volume", FieldType.DECIMAL_AS_LONG).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("AskVolume", "Ask.Volume", FieldType.DECIMAL_AS_LONG).exceptSuffixes(BID_ASK_VOLUME_SUFFIXES).optional().
            map("OpenInterest", FieldType.DECIMAL_AS_LONG).onlySuffixes(null, TRADE_DAILY_ONLY_SUFFIXES).optional().
            map("ImpVolatility", FieldType.DECIMAL_AS_DOUBLE).onlySuffixes(null, TRADE_DAILY_ONLY_SUFFIXES).optional().
            publishable();

        ctx.delegate("Message", Message.class, "Message").
            map("MarshalledAttachment", "Message", "Message", FieldType.MARSHALLED).
            publishable();

        ctx.delegate("Configuration", Configuration.class, "Configuration").
            map("Version", "Version", FieldType.INT).optional().
            map("MarshalledAttachment", "Configuration", "Configuration", FieldType.MARSHALLED).
            publishable();

        ctx.delegate("Greeks", Greeks.class, "Greeks").
            withPlainEventFlags().
            mapTimeAndSequenceToIndex().optional().prevOptional().
            map("Price", "Greeks.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).
            map("Delta", FieldType.DECIMAL_AS_DOUBLE).
            map("Gamma", FieldType.DECIMAL_AS_DOUBLE).
            map("Theta", FieldType.DECIMAL_AS_DOUBLE).
            map("Rho", FieldType.DECIMAL_AS_DOUBLE).
            map("Vega", FieldType.DECIMAL_AS_DOUBLE).
            publishable();

        ctx.delegate("TheoPrice", TheoPrice.class, "TheoPrice").
            map("Time", "Theo.Time", FieldType.TIME).
            map("Price", "Theo.Price", FieldType.DECIMAL_AS_DOUBLE).
            map("UnderlyingPrice", "Theo.UnderlyingPrice", FieldType.DECIMAL_AS_DOUBLE).
            map("Delta", "Theo.Delta", FieldType.DECIMAL_AS_DOUBLE).
            map("Gamma", "Theo.Gamma", FieldType.DECIMAL_AS_DOUBLE).
            map("Dividend", "Theo.Dividend", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("Interest", "Theo.Interest", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.delegate("Underlying", Underlying.class, "Underlying").
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("FrontVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("BackVolatility", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("PutCallRatio", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.delegate("Series", Series.class, "Series").
            withPlainEventFlags().
            map("Expiration", "Expiration", FieldType.DATE).time(0).internal().
            map("Sequence", "Sequence", FieldType.SEQUENCE).time(1).internal().
            assign("Index", "((long)#Expiration# << 32) | (#Sequence# & 0xFFFFFFFFL)").
            injectPutEventCode(
                "#Expiration=(int)(event.getIndex() >> 32)#;",
                "#Sequence=(int)event.getIndex()#;"
            ).
            map("Volatility", FieldType.DECIMAL_AS_DOUBLE).
            map("PutCallRatio", FieldType.DECIMAL_AS_DOUBLE).
            map("ForwardPrice", FieldType.DECIMAL_AS_DOUBLE).
            map("Dividend", FieldType.DECIMAL_AS_DOUBLE).optional().
            map("Interest", FieldType.DECIMAL_AS_DOUBLE).optional().
            publishable();

        ctx.generateSources();
    }
}
