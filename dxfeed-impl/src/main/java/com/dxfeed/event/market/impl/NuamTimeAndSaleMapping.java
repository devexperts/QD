/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.util.MappingUtil;
import com.devexperts.qd.util.ShortString;
import com.devexperts.util.TimeUtil;

public class NuamTimeAndSaleMapping extends MarketEventMapping {
// BEGIN: CODE AUTOMATICALLY GENERATED: DO NOT MODIFY. IT IS REGENERATED BY com.dxfeed.api.codegen.ImplCodeGen
    private final int iTime;
    private final int iSequence;
    private final int iTimeNanoPart;
    private final int iExchangeCode;
    private final int iPrice;
    private final int iSize;
    private final int iBidPrice;
    private final int iAskPrice;
    private final int iSaleConditions;
    private final int iFlags;
    private final int oBuyer;
    private final int oSeller;
    private final int iActorId;
    private final int iParticipantId;
    private final int iOrderId;
    private final int oClientOrderId;
    private final int iTradeId;
    private final int oCustomerAccount;
    private final int oCustomerInfo;

    public NuamTimeAndSaleMapping(DataRecord record) {
        super(record);
        iTime = MappingUtil.findIntField(record, "Time", true);
        iSequence = MappingUtil.findIntField(record, "Sequence", true);
        iTimeNanoPart = MappingUtil.findIntField(record, "TimeNanoPart", false);
        iExchangeCode = MappingUtil.findIntField(record, "Exchange", true);
        iPrice = findIntField("Price", true);
        iSize = findIntField("Size", true);
        iBidPrice = findIntField("Bid.Price", true);
        iAskPrice = findIntField("Ask.Price", true);
        iSaleConditions = MappingUtil.findIntField(record, "ExchangeSaleConditions", true);
        iFlags = MappingUtil.findIntField(record, "Flags", true);
        oBuyer = MappingUtil.findObjField(record, "Buyer", false);
        oSeller = MappingUtil.findObjField(record, "Seller", false);
        iActorId = MappingUtil.findIntField(record, "ActorId", true);
        iParticipantId = MappingUtil.findIntField(record, "ParticipantId", true);
        iOrderId = MappingUtil.findIntField(record, "OrderId", true);
        oClientOrderId = MappingUtil.findObjField(record, "ClientOrderId", true);
        iTradeId = MappingUtil.findIntField(record, "TradeId", true);
        oCustomerAccount = MappingUtil.findObjField(record, "CustomerAccount", true);
        oCustomerInfo = MappingUtil.findObjField(record, "CustomerInfo", true);
        putNonDefaultPropertyName("Exchange", "ExchangeCode");
        putNonDefaultPropertyName("ExchangeSaleConditions", "SaleConditions");
    }

    public long getTimeMillis(RecordCursor cursor) {
        return getInt(cursor, iTime) * 1000L;
    }

    public void setTimeMillis(RecordCursor cursor, long time) {
        setInt(cursor, iTime, TimeUtil.getSecondsFromTime(time));
    }

    public int getTimeSeconds(RecordCursor cursor) {
        return getInt(cursor, iTime);
    }

    public void setTimeSeconds(RecordCursor cursor, int time) {
        setInt(cursor, iTime, time);
    }

    public int getSequence(RecordCursor cursor) {
        return getInt(cursor, iSequence);
    }

    public void setSequence(RecordCursor cursor, int sequence) {
        setInt(cursor, iSequence, sequence);
    }

    public int getTimeNanoPart(RecordCursor cursor) {
        if (iTimeNanoPart < 0)
            return 0;
        return getInt(cursor, iTimeNanoPart);
    }

    public void setTimeNanoPart(RecordCursor cursor, int timeNanoPart) {
        if (iTimeNanoPart < 0)
            return;
        setInt(cursor, iTimeNanoPart, timeNanoPart);
    }

    @Deprecated
    public char getExchange(RecordCursor cursor) {
        return (char) getInt(cursor, iExchangeCode);
    }

    @Deprecated
    public void setExchange(RecordCursor cursor, char exchange) {
        setInt(cursor, iExchangeCode, exchange);
    }

    public char getExchangeCode(RecordCursor cursor) {
        return (char) getInt(cursor, iExchangeCode);
    }

    public void setExchangeCode(RecordCursor cursor, char exchangeCode) {
        setInt(cursor, iExchangeCode, exchangeCode);
    }

    public double getPrice(RecordCursor cursor) {
        return getAsDouble(cursor, iPrice);
    }

    public void setPrice(RecordCursor cursor, double price) {
        setAsDouble(cursor, iPrice, price);
    }

    public int getPriceDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iPrice);
    }

    public void setPriceDecimal(RecordCursor cursor, int price) {
        setAsTinyDecimal(cursor, iPrice, price);
    }

    public long getPriceWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iPrice);
    }

    public void setPriceWideDecimal(RecordCursor cursor, long price) {
        setAsWideDecimal(cursor, iPrice, price);
    }

    public int getSize(RecordCursor cursor) {
        return getAsInt(cursor, iSize);
    }

    public void setSize(RecordCursor cursor, int size) {
        setAsInt(cursor, iSize, size);
    }

    public long getSizeLong(RecordCursor cursor) {
        return getAsLong(cursor, iSize);
    }

    public void setSizeLong(RecordCursor cursor, long size) {
        setAsLong(cursor, iSize, size);
    }

    public double getSizeDouble(RecordCursor cursor) {
        return getAsDouble(cursor, iSize);
    }

    public void setSizeDouble(RecordCursor cursor, double size) {
        setAsDouble(cursor, iSize, size);
    }

    public int getSizeDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iSize);
    }

    public void setSizeDecimal(RecordCursor cursor, int size) {
        setAsTinyDecimal(cursor, iSize, size);
    }

    public long getSizeWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iSize);
    }

    public void setSizeWideDecimal(RecordCursor cursor, long size) {
        setAsWideDecimal(cursor, iSize, size);
    }

    public double getBidPrice(RecordCursor cursor) {
        return getAsDouble(cursor, iBidPrice);
    }

    public void setBidPrice(RecordCursor cursor, double bidPrice) {
        setAsDouble(cursor, iBidPrice, bidPrice);
    }

    public int getBidPriceDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iBidPrice);
    }

    public void setBidPriceDecimal(RecordCursor cursor, int bidPrice) {
        setAsTinyDecimal(cursor, iBidPrice, bidPrice);
    }

    public long getBidPriceWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iBidPrice);
    }

    public void setBidPriceWideDecimal(RecordCursor cursor, long bidPrice) {
        setAsWideDecimal(cursor, iBidPrice, bidPrice);
    }

    public double getAskPrice(RecordCursor cursor) {
        return getAsDouble(cursor, iAskPrice);
    }

    public void setAskPrice(RecordCursor cursor, double askPrice) {
        setAsDouble(cursor, iAskPrice, askPrice);
    }

    public int getAskPriceDecimal(RecordCursor cursor) {
        return getAsTinyDecimal(cursor, iAskPrice);
    }

    public void setAskPriceDecimal(RecordCursor cursor, int askPrice) {
        setAsTinyDecimal(cursor, iAskPrice, askPrice);
    }

    public long getAskPriceWideDecimal(RecordCursor cursor) {
        return getAsWideDecimal(cursor, iAskPrice);
    }

    public void setAskPriceWideDecimal(RecordCursor cursor, long askPrice) {
        setAsWideDecimal(cursor, iAskPrice, askPrice);
    }

    @Deprecated
    public String getExchangeSaleConditionsString(RecordCursor cursor) {
        return ShortString.decode(getInt(cursor, iSaleConditions));
    }

    @Deprecated
    public void setExchangeSaleConditionsString(RecordCursor cursor, String exchangeSaleConditions) {
        setInt(cursor, iSaleConditions, (int) ShortString.encode(exchangeSaleConditions));
    }

    @Deprecated
    public int getExchangeSaleConditions(RecordCursor cursor) {
        return getInt(cursor, iSaleConditions);
    }

    @Deprecated
    public void setExchangeSaleConditions(RecordCursor cursor, int exchangeSaleConditions) {
        setInt(cursor, iSaleConditions, exchangeSaleConditions);
    }

    public String getSaleConditionsString(RecordCursor cursor) {
        return ShortString.decode(getInt(cursor, iSaleConditions));
    }

    public void setSaleConditionsString(RecordCursor cursor, String saleConditions) {
        setInt(cursor, iSaleConditions, (int) ShortString.encode(saleConditions));
    }

    public int getSaleConditions(RecordCursor cursor) {
        return getInt(cursor, iSaleConditions);
    }

    public void setSaleConditions(RecordCursor cursor, int saleConditions) {
        setInt(cursor, iSaleConditions, saleConditions);
    }

    public int getFlags(RecordCursor cursor) {
        return getInt(cursor, iFlags);
    }

    public void setFlags(RecordCursor cursor, int flags) {
        setInt(cursor, iFlags, flags);
    }

    public String getBuyer(RecordCursor cursor) {
        if (oBuyer < 0)
            return null;
        return (String) getObj(cursor, oBuyer);
    }

    public void setBuyer(RecordCursor cursor, String buyer) {
        if (oBuyer < 0)
            return;
        setObj(cursor, oBuyer, buyer);
    }

    public String getSeller(RecordCursor cursor) {
        if (oSeller < 0)
            return null;
        return (String) getObj(cursor, oSeller);
    }

    public void setSeller(RecordCursor cursor, String seller) {
        if (oSeller < 0)
            return;
        setObj(cursor, oSeller, seller);
    }

    public int getActorId(RecordCursor cursor) {
        return getInt(cursor, iActorId);
    }

    public void setActorId(RecordCursor cursor, int actorId) {
        setInt(cursor, iActorId, actorId);
    }

    public int getParticipantId(RecordCursor cursor) {
        return getInt(cursor, iParticipantId);
    }

    public void setParticipantId(RecordCursor cursor, int participantId) {
        setInt(cursor, iParticipantId, participantId);
    }

    public long getOrderId(RecordCursor cursor) {
        return getLong(cursor, iOrderId);
    }

    public void setOrderId(RecordCursor cursor, long orderId) {
        setLong(cursor, iOrderId, orderId);
    }

    public String getClientOrderId(RecordCursor cursor) {
        return (String) getObj(cursor, oClientOrderId);
    }

    public void setClientOrderId(RecordCursor cursor, String clientOrderId) {
        setObj(cursor, oClientOrderId, clientOrderId);
    }

    public long getTradeId(RecordCursor cursor) {
        return getLong(cursor, iTradeId);
    }

    public void setTradeId(RecordCursor cursor, long tradeId) {
        setLong(cursor, iTradeId, tradeId);
    }

    public String getCustomerAccount(RecordCursor cursor) {
        return (String) getObj(cursor, oCustomerAccount);
    }

    public void setCustomerAccount(RecordCursor cursor, String customerAccount) {
        setObj(cursor, oCustomerAccount, customerAccount);
    }

    public String getCustomerInfo(RecordCursor cursor) {
        return (String) getObj(cursor, oCustomerInfo);
    }

    public void setCustomerInfo(RecordCursor cursor, String customerInfo) {
        setObj(cursor, oCustomerInfo, customerInfo);
    }
// END: CODE AUTOMATICALLY GENERATED
}
