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
package com.dxfeed.ipf.tools;

import java.io.Serializable;
import java.util.HashMap;

/**
 * A wrapper class for Market Identification Code (MIC) as defined in ISO 10383 standard.
 * Main purpose is to provide code validity checks and a list of existing MICs with their attributes
 * as defined in the standard via Java API. This class does not provide API-accessible constants
 * for specific MICs.
 */
// ### NOTE: The class is moved to "tools" package as a temporary storage to reduce size of "glossary".
// ### NOTE: It shall be re-implemented as a distributed online service to describe all registered MICs.
class MIC implements Serializable {
    private static final long serialVersionUID = 0;


    // ========== Static API ==========

    /**
     * Returns an instance of MIC for specified MIC code.
     * The specified code must be composed of exactly 4 alphanumeric characters.
     *
     * @throws IllegalArgumentException if MIC code is invalid
     */
    public static MIC valueOf(String mic) {
        MIC m = byMIC.get(mic);
        if (m != null)
            return m;
        return new MIC("", "", "", mic, "", "", "", "", "", "", false);
    }


    // ========== Instance API ==========

    private final String cc;
    private final String country;
    private final String city;
    private final String mic;
    private final String institution;
    private final String acronym;
    private final String website;
    private final String comments;
    private final String date;
    private final String status;
    private final boolean active;

    private MIC(String cc, String country, String city, String mic, String institution, String acronym, String website, String comments, String date, String status, boolean active) {
        if (cc == null || country == null || city == null || mic == null || institution == null || acronym == null || website == null || comments == null || date == null || status == null)
            throw new NullPointerException();
        if (mic.length() != 4)
            throw new IllegalArgumentException("MIC has incorrect length");
        for (int i = 0; i < 4; i++)
            if (!(mic.charAt(i) >= '0' && mic.charAt(i) <= '9' || mic.charAt(i) >= 'A' && mic.charAt(i) <= 'Z'))
                throw new IllegalArgumentException("MIC uses invalid letter");
        this.cc = cc;
        this.country = country;
        this.city = city;
        this.mic = mic;
        this.institution = institution;
        this.acronym = acronym;
        this.website = website;
        this.comments = comments;
        this.date = date;
        this.status = status;
        this.active = active;
    }

    /**
     * Returns two-letter country code where corresponding market is located.
     */
    public String getCC() {
        return cc;
    }

    /**
     * Returns country name where corresponding market is located.
     */
    public String getCountry() {
        return country;
    }

    /**
     * Returns city name where corresponding market is located.
     */
    public String getCity() {
        return city;
    }

    /**
     * Returns MIC code.
     */
    public String getMIC() {
        return mic;
    }

    /**
     * Returns institution description of the corresponding market.
     */
    public String getInstitution() {
        return institution;
    }

    /**
     * Returns acronym of the corresponding institution.
     */
    public String getAcronym() {
        return acronym;
    }

    /**
     * Returns website address of the corresponding market.
     */
    public String getWebsite() {
        return website;
    }

    /**
     * Returns comments attached to corresponding MIC entry during its lifetime.
     */
    public String getComments() {
        return comments;
    }

    /**
     * Returns last date of addition/modification/deletion of corresponding MIC entry.
     */
    public String getDate() {
        return date;
    }

    /**
     * Returns status of the MIC entry.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns <b>true</b> if corresponding MIC entry is listed in latest version of MIC database, <b>false</b> otherwise.
     */
    public boolean isActive() {
        return active;
    }

    public int hashCode() {
        return mic.hashCode();
    }

    public boolean equals(Object obj) {
        return obj == this || obj instanceof MIC && mic.equals(((MIC) obj).mic);
    }

    public String toString() {
        return mic;
    }


    // ========== MIC Standard Reconstruction ==========

    private static final HashMap<String, MIC> byMIC = new HashMap<String, MIC>();

    private static void add(String cc, String country, String city, String mic, String institution, String acronym, String website, String comments, String date, String status, boolean active) {
        MIC m = new MIC(cc, country, city, mic, institution, acronym, website, comments, date, status, active);
        if (byMIC.containsKey(mic))
            throw new IllegalArgumentException("Duplicate MIC code");
        byMIC.put(mic, m);
    }

    static {
        add("AL", "Albania", "Tirana", "XTIR", "TIRANA STOCK EXCHANGE", "", "www.tse.com.al", "", "Before June 2005", "Active", true);
        add("DZ", "Algeria", "Algiers", "XALG", "ALGIERS STOCK EXCHANGE", "", "", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Buenos Aires", "BACE", "BUENOS AIRES CEREAL EXCHANGE", "", "www.bolcereales.com.ar/default_ing.asp", "Commodity Exchange", "June 2008", "Active", true);
        add("AR", "Argentina", "Buenos Aires", "XA1X", "A1", "", "a1.primary.com.ar", "", "December 2005", "Active", true);
        add("AR", "Argentina", "Buenos Aires", "XBUE", "BUENOS AIRES STOCK EXCHANGE", "", "www.bcba.sba.com.ar", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Buenos Aires", "XMAB", "MERCADO ABIERTO ELECTRONICO S.A.", "MAE", "www.mae.com.ar", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Buenos Aires", "XMEV", "MERCADO DE VALORES DE BUENOS AIRES S.A.", "MERVAL", "www.merval.sba.com.ar", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Buenos Aires", "XMTB", "MERCADO A TERMINO DE BUENOS AIRES S.A.", "MAT", "www.matba.com.ar", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Cordoba", "MVCX", "MERCADO DE VALORES DE CORBODA", "", "www.mervalcordoba.com.ar/main/", "", "November 2007", "Active", true);
        add("AR", "Argentina", "Cordoba", "XBCC", "BOLSA DE COMERCIO DE CORBODA", "", "www.bolsacba.com.ar", "", "August 2006", "Active", true);
        add("AR", "Argentina", "Corrientes", "XCNF", "BOLSA DE COMERCIO CONFEDERADA S.A.", "BCC", "www.bolsanor.com", "", "November 2007", "Active", true);
        add("AR", "Argentina", "Mendoza", "XBCM", "BOLSA DE COMMERCIO DE MENDOZA S.A.", "", "www.bolsamza.com.ar", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Mendoza", "XBCX", "MERCADO DE VALORES DE MENDOZA", "", "www.mervalmza.com.ar", "", "November 2007", "Active", true);
        add("AR", "Argentina", "Rosario", "XROS", "BOLSA DE COMERCIO ROSARIO", "ROFEX", "www.bcr.com.ar", "", "Before June 2005", "Active", true);
        add("AR", "Argentina", "Rosario", "XROX", "MERCADO DE VALORES DE ROSARIO", "", "www.mervaros.com.ar", "", "November 2007", "Active", true);
        add("AR", "Argentina", "Santa Fe", "BCFS", "BOLSA DE COMERCIO DE SANTA FE", "", "www.bcsf.com.ar", "", "November 2007", "Active", true);
        add("AR", "Argentina", "Santa Fe", "XMVL", "MERCADO DE VALORES DEL LITORAL", "", "www.mvl.com.ar", "", "November 2007", "Active", true);
        add("AR", "Argentina", "Tucuman", "XTUC", "NUEVA BOLSA DE COMERCIO DE TUCUMAN S.A.", "", "www.mervaros.com.ar", "", "November 2007", "Active", true);
        add("AM", "Armenia", "Yerevan", "XARM", "ARMENIAN STOCK EXCHANGE", "ARMEX", "www.armex.am", "", "Before June 2005", "Active", true);
        add("AU", "Australia", "Lane Cove", "AWEX", "AUSTRALIAN WOOL EXCHANGE", "AWEX", "www.awex.com.au", "", "September 2006", "Active", true);
        add("AU", "Australia", "Melbourne", "AWBX", "AUSTRALIAN WHEAT BOARD", "AWB", "www.awb.com.au", "", "September 2006", "Active", true);
        add("AU", "Australia", "Melbourne", "NSXB", "BENDIGO STOCK EXCHANGE LIMITED", "BSX", "www.bsx.com.au", "", "June 2007", "Active", true);
        add("AU", "Australia", "Newcastle", "XNEC", "NATIONAL STOCK EXCHANGE OF AUSTRALIA LIMITED", "NSXA", "www.nsxa.com.au", "\"Stock Exchange of Newcastle Limited\" has changed its name to \"National Stock Exchange of Australia Limited\"", "June 2007", "Active", true);
        add("AU", "Australia", "Sydney", "XAOM", "AUSTRALIAN OPTIONS MARKET", "", "", "Integrated in ASX. Do not require a specific MIC", "June 2004", "Deleted", false);
        add("AU", "Australia", "Sydney", "XASX", "AUSTRALIA STOCK EXCHANGE LTD.", "ASX", "www.asx.com.au", "", "Before June 2005", "Active", true);
        add("AU", "Australia", "Sydney", "XSFE", "ASX - SYDNEY FUTURES EXCHANGE LIMITED", "SFE", "www.sfe.com.au", "", "Before June 2005", "Active", true);
        add("AU", "Australia", "Sydney", "XYIE", "YIELDBROKER PTY LTD", "", "www.yieldbroker.com", "", "Before June 2005", "Active", true);
        add("AT", "Austria", "Vienna", "EXAA", "AUSTRIAN ENERGY EXCHANGE", "EXAA", "www.exaa.at", "", "May 2007", "Active", true);
        add("AT", "Austria", "Vienna", "WBAH", "WIENER BOERSE AG AMTLICHER HANDEL (OFFICIAL MARKET)", "", "www.wienerboerse.at", "Need for MICs per trading segment.", "July 2007", "Active", true);
        add("AT", "Austria", "Vienna", "WBDM", "WIENER BOERSE AG DRITTER MARKT (THIRD MARKET)", "", "www.wienerboerse.at", "Need for MICs per trading segment.", "July 2007", "Active", true);
        add("AT", "Austria", "Vienna", "WBGF", "WIENER BOERSE AG GEREGELTER FREIVERKEHR (SEMI-OFFICIAL MARKET)", "", "www.wienerboerse.at", "Need for MICs per trading segment.", "July 2007", "Active", true);
        add("AT", "Austria", "Vienna", "XOTB", "OESTERREICHISCHE TERMIN- UND OPTIONENBOERSE, CLEARING BANK AG", "OTOB", "www.wienerborse.at", "XWBO should be used for all market segements of Wiener Boerse", "June 2007", "Deleted", false);
        add("AT", "Austria", "Vienna", "XWBO", "WIENER BOERSE AG", "WBAG", "www.wienerboerse.at", "Wiener Boerse's request for 3 MICs corresponding to the 3 trading segments (WBAH, WBGF, WBDM) and deletion of the operating market MIC.", "July 2007", "Deleted", false);
        add("AZ", "Azerbaijan", "Baku", "BSEX", "BAKU STOCK EXCHANGE", "", "www.bse.az/index.php", "", "September 2007", "Active", true);
        add("AZ", "Azerbaijan", "Baku", "XIBE", "BAKU INTERBANK CURRENCY EXCHANGE", "", "www.bbvb.org", "", "Before June 2005", "Active", true);
        add("BS", "Bahamas", "Nasau", "XBAA", "BAHAMAS INTERNATIONAL SECURITIES EXCHANGE", "BISX", "www.bisxbahamas.com", "", "Before June 2005", "Active", true);
        add("BH", "Bahrain", "Manama", "XBAH", "BAHRAIN STOCK EXCHANGE", "BSE", "www.bahrainstock.com", "", "Before June 2005", "Active", true);
        add("BD", "Bangladesh", "Chittagong", "XCHG", "CHITTAGONG STOCK EXCHANGE LTD.", "CSE", "www.csebd.com", "", "Before June 2005", "Active", true);
        add("BD", "Bangladesh", "Dhaka", "XDHA", "DHAKA STOCK EXCHANGE LTD", "DSE", "www.dsebd.org", "", "Before June 2005", "Active", true);
        add("BB", "Barbados", "Bridgetown", "XBAB", "BARBADOS STOCK EXCHANGE", "BSE", "www.bse.com.bb", "", "Before June 2005", "Active", true);
        add("BY", "Belarus", "Minsk", "BCSE", "BELARUS CURRENCY AND STOCK EXCHANGE", "BCSE", "www.bcse.by", "", "August 2006", "Active", true);
        add("BE", "Belgium", "Antwerpen", "XANT", "BEURS VAN ANTWERPEN (ANTWERP STOCK EXCHANGE)", "", "", "", "April 2003", "Deleted", false);
        add("BE", "Belgium", "Brussels", "ALXB", "NYSE EURONEXT - ALTERNEXT BRUSSELS", "", "www.alternext.com", "", "August 2007", "Active", true);
        add("BE", "Belgium", "Brussels", "BLPX", "BELGIAN POWER EXCHANGE", "BLPX", "www.belpex.be", "Description change. Typo corrected.", "November 2007", "Active", true);
        add("BE", "Belgium", "Brussels", "BMTS", "MTS BELGIUM", "", "www.mtsbelgium.com", "", "November 2005", "Active", true);
        add("BE", "Belgium", "Brussels", "ENXB", "NYSE EURONEXT - EASY NEXT", "", "www.euronext.com", "MTF for warrants and certificates", "June 2008", "Active", true);
        add("BE", "Belgium", "Brussels", "FRRF", "FONDS DES RENTES / RENTENFONDS", "", "www.nbb.be/rk/fonds.htm", "", "October 2005", "Active", true);
        add("BE", "Belgium", "Brussels", "MLXB", "NYSE EURONEXT - MARCHE LIBRE BRUSSELS", "", "www.euronext.com", "", "August 2007", "Active", true);
        add("BE", "Belgium", "Brussels", "MTSD", "MTS DENMARK", "", "www.mtsdenmark.com", "", "November 2005", "Active", true);
        add("BE", "Belgium", "Brussels", "MTSF", "MTS FINLAND", "", "www.mtsfinland.com", "", "November 2005", "Active", true);
        add("BE", "Belgium", "Brussels", "TNLB", "NYSE EURONEXT - TRADING FACILITY BRUSSELS", "", "www.euronext.com", "", "August 2007", "Active", true);
        add("BE", "Belgium", "Brussels", "VPXB", "NYSE EURONEXT - VENTES PUBLIQUES BRUSSELS", "", "www.euronext.com", "", "August 2007", "Active", true);
        add("BE", "Belgium", "Brussels", "XBFO", "BELGIAN FUTURES AND OPTIONS EXCHANGE", "", "", "", "June 2003", "Deleted", false);
        add("BE", "Belgium", "Brussels", "XBRD", "NYSE EURONEXT - EURONEXT BRUSSELS - DERIVATIVES", "", "www.euronext.com/derivatives", "Registered market for trading derivative products.", "November 2007", "Active", true);
        add("BE", "Belgium", "Brussels", "XBRU", "NYSE EURONEXT - EURONEXT BRUSSELS", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("BE", "Belgium", "Leuven", "XEAS", "EQUIDUCT", "EASDAQ", "www.equiduct.com", "Market closed. Formerly closed, market reopened. Modification of the name and website.", "September 2007", "Active", true);
        add("BM", "Bermuda", "Hamilton", "XBDA", "BERMUDA STOCK EXCHANGE LTD", "BSX", "www.bsx.com", "", "Before June 2005", "Active", true);
        add("BO", "Bolivia", "La Paz", "XBOL", "BOLSA BOLIVIANA DE VALORES S.A.", "", "www.bolsa-valores-bolivia.com", "", "Before June 2005", "Active", true);
        add("BA", "Bosnia and Herzegovina", "Banja Luka", "XBLB", "BANJA LUKA STOCK EXCHANGE", "", "www.blberza.com", "", "October 2005", "Active", true);
        add("BA", "Bosnia and Herzegovina", "Sarajevo", "XSSE", "SARAJEVO STOCK EXCHANGE", "SASE", "www.sase.ba", "", "June 2007", "Active", true);
        add("BW", "Botswana", "Gaborono", "XBOT", "BOTSWANA STOCK EXCHANGE", "", "www.mbendi.co.za/exbo.htm", "NAME CHANGE FROM BOTSWANA SHARE MARKET", "Before June 2005", "Active", true);
        add("BR", "Brazil", "Curitiba", "XBVP", "BOLSA DE VALORES DO PARANA", "", "www.bvpr.com.br", "Incorporated in Bovespa (www.bovespa.com.br)", "July 2006", "Deleted", false);
        add("BR", "Brazil", "Rio de Janeiro", "XBBF", "BOLSA BRASILIERA DE FUTUROS", "BBF", "", "Integrated into BM&F", "September 2007", "Deleted", false);
        add("BR", "Brazil", "Rio de Janeiro", "XRIO", "BOLSA DE VALORES DO RIO DE JANEIRO", "BVRJ", "www.bvrj.com.br", "Integrated into BOVESPA. BVRJ still exists as a company but its markets are managed by Bovespa (Bolsa de Valores de S?o Paulo - XBSP).", "September 2007", "Deleted", false);
        add("BR", "Brazil", "Sao Paulo", "XBMF", "BOLSA DE MERCADORIAS E FUTUROS", "BM&F", "www.bmf.com.br", "", "Before June 2005", "Active", true);
        add("BR", "Brazil", "Sao Paulo", "XBSP", "BOLSA DE VALORES DE SAO PAULO", "BOVESPA", "www.bovespa.com.br", "", "Before June 2005", "Active", true);
        add("BR", "Brazil", "Sao Paulo", "XSOM", "BOLSA DE VALORES DE SAO PAULO - SOMA", "SOMA", "www.bovespa.com.br", "Became the OTC segment of  BOVESPA. Incorporated in BOVESPA.", "November 2007", "Deleted", false);
        add("BG", "Bulgaria", "Sofia", "XBUL", "BULGARIAN STOCK EXCHANGE", "BSE", "www.bse-sofia.bg", "", "Before June 2005", "Active", true);
        add("CM", "Cameroon", "Douala", "XDSX", "DOUALA STOCK EXCHANGE", "", "www.douala-stock-exchange.com", "Registered market for equities", "May 2008", "Active", true);
        add("CA", "Canada", "Alberta", "XALB", "ALBERTA STOCK EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("CA", "Canada", "Mississauga", "OMGA", "OMEGA ATS", "", "http://www.omegaats.com/index.php", "OMEGA ATS is an Alternative Trading System (ATS) serving members of the Investment Dealers Association of Canada", "January 2008", "Active", true);
        add("CA", "Canada", "Montreal", "XMOD", "MONTREAL EXCHANGE THE / BOURSE DE MONTREAL", "CDE", "www.m-x.ca", "", "Before June 2005", "Active", true);
        add("CA", "Canada", "Montreal", "XMOO", "MONTREAL EXCHANGE THE / BOURSE DE MONTREAL", "ME", "www.m-x.ca", "Duplicate of XMOD, MONTREAL EXCHANGE THE / BOURSE DE MONTREAL (OTIONS AND OTHER DERIVATIVES)", "January 2005", "Deleted", false);
        add("CA", "Canada", "Toronto", "CANX", "CANNEX FINANCIAL EXCHANGE LTS", "CANNEX", "www.cannex.com", "", "Before June 2005", "Active", true);
        add("CA", "Canada", "Toronto", "CHIC", "CHI-X CANADA ATS", "", "www.chi-xcanada.com", "New market model focused on equal opportunity trading and market-level innovation to drive growth in the Canadian equity market", "February 2008", "Active", true);
        add("CA", "Canada", "Toronto", "MATN", "MATCH NOW", "", "www.triactcanada.com", "Marketplace for Registered Canadian Investment Dealers and their clients to trade Canadian listed equities", "January 2008", "Active", true);
        add("CA", "Canada", "Toronto", "PURE", "PURE TRADING", "", "www.puretrading.ca", "", "December 2006", "Active", true);
        add("CA", "Canada", "Toronto", "XBBK", "PERIMETER FINANCIAL CORP. - BLOCKBOOK ATS", "", "www.pfin.ca", "Alternative Trading System for block trading of Canadian listed equities", "May 2008", "Active", true);
        add("CA", "Canada", "Toronto", "XCNQ", "CANADA'S NEW STOCK EXCHANGE (CANADIAN TRADING AND QUOTING SYSTEM INC.)", "CNQ", "www.cnq.ca", "", "Before June 2005", "Active", true);
        add("CA", "Canada", "Toronto", "XTFE", "TORONTO FUTURES EXCHANGE", "", "www.tse.com", "Derivatives market for Canada consolidated at the Montreal Stock Exchange (XMOD).", "September 2006", "Deleted", false);
        add("CA", "Canada", "Toronto", "XTOE", "TORONTO OPTIONS EXCHANGE", "", "", "Derivatives market for Canada consolidated at the Montreal Stock Exchange (XMOD).", "September 2006", "Deleted", false);
        add("CA", "Canada", "Toronto", "XTSE", "TORONTO STOCK EXCHANGE", "TSE", "www.tse.com", "", "Before June 2005", "Active", true);
        add("CA", "Canada", "Toronto", "XTSX", "TSX VENTURE EXCHANGE", "TSX", "www.tsx.com", "", "Before June 2005", "Active", true);
        add("CA", "Canada", "Vancouver", "XTNX", "TSX VENTURE EXCHANGE - NEX", "NEX", "www.tsx.com/en/nex/index.html", "", "Before June 2005", "Active", true);
        add("CA", "Canada", "Vancouver", "XVSE", "VANCOUVER STOCK EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("CA", "Canada", "Winnipeg", "XWCE", "INTERCONTINENTAL EXCHANGE - ICE FUTURES CANADA", "ICE", "ww.theice.com", "Winnipeg Commodity Exchange became ICE Futures Canada since January 1st, 2008", "March 2008", "Active", true);
        add("CV", "Cape Verde", "Praia", "XBVC", "CAPE VERDE STOCK EXCHANGE", "BVC", "www.bvc.cv", "", "November 2005", "Active", true);
        add("KY", "Cayman Islands", "Georgetown", "XCAY", "CAYMAN ISLANDS STOCK EXCHANGE", "", "www.csx.com.ky", "", "Before June 2005", "Active", true);
        add("CL", "Chile", "Santiago", "XBCL", "LA BOLSA ELECTRONICA DE CHILE", "BOLCHILE", "www.bolchile.cl", "", "Before June 2005", "Active", true);
        add("CL", "Chile", "Santiago", "XSGO", "SANTIAGO STOCK EXCHANGE", "", "www.bolsadesantiago.com", "", "Before June 2005", "Active", true);
        add("CN", "China", "Dalian", "XDCE", "DALIAN COMMODITY EXCHANGE", "DCE", "www.dce.com.cn", "", "Before June 2005", "Active", true);
        add("CN", "China", "Shanghai", "CCFX", "CHINA FINANCIAL FUTURES EXCHANGE", "", "www.cffex.com.cn", "", "September 2007", "Active", true);
        add("CN", "China", "Shanghai", "SGEX", "SHANGHAI GOLD EXCHANGE", "SGE", "www.sge.sh", "", "June 2006", "Active", true);
        add("CN", "China", "Shanghai", "XCFE", "CHINA FOREIGN EXCHANGE TRADE SYSTEM", "CFETS", "www.chinamoney.com.cn", "", "Before June 2005", "Active", true);
        add("CN", "China", "Shanghai", "XSGE", "SHANGHAI FUTURES EXCHANGE", "SHFE", "www.shfe.com.cn", "", "Before June 2005", "Active", true);
        add("CN", "China", "Shanghai", "XSHG", "SHANGHAI STOCK EXCHANGE", "", "www.sse.com.cn", "", "Before June 2005", "Active", true);
        add("CN", "China", "Shenzhen", "XSHE", "SHENZHEN STOCK EXCHANGE", "", "www.szse.cn/main/en", "", "Before June 2005", "Active", true);
        add("CN", "China", "Shenzhen", "XSME", "SHENZHEN MERCANTILE EXCHANGE", "", "", "Does not exist anymore.", "September 2007", "Deleted", false);
        add("CN", "China", "Zhengzhou", "XZCE", "ZHENGZHOU COMMODITY EXCHANGE", "ZCE", "www.czce.com.cn", "", "Before June 2005", "Active", true);
        add("CO", "Colombia", "Bogota", "XBOG", "BOLSA DE VALORES DE COLOMBIA", "BVC", "www.bvc.com.co", "", "Before June 2005", "Active", true);
        add("CR", "Costa Rica", "San Jose", "XBNV", "BOLSA NACIONAL DE VALORES, S.A.", "BNV", "www.bnv.co.cr", "", "Before June 2005", "Active", true);
        add("HR", "Croatia", "Varazdin", "XVAR", "VARAZDIN STOCK EXCHANGE", "VSE", "www.vse.hr", "Merged into ZAGREB STOCK EXCHANGE (XZAG)", "September 2007", "Deleted", false);
        add("HR", "Croatia", "Zagreb", "XTRZ", "ZAGREB MONEY AND SHORT TERM SECURITIES MARKET INC", "", "www.trzistenovca.hr", "", "April 2007", "Active", true);
        add("HR", "Croatia", "Zagreb", "XZAG", "ZAGREB STOCK EXCHANGE", "", "www.zse.hr", "", "Before June 2005", "Active", true);
        add("CY", "Cyprus", "Nicosia (Lefkosia)", "XCYS", "CYPRUS STOCK EXCHANGE", "CSE", "www.cse.com.cy", "", "Before June 2005", "Active", true);
        add("CZ", "Czech Republic", "Prague", "XPRA", "THE PRAGUE STOCK EXCHANGE", "PSE", "www.pse.cz", "Description change.", "November 2007", "Active", true);
        add("CZ", "Czech Republic", "Prague", "XRMZ", "RM-SYSTEM A.S.", "RMS CZ", "www.rmsystem.cz", "", "October 2005", "Active", true);
        add("DK", "Denmark", "Copenhagen", "DAMP", "DANISH AUTHORISED MARKET PLACE LTD.", "Dansk AMP", "www.danskamp.dk", "", "October 2005", "Active", true);
        add("DK", "Denmark", "Copenhagen", "XCSE", "OMX NORDIC EXCHANGE COPENHAGEN A/S", "FUTOP", "www.cse.dk", "Modification of the name to match the current legal name.", "September 2007", "Active", true);
        add("DK", "Denmark", "Copenhagen", "XFND", "FIRST NORTH DENMARK", "", "www.omxgroup.com/firstnorth", "", "November 2007", "Active", true);
        add("DK", "Denmark", "Copenhagen", "XTRA", "XTRAMARKED", "", "www.xtramarked.dk", "Incorporated in OMX The Nordic Exchange", "December 2007", "Deleted", false);
        add("DK", "Denmark", "Horsens", "DKTC", "DANSK OTC", "", "www.danskotc.dk", "", "September 2007", "Active", true);
        add("DO", "Dominican Republic", "St Domingo", "XBVR", "BOLSA DE VALORES DE LA REPUBLICA DOMINICANA SA.", "BVRD", "www.bolsard.com", "", "Before June 2005", "Active", true);
        add("EC", "Ecuador", "Guayaquil", "XGUA", "GUAYAQUIL STOCK EXCHANGE", "", "www.mundobvg.com", "", "Before June 2005", "Active", true);
        add("EC", "Ecuador", "Quito", "XQUI", "QUITO STOCK EXCHANGE", "", "www.ccbvq.com", "", "Before June 2005", "Active", true);
        add("EG", "Egypt", "Cairo", "XCAI", "CAIRO AND ALEXANDRIA STOCK EXCHANGE", "CASE", "www.egyptse.com", "", "Before June 2005", "Active", true);
        add("SV", "El Salvador", "El Salvador", "XSVA", "EL SALVADOR STOCK EXCHANGE", "", "www.bves.com.sv", "", "Before June 2005", "Active", true);
        add("EE", "Estonia", "Tallinn", "XTAA", "FIRST NORTH ESTONIA", "", "www.firstnorthbaltic.omxgroup.com/", "", "November 2007", "Active", true);
        add("EE", "Estonia", "Tallinn", "XTAL", "TALLINN STOCK EXCHANGE - LISTING", "", "www.ee.omxgroup.com", "", "Before June 2005", "Active", true);
        add("EE", "Estonia", "Tallinn", "XTAR", "TALLINN STOCK EXCHANGE - REGULATED MARKET", "", "www.ee.omxgroup.com", "", "November 2007", "Active", true);
        add("FJ", "Fiji", "Suva", "XSPS", "SOUTH PACIFIC STOCK EXCHANGE", "SPSE", "www.spse.com.fj", "", "Before June 2005", "Active", true);
        add("FI", "Finland", "Helsinki", "FNFI", "FIRST NORTH FINLAND", "", "www.omxgroup.com/firstnorth", "", "March 2008", "Active", true);
        add("FI", "Finland", "Helsinki", "XFOM", "FINNISH OPTIONS MARKET", "FOM", "www.som.fi", "Closed.", "December 2007", "Deleted", false);
        add("FI", "Finland", "Helsinki", "XHEL", "OMX NORDIC EXCHANGE HELSINKI OY", "", "www.hex.com", "Modification of the name to match the current legal name.", "September 2007", "Active", true);
        add("FR", "France", "Paris", "ALXP", "NYSE EURONEXT - ALTERNEXT PARIS", "", "www.alternext.com", "", "August 2007", "Active", true);
        add("FR", "France", "Paris", "FMTS", "MTS FRANCE SAS", "", "www.mtsfrance.com", "", "November 2005", "Active", true);
        add("FR", "France", "Paris", "VRXP", "NYSE EURONEXT - COMPARTIMENT DES VALEURS RADIEES PARIS", "", "www.euronext.com", "This MTF has merged with NYSE Euronext Marche Libre Paris (XMLI) in January 2008", "March 2008", "Deleted", false);
        add("FR", "France", "Paris", "XAFR", "ALTERNATIVA FRANCE", "", "www.alternativa.fr", "Multilateral Trading Facility for unquoted stocks and funds", "June 2008", "Active", true);
        add("FR", "France", "Paris", "XBLN", "BLUENEXT - MTF", "", "www.bluenext.eu", "Environmental exchange MTF", "June 2008", "Modified", true);
        add("FR", "France", "Paris", "XFMN", "SOCIETE DU NOUVEAU MARCHE", "", "www.nouveau-marche.fr", "Does not exist anymore.", "June 2007", "Deleted", false);
        add("FR", "France", "Paris", "XMAT", "EURONEXT PARIS MATIF", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("FR", "France", "Paris", "XMLI", "NYSE EURONEXT - MARCHE LIBRE PARIS", "EURONEXT", "www.euronext.com", "", "Before June 2006", "Active", true);
        add("FR", "France", "Paris", "XMON", "EURONEXT PARIS MONEP", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("FR", "France", "Paris", "XPAR", "NYSE EURONEXT - EURONEXT PARIS", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("FR", "France", "Paris", "XPOW", "POWERNEXT", "", "www.powernext.fr", "", "Before June 2005", "Active", true);
        add("GE", "Georgia", "Tbilisi", "XGSE", "GEORGIA STOCK EXCHANGE", "GSE", "www.gse.ge", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Berlin", "BERA", "BORSE BERLIN - REGULIERTER MARKT", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Berlin", "BERB", "BORSE BERLIN - FREIVERKHER", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Berlin", "EQTA", "BORSE BERLIN EQUIDUCT TRADING - REGULIERTER MARKT", "", "www.berlin-boerse.de/die-gruppe/equiduct-trading.asp", "Electronic trading system - Meet MiFID requirements", "May 2008", "Active", true);
        add("DE", "Germany", "Berlin", "EQTB", "BORSE BERLIN EQUIDUCT TRADING - FREIVERKEHR", "", "www.berlin-boerse.de/die-gruppe/equiduct-trading.asp", "Electronic trading system - Meet MiFID requirements", "May 2008", "Active", true);
        add("DE", "Germany", "Berlin", "XBER", "B?RSE BERLIN", "", "www.boerse-berlin.de", "Modification of the name.", "September 2007", "Active", true);
        add("DE", "Germany", "Berlin", "XEQT", "BORSE BERLIN EQUIDUCT TRADING", "", "www.berlin-boerse.de/die-gruppe/equiduct-trading.asp", "Electronic trading system - Meet MiFID requirements", "May 2008", "Active", true);
        add("DE", "Germany", "Berlin", "XGAT", "TRADEGATE AG", "", "www.tradegate.de", "", "March 2007", "Active", true);
        add("DE", "Germany", "Berlin", "ZOBX", "ZOBEX", "ZOBEX", "www.berlinerboerse.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Bremen", "XBRE", "BREMER WERTPAPIERBOERSE", "", "www.boerse-bremen.de", "Merged with Borse Berlin.", "September 2007", "Deleted", false);
        add("DE", "Germany", "Duesseldorf", "XDUS", "B?RSE DUSSELDORF", "", "www.boerse-duesseldorf.de", "Modification of the name.", "September 2007", "Active", true);
        add("DE", "Germany", "Dusseldorf", "DUSA", "BORSE DUSSELDORF - REGULIERTER MARKT", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Dusseldorf", "DUSB", "BORSE DUSSELDORF - FREIVERKEHR", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Dusseldorf", "DUSC", "BORSE DUSSELDORF - QUOTRIX", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Frankfurt", "360T", "360T", "", "www.360T.com", "Electronic trading platform for OTC derivatives.", "September 2007", "Active", true);
        add("DE", "Germany", "Frankfurt", "ECAG", "EUREX CLEARING AG", "", "www.eurexchange.com", "", "November 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "FRAA", "BORSE FRANKFURT - REGULIERTER MARKT", "", "", "", "March 2008", "Active", true);
        add("DE", "Germany", "Frankfurt", "FRAB", "BORSE FRANKFURT - FREIVERKEHR", "", "", "", "March 2008", "Active", true);
        add("DE", "Germany", "Frankfurt", "GMTS", "MTS DEUTSCHLAND AG", "", "www.mtsgermany.com", "", "November 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XEEE", "EUROPEAN ENERGY EXCHANGE AG", "", "www.eex.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XETA", "XETRA - REGULIERTER MARKT", "XETRA", "www.scoach.de", "", "March 2008", "Active", true);
        add("DE", "Germany", "Frankfurt", "XETB", "XETRA - FREIVERKEHR", "XETRA", "www.scoach.be", "", "March 2008", "Active", true);
        add("DE", "Germany", "Frankfurt", "XETR", "XETRA", "XETRA", "www.deutsche-boerse.com", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XEUB", "EUREX BONDS", "EUREX", "www.eurex-bonds.com", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XEUP", "EUREX REPO GMBH", "", "www.eurexchange.com", "", "November 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XEUR", "EUREX DEUTSCHLAND", "EUREX", "www.eurexchange.com", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XFRA", "DEUTSCHE BOERSE AG", "", "www.scoatch.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Frankfurt", "XNEW", "NEWEX", "NEWEX", "www.newex.com", "NEWEX is in Frankfurt.", "September 2006", "Active", true);
        add("DE", "Germany", "Frankfurt", "XRTR", "RTR (REUTERS-REALTIME-DATEN)", "RTR", "www.wtb-hannover.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Frankfurt Am Main", "XDTB", "DTB DEUTSCHE TERMINBOERSE GMBH", "", "", "", "April 2003", "Deleted", false);
        add("DE", "Germany", "Frankfurt Am Main", "XDWZ", "DEUTSCHE BOERSE AG, FRANKFURT AM MAIN", "", "", "", "January 2004", "Deleted", false);
        add("DE", "Germany", "Hamburg", "HAMA", "BORSE HAMBURG - REGULIERTER MARKT", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Hamburg", "HAMB", "BORSE HAMBURG HAMBURG - FREIVERKEHR", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Hamburg", "XHAM", "HANSEATISCHE WERTPAPIERBOERSE HAMBURG", "", "www.boersenag.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Hannover", "HANA", "BORSE HANNOVER - REGULIERTER MARKT", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Hannover", "HANB", "BORSE HANNOVER - FREIVERKEHR", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Hannover", "XHAN", "NIEDERSAECHSISCHE BOERSE ZU HANNOVER", "", "www.boersenag.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Hannover", "XHCE", "RISK MANAGEMENT EXCHANGE", "RMX", "www.wtb-hannover.de", "Institution description modified from WARENTERMINBOERSE HANNOVER to become RISK MANAGEMENT EXCHANGE", "June 2007", "Active", true);
        add("DE", "Germany", "Muenchen", "XMUN", "B?RSE MUNCHEN", "", "www.boerse-muenchen.de", "Modification of the name.", "September 2007", "Active", true);
        add("DE", "Germany", "Munchen", "MUNA", "BORSE MUNCHEN - REGULIERTER MARKT", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Munchen", "MUNB", "BORSE MUNCHEN - FREIVERKEHR", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Stuttgart", "EUWX", "EUWAX", "EUWAX", "www.euwax.de", "", "Before June 2005", "Active", true);
        add("DE", "Germany", "Stuttgart", "STUA", "BORSE STUTTGART - REGULIERTER MARKT", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Stuttgart", "STUB", "BORSE STUTTGART - FREIVERKEHR", "", "", "MiFID requirement", "February 2008", "Active", true);
        add("DE", "Germany", "Stuttgart", "XSTU", "B?RSE STUTTGART", "", "www.boerse-stuttgart.de", "Modification of the name.", "September 2007", "Active", true);
        add("GH", "Ghana", "Accra", "XGHA", "GHANA STOCK EXCHANGE", "", "www.gse.com.gh", "", "Before June 2005", "Active", true);
        add("GR", "Greece", "Athens", "ENAX", "ATHENS EXCHANGE ALTERNATIVE MARKET", "ENAX", "www.athex.gr", "ENAX, is an alternative trading market dedicated to small companies.", "November 2007", "Active", true);
        add("GR", "Greece", "Athens", "HDAT", "ELECTRONIC SECONDARY SECURITIES MARKET (HDAT)", "HDAT", "www.bankofgreece.gr", "", "Before June 2005", "Active", true);
        add("GR", "Greece", "Athens", "XADE", "ATHENS EXCHANGE S.A. DERIVATIVES MARKET", "ATHEXD", "www.athex.gr", "Description and webiste change.", "November 2007", "Active", true);
        add("GR", "Greece", "Athens", "XATH", "ATHENS EXCHANGE S.A. CASH MARKET", "ATHEXC", "www.athex.gr", "Description and webiste change.", "November 2007", "Active", true);
        add("GT", "Guatemala", "Guatemala", "XGTG", "BOLSA DE VALORES NACIONAL SA", "", "www.bvnsa.com.gt", "", "Before June 2005", "Active", true);
        add("GG", "Guernsey, C.I.", "St.  Peter Port", "XCIE", "CHANNEL ISLANDS STOCK EXCHANGE", "CISX", "www.cisx.com", "", "Before June 2005", "Active", true);
        add("HN", "Honduras", "San Pedro Sula", "XHON", "HONDURIAN STOCK EXCHANGE", "BVH", "www.bhv.hn", "", "Before June 2005", "Active", true);
        add("HN", "Honduras", "Tegucigalpa", "XBCV", "BOLSA CENTROAMERICANA DE VALORES S.A.", "BCV", "www.bcv.hn", "", "Before June 2005", "Active", true);
        add("HK", "Hong Kong", "Hong Kong", "XCGS", "CHINESE GOLD & SILVER EXCHANGE SOCIETY", "", "www.cgse.com.hk", "", "April 2006", "Active", true);
        add("HK", "Hong Kong", "Hong Kong", "XGEM", "HONG KONG GROWTH ENTERPRISES MARKET", "HK GEM", "www.hkgem.com", "", "Before June 2005", "Active", true);
        add("HK", "Hong Kong", "Hong Kong", "XHKF", "HONG KONG FUTURES EXCHANGE LTD.", "HKFE", "www.hkfe.com", "", "Before June 2005", "Active", true);
        add("HK", "Hong Kong", "Hong Kong", "XHKG", "STOCK EXCHANGE OF HONG KONG LTD", "SEHK", "www.sehk.com.hk", "", "Before June 2005", "Active", true);
        add("HU", "Hungary", "Budapest", "XBCE", "BUDAPEST COMMODITY EXCHANGE", "BCE", "www.bce-bat.com", "Commodity business transferred to the Budapest Stock Exchange (XBUD)", "September 2006", "Deleted", false);
        add("HU", "Hungary", "Budapest", "XBUD", "BUDAPEST STOCK EXCHANGE", "", "www.bet.hu", "", "Before June 2005", "Active", true);
        add("IS", "Iceland", "Reykjavik", "ISEC", "FIRST NORTH ICELAND", "isec", "www.omxgroup.com/firstnorth", "Rebranding from ICEX into FIRST NORTH.", "September 2007", "Active", true);
        add("IS", "Iceland", "Reykjavik", "XICE", "OMX NORDIC EXCHANGE ICELAND HF.", "ICEX", "www.icex.is", "Modification of the name to match the current legal name.", "September 2007", "Active", true);
        add("IN", "India", "Ahmedabad", "NMCE", "NATIONAL MULTI-COMMODITY EXCHANGE OF INDIA", "", "www.nmce.com/main.jsp", "De-Mutualised Electronic Multi-Commodity Exchange.", "November 2007", "Active", true);
        add("IN", "India", "Bangalore", "XBAN", "BANGALORE STOCK EXCHANGE LTD", "", "www.karnataka.com/stock/bgse.shtml", "", "Before June 2005", "Active", true);
        add("IN", "India", "Calcutta", "XCAL", "CALCUTTA STOCK EXCHANGE", "", "www.cse-india.com", "", "Before June 2005", "Active", true);
        add("IN", "India", "Delhi", "XDES", "DELHI STOCK EXCHANGE", "", "business.vsnl.com/dse/", "", "Before June 2005", "Active", true);
        add("IN", "India", "Madras", "XMDS", "MADRAS STOCK EXCHANGE", "", "", "", "Before June 2005", "Active", true);
        add("IN", "India", "Mumbai", "ISEX", "INTER-CONNECTED STOCK EXCHANGE OF INDIA LTD", "ISE", "www.iseindia.com", "", "September 2007", "Active", true);
        add("IN", "India", "Mumbai", "OTCX", "OTC EXCHANGE OF INDIA", "OTCEI", "www.otcei.net", "", "June 2006", "Active", true);
        add("IN", "India", "Mumbai", "XBOM", "MUMBAI STOCK EXCHANGE", "MSE", "www.bseindia.com/index_op.htm", "", "Before June 2005", "Active", true);
        add("IN", "India", "Mumbai", "XIMC", "MULTI COMMODITY EXCHANGE OF INDIA LTD.", "MCX", "www.mcxindia.com", "", "April 2006", "Active", true);
        add("IN", "India", "Mumbai", "XNCD", "NATIONAL COMMODITY & DERIVATIVES EXCHANGE LTD", "NCDEX", "www.ncdex.com", "", "April 2006", "Active", true);
        add("IN", "India", "Mumbai", "XNSE", "NATIONAL STOCK EXCHANGE OF INDIA", "NSE", "www.nseindia.com", "", "Before June 2005", "Active", true);
        add("ID", "Indonesia", "Jakarta", "XBBJ", "JAKARTA FUTURES EXCHANGE (BURSA BERJANGKA JAKARTA)", "BBJ", "www.bbj-jfx.com", "", "Before June 2005", "Active", true);
        add("ID", "Indonesia", "Jakarta", "XIDX", "INDONESIA STOCK EXCHANGE", "IDX", "www.idx.co.id", "The Jakarta (JSX) and Surabaya (SSX) Stock Exchanges have merged to become the Indonesia Stock Exchange (IDX)", "April 2008", "Active", true);
        add("ID", "Indonesia", "Jakarta", "XJKT", "JAKARTA STOCK EXCHANGE", "JSX", "www.jsx.co.id", "The Jakarta (JSX) and Surabaya (SSX) Stock Exchanges have merged to become the Indonesia Stock Exchange (IDX). MIC to use is XIDX", "April 2008", "Deleted", false);
        add("ID", "Indonesia", "Jakarta", "XJNB", "JAKARTA NEGOTIATED BOARD", "", "", "", "Before June 2005", "Active", true);
        add("ID", "Indonesia", "Surabaya", "XSUR", "SURABAYA STOCK EXCHANGE", "SSC", "www.bes.co.id", "The Jakarta (JSX) and Surabaya (SSX) Stock Exchanges have merged to become the Indonesia Stock Exchange (IDX). MIC to use is XIDX", "April 2008", "Deleted", false);
        add("IR", "Iran", "Tehran", "XTEH", "TEHRAN STOCK EXCHANGE", "TSE", "www.tse.ir", "Description change. Typo corrected.", "November 2007", "Active", true);
        add("IQ", "Iraq", "Baghdad", "XIQS", "IRAK STOCK EXCHANGE", "ISX", "www.isx-iq.net", "", "September 2006", "Active", true);
        add("IE", "Ireland", "Dublin", "XASM", "IRISH STOCK EXCHANGE - ALTERNATIVE SECURITIES MARKET", "ASM", "www.ise.ie", "Market operated and regulated by the Irish Stock Exchange for debt and derivative securities. Description modification.", "October 2007", "Active", true);
        add("IE", "Ireland", "Dublin", "XCDE", "BAXTER FINANCIAL SERVICES", "", "www.baxter-fx.com", "Electronic trading platform for Currency Futures EFP's and OTC Currency trading", "May 2008", "Active", true);
        add("IE", "Ireland", "Dublin", "XDUB", "IRISH STOCK EXCHANGE - MAIN MARKET", "ISE", "www.ise.ie", "Description modification.", "October 2007", "Active", true);
        add("IE", "Ireland", "Dublin", "XFNX", "FINEX (NEW YORK AND DUBLIN)", "FINEX", "www.finex.com", "", "Before June 2005", "Active", true);
        add("IE", "Ireland", "Dublin", "XIEX", "IRISH ENTERPRISE EXCHANGE", "IEX", "www.ise.ie", "Market operated and regulated by the Irish Stock Exchange for securities of small to medium-sized companies.", "August 2007", "Active", true);
        add("IE", "Ireland", "Dublin", "XPOS", "POSIT", "POSIT", "www.itg.com/compliance.php", "", "July 2007", "Active", true);
        add("IL", "Israel", "Tel Aviv", "XTAE", "TEL AVIV STOCK EXCHANGE", "TASE", "www.tase.co.il", "", "Before June 2005", "Active", true);
        add("IT", "Italy", "Milano", "EMDR", "E-MID - E-MIDER MARKET", "", "www.e-mid.it", "Market for the trading of multi-currency derivative financial instruments", "June 2008", "Active", true);
        add("IT", "Italy", "Milano", "ETFP", "ELECTRONIC OPEN-END FUNDS AND ETC MARKET", "ETFplus", "www.borsaitaliana.it", "ETFplus is the Borsa Italiana electronic regulated market where, through authorised intermediaries, it is possible to trade the following financial instruments: ETFs (Exchange Traded Funds), ETCs (Exchange Traded Commodities),Structured ETFs.", "September 2007", "Active", true);
        add("IT", "Italy", "Milano", "ETLX", "EUROTLX", "", "www.eurotlx.com", "Multilateral Trading Facility for bonds and equities.", "August 2007", "Active", true);
        add("IT", "Italy", "Milano", "EXPA", "EXPANDI MARKET", "", "www.borsaitaliana.it", "Borsa Italiana?s Expandi market is created for those small and mid size companies operating in traditional sectors, with success in their respective fields, who can demonstrate a consolidated economical - financial track record.", "September 2007", "Active", true);
        add("IT", "Italy", "Milano", "HMTF", "HI-MTF", "", "www.himtf.com", "Multilateral Trading Facilities for bonds and equities.", "November 2007", "Active", true);
        add("IT", "Italy", "Milano", "MACX", "MERCATO ALTERNATIVO DEL CAPITALE", "MAC", "www.borsaitaliana.it", "The Alternative Capital Market (MAC), organised and managed by Borsa Italiana, is an alternative trading system dedicated to small entreprises.", "October 2007", "Active", true);
        add("IT", "Italy", "Milano", "MOTX", "ELECTRONIC BOND MARKET", "MOT", "www.borsaitaliana.it", "Mot is Borsa Italiana?s Electronic Bond and Government Securities Market for the trading of: Government Securities (BOT; BTP; BTPi; CCT; CTZ); Local Authority bonds; Bank and corporate \"plain vanilla\" and structured non convertible bonds; Eurobonds, foreign bonds and asset backed securities.", "September 2007", "Active", true);
        add("IT", "Italy", "Milano", "MTAA", "ELECTRONIC SHARE MARKET", "MTA", "www.borsaitaliana.it", "Borsa Italiana?s electronic market on which shares, convertible bonds, warrants and option rights are traded.", "September 2007", "Active", true);
        add("IT", "Italy", "Milano", "MTAX", "MTAX", "MTAX", "www.borsaitaliana.it", "MTAX is Borsa Italiana electronic markets on which shares, convertible bonds, warrants and option rights are traded. Unification of MTA and MTAX on March 3rd 2008. MTAA is the MIC to use.", "April 2008", "Deleted", false);
        add("IT", "Italy", "Milano", "SEDX", "SECURITISED DERIVATIVES MARKET", "SeDeX", "www.borsaitaliana.it", "SeDeX is Borsa Italiana's electronic regulated market where, through authorised intermediaries, it is possible to trade securitised derivatives, i.e. so-called derivatives with leverage effect (covered warrants and leverage certificates) and derivatives without leverage effect which fit medium-long term investment logics (investment certificates).", "September 2007", "Active", true);
        add("IT", "Italy", "Milano", "TLAB", "TRADINGLAB", "TLX", "www.tradinglab.it", "Does not longer exist as such.", "August 2007", "Deleted", false);
        add("IT", "Italy", "Milano", "XDMI", "ITALIAN DERIVATIVES MARKET", "IDEM", "www.borsaitaliana.it", "", "Before June 2005", "Active", true);
        add("IT", "Italy", "Milano", "XMIF", "MERCATO ITALIANO DEI FUTURES", "MIF", "www.tesoro.it", "Closed", "September 2007", "Deleted", false);
        add("IT", "Italy", "Milano", "XMIL", "BORSA ITALIANA S.P.A.", "", "www.borsaitalia.it", "At the request of the exchange. Segment MICs should be used instead.", "December 2007", "Deleted", false);
        add("IT", "Italy", "Milano", "XTLX", "TLX", "", "www.eurotlx.com", "Regulated market for bonds and equities.", "August 2007", "Active", true);
        add("IT", "Italy", "Roma", "BOND", "BONDVISION", "", "www.bondvision.net", "", "November 2005", "Active", true);
        add("IT", "Italy", "Roma", "MTSC", "MTS S.P.A.", "MTS Italy", "www.mtsspa.it", "", "November 2005", "Active", true);
        add("IT", "Italy", "Roma", "MTSM", "MTS CORPORATE MARKET", "MTS Italy", "www.mtsspa.it", "MTS S.p.A., a regulated Market, provides wholesale electronic trading of Italian government bonds and other types of fixed income securities", "January 2008", "Active", true);
        add("IT", "Italy", "Roma", "SSOB", "SSO BONDVISION", "", "www.bondvision.net", "Bondvision is a regulated market. SSO Bondvision is an ATS. See www.bondvision.net/new/content/functionality/market_rules.php for explanations and market rules.", "October 2007", "Active", true);
        add("CI", "Ivory Coast", "Abidjan", "XABJ", "BOURSE DES VALEURS ABIDJAN", "", "", "", "January 2004", "Deleted", false);
        add("CI", "Ivory Coast", "Abidjan", "XBRV", "BOURSE REGIONALE DES VALEURS MOBILIERES", "BRVM", "www.brvm.org/", "", "Before June 2005", "Active", true);
        add("JM", "Jamaica", "Kingston", "XJAM", "JAMAICA STOCK EXCHANGE", "", "www.jamstockex.com/", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Aichi", "XTKA", "TOYOHASHI KANKEN TORIHIKIJO (DRIED COCOON EXCHANGE) - CHUBU COMMODITY EXCHANGE", "", "", "All CHUBU COMMODITY EXCHANGES are part of the renamed CENTRAL JAPAN COMMODITY EXCHANGE (XNKS)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Fukuoka", "XFFE", "FUKUOKA FUTURES EXCHANGE", "", "www.ffe.or.jp/", "Absorbed by Kansai Commodity Exchange (XKAC)", "September 2007", "Deleted", false);
        add("JP", "Japan", "Fukuoka", "XFKA", "FUKUOKA STOCK EXCHANGE", "", "www.fse.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Hiroshima", "XHIR", "HIROSHIMA STOCK EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("JP", "Japan", "Kobe", "XKGT", "KOBE GOMU TORIHIKIJO (RUBBER EXCHANGE)", "KRE", "", "Part of the OSAKA MERCANTILE EXCHANGE (XOSM)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Kobe", "XKKT", "KOBE KIITO TORIHIKIJO (RAW SILK EXCHANGE)", "KSE", "", "Merged with KANSAI AGRICULTURAL COMMODITIES EXCHANGE into the KANSAI COMMODITIES EXCHANGE (XKAC)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Kyoto", "XKYO", "KYOTO STOCK EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("JP", "Japan", "Nagoya", "XCCE", "CHUBU COMMODITY EXCHANGE", "C-COM", "", "All CHUBU COMMODITY EXCHANGES are part of the renamed CENTRAL JAPAN COMMODITY EXCHANGE (XNKS)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Nagoya", "XNGO", "NAGOYA STOCK EXCHANGE", "NSE", "www.nse.or.jp/e/index.html", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Nagoya", "XNKC", "CENTRAL JAPAN COMMODITY EXCHANGE", "", "", "Merged of all CHUBU COMMODITY EXCHANGES and renaming into CENTRAL JAPAN COMMODITY EXCHANGE (XNKS)", "", "", false);
        add("JP", "Japan", "Nagoya", "XNKS", "CENTRAL JAPAN COMMODITIES EXCHANGE", "C-COM", "www.c-com.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Nagoya", "XNST", "NAGOYA SENI TORIHIKIJO (TEXTILE EXCHANGE) - CHUBU COMMODITY EXCHANGE", "", "", "All CHUBU COMMODITY EXCHANGES are part of the renamed CENTRAL JAPAN COMMODITY EXCHANGE (XNKS)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Nigita", "XNII", "NIIGATA STOCK EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("JP", "Japan", "Osaka", "XHER", "NIPPON NEW MARKET - HERCULES", "HERCULES", "hercules.ose.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Osaka", "XKAC", "KANSAI COMMODITIES EXCHANGE", "KANEX", "www.kanex.or.jp", "Description change from KANSAI AGRICULTURAL COMMODITIES EXCHANGE following merger with KOBE KIITO TORIHIKIJO (RAW SILK EXCHANGE) (XKKT)", "Before June 2005", "Active", true);
        add("JP", "Japan", "Osaka", "XOSE", "OSAKA SECURITIES EXCHANGE", "OSE", "www.ose.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Osaka", "XOSJ", "OSAKA SECURITIES EXCHANGE J-NET", "J-NET", "www.ose.or.jp", "", "January 2006", "Active", true);
        add("JP", "Japan", "Osaka", "XOSM", "OSAKA MERCANTILE EXCHANGE", "OME", "www.osamex.com", "Merged with CENTRAL JAPAN COMMODITIES EXCHANGE (XNKS)", "September 2007", "Deleted", false);
        add("JP", "Japan", "Osaka", "XOST", "OSAKA SENI TORIHIKIJO (TEXTILE EXCHANGE)", "", "", "Part of the OSAKA MERCANTILE EXCHANGE (XOSM)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Sapporo", "XSAP", "SAPPORO SECURITIES EXCHANGE", "", "www.sse.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Shimonoseki", "XKST", "KANMON SHOHIN TORIHIKIJO (COMMODITY EXCHANGE)", "", "", "Duplicate with FUKUOKA FUTURES EXCHANGE (XFFE)", "August 2004", "Deleted", false);
        add("JP", "Japan", "Tokyo", "JASR", "JAPANCROSSING", "", "www.instinet.com", "Correction of the name.", "September 2007", "Active", true);
        add("JP", "Japan", "Tokyo", "SBIJ", "JAPANNEXT PTS", "", "www.japannext.co.jp", "", "April 2008", "Active", true);
        add("JP", "Japan", "Tokyo", "XJAS", "JASDAQ SECURITIES EXCHANGE", "JASDAQ", "www.jasdaq.co.jp", "Name change from JASDAQ into JASDAQ SECURITIES EXCHANGE", "Before June 2005", "Active", true);
        add("JP", "Japan", "Tokyo", "XTFF", "TOKYO FINANCIAL  EXCHANGE", "TFX", "www.tfx.co.jp", "Modification of the name.", "September 2007", "Active", true);
        add("JP", "Japan", "Tokyo", "XTK1", "TOKYO STOCK EXCHANGE - TOSTNET-1", "", "www.tse.or.jp", "", "January 2006", "Active", true);
        add("JP", "Japan", "Tokyo", "XTK2", "TOKYO STOCK EXCHANGE - TOSTNET-2", "", "www.tse.or.jp", "", "January 2006", "Active", true);
        add("JP", "Japan", "Tokyo", "XTKO", "TOKYO KOKUMOTSU SHOHIN TORIHIKIJO (GRAIN EXCHANGE)", "TGE", "www.tge.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Tokyo", "XTKS", "TOKYO STOCK EXCHANGE", "TSE", "www.tse.or.jp", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Tokyo", "XTKT", "TOKYO KOGYOIN TORIHIKIJO (COMMODITY EXCHANGE)", "TOCOM", "www.tocom.or.jp/", "", "Before June 2005", "Active", true);
        add("JP", "Japan", "Tokyo", "XYKT", "YOKOHAMA COMMODITY EXCHANGE (WRONGLY RENAMED TOKYO GRAIN EXCHANGE SEPT2006)", "TGE", "www.tge.or.jp", "YOKOHAMA COMMODITY EXCHANGE is integrated in Tokyo Grain Exchange Duplicate of XTKO, TOKYO KOKUMOTSU SHOHIN TORIHIKIJO (GRAIN EXCHANGE). XYKT (YOKOHAMA COMMODITY EXCHANGE), that was integrated to the Tokyo Grain exchange, should have been deleted instead of renamed.", "November 2006", "Deleted", false);
        add("JO", "Jordan", "Amman", "XAMM", "AMMAN STOCK EXCHANGE", "ASE", "www.ammanstockex.com", "", "Before June 2005", "Active", true);
        add("KZ", "Kazakhstan", "Alma-Ata", "XKAZ", "KAZAKHSTAN STOCK EXCHANGE", "KAZE", "www.kase.kz/eng/", "Name change from CENTRAL ASIAN STOCK EXCHANGE to KAZAKHSTAN STOCK EXCHANGE", "Before June 2005", "Active", true);
        add("KE", "Kenya", "Nairobi", "XNAI", "NAIROBI STOCK EXCHANGE", "", "www.nse.co.ke", "", "Before June 2005", "Active", true);
        add("KR", "Korea, Republic of", "Seoul", "KOCN", "KOREA ECN SECURITIES CO. LTD (ATS)", "", "www.seoulfn.co.kr", "Closed in 2005", "September 2007", "Deleted", false);
        add("KR", "Korea, Republic of", "Seoul", "XKFE", "KOREA EXCHANGE (FUTURES MARKET)", "KRX FM", "http://fm.krx.co.kr/english/index.html", "Consolidated into the Korea Exchange (XKRX)", "November 2006", "Active", true);
        add("KR", "Korea, Republic of", "Seoul", "XKOR", "KOREA STOCK EXCHANGE", "KSE", "sm.krx.co.kr", "Consolidated into the Korea Exchange (XKRX)", "February 2006", "Deleted", false);
        add("KR", "Korea, Republic of", "Seoul", "XKOS", "KOREA EXCHANGE (KOSDAQ)", "KOSDAQ", "http://english.kosdaq.com", "Consolidated into the Korea Exchange (XKRX)", "November 2006", "Active", true);
        add("KR", "Korea, Republic of", "Seoul", "XKRX", "KOREA EXCHANGE (STOCK MARKET)", "KRX SM", "http://sm.krx.co.kr/webeng/", "KRX has three divisions, Stock Market, Kosdaq and Futures Market. A need to have a MIC for each division exists.", "November 2006", "Active", true);
        add("KW", "Kuwait", "Kuwait", "XKUW", "KUWAIT STOCK EXCHANGE", "", "www.kuwaitse.com", "KUWAIT STOCK EXCHANGE deleted by mistake in version 1.29 is put back I the list with original MIC XKUW, NOT XKSE as announced on 01 March 2007", "March 5th 2007", "Active", true);
        add("KG", "Kyrgyzstan", "Bishkek", "XKSE", "KYRGYZ STOCK EXCHANGE", "KSE", "www.kse.kg", "KYRGYZ STOCK EXCHANGE deleted by mistake in v1.29 is put back in the list with its original MIC XKSE.", "March 5th 2007", "Active", true);
        add("LV", "Latvia", "Riga", "FNLV", "FIRST NORTH LATVIA", "", "www.firstnorthbaltic.omxgroup.com", "", "March 2008", "Active", true);
        add("LV", "Latvia", "Riga", "XRIS", "OMX NORDIC EXCHANGE RIGA", "", "www.lv.omxgroup.com", "Description change.", "November 2007", "Active", true);
        add("LB", "Lebanon", "Beyrouth", "XBEY", "BOURSE DE BEYROUTH", "", "www.bse.com.lb", "", "Before June 2005", "Active", true);
        add("LT", "Lithuania", "Vilnius", "XLIT", "OMX NORDIC EXCHANGE VILNIUS", "", "www.lt.omxgroup.com", "Modification of the name to match the current legal name. Description change.", "November 2007", "Active", true);
        add("LT", "Lithuania", "Vilnius", "XVIA", "ALTERNATIVE MARKET-FIRST NORTH LITHUANIA", "", "www.firstnorthbaltic.omxgroup.com", "Alternative market related to the Vilnius Stock Exchange (OMX Group)", "February 2008", "Active", true);
        add("LU", "Luxembourg", "Luxembourg", "CCLX", "CENTRALE DE COMMUNICATIONS LUXEMBOURG S.A.", "CCLux", "www.cclux.lu", "", "Before June 2005", "Active", true);
        add("LU", "Luxembourg", "Luxembourg", "EMTF", "EURO MTF", "", "www.bourse.lu", "see http://www.bourse.lu/application?_flowId=PageStatiqueFlow&content=services/Admission.jsp", "Before June 2005", "Active", true);
        add("LU", "Luxembourg", "Luxembourg", "XLUX", "LUXEMBOURG STOCK EXCHANGE", "", "www.bourse.lu", "", "Before June 2005", "Active", true);
        add("LU", "Luxembourg", "Luxembourg", "XVES", "VESTIMA+", "", "www.vestima.com", "", "Before June 2005", "Active", true);
        add("MK", "Macedonia", "Skopje", "XMAE", "MACEDONIAN STOCK EXCHANGE", "", "www.mse.org.mk", "", "Before June 2005", "Active", true);
        add("MG", "Madagascar", "Antananarivo", "XMDG", "MARCHE INTERBANCAIRE DES DEVISES M.I.D.", "", "", "", "Before June 2005", "Active", true);
        add("MW", "Malawi", "Blantyre", "XMSW", "MALAWI STOCK EXCHANGE", "", "www.mse.co.mw", "", "Before June 2005", "Active", true);
        add("MY", "Malaysia", "Kuala Lumpur", "XKLS", "BURSA MALAYSIA", "KLSE", "www.klse.com.my", "Name change from KUALA LUMPUR STOCK EXCHANGE, THE to BURSA MALAYSIA", "Before June 2005", "Active", true);
        add("MY", "Malaysia", "Kuala Lumpur", "XLOF", "MALAYSIA DERIVATIVES EXCHANGE BHD", "MDEX", "www.mdex.com.my", "", "Before June 2005", "Active", true);
        add("MY", "Malaysia", "Kuala Lumpur", "XRBM", "RINGGIT BOND MARKET", "RBM", "rmbond.bnm.gov.my", "", "Before June 2005", "Active", true);
        add("MY", "Malaysia", "Labuan", "XLFX", "LABUAN INTERNATIONAL FINANCIAL EXCHANGE", "LFX", "lfxsys.lfx.com.my/index.asp", "", "Before June 2005", "Active", true);
        add("MT", "Malta", "Valletta", "XMAL", "MALTA STOCK EXCHANGE", "", "www.borzamalta.com.mt", "", "Before June 2005", "Active", true);
        add("MU", "Mauritius", "Port Louis", "XMAU", "STOCK EXCHANGE OF MAURITIUS LTD", "", "www.stockexchangeofmauritius.com", "", "Before June 2005", "Active", true);
        add("MX", "Mexico", "Mexico", "XEMD", "MERCADO MEXICANO DE DERIVADOS", "MEXDER", "www.mexder.com.mx", "", "Before June 2005", "Active", true);
        add("MX", "Mexico", "Mexico", "XMEX", "BOLSA MEXICANA DE VALORES (MEXICAN STOCK EXCHANGE)", "", "www.bmv.com.mx", "", "Before June 2005", "Active", true);
        add("MD", "Moldova, Republic of", "Chisinau", "XMOL", "MOLDOVA STOCK EXCHANGE", "", "www.moldse.md", "", "Before June 2005", "Active", true);
        add("MN", "Mongolia", "Ulaan Baatar", "XULA", "MONGOLIAN STOCK EXCHANGE", "", "www.mse.mn", "", "Before June 2005", "Active", true);
        add("ME", "Montenegro, Republic of", "Montenegro", "XMNX", "MONTENEGRO STOCK EXCHANGE", "", "www.montenegroberza.com", "", "December 2006", "Active", true);
        add("MA", "Morocco", "Casablanca", "XCAS", "CASABLANCA STOCK EXCHANGE", "", "www.casablanca-bourse.com", "", "Before June 2005", "Active", true);
        add("MZ", "Mozambique", "Maputo", "XMAP", "MAPUTO STOCK  EXCHANGE", "", "www.mbendi.co.za/exmz.htm", "", "Before June 2005", "Active", true);
        add("NA", "Namibia", "Windhoek", "XNAM", "NAMIBIAN STOCK EXCHANGE", "", "www.nsx.com.na", "", "Before June 2005", "Active", true);
        add("NP", "Nepal", "Kathmandu", "XNEP", "NEPAL STOCK EXCHANGE", "", "www.nepalstock.com", "", "Before June 2005", "Active", true);
        add("NZ", "New Zealand", "Auckland", "NZFX", "NEW ZEALAND FUTURES & OPTIONS", "NZFOX", "www.nzfox.nzx.com", "", "September 2007", "Active", true);
        add("NZ", "New Zealand", "Auckland", "XAUK", "NEW ZEALAND STOCK EXCHANGE - AUCKLAND", "", "", "", "April 2003", "Deleted", false);
        add("NZ", "New Zealand", "Auckland", "XNEE", "NEW ZEALAND FUTURES AND OPTIONS EXCHANGE", "NZFOE", "www.nzfoe.co.nz", "Closed in 2004.", "September 2007", "Deleted", false);
        add("NZ", "New Zealand", "Wellington", "XNZE", "NEW ZEALAND EXCHANGE LTD", "NZX", "www.nzx.com", "", "Before June 2005", "Active", true);
        add("NI", "Nicaragua", "Managua", "XMAN", "BOLSA DE VALORES DE NICARAGUA", "", "www.bolsanic.com", "", "Before June 2005", "Active", true);
        add("NG", "Nigeria", "Lagos", "XNSA", "NIGERIAN STOCK EXCHANGE", "", "www.nigerianstockexchange.com", "", "Before June 2005", "Active", true);
        add("NO", "Norway", "Bergen", "FISH", "FISH POOL ASA", "", "www.fishpool.eu", "", "June 2007", "Active", true);
        add("NO", "Norway", "Olso", "NORX", "NORD POOL ASA", "", "www.nordpool.com", "", "June 2006", "Active", true);
        add("NO", "Norway", "Olso", "NOTC", "NORWEGIAN OVER THE COUNTER MARKET", "NOTC", "www.nfmf.no/NOTC/Default_en.htm", "", "Before June 2005", "Active", true);
        add("NO", "Norway", "Olso", "XIMA", "INTERNATIONAL MARTIME EXCHANGE", "IMAREX", "www.imarex.com/home", "", "June 2006", "Active", true);
        add("NO", "Norway", "Olso", "XOAM", "OSLO BORS ALTERNATIVE BOND MARKET", "ABM", "www.abmportal.no", "", "November 2007", "Active", true);
        add("NO", "Norway", "Olso", "XOAS", "OSLO AXESS", "", "www.osloaxess.no", "This market (and website) is planed to be live in May 2007.", "February 2007", "Active", true);
        add("NO", "Norway", "Olso", "XOSL", "OSLO BORS ASA", "", "www.oslobors.no/ob/", "", "Before June 2005", "Active", true);
        add("NO", "Norway", "Tromso", "FSHX", "FISHEX", "", "www.fishex.no", "Registered market for fish derivatives.", "September 2007", "Active", true);
        add("OM", "Oman", "Muscat", "XMUS", "MUSCAT SECURITIES MARKET", "MSM", "www.msm.gov.om", "", "Before June 2005", "Active", true);
        add("PK", "Pakistan", "Islamabad", "XISL", "ISLAMABAD STOCK EXCHANGE", "ISE", "www.ise.com.pk", "", "Before June 2005", "Active", true);
        add("PK", "Pakistan", "Karachi", "XKAR", "KARACHI STOCK EXCHANGE (GUARANTEE) LIMITED", "KSE", "www.kse.com.pk", "", "Before June 2005", "Active", true);
        add("PK", "Pakistan", "Lahore", "XLAH", "LAHORE STOCK EXCHANGE", "LSE", "www.lahorestock.com", "", "Before June 2005", "Active", true);
        add("PS", "Palestinian Territory, Occupied", "Nablus", "XPAE", "PALESTINE SECURITIES EXCHANGE", "PSE", "www.p-s-e.com", "", "Before June 2005", "Active", true);
        add("PA", "Panama", "Panama", "XPTY", "BOLSA DE VALORES DE PANAMA, S.A.", "BVP", "www.panabolsa.com", "", "Before June 2005", "Active", true);
        add("PG", "Papua New Guinea", "Port Moresby", "XPOM", "PORT MORESBY STOCK EXCHANGE", "", "www.pomsox.com.pg", "", "Before June 2005", "Active", true);
        add("PY", "Paraguay", "Asuncion", "XVPA", "BOLSA DE VALORES Y PRODUCTOS DE ASUNCION SA", "BVPASA", "www.bvpasa.com.py", "", "Before June 2005", "Active", true);
        add("PE", "Peru", "Lima", "XLIM", "BOLSA DE VALORES DE LIMA", "BVL", "www.bvl.com.pe", "", "Before June 2005", "Active", true);
        add("PH", "Philippine", "Pasig City", "XPHS", "PHILIPPINE STOCK EXCHANGE, INC.", "PSE", "www.pse.org.ph", "", "Before June 2005", "Active", true);
        add("PL", "Poland", "Warsaw", "CETO", "MTS-CeTO S.A.", "", "www.mts-ceto.pl", "", "Before June 2005", "Active", true);
        add("PL", "Poland", "Warsaw", "MTSP", "MTS POLAND", "", "www.mtspoland.com", "", "November 2005", "Active", true);
        add("PL", "Poland", "Warsaw", "PLPX", "TOWAROWA GIELDA ENERGII S.A. (POLISH POWER EXCHANGE)", "TGE", "www.polpx.pl", "", "July 2007", "Active", true);
        add("PL", "Poland", "Warsaw", "RPWC", "CETO SECURITIES MARKET", "", "www.mts-ceto.pl", "", "October 2007", "Active", true);
        add("PL", "Poland", "Warsaw", "XWAR", "WARSAW STOCK EXCHANGE", "WSE", "www.wse.com.pl", "", "Before June 2005", "Active", true);
        add("PL", "Poland", "Warszawa", "XNCO", "NEW CONNECT", "", "www.newconnect.pl", "A new market financing the growth of young companies with a large growth potential, organised and operated by the WSE", "January 2008", "Active", true);
        add("PT", "Portugal", "Lisboa", "ENXL", "NYSE EURONEXT - EASYNEXT LISBON", "", "www.euronext.com", "", "August 2007", "Active", true);
        add("PT", "Portugal", "Lisboa", "MDIP", "MEDIP  (MTS PORTUGAL SGMR, SA)", "MEDIP", "www.mtsportugal.com", "Institution description modified to include Operating entity", "June 2007", "Active", true);
        add("PT", "Portugal", "Lisboa", "MFOX", "NYSE EURONEXT - MERCADO DE FUTUROS E OP??ES", "", "www.euronext.com", "", "November 2007", "Active", true);
        add("PT", "Portugal", "Lisboa", "OMIP", "OPERADOR DE MERCADO IBERICO DE ENERGIA - PORTUGAL", "OMIP", "www.omip.pt", "", "May 2007", "Active", true);
        add("PT", "Portugal", "Lisboa", "OPEX", "PEX-PRIVATE EXCHANGE", "OPEX", "www.opex.pt/en/pex", "", "Before June 2005", "Active", true);
        add("PT", "Portugal", "Lisboa", "PMTS", "MTS PORTUGAL SGMR, SA", "", "www.mtsportugal.com", "Duplicate. Use MDIP MEDIP (MTS PORTUGAL SGMR, SA)", "June 2007", "Deleted", false);
        add("PT", "Portugal", "Lisboa", "WQXL", "NYSE EURONEXT - MARKET WITHOUT QUOTATIONS LISBON", "", "www.euronext.com", "", "August 2007", "Active", true);
        add("PT", "Portugal", "Lisboa", "XLIS", "NYSE EURONEXT - EURONEXT LISBON", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("QA", "Qatar", "Doha", "DSMD", "DOHA SECURITIES MARKET", "DSM", "www.dsm.com.qa", "", "Before June 2005", "Active", true);
        add("RO", "Romania", "Bucharest", "XBRM", "ROMANIAN  COMMODITIES EXCHANGE", "BRM", "www.brm.ro", "", "Before June 2005", "Active", true);
        add("RO", "Romania", "Bucharest", "XBSD", "DERIVATIVES REGULATED MARKET - BVB", "REGF", "www.bvb.ro", "Derivatives Market segement of the Bucharest Stock Exchange. Updates: inst.description+Accr", "January 2008", "Active", true);
        add("RO", "Romania", "Bucharest", "XBSE", "SPOT REGULATED MARKET - BVB", "REGS", "www.bvb.ro", "Updates: inst.description+Accr", "January 2008", "Active", true);
        add("RO", "Romania", "Bucharest", "XRAS", "RASDAQ", "RASDAQ", "www.rasd.ro", "", "Before June 2005", "Active", true);
        add("RO", "Romania", "Sibiu", "BMFM", "DERIVATIVES REGULATED MARKET - BMFMS", "BMFMS", "www.bmfms.ro", "Updates: inst.description+Accr", "January 2008", "Active", true);
        add("RU", "Russia", "Ekaterinburg", "URCE", "URALS REGIONAL CURRENCY EXCHANGE", "URCEX", "www.urvb.ru", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Moscow", "RTSX", "RUSSIAN TRADING SYSTEM STOCK EXCHANGE", "RTS", "www.rts.ru", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Moscow", "XMIC", "MOSCOW INTERBANK CURRENCY EXCHANGE", "MICEX", "www.micex.com", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Moscow", "XMOS", "MOSCOW STOCK EXCHANGE", "MSE", "www.mse.ru", "Description change.", "November 2007", "Active", true);
        add("RU", "Russia", "Moscow", "XRUS", "INTERNET DIRECT-ACCESS EXCHANGE", "INDX", "www.indx.ru", "Modification of the name.", "September 2007", "Active", true);
        add("RU", "Russia", "Nizhniy Novgorod", "NNCS", "NIZHNY NOVGOROD STOCK AND CURRENCY EXCHANGE", "NCSE", "www.nnx.ru/eng/deystv.htm", "Description change.", "November 2007", "Active", true);
        add("RU", "Russia", "Novosibirsk", "XSIB", "SIBERIAN STOCK EXCHANGE", "SIMEX", "www.sse.nsk.su", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Novosibirsk", "XSIC", "SIBERIAN INTERBANK CURRENCY EXCHANGE", "SICEX", "www.sice.ru", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Rostov", "XROV", "REGIONAL EXCHANGE CENTRE -  MICEX SOUTH", "MICEX SOUTH", "www.rndex.ru", "Modification of the name. See http://www.micex.com/press/issue_6293.html Description change.", "November 2007", "Active", true);
        add("RU", "Russia", "Saint-Petersburg", "IXSP", "INTERNATIONAL STOCK EXCHANGE SAINT-PETERSBOURG", "IXSP", "www.ixsp.ru", "Securities markets, IPO. Registred in April 2007", "March 2008", "Active", true);
        add("RU", "Russia", "Saint-Petersburg", "XPET", "STOCK EXCHANGE SAINT PETERSBURG", "SPBEX", "www.spbex.ru", "Description change.", "November 2007", "Active", true);
        add("RU", "Russia", "Saint-Petersburg", "XPIC", "SAINT-PETERSBURG CURRENCY EXCHANGE", "SPSEX", "www.spcex.ru", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Samara", "XSAM", "SAMARA CURRENCY INTERBANK EXCHANGE", "SCIEX", "www.sciex.ru", "", "Before June 2005", "Active", true);
        add("RU", "Russia", "Vladivostok", "XAPI", "REGIONAL EXCHANGE CENTER - MICEX FAR EAST", "MICEX FAR EAST", "www.micex.com", "Modification of the name. See http://www.micex.com/press/issue_6293.html Description change.", "November 2007", "Active", true);
        add("RU", "Russia", "Vladivostok", "XVLA", "VLADIVOSTOK (RUSSIA) STOCK EXCHANGE", "VSX", "", "Merged into Moscow Stock Exchange.", "November 2007", "Deleted", false);
        add("RW", "Rwanda", "Kigali", "ROTC", "RWANDA OTC MARKET", "", "", "Rwanda Over-the-Counter Market.", "June 2008", "Active", true);
        add("KN", "Saint Kitts and Nevis", "Basseterre", "XECS", "EASTERN CARIBBEAN SECURITIES EXCHANGE", "ECSE", "www.ecseonline.com", "", "Before June 2005", "Active", true);
        add("SA", "Saudi Arabia", "Rijad", "XSAU", "SAUDI STOCK EXCHANGE", "", "www.tadawul.com.sa", "", "Before June 2005", "Active", true);
        add("RS", "Serbia (Republic of)", "Belgrade", "XBEL", "BELGRADE STOCK EXCHANGE", "", "www.belex.co.yu", "Country name and code change from YU to CS (SERBIA AND MONTENEGRO) COUNTRY and CC modified from SERBIA AND MONTENEGRO / CS to SERBIA (REPUBLIC OF)  / RS", "Before June 2005", "Active", true);
        add("SG", "Singapore", "Singapore", "JADX", "JOINT ASIAN DERIVATIVES EXCHANGE", "", "www.jadeexchange.com", "", "May 2007", "Active", true);
        add("SG", "Singapore", "Singapore", "XSCE", "SINGAPORE COMMODITY EXCHANGE", "SICOM", "www.sicom.com.sg", "", "Before June 2005", "Active", true);
        add("SG", "Singapore", "Singapore", "XSES", "SINGAPORE EXCHANGE", "SGX", "www.sgx.com", "", "Before June 2005", "Active", true);
        add("SG", "Singapore", "Singapore", "XSIM", "SINGAPORE EXCHANGE DERIVATIVES CLEARING LIMITED", "SGX-DT", "www.sgx.com", "", "Before June 2005", "Active", true);
        add("SK", "Slovakia", "Bratislava", "XBRA", "BRATISLAVA STOCK EXCHANGE", "BSSE", "www.bsse.sk", "", "Before June 2005", "Active", true);
        add("SK", "Slovakia", "Bratislava", "XRMS", "SK RM-S (SLOVAK STOCK EXCHANGE)", "SK RM-S", "www.rms.sk", "XRMS is not anymore existing market in Slovak republic. XRMS is not anymore existing market in Slovak republic", "January 2008", "Deleted", false);
        add("SI", "Slovenia", "Ljubljana", "XLJS", "LJUBLJANA STOCK EXCHANGE (SEMI-OFFICIAL MARKET)", "", "www.ljse.si", "", "July 2007", "Active", true);
        add("SI", "Slovenia", "Ljubljana", "XLJU", "LJUBLJANA STOCK EXCHANGE (OFFICIAL MARKET)", "", "www.ljse.si", "", "Before June 2005", "Active", true);
        add("ZA", "South Africa", "Johannesburg", "ALTX", "ALTERNATIVE EXCHANGE", "ALTX", "www.altx.co.za", "", "August 2006", "Active", true);
        add("ZA", "South Africa", "Johannesburg", "XBES", "BOND EXCHANGE OF SOUTH AFRICA", "BESA", "www.besa.za.com", "", "Before June 2005", "Active", true);
        add("ZA", "South Africa", "Johannesburg", "XJSE", "JSE SECURITIES EXCHANGE", "JSE", "www.jse.co.za", "", "Before June 2005", "Active", true);
        add("ZA", "South Africa", "Johannesburg", "XSAF", "SOUTH AFRICAN FUTURES EXCHANGE", "SAFEX", "www.safex.co.za", "", "Before June 2005", "Active", true);
        add("ZA", "South Africa", "Johannesburg", "XSFA", "SOUTH AFRICAN FUTURES EXCHANGE - AGRICULTURAL MARKET DIVISION", "", "www.safex.co.za", "", "Before June 2005", "Active", true);
        add("ZA", "South Africa", "Johannesburg", "YLDX", "JSE YIELD-X", "", "www.yieldx.co.za", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Barcelona", "XBAR", "BARCELONA STOCK EXCHANGE", "", "www.borsabcn.es", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Barcelona", "XBAV", "MERCHBOLSA AGENCIA DE VALORES, SA", "", "", "Does no longer exists as per CNMV", "July 2007", "Deleted", false);
        add("ES", "Spain", "Barcelona", "XMEF", "MEFF RENTA FIJA", "MEFF", "www.meff.com", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Barcelona", "XMRV", "MEFF RENTA VARIABLE", "MEFF", "www.meffclear.com", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Bilbao", "XBIL", "BOLSA DE VALORES DE BILBAO", "", "www.bolsabilbao.es", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Jaen", "XSRM", "MERCADO DE FUTUROS DE ACEITE DE OLIVA, S.A.", "MFAO", "www.mfao.es", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Madrid", "MABX", "MERCADO ALTERNATIVO BURSATIL", "MAB", "www.bolsasymercados.es", "", "July 2007", "Active", true);
        add("ES", "Spain", "Madrid", "OMEL", "OPERADOR DE MERCADO IBERICO DE ENERGIA - SPAIN", "OMEL", "www.omel.es", "", "May 2007", "Active", true);
        add("ES", "Spain", "Madrid", "SMTS", "MTS SPAIN, S.A.", "", "www.mtsspain.com", "", "November 2005", "Active", true);
        add("ES", "Spain", "Madrid", "XDPA", "CADE - MERCADO DE DEUDA PUBLICA ANOTADA", "", "", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Madrid", "XDRF", "AIAF - MERCADO DE RENTA FIJA", "", "www.aiaf.es/aiaf/index.home", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Madrid", "XLAT", "LATIBEX", "", "www.latibex.com/esp/home.htm", "see www.latibex.com", "Before June 2005", "Active", true);
        add("ES", "Spain", "Madrid", "XMAD", "BOLSA DE MADRID", "", "www.bolsamadrid.es", "", "Before June 2005", "Active", true);
        add("ES", "Spain", "Madrid", "XMCE", "MERCADO CONTINUO ESPANOL", "SIBE", "www.bolsaymercados.es", "Description change. Typo corrected. City change.", "November 2007", "Active", true);
        add("ES", "Spain", "Madrid", "XNAF", "SISTEMA ESPANOL DE NEGOCIACION DE ACTIVOS FINANCIEROS", "SENAF", "www.senaf.net", "", "June 2007", "Active", true);
        add("ES", "Spain", "Valencia", "XFCM", "MERCADO DE FUTUROS Y OPCIONES SOBRE CITRICOS", "FC&M", "www.fcym.com", "Does no longer exists as per CNMV", "July 2007", "Deleted", false);
        add("ES", "Spain", "Valencia", "XVAL", "BOLSA DE VALENCIA", "", "www.bolsavalencia.es", "", "Before June 2005", "Active", true);
        add("LK", "Sri Lanka", "Colombo", "XCOL", "COLOMBO STOCK EXCHANGE", "", "www.cse.lk", "", "Before June 2005", "Active", true);
        add("SD", "Sudan", "Khartoum", "XKHA", "KHARTOUM STOCL EXCHANGE", "KSE", "www.ksesudan.com", "", "Before June 2005", "Active", true);
        add("SZ", "Swaziland", "Mbabane", "XSWA", "SWAZILAND STOCK EXCHANGE", "SSX", "www.ssx.org.sz", "", "Before June 2005", "Active", true);
        add("SE", "Sweden", "Stockholm", "FNSE", "FIRST NORTH STOCKHOLM", "", "www.omxgroup.com/firstnorth", "First North is operated by OMX.First North is a Nordic alternative marketplace for trading in shares. MTF for equities and related", "January 2008", "Active", true);
        add("SE", "Sweden", "Stockholm", "NMTF", "NORDIC MTF", "", "www.nordicmtf.se", "", "November 2007", "Active", true);
        add("SE", "Sweden", "Stockholm", "XNDX", "NORDIC DERIVATIVES EXCHANGE", "NDX", "www.ndx.se", "", "March 2008", "Active", true);
        add("SE", "Sweden", "Stockholm", "XNGM", "NORDIC GROWTH MARKET", "NGM", "www.ngm.se", "", "Before June 2005", "Active", true);
        add("SE", "Sweden", "Stockholm", "XNMR", "NORDIC MTF REPORTING", "", "www.nordicmtf.se", "Off exchange trade reporting", "April 2008", "Active", true);
        add("SE", "Sweden", "Stockholm", "XOME", "OMX NORDIC EXCHANGE STOCKHOLM AB", "OM", "www.omxgroup.com", "Modification of the name to match the current legal name. The OMX Nordic Exchange Stockholm requested its MIC to be changed from XOME to XSTO. This request was accepted exceptionaly. Replaced by XSTO as per exchange request", "November 2007", "Deleted", false);
        add("SE", "Sweden", "Stockholm", "XOMF", "OM FIXED INTEREST EXCHANGE", "OM", "www.omxgroup.com", "No longer existing as separate exchange.", "November 2007", "Deleted", false);
        add("SE", "Sweden", "Stockholm", "XOPV", "OMX OTC PUBLICATION VENUE", "", "www.omxgroup.com", "", "November 2007", "Active", true);
        add("SE", "Sweden", "Stockholm", "XSAT", "AKTIETORGET", "", "www.actietorget.se", "", "June 2007", "Active", true);
        add("SE", "Sweden", "Stockholm", "XSTO", "OMX NORDIC EXCHANGE STOCKHOLM AB", "OM", "www.omxgroup.com", "The OMX Nordic Exchange Stockholm requested its MIC to be changed from XOME to XSTO. This request was accepted exceptionaly.", "November 2007", "Active", true);
        add("CH", "Switzerland", "Berne", "XBRN", "BERNE STOCK EXCHANGE", "", "", "", "Before June 2005", "Active", true);
        add("CH", "Switzerland", "Zurich", "ALEX", "ALEX EXCHANGE SCHWEIZ AG", "ALEX", "www.alexchange.com", "", "November 2006", "", false);
        add("CH", "Switzerland", "Zurich", "XQMH", "SCOACH SWITZERLAND", "", "www.scoach.com", "Trademark change.  Alexchange (ALEX) is now SWX QUOTEMATCH AG (XQMH) Description and website modification.", "October 2007", "Active", true);
        add("CH", "Switzerland", "Zurich", "XSWO", "SWISS OPTIONS AND FINANIAL FUTURES EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("CH", "Switzerland", "Zurich", "XSWX", "SWISS EXCHANGE", "SWX", "www.swx.com", "", "Before June 2005", "Active", true);
        add("TW", "Taiwan", "Taipei", "ROCO", "GRETAI SECURITIES MARKET", "", "www.gretai.org.tw/e_index.htm", "", "Before June 2005", "Active", true);
        add("TW", "Taiwan", "Taipei", "XIME", "TAIWAN INTERNATIONAL MERCANTILE EXCHANGE", "TAIMEX", "www.taimex.com.tw", "Duplicate of XTAF", "November 2007", "Deleted", false);
        add("TW", "Taiwan", "Taipei", "XTAD", "TAISDAQ", "TAISDAQ", "", "Former name of GreTai Securities Market (MIC: ROCO). ROCO should be used.", "September 2005", "Deleted", false);
        add("TW", "Taiwan", "Taipei", "XTAF", "TAIWAN FUTURES EXCHANGE", "TAIFEX", "www.taifex.com.tw", "", "Before June 2005", "Active", true);
        add("TW", "Taiwan", "Taipei", "XTAI", "TAIWAN STOCK EXCHANGE", "TSEC", "www.tse.com.tw", "", "Before June 2005", "Active", true);
        add("TZ", "Tanzania", "Dar es Salaam", "XDAR", "DAR ES  SALAAM STOCK EXCHANGE", "", "www.darstockexchange.com", "", "Before June 2005", "Active", true);
        add("TH", "Thailand", "Bangkok", "AFET", "AGRICULTURAL FUTURES EXCHANGE OF THAILAND", "", "www.afet.or.th/english/", "", "January 2008", "Active", true);
        add("TH", "Thailand", "Bangkok", "BEEX", "BOND ELECTRONIC EXCHANGE", "", "www.bex.or.th/en/index.html", "", "November 2007", "Active", true);
        add("TH", "Thailand", "Bangkok", "TFEX", "THAILAND FUTURES EXCHANGE", "TFEX", "www.tfex.co.th", "", "June 2006", "Active", true);
        add("TH", "Thailand", "Bangkok", "XBKF", "STOCK EXCHANGE OF THAILAND - FOREIGN BOARD", "SET", "www.set.or.th", "", "Before June 2005", "Active", true);
        add("TH", "Thailand", "Bangkok", "XBKK", "STOCK EXCHANGE OF THAILAND", "SET", "www.set.or.th", "", "Before June 2005", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "ALXA", "NYSE EURONEXT - ALTERNEXT AMSTERDAM", "", "www.alternext.com", "", "August 2007", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "AMTS", "MTS AMSTERDAM N.V.", "", "www.mtsamsterdam.com", "", "November 2005", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "ECXE", "EUROPEAN CLIMATE EXCHANGE", "", "www.ecxeurope.com", "", "February 2008", "Deleted", false);
        add("NL", "The Netherlands", "Amsterdam", "NDEX", "EUROPEAN ENERGY DERIVATIVES EXCHANGE N.V.", "ENDEX", "www.endex.nl", "", "May 2007", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "NLPX", "APX POWER NL", "", "www.apxgroup.com", "", "June 2006", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "TNLA", "NYSE EURONEXT - TRADED BUT NOT LISTED AMSTERDAM", "", "www.euronext.com", "", "August 2007", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "XACE", "AMSTERDAM COMMODITY EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("NL", "The Netherlands", "Amsterdam", "XAEX", "AEX-AGRICULTURAL FUTURES EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("NL", "The Netherlands", "Amsterdam", "XAMS", "NYSE EURONEXT - EURONEXT AMSTERDAM", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "XEUC", "EURONEXT COM, COMMODITIES FUTURES AND OPTIONS", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "XEUE", "EURONEXT EQF, EQUITIES AND INDICES DERIVATIVES", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "XEUI", "EURONEXT IRF, INTEREST RATE FUTURE AND OPTIONS", "EURONEXT", "www.euronext.com", "", "Before June 2005", "Active", true);
        add("NL", "The Netherlands", "Amsterdam", "XFTA", "FINANCIELE TERMIJNMARKET AMSTERDAM", "", "", "", "April 2003", "Deleted", false);
        add("TT", "Trinidad and Tobago", "Port of Spain", "XTRN", "TRINIDAD AND TOBAGO STOCK EXCHANGE", "TTSE", "", "", "Before June 2005", "Active", true);
        add("TN", "Tunisia", "Tunis", "XTUN", "BOURSE DE TUNIS", "BVMT", "www.bvmt.com.tn", "Modification of the name.", "September 2007", "Active", true);
        add("TR", "Turkey", "Istambul", "XIAB", "ISTANBUL GOLD EXCHANGE", "IAB", "www.iab.gov.tr", "", "June 2006", "Active", true);
        add("TR", "Turkey", "Istambul", "XIST", "ISTANBUL STOCK EXCHANGE", "ISE", "www.ise.org", "", "Before June 2005", "Active", true);
        add("TR", "Turkey", "Izmir", "XTUR", "TURKISH DERIVATIVES EXCHANGE", "TURKDEX", "www.turkdex.org.tr", "", "November 2006", "Active", true);
        add("UG", "Uganda", "Kampala", "XUGA", "UGANDA SECURITIES EXCHANGE", "USE", "www.use.or.ug/home.asp", "", "Before June 2005", "Active", true);
        add("UA", "Ukraine", "Donetsk", "XDFB", "DONETSK STOCK EXCHANGE", "DFB", "www.dfb.donbass.com", "", "June 2006", "Active", true);
        add("UA", "Ukraine", "Kharkov", "XKHR", "KHARKOV COMMODITY EXCHANGE", "", "www.xtb.com.ua", "", "Before June 2005", "Active", true);
        add("UA", "Ukraine", "Kiev", "PFTS", "FIRST SECURITIES TRADING SYSTEM - UKRAINIAN OTC", "PFTS", "www.pfts.com/ukr", "", "Before June 2005", "Active", true);
        add("UA", "Ukraine", "Kiev", "XKIE", "KIEV UNIVERSAL EXCHANGE", "", "www.kue.kiev.ua", "", "Before June 2005", "Active", true);
        add("UA", "Ukraine", "Kiev", "XKIS", "KIEV INTERNATIONAL STOCK EXCHANGE", "KISE", "www.kise-ua.com", "", "June 2006", "Active", true);
        add("UA", "Ukraine", "Kiev", "XUAX", "UKRAINIAN STOCK EXCHANGE", "UKRSE", "www.ukrse.kiev.ua", "", "February 2006", "Active", true);
        add("UA", "Ukraine", "Kiev", "XUKR", "UKRAINIAN UNIVERSAL COMMODITY EXCHANGE", "", "www.uutb.com.ua", "", "Before June 2005", "Active", true);
        add("UA", "Ukraine", "Odessa", "XODE", "ODESSA COMMODITY EXCHANGE", "", "www.otb.odessa.ua", "", "Before June 2005", "Active", true);
        add("UA", "Ukraine", "Pridneprovsk", "XPRI", "PRIDNEPROVSK COMMODITY EXCHANGE", "", "www.pce.dp.ua", "", "Before June 2005", "Active", true);
        add("AE", "United Arab Emirates", "Abu Dhabi", "XADS", "ABU DHABI SECURITIES MARKET", "ADSM", "portal.adsm.ae/wps/portal", "", "Before June 2005", "Active", true);
        add("AE", "United Arab Emirates", "Dubai", "DGCX", "DUBAI GOLD & COMMODITIES EXCHANGE DMCC", "DGCX", "www.dgcx.ae", "", "December 2005", "Active", true);
        add("AE", "United Arab Emirates", "Dubai", "DIFX", "DUBAI INTERNATIONAL FINANCIAL EXCHANGE LTD.", "DIFX", "www.difx.ae", "", "September 2005", "Active", true);
        add("AE", "United Arab Emirates", "Dubai", "DUMX", "DUBAI MERCANTILE EXCHANGE", "", "www.dubaimerc.com", "", "July 2007", "Active", true);
        add("AE", "United Arab Emirates", "Dubai", "XDFM", "DUBAI FINANCIAL MARKET", "DFM", "www.dfm.co.ae", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "Aylesbury", "SHAR", "SHAREMARK", "", "www.share.com", "Multilateral Trading Facility (MTF) for small and medium sized companies.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "AIMX", "ALTERNATE INVESTMENT MARKET", "AIM", "www.londonstockexchange.com", "XLON should be used for all London Stock Exchange platforms. MIC for the London Stock Exchange is  XLON. MIC for the London Stock Exchange is  XLON", "November 2007", "Deleted", false);
        add("GB", "United Kingdom", "London", "BGCI", "BGC BROKERS LP", "", "www.bgcpartners.com", "Description modification.", "October 2007", "Active", true);
        add("GB", "United Kingdom", "London", "BLOX", "BLOCKMATCH", "", "www.instinet.com", "Instinet Europe Limited will be launching a NEW Multilateral Trading Facility, MTF, for Equities called BlockMatch.", "November 2007", "Active", true);
        add("GB", "United Kingdom", "London", "BOAT", "MARKIT BOAT", "", "www.markit.com/boat", "OTC Equities Trade Reporting Platform", "June 2008", "Active", true);
        add("GB", "United Kingdom", "London", "BOSC", "BONDSCAPE", "", "www.bondscape.net", "Multilateral Trading Facility.", "November 2007", "Active", true);
        add("GB", "United Kingdom", "London", "BTEE", "ICAP ELECTRONIC BROKING (EUROPE)", "BTEC", "www.icap.com", "Institution description modified from Brokertec Europe to ICAP Electronic Broking (Europe).", "June 2007", "Active", true);
        add("GB", "United Kingdom", "London", "CAZE", "THE CAZENOVE MTF", "", "www.jpmorgancazenove.com", "MTF for transactions in equties and bonds.", "November 2007", "Active", true);
        add("GB", "United Kingdom", "London", "CCO2", "CANTORCO2E.COM LIMITED", "", "www.cantorco2e.com", "", "July 2007", "Active", true);
        add("GB", "United Kingdom", "London", "CHIX", "CHI-X EUROPE LIMITED.", "CHI-X", "www.chi-x.com", "Registered Multilateral Trading Facility (ATS) for the electronic trading of Pan-European stocks . Description and website modification.", "October 2007", "Active", true);
        add("GB", "United Kingdom", "London", "CMTS", "EUROCREDIT MTS", "", "www.eurocreditmts.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "CRDL", "COREDEAL MTS", "", "www.mtsgroup.org", "", "August 2006", "Active", true);
        add("GB", "United Kingdom", "London", "CXRT", "CREDITEX REALTIME", "", "www.creditex.com", "Multilateral Trading Facility (MTF) for credit derivatives.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "EMTS", "EUROMTS", "EMTS", "www.euromts-ltd.com", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "FAIR", "CANTOR SPREADFAIR", "", "www.spreadfair.com", "Cantor Spreadfair is currently an Alternative Trading System under FSA rules, and will be Multilateral Trading System under the new rules post MiFID introduction on 1st November 2007.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "FXMT", "FXMARKETSPACE LIMITED", "", "www.fxmarketspace.com", "", "July 2007", "Active", true);
        add("GB", "United Kingdom", "London", "GEMX", "GEMMA (Gilt Edged Market Makers?Association)", "GEMMA", "", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "GFIC", "GFI CREDITMATCH", "", "www.gfigroup.com", "Electronic Trading platform for Credit Derivatives and Bonds.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "GFIF", "GFI FOREXMATCH", "", "www.gfigroup.com", "Electronic Trading platform for OTC currency derivatives.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "GFIN", "GFI ENERGYMATCH", "", "www.gfigroup.com", "Electronic trading for OTC Commodity derivatives (both physical and swaps).", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "ICAH", "ICAP HYDE DERIVATIVES LTD", "", "www.icap.com", "Multilateral Trading Facility for forward freight agreements.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "ICAP", "ICAP EUROPE", "", "www.i-swap.com", "For OTC Interest Rate Swaps.", "June 2007", "Active", true);
        add("GB", "United Kingdom", "London", "ICAT", "ICAP HYDE TANKER DERIVATIVES LIMITED", "ICAP", "www.icap.com", "ICAP market's segment", "March 2008", "Active", true);
        add("GB", "United Kingdom", "London", "ICEN", "ICAP ENERGY", "", "www.icapenergy.com", "For OTC Commodity Derivatives (both physical and swaps). See www.icap.com.", "June 2007", "Active", true);
        add("GB", "United Kingdom", "London", "ICEU", "INTERCONTINENTAL EXCHANGE - ICE FUTURES EUROPE", "ICE", "www.theice.com", "Regulated by the Financial Services Authority", "March 2008", "Modified", true);
        add("GB", "United Kingdom", "London", "ICSE", "ICAP SECURITIES", "", "www.icap.com", "For OTC Eurobonds.", "June 2007", "Active", true);
        add("GB", "United Kingdom", "London", "IMTS", "MTS IRELAND", "", "www.mtsireland.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "KMTS", "EUROMTS LINKERS MARKET", "", "www.euromts-ltd.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "LIQU", "LIQUIDNET SYSTEMS", "", "www.liquidnet.com", "Multilateral Trading Facility (MTF).", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "LMTS", "EUROGLOBALMTS", "", "www.euroglobalmts.com/", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "LPPM", "LONDON PLATINUM AND PALLADIUM MARKET", "LPPM", "www.lppm.org.uk", "", "April 2007", "Active", true);
        add("GB", "United Kingdom", "London", "MAEL", "MARKETAXESS EUROPE LIMITED", "", "www.marketaxess.com", "", "July 2007", "Active", true);
        add("GB", "United Kingdom", "London", "MFGL", "MF GLOBAL ENERGY MTF", "", "www.mfglobal.com", "MTF for transactions in Emissions / CO2 / Power products / OTC Energy Derivatives products.", "November 2007", "Active", true);
        add("GB", "United Kingdom", "London", "MTSA", "MTS AUSTRIAN MARKET", "", "www.mtsaustria.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "MTSG", "MTS GREEK MARKET", "", "www.mtsgreece.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "MTSS", "MTS SWAP MARKET", "", "Not yet available", "Website not yet availbale.  MTF for swap transactions.", "October 2007", "Active", true);
        add("GB", "United Kingdom", "London", "MYTR", "MYTREASURY", "", "www.mytreasury.com (as from 17/09)", "Multi-lateral trading facility acting as an arranger of trades in triple A rated MMF, deposits and loans and ECP. The trading model is full disclosure of counterparties to each other and KYC responsibility rests between the trading counterparties on the system.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "NMTS", "NEW EUROMTS", "", "www.neweuromts.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "NYFX", "EURO-MILLENNIUM", "", "www.nyfix.com", "ATS for Equity trading.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "NYMX", "NYMEX EUROPE LTD", "", "www.nymexeurope.co.uk", "Closed June 2007.", "November 2007", "Deleted", false);
        add("GB", "United Kingdom", "London", "OFEX", "OFEX", "OFEX", "www.ofex.com", "see www.plusmarketsgroup.com. Part of PLUS MARKETS GROUP (XPLU)", "September 2006", "Deleted", false);
        add("GB", "United Kingdom", "London", "PCDS", "TULLETT PREBON PLC - PREBON CDS", "", "www.tullettprebon.com", "Multilateral Trading Facility (MTF) for credit derivatives.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "QMTS", "MTS QUASI GOVERNMENT", "", "www.euromts-ltd.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "QWIX", "Q-WIXX PLATFORM", "", "www.qwixx.com", "Multilateral Trading Facility (MTF) for credit derivatives.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "RMTS", "MTS ISRAEL", "", "www.mtsisrael.com", "Electronic Trading Platform.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "RTSL", "REUTERS TRANSACTION SERVICES LIMITED", "RTSL", "www.reuters.com", "", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "SGMA", "GOLDMAN SACH MTF", "", "www.gs.com", "GS's MTF. Implementation date: February 1st 2008", "March 2008", "Active", true);
        add("GB", "United Kingdom", "London", "SPEC", "SPECTRONLIVE", "", "www.spectrongroup.com", "Electronic Trading Platform for OTC Commodity Contracts.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "SWAP", "SWAPSTREAM", "", "www.swapstream.com", "Electronic trading platform for interest rate derivatives.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TBEN", "TULLETT PREBON PLC - TULLET PREBON ENERGY", "", "www.tullettprebon.com", "Multilateral Trading Facility (MTF) for physical energy products and energy derivatives.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TBLA", "TULLETT PREBON PLC - TP TRADEBLADE", "", "www.tullettprebon.com", "OTC Electronic Trading Platform. Multi-lateral Trading Facility. Modification of the name. Modification in the institution description", "June 2008", "Modified", true);
        add("GB", "United Kingdom", "London", "TCDS", "TRADITION CDS", "", "www.tradition.co.uk", "Electronic trading plaform for Credit Derivative products.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TFSG", "TFS GREEN SCREEN", "", "www.tsfbrokers.com", "Electronic system for trades in environmental products, energy and other commodities.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TFSS", "TFS VARIANCE SWAPS SYSTEM", "", "www.tsfvarswaps.com", "Electronic system for trades in OTC variance swaps.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TFSV", "VOLBROKER", "", "www.tfsicap.com", "Electronic platform for OTC transactions in currency options.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TMTS", "EUROBENCHMARK TRES. BILLS", "", "www.euromts-ltd.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "TPCD", "TULLETT PREBON PLC - TP CREDITDEAL", "", "www.tullettprebon.com", "Electronic trading platform for Credit products", "June 2008", "Active", true);
        add("GB", "United Kingdom", "London", "TPIE", "THE PROPERTY INVESTMENT EXCHANGE", "", "www.thepropertyinvestmentexchange.com", "MTF listing property shares to more sophisticated client. See www.thepropertyinvestmentmarket.com", "October 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TPIM", "THE PROPERTY INVESTMENT MARKET", "", "www.thepropertyinvestmentmarket.com", "MTF listing residential property shares to retail client.", "October 2007", "Active", true);
        add("GB", "United Kingdom", "London", "TPRE", "TULLETT PREBON PLC - TP REPO", "", "www.tullettprebon.com", "Electronic trading platform for repurchase agreements", "June 2008", "Active", true);
        add("GB", "United Kingdom", "London", "TREU", "TRADEWEB EUROPE LIMITED", "", "www.tradeweb.com", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "TRQX", "TURQUOISE", "", "www.tradeturquoise.com", "Multilateral Trading Facility (MTF). Website informtation not yet available.", "September 2007", "Active", true);
        add("GB", "United Kingdom", "London", "UKPX", "APX POWER UK", "", "www.apxgroup.com", "", "June 2006", "Active", true);
        add("GB", "United Kingdom", "London", "UMTS", "MTS CEDULAS MARKET", "", "www.euromts-ltd.com", "", "November 2005", "Active", true);
        add("GB", "United Kingdom", "London", "VMTS", "MTS SLOVENIA", "", "www.mtsslovenia.com", "Electronic Trading Platform.", "August 2007", "Active", true);
        add("GB", "United Kingdom", "London", "WCLK", "ICAP WCLK", "", "www.icap.com", "For OTC UK Gilts.", "June 2007", "Active", true);
        add("GB", "United Kingdom", "London", "XALT", "ALTEX-ATS", "", "www.altex-ats.co.uk", "For OTC Financial Futures.", "June 2007", "Active", true);
        add("GB", "United Kingdom", "London", "XCOR", "ICMA", "", "www.icma-group.org", "Formerly ISMA. MIC is to be used for identifying ICMA as a source of price.", "September 2006", "Active", true);
        add("GB", "United Kingdom", "London", "XEDX", "EDX LONDON LIMITED", "", "www.londonstockexchange.com", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "XIPE", "INTERNATIONAL PETROLEUM EXCHANGE", "IPE", "www.theIPE.com", "Merged with IEPA, INTERCONTINENTAL EXCHANGE LTD.  (ICE)", "April 2006", "Deleted", false);
        add("GB", "United Kingdom", "London", "XJWY", "JIWAY EXCHANGE LTD", "", "", "Ceased activities.", "August 2004", "Deleted", false);
        add("GB", "United Kingdom", "London", "XLBM", "LONDON BULLION MARKET", "", "www.lbma.org.uk", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "XLCE", "LONDON COMMODITY EXCHANGE", "LCE", "www.londoncommoditiesexchange.com", "Merged with NYSE Euronext Liffe", "September 2007", "Deleted", false);
        add("GB", "United Kingdom", "London", "XLIF", "NYSE EURONEXT LIFFE", "LIFFE", "www.euronext.com/derivatives", "Name change from LONDON INTERNATIONAL FINANCIAL FUTURES AND OPTIONS EXCHANGE into EURONEXT.LIFFE", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "XLME", "LONDON METAL EXCHANGE", "LME", "www.lme.co.uk", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "XLON", "LONDON STOCK EXCHANGE", "LSE", "www.londonstockexchange.com", "", "Before June 2005", "Active", true);
        add("GB", "United Kingdom", "London", "XLTO", "LONDON TRADED OPTIONS MARKET", "LTOM", "www.euronext.com/home_derivatives/0,4810,1732_6391950,00.html", "Needs to be differentiated from LIFFE, although merged with LIFFE in 1992. Merged with NYSE Euronext Liffe", "September 2007", "Deleted", false);
        add("GB", "United Kingdom", "London", "XMLX", "OMLX, THE LONDON SECURITIES AND DERIVATIVES EXCHANGE LIMITED", "OMLX", "www.omxgroup.com", "Now  EDX London (see www.londonstockexchange.com/en-gb/edx/about/about.htm)", "July 2006", "Deleted", false);
        add("GB", "United Kingdom", "London", "XPLU", "PLUS MARKETS", "PLUS-TRADING", "www.plus-trading.co.uk", "Registered Market for equities", "March 2006", "Active", true);
        add("GB", "United Kingdom", "London", "XTFN", "TRADEPOINT FINANCIAL NETWORKS PLC", "", "", "Duplicate with VIRT-X (XVTX)", "August 2004", "Deleted", false);
        add("GB", "United Kingdom", "London", "XVTX", "SWX EUROPE", "VIRT-X", "www.virt-x.com", "VIRT-X is changing is name to SWX Europe on 3rd March 2008", "March 2008", "Active", true);
        add("US", "United States of America", "Atlanta", "IEPA", "INTERCONTINENTAL EXCHANGE", "ICE", "www.theice.com", "Mapped to US", "March 2008", "Active", true);
        add("US", "United States of America", "Boston", "LEVL", "LEVEL ATS", "LEVEL", "www.levelats.com", "", "February 2007", "Active", true);
        add("US", "United States of America", "Boston", "XBOS", "BOSTON STOCK EXCHANGE", "BSE", "www.bostonstock.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Boston", "XBOX", "BOSTON OPTIONS EXCHANGE", "BOX", "www.bostonoptions.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "CBSX", "CBOE STOCK EXCHANGE", "CBSX", "www.cbsx.com", "", "September 2007", "Active", true);
        add("US", "United States of America", "Chicago", "CCFE", "CHICAGO CLIMATE FUTURES EXCHANGE", "", "www.ccfe.com", "", "June 2007", "Active", true);
        add("US", "United States of America", "Chicago", "FCBT", "CHICAGO BOARD OF TRADE (FLOOR)", "CBOT (FLOOR)", "www.cbot.com", "Need to differentiate electronic (XCBT) and floor (FCBT) market.", "February 2007", "Active", true);
        add("US", "United States of America", "Chicago", "FCME", "CHICAGO MERCANTILE EXCHANGE (FLOOR)", "CME (FLOOR)", "www.cme.com", "Need to differentiate electronic (XCME) and floor (FCME) market.", "February 2007", "Active", true);
        add("US", "United States of America", "Chicago", "THRD", "THIRD MARKET CORPORATION", "eTHRD", "www.ETHRD.COM", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XARC", "THE ARCHIPELAGO ECN", "ARCA", "www.archipelago.com", "Merged with Pacific Exchange into NYSE ARCA (ARCA)", "September 2006", "Deleted", false);
        add("US", "United States of America", "Chicago", "XCBF", "CBOE FUTURES EXCHANGE", "CFE", "www.cfe.cboe.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCBO", "CHICAGO BOARD OPTIONS EXCHANGE", "CBOE", "www.cboe.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCBT", "CHICAGO BOARD OF TRADE", "CBOT", "www.cbot.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCCX", "CHICAGO CLIMATE EXCHANGE, INC", "CCX", "www.chicagoclimateexchange.com", "see www.chicagoclimateexchange.com", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCHI", "CHICAGO STOCK EXCHANGE, INC", "CHX", "www.chx.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCIS", "NATIONAL STOCK EXCHANGE", "CSE", "www.cincinnatistock.com", "NAME CHANGE FROM CINCINNATI STOCK EXCHANGE . SEE www.cincinnatistock.com", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCME", "CHICAGO MERCANTILE EXCHANGE", "CME", "www.cme.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XCRC", "CHICAGO RICE AND COTTON EXCHANGE", "", "", "Closed", "September 2007", "Deleted", false);
        add("US", "United States of America", "Chicago", "XEUS", "US FUTURES EXCHANGE", "", "www.usfe.com", "Modification of XEUS description from EUREX US to US FUTURES EXCHANGE and modification of website.", "January 2007", "Active", true);
        add("US", "United States of America", "Chicago", "XIMM", "INTERNATIONAL MONETARY MARKET", "", "www.cme.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XIOM", "INDEX AND OPTIONS MARKET", "IOM", "www.cme.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XMAC", "MID AMERICA COMMODITY EXCHANGE", "MidAm", "", "Closed", "September 2007", "Deleted", false);
        add("US", "United States of America", "Chicago", "XMER", "MERCHANTS' EXCHANGE", "ME", "www.merchants-exchange.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Chicago", "XMID", "MIDWEST STOCK EXCHANGE", "", "", "", "April 2003", "Deleted", false);
        add("US", "United States of America", "Chicago", "XOCH", "ONECHICAGO, LLC", "", "www.onechicago.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Jersey City", "BTEC", "ICAP ELECTRONIC BROKING (US)", "BTEC", "www.icap.com", "Formerly Brokertec USA. Institution description modified from Brokertec to ICAP Electronic Broking (US).", "June 2007", "Active", true);
        add("US", "United States of America", "Jersey City", "EDGA", "DIRECT EDGE ECN (EDGA)", "", "www.directedge.com/edga", "", "February 2007", "Active", true);
        add("US", "United States of America", "Jersey City", "EDGX", "DIRECT EDGE ECN (EDGX)", "", "www.directedge.com/edgx", "", "February 2007", "Active", true);
        add("US", "United States of America", "Jersey City", "TRWB", "TRADEWEB LLC", "", "www.tradeweb.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Jersey City", "XBTF", "BROKERTEC FUTURES EXCHANGE", "BTEX", "www.btecfutures.com", "Purchased by Eurex US (XEUS)", "September 2006", "Deleted", false);
        add("US", "United States of America", "Kansas City", "BATS", "BATS TRADING", "BATS", "www.batstrading.com", "", "January 2007", "Active", true);
        add("US", "United States of America", "Kansas City", "XKBT", "KANSAS CITY BOARD OF TRADE", "KCBT", "www.kcbt.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Mill Valley", "BNDD", "BONDDESK", "", "www.bonddeskgroup.com", "Fixed income ATS.", "August 2007", "Active", true);
        add("US", "United States of America", "Minneapolis", "XMGE", "MINNEAPOLIS GRAIN EXCHANGE", "MGE", "www.mgex.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "ARCX", "NYSE ARCA", "NYSE", "www.nyse.com", "Merger of Archipelago and Pacific Exchange with the New York Stockj Exchange into NYSE ARCA.", "September 2006", "Active", true);
        add("US", "United States of America", "New York", "BGCF", "BGC FINANCIAL INC", "", "www.bgcpartners.com", "SEC registered ATS for governtment securities, emerging market debt, government repo, and corporate repo via electronic trading platform and voice brokerage.", "September 2007", "Active", true);
        add("US", "United States of America", "New York", "BIDS", "BIDS TRADING L.P.", "BIDS", "www.bidstrading.com", "", "February 2007", "Active", true);
        add("US", "United States of America", "New York", "BLTD", "BLOOMBERG TRADEBOOK LLC", "", "www.bloombergtradebook.com", "Tradebook is an ATS and ECN registered with the SEC.  Full agency/institutional business and FINRA (formerly NASD) member.", "November 2007", "Active", true);
        add("US", "United States of America", "New York", "ICEL", "ISLAND ECN LTD, THE", "", "www.island.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "ICUS", "ICE FUTURES U.S. INC", "ICE", "www.theice.com", "New York Board of Trade (NYBOT) is renamed in ICE Futures US. XNYF is changed into ICUS as per exchange request New York Board of Trade (NYBOT) is renamed in ICE Futures US. XNYF is changed into ICUS (already created in May 08 - v48) as per exchange request", "June 2008", "Active", true);
        add("US", "United States of America", "New York", "ITGI", "ITG - POSIT EXCHANGE", "", "www.itginc.com", "", "December 2005", "Active", true);
        add("US", "United States of America", "New York", "PINX", "PINK SHEETS LLC (NQB)", "", "www.pinksheets.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "PIPE", "PIPELINE", "", "www.pipelinetrading.com", "", "November 2006", "Active", true);
        add("US", "United States of America", "New York", "PSGM", "PINK SHEETS GREY MARKET", "", "www.pinksheets.com/pink/otcguide/investors_market_tiers.jsp", "", "June 2008", "Active", true);
        add("US", "United States of America", "New York", "XASE", "AMERICAN STOCK EXCHANGE", "AMEX", "www.amex.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XBMK", "BONDMART", "", "www.percentex.com", "", "January 2007", "Active", true);
        add("US", "United States of America", "New York", "XBRT", "BRUT ECN", "BRUT", "www.nasdaqtrader.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XCEC", "COMMODITIES EXCHANGE CENTER", "COMEX", "www.nymex.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XCFF", "CANTOR FINANCIAL FUTURES EXCHANGE", "CANTOR", "www.cantor.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XCSC", "NEW YORK COCOA, COFFEE AND SUGAR EXCHANGE", "CSCE", "www.csce.com", "Merged in 2004 with New York Cotton Exchange (XNYC) and the New York Board of Trade (XNYF)", "September 2006", "Deleted", false);
        add("US", "United States of America", "New York", "XISA", "INTERNATIONAL SECURITIES EXCHANGE, LLC -  ALTERNATIVE MARKETS", "ISE", "www.ise.com", "Parimutuel, eletronic trading platform for derivatives Parimutuel, eletronic trading platform for derivatives. Institution description is updated", "June 2008", "Modified", true);
        add("US", "United States of America", "New York", "XISE", "INTERNATIONAL SECURITIES EXCHANGE, LLC - EQUITIES", "ISE", "www.ise.com", "The ISE Stock Exchange trades equities. Electronic marketplace Equities. Electronic marketplace - Institution description is updated", "June 2008", "Modified", true);
        add("US", "United States of America", "New York", "XISX", "INTERNATIONAL SECURITIES EXCHANGE, LLC", "ISE", "www.iseoptions.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XNAS", "NASDAQ", "NASDAQ", "www.nasdaq.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XNCM", "NASDAQ CAPITAL MARKET", "", "www.nasdaq.com", "Previously (2005) named NASDAQ SmallCap Market.NASDAQ Capital Market security satisfies all applicable qualification requirements for NASDAQ securities in Rule 4300 other than those applicable to NASDAQ National Market securities.", "February 2008", "Active", true);
        add("US", "United States of America", "New York", "XNDQ", "NASDAQ OPTIONS MARKET", "", "www.nasdaq.com", "", "May 2007", "Active", true);
        add("US", "United States of America", "New York", "XNGS", "NASDAQ/NGS (GLOBAL SELECT MARKET)", "NGS", "www.nasdaq.com", "See announcement: http://www.nasdaqtrader.com/trader/news/2006/vendoralerts/nva2006-041.stm", "July 2006", "Active", true);
        add("US", "United States of America", "New York", "XNIM", "NASDAQ INTERMARKET", "", "www.nasdaq.com", "Electronic marketplace comprised of multiple briker-dealers that quote and trade securities listed on NYSE and AMEX", "February 2008", "Active", true);
        add("US", "United States of America", "New York", "XNMS", "NASDAQ/NMS (GLOBAL MARKET)", "NASDAQ/NMS", "www.nasdaq.com", "Renamed from NASDAQ (NATIONAL MARKET SYSTEM)", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XNQL", "NQLX", "NQLX", "www.nqlx.com", "Does no longer exist", "September 2007", "Deleted", false);
        add("US", "United States of America", "New York", "XNYC", "NEW YORK COTTON EXCHANGE", "NYCE", "www.nyce.com", "Merged in 2004 with New York Cocoa, Coffee and Sugar Exchange (XCSC) and the New York Board of Trade (XNYF)", "September 2006", "Deleted", false);
        add("US", "United States of America", "New York", "XNYE", "NEW YORK MERCANTILE EXCHANGE - OTC MARKETS", "NYMEX ECM", "www.nymex.com", "Division of the New York Mercantile Exchange - OTC Markets (ECM)", "May 2008", "Modified", true);
        add("US", "United States of America", "New York", "XNYF", "ICE FUTURES U.S. INC", "ICE", "www.theice.com", "New York Board of Trade (NYBOT) is renamed in ICE Futures US New York Board of Trade (NYBOT) is renamed in ICE Futures US. XNYF is changed into ICUS as per exchange request", "June 2008", "Deleted", false);
        add("US", "United States of America", "New York", "XNYL", "NEW YORK MERCANTILE EXCHANGE - ENERGY MARKETS", "NYMEX MTF LIMITED", "www.nymexonlch.com", "Electronic Trading Platform for energy markets", "May 2008", "Modified", true);
        add("US", "United States of America", "New York", "XNYM", "NEW YORK MERCANTILE EXCHANGE", "NYMEX", "www.nymex.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "New York", "XNYS", "NEW YORK STOCK EXCHANGE, INC.", "NYSE", "www.nyse.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Philadelphia", "XPBT", "PHILADELPHIA BOARD OF TRADE", "PBOT", "www.phlx.com/pbot/index.html", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Philadelphia", "XPHL", "PHILADELPHIA STOCK EXCHANGE", "PHLX", "www.phlx.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Philadelphia", "XPHO", "PHILADELPHIA OPTIONS EXCHANGE", "", "www.phlx.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Phoenix", "XAZX", "ARIZONA STOCK EXCHANGE", "AZX", "www.azx.com", "Closed", "September 2007", "Deleted", false);
        add("US", "United States of America", "San Francisco", "XPSE", "PACIFIC EXCHANGE", "PCX", "www.pacificex.com", "Merged with ARCHIPELAGO into NYSE ARCA (ARCA)", "September 2006", "Deleted", false);
        add("US", "United States of America", "San Mateo", "HEGX", "HEDGESTREET EXCHANGE", "", "www.hedgestreet.com", "", "December 2006", "Active", true);
        add("US", "United States of America", "Stamford", "XPIN", "UBS PIN ATS", "", "www.ubs.com", "Registered ATS for trading US equities", "June 2008", "Active", true);
        add("US", "United States of America", "Washington", "OOTC", "OTC BULLETING BOARD - OTHER OTC", "OTCBB", "www.otcbb.com", "Other OTC", "June 2008", "Active", true);
        add("US", "United States of America", "Washington", "XOTC", "OTC BULLETIN BOARD", "OTCBB", "www.otcbb.com", "", "Before June 2005", "Active", true);
        add("US", "United States of America", "Washington", "XPOR", "PORTAL", "", "www.nasdaqportalmarket.com", "No proof of existence can be found. Deleted in September 2007 due to lack of information on the market. Recreated following the receipt of the needed info.", "October 2007", "Active", true);
        add("US", "United States of America", "Washington/New York", "XADF", "FINRA ALTERNATIVE DISPLAY FACILITY", "", "www.finra.org/AboutFINRA/index.htm", "Created in July 2007 through the consolidation of NASD and the member regulation, FINRA is the largest non-governmental regulator for all securities firms doing business in the United States", "February 2008", "Active", true);
        add("UY", "Uruguay", "Montevideo", "XMNT", "BOLSA DE VALORES DE MONTEVIDEO", "BVMT", "www.bvm.com.uy", "", "Before June 2005", "Active", true);
        add("UZ", "Uzbekistan", "Tashkent", "XCET", "UZBEK COMMODITY EXCHANGE", "", "www.uzex.com", "Modification of the name.", "September 2007", "Active", true);
        add("UZ", "Uzbekistan", "Tashkent", "XCUE", "UZBEKISTAN REPUBLICAN CURRENCY EXCHANGE", "", "", "", "Before June 2005", "Active", true);
        add("UZ", "Uzbekistan", "Tashkent", "XKCE", "KHOREZM INTERREGION COMMODITY EXCHANGE", "", "", "", "Before June 2005", "Active", true);
        add("UZ", "Uzbekistan", "Tashkent", "XSTE", "REPUBLICAN STOCK EXCHANGE", "UZSE", "www.uzse.uz", "Modification of the name.", "September 2007", "Active", true);
        add("UZ", "Uzbekistan", "Tashkent", "XUNI", "UNIVERSAL BROKER'S EXCHANGE 'TASHKENT'", "", "", "", "Before June 2005", "Active", true);
        add("WS", "Vanuatu", "Vila", "GXMA", "GX MARKETCENTER", "", "www.globaris.bind.ws", "", "September 2006", "Active", true);
        add("VE", "Venezuela", "Caracas", "BVCA", "BOLSA ELECTRONICA DE VALORES DE CARACAS", "", "www.bolsadecaracas.com", "Registered market for equities and bonds, Electronic Trading only", "May 2008", "Active", true);
        add("VE", "Venezuela", "Caracas", "XCAR", "BOLSA DE VALORES DE CARACAS", "", "www.bolsadecaracas.com", "", "Before June 2005", "Active", true);
        add("VN", "Viet Nam", "Hanoi", "HSTC", "HANOI SECURITIES TRADING CENTER", "Hanoi STC", "", "OTC auction, equities and bonds for small to medium sized enterprises.", "July 2006", "Active", true);
        add("VN", "Viet Nam", "Ho Chi Minh City", "XSTC", "VIETNAM STOCK EXCHANGE", "STC", "www.vse.org.vn", "Name change from HO CHI MINH SECURITIES TRANSACTION CENTER to VIETNAM STOCK EXCHANGE", "Before June 2005", "Active", true);
        add("ZM", "Zambia", "Lusaka", "XLUS", "LUSAKA STOCK EXCHANGE", "", "www.luse.co.zm", "", "Before June 2005", "Active", true);
        add("ZW", "Zimbabwe", "Harare", "XZIM", "ZIMBABWE STOCK EXCHANGE", "", "www.zse.co.zw", "", "Before June 2005", "Active", true);
        add("ZZ", "Zz", "", "XXXX", "NO MARKET (EG, UNLISTED)", "", "", "", "October 2005", "Active", true);
    }
}
