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
package com.dxfeed.glossary;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper class for Classification of Financial Instruments code as defined in ISO 10962 standard.
 * Main purpose is to provide code validity checks and to construct textual explanation of CFI code
 * as defined in the standard via Java API. This class does not provide API-accessible constants
 * for specific instrument attributes and values.
 */
public class CFI implements Serializable {
    private static final long serialVersionUID = 0;


    // ========== Static API ==========

    /**
     * Empty CFI - it has code "XXXXXX".
     */
    public static final CFI EMPTY = new CFI(parse("XXXXXX"));

    private static final CFI[] cache = new CFI[239];

    /**
     * Returns an instance of CFI for specified CFI code.
     * Accepts short code and expands it to 6 letters by appending "X" at the end.
     *
     * @throws IllegalArgumentException if code is invalid
     */
    public static CFI valueOf(String code) {
        if (code == null || code.isEmpty() || "XXXXXX".startsWith(code))
            return EMPTY;
        return valueOf(parse(code));
    }

    /**
     * Returns an instance of CFI for specified integer representation of CFI code.
     *
     * @throws IllegalArgumentException if code is invalid
     */
    public static CFI valueOf(int intCode) {
        if (intCode == 0 || intCode == EMPTY.intCode)
            return EMPTY;
        int h = Math.abs(intCode % cache.length);
        CFI cfi = cache[h]; // Atomic read.
        if (cfi == null || intCode != cfi.intCode)
            cache[h] = cfi = new CFI(intCode);
        return cfi;
    }


    // ========== Instance API ==========

    private transient volatile String code;
    private final int intCode;

    private CFI(int intCode) {
        this.code = format(intCode);
        this.intCode = intCode;
    }

    /**
     * Returns CFI code. The code always has length of 6 characters.
     */
    public String getCode() {
        String code = this.code; // Atomic read.
        if (code == null)
            this.code = code = format(intCode);
        return code;
    }

    /**
     * Returns integer representation of CFI code.
     */
    public int getIntCode() {
        return intCode;
    }

    private char c(int position) {
        return (char) ('A' - 1 + ((intCode >> (25 - position * 5)) & 0x1F));
    }

    /**
     * Returns single character for instrument category - the first character of the CFI code.
     */
    public char getCategory() {
        return c(0);
    }

    /**
     * Returns single character for instrument group - the second character of the CFI code.
     */
    public char getGroup() {
        return c(1);
    }

    /**
     * Returns true if corresponding instrument is an equity.
     */
    public boolean isEquity() {
        return getCategory() == 'E';
    }

    /**
     * Returns true if corresponding instrument is a debt instrument.
     */
    public boolean isDebtInstrument() {
        return getCategory() == 'D';
    }

    /**
     * Returns true if corresponding instrument is an entitlement (right).
     */
    public boolean isEntitlement() {
        return getCategory() == 'R';
    }

    /**
     * Returns true if corresponding instrument is an option.
     */
    public boolean isOption() {
        return getCategory() == 'O';
    }

    /**
     * Returns true if corresponding instrument is a future.
     */
    public boolean isFuture() {
        return getCategory() == 'F';
    }

    /**
     * Returns true if corresponding instrument is an other (miscellaneous) instrument.
     */
    public boolean isOther() {
        return getCategory() == 'M';
    }

    /**
     * Returns array of values that explain meaning of each character in the CFI code.
     * Array always has length of 6 and each value explains corresponding character.
     * If certain character is unapplicable, unknown or unrecognized - corresponding
     * value will contain reference to this fact.
     */
    public Value[] decipher() {
        Value[] values = new Value[6];
        char category = getCategory();
        values[0] = CATEGORIES.find(category);
        Attribute group = GROUPS[category];
        values[1] = (group == null ? XATTR : group).find(getGroup());
        List<Attribute> attributes = ATTRIBUTES.get(getCode().substring(0, 2));
        for (int i = 0; i < 4; i++)
            values[i + 2] = (attributes == null ? XATTR : attributes.get(i)).find(c(i + 2));
        return values;
    }

    /**
     * Returns short textual description of this CFI code by listing names of all values
     * for the characters in this CFI code. See also {@link #decipher} method.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Value value : decipher())
            sb.append(sb.length() == 0 ? "" : "; ").append(value.getName());
        return sb.toString();
    }

    public int hashCode() {
        return intCode;
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof CFI && intCode == ((CFI) obj).intCode;
    }

    public String toString() {
        return getCode();
    }


    // ========== Internal Implementation ==========

    private static String format(int intCode) {
        if ((intCode >> 30) != 0)
            throw new IllegalArgumentException("code is invalid");
        char[] code = new char[6];
        for (int i = 5; i >= 0; i--) {
            char c = (char) ('A' - 1 + (intCode & 0x1F));
            if (c < 'A' || c > 'Z')
                throw new IllegalArgumentException("code contains invalid character");
            code[i] = c;
            intCode >>= 5;
        }
        return new String(code);
    }

    private static int parse(String code) {
        if (code.length() > 6)
            throw new IllegalArgumentException("code is too long");
        int intCode = 0;
        for (int i = 0; i < 6; i++) {
            char c = i < code.length() ? code.charAt(i) : 'X';
            if (c < 'A' || c > 'Z')
                throw new IllegalArgumentException("code contains invalid character");
            intCode = (intCode << 5) | (c - 'A' + 1);
        }
        return intCode;
    }


    // ========== CFI Standard Reconstruction ==========

    /**
     * Describes single value of single character of CFI code as defined in the ISO 10962 standard.
     */
    public static final class Value implements Serializable {
        private static final long serialVersionUID = 0;

        Attribute attribute;

        final char code;
        final String name;
        final String description;

        Value(char code, String name) {
            this(code, name, "");
        }

        Value(char code, String name, String description) {
            this.code = code;
            this.name = name;
            this.description = description;
        }

        /**
         * Returns attribute that contains this value.
         */
        public Attribute getAttribute() {
            return attribute;
        }

        /**
         * Returns single character code of this value.
         */
        public char getCode() {
            return code;
        }

        /**
         * Returns short name of this value.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns description of this value.
         */
        public String getDescription() {
            return description;
        }

        public String toString() {
            return code + " = " + name;
        }
    }

    /**
     * Describes single attribute with all values as defined in the ISO 10962 standard.
     */
    public static final class Attribute implements Serializable {
        private static final long serialVersionUID = 0;

        private static final Value XVALUE = new Value('X', "Unknown", "Attribute value is unknown.");
        private static final Value UVALUE = new Value('*', "Unrecognized", "Attribute value is not recognized.");

        final String name;
        final String description;
        final Value[] values;

        Attribute(String name, Value... values) {
            this(name, "", values);
        }

        Attribute(String name, String description, Value... values) {
            this.name = name;
            this.description = description;
            this.values = values;
            for (Value value : values)
                value.attribute = this;
        }

        /**
         * Returns short name of this attribute.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns description of this attribute.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns values of this attribute.
         */
        public Value[] getValues() {
            return values.clone();
        }

        public String toString() {
            return name;
        }

        Value find(char code) {
            for (Value value : values)
                if (code == value.code)
                    return value;
            Value value = code == 'X' ? XVALUE : UVALUE;
            value = new Value(code, value.name, value.description);
            value.attribute = this;
            return value;
        }
    }

    private static final Attribute[] GROUPS = new Attribute[127];
    private static final Map<String, List<Attribute>> ATTRIBUTES = new HashMap<String, List<Attribute>>();

    // ----- Common Attributes -----
    private static final Attribute XATTR = new Attribute("Undefined", "No attribute is defined.",
        new Value('X', "Undefined", "No attribute is defined"));

    private static final Attribute CATEGORIES = new Attribute("Categories",
        new Value('E', "Equities", "Financial instruments representing an ownership interest in an entity or pool of assets."),
        new Value('D', "Debt Instruments", "Financial instruments evidencing moneys owed by the issuer to the holder on terms as specified."),
        new Value('R', "Entitlements (Rights)", "Financial instruments providing the holder the privilege to subscribe to or to receive specific assets on terms specified."),
        new Value('S', "Structured Products", "Financial products that have pre-defined returns and are linked to one or more underlying price, index or rate with payment at one or more future dates. Note: added in ISO/DIS 10962."),
        new Value('T', "Referential Products", "Note: added in ISO/DIS 10962."),
        new Value('O', "Options", "Contracts which grant to the holder either the privilege to purchase or the privilege to sell the assets specified at a predetermined price or formula at or within a time in the future."),
        new Value('F', "Futures", "Contracts which obligate the buyer to receive and the seller to deliver in the future the assets specified at an agreed price."),
        new Value('M', "Others (Miscellaneous)", "Financial instruments which do not meet categories as defined."));

    private static final Attribute FORM = new Attribute("Form",
        new Value('B', "Bearer", "The owner is not registered in the books of the issuer or of the registrar."),
        new Value('R', "Registered", "Securities are recorded in the name of the owner on the books of the issuer or the issuer's registrar and can only be transferred to another owner when endorsed by the registered owner."),
        new Value('N', "Bearer/Registered", "Securities issued in both bearer and registered form but with the same identification number."),
        new Value('Z', "Bearer depository receipt", "Receipt - in bearer form - for securities issued in a foreign market to promote trading outside the home country of the underlying securities."),
        new Value('A', "Registered depository receipt (e.g. ADR)", "Receipt - in registered form ? for securities issued in a foreign market to promote trading outside the home country of the underlying securities."),
        new Value('G', "Regulation 144 A", "A Securities & Exchange Commission rule modifying a two-year holding period requirement on privately placed securities to permit qualified institutional buyers to trade these positions among themselves."),
        new Value('S', "Regulation S", "Rights acquired through a contractual agreement between the Investor (Restricted Securities Holder) and the Company (Issuer). Registration Rights entitle investors to force a company to register the investors' shares of company stock with the Securities and Exchange Commission (SEC) and state securities commissions. This registration, in turn, enables the investors to sell their shares to the public."),
        new Value('H', "Regulation 144 A bearer depositary receipt"),
        new Value('I', "Regulation S bearer depositary receipt"),
        new Value('V', "Regulation 144 A registered depositary receipt"),
        new Value('W', "Regulation S registered depositary receipt"),
        new Value('M', "Others (Miscellaneous)"));

    // ----- Category EQUITIES -----
    private static final Attribute EQUITIES = new Attribute("Equities", "Financial instruments representing an ownership interest in an entity or pool of assets.",
        new Value('S', "Shares, i.e. common/ordinary", "Holders typically being entitled to vote and receive dividends. In the event of liquidation, holders of shares usually rank behind the entity's creditors and holders of preferred shares."),
        new Value('P', "Preferred shares", "Payment of dividends to holders normally takes preference over the payment of dividends to other classes of shares. In the event of liquidation, preferred shares normally rank above ordinary shares but behind creditors of the company."),
        new Value('R', "Preference shares", "Like the preferred shares, preference shares have a prior claim on dividends, and on assets in an event of corporate liquidation or dissolution. But preferred stock would take precedence over preference stock in respect of dividends and assets that may be available for distribution."),
        new Value('C', "Convertible shares", "Shares (common/ordinary) that, at the option of the holder, are convertible into other securities, at a designated rate. The conversion privilege may be perpetual or limited to a specific period."),
        new Value('F', "Preferred convertible shares", "Preferred shares (common/ordinary or preferred) that, at the option of the holder, are convertible into other securities, usually common shares, at a designated rate. The conversion privilege may be perpetual or limited to a specified period."),
        new Value('V', "Preference convertibles shares", "Preference shares (common/ordinary or preferred) that, at the option of the holder, are convertible into other securities, usually common shares, at a designated rate. The conversion privilege may be perpetual or limited to a specified period."),
        new Value('U', "Units, i.e. unit trusts/mutual funds/OPCVM/OICVM", "Securities representing a portion of assets pooled by investors: run by a management company whose share capital remains separate from such assets."),
        new Value('L', "Limited partnership units", "Note: added in ISO/DIS 10962."),
        new Value('M', "Others (Miscellaneous)", "Equities which do not fit into any of the above Groups."));

    private static final Attribute EQUITIES_VOTING = new Attribute("Voting right", "Indicates the kind of voting power conferred to the shareholder.",
        new Value('V', "Voting", "Each share has one vote."),
        new Value('N', "Non-voting", "Share has no voting right."),
        new Value('R', "Restricted voting", "The shareholder may be entitled to less than one vote per share."),
        new Value('E', "Enhanced voting", "The shareholder is entitled to more than one vote per share."));

    private static final Attribute EQUITIES_TRANSFER = new Attribute("Ownership/transfer restrictions",
        new Value('T', "Restrictions", "The ownership or transfer of the security is subject to special conditions."),
        new Value('U', "Free (Unrestricted)", "The ownership or transfer of the security is not subject to special conditions."));

    private static final Attribute EQUITIES_PAYMENT = new Attribute("Payment status",
        new Value('O', "Nil paid"),
        new Value('P', "Partly paid"),
        new Value('F', "Fully paid"));

    private static final Attribute EQUITIES_REDEMPTION = new Attribute("Redemption",
        new Value('R', "Redeemable"),
        new Value('E', "Extendible"),
        new Value('T', "Redeemable/extendible"));

    private static final Attribute EQUITIES_INCOME = new Attribute("Income", "Indicates the kind of dividend income the shareholders are entitled to.",
        new Value('F', "Fixed Rate Income", "The shareholder periodically receives a stated income."),
        new Value('C', "Cumulative, Fixed Rate Income", "The shareholder periodically receives a stated amount. Dividends not paid in any year accumulate and must be paid at a later date before dividends can be paid on the common/ordinary shares."),
        new Value('P', "Participating Income", "Preferred shareholders, in addition to receiving their fixed rate of prior dividend, share with the common shareholders in further dividend distributions and in capital distributions."),
        new Value('Q', "Cumulative, Participating Income", "Shareholders are entitled to dividends in excess of the stipulated preferential rate under specified conditions. Dividends not paid in any year accumulate and must be paid at a later date before dividends can be paid on the common/ordinary shares."),
        new Value('A', "Adjustable Rate Income", "The dividend rate is set periodically, usually based on a certain yield."),
        new Value('N', "Normal Rate Income", "Shareholders are entitled to the same dividends as common/ordinary shareholders but have other privileges, e.g. as regards distribution of assets upon dissolution."));

    private static final Attribute EQUITIES_UNITS_CLOSED_OPEN = new Attribute("Closed/open-end", "Indicates whether units are traded or whether funds continually stand ready to sell new units and to redeem the outstanding units on demand.",
        new Value('C', "Closed-end", "Units are sold on either an organized exchange or in the over-thecounter market and are usually not redeemed."),
        new Value('O', "Open-end", "Funds permanently sell new units to the public and redeem outstanding units on demand, resulting in an increase or decrease of outstanding capital."));

    private static final Attribute EQUITIES_UNITS_DISTRIBUTION = new Attribute("Distribution policy", "Indicates the fund's normal distribution policy.",
        new Value('I', "Income funds", "The fund regularly distributes its investment profits."),
        new Value('G', "Growth funds", "The fund normally reinvests its investment profits."),
        new Value('M', "Mixed funds", "Investment profits are partly distributed, partly reinvested."));

    private static final Attribute EQUITIES_UNITS_ASSETS = new Attribute("Assets", "Indicates the investment policy/objective of the fund as set forth in its prospectus.",
        new Value('R', "Real estate", "Fund invests exclusively in real estate."),
        new Value('S', "Securities", "Fund invests in securities/financial instruments."),
        new Value('M', "Mixed-general", "Fund invests in different assets."),
        new Value('C', "Commodities", "Fund invests exclusively in commodities."),
        new Value('D', "Derivatives", "Fund invests in derivatives."));

    private static final Attribute EQUITIES_UNITS_STRATEGY = new Attribute("Strategy", "Indicates the investment strategy of the fund as set forth in its prospectus. Note: added in ISO/DIS 10962.",
        new Value('H', "Hedge fund", "Type of investment funds which pursue a total return strategy and usually charge a high performance fee in addition to annual management charges and initial fees."),
        new Value('B', "Balanced", "Funds that buy a combination of common stock, preferred stock, bonds, and short-term bonds, to provide both income and capital appreciation while avoiding excessive risk."),
        new Value('E', "Exchange Traded Funds", "Funds that track an index, but can be traded like a stock. ETFs always bundle together the securities that are in an index."),
        new Value('S', "Sector fund", "Mutual fund whose investments are in a particular sector of industry or economy."),
        new Value('N', "Mid-cap fund", "Mutual funds which invest in small / medium sized companies."),
        new Value('L', "Large-cap fund", "Mutual funds which invest in the largest companies."),
        new Value('I', "High-yield fund", "Mutual funds composed primarily of lower-quality, lower-rated securities which offer higher than average income, or yield."),
        new Value('A', "Small-cap fund", "Mutual fund that holds the stocks of small capitalized companies as opposed to large 'blue chip' companies."),
        new Value('M', "Others (Miscellaneous)", ""));

    static {
        add('E', EQUITIES);
        add("ES", EQUITIES_VOTING, EQUITIES_TRANSFER, EQUITIES_PAYMENT, FORM);
        add("EP", EQUITIES_VOTING, EQUITIES_REDEMPTION, EQUITIES_INCOME, FORM);
        add("ER", EQUITIES_VOTING, EQUITIES_REDEMPTION, EQUITIES_INCOME, FORM);
        add("EC", EQUITIES_VOTING, EQUITIES_TRANSFER, EQUITIES_INCOME, FORM);
        add("EF", EQUITIES_VOTING, EQUITIES_REDEMPTION, EQUITIES_INCOME, FORM);
        add("EV", EQUITIES_VOTING, EQUITIES_REDEMPTION, EQUITIES_INCOME, FORM);
//      add("EU", EQUITIES_UNITS_CLOSED_OPEN, EQUITIES_UNITS_DISTRIBUTION, EQUITIES_UNITS_ASSETS, FORM); // Note: updated in ISO/DIS 10962.
        add("EU", EQUITIES_UNITS_CLOSED_OPEN, EQUITIES_UNITS_DISTRIBUTION, EQUITIES_UNITS_ASSETS, EQUITIES_UNITS_STRATEGY); // Note: added in ISO/DIS 10962.
        add("EL", EQUITIES_VOTING, EQUITIES_TRANSFER, EQUITIES_PAYMENT, FORM); // Note: added in ISO/DIS 10962.
        add("EM", XATTR, XATTR, XATTR, FORM);
    }

    // ----- Category DEBT_INSTRUMENTS -----
    private static final Attribute DEBT_INSTRUMENTS = new Attribute("Debt Instruments", "Financial instruments evidencing moneys owed by the issuer to the holder on terms as specified.",
        new Value('B', "Bonds", "Any interest-bearing or discounted security that normally obliges the issuer to pay the bondholder a contracted sum of money and to repay the principal amount of the debt."),
        new Value('C', "Convertible bonds", "A bond that can be converted into other securities."),
        new Value('W', "Bonds with warrants attached", "A bond that is issued together with one or more warrant(s) attached as part of the offer, the warrant(s) granting the holder the right to purchase a designated security, often the common stock of the issuer of the debt, at a specified price."),
        new Value('T', "Medium-term notes", "Negotiable debt instruments offered under a program agreement through one or more dealers upon request of the issuer. The program defines the terms and conditions of the notes."),
        new Value('Y', "Money market instruments", "Financial instruments designated at issuance as such with a short-term life, usually twelve months or less, e.g. treasury bills, commercial paper."),
        new Value('A', "Asset-backed securities", "Note: added in ISO/DIS 10962."),
        new Value('G', "Mortgage-backed securities", "Note: added in ISO/DIS 10962."),
        new Value('N', "Municipal bonds", "Note: added in ISO/DIS 10962."),
        new Value('M', "Others (Miscellaneous)", "Debt instruments which do not fit into any of above Groups."));

    private static final Attribute DEBT_INTEREST = new Attribute("Type of interest",
        new Value('F', "Fixed rate", "All interest payments are known at issuance and remain constant for the life of the issue."),
        new Value('Z', "Zero rate/Discounted", "No periodical interest payments are made; the interest charge (discount) is the difference between maturity value and proceeds at time of acquisition."),
        new Value('V', "Variable", "The interest rate is subject to adjustment through the life of the issue; includes graduated, i.e. step-up/step-down, floating and indexed interest rates."));

    private static final Attribute DEBT_GUARANTEE = new Attribute("Guarantee", "Indicates, in the case of the issuer's insolvency, whether the debt issue is additionally secured.",
        new Value('T', "Government/Treasury guarantee", "The debt instrument is guaranteed by a federal or state government."),
        new Value('G', "Guaranteed", "The debt instrument is guaranteed by an entity other than the issuer; not a federal or state government."),
        new Value('S', "Secured", "A debt issue against which specific assets are pledged to secure the obligation e.g. mortgage, receivables."),
        new Value('U', "Unsecured/unguaranteed", "The direct obligations of the issuer rest solely on its general credit."));

    private static final Attribute DEBT_REIMBURSEMENT = new Attribute("Redemption/Reimbursement", "Indicates the retirement provisions made for the debt issue.",
        new Value('F', "Fixed maturity", "The principal amount is repaid in full at maturity."),
        new Value('G', "Fixed maturity with call feature", "The issue may be called for redemption prior to the fixed maturity date."),
        new Value('C', "Fixed maturity with put", "The holder may request the reimbursment of his bonds prior to the maturity date."),
        new Value('D', "Fixed maturity with put and call"),
        new Value('A', "Amortization plan", "Reduction of principal by regular payments."),
        new Value('B', "Amortization plan with call feature", "The redemption of principal may occur as the result of the outstanding portion of the bond being called."),
        new Value('T', "Amortization plan with put"),
        new Value('L', "Amortization plan with put and call"),
        new Value('P', "Perpetual", "The debt instrument has no fixed maturity date and is only due for redemption in the case of the issuer's liquidation."),
        new Value('Q', "Perpetual with call feature", "The issue may be called for redemption at some time in the future."));

    static {
        add('D', DEBT_INSTRUMENTS);
        add("DB", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM);
        add("DC", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM);
        add("DW", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM);
        add("DT", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM);
        add("DY", DEBT_INTEREST, DEBT_GUARANTEE, XATTR, FORM);
        add("DA", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM); // Note: added in ISO/DIS 10962.
        add("DG", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM); // Note: added in ISO/DIS 10962.
        add("DN", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM); // Note: added in ISO/DIS 10962.
        add("DM", DEBT_INTEREST, DEBT_GUARANTEE, DEBT_REIMBURSEMENT, FORM);
    }

    // ----- Category ENTITLEMENTS -----
    private static final Attribute ENTITLEMENTS = new Attribute("Entitlements (Rights)", "Financial instruments providing the holder the privilege to subscribe to or to receive specific assets on terms specified.",
        new Value('A', "Allotment rights", "Privileges allotted to existing security holders, entitling them to receive new securities free of charge."),
        new Value('S', "Subscription rights", "Privileges allotted to existing security holders, entitling them to subscribe to new securities at a price normally lower than the prevailing market price."),
        new Value('P', "Purchase rights", "Anti-takeover device that gives a prospective acquiree?s shareholders the right to buy shares of the firm or shares of anyone who acquires the firm at a deep discount to their fair market value."),
        new Value('W', "Warrants", "Financial instruments which permit the holder to purchase a specified amount of a financial instrument, commodity, currency or other during a specified period at a specified price."),
        new Value('M', "Others (Miscellaneous)", "Entitlements (Rights) which do not fit into any of above Groups."));

    private static final Attribute ENTITLEMENTS_RIGHTS_ASSETS = new Attribute("Underlying assets",
        new Value('S', "Ordinary shares"),
        new Value('P', "Preferred shares"),
        new Value('R', "Preference shares"),
        new Value('C', "Convertible shares"),
        new Value('F', "Preferred convertible shares"),
        new Value('V', "Preference convertible shares"),
        new Value('B', "Bonds"),
        new Value('O', "Others"));

    private static final Attribute ENTITLEMENTS_WARRANTS_ASSETS = new Attribute("Underlying assets", "Indicates the type of underlying assets that the warrant holder is entitled to acquire.",
        new Value('B', "Basket", "The warrant holder is entitled to acquire a package or group of assets."),
        new Value('S', "Stock-Equities", "The warrant holder is entitled to acquire equity."),
        new Value('D', "Debt Instruments/Interest Rates", "The warrant holder is entitled to acquire debt instruments."),
        new Value('T', "Commodities", "The warrant holder is entitled to acquire a specific commodity."),
        new Value('C', "Currencies", "The warrant holder is entitled to acquire a specified amount in a certain currency at a specified exchange rate."),
        new Value('I', "Indices", "The warrant holder is entitled to acquire a specified amount based on the performance of an index."),
        new Value('M', "Others (Miscellaneous)", "The warrant holder is entitled to acquire other assets not mentioned above."));

    private static final Attribute ENTITLEMENTS_WARRANTS_TYPE = new Attribute("Type", "Indicates whether the warrant is issued by the issuer of the underlying instrument or by a third party.",
        new Value('T', "Traditional warrants", "Issued by the issuer of the underlying instrument."),
        new Value('N', "Naked warrants", "Issued by a third party which is not the issuer of the underlying securities to which the warrant refers. The warrant issuer does not hold as many securities as would be required if all the warrants are exercised."),
        new Value('C', "Covered warrants", "Issued by a third party which is not the issuer of the underlying securities to which the warrant refers. The warrant issuer holds as many securities as would be required if all the warrants are exercised."));

    private static final Attribute ENTITLEMENTS_WARRANTS_CALL_PUT = new Attribute("Call/Put", "Indicates whether the warrant entitles the holder to acquire assets at specified terms or to acquire cash in exchange for specific underlying assets.",
        new Value('C', "Call", "As in most cases, the warrant entitles the holder to acquire specific underlying assets during a specified period at a specified price."),
        new Value('P', "Put", "The warrant entitles the holder to acquire cash in exchange for specific underlying assets."),
        new Value('B', "Call and Put", "Warrants with neither call nor put feature, warrants with call and put feature."));

    static {
        add('R', ENTITLEMENTS);
        add("RA", XATTR, XATTR, XATTR, FORM);
        add("RS", ENTITLEMENTS_RIGHTS_ASSETS, XATTR, XATTR, FORM);
        add("RP", ENTITLEMENTS_RIGHTS_ASSETS, XATTR, XATTR, FORM);
        add("RW", ENTITLEMENTS_WARRANTS_ASSETS, ENTITLEMENTS_WARRANTS_TYPE, ENTITLEMENTS_WARRANTS_CALL_PUT, FORM);
        add("RM", XATTR, XATTR, XATTR, FORM);
    }

    // ----- Category STRUCTURED_PRODUCTS -----
    private static final Attribute STRUCTURED_PRODUCTS = new Attribute("Structured Products", "Financial products that have pre-defined returns and are linked to one or more underlying price, index or rate with payment at one or more future dates. Note: added in ISO/DIS 10962.",
        new Value('W', "Structured products with capital protection", "Note: added in ISO/DIS 10962."),
        new Value('O', "Structured products without capital protection", "Note: added in ISO/DIS 10962."));

    private static final Attribute STRUCTURED_ASSETS = new Attribute("Underlying assets", "Indicates the type of underlying assets that the holder is entitled to purchase or sell. Note: added in ISO/DIS 10962.",
        new Value('B', "Basket", "The option holder is entitled to acquire, respectively to sell a package or group of assets."),
        new Value('S', "Stock-Equities", "The option holder is entitled to acquire, respectively to sell , respectively to sell equity."),
        new Value('D', "Debt Instruments", "The option holder is entitled to acquire, respectively to sell debt instruments."),
        new Value('T', "Commodities", "The holder is entitled to acquire, respectively to sell a specific commodity."),
        new Value('C', "Currencies", "The option holder is entitled to acquire, respectively to sell a specified amount in a certain currency at a specified exchange rate."),
        new Value('I', "Indices", "The option holder is entitled to acquire, respectively to sell a specified amount based on the performance of an index."),
        new Value('R', "Ratings", "The holder is entitled to acquire, respectively to sell a specified amount based on ratings."),
        new Value('N', "Interest rates", "The option's payoff depends on the future level of interest rates."),
        new Value('M', "Others (Miscellaneous)", "The option holder is entitled to acquire, respectively to sell other assets not mentioned above."));

    private static final Attribute STRUCTURED_DISTRIBUTION = new Attribute("Distribution", "Indicates the payments the structured product provides for. Note: added in ISO/DIS 10962.",
        new Value('F', "Fixed interest payments"),
        new Value('D', "Fixed dividend payments"),
        new Value('P', "Fixed premium payments"),
        new Value('I', "Fixed interest and premium payments"),
        new Value('T', "Fixed interest and dividend payments"),
        new Value('V', "Variable interest payments"),
        new Value('E', "Variable dividend payments"),
        new Value('R', "Variable premium payments"),
        new Value('N', "Variable interest and premium payments"),
        new Value('A', "Variable interest and dividend payments"),
        new Value('Y', "No payments"),
        new Value('M', "Others (Miscellaneous)"));

    private static final Attribute STRUCTURED_REPAYMENT = new Attribute("Repayment", "Indicates the payments the structured product provides for. Note: added in ISO/DIS 10962.",
        new Value('F', "Fixed cash repayment"),
        new Value('V', "Variable cash repayment"),
        new Value('S', "Repayment in stock"),
        new Value('C', "Repayment in stock and cash"),
        new Value('T', "Repayment in stock or cash"),
        new Value('N', "No repayment"),
        new Value('M', "Others (Miscellaneous)"));

    private static final Attribute STRUCTURED_STRATEGY = new Attribute("Strategy", "Indicates the strategy used in a structured product. Note: added in ISO/DIS 10962.",
        new Value('K', "High (unlimited) return potential with sudden death feature for entire product", "This product may expire before the indicated expiration date if a predefined knock-out condition (for example a barrier) has been touched."),
        new Value('I', "High (unlimited) return potential with sudden lock-in feature for entire product", "This product may expire worthless at the indicated expiration date if a predefined knockin condition (for example a barrier) has not been touched."),
        new Value('T', "High (unlimited) return potential with static underlying tracking feature", "This product has a static component tracking the performance of the underlying instrument. The risk/return profile has no dynamic features; therefore it mimics the price movements of the underlying instrument."),
        new Value('R', "High (unlimited) return potential with sudden change feature for part of the product", "This product has a static component, tracking the performance of a traditional combination strategy with unlimited profit/loss profile. However, there is an embedded knock-out or knock-in component, which can lead to the immediate expiration of parts of the product feature. Therefore, this instrument may possibly change the strategy features, e.g. a bonus certificate may transform into a tracker certificate, or a twin-win certificate will transform into a outperformance certificate."),
        new Value('S', "Limited return potential with static protection feature", "This product has a static component with limited risk/return profiles because the embedded capital protection feature minimizes the loss potential. The profit potential is limited as well because the maximal income is capped."),
        new Value('D', "Limited return potential with static high (unlimited) loss potential", "This product has a static component with limited return profiles and a theoretically unlimited loss potential because there is no embedded capital protection feature. The profit potential is limited because the maximal income is capped."),
        new Value('B', "Limited return potential with sudden change feature for (unlimited) loss", "This product has a static component tracking the performance of a traditionial combination strategy with limited profit and theoretically unlimited loss potential. However, there is an embedded knock-out or knock-in component which can lead to the immediate expiration of parts of the product features. Therefore, this instrument may possibly change the strategy features, e.g. a barrier discount certificate may transform into a discount certificate, or a barrier reverse convertible may transform into a reverse convertible after the sudden death of the dynamic portion of the strategy."),
        new Value('M', "Others (Miscellaneous)"));

    static {
        add('S', STRUCTURED_PRODUCTS); // Note: added in ISO/DIS 10962.
        add("SW", STRUCTURED_ASSETS, STRUCTURED_DISTRIBUTION, STRUCTURED_REPAYMENT, STRUCTURED_STRATEGY); // Note: added in ISO/DIS 10962.
        add("SO", STRUCTURED_ASSETS, STRUCTURED_DISTRIBUTION, STRUCTURED_REPAYMENT, STRUCTURED_STRATEGY); // Note: added in ISO/DIS 10962.
    }

    // ----- Category REFERENTIAL_PRODUCTS -----
    private static final Attribute REFERENTIAL_PRODUCTS = new Attribute("Referential Products", "Note: added in ISO/DIS 10962.",
        new Value('C', "Currencies", "Note: added in ISO/DIS 10962."),
        new Value('T', "Commodities", "Note: added in ISO/DIS 10962."),
        new Value('R', "Interest rates", "Note: added in ISO/DIS 10962."),
        new Value('I', "Indices", "Note: added in ISO/DIS 10962."),
        new Value('S', "Credit Default Swaps", "Note: added in ISO/DIS 10962."),
        new Value('M', "Others (Miscellaneous)", "Note: added in ISO/DIS 10962."));

    private static final Attribute REFERENTIAL_CURRENCIES_TYPE = new Attribute("Type", "Indicates the type or usage of currencies. Note: added in ISO/DIS 10962.",
        new Value('N', "National currency"),
        new Value('S', "Spot rate"),
        new Value('F', "Forward"),
        new Value('C', "Coins"),
        new Value('M', "Others (Miscellaneous)"));

    private static final Attribute REFERENTIAL_COMMODITIES_TYPE = new Attribute("Type", "Indicates the type of commodity. Note: added in ISO/DIS 10962.",
        new Value('E', "Extraction Resources", "Metals, Precious Metals, Coal, Oil, Gas."),
        new Value('A', "Agriculture, forestry and fishing"),
        new Value('I', "Industrial Products", "Construction, Manufacturing."),
        new Value('S', "Services", "Transportation, Communication, Trade."));

    private static final Attribute REFERENTIAL_INTEREST_ANNUITY = new Attribute("Annuity", "Indicates type of interest payments. Note: added in ISO/DIS 10962.",
        new Value('F', "Fixed annuity", "Periodical payments are made in a specified amount during a given term."),
        new Value('V', "Variable annuity", "Payments fluctuate in size contingent upon the success of the investment of the principal."),
        new Value('M', "Others (Miscellaneous)"));

    private static final Attribute REFERENTIAL_INDICES_WEIGHTING = new Attribute("Weighting", "Indicates the relative importance of index components. Note: added in ISO/DIS 10962.",
        new Value('P', "Price weighted", "All index components are weighted by their price."),
        new Value('W', "Capitalization weighted", "All index components are weighted by their market value."),
        new Value('C', "Capped capitalization weighted", "Index components are weighted by their market value, whereby overweighted index components are underweighted."),
        new Value('E', "Equal weighted by arithmetic mean", "Each index component is taken into account, i.e. the sum of all values is divided by the number of components."),
        new Value('G', "Equal weighted by geometric mean", "Each index component is taken into account, i.e. the nth root of the product of n components."));

    private static final Attribute REFERENTIAL_INDICES_CONSTITUENTS = new Attribute("Constituents", "Indicates the index components. Note: added in ISO/DIS 10962.",
        new Value('E', "Equities"),
        new Value('D', "Debt instruments"),
        new Value('T', "Commodities"),
        new Value('M', "Others (Miscellaneous)"));

    private static final Attribute REFERENTIAL_INDICES_RETURN = new Attribute("Return", "Describes the method applied to the index calculation. Note: added in ISO/DIS 10962.",
        new Value('P', "Price return", "Not payout adjusted."),
        new Value('T', "Total return", "Gross dividend, not tax adjusted."),
        new Value('N', "Net total return", "Net dividend, tax adjusted."),
        new Value('E', "Excess return", "Total return, adjusted by financing cost or inflation rate."),
        new Value('B', "Bundled", "Various return calculations integrated in the same instrument."));

    private static final Attribute REFERENTIAL_INDICES_HEDGE = new Attribute("Hedge Definition", "Specifies whether the index is calculated in local currencies or is currency hedged. Note: added in ISO/DIS 10962.",
        new Value('C', "Local currency", "Not hedged."),
        new Value('H', "Hedged", "Currency risk is hedged, but constituent risk is not."));

    static {
        add('T', REFERENTIAL_PRODUCTS); // Note: added in ISO/DIS 10962.
        add("TC", REFERENTIAL_CURRENCIES_TYPE, XATTR, XATTR, XATTR); // Note: added in ISO/DIS 10962.
        add("TT", REFERENTIAL_COMMODITIES_TYPE, XATTR, XATTR, XATTR); // Note: added in ISO/DIS 10962.
        add("TR", REFERENTIAL_INTEREST_ANNUITY, XATTR, XATTR, XATTR); // Note: added in ISO/DIS 10962.
        add("TI", REFERENTIAL_INDICES_WEIGHTING, REFERENTIAL_INDICES_CONSTITUENTS, REFERENTIAL_INDICES_RETURN, REFERENTIAL_INDICES_HEDGE); // Note: added in ISO/DIS 10962.
        add("TS", XATTR, XATTR, XATTR, XATTR); // Note: added in ISO/DIS 10962.
        add("TM", XATTR, XATTR, XATTR, XATTR); // Note: added in ISO/DIS 10962.
    }

    // ----- Category OPTIONS -----
    private static final Attribute OPTIONS = new Attribute("Options", "Contracts which grant to the holder either the privilege to purchase or the privilege to sell the assets specified at a predetermined price or formula at or within a time in the future.",
        new Value('C', "Call options", "Contracts between a buyer and a seller giving the buyer (holder) the right, but not the obligation, to buy the assets specified at a fixed price or formula, on or before a specified date. The seller of the call option assumes the obligation of delivering the assets specified should the buyer exercise his option."),
        new Value('P', "Put options", "Contracts between a buyer and a seller giving the buyer (holder) the right, but not the obligation, to sell the assets specified at a fixed price or formula, on or before a specified date. The seller of the put option assumes the obligation of buying the assets specified should the buyer exercise his option."),
        new Value('M', "Others (Miscellaneous)", "Options which do not fit into any of the above Groups."));

    private static final Attribute OPTIONS_SCHEME = new Attribute("Type of scheme", "Indicates whether an option can be exercised at a specific date or within a defined period.",
        new Value('A', "American", "The option can be exercised at any time between its issuance and expiration date."),
        new Value('E', "European", "The option can be exercised on its expiration date."));

    private static final Attribute OPTIONS_ASSETS = new Attribute("Underlying assets", "Indicates the type of underlying assets that the option holder is entitled to buy, respectively to sell.",
        new Value('B', "Basket", "The option gives the right to buy, respectively to sell a package or group of assets."),
        new Value('S', "Stock-Equities", "The option gives the right to buy, respectively to sell equity."),
        new Value('D', "Interest rate/notional debt securities", "The option gives the right to buy, respectively to sell existing or notional/fictitious debt instruments with a specific interest rate and maturity."),
        new Value('T', "Commodities", "The option gives the right to buy, respectively to sell a specific commodity."),
        new Value('C', "Currencies", "The option gives the right to buy, respectively to sell a specified amount in a certain currency at a specified exchange rate."),
        new Value('I', "Indices", "The option gives the right to buy, respectively to sell a specified amount based on the performance of an index."),
        new Value('O', "Options", "The option gives the right to buy, respectively to sell options."),
        new Value('F', "Futures", "The option gives the right to buy, respectively to sell futures."),
        new Value('W', "Swaps", "The option gives the right to buy, respectively to sell swaps."),
        new Value('M', "Others (Miscellaneous)", "The option gives the right to buy, respectively to sell other instruments not mentioned above."));

    private static final Attribute OPTIONS_DELIVERY = new Attribute("Delivery", "Indicates whether the settlement of the option when exercised is made in cash or whether the underlying instruments are delivered",
        new Value('P', "Physical", "The underlying instrument must be delivered when the option is exercised."),
        new Value('C', "Cash", "The settlement of the option is made in cash."));

    private static final Attribute OPTIONS_STANDARD = new Attribute("Standardized/non-standardized", "Indicates whether the terms of options (underlying instruments, strike price, expiration date, contract size) are standardized or not.",
        new Value('S', "Standardized", "The underlying instruments, exercise price, expiration date and contract size of the options are standardized. These options are traded on special option exchanges."),
        new Value('N', "Non-standardized", "The options are custom-made instruments normally sold over the counter. Underlying instruments, strike price, expiration date and contract size of the options are not standardized."));

    static {
        add('O', OPTIONS);
        add("OC", OPTIONS_SCHEME, OPTIONS_ASSETS, OPTIONS_DELIVERY, OPTIONS_STANDARD);
        add("OP", OPTIONS_SCHEME, OPTIONS_ASSETS, OPTIONS_DELIVERY, OPTIONS_STANDARD);
        add("OM", XATTR, XATTR, XATTR, XATTR);
    }

    // ----- Category FUTURES -----
    private static final Attribute FUTURES = new Attribute("Futures", "Contracts which obligate the buyer to receive and the seller to deliver in the future the assets specified at an agreed price.",
        new Value('F', "Financial Futures", "Futures contracts based on a financial instrument."),
        new Value('C', "Commodities Futures", "Futures contracts based on bulk goods."));

    private static final Attribute FUTURES_FINANCIAL_ASSETS = new Attribute("Underlying assets", "Indicates the type of underlying assets that the futures buyer receives, respectively that the seller delivers.",
        new Value('B', "Basket", "The buyer receives, respectively the seller delivers a package or group of assets."),
        new Value('S', "Stock-Equities", "The buyer receives, respectively the seller delivers equity."),
        new Value('D', "Interest rate/notional debt securities", "The buyer receives, respectively the seller delivers existing or notional debt instruments with a specific interest rate and maturity."),
        new Value('C', "Currencies", "The buyer receives, respectively the seller delivers a specified amount in a certain currency at a specified exchange rate."),
        new Value('I', "Indices", "The buyer receives, respectively the seller delivers a specified amount based on the performance of an index."),
        new Value('O', "Options", "The buyer receives, respectively the seller delivers options."),
        new Value('F', "Futures", "The buyer receives, respectively the seller delivers futures."),
        new Value('W', "Swaps", "The buyer receives, respectively the seller delivers swaps."),
        new Value('M', "Others (Miscellaneous)", "The buyer receives, respectively the seller delivers other instruments not mentioned above."));

    private static final Attribute FUTURES_DELIVERY = new Attribute("Delivery", "Indicates whether the settlement is made in cash or whether the underlying instruments are delivered.",
        new Value('P', "Physical", "The underlying instrument must be delivered."),
        new Value('C', "Cash", "The settlement is made in cash."));

    private static final Attribute FUTURES_STANDARD = new Attribute("Standardized/non-standardized", "Indicates whether the terms of the futures (underlying instruments expiration date, contract size) are standardized or not.",
        new Value('S', "Standardized", "The underlying instruments, expiration date and contract size of the futures are standardized. These futures are traded on special exchanges."),
        new Value('N', "Non-standardized", "Custom-made instruments between two parties. Underlying instruments, expiration date and contract size of the forwards are not standardized."));

    private static final Attribute FUTURES_COMMODITIES_ASSETS = new Attribute("Underlying assets", "Indicates the type of underlying assets that the futures buyer receives, respectively that the seller delivers.",
        new Value('E', "Extraction Resources", "Metals, Precious Metals, Coal, Oil, Gas."),
        new Value('A', "Agriculture, forestry and fishing"),
        new Value('I', "Industrial Products", "Construction, Manufacturing."),
        new Value('S', "Services", "Transportation, Communication, Trade."));

    static {
        add('F', FUTURES);
        add("FF", FUTURES_FINANCIAL_ASSETS, FUTURES_DELIVERY, FUTURES_STANDARD, XATTR);
        add("FC", FUTURES_COMMODITIES_ASSETS, FUTURES_DELIVERY, FUTURES_STANDARD, XATTR);
    }

    // ----- Category OTHERS -----
    private static final Attribute OTHERS = new Attribute("Others (Miscellaneous)", "Financial instruments which do not meet categories as defined.",
        new Value('C', "Combined Instruments", "Financial instruments that are composed of at least two other financial instruments. Note: added in ISO/DIS 10962."),
        new Value('R', "Referential Instruments", "Entities that, in a stricter sense, are not financial instruments but are often made reference to. Note: made separate category in ISO/DIS 10962."),
        new Value('M', "Other assets (Miscellaneous)", "Other assets which do not meet groupings as defined."));

    private static final Attribute OTHERS_COMBINED_TYPE = new Attribute("Type of components", "Indicates types of securities combined. Note: added in ISO/DIS 10962.",
        new Value('S', "Combination of shares (with different characteristics)"),
        new Value('B', "Combination of bonds (with different characteristics)"),
        new Value('H', "Share(s) and bond(s)"),
        new Value('A', "Share(s) and warrant(s)"),
        new Value('W', "Combination of warrants"),
        new Value('R', "Share(s), bond(s) and warrant(s)"),
        new Value('M', "Other combinations"));

    private static final Attribute OTHERS_COMBINED_TRANSFER = new Attribute("Ownership/transfer restrictions", "Note: added in ISO/DIS 10962.",
        new Value('T', "Restrictions", "The ownership or transfer of the security is subject to special conditions."),
        new Value('U', "Free", "Unrestricted; the ownership or transfer of the security is not subject to special conditions."));

    private static final Attribute OTHERS_REFERENTIAL_GROUPING = new Attribute("Further grouping",
        new Value('C', "Currencies"),
        new Value('T', "Commodities"),
        new Value('R', "Interest Rates"),
        new Value('I', "Indices"));

    private static final Attribute OTHERS_OTHER_GROUPING = new Attribute("Further grouping",
        new Value('R', "Real Estate Deeds"),
        new Value('I', "Insurance Policies"),
        new Value('E', "Escrow Receipts"),
        new Value('F', "Forwards"),
        new Value('P', "Precious Metal Receipts"),
        new Value('M', "Others (Miscellaneous)"));

    static {
        add('M', OTHERS);
        add("MC", OTHERS_COMBINED_TYPE, OTHERS_COMBINED_TRANSFER, XATTR, FORM); // Note: added in ISO/DIS 10962.
        add("MR", OTHERS_REFERENTIAL_GROUPING, XATTR, XATTR, XATTR); // Note: made separate category in ISO/DIS 10962.
        add("MM", OTHERS_OTHER_GROUPING, XATTR, XATTR, XATTR);
    }

    private static void add(char category, Attribute groups) {
        if (GROUPS[category] != null || groups == null)
            throw new ExceptionInInitializerError();
        GROUPS[category] = groups;
    }

    private static void add(String categoryAndGroup, Attribute a1, Attribute a2, Attribute a3, Attribute a4) {
        if (ATTRIBUTES.get(categoryAndGroup) != null || a1 == null || a2 == null || a3 == null || a4 == null)
            throw new ExceptionInInitializerError();
        ATTRIBUTES.put(categoryAndGroup, Arrays.asList(a1, a2, a3, a4));
    }
}
