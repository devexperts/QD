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
package com.dxfeed.viewer;

import com.devexperts.util.TimeFormat;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.viewer.tickchart.VolumeAtPriceBar;
import com.dxfeed.viewer.tickchart.VolumeAtPriceChart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JPanel;

public class TickChartRendererPanel extends JPanel {
    private static final Color COLOR_TICK_INVALID = Color.GRAY;
    private static final Color COLOR_TICK_UP = Color.GREEN;
    private static final Color COLOR_TICK_DOWN = Color.RED;
    private static final Color COLOR_TICK_SAME = Color.YELLOW;
    private static final Color COLOR_BID = new Color(125, 2, 183);
    private static final Color COLOR_ASK = new Color(162, 25, 154);
    private static final Color COLOR_BIDASK = new Color(58, 32, 81, 192);
    private static final Color COLOR_INVALID_BIDASK = new Color(119, 0, 17, 192);
    private static final Color COLOR_VOLUME = new Color(0, 88, 195);
    private static final Color COLOR_VOLUME_SELL = new Color(195, 0, 88);
    private static final Color COLOR_VOLUME_BUY = new Color(0, 195, 88);
    private static final Color COLOR_SELECTION = new Color(0xFF, 0xCC, 0x00, 64);
    private static final Color COLOR_AXIS = new Color(105, 105, 105);
    private static final Color COLOR_SYMBOL = new Color(200, 200, 200);
    private static final Color COLOR_CROSSHAIR = new Color(203, 160, 0x00, 192);
    private static final Color COLOR_CROSSHAIR_TEXT = COLOR_CROSSHAIR.brighter().brighter();
    private static final Color COLOR_CURSOR = COLOR_CROSSHAIR.brighter().brighter();
    private static final Color COLOR_VERTICAL_BOOK_BBO_TEXT = Color.WHITE;
    private static final Color COLOR_VERTICAL_BOOK_TEXT = new Color(180, 180, 180);
    // private static final Color COLOR_VERTICAL_BOOK_LINES = new Color(70, 70, 70);
    private static final Color COLOR_VERTICAL_BOOK_BBO_LINE = new Color(200, 200, 200);

    private static final int TIMELINE_PROTRUSION_D = 4;
    private static final int TIMELINE_PROTRUSION_H = 3;
    private static final int TIMELINE_PROTRUSION_M = 2;
    private static final int TIMELINE_PROTRUSION_S = 1;
    private static final int TIMELINE_PROTRUSION_MS = 0;
    private static final int[] TIMELINE_PROTRUSIONS = {
        TIMELINE_PROTRUSION_MS,
        TIMELINE_PROTRUSION_S,
        TIMELINE_PROTRUSION_M,
        TIMELINE_PROTRUSION_H,
        TIMELINE_PROTRUSION_D
    };

    private static final int TIMELINE_PROTRUSION_MULTIPLIER = 10;

    private static final Color[] COLOR_TRADE_TIMELINE = {
        new Color(32, 32, 32),  // ms
        new Color(32, 32, 32),  // s
        new Color(52, 52, 52),  // m
        new Color(72, 72, 72),  // h
        new Color(92, 92, 92)   // d
    };

    /* compare times up to seconds
     * str: 20130726-214435.015
     * pos: 0123456789012345678
     */
    private static final int[][] STRING_TIMELINE_INTERVAL = {
        {16, 19},  // ms
        {13, 15},  // s
        {11, 13},  // m
        {9, 11},   // h
        {0, 8}     // d
    };

    private static final String[] STRING_TIME_AXIS_LABEL = {
        "",        // ms
        "seconds", // s
        "minutes", // m
        "hours",   // h
        "days"     // d
    };

    /*
    private static final Color COLOR_PLAY = Color.GREEN;
    private static final Color COLOR_PAUSE = new Color(0xFF, 0xCC, 0x00);
    */

    private static final int TITLE_HEIGHT = 20;
    private static final int CHART_TOP_MARGIN = TITLE_HEIGHT + 3;
    private static final int CHART_BOTTOM_MARGIN = 65;
    private static final int VERTICAL_BOOK_WIDTH = 80;
    private static final int CHART_RIGHT_MARGIN = VERTICAL_BOOK_WIDTH + 50;
    private static final int LEFT_MARGIN = 2;
    private static final int VOLUME_AT_PRICE_CHART_WIDTH = 60;
    private static final int CHART_LEFT_MARGIN = LEFT_MARGIN + VOLUME_AT_PRICE_CHART_WIDTH + 50;
    private static final int AXIS_TEXT_MARGIN = 3;

    /*
    private static final int PLAYPAUSE_ICON_WIDTH = 6;
    private static final int PLAYPAUSE_ICON_HEIGHT = 8;
    */

    private static final int MIN_HEIGHT_EX_MARGINS = 10;
    private static final int MIN_WIDTH_EX_MARGINS = 90;
    private static final double SIZE_CHART_HEIGHT_PERCENT = 0.10;
    private static final double PRICE_CHART_HEIGHT_PERCENT = 1 - 0.03 - SIZE_CHART_HEIGHT_PERCENT;

    private static final int MIN_TICK_DISPLAY_WIDTH = 3;

    private static final BasicStroke SIMPLE_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] {1.5f}, 0.0f);
    private static final Font DEFAULT_FONT = new Font("SansSerif", Font.PLAIN, 9);

    private BufferedImage chartImage;
    private Graphics2D chartG2D;
    private ArrayList<TimeAndSale> timeAndSales = null;
    private ArrayList<Order> bidOrders = null;
    private ArrayList<Order> askOrders = null;

    private int scheme;

    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean repaintRequired = true;
    private boolean drawCrosshairX = false;
    private boolean drawCrosshairY = false;

    private double minPrice = Double.POSITIVE_INFINITY;
    private double maxPrice = Double.NEGATIVE_INFINITY;
    private double minSize = Double.POSITIVE_INFINITY;
    private double maxSize = Double.NEGATIVE_INFINITY;

    private int totalTickCount = 0;
    private int visibleTickCount = 0;
    private int chartHeightExMargins = 0;
    private int chartWidthExMargins = 0;
    private int tickDisplayWidth = MIN_TICK_DISPLAY_WIDTH;

    private boolean autoZoom = true;
    private double zoomFactorForPriceChart = 1;
    private double zoomFactorForSizeChart = 1;

    private int x_crossHair = 0;
    private int y_crossHair = 0;
    private boolean mouseInside = false;

    private int[] x_lastTimeLineProtrusion = new int[5];
    private int maxProtrusionLevel = -1;

    private VolumeAtPriceChart volumeAtPriceChart;

    private int selectionStart = -1;
    private int selectionEnd = -1;

    private String symbol = "";
    private int sizeChartHeight;
    private int priceChartHeight;
    private double maxBuySize;
    private double maxSellSize;

    @SuppressWarnings("unchecked")
    public TickChartRendererPanel(List<? extends TimeAndSale> timeAndSales, List<? extends Order> bidOrders,
        List<? extends Order> askOrders, int scheme)
    {
        setBackground(Color.BLACK);
        this.timeAndSales = (ArrayList<TimeAndSale>) timeAndSales;
        this.bidOrders = (ArrayList<Order>) bidOrders;
        this.askOrders = (ArrayList<Order>) askOrders;
        this.scheme = scheme;
        setCursor();
    }

    public TickChartRendererPanel(List<? extends TimeAndSale> timeAndSales, List<? extends Order> bidOrders,
        List<? extends Order> askOrders, String title, int scheme)
    {
        this(timeAndSales, bidOrders, askOrders, scheme);
        this.symbol = title;
    }

    private static String price2String(double price) {
        return String.format("%.2f", price);          // always cut to 2 digits, sorry 4-digit prices...
    }

    private static String size2String(double size) {
        return size == (long) size ? Long.toString((long) size) : String.format("%.2f", size); // always cut to 2 digits
    }

    public void selectTicks(int start, int end) {
        selectionStart = start;
        selectionEnd = end;
        setRepaintRequired(true);
    }

    public int getTickIndexByX(int x) {
        int tickIndex = -1;
        if (x > CHART_LEFT_MARGIN && x < CHART_LEFT_MARGIN + chartWidthExMargins && visibleTickCount > 0) {
            tickIndex = (CHART_LEFT_MARGIN + chartWidthExMargins - x) / tickDisplayWidth;
            if (tickIndex > visibleTickCount - 1)
                tickIndex = -1;
        }
        return tickIndex;
    }

    public void setCrosshair(int x, int y) {
        x_crossHair = x;
        y_crossHair = y;
        setDrawCrosshair(x, y);
        repaint();
    }

    public void mouseEntered(int x, int y) {
        mouseInside = true;
        setDrawCrosshair(x, y);
    }

    public void mouseExited() {
        mouseInside = false;
        setDrawCrosshair(-1, -1);
        setRepaintRequired(true);
        repaint();
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getScheme() {
        return scheme;
    }

    public void setScheme(int scheme) {
        this.scheme = scheme;
    }

    public boolean isAutoZoom() {
        return autoZoom;
    }

    public void setAutoZoom(boolean autoZoom) {
        this.autoZoom = autoZoom;
    }

    public void zoom(int amount) {
        autoZoom = false;
        int w = tickDisplayWidth;
        w += amount;
        if (w > chartWidthExMargins)
            w = chartWidthExMargins;
        if (w < MIN_TICK_DISPLAY_WIDTH)
            w = MIN_TICK_DISPLAY_WIDTH;
        tickDisplayWidth = w;
        setRepaintRequired(true);
        repaint();
    }

    /*package*/ void setRepaintRequired(boolean repaintRequired) {
        this.repaintRequired = repaintRequired;
        if (repaintRequired) {
            setDrawCrosshair(x_crossHair, y_crossHair);
        } else {
            lastWidth = getWidth();
            lastHeight = getHeight();
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (isRepaintRequired()) {
            clear();
            clearTimelines();
            computeChartSize();
            computeTotalTickCountAndTicksVisible();
            computeChartZoomFactors();
            drawTitle();

            if (totalTickCount > 0 && chartHeightExMargins > MIN_HEIGHT_EX_MARGINS && chartWidthExMargins > MIN_WIDTH_EX_MARGINS) {
                drawVerticalBook();

                // start with prev = cur
                TimeAndSale curTimeAndSale = timeAndSales.get(0);
                TimeAndSale priorTimeAndSale = curTimeAndSale;

                // save prior time-and-sale Y coordinates for bid/price/ask
                int y_curTickPrice = getChartYCoordinateForPrice(curTimeAndSale.getPrice());
                int y_curTickBid = getChartYCoordinateForPrice(curTimeAndSale.getBidPrice());
                int y_curTickAsk = getChartYCoordinateForPrice(curTimeAndSale.getAskPrice());

                // if more than one event draw line chart from right to left
                if (visibleTickCount > 1) {
                    // x coordinate for current tick (drawing right to left!)
                    int x = CHART_LEFT_MARGIN + chartWidthExMargins - tickDisplayWidth;

                    // x and y for last valid tick
                    TimeAndSale lastValidTimeAndSale = curTimeAndSale;
                    int x_lastValidTick = -1;
                    int y_lastValidTick = -1;

                    if (curTimeAndSale.isValidTick()) {
                        x_lastValidTick = x;
                        y_lastValidTick = y_curTickPrice;
                    }

                    // draw labels for current price/bid/ask/time
                    drawPriceAxisAndLabels(y_curTickPrice, y_curTickBid, y_curTickAsk, timeAndSales.get(1), curTimeAndSale);

                    volumeAtPriceChart = new VolumeAtPriceChart(maxPrice, minPrice, VOLUME_AT_PRICE_CHART_WIDTH, priceChartHeight,
                        (int) (tickDisplayWidth * (1.5 * chartHeightExMargins / chartWidthExMargins)), COLOR_VOLUME_BUY, COLOR_VOLUME_SELL, COLOR_VOLUME);

                    // many events - draw graph with most recent event from the right
                    for (int i = 0; i < visibleTickCount; i++) {
                        if (i < visibleTickCount - 1) {
                            priorTimeAndSale = timeAndSales.get(i + 1);
                        }

                        // update volume at price
                        volumeAtPriceChart.add(curTimeAndSale);

                        // draw background time slices and time labels
                        if (priorTimeAndSale.getTime() != curTimeAndSale.getTime()) {
                            drawTimeLine(x, priorTimeAndSale, curTimeAndSale);
                        }

                        int y_priorTickPrice = getChartYCoordinateForPrice(priorTimeAndSale.getPrice());
                        int y_priorTickBid = getChartYCoordinateForPrice(priorTimeAndSale.getBidPrice());
                        int y_priorTickAsk = getChartYCoordinateForPrice(priorTimeAndSale.getAskPrice());

                        int curTickSizeBarHeight = getChartBarHeightForSize(curTimeAndSale.getSizeAsDouble());

                        // size bar
                        selectSizeColor(curTimeAndSale);
                        if (curTimeAndSale.getAggressorSide() == Side.SELL) {
                            chartG2D.fillRect(x, CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight / 2, tickDisplayWidth - 1, curTickSizeBarHeight);
                        } else {
                            chartG2D.fillRect(x, CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight / 2 - curTickSizeBarHeight, tickDisplayWidth - 1, curTickSizeBarHeight);
                        }

                        // bid-ask channel
                        if (y_curTickBid > y_curTickAsk) {
                            chartG2D.setColor(COLOR_BIDASK);
                            chartG2D.fillRect(x, y_curTickAsk, tickDisplayWidth, y_curTickBid - y_curTickAsk);
                        } else if (y_curTickBid < y_curTickAsk) {
                            chartG2D.setColor(COLOR_INVALID_BIDASK);
                            chartG2D.fillRect(x, y_curTickBid, tickDisplayWidth, y_curTickAsk - y_curTickBid);
                        }

                        // bid line
                        if (!Double.isNaN(curTimeAndSale.getBidPrice())) {
                            chartG2D.setColor(COLOR_BID);
                            chartG2D.drawLine(x, y_curTickBid, x + tickDisplayWidth, y_curTickBid);
                        }

                        // ask line
                        if (!Double.isNaN(curTimeAndSale.getAskPrice())) {
                            chartG2D.setColor(COLOR_ASK);
                            chartG2D.drawLine(x, y_curTickAsk, x + tickDisplayWidth, y_curTickAsk);
                        }

                        // tick chart
                        selectTickColor(priorTimeAndSale, curTimeAndSale);
                        if ((!priorTimeAndSale.isValidTick() && curTimeAndSale.isValidTick()) || !curTimeAndSale.isValidTick()) {
                            chartG2D.drawLine(x, y_curTickPrice, x + tickDisplayWidth, y_curTickPrice);    // horizontal line
                        } else {
                            chartG2D.drawLine(x, y_priorTickPrice, x + tickDisplayWidth, y_curTickPrice);
                        }

                        // if current tick is valid and prior is invalid, then scan back to first valid tick and store coordinates and colors to draw a line between valid ticks
                        if (curTimeAndSale.isValidTick() && !priorTimeAndSale.isValidTick()) {
                            lastValidTimeAndSale = curTimeAndSale;
                            x_lastValidTick = x;
                            y_lastValidTick = y_curTickPrice;
                        } else if (!curTimeAndSale.isValidTick() && priorTimeAndSale.isValidTick() && x_lastValidTick >= 0) {
                            selectTickColor(priorTimeAndSale, lastValidTimeAndSale);
                            // draw dashed line for millisecond intervals
                            Stroke oldStroke = chartG2D.getStroke();
                            chartG2D.setStroke(DASHED_STROKE);
                            chartG2D.drawLine(x, y_priorTickPrice, x_lastValidTick, y_lastValidTick);
                            chartG2D.setStroke(oldStroke);
                        }

                        // selection, if any
                        if (selectionStart <= i && i <= selectionEnd) {
                            chartG2D.setColor(COLOR_SELECTION);
                            chartG2D.fillRect(x, CHART_TOP_MARGIN, tickDisplayWidth, chartHeightExMargins);
                        }

                        // moving backwards
                        x -= tickDisplayWidth;

                        y_curTickPrice = y_priorTickPrice;
                        y_curTickAsk = y_priorTickAsk;
                        y_curTickBid = y_priorTickBid;
                        curTimeAndSale = priorTimeAndSale;
                    }
                    drawTimeLineProtrusionAxises();
                } else {
                    // just one point - draw point
                    chartImage.setRGB(CHART_LEFT_MARGIN + chartWidthExMargins - 1, y_curTickBid, COLOR_BID.getRGB());
                    chartImage.setRGB(CHART_LEFT_MARGIN + chartWidthExMargins - 1, y_curTickPrice, COLOR_TICK_SAME.getRGB());
                    chartImage.setRGB(CHART_LEFT_MARGIN + chartWidthExMargins - 1, y_curTickAsk, COLOR_ASK.getRGB());

                    // draw labels for current price/bid/ask/time
                    drawPriceAxisAndLabels(y_curTickPrice, y_curTickBid, y_curTickAsk, curTimeAndSale, curTimeAndSale);
                }
            }
            setRepaintRequired(false);
        }

        if (volumeAtPriceChart != null) {
            volumeAtPriceChart.paint(chartG2D, LEFT_MARGIN, CHART_TOP_MARGIN);
        }

        g.drawImage(chartImage, 0, 0, null);

        if (visibleTickCount > 0 && chartHeightExMargins > MIN_HEIGHT_EX_MARGINS && chartWidthExMargins > MIN_WIDTH_EX_MARGINS && (drawCrosshairX || drawCrosshairY)) {
            drawCrosshair(g);
        }
    }

    private void setCursor() {
        // Transparent 16 x 16 pixel cursor image.
        BufferedImage cursorImg = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) cursorImg.getGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        g2d.setColor(COLOR_CURSOR);
        g2d.drawLine(0, 8, 16, 8);
        g2d.drawLine(8, 0, 8, 16);
        //g2d.drawOval(4, 4, 8, 8);
        setCursor(Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(8, 8), "Crosshair cursor"));
    }

    private void createEmptyImage() {
        if (getWidth() > 0 && getHeight() > 0) {
            chartImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            chartG2D = (Graphics2D) chartImage.getGraphics();
            chartG2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            chartG2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            chartG2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            chartG2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            chartG2D.setFont(DEFAULT_FONT);
            setRepaintRequired(true);
        }
    }

    private void setDrawCrosshair(int x, int y) {
        drawCrosshairX = (mouseInside && x > CHART_LEFT_MARGIN && x < CHART_LEFT_MARGIN + chartWidthExMargins);
        drawCrosshairY = (y > CHART_TOP_MARGIN && y < CHART_TOP_MARGIN + chartHeightExMargins * PRICE_CHART_HEIGHT_PERCENT);
    }

    private void clear() {
        if (chartImage == null || chartImage.getWidth() != getWidth() || chartImage.getHeight() != getHeight()) {
            createEmptyImage();
        } else {
            chartG2D.clearRect(0, 0, getWidth(), getHeight());
        }
        volumeAtPriceChart = null;
        setRepaintRequired(true);
    }

    private void clearTimelines() {
        Arrays.fill(x_lastTimeLineProtrusion, 0);
        maxProtrusionLevel = -1;
    }

    private void computeChartSize() {
        chartHeightExMargins = getHeight() - CHART_TOP_MARGIN - CHART_BOTTOM_MARGIN;
        chartWidthExMargins = getWidth() - CHART_LEFT_MARGIN - CHART_RIGHT_MARGIN;
    }

    private void computeTotalTickCountAndTicksVisible() {
        totalTickCount = timeAndSales.size();
        if (totalTickCount > 0) {
            // compute scaled tick display width
            if (autoZoom)
                doAutoZoom();
            visibleTickCount = chartWidthExMargins / tickDisplayWidth;
        }
        if (visibleTickCount > totalTickCount)
            visibleTickCount = totalTickCount;
    }

    private void computeChartZoomFactors() {
        minPrice = Double.POSITIVE_INFINITY;
        maxPrice = Double.NEGATIVE_INFINITY;
        minSize = Double.POSITIVE_INFINITY;
        maxSize = Double.NEGATIVE_INFINITY;

        maxBuySize = Double.NEGATIVE_INFINITY;
        maxSellSize = Double.NEGATIVE_INFINITY;

        // get min and max values for zooming
        for (int i = 0; i < visibleTickCount; i++) {
            TimeAndSale timeAndSale = timeAndSales.get(i);

            double price = timeAndSale.getPrice();
            double bidPrice = timeAndSale.getBidPrice();
            double askPrice = timeAndSale.getAskPrice();
            Side side = timeAndSale.getAggressorSide();
            double size = timeAndSale.getSizeAsDouble();

            if (minPrice > price)
                minPrice = price;
            if (minPrice > bidPrice)
                minPrice = bidPrice;
            if (minPrice > askPrice)
                minPrice = askPrice;
            if (minSize > size)
                minSize = size;

            if (maxPrice < price)
                maxPrice = price;
            if (maxPrice < bidPrice)
                maxPrice = bidPrice;
            if (maxPrice < askPrice)
                maxPrice = askPrice;
            if (maxSize < size)
                maxSize = size;

            if (side == Side.SELL) {
                if (maxSellSize < size)
                    maxSellSize = size;
            } else {
                if (maxBuySize < size)
                    maxBuySize = size;
            }
        }

        priceChartHeight = (int) (PRICE_CHART_HEIGHT_PERCENT * chartHeightExMargins);
        sizeChartHeight = (int) (SIZE_CHART_HEIGHT_PERCENT * chartHeightExMargins);
        zoomFactorForPriceChart = (PRICE_CHART_HEIGHT_PERCENT * chartHeightExMargins) / (maxPrice - minPrice);
        zoomFactorForSizeChart = (SIZE_CHART_HEIGHT_PERCENT * chartHeightExMargins) / (2 * maxSize);
    }

    private void drawTitle() {
        if (symbol.length() > 0) {
            final String period;
            if (timeAndSales.size() > 1) {
                TimeAndSale firstEvent = timeAndSales.get(timeAndSales.size() - 1);
                TimeAndSale lastEvent = timeAndSales.get(0);
                period = "showing " + visibleTickCount + " of " + timeAndSales.size() + " last ticks from " +
                    TimeFormat.DEFAULT.withMillis().format(firstEvent.getTime()) + " to " + TimeFormat.DEFAULT.withMillis().format(lastEvent.getTime());
            } else if (timeAndSales.size() == 1) {
                period = "single tick at " + TimeFormat.DEFAULT.withMillis().format(timeAndSales.get(0).getTime());
            } else {
                period = "waiting for data...";
            }

            FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
            Rectangle2D bounds = metrics.getStringBounds(symbol, null);

            chartG2D.setColor(COLOR_SYMBOL);
            chartG2D.drawString(symbol, 1, (int) bounds.getHeight() + 2);
            chartG2D.setColor(COLOR_AXIS);
            chartG2D.drawString(period, 1 + (int) bounds.getWidth() + 4, (int) bounds.getHeight() + 2);
        }
    }

    private void selectTickColor(TimeAndSale priorTimeAndSale, TimeAndSale curTimeAndSale) {
        if (!curTimeAndSale.isValidTick()) {
            chartG2D.setColor(COLOR_TICK_INVALID);
        } else if (curTimeAndSale.getPrice() > priorTimeAndSale.getPrice()) {
            chartG2D.setColor(COLOR_TICK_UP);
        } else if (curTimeAndSale.getPrice() == priorTimeAndSale.getPrice()) {
            chartG2D.setColor(COLOR_TICK_SAME);
        } else {
            chartG2D.setColor(COLOR_TICK_DOWN);
        }
    }

    private void selectSizeColor(TimeAndSale timeAndSale) {
        if (!timeAndSale.isValidTick()) {
            chartG2D.setColor(COLOR_TICK_INVALID);
        } else if (timeAndSale.getAggressorSide() == Side.SELL) {
            chartG2D.setColor(COLOR_VOLUME_SELL);
        } else if (timeAndSale.getAggressorSide() == Side.BUY) {
            chartG2D.setColor(COLOR_VOLUME_BUY);
        } else if (timeAndSale.getAggressorSide() == Side.UNDEFINED) {
            chartG2D.setColor(COLOR_VOLUME);
        }
    }

    private int getChartYCoordinateForPrice(double price) {
        int y;
        if (price == 0 || Double.isNaN(price)) {
            y = (int) ((maxPrice - minPrice) * zoomFactorForPriceChart);
        } else {
            y = (int) ((maxPrice - price) * zoomFactorForPriceChart);
        }
        return y + CHART_TOP_MARGIN;
    }

    private double getPriceByY(int y) {
        double price = 0;
        if (zoomFactorForPriceChart > 0) {
            price = maxPrice - (y - CHART_TOP_MARGIN) / zoomFactorForPriceChart;
        }
        return price;
    }

    private int getChartBarHeightForSize(double size) {
        int y = (int) Math.floor(size * zoomFactorForSizeChart);
        if (size > 0 && y == 0)
            y = 1;
        return y;
    }

    private void doAutoZoom() {
        autoZoom = true;
        tickDisplayWidth = Math.max(chartWidthExMargins / totalTickCount, MIN_TICK_DISPLAY_WIDTH);
        setRepaintRequired(true);
        repaint();
    }

    private void drawVerticalBook() {
        // draw bbo
        if (bidOrders.size() > 0 && askOrders.size() > 0) {
            double bidMaxSize = getMaxAggregatedSize(bidOrders);
            double askMaxSize = getMaxAggregatedSize(askOrders);

            double maxSize = Math.max(bidMaxSize, askMaxSize);
            double sizeZoomFactor = (VERTICAL_BOOK_WIDTH - 2) / maxSize;

            // aggregate sizes
            int askIndex = 0;
            double askBestPrice = askOrders.get(askIndex).getPrice();
            double askSize = askOrders.get(askIndex).getSizeAsDouble();
            while (askIndex + 1 < askOrders.size() && askOrders.get(askIndex + 1).getPrice() == askBestPrice) {
                askSize += askOrders.get(askIndex + 1).getSizeAsDouble();
                askIndex++;
            }

            // aggregate sizes
            int bidIndex = 0;
            double bidBestPrice = bidOrders.get(bidIndex).getPrice();
            double bidSize = bidOrders.get(bidIndex).getSizeAsDouble();
            while (bidIndex + 1 < bidOrders.size() && bidOrders.get(bidIndex + 1).getPrice() == bidBestPrice) {
                bidSize += bidOrders.get(bidIndex + 1).getSizeAsDouble();
                bidIndex++;
            }

            int y_price = getChartYCoordinateForPrice(askBestPrice);
            int y_nextPrice = getChartYCoordinateForPrice(bidBestPrice);

            if (y_price < CHART_TOP_MARGIN)
                y_price = CHART_TOP_MARGIN;
            if (y_nextPrice > priceChartHeight + CHART_TOP_MARGIN)
                y_nextPrice = priceChartHeight + CHART_TOP_MARGIN;

            final int h = y_nextPrice - y_price;
            // draw bar
            Color color = OrderCellSupport.selectBackground(1, scheme).darker();
            chartG2D.setColor(color);
            chartG2D.fillRect(getWidth() - VERTICAL_BOOK_WIDTH - 2, y_price, VERTICAL_BOOK_WIDTH, h);

            // draw sizes
            chartG2D.setColor(color.brighter());

            int askSizeBarWidth = (int) (askSize * sizeZoomFactor) - 1;
            if (askSizeBarWidth > VERTICAL_BOOK_WIDTH - 2)
                askSizeBarWidth = VERTICAL_BOOK_WIDTH - 2;
            if (askSizeBarWidth == 0 && askSize > 0)
                askSizeBarWidth = 1;
            chartG2D.fillRect(getWidth() - VERTICAL_BOOK_WIDTH - 1, y_price + 2, askSizeBarWidth, h / 2 - 2);

            int bidSizeBarWidth = (int) (bidSize * sizeZoomFactor) - 1;
            if (bidSizeBarWidth > VERTICAL_BOOK_WIDTH - 2)
                bidSizeBarWidth = VERTICAL_BOOK_WIDTH - 2;
            if (bidSizeBarWidth == 0 && bidSize > 0)
                bidSizeBarWidth = 1;
            chartG2D.fillRect(getWidth() - VERTICAL_BOOK_WIDTH - 1, y_price + h / 2 + 1, bidSizeBarWidth, h / 2 - 1);

            //chartG2D.setColor(COLOR_BID);
            //chartG2D.setColor(COLOR_VERTICAL_BOOK_LINES);
            chartG2D.drawLine(getWidth() - VERTICAL_BOOK_WIDTH - 4, y_nextPrice, getWidth() - 3, y_nextPrice);

            //chartG2D.setColor(COLOR_ASK);
            //chartG2D.setColor(COLOR_VERTICAL_BOOK_LINES);
            chartG2D.drawLine(getWidth() - VERTICAL_BOOK_WIDTH - 4, y_price, getWidth() - 3, y_price);

            chartG2D.setColor(COLOR_VERTICAL_BOOK_BBO_LINE);
            chartG2D.drawLine(getWidth() - VERTICAL_BOOK_WIDTH - 4, y_price + h / 2, getWidth() - 3, y_price + h / 2);
             /*
            chartG2D.setColor(COLOR_VERTICAL_BOOK_BBO_TEXT);
            String strPriceAndSize = bidBestPrice + " x " + bidSize;
            FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
            Rectangle2D bounds = metrics.getStringBounds(strPriceAndSize, null);
            if ((int) bounds.getHeight() < h - 6)
                chartG2D.drawString(strPriceAndSize, getWidth() - VERTICAL_BOOK_WIDTH - 2 + VERTICAL_BOOK_WIDTH / 2 - (int) bounds.getWidth() / 2, y_Price + h - (int) bounds.getHeight() / 2 + 3);

            strPriceAndSize = askBestPrice + " x " + askSize;
            metrics = chartG2D.getFontMetrics(chartG2D.getFont());
            bounds = metrics.getStringBounds(strPriceAndSize, null);
            if ((int) bounds.getHeight() < h - 6)
                chartG2D.drawString(strPriceAndSize, getWidth() - VERTICAL_BOOK_WIDTH - 2 + VERTICAL_BOOK_WIDTH / 2 - (int) bounds.getWidth() / 2, y_Price + (int) bounds.getHeight() / 2 + 5);
            */

            chartG2D.setColor(COLOR_VERTICAL_BOOK_BBO_TEXT);
            String strPrice = ViewerCellValue.formatPrice(bidBestPrice);
            String strSize = ViewerCellValue.formatSize(bidSize);

            FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
            Rectangle2D bounds = metrics.getStringBounds(strPrice, null);
            if ((int) bounds.getHeight() < h - 4) {
                chartG2D.drawString(strPrice, getWidth() - VERTICAL_BOOK_WIDTH - 2, y_price + h - (int) bounds.getHeight() / 2 + 3);
            }

            bounds = metrics.getStringBounds(strSize, null);
            if ((int) bounds.getHeight() < h - 4) {
                chartG2D.drawString(strSize, getWidth() - (int) bounds.getWidth() - 2, y_price + h - (int) bounds.getHeight() / 2 + 3);
            }

            strPrice = ViewerCellValue.formatPrice(askBestPrice);
            strSize = ViewerCellValue.formatSize(askSize);

            bounds = metrics.getStringBounds(strPrice, null);
            if ((int) bounds.getHeight() < h - 4) {
                chartG2D.drawString(strPrice, getWidth() - VERTICAL_BOOK_WIDTH - 2, y_price + (int) bounds.getHeight() / 2 + 4);
            }

            bounds = metrics.getStringBounds(strSize, null);
            if ((int) bounds.getHeight() < h - 4) {
                chartG2D.drawString(strSize, getWidth() - (int) bounds.getWidth() - 2, y_price + (int) bounds.getHeight() / 2 + 4);
            }

            if (bidIndex < bidOrders.size() - 1)
                drawVerticalBookBidOrAsk(true, bidOrders, bidIndex, sizeZoomFactor);
            if (askIndex < askOrders.size() - 1)
                drawVerticalBookBidOrAsk(false, askOrders, askIndex, sizeZoomFactor);
        }
    }

    private double getMaxAggregatedSize(List<? extends Order> orders) {
        double maxSize = 0;
        if (orders.size() > 0) {
            double curSize = 0;
            double curPrice = 0;
            for (Order o : orders) {
                if (o.getPrice() != curPrice) {
                    if (curSize > maxSize)
                        maxSize = curSize;
                    // next price level started
                    curPrice = o.getPrice();
                    curSize = o.getSizeAsDouble();
                } else {
                    // continuing current price level
                    curSize += o.getSizeAsDouble();
                }
                if (o.getPrice() < minPrice || o.getPrice() > maxPrice)
                    break;
            }
            // check for last price level
            if (curSize > maxSize)
                maxSize = curSize;
        }
        return maxSize;
    }

    private void drawVerticalBookBidOrAsk(boolean isBid, List<? extends Order> orders, int startIndex,
        double sizeZoomFactor)
    {
        int y_nextPrice = 1 + CHART_TOP_MARGIN;
        int priceGroup = 1;

        if (orders.size() > 0) {
            double price = orders.get(startIndex).getPrice();
            int y_price = getChartYCoordinateForPrice(price);

            for (int i = startIndex + 1; i < orders.size() && y_nextPrice < priceChartHeight + CHART_TOP_MARGIN && y_nextPrice > CHART_TOP_MARGIN; i++)
            {
                Order o = orders.get(i);
                final double nextPrice = o.getPrice();
                y_nextPrice = getChartYCoordinateForPrice(nextPrice);

                if (price != nextPrice)
                    priceGroup++;

                if (isBid) {
                    if (y_price < CHART_TOP_MARGIN || y_price > priceChartHeight + CHART_TOP_MARGIN) {
                        y_price = priceChartHeight + CHART_TOP_MARGIN;
                    }
                    if (y_nextPrice < CHART_TOP_MARGIN || y_nextPrice > priceChartHeight + CHART_TOP_MARGIN) {
                        y_nextPrice = priceChartHeight + CHART_TOP_MARGIN;
                    }
                } else {
                    if (y_price < CHART_TOP_MARGIN || y_price > priceChartHeight + CHART_TOP_MARGIN) {
                        y_price = CHART_TOP_MARGIN;
                    }
                    if (y_nextPrice < CHART_TOP_MARGIN || y_nextPrice > priceChartHeight + CHART_TOP_MARGIN) {
                        y_nextPrice = CHART_TOP_MARGIN;
                    }
                }

                Color color = OrderCellSupport.selectBackground(priceGroup, scheme).darker();
                chartG2D.setColor(color);
                final int h = (isBid ? y_nextPrice - y_price : y_price - y_nextPrice) - 1;
                final int y_curPrice = isBid ? y_price : y_nextPrice;
                chartG2D.fillRect(getWidth() - VERTICAL_BOOK_WIDTH - 2, y_curPrice + 1, VERTICAL_BOOK_WIDTH, h);
                // draw size
                chartG2D.setColor(color.brighter());
                int sizeBarWidth = (int) (o.getSizeAsDouble() * sizeZoomFactor) - 1;
                if (sizeBarWidth > VERTICAL_BOOK_WIDTH - 2)
                    sizeBarWidth = VERTICAL_BOOK_WIDTH - 2;
                if (sizeBarWidth == 0 && o.getSizeAsDouble() > 0)
                    sizeBarWidth = 1;
                chartG2D.fillRect(getWidth() - VERTICAL_BOOK_WIDTH - 1, y_curPrice + 2, sizeBarWidth, h - 2);

                if (nextPrice >= minPrice && nextPrice < maxPrice) {
                    //chartG2D.setColor(isBid? COLOR_BID : COLOR_ASK);
                    //chartG2D.setColor(COLOR_VERTICAL_BOOK_LINES);
                    chartG2D.drawLine(getWidth() - VERTICAL_BOOK_WIDTH - 5, y_nextPrice, getWidth() - 3, y_nextPrice);
                }

                // draw price and size
                chartG2D.setColor(COLOR_VERTICAL_BOOK_TEXT);
                String strPrice = ViewerCellValue.formatPrice(nextPrice);
                String strSize = ViewerCellValue.formatSize(o.getSizeAsDouble());

                FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
                Rectangle2D bounds = metrics.getStringBounds(strPrice, null);
                if ((int) bounds.getHeight() < h - 4) {
                    chartG2D.drawString(strPrice, getWidth() - VERTICAL_BOOK_WIDTH - 2,
                        (isBid ? y_price + 1 + h - (int) bounds.getHeight() + 10 : y_nextPrice + 1 + (int) bounds.getHeight() - 2));
                }
                bounds = metrics.getStringBounds(strSize, null);
                if ((int) bounds.getHeight() < h - 4) {
                    chartG2D.drawString(strSize, getWidth() - (int) bounds.getWidth() - 2,
                        (isBid ? y_price + 1 + h - (int) bounds.getHeight() + 10 : y_nextPrice + 1 + (int) bounds.getHeight() - 2));
                }

                y_price = y_nextPrice;
                price = nextPrice;
            }
        }
    }

    private void drawCrosshair(Graphics g) {
        g.setFont(DEFAULT_FONT);
        final FontMetrics metrics = g.getFontMetrics(g.getFont());
        Rectangle2D bounds;

        if (drawCrosshairX) {
            g.setColor(COLOR_CROSSHAIR);
            g.drawLine(x_crossHair, CHART_TOP_MARGIN, x_crossHair, getHeight() - 14);

            final int tickIndexAtCrosshair = getTickIndexByX(x_crossHair);
            if (tickIndexAtCrosshair >= 0) {
                g.setColor(COLOR_CROSSHAIR_TEXT);
                final TimeAndSale curTimeAndSale = timeAndSales.get(tickIndexAtCrosshair);
                final String strTimeAtCrosshair = TimeFormat.DEFAULT.withMillis().format(curTimeAndSale.getTime());
                bounds = metrics.getStringBounds(strTimeAtCrosshair, null);

                int x_text = x_crossHair - (int) bounds.getWidth() / 2;
                if (x_text < (int) bounds.getWidth() / 2) {
                    x_text = x_crossHair;
                } else if (x_text > CHART_LEFT_MARGIN + chartWidthExMargins - (int) bounds.getWidth() / 2) {
                    x_text = x_crossHair - metrics.stringWidth(strTimeAtCrosshair);
                }

                g.drawString(strTimeAtCrosshair, x_text, getHeight() - 4);

                int y_curTickPrice = getChartYCoordinateForPrice(curTimeAndSale.getPrice()) + (int) bounds.getHeight() / 2 - 2;
                int y_curTickBid = getChartYCoordinateForPrice(curTimeAndSale.getBidPrice()) + (int) bounds.getHeight() + 2;
                int y_curTickAsk = getChartYCoordinateForPrice(curTimeAndSale.getAskPrice());

                if (curTimeAndSale.getBidPrice() > 0) {
                    drawCrosshairLabel(g, price2String(curTimeAndSale.getBidPrice()), x_crossHair, y_curTickBid, COLOR_BID);
                }
                if (curTimeAndSale.getAskPrice() > 0) {
                    drawCrosshairLabel(g, price2String(curTimeAndSale.getAskPrice()), x_crossHair, y_curTickAsk, COLOR_ASK);
                }

                int y_curTickSize = CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight / 2;
                if (curTimeAndSale.getAggressorSide() == Side.SELL) {
                    y_curTickSize += getChartBarHeightForSize(curTimeAndSale.getSizeAsDouble()) + (int) bounds.getHeight() + 1;
                } else {
                    y_curTickSize -= getChartBarHeightForSize(curTimeAndSale.getSizeAsDouble()) + 1;
                }

                selectSizeColor(curTimeAndSale);
                drawCrosshairLabel(g, size2String(curTimeAndSale.getSizeAsDouble()), x_crossHair, y_curTickSize, chartG2D.getColor());

                if (tickIndexAtCrosshair < visibleTickCount - 1) {
                    selectTickColor(timeAndSales.get(tickIndexAtCrosshair + 1), curTimeAndSale);
                    g.setColor(chartG2D.getColor());  // dirty hack
                } else {
                    g.setColor(COLOR_TICK_SAME);
                }
                drawCrosshairLabel(g, price2String(curTimeAndSale.getPrice()), x_crossHair, y_curTickPrice, g.getColor());
            }
        }

        if (drawCrosshairY) {
            g.setColor(COLOR_CROSSHAIR);
            g.drawLine(CHART_LEFT_MARGIN, y_crossHair, CHART_LEFT_MARGIN + chartWidthExMargins, y_crossHair);
            g.drawLine(LEFT_MARGIN, y_crossHair, LEFT_MARGIN + VOLUME_AT_PRICE_CHART_WIDTH, y_crossHair);
            g.drawLine(getWidth() - VERTICAL_BOOK_WIDTH - 5, y_crossHair, getWidth() - 2, y_crossHair);

            g.setColor(COLOR_CROSSHAIR_TEXT);
            final String strPriceAtCrosshair = price2String(getPriceByY(y_crossHair));
            bounds = metrics.getStringBounds(strPriceAtCrosshair, null);
            g.drawString(strPriceAtCrosshair, CHART_LEFT_MARGIN + chartWidthExMargins + AXIS_TEXT_MARGIN, y_crossHair + (int) bounds.getHeight() / 2 - 2);
            g.drawString(strPriceAtCrosshair, CHART_LEFT_MARGIN - AXIS_TEXT_MARGIN - (int) bounds.getWidth(), y_crossHair + (int) bounds.getHeight() / 2 - 2);

            if (volumeAtPriceChart != null) {
                //VolumeAtPriceBar volumeAtPriceBar = volumeAtPriceChart.getVolumeAtPrice(price);
                VolumeAtPriceBar volumeAtPriceBar = volumeAtPriceChart.getVolumeAtY(y_crossHair - CHART_TOP_MARGIN);
                if (volumeAtPriceBar != null) {
                    if (volumeAtPriceBar.getBuySize() + volumeAtPriceBar.getUndefinedSize() > 0) {
                        final String strBuyVolumeAtCrosshair = size2String(volumeAtPriceBar.getBuySize());
                        final String strUndefinedVolumeAtCrosshair = size2String(volumeAtPriceBar.getUndefinedSize());
                        //String strBuyAndUndefinedVolumeAtCrosshair = size2String(volumeAtPriceBar.getBuySize() + volumeAtPriceBar.getUndefinedSize());

                        bounds = metrics.getStringBounds(strUndefinedVolumeAtCrosshair, null);
                        int buyVolumeWidth = (int) bounds.getWidth();
                        if (volumeAtPriceBar.getUndefinedSize() > 0) {
                            drawCrosshairLabel(g, strUndefinedVolumeAtCrosshair, LEFT_MARGIN + VOLUME_AT_PRICE_CHART_WIDTH / 2 - 2,
                                y_crossHair + (int) bounds.getHeight() / 2, COLOR_VOLUME);
                        } else {
                            buyVolumeWidth = -3; // move back to center
                        }

                        if (volumeAtPriceBar.getBuySize() > 0) {
                            bounds = metrics.getStringBounds(strBuyVolumeAtCrosshair, null);
                            drawCrosshairLabel(g, strBuyVolumeAtCrosshair, LEFT_MARGIN + VOLUME_AT_PRICE_CHART_WIDTH / 2 -
                                2 + buyVolumeWidth + 3, y_crossHair + (int) bounds.getHeight() / 2, COLOR_VOLUME_BUY);
                        }

                        //bounds = metrics.getStringBounds(strBuyAndUndefinedVolumeAtCrosshair, null);
                        //drawCrosshairLabel(g, strBuyAndUndefinedVolumeAtCrosshair, LEFT_MARGIN + VOLUME_AT_PRICE_CHART_WIDTH / 2 - 2, y_crossHair + (int)bounds.getHeight() / 2, COLOR_VOLUME);
                    }

                    if (volumeAtPriceBar.getSellSize() > 0) {
                        final String strSellVolumeAtCrosshair = size2String(volumeAtPriceBar.getSellSize());
                        bounds = metrics.getStringBounds(strSellVolumeAtCrosshair, null);
                        drawCrosshairLabel(g, strSellVolumeAtCrosshair, LEFT_MARGIN + VOLUME_AT_PRICE_CHART_WIDTH / 2 -
                            (int) bounds.getWidth() - 5, y_crossHair + (int) bounds.getHeight() / 2, COLOR_VOLUME_SELL);
                    }
                }
            }
        }
    }

    private void drawCrosshairLabel(Graphics g, String label, int x, int y, Color color) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        Rectangle2D bounds = metrics.getStringBounds(label, null);

        Color c = color.darker().darker().darker();
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 192));
        g.fillRect(x + 2, y - (int) bounds.getHeight() - 1, (int) bounds.getWidth() + 2, (int) bounds.getHeight() + 2);

        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 192));
        g.drawRect(x + 2, y - (int) bounds.getHeight() - 1, (int) bounds.getWidth() + 2, (int) bounds.getHeight() + 2);
        g.drawString(label, x + 4, y - 2);
    }

    private boolean isRepaintRequired() {
        return repaintRequired || lastWidth != getWidth() || lastHeight != getHeight();
    }

    private void drawTimeLineProtrusionAxises() {
        final FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
        for (int protrusionLevel : TIMELINE_PROTRUSIONS) {
            if (protrusionLevel == TIMELINE_PROTRUSION_MS || protrusionLevel > maxProtrusionLevel)
                continue;
            chartG2D.setColor(COLOR_TRADE_TIMELINE[protrusionLevel]);
            final int y_timeLine = CHART_TOP_MARGIN + chartHeightExMargins + 2 + protrusionLevel * TIMELINE_PROTRUSION_MULTIPLIER;
            chartG2D.drawLine(CHART_LEFT_MARGIN, y_timeLine, CHART_LEFT_MARGIN + chartWidthExMargins, y_timeLine);

            final String strTimeLineAxisLabel = STRING_TIME_AXIS_LABEL[protrusionLevel];
            final Rectangle2D bounds = metrics.getStringBounds(strTimeLineAxisLabel, null);
            chartG2D.drawString(strTimeLineAxisLabel, CHART_LEFT_MARGIN + chartWidthExMargins + 2, y_timeLine + (int) bounds.getHeight() / 2 - 1);
        }
    }

    private void drawTimeLine(int x, TimeAndSale priorTimeAndSale, TimeAndSale curTimeAndSale) {
        final String strPriorTime = TimeFormat.DEFAULT.withMillis().format(priorTimeAndSale.getTime());
        final String strCurTime = TimeFormat.DEFAULT.withMillis().format(curTimeAndSale.getTime());
        final FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
        final Stroke oldStroke = chartG2D.getStroke();

        // Drawing timeline axis protrusions like this:
        // |   |   |   |   |     |    |  |  |   |
        // ---------- axis -----------------------
        // D1  H1  M1  S1  MS1   MS2  S2 M2 H2  D2
        // |   |   |   |              |  |  |   |
        // |   |   |   |______________|  |  |   |
        // |   |   |_____________________|  |   |
        // |   |____________________________|   |
        // |____________________________________|

        for (int protrusionLevel : TIMELINE_PROTRUSIONS) {
            if (!strPriorTime.regionMatches(0, strCurTime, 0, STRING_TIMELINE_INTERVAL[protrusionLevel][1])) {
                final String strTimeAxisLabel = strCurTime.substring(STRING_TIMELINE_INTERVAL[protrusionLevel][0],
                    STRING_TIMELINE_INTERVAL[protrusionLevel][1]);
                chartG2D.setStroke(protrusionLevel == TIMELINE_PROTRUSION_MS ? DASHED_STROKE : SIMPLE_STROKE);
                chartG2D.setColor(COLOR_TRADE_TIMELINE[protrusionLevel]);
                final int y_timeLine = CHART_TOP_MARGIN + chartHeightExMargins + 2 + protrusionLevel * TIMELINE_PROTRUSION_MULTIPLIER;
                chartG2D.drawLine(x, CHART_TOP_MARGIN, x, y_timeLine);
                if (protrusionLevel != TIMELINE_PROTRUSION_MS) {
                    final int x_last = x_lastTimeLineProtrusion[protrusionLevel];
                    final Rectangle2D bounds = metrics.getStringBounds(strTimeAxisLabel, null);
                    if (x_last == 0 || x_last - x > (int) bounds.getWidth() / 2 + 4) {
                        chartG2D.setColor(COLOR_TRADE_TIMELINE[protrusionLevel].brighter().brighter());
                        chartG2D.drawString(strTimeAxisLabel, x - (int) bounds.getWidth() / 2 + 1, y_timeLine + (int) bounds.getHeight() - 2);
                        x_lastTimeLineProtrusion[protrusionLevel] = x;
                    }
                }
            } else {
                // TIMELINE_PROTRUSIONS' elements are increasing
                maxProtrusionLevel = Math.max(maxProtrusionLevel, protrusionLevel - 1);
                break;
            }
        }
        chartG2D.setStroke(oldStroke);
    }

    private void drawPriceAxisAndLabels(int y_priceLabel, int y_bidPriceLabel, int y_askPriceLabel,
        TimeAndSale priorTimeAndSale, TimeAndSale curTimeAndSale)
    {
        String strLastTickPrice = Double.toString(curTimeAndSale.getPrice());
        String strMinPrice = price2String(minPrice);
        String strMaxPrice = price2String(maxPrice);
        String strLastTickBid = "";
        String strLastTickAsk = "";
        if (!Double.isNaN(curTimeAndSale.getBidPrice()))
            strLastTickBid = price2String(curTimeAndSale.getBidPrice());
        if (!Double.isNaN(curTimeAndSale.getAskPrice()))
            strLastTickAsk = price2String(curTimeAndSale.getAskPrice());

        String strMaxBuySize = "";
        String strMaxSellSize = "";
        if (maxBuySize > 0)
            strMaxBuySize = size2String(maxBuySize);
        if (maxSellSize > 0)
            strMaxSellSize = size2String(maxSellSize);

        String strLastTickSize = size2String(curTimeAndSale.getSizeAsDouble());
        FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
        Stroke oldStroke = chartG2D.getStroke();
        chartG2D.setStroke(SIMPLE_STROKE);
        drawPriceAxis();
        drawSizeAxis();
        chartG2D.setStroke(oldStroke);
        drawSizeLabels(strMaxBuySize, strMaxSellSize, strLastTickSize, curTimeAndSale, metrics);
        drawPriceLabels(y_priceLabel, y_bidPriceLabel, y_askPriceLabel, priorTimeAndSale, curTimeAndSale, strLastTickPrice, strMinPrice, strMaxPrice, strLastTickBid, strLastTickAsk, metrics);
        // String strLastTickTime = TimeFormat.DEFAULT.withMillis().format(curTimeAndSale.getTime());
        // drawCurrentTickTime(strLastTickTime, metrics);
    }

    private void drawPriceAxis() {
        final int x1 = CHART_LEFT_MARGIN;
        final int x2 = CHART_LEFT_MARGIN + chartWidthExMargins;
        final int y1 = CHART_TOP_MARGIN;
        final int y2 = CHART_TOP_MARGIN + (int) (chartHeightExMargins * PRICE_CHART_HEIGHT_PERCENT);
        chartG2D.setColor(COLOR_AXIS);
        chartG2D.drawLine(x1, y1, x1, y2);
        chartG2D.drawLine(x2, y1, x2, y2);
    }

    private void drawSizeAxis() {
        final int x1 = CHART_LEFT_MARGIN + chartWidthExMargins;
        final int x2 = CHART_LEFT_MARGIN;
        final int y1 = CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight;
        final int y2 = CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight / 2;
        final int y3 = CHART_TOP_MARGIN + chartHeightExMargins;
        chartG2D.setColor(COLOR_VOLUME);
        chartG2D.drawLine(x1, y1, x1, y2 - 1);
        chartG2D.drawLine(x2, y1, x2, y2 - 1);
        chartG2D.setColor(COLOR_VOLUME_SELL);
        chartG2D.drawLine(x1, y2 + 1, x1, y3);
        chartG2D.drawLine(x2, y2 + 1, x2, y3);
    }

    private void drawSizeLabels(String strMaxBuySize, String strMaxSellSize, String strLastTickSize,
        TimeAndSale curTimeAndSale, FontMetrics metrics)
    {
        chartG2D.setColor(COLOR_VOLUME);
        Rectangle2D bounds = metrics.getStringBounds(strMaxBuySize, null);
        final int x1 = CHART_LEFT_MARGIN + chartWidthExMargins + AXIS_TEXT_MARGIN;
        int x2 = CHART_LEFT_MARGIN - AXIS_TEXT_MARGIN - (int) bounds.getWidth();
        int y = CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight + (int) bounds.getHeight() - 4;
        chartG2D.drawString(strMaxBuySize, x1, y);
        chartG2D.drawString(strMaxBuySize, x2, y);

        chartG2D.setColor(COLOR_VOLUME_SELL);
        bounds = metrics.getStringBounds(strMaxSellSize, null);
        x2 = CHART_LEFT_MARGIN - AXIS_TEXT_MARGIN - (int) bounds.getWidth();
        y = CHART_TOP_MARGIN + chartHeightExMargins + (int) bounds.getHeight() - 4;
        chartG2D.drawString(strMaxSellSize, x1, y);
        chartG2D.drawString(strMaxSellSize, x2, y);

        selectSizeColor(curTimeAndSale);
        bounds = metrics.getStringBounds(strLastTickSize, null);
        y = CHART_TOP_MARGIN + chartHeightExMargins - sizeChartHeight / 2 + (int) bounds.getHeight() / 2 - 2;
        chartG2D.drawString(strLastTickSize, x1, y);
    }

    private void drawPriceLabels(int y_priceLabel, int y_bidPriceLabel, int y_askPriceLabel,
        TimeAndSale priorTimeAndSale, TimeAndSale curTimeAndSale, String strLastTickPrice, String strMinPrice,
        String strMaxPrice, String strLastTickBid, String strLastTickAsk, FontMetrics metrics)
    {
        chartG2D.setColor(COLOR_AXIS);
        final int x1 = CHART_LEFT_MARGIN + chartWidthExMargins + AXIS_TEXT_MARGIN;
        final int x2 = CHART_LEFT_MARGIN - AXIS_TEXT_MARGIN;
        // min price label
        Rectangle2D bounds = metrics.getStringBounds(strMinPrice, null);
        final int y = CHART_TOP_MARGIN + (int) (chartHeightExMargins * PRICE_CHART_HEIGHT_PERCENT) + (int) bounds.getHeight() / 2 - 2;
        chartG2D.drawString(strMinPrice, x1, y);
        chartG2D.drawString(strMinPrice, x2 - (int) bounds.getWidth(), y);

        // max price label
        bounds = metrics.getStringBounds(strMaxPrice, null);
        chartG2D.drawString(strMaxPrice, x1, CHART_TOP_MARGIN + (int) bounds.getHeight() / 2 - 2);
        chartG2D.drawString(strMaxPrice, x2 - (int) bounds.getWidth(), CHART_TOP_MARGIN + (int) bounds.getHeight() / 2 - 2);

        // bid price label
        chartG2D.setColor(COLOR_BID);
        bounds = metrics.getStringBounds(strLastTickBid, null);
        chartG2D.drawString(strLastTickBid, x1, y_bidPriceLabel + (int) bounds.getHeight() / 2 - 2);

        // ask price label
        chartG2D.setColor(COLOR_ASK);
        bounds = metrics.getStringBounds(strLastTickAsk, null);
        chartG2D.drawString(strLastTickAsk, x1, y_askPriceLabel + (int) bounds.getHeight() / 2 - 2);

        // current tick label
        selectTickColor(priorTimeAndSale, curTimeAndSale);
        bounds = metrics.getStringBounds(strLastTickPrice, null);
        chartG2D.drawString(strLastTickPrice, x1, y_priceLabel + (int) bounds.getHeight() / 2 - 2);
        //chartG2D.drawString(strLastTickPrice, CHART_LEFT_MARGIN - AXIS_TEXT_MARGIN - (int) bounds.getWidth(), y_priceLabel + (int) bounds.getHeight() / 2 - 2);
    }

    private void drawCurrentTickTime(String strLastTickTime, FontMetrics metrics) {
        chartG2D.setColor(COLOR_AXIS);
        Rectangle2D bounds = metrics.getStringBounds(strLastTickTime, null);
        chartG2D.drawString(strLastTickTime, CHART_LEFT_MARGIN + chartWidthExMargins - metrics.stringWidth(strLastTickTime), CHART_TOP_MARGIN + chartHeightExMargins + (int) bounds.getHeight());
    }
}
