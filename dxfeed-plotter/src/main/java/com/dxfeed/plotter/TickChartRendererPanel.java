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
package com.dxfeed.plotter;

import com.devexperts.util.TimeFormat;
import com.dxfeed.event.market.Quote;

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
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import javax.swing.JPanel;

class TickChartRendererPanel extends JPanel {
    private static final Color COLOR_AXIS = new Color(105, 105, 105);
    private static final Color COLOR_CROSSHAIR = new Color(203, 160, 0x00, 192);
    private static final Color COLOR_CROSSHAIR_TEXT = COLOR_CROSSHAIR.brighter().brighter();
    private static final Color COLOR_CURSOR = COLOR_CROSSHAIR.brighter().brighter();
    private static final Color COLOR_SELECTION = new Color(0xFF, 0xCC, 0x00, 64);

    private static final int TIMELINE_PROTRUSION_MULTIPLIER = 10;
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

    private static final Color[] COLOR_TRADE_TIMELINE = {
        new Color(32, 32, 32),  // ms
        new Color(32, 32, 32),  // s
        new Color(52, 52, 52),  // m
        new Color(72, 72, 72),  // h
        new Color(92, 92, 92)   // d
    };

    // compare times up to seconds
    // str: 20130726-214435.015
    // pos: 0123456789012345678
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

    private static final int TITLE_HEIGHT = 20;
    private static final int CHART_TOP_MARGIN = TITLE_HEIGHT + 3;
    private static final int CHART_BOTTOM_MARGIN = 65;
    private static final int CHART_RIGHT_MARGIN = 50;
    private static final int LEFT_MARGIN = 2;
    private static final int CHART_LEFT_MARGIN = LEFT_MARGIN + 50;
    private static final int AXIS_TEXT_MARGIN = 3;

    private static final int MIN_HEIGHT_EX_MARGINS = 10;
    private static final int MIN_WIDTH_EX_MARGINS = 90;
    private static final double SIZE_CHART_HEIGHT_PERCENT = 0.;
    private static final double PRICE_CHART_HEIGHT_PERCENT = 1 - 0.03 - SIZE_CHART_HEIGHT_PERCENT;

    private static final int MIN_TICK_DISPLAY_WIDTH = 3;

    private static final BasicStroke SIMPLE_STROKE = new BasicStroke(1.0f);
    private static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{1.5f}, 0.0f);
    private static final Font DEFAULT_FONT = new Font("SansSerif", Font.PLAIN, 9);

    private static final Color[] COLORS = {
        new Color(0x0016FF),
        new Color(0x00CBE8),
        new Color(0x00FF49),
        new Color(0xA1E800),
        new Color(0xFFC800),
        new Color(0xE86600),
        new Color(0xFF0000),
        new Color(0xA900E8)
    };

    private final ArrayList<Double> visibleQuotePrices = new ArrayList<>();
    private final ArrayList<Long> visibleQuoteTimes = new ArrayList<>();
    private final ArrayList<Long> drawnTimes = new ArrayList<>();
    private final ArrayList<Integer> visibleQuoteSourceId = new ArrayList<>();
    private final ArrayList<Integer> lastXBySourceId = new ArrayList<>();
    private final ArrayList<Integer> lastYBySourceId = new ArrayList<>();
    private final ArrayList<Integer> counterBySourceId = new ArrayList<>();
    private final ArrayList<String> labelBySourceId = new ArrayList<>();
    private final PriorityQueue<Integer> sources = new PriorityQueue<>(4, new Comparator<Integer>() {
        @Override
        public int compare(Integer o1, Integer o2) {
            return -Long.compare(plots.get(o1).times.get(counterBySourceId.get(o1)),
                plots.get(o2).times.get(counterBySourceId.get(o2)));
        }
    });

    private BufferedImage chartImage;
    private Graphics2D chartG2D;
    private List<PlotData> plots;
    private int lastWidth = 0;
    private int lastHeight = 0;
    private boolean repaintRequired = true;
    private boolean drawCrosshairX = false;
    private boolean drawCrosshairY = false;
    private double minPrice = Double.POSITIVE_INFINITY;
    private double maxPrice = Double.NEGATIVE_INFINITY;
    private int totalTickCount = 0;
    private int visibleTickCount = 0;
    private int chartHeightExMargins = 0;
    private int chartWidthExMargins = 0;
    private int tickDisplayWidth = MIN_TICK_DISPLAY_WIDTH;
    private boolean autoZoom = true;
    private double zoomFactorForPriceChart = 1;
    private int x_crossHair = 0;
    private int y_crossHair = 0;
    private boolean mouseInside = false;
    private int[] x_lastTimeLineProtrusion = new int[5];
    private int maxProtrusionLevel = -1;
    private long firstSelectedTickTime = -1;
    private long secondSelectedTickTime = -1;

    TickChartRendererPanel(List<PlotData> plots) {
        setBackground(Color.BLACK);
        this.plots = plots;
        setCursor();
    }

    private static double price(Quote quote) {
        return (quote.getBidPrice() + quote.getAskPrice()) / 2.;
    }

    private static String price2String(double price) {
        return String.format("%.5f", price);
    }

    private static Color getColor(int idx) {
        return COLORS[idx % COLORS.length];
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (isRepaintRequired()) {
            clear();
            clearTimelines();
            computeChartSize();
            computeAuxiliaryArrays();
            computeChartZoomFactors();
            drawPriceAxisAndLabels();
            drawTitle();

            if (totalTickCount > 0 && chartHeightExMargins > MIN_HEIGHT_EX_MARGINS && chartWidthExMargins > MIN_WIDTH_EX_MARGINS) {
                // x coordinate for current tick (drawing right to left!)
                int x = CHART_LEFT_MARGIN + chartWidthExMargins - tickDisplayWidth;
                long firstVisibleTime = drawnTimes.get(visibleTickCount - 1);
                for (int i = 0; i < visibleQuoteTimes.size(); i++) {
                    long currentTime = visibleQuoteTimes.get(i);
                    if (currentTime < firstVisibleTime) {
                        break;
                    }
                    long priorTime = i + 1 < visibleQuoteTimes.size() ? visibleQuoteTimes.get(i + 1) : currentTime;
                    if (currentTime != priorTime) {
                        if (currentTime == firstSelectedTickTime || (secondSelectedTickTime != -1 &&
                            (currentTime == secondSelectedTickTime || ((currentTime - firstSelectedTickTime) ^ (secondSelectedTickTime - currentTime)) > 0))) {
                            chartG2D.setColor(COLOR_SELECTION);
                            chartG2D.fillRect(x, CHART_TOP_MARGIN, tickDisplayWidth, chartHeightExMargins);
                        }
                        drawTimeLine(x, currentTime, priorTime);
                    }
                    double currentPrice = visibleQuotePrices.get(i);
                    int sourceId = visibleQuoteSourceId.get(i);
                    int px = lastXBySourceId.get(sourceId);
                    int y = getChartYCoordinateForPrice(currentPrice);

                    if (px == 0) { // it's last quote, print labels
                        drawPriceAndSourceLabels(labelBySourceId.get(sourceId), currentPrice, COLORS[sourceId % COLORS.length]);
                        chartImage.setRGB(CHART_LEFT_MARGIN + chartWidthExMargins - 1, y, getColor(sourceId).getRGB());
                    } else {
                        int py = lastYBySourceId.get(sourceId);
                        chartG2D.setColor(getColor(sourceId));
                        chartG2D.drawLine(x, y, px, py);
                    }
                    lastXBySourceId.set(sourceId, x);
                    lastYBySourceId.set(sourceId, y);
                    if (currentTime != priorTime) {
                        x -= tickDisplayWidth;
                    }
                }
                drawTimeLineProtrusionAxises();
            }
            setRepaintRequired(false);
        }

        g.drawImage(chartImage, 0, 0, null);

        if (visibleTickCount > 0 && chartHeightExMargins > MIN_HEIGHT_EX_MARGINS && chartWidthExMargins > MIN_WIDTH_EX_MARGINS && (drawCrosshairX || drawCrosshairY)) {
            drawCrosshair(g);
        }
    }

    boolean isAutoZoom() {
        return autoZoom;
    }

    void setAutoZoom(boolean autoZoom) {
        this.autoZoom = autoZoom;
    }

    void setCrosshair(int x, int y) {
        x_crossHair = x;
        y_crossHair = y;
        setDrawCrosshair(x, y);
        repaint();
    }

    void mouseEntered(int x, int y) {
        mouseInside = true;
        setDrawCrosshair(x, y);
    }

    void mouseExited() {
        mouseInside = false;
        setDrawCrosshair(-1, -1);
        setRepaintRequired(true);
        repaint();
    }

    void zoom(int amount) {
        autoZoom = false;
        int w = tickDisplayWidth;
        w += amount;
        if (w > chartWidthExMargins) w = chartWidthExMargins;
        if (w < MIN_TICK_DISPLAY_WIDTH) w = MIN_TICK_DISPLAY_WIDTH;
        tickDisplayWidth = w;
        setRepaintRequired(true);
        repaint();
    }

    void selectTickOnCrosshair() {
        int tickIdx = getTickIndexByX(x_crossHair);
        if (tickIdx >= 0) {
            long time = drawnTimes.get(tickIdx);
            if (firstSelectedTickTime == -1) {
                firstSelectedTickTime = time;
            } else if (secondSelectedTickTime == -1) {
                secondSelectedTickTime = time;
            } else { // new selection
                firstSelectedTickTime = time;
                secondSelectedTickTime = -1;
            }
        }
        setRepaintRequired(true);
    }

    void disableSelection() {
        firstSelectedTickTime = secondSelectedTickTime = -1;
        setRepaintRequired(true);
    }

    private void computeAuxiliaryArrays() {
        clearAuxiliaryArrays();

        int sourceId = 0;
        for (PlotData plot : plots) {
            lastXBySourceId.add(0);
            lastYBySourceId.add(0);
            counterBySourceId.add(0);
            labelBySourceId.add(plot.name);
            if (plot.data.size() > 0) {
                sources.add(sourceId);
            }
            ++sourceId;
        }

        long previousTime = -1;
        visibleTickCount = chartWidthExMargins / MIN_TICK_DISPLAY_WIDTH; // maxValue for visibleTickCount
        while (drawnTimes.size() < visibleTickCount && !sources.isEmpty()) {
            sourceId = sources.poll();
            PlotData plot = plots.get(sourceId);
            int ptr = counterBySourceId.get(sourceId);
            Quote quote = plot.data.get(ptr);
            long time = plot.times.get(ptr);

            visibleQuotePrices.add(price(quote));
            visibleQuoteTimes.add(time);
            visibleQuoteSourceId.add(sourceId);
            counterBySourceId.set(sourceId, ptr + 1);
            if (previousTime != time) {
                drawnTimes.add(time);
            }
            if (ptr + 1 < plot.data.size()) {
                sources.add(sourceId);
            }
            previousTime = time;
        }
        totalTickCount = drawnTimes.size();
        if (totalTickCount > 0) {
            if (autoZoom) doAutoZoom();
            visibleTickCount = chartWidthExMargins / tickDisplayWidth;
        }
        visibleTickCount = Math.min(visibleTickCount, totalTickCount);
    }

    private void clearAuxiliaryArrays() {
        visibleQuotePrices.clear();
        visibleQuoteTimes.clear();
        drawnTimes.clear();
        visibleQuoteSourceId.clear();
        lastXBySourceId.clear();
        lastYBySourceId.clear();
        counterBySourceId.clear();
        labelBySourceId.clear();
        sources.clear();
    }

    private void doAutoZoom() {
        autoZoom = true;
        tickDisplayWidth = Math.max(chartWidthExMargins / totalTickCount, MIN_TICK_DISPLAY_WIDTH);
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
//        g2d.drawOval(4, 4, 8, 8);
        setCursor(Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(8, 8), "Crosshair cursor"));
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
        setRepaintRequired(true);
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
        }
    }

    private void clearTimelines() {
        Arrays.fill(x_lastTimeLineProtrusion, 0);
        maxProtrusionLevel = -1;
    }

    private void computeChartSize() {
        chartHeightExMargins = getHeight() - CHART_TOP_MARGIN - CHART_BOTTOM_MARGIN;
        chartWidthExMargins = getWidth() - CHART_LEFT_MARGIN - CHART_RIGHT_MARGIN;
    }

    private void computeChartZoomFactors() {
        minPrice = Double.POSITIVE_INFINITY;
        maxPrice = Double.NEGATIVE_INFINITY;

        long firstVisibleTime = visibleTickCount > 0 ? drawnTimes.get(visibleTickCount - 1) : 0;
        // get min and max values for zooming
        for (int i = 0; i < visibleQuoteTimes.size() && visibleQuoteTimes.get(i) >= firstVisibleTime; i++) {
            double price = visibleQuotePrices.get(i);
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }

        zoomFactorForPriceChart = (PRICE_CHART_HEIGHT_PERCENT * chartHeightExMargins) / (maxPrice - minPrice);
    }

    private void drawTitle() {
        final String period;
        if (totalTickCount > 1) {
            period = "showing " + visibleTickCount + " of " + totalTickCount + " last ticks";
        } else if (totalTickCount == 1) {
            period = "single tick";
        } else {
            period = "waiting for data...";
        }

        FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
        Rectangle2D bounds = metrics.getStringBounds(period, null);

        chartG2D.setColor(COLOR_AXIS);
        chartG2D.drawString(period, 1, (int) bounds.getHeight() + 2);
        int offset = 1 + (int) bounds.getWidth() + 13;
        for (int sourceId = 0; sourceId < plots.size(); ++sourceId) {
            if (counterBySourceId.get(sourceId) > 0) {
                bounds = metrics.getStringBounds(labelBySourceId.get(sourceId), null);
                chartG2D.setColor(getColor(sourceId));
                chartG2D.drawString(labelBySourceId.get(sourceId), offset, (int) bounds.getHeight() + 2);
                offset += (int) bounds.getWidth() + 5;
            }
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

    private void drawCrosshair(Graphics g) {
        g.setFont(DEFAULT_FONT);
        final FontMetrics metrics = g.getFontMetrics(g.getFont());
        Rectangle2D bounds;

        long timeUnderCursor = 0;
        if (drawCrosshairX) {
            g.setColor(COLOR_CROSSHAIR);
            g.drawLine(x_crossHair, CHART_TOP_MARGIN, x_crossHair, getHeight() - 14);

            final int tickIndexAtCrosshair = getTickIndexByX(x_crossHair);
            if (tickIndexAtCrosshair >= 0) {
                g.setColor(COLOR_CROSSHAIR_TEXT);
                timeUnderCursor = drawnTimes.get(tickIndexAtCrosshair);
                final String strTimeAtCrosshair = TimeFormat.DEFAULT.withMillis().format(timeUnderCursor);
                bounds = metrics.getStringBounds(strTimeAtCrosshair, null);

                int x_text = x_crossHair - (int) bounds.getWidth() / 2;
                if (x_text < (int) bounds.getWidth() / 2) {
                    x_text = x_crossHair;
                } else if (x_text > CHART_LEFT_MARGIN + chartWidthExMargins - (int) bounds.getWidth() / 2) {
                    x_text = x_crossHair - metrics.stringWidth(strTimeAtCrosshair);
                }

                g.drawString(strTimeAtCrosshair, x_text, getHeight() - 4);
            }
        }

        if (drawCrosshairY) {
            g.setColor(COLOR_CROSSHAIR);
            g.drawLine(CHART_LEFT_MARGIN, y_crossHair, CHART_LEFT_MARGIN + chartWidthExMargins, y_crossHair);
            drawPriceLabel(g, y_crossHair, price2String(getPriceByY(y_crossHair)), COLOR_CROSSHAIR_TEXT, true);
        }

        if (drawCrosshairX && drawCrosshairY && firstSelectedTickTime >= 0) {
            long elapsedTime = timeUnderCursor - firstSelectedTickTime;
            drawCrosshairLabel(g, elapsedTime + "ms.", x_crossHair, y_crossHair, COLOR_SELECTION);
            if (secondSelectedTickTime >= 0) {
                elapsedTime = secondSelectedTickTime - firstSelectedTickTime;
                drawCrosshairLabelBelow(g, elapsedTime + "ms.", x_crossHair, y_crossHair, COLOR_SELECTION);
            }
        }
    }

    private int getTickIndexByX(int x) {
        int tickIndex = -1;
        if (x > CHART_LEFT_MARGIN && x < CHART_LEFT_MARGIN + chartWidthExMargins && visibleTickCount > 0) {
            tickIndex = (CHART_LEFT_MARGIN + chartWidthExMargins - x) / tickDisplayWidth;
            if (tickIndex > visibleTickCount - 1) tickIndex = -1;
        }
        return tickIndex;
    }

    private void drawCrosshairLabel(Graphics g, String label, int x, int y, Color color) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        Rectangle2D bounds = metrics.getStringBounds(label, null);
        drawCrosshairLabel(g, label, x, y - 2, color, bounds);
    }

    private void drawCrosshairLabelBelow(Graphics g, String label, int x, int y, Color color) {
        FontMetrics metrics = g.getFontMetrics(g.getFont());
        Rectangle2D bounds = metrics.getStringBounds(label, null);
        drawCrosshairLabel(g, label, x, y + (int) bounds.getHeight() + 2, color, bounds);
    }

    private void drawCrosshairLabel(Graphics g, String label, int x, int y, Color color, Rectangle2D bounds) {
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

    void setRepaintRequired(boolean repaintRequired) {
        this.repaintRequired = repaintRequired;
        if (repaintRequired) {
            setDrawCrosshair(x_crossHair, y_crossHair);
        } else {
            lastWidth = getWidth();
            lastHeight = getHeight();
        }
    }

    private void drawTimeLine(int x, long previousTime, long currentTime) {
        final String strPriorTime = TimeFormat.DEFAULT.withMillis().format(previousTime);
        final String strCurTime = TimeFormat.DEFAULT.withMillis().format(currentTime);
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

    private void drawTimeLineProtrusionAxises() {
        final FontMetrics metrics = chartG2D.getFontMetrics(chartG2D.getFont());
        for (int protrusionLevel : TIMELINE_PROTRUSIONS) {
            if (protrusionLevel == TIMELINE_PROTRUSION_MS || protrusionLevel > maxProtrusionLevel) continue;
            chartG2D.setColor(COLOR_TRADE_TIMELINE[protrusionLevel]);
            final int y_timeLine = CHART_TOP_MARGIN + chartHeightExMargins + 2 + protrusionLevel * TIMELINE_PROTRUSION_MULTIPLIER;
            chartG2D.drawLine(CHART_LEFT_MARGIN, y_timeLine, CHART_LEFT_MARGIN + chartWidthExMargins, y_timeLine);

            final String strTimeLineAxisLabel = STRING_TIME_AXIS_LABEL[protrusionLevel];
            final Rectangle2D bounds = metrics.getStringBounds(strTimeLineAxisLabel, null);
            chartG2D.drawString(strTimeLineAxisLabel, CHART_LEFT_MARGIN + chartWidthExMargins + 2, y_timeLine + (int) bounds.getHeight() / 2 - 1);
        }
    }

    private void drawPriceAndSourceLabels(String sourceName, double currentPrice, Color color) {
        String strLabel = price2String(currentPrice)/* + " - " + sourceName*/;
        int y_curTickPrice = getChartYCoordinateForPrice(currentPrice);
        drawPriceLabel(y_curTickPrice, strLabel, color, false);
    }

    private void drawPriceAxisAndLabels() {
        String strMinPrice = price2String(minPrice);
        String strMaxPrice = price2String(maxPrice);

        Stroke oldStroke = chartG2D.getStroke();
        chartG2D.setStroke(SIMPLE_STROKE);
        drawPriceAxis();
        chartG2D.setStroke(oldStroke);
        // draw minPrice
        drawPriceLabel(CHART_TOP_MARGIN + (int) (chartHeightExMargins * PRICE_CHART_HEIGHT_PERCENT), strMinPrice, COLOR_AXIS, true);
        // draw maxPrice
        drawPriceLabel(CHART_TOP_MARGIN, strMaxPrice, COLOR_AXIS, true);
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

    private void drawPriceLabel(int y_priceLabel, String strPrice, Color color, boolean bothSides) {
        drawPriceLabel(chartG2D, y_priceLabel, strPrice, color, bothSides);
    }

    private void drawPriceLabel(Graphics g, int y_priceLabel, String strPrice, Color color, boolean bothSides) {
        g.setColor(color);
        int x = CHART_LEFT_MARGIN + chartWidthExMargins + AXIS_TEXT_MARGIN;
        Rectangle2D bounds = g.getFontMetrics().getStringBounds(strPrice, null);
        g.drawString(strPrice, x, y_priceLabel + (int) bounds.getHeight() / 2 - 2);
        if (bothSides) {
            x = CHART_LEFT_MARGIN - AXIS_TEXT_MARGIN - (int) bounds.getWidth();
            g.drawString(strPrice, x, y_priceLabel + (int) bounds.getHeight() / 2 - 2);
        }
    }
}
