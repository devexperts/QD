DXFEED API SAMPLES
==================
Copyright (C) 2002-2022 Devexperts LLC

This package contains samples for dxFeed API.
Binaries are in "lib" directory, the sources for samples are in "src" directory.

Included samples
----------------

SIMPLE SAMPLES:

Start with code of simple samples to study DXFeed API.
See src/com/dxfeed/sample/_simple_/ directory.

Use "simple" batch file to run them:

 * "simple PrintQuoteEvents <symbol>" (see src/com/dxfeed/sample/_simple_/PrintQuoteEvents.java)
   Subscribes to Quote events for a specified symbol and prints them until terminated.
 * "simple FetchDailyCandles <symbol>" (see src/com/dxfeed/sample/_simple_/FetchDailyCandles.java)
   Fetches last 20 days of candles for a specified symbol, prints them, and exits.
 * "simple PublishProfiles <address>" (see src/com/dxfeed/sample/_simple_/PublishProfiles.java)
   Using address like ":7700" it starts a server on a specified port where it provides Profile
   event for any symbol ending with ":TEST" suffix.
 * "simple RequestProfile <address> <symbol>" (see src/com/dxfeed/sample/_simple_/RequestProfile.java)
   Using address like "localhost:7700" and a symbol list "A:TEST" it connects to the running
   "PublishProfiles" sample (above), prints symbol profile event, and exits.

OTHER COMMAND-LINE SAMPLES:

 * "sample" (see src/com/dxfeed/sample/api/DXFeedSample.java)
     This sample contains several simple of pieces of code that are featured in DXFeed documentation.
     See http://docs.dxfeed.com/dxfeed/api/com/dxfeed/api/DXFeed.html
     The only difference, is that is expects a symbol (for example IBM) on the command line.
 * "connect" (see src/com/dxfeed/sample/api/DXFeedConnect.java)
     Connects to dxFeed and prints events for a command-line specified event type and symbol.
 * "connect_Quote_IBM" uses the same code as above, but is preconfigured on the command line
     to print Quotes for "IBM" symbol from the dxFeed demo feed.
 * "connect_Quote_IBM_from_File" uses the same code as above, but is preconfigured on the command
     line to print Quotes for "IBM" symbol from the prerecorded "demo-sample.data" file.
     It works without any connection to the internet.
 * "connectIpf" (see src/com/dxfeed/sample/ipf/DXFeedIpfConnect.java)
     Similar to "connect", but instead of taking a list of symbols from a comma-separated list on
     the command line, it takes a list of symbols from an instrument profile file.
 * "connectIpf_Quote_demo" use the same code as above, but is preconfigured to print quotes
     for all symbols from dxFeed demo feed.
 * "fileparser" (see src/com/dxfeed/sample/api/DXFeedFileParser.java)
     Similar to "connect", but instead of connection to the configured data feed source,
     it parses a specified file at once and prints all events for the specified event type and symbol.
 * "fileparser_Quote_IBM" uses the same code as above, but is preconfigured on the command line
     to print Quotes for "IBM" symbol from the provided "demo-sample.data" file.
 * "optionchain" (see com/dxfeed/sample/ipf/option/DXFeedOptionChain.java)
     Download and prints option chains from a specified file for a specified symbol, number of strikes, and months.
 * "optionchain_IBM_10_3: uses the same code as above, but is preconfigured on the command line
     to a demo list of options chain, symbol IBM, 10 strikes, and 3 months to print.
 * "ondemand" (see src/com/dxfeed/sample/ondemand/OnDemandSample.java)
     Uses dxFeed on-demand history data replay service API show Accenture symbol "ACN" drops
     under $1 on May 6, 2010 "Flashcrash" from 14:47:48 to 14:48:02 EST.

CONSOLE SAMPLES:

 * "lastevents" (see src/com/dxfeed/sample/console/LastEventsConsole.java)
     A sample console application that demonstrates a way to subscribe to the big world of symbols, so that the events
     are updated and cached in memory of the process, and then take snapshots of those events from memory whenever they
     are needed. This example repeatedly reads symbol name from the console and prints a snapshot of its last quote,
     trade, summary, and profile events.

SWING UI SAMPLES:

 * "ui_marketdepth" (see src/com/dxfeed/sample/ui/swing/DXFeedMarketDepth.java)
     Simple Swing UI that uses Order event to populate a market-depth type of interface.
 * "ui_quotetable" (see src/com/dxfeed/sample/ui/swing/DXFeedQuoteTable.java)
     Simple Swing UI that uses Quote, Trade, Summary, and Profile events to populate a simple table
     with real-time data. The list of symbols is initialized from "symbols.txt" file.
 * "ui_quotetable_ondemand" (see src/com/dxfeed/sample/ui/swing/DXFeedQuoteTableOnDemand.java)
     Similar to quotetable sample above, but also provides on-demand historical tick data replay
     controls. The list of symbols is initialized from "symbols_ondemand.txt" file.
 * "ui_timeandsales" (see src/com/dxfeed/sample/ui/swing/DXFeedTimeAndSales.java)
     Simple Swing UI that uses TimeAndSale event to populate a tables with a streaming time and sales.
 * "ui_candlechart" (see src/com/dxfeed/sample/ui/swing/DXFeedCandleChart.java)
     Simple Swing UI that uses Candle event with DXFeedTimeSeriesSubscription to draw a simple chart.

All of the above samples take configuration from "dxfeed.properties" file.

Included files
--------------

 * "symbols.txt"          - a list of sample symbols for "ui_quotetable" demo.
 * "symbols_ondemand.txt" - a list of sample symbols for "ui_quotetable_ondemand" demo.
 * "demo-sample.data"     - prerecorded data file from demo feed for a sample list of symbols.
 * "demo-sample.time"     - timestamps for the above prerecorded data file.

Useful addresses
----------------

 * "demo.dxfeed.com:7300"
   Address of free dxFeed demo feed with some test symbols that tick 24/7 and select delayed data.
 * "ondemand:demo.dxfeed.com:7680"
   Address of free dxFeed on-demand data replay service feed that gives full historically access to
   data from May 6, 2010 "Flashcrash".
 * "http://demo.dxfeed.com:7070/onDemand/data"
   Data extraction service for free dxFeed on-demand data.
 * "http://dxfeed.s3.amazonaws.com/masterdata/ipf/demo/mux-demo.ipf.zip"
   URL for instrument profiles for everything provided in demo feed.

The user name for free demo services is "demo" and password is "demo".
Access to the free demo services is configured by default in the provided "dxfeed.properties" file.
