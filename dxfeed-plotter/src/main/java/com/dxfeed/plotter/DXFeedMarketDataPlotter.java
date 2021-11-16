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

import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.event.market.Quote;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;

public class DXFeedMarketDataPlotter implements Runnable {
    private static final Logging log = Logging.getLogging(DXFeedMarketDataPlotter.class);

    /* Properties */
    private static final String PROPERTIES_FILE = "dxplotter.cfg";
    private static final String PROPERTIES_PATH = File.separatorChar + ".dxplotter" + File.separatorChar + PROPERTIES_FILE;

    private static final String NAME_PROPERTY = "name";
    private static final String ADDRESSES_PROPERTY = "addresses";
    private static final String SYMBOLS_PROPERTY = "symbols";
    private static final String MAX_QUOTES_PROPERTY = "maxQuotesCapacity";
    private static final String UI_REFRESH_PERIOD_PROPERTY = "uiRefreshPeriod";
    private static final String PROCRASTINATION_PERIOD_PROPERTY = "procrastinationPeriod";

    private static final int MIN_UI_REFRESH_PERIOD = 10; // have some wiggle room, refresh no less than 10 ms
    private static final int MIN_PROCRASTINATION_PERIOD = 150;

    static final Color WORKING_COLOR = Color.green;
    static final Color PROCRASTINATING_COLOR = Color.red;

    private final String configFile;

    private final String name;
    private final String addresses;

    private final int maxQuotesCapacity;
    private final int uiRefreshPeriod;
    private final int procrastinationPeriod;

    private final ScheduledExecutorService procrastinationChecker = Executors.newSingleThreadScheduledExecutor();

    private final SimpleDateFormat timeFormatSelectedTZ = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private final Stats eventStats = new Stats("Total/s");
    private final Stats quoteStats = new Stats("Q/s");

    /* Swing Components */
    private JPanel form;
    private JLabel mpsLabel;
    private JLabel qpsLabel;
    private JPanel tickChartPanel;
    private JTabbedPane tabbedPane;
    private JTextField subscribedSymbolsEdit;
    private JButton playButton;
    private JButton pauseButton;
    private JToolBar addressToolbar;
    private JLabel currentTimeLabel;
    private JPanel endpointLabelsPanel;
    private TickChartRendererPanel tickChartRendererPanel;

    private JFrame mainFrame;

    private String[] symbols;
    private List<Feed> feeds;
    private List<PlotData> plots;
    private Map<String, Integer> plotIdxByName;

    private DXFeedMarketDataPlotter(String configFile) {
        this.configFile = configFile;

        $$$setupUI$$$();
        Properties properties = loadConfiguration(configFile);
        name = properties.getProperty(NAME_PROPERTY, "");
        addresses = properties.getProperty(ADDRESSES_PROPERTY, "demo demo.dxfeed.com:7300");
        String symbolsCsv = properties.getProperty(SYMBOLS_PROPERTY, "");
        symbols = sortAndUnique(symbolsCsv);
        maxQuotesCapacity = getIntProperty(properties.getProperty(MAX_QUOTES_PROPERTY), 1000);
        uiRefreshPeriod = Math.max(MIN_UI_REFRESH_PERIOD,
            getIntProperty(properties.getProperty(UI_REFRESH_PERIOD_PROPERTY), 150));
        procrastinationPeriod = Math.max(MIN_PROCRASTINATION_PERIOD,
            getIntProperty(properties.getProperty(PROCRASTINATION_PERIOD_PROPERTY), 3000));
    }

    private static int getIntProperty(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("Failed to parse integer property from string \"" + value + "\"");
        }
        return defaultValue;
    }

    private static String stringsToCsv(String[] array) {
        if (array.length == 0) return "";
        StringBuilder csv = new StringBuilder(array[0]);
        for (int i = 1; i < array.length; ++i) {
            csv.append(',').append(array[i]);
        }
        return csv.toString();
    }

    private static String[] sortAndUnique(String csv) {
        String[] array = csv.split(",");
        Arrays.sort(array);
        int ptr = 1;
        for (int i = 1; i < array.length; ++i) {
            if (!array[i].equals(array[i - 1])) {
                array[ptr++] = array[i];
            }
        }
        if (ptr == array.length) return array;
        return Arrays.copyOf(array, ptr);
    }

    private static Properties loadConfiguration(String configFile) {
        Properties properties = new Properties();
        File file = new File(configFile);
        if (!file.exists()) {
            log.info(LogUtil.hideCredentials(file.getAbsoluteFile()) + " file not found; will use default configuration");
            file = new File(PROPERTIES_FILE);
        }

        if (file.exists()) {
            log.info("Loading configuration from " + LogUtil.hideCredentials(file.getAbsoluteFile()));
            try (FileInputStream in = new FileInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                log.error("Failed to load configuration from " + LogUtil.hideCredentials(file.getAbsoluteFile()) + "; will use default configuration");
            }
        }
        return properties;
    }

    private void saveConfiguration(String configFile) {
        File file = new File(configFile);
        log.info("Saving configuration into " + LogUtil.hideCredentials(file.getAbsoluteFile()));
        try {
            Properties properties = new Properties();
            properties.setProperty(NAME_PROPERTY, name);
            properties.setProperty(ADDRESSES_PROPERTY, addresses);
            properties.setProperty(SYMBOLS_PROPERTY, stringsToCsv(symbols));
            properties.setProperty(MAX_QUOTES_PROPERTY, Integer.toString(maxQuotesCapacity));
            properties.setProperty(UI_REFRESH_PERIOD_PROPERTY, Integer.toString(uiRefreshPeriod));
            if (file.getParentFile() != null && !file.getParentFile().mkdirs()) {
                log.warn("Failed to create path to configuration file");
            }
            properties.store(new FileOutputStream(file), "dxFeed Market Data Plotter Configuration");
        } catch (IOException e) {
            log.error("Failed to save configuration into " + LogUtil.hideCredentials(file.getAbsoluteFile()), e);
        }
    }

    @SuppressWarnings("serial")
    private final Action pause = new AbstractAction("Freeze") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Feed feed : feeds) {
                feed.disconnect();
            }
            pauseButton.setEnabled(false);
            playButton.setEnabled(true);
        }
    };

    @SuppressWarnings("serial")
    private final Action play = new AbstractAction("Unfreeze") {
        @Override
        public void actionPerformed(ActionEvent e) {
            for (Feed feed : feeds) {
                feed.reconnect();
            }
            pauseButton.setEnabled(true);
            playButton.setEnabled(false);
        }
    };

    private static void setDarkTheme() {
        try {
            UIManager.setLookAndFeel(new FlatOneDarkIJTheme());

            // Theme tweaks
            FontUIResource text = (FontUIResource) UIManager.get("defaultFont");
            Font boldFont = text.deriveFont(Font.BOLD, text.getSize());
            UIManager.put("defaultFont", new FontUIResource(boldFont));
            UIManager.put("Component.arrowType", "chevron");
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("ScrollBar.width", 16);
            UIManager.put("ScrollBar.showButtons", true);
            UIManager.put("Table.intercellSpacing", new Dimension(1, 1));
        } catch (Exception e) {
            log.error("Cannot set LookAndFeel theme", e);
        }
    }

    // ==================== Initialization ====================

    public static void main(String[] args) {
        setDarkTheme();
        String defaultConfigFile = System.getProperty("user.home") + PROPERTIES_PATH;
        String configFile = (args.length > 0) ? args[0] : defaultConfigFile;
        SwingUtilities.invokeLater(new DXFeedMarketDataPlotter(configFile));
    }

    @Override
    public void run() {
        createFeed();
        initTickChartRendererPanel();
        initStatsUpdater();
        createFrame();
        startProcrastinationChecker();
    }

    private void createFeed() {
        String[] addressList = addresses.split(",");
        plots = new ArrayList<>();
        plotIdxByName = new HashMap<>();
        feeds = new ArrayList<>(addressList.length);
        for (String nameAddress : addressList) {
            int spaceIdx = nameAddress.indexOf(' ');
            final String name;
            String address;
            if (spaceIdx == -1) {
                name = LogUtil.hideCredentials(nameAddress);
                address = nameAddress;
            } else {
                name = nameAddress.substring(0, spaceIdx);
                address = nameAddress.substring(spaceIdx + 1);
            }
            final String wrappedName = "-" + name;
            for (String symbol : symbols) {
                String plotName = symbol + wrappedName;
                if (plotIdxByName.containsKey(plotName)) {
                    throw new IllegalStateException("Plot name of symbol '" + symbol + "' and feedName '" + name + "' is already used");
                }
                plotIdxByName.put(plotName, plots.size());
                plots.add(new PlotData(plotName, new SimpleMovingQueue<>(maxQuotesCapacity), new SimpleMovingQueue<>(maxQuotesCapacity)));
            }

            JLabel label = new JLabel(name);
            label.setForeground(WORKING_COLOR);
            endpointLabelsPanel.add(label);
            JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
            separator.setPreferredSize(new Dimension(10, 14));
            endpointLabelsPanel.add(separator);

            Feed feed = new Feed(name, new LabelFlashSupport(label), DXEndpoint.create().executor(new SwingExecutor(uiRefreshPeriod)));
            feed.addListener(events -> {
                long receiveTime = System.currentTimeMillis();
                eventStats.increment(events.size());
                quoteStats.increment(events.size());
                for (Quote quote : events) {
                    PlotData plot = plots.get(plotIdxByName.get(quote.getEventSymbol() + wrappedName));
                    plot.data.add(quote);
                    plot.times.add(receiveTime);
                }
                tickChartRendererPanel.setRepaintRequired(true);
                tickChartRendererPanel.repaint();
            });
            feed.addSymbols(Arrays.asList(symbols));
            feed.connect(address);
            feeds.add(feed);
            log.info("Connected to '" + name + "' (" + LogUtil.hideCredentials(address) + ")");
        }
    }

    private void initTickChartRendererPanel() {
        tickChartRendererPanel = new TickChartRendererPanel(plots);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = gbc.weighty = 1.0;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.BOTH;
        tickChartPanel.setLayout(new GridBagLayout());
        tickChartPanel.add(tickChartRendererPanel, gbc);

        tickChartRendererPanel.addMouseWheelListener(e -> tickChartRendererPanel.zoom(-e.getWheelRotation() * 2));

        tickChartRendererPanel.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                tickChartRendererPanel.setCrosshair(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                tickChartRendererPanel.setCrosshair(e.getX(), e.getY());
            }
        });

        tickChartRendererPanel.addMouseListener(new MouseListener() {
            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                tickChartRendererPanel.selectTickOnCrosshair();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                tickChartRendererPanel.mouseExited();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                tickChartRendererPanel.mouseEntered(e.getX(), e.getY());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
            }
        });

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(e -> {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    tickChartRendererPanel.disableSelection();
                }
                return false;
            });
    }

    private void setCurrentTimeLabel(Date time) {
        currentTimeLabel.setText(timeFormatSelectedTZ.format(time));
    }

    private void initStatsUpdater() {
        Timer timer = new Timer(1000, new ActionListener() {
            private long lastTime = System.currentTimeMillis();

            @Override
            public void actionPerformed(ActionEvent e) {
                long curTime = System.currentTimeMillis();
                long delta = curTime - lastTime;

                mpsLabel.setText(eventStats.update(delta));
                qpsLabel.setText(quoteStats.update(delta));

                setCurrentTimeLabel(new Date(curTime));

                lastTime = curTime;
            }
        });
        timer.start();
    }

    private void createFrame() {
        mainFrame = new JFrame("dxFeed Market Data Plotter" + (name.length() == 0 ? "" : ": " + name));
        mainFrame.add(form);
        mainFrame.pack();
        mainFrame.setLocationByPlatform(true);

        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveConfiguration(configFile);
                System.exit(0);
            }
        });

        pauseButton.addActionListener(pause);
        playButton.addActionListener(play);

        subscribedSymbolsEdit.setText(stringsToCsv(symbols));
        subscribedSymbolsEdit.addActionListener(e -> {
            updateSubscription(subscribedSymbolsEdit.getText());
            tickChartRendererPanel.setRepaintRequired(true);
        });

        setComponentSizes();

        mainFrame.setMinimumSize(new Dimension(800, 600));
        mainFrame.setExtendedState(mainFrame.getExtendedState() | Frame.MAXIMIZED_BOTH);

        mainFrame.setVisible(true);
    }

    private void startProcrastinationChecker() {
        procrastinationChecker.scheduleWithFixedDelay(() -> {
            long threshold = System.currentTimeMillis() - procrastinationPeriod;
            for (Feed feed : feeds) {
                if (feed.lastReceivingTime() < threshold) {
                    if (!feed.flasher.isFlashing()) {
                        feed.flasher.updateColor(PROCRASTINATING_COLOR);
                        feed.flasher.startFlashing();
                    }
                }
            }
        }, procrastinationPeriod, procrastinationPeriod, TimeUnit.MILLISECONDS);
    }

    private void updateSubscription(String newSymbolsCsv) {
        updateSubscription(sortAndUnique(newSymbolsCsv));
    }

    private void updateSubscription(String[] newSymbols) {
        Set<String> removed = new HashSet<>(symbols.length);
        List<String> added = new ArrayList<>(newSymbols.length);

        int a = 0;
        int b = 0;
        while (a < symbols.length && b < newSymbols.length) {
            int cmp = symbols[a].compareTo(newSymbols[b]);
            if (cmp < 0) {
                removed.add(symbols[a++]);
            } else if (cmp > 0) {
                added.add(newSymbols[b++]);
            } else {
                ++a;
                ++b;
            }
        }
        for (; a < symbols.length; ++a) {
            removed.add(symbols[a]);
        }
        for (; b < newSymbols.length; ++b) {
            added.add(newSymbols[b]);
        }

        List<List<Quote>> freeQQueue = new ArrayList<>();
        List<List<Long>> freeTQueue = new ArrayList<>();
        int ptr = 0;
        for (int i = 0; i < plots.size(); ++i) {
            String plotName = plots.get(i).name;
            // plotName = symbol + "-" + feed.name
            String symbol = plotName.substring(0, plotName.indexOf('-'));
            if (removed.contains(symbol)) {
                PlotData removedData = plots.get(i);
                freeQQueue.add(removedData.data);
                freeTQueue.add(removedData.times);
            } else {
                plots.set(ptr++, plots.get(i));
                plotIdxByName.put(plots.get(i).name, ptr - 1);
            }
        }
        for (int i = plots.size() - 1; i >= ptr; --i) {
            plots.remove(i);
        }
        for (Feed feed : feeds) {
            feed.removeSymbols(new ArrayList<>(removed));
            for (String symbol : added) {
                List<Quote> quoteList;
                List<Long> timeList;
                if (freeQQueue.isEmpty()) {
                    quoteList = new SimpleMovingQueue<>(maxQuotesCapacity);
                    timeList = new SimpleMovingQueue<>(maxQuotesCapacity);
                } else {
                    quoteList = freeQQueue.remove(freeQQueue.size() - 1);
                    timeList = freeTQueue.remove(freeTQueue.size() - 1);
                    quoteList.clear();
                    timeList.clear();
                }
                final String plotName = symbol + '-' + feed.name;
                plotIdxByName.put(plotName, plots.size());
                plots.add(new PlotData(plotName, quoteList, timeList));
            }
            feed.addSymbols(added);
        }

        tickChartRendererPanel.setAutoZoom(true);

        symbols = newSymbols;
    }

    private void setComponentSizes() {
        mpsLabel.setPreferredSize(new Dimension(70, 14));
        qpsLabel.setPreferredSize(new Dimension(70, 14));

        currentTimeLabel.setPreferredSize(new Dimension(100, 14));
    }

    private void createUIComponents() {
        // TODO: place custom component creation code here
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$()
    {
        tabbedPane = new JTabbedPane();
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        tabbedPane.addTab("Default view", panel1);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(panel2, gbc);
        form = new JPanel();
        form.setLayout(new BorderLayout(0, 0));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel2.add(form, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        form.add(panel3, BorderLayout.SOUTH);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridBagLayout());
        panel4.setToolTipText("Feed address");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 6.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel3.add(panel4, gbc);
        final JToolBar.Separator toolBar$Separator1 = new JToolBar.Separator();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel4.add(toolBar$Separator1, gbc);
        currentTimeLabel = new JLabel();
        Font currentTimeLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, currentTimeLabel.getFont());
        if (currentTimeLabelFont != null) currentTimeLabel.setFont(currentTimeLabelFont);
        currentTimeLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel4.add(currentTimeLabel, gbc);
        final JSeparator separator1 = new JSeparator();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel4.add(separator1, gbc);
        final JSeparator separator2 = new JSeparator();
        separator2.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator2, gbc);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridBagLayout());
        panel5.setToolTipText("Total events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel5, gbc);
        mpsLabel = new JLabel();
        Font mpsLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, mpsLabel.getFont());
        if (mpsLabelFont != null) mpsLabel.setFont(mpsLabelFont);
        mpsLabel.setHorizontalAlignment(0);
        mpsLabel.setHorizontalTextPosition(0);
        mpsLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel5.add(mpsLabel, gbc);
        final JSeparator separator3 = new JSeparator();
        separator3.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator3, gbc);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridBagLayout());
        panel6.setToolTipText("Quote events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel6, gbc);
        qpsLabel = new JLabel();
        Font qpsLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, qpsLabel.getFont());
        if (qpsLabelFont != null) qpsLabel.setFont(qpsLabelFont);
        qpsLabel.setHorizontalAlignment(0);
        qpsLabel.setHorizontalTextPosition(0);
        qpsLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel6.add(qpsLabel, gbc);
        endpointLabelsPanel = new JPanel();
        endpointLabelsPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(endpointLabelsPanel, gbc);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridBagLayout());
        form.add(panel7, BorderLayout.CENTER);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel7.add(panel8, gbc);
        tickChartPanel = new JPanel();
        tickChartPanel.setLayout(new GridBagLayout());
        tickChartPanel.setPreferredSize(new Dimension(800, 200));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel8.add(tickChartPanel, gbc);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel7.add(panel9, gbc);
        addressToolbar = new JToolBar();
        addressToolbar.setBorderPainted(true);
        addressToolbar.setFloatable(false);
        Font addressToolbarFont = this.$$$getFont$$$(null, Font.PLAIN, 9, addressToolbar.getFont());
        if (addressToolbarFont != null) addressToolbar.setFont(addressToolbarFont);
        addressToolbar.setRollover(false);
        addressToolbar.putClientProperty("JToolBar.isRollover", Boolean.FALSE);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel9.add(addressToolbar, gbc);
        playButton = new JButton();
        playButton.setEnabled(false);
        playButton.setIcon(new ImageIcon(getClass().getResource("/com/dxfeed/plotter/icons/play-icon.png")));
        playButton.setInheritsPopupMenu(true);
        playButton.setText("");
        playButton.setToolTipText("Connect/Replay");
        playButton.putClientProperty("hideActionText", Boolean.FALSE);
        addressToolbar.add(playButton);
        pauseButton = new JButton();
        pauseButton.setIcon(new ImageIcon(getClass().getResource("/com/dxfeed/plotter/icons/pause-icon.png")));
        pauseButton.setText("");
        pauseButton.setToolTipText("Pause");
        addressToolbar.add(pauseButton);
        final JToolBar.Separator toolBar$Separator2 = new JToolBar.Separator();
        addressToolbar.add(toolBar$Separator2);
        final JLabel label1 = new JLabel();
        Font label1Font = this.$$$getFont$$$(null, -1, -1, label1.getFont());
        if (label1Font != null) label1.setFont(label1Font);
        label1.setText("Subscribe to: ");
        addressToolbar.add(label1);
        subscribedSymbolsEdit = new JTextField();
        subscribedSymbolsEdit.setColumns(0);
        subscribedSymbolsEdit.setEditable(true);
        Font subscribedSymbolsEditFont = this.$$$getFont$$$(null, -1, -1, subscribedSymbolsEdit.getFont());
        if (subscribedSymbolsEditFont != null) subscribedSymbolsEdit.setFont(subscribedSymbolsEditFont);
        subscribedSymbolsEdit.setForeground(new Color(-1));
        subscribedSymbolsEdit.setToolTipText("Connection address");
        addressToolbar.add(subscribedSymbolsEdit);
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return tabbedPane;
    }

}
