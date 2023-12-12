/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.viewer;

import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.LogUtil;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.ipf.option.OptionChain;
import com.dxfeed.ipf.option.OptionChainsBuilder;
import com.dxfeed.ipf.option.OptionSeries;
import com.dxfeed.model.market.OrderBookModel;
import com.dxfeed.model.market.OrderBookModelFilter;
import com.dxfeed.ondemand.OnDemandService;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.TableColumnModel;
import javax.swing.text.StyleContext;

@SuppressWarnings("unchecked")
public class DXFeedMarketDataViewer implements Runnable {

    private static final String PROPERTIES_FILE = "dxviewer.cfg";
    private static final String PROPERTIES_PATH =
        File.separatorChar + ".dxviewer" + File.separatorChar + PROPERTIES_FILE;

    private static final String NAME_PROPERTY = "name";
    private static final String ADDRESS_PROPERTY = "address";
    private static final String SYMBOLS_PROPERTY = "symbols";
    private static final String MAX_TIME_AND_SALES_PROPERTY = "maxTimeAndSalesCapacity";
    private static final String UI_REFRESH_PERIOD_PROPERTY = "uiRefreshPeriod";
    private static final String UI_SCHEME_PROPERTY = "uiScheme";
    private static final String IPF_ADDRESS = "ipfAddress";

    private static final Logging log = Logging.getLogging(DXFeedMarketDataViewer.class);

    private JPanel form;
    private JLabel mpsLabel;
    private JTable quoteBoardTable;
    private JTable timeAndSalesTable;
    private JTable bidTable;
    private JTable askTable;
    private JLabel qpsLabel;
    private JLabel tpsLabel;
    private JLabel spsLabel;
    private JLabel opsLabel;
    private JLabel tspsLabel;
    private JScrollPane timeAndSalesScrollPane;
    private JPanel tickChartPanel;
    private JTabbedPane tabbedPane;
    private JLabel bookModeLabel;
    private JLabel lotSizeLabel;
    private JTextField connectionAddressEdit;
    private JButton playButton;
    private JButton pauseButton;
    private JSlider onDemandSpeedSlider;
    private JComboBox<String> modeComboBox;
    private JToolBar addressToolbar;
    private JToolBar onDemandToolbar;
    private JSpinner onDemandTimeSpinner;
    private JLabel onDemandSpeedLabel;
    private JLabel currentTimeLabel;
    private JComboBox<String> tzComboBox;
    private JButton connectAsButton;
    private JLabel onDemandConnectionStatusLabel;
    private JLabel onDemandUsernameLabel;
    private JLabel ipfWebConnectionStatusLabel;
    private JLabel ipfWebUsernameLabel;
    private TickChartRendererPanel tickChartRendererPanel;

    private LabelFlashSupport labelFlasher;

    private SuggestMenu suggestMenu;

    private final String configFile;

    private final String name;
    private String address;

    private IpfMode ipfMode;
    private final String ipfAddress;
    private final InstrumentProfileReader ipfReader;
    private List<InstrumentProfile> instruments;
    private Map<String, OptionChain<InstrumentProfile>> chains;
    private final String symbols;
    private final int maxTimeAndSalesCapacity;
    private final int uiRefreshPeriod;

    private final SimpleDateFormat timeFormatSelectedTZ = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private Credentials onDemandCredentials;
    private Credentials ipfWebServiceCredentials;

    private DXEndpoint endpoint;
    private DXFeed feed;
    private OnDemandService onDemand;

    private boolean isInOnDemandMode = false;

    private final Stats eventStats = new Stats("Total/s");
    private final Stats quoteStats = new Stats("Q/s");
    private final Stats tradeStats = new Stats("T/s");
    private final Stats summaryStats = new Stats("OHLC/s");
    private final Stats orderStats = new Stats("Ord/s");
    private final Stats tnsStats = new Stats("TnS/s");

    private final List<DXFeedSubscription<?>> quoteBoardSubscriptions = new ArrayList<>();
    private DXFeedSubscription<Order> orderSubscription;
    private DXFeedTimeSeriesSubscription<TimeAndSale> timeAndSalesSubscription;

    private BarGraphCellRenderer bidSizeGraphRenderer;
    private BarGraphCellRenderer askSizeGraphRenderer;

    private final int scheme = ViewerCellRenderer.DEFAULT_SCHEME;

    private final QuoteBoardTableModel quoteBoardTableModel =
        new QuoteBoardTableModel(new SubscriptionChangeListener() {
            @Override
            public void addSymbol(String symbol) {
                Set<String> symbols = Collections.singleton(symbol);
                for (DXFeedSubscription<?> subscription : quoteBoardSubscriptions) {
                    subscription.addSymbols(symbols);
                }
            }

            @Override
            public void removeSymbol(String symbol) {
                Set<String> symbols = Collections.singleton(symbol);
                for (DXFeedSubscription<?> subscription : quoteBoardSubscriptions) {
                    subscription.removeSymbols(symbols);
                }
            }
        });
    private final OrderBookModel orderBookModel = new OrderBookModel();
    private final OrderTableModel bidTableModel = new OrderTableModel(orderBookModel.getBuyOrders());
    private final OrderTableModel askTableModel = new OrderTableModel(orderBookModel.getSellOrders());
    private final TimeAndSalesTableModel timeAndSalesTableModel;

    private String selectedSymbol;
    private double onDemandSpeed;
    private TimezoneComboBoxSupport tzComboBoxSupport;
    private JFrame mainFrame;

    private DXFeedMarketDataViewer(String configFile) {
        this.configFile = configFile;

        $$$setupUI$$$();
        Properties properties = loadConfiguration(configFile);
        name = properties.getProperty(NAME_PROPERTY, "");
        address = properties.getProperty(ADDRESS_PROPERTY, "demo.dxfeed.com:7300");
        symbols = properties.getProperty(SYMBOLS_PROPERTY, "");
        ipfAddress = properties.getProperty(IPF_ADDRESS, "securities.ipf.zip");
        maxTimeAndSalesCapacity = getIntProperty(properties.getProperty(MAX_TIME_AND_SALES_PROPERTY), 1000);
        int refreshPeriod = getIntProperty(properties.getProperty(UI_REFRESH_PERIOD_PROPERTY), 150);
        uiRefreshPeriod = refreshPeriod < 10 ? 10 : refreshPeriod; // have some wiggle room, refresh no less than 10 ms

        ipfReader = new InstrumentProfileReader();
        loadIpfAndCredentials();

        timeAndSalesTableModel = new TimeAndSalesTableModel(maxTimeAndSalesCapacity);
    }

    private int getIntProperty(String value, int defaultValue) {
        try {
            if (value != null)
                return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error("Failed to parse integer property from string \"" + value + "\"");
        }
        return defaultValue;
    }

    private Properties loadConfiguration(String configFile) {
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
            properties.setProperty(ADDRESS_PROPERTY, address);
            properties.setProperty(IPF_ADDRESS, ipfAddress);
            properties.setProperty(SYMBOLS_PROPERTY, quoteBoardTableModel.getSymbols());
            properties.setProperty(MAX_TIME_AND_SALES_PROPERTY, Integer.toString(maxTimeAndSalesCapacity));
            properties.setProperty(UI_REFRESH_PERIOD_PROPERTY, Integer.toString(uiRefreshPeriod));
            properties.setProperty(UI_SCHEME_PROPERTY, Integer.toString(scheme));
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            properties.store(new FileOutputStream(file), "dxFeed Market Data Viewer Configuration");
        } catch (IOException e) {
            log.error("Failed to save configuration into " + LogUtil.hideCredentials(file.getAbsoluteFile()), e);
        }
    }

    private void loadIpfAndCredentials() {
        ipfWebConnectionStatusLabel.setText("NOT CONNECTED");
        ipfWebConnectionStatusLabel.setForeground(Color.RED.darker());
        if (ipfAddress.startsWith("http")) {
            instruments = null;
            chains = null;
            if (!showIpfWebServicePasswordDialog()) {
                ipfMode = IpfMode.NO_IPF;
                log.info("No credentials for " + LogUtil.hideCredentials(ipfAddress) + ", work without instrument profiles");
            } else {
                ipfMode = IpfMode.WEB_SERVICE;
                log.info("Will load instrument profiles from " + LogUtil.hideCredentials(ipfAddress) + " on demand.");
                ipfWebConnectionStatusLabel.setText("CONNECTED");
                ipfWebConnectionStatusLabel.setForeground(Color.GREEN.darker());
            }
        } else {
            try {
                log.debug("Reading instrument profiles from " + LogUtil.hideCredentials(ipfAddress) + "...");
                instruments = ipfReader.readFromFile(ipfAddress);
                log.debug("done");
                log.debug("Building option chains from " + LogUtil.hideCredentials(ipfAddress) + "...");
                chains = OptionChainsBuilder.build(instruments).getChains();
                log.debug("done");
                ipfMode = IpfMode.LOCAL_FILE;
            } catch (IOException e) {
                ipfMode = IpfMode.NO_IPF;
                log.debug("fail to load ipf from " + LogUtil.hideCredentials(ipfAddress));
                log.info("Work without instrument profiles");
            }
        }
    }

    private boolean showIpfWebServicePasswordDialog() {
        boolean result = false;
        Credentials c;
        if (ipfWebServiceCredentials != null) {
            c = ipfWebServiceCredentials;
        } else {
            c = new Credentials("demo", "demo");
        }

        final String title = "Accessing IPF service [" + LogUtil.hideCredentials(ipfAddress) + "]";
        boolean firstIteration = true;
        while (true) {
            c = PasswordDialog.showPasswordDialog(
                title, c, mainFrame, firstIteration ? null : "Wrong username/password/ipfAddress");
            firstIteration = false;
            if (c != null) {
                ipfWebServiceCredentials = new Credentials(c.getUsername(), c.getPassword());
                try {
                    URLConnection connection = URLInputStream
                        .openConnection(URLInputStream.resolveURL(InstrumentProfileReader.resolveSourceURL(ipfAddress)),
                            ipfWebServiceCredentials.getUsername(), ipfWebServiceCredentials.getPassword());
                    URLInputStream.checkConnectionResponseCode(connection);
                } catch (IOException e) {
                    log.error("Cannot connect to " + LogUtil.hideCredentials(ipfAddress) + " (are username/password correct?)", e);
                    continue;
                }
                ipfWebUsernameLabel.setText("ipf: " + ipfWebServiceCredentials.getUsername().toLowerCase());
                result = true;
            }
            // disable option menu
            return result;
        }
    }


    private void addOptionsToWatchlist(final int strikesAround) {
        final int rowIndex = quoteBoardTable.getSelectionModel().getLeadSelectionIndex();
        if (ipfMode == IpfMode.NO_IPF || rowIndex == -1 || rowIndex >= quoteBoardTable.getRowCount() - 1) {
            return;
        }

        SwingWorker<OptionChain<InstrumentProfile>, Void> worker =
            new SwingWorker<OptionChain<InstrumentProfile>, Void>() {
                @Override
                protected OptionChain<InstrumentProfile> doInBackground() throws Exception {
                    return getOptionChain(selectedSymbol);
                }

                @Override
                protected void done() {
                    try {
                        int rowIndex_ = rowIndex;
                        OptionChain<InstrumentProfile> chain = get();
                        double price = quoteBoardTableModel.getRowAt(rowIndex_).lastPrice;
                        ArrayList<String> symbolList =
                            new ArrayList<>(Arrays.asList(quoteBoardTableModel.getSymbols().split(",")));

                        for (OptionSeries<InstrumentProfile> series : chain.getSeries()) {
                            List<Double> strikes;
                            if (strikesAround == Integer.MAX_VALUE) {
                                strikes = series.getStrikes();
                            } else {
                                strikes = series.getNStrikesAround(strikesAround, price);
                            }

                            for (Double strike : strikes) {
                                InstrumentProfile call = series.getCalls().get(strike);
                                InstrumentProfile put = series.getPuts().get(strike);
                                if (call != null) {
                                    symbolList.add(++rowIndex_, call.getSymbol());
                                }
                                if (put != null) {
                                    symbolList.add(++rowIndex_, put.getSymbol());
                                }
                            }
                        }
                        log.info(symbolList.size() + " symbols added from chains");

                        quoteBoardTableModel
                            .setSymbols(symbolList.toString().replace("[", "").replace("]", "").replace(", ", ","));
                    } catch (InterruptedException ignore) {
                    } catch (ExecutionException e) {
                        log.error("Cannot add options to watchlist", e);
                    }
                }
            };
        worker.execute();
    }

    private OptionChain<InstrumentProfile> getOptionChain(final String selectedSymbol) throws IOException {
        if (ipfMode == IpfMode.LOCAL_FILE) {
            return chains.get(selectedSymbol);
        }
        final List<InstrumentProfile> instrumentProfiles;
        if (ipfMode == IpfMode.WEB_SERVICE) {
            final String url = ipfAddress + "?" + "types=OPTION" + "&" + "underlyings=" + selectedSymbol;
            instrumentProfiles = ipfReader
                .readFromFile(url, ipfWebServiceCredentials.getUsername(), ipfWebServiceCredentials.getPassword());
        } else {
            throw new IllegalStateException("Cannot extract option chain in " + ipfMode + " mode");
        }
        return OptionChainsBuilder.build(instrumentProfiles).getChains().get(selectedSymbol);
    }

    // -------------------- Instrument selection --------------------

    private void updateOrderBook() {
        int index =
            Math.min(quoteBoardTable.getSelectionModel().getLeadSelectionIndex(), quoteBoardTable.getRowCount() - 1);
        if (index >= 0) {
            selectSymbol(quoteBoardTableModel.getRowAt(quoteBoardTable.convertRowIndexToModel(index)).symbol);
        }
    }

    private void forceSelectSymbol(String symbol) {
        selectedSymbol = symbol;

        orderSubscription.clear();
        orderBookModel.clear();
        timeAndSalesSubscription.clear();
        timeAndSalesTableModel.eventsReceived(Collections.singletonList(TimeAndSalesTableModel.CLEAR));

        if (symbol != null && symbol.length() > 0) {
            orderBookModel.setSymbol(symbol);

            Collection<String> symbols = Collections.singletonList(symbol);
            orderSubscription.setSymbols(symbols);
            timeAndSalesSubscription.setSymbols(symbols);

            tickChartRendererPanel.setSymbol(selectedSymbol);
            tickChartRendererPanel.setAutoZoom(true);
            tickChartRendererPanel.repaint();
        }
    }

    private void selectSymbol(String symbol) {
        if (selectedSymbol != null && selectedSymbol.equals(symbol)) {
            return;
        }

        forceSelectSymbol(symbol);
    }

    private double adjustMaxSizeForZoomFactor(double size) {
        double adjustedMaxSize;
        if (size <= 5) {
            adjustedMaxSize = 10;
        } else if (size <= 10) {
            adjustedMaxSize = 18;
        } else if (size <= 30) {
            adjustedMaxSize = 48;
        } else if (size <= 55) {
            adjustedMaxSize = 75;
        } else if (size <= 95) {
            adjustedMaxSize = 130;
        } else if (size <= 1000) {
            adjustedMaxSize = size * 1.2;
        } else if (size <= 10000) {
            adjustedMaxSize = size * 1.01;
        } else if (size <= 100000) {
            adjustedMaxSize = size * 1.001;
        } else {
            adjustedMaxSize = size * 1.0002;
        }
        return adjustedMaxSize;
    }

    private void calibrateGraphRendererZoomFactors() {
        double maxSize = Math.max(bidTableModel.getMaxSize(), askTableModel.getMaxSize());
        double zoomFactor = adjustMaxSizeForZoomFactor(maxSize);

        if (bidSizeGraphRenderer.setMaxValue(zoomFactor)) {
            bidTable.repaint();
        }
        if (askSizeGraphRenderer.setMaxValue(zoomFactor)) {
            askTable.repaint();
        }
    }

    // -------------------- Quote Board Actions --------------------

    private final Action insertAction = new AbstractAction("Insert") {
        @Override
        public void actionPerformed(ActionEvent e) {
            addRow();
        }
    };

    private void addRow() {
        if (quoteBoardTable.getCellEditor() != null) {
            quoteBoardTable.getCellEditor().stopCellEditing();
        }
        int rowIndex = quoteBoardTable.getSelectionModel().getLeadSelectionIndex();
        if (rowIndex == -1) {
            rowIndex = quoteBoardTable.getRowCount();
        }

        quoteBoardTableModel.addRow(quoteBoardTable.convertRowIndexToModel(rowIndex));
        quoteBoardTable.changeSelection(rowIndex, -1, false, false);
        quoteBoardTable.requestFocus();
        quoteBoardTable
            .editCellAt(rowIndex, quoteBoardTable.convertColumnIndexToView(QuoteBoardTableColumn.SYMBOL.ordinal()));
        quoteBoardTable.getEditorComponent().requestFocusInWindow();
    }

    private final Action removeAction = new AbstractAction("Remove") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (quoteBoardTable.getCellEditor() != null) {
                quoteBoardTable.getCellEditor().stopCellEditing();
            }
            int rowIndex = quoteBoardTable.getSelectionModel().getMinSelectionIndex();
            synchronized (this) {
                int[] selectedRows = quoteBoardTable.getSelectedRows();
                for (int i = 0; i < selectedRows.length; ++i) {
                    selectedRows[i] = quoteBoardTable.convertRowIndexToModel(selectedRows[i]);
                }
                quoteBoardTableModel.removeRows(selectedRows);
                rowIndex = Math.min(rowIndex, quoteBoardTable.getRowCount() - 1);
                quoteBoardTable.changeSelection(rowIndex, -1, false, false);
            }
            quoteBoardTable.requestFocus();
        }
    };

    private final Action upAction = new AbstractAction("Move Up") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (quoteBoardTable.getCellEditor() != null) {
                quoteBoardTable.getCellEditor().stopCellEditing();
            }
            int rowIndex = quoteBoardTable.getSelectionModel().getLeadSelectionIndex();
            if (rowIndex <= 0) {
                return;
            }
            quoteBoardTableModel.swapRows(rowIndex - 1, rowIndex);
            quoteBoardTable.changeSelection(rowIndex - 1, -1, false, false);
            quoteBoardTable.requestFocus();
        }
    };

    private final Action downAction = new AbstractAction("Move Down") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (quoteBoardTable.getCellEditor() != null) {
                quoteBoardTable.getCellEditor().stopCellEditing();
            }
            int rowIndex = quoteBoardTable.getSelectionModel().getLeadSelectionIndex();
            if (rowIndex == -1 || rowIndex >= quoteBoardTable.getRowCount() - 1) {
                return;
            }
            quoteBoardTableModel.swapRows(rowIndex, rowIndex + 1);
            quoteBoardTable.changeSelection(rowIndex + 1, -1, false, false);
            quoteBoardTable.requestFocus();
        }
    };

    private final Action editAction = new AbstractAction("Edit symbol") {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (quoteBoardTable.getCellEditor() == null) {
                quoteBoardTable.editCellAt(quoteBoardTable.getSelectionModel().getLeadSelectionIndex(),
                    quoteBoardTable.convertColumnIndexToView(0));
                quoteBoardTable.getEditorComponent().requestFocusInWindow();
            } else {
                quoteBoardTable.getCellEditor().stopCellEditing();
                if (suggestMenu != null && suggestMenu.isShowing()) {
                    suggestMenu.setVisible(false);
                }
            }
        }
    };

    private final Action viewOptions4Strikes = new AbstractAction("View options: 4 strikes") {
        @Override
        public void actionPerformed(ActionEvent e) {
            addOptionsToWatchlist(4);
        }
    };

    private final Action viewOptions8Strikes = new AbstractAction("View options: 8 strikes") {
        @Override
        public void actionPerformed(ActionEvent e) {
            addOptionsToWatchlist(8);
        }
    };

    private final Action viewOptionsAllStrikes = new AbstractAction("View options: all strikes") {
        @Override
        public void actionPerformed(ActionEvent e) {
            addOptionsToWatchlist(Integer.MAX_VALUE);
        }
    };


    private final Action toggleBidSizeGraphRendererAlignment = new AbstractAction("Toggle Bid Size graph alignment") {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            bidSizeGraphRenderer.toggleHorizontalAlignment();
            bidTableModel.fireTableChanged(new TableModelEvent(bidTableModel));
        }
    };

    private final Action toggleAskSizeGraphRendererAlignment = new AbstractAction("Toggle Ask Size graph alignment") {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            askSizeGraphRenderer.toggleHorizontalAlignment();
            askTableModel.fireTableChanged(new TableModelEvent(askTableModel));
        }
    };

    private final Action replayFromSelectedTimeAndSale = new AbstractAction("Replay from selected time") {
        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            int index = timeAndSalesTable.getSelectionModel().getMinSelectionIndex();
            if (index >= 0) {
                TimeAndSale tns = timeAndSalesTableModel.events.get(index);
                if (switchToOnDemandMode()) {
                    Date date = new Date(tns.getTime());
                    onDemandTimeSpinner.setValue(date);
                    onDemand.replay(date);
                    play.actionPerformed(null);
                    forceSelectSymbol(selectedSymbol);
                }
            }
        }
    };

    private final Action pause = new AbstractAction("Freeze") {
        @Override
        public void actionPerformed(ActionEvent e) {
            onDemand.pause();
            quoteBoardTableModel.setFrozen(true);
            bidTableModel.setFrozen(true);
            askTableModel.setFrozen(true);
            timeAndSalesTableModel.setFrozen(true);

            pauseButton.setEnabled(false);
            playButton.setEnabled(true);
        }
    };

    private final Action play = new AbstractAction("Unfreeze") {
        @Override
        public void actionPerformed(ActionEvent e) {
            quoteBoardTableModel.setFrozen(false);
            bidTableModel.setFrozen(false);
            askTableModel.setFrozen(false);
            timeAndSalesTableModel.setFrozen(false);

            if (isInOnDemandMode) {
                onDemand.replay(onDemand.getTime());
            }

            pauseButton.setEnabled(true);
            playButton.setEnabled(false);
        }
    };

    private static void setDarkTheme() {
        ViewerCellRenderer.INSTANCE.setScheme(ViewerCellRenderer.DARK_SCHEME);
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
        SwingUtilities.invokeLater(new DXFeedMarketDataViewer(configFile));
    }

    @Override
    public void run() {
        createFeed();
        initQuoteBoard();
        initMarketDepth();
        initTimeAndSales();
        initTickChartRendererPanel();
        initStatsUpdater();
        createFrame();
    }

    private void createFeed() {
        if (endpoint != null) {
            endpoint.disconnectAndClear();
        } else {
            endpoint = DXEndpoint.create().executor(new SwingExecutor(uiRefreshPeriod));

            labelFlasher = new LabelFlashSupport(onDemandConnectionStatusLabel);

            endpoint.addStateChangeListener(e -> {
                if ("state".equals(e.getPropertyName())) {
                    String text = "";
                    Color color = Color.WHITE;
                    DXEndpoint.State state = (DXEndpoint.State) e.getNewValue();

                    labelFlasher.stopFlashing();

                    switch (state) {
                    case CLOSED:
                        text += "CLOSED";
                        color = Color.GRAY;
                        break;
                    case CONNECTED:
                        text += "CONNECTED";
                        color = Color.GREEN.darker();
                        break;
                    case CONNECTING:
                        text += "Connecting...";
                        color = Color.YELLOW.darker();
                        break;
                    case NOT_CONNECTED:
                        text += "NOT CONNECTED";
                        color = Color.RED.darker();
                        break;
                    }
                    onDemandConnectionStatusLabel.setText(text);
                    onDemandConnectionStatusLabel.setForeground(color);

                    if (state == DXEndpoint.State.CONNECTING) {
                        labelFlasher.setLabel(onDemandConnectionStatusLabel);
                        labelFlasher.startFlashing();
                    }
                }
            });

            onDemand = OnDemandService.getInstance(endpoint);


            onDemand.addPropertyChangeListener(e -> {
                if (e.getPropertyName().equals("replaySupported")) {
                    if ((Boolean) e.getNewValue()) {
                        enableOnDemandControls();
                    } else {
                        disableOnDemandControls();
                    }
                }

                if (isInOnDemandMode) { // show only in onDemand mode
                    if (e.getPropertyName().equals("time")) {
                        setCurrentTimeLabel((Date) e.getNewValue());
                    }
                }
            });
        }

        feed = endpoint.getFeed();
        if (onDemandCredentials != null) {
            endpoint.user(onDemandCredentials.getUsername());
            endpoint.password(onDemandCredentials.getPassword());
        }

        // Actually connect to the address
        endpoint.connect(address);
    }

    private void enableOnDemandControls() {
        modeComboBox.setEnabled(true);
        replayFromSelectedTimeAndSale.setEnabled(true);
    }

    private void disableOnDemandControls() {
        modeComboBox.setSelectedIndex(0);
        modeComboBox.setEnabled(false);
        replayFromSelectedTimeAndSale.setEnabled(false);
    }

    private boolean switchToOnDemandMode() {
        boolean result = false;
        if (!isInOnDemandMode && modeComboBox.isEnabled()) {
            if (onDemandCredentials != null || showOnDemandPasswordDialog()) {
                modeComboBox.setSelectedIndex(1);
                result = true;
            }
        } else {
            result = true;
        }
        return result;
    }

    private void initQuoteBoard() {
        initQuoteBoardTable();
        registerQuoteBoardActions();
        initQuoteBoardSubscriptions();
    }

    private void setCellRenderer(JTable table) {
        if (scheme == ViewerCellRenderer.DARK_SCHEME) {
            table.setGridColor(ViewerCellRenderer.DARK_SCHEME_GRID_COLOR);
        } else {
            table.setGridColor(ViewerCellRenderer.LIGHT_SCHEME_GRID_COLOR);
        }
        table.setDefaultRenderer(Object.class, ViewerCellRenderer.INSTANCE);

        DefaultCellEditor ed = ((DefaultCellEditor) table.getDefaultEditor(Object.class));
        ed.getComponent().setBackground(ViewerCellRenderer.EDIT_BG_COLOR);
        ed.getComponent().setForeground(ViewerCellRenderer.EDIT_FG_COLOR);
    }

    private void initQuoteBoardTable() {
        setCellRenderer(quoteBoardTable);
        quoteBoardTable.setModel(quoteBoardTableModel);

        TableColumnModel columnModel = quoteBoardTable.getColumnModel();
        for (QuoteBoardTableColumn column : QuoteBoardTableColumn.values()) {
            columnModel.getColumn(column.ordinal()).setPreferredWidth(column.preferredWidth);
        }

        quoteBoardTable.getSelectionModel().addListSelectionListener(e -> updateOrderBook());
        quoteBoardTable.getModel().addTableModelListener(e -> updateOrderBook());

//        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(quoteBoardTableModel) {
//            @Override
//            public boolean isSortable(int column) {
//                return column == QuoteBoardTableColumn.SYMBOL.ordinal();
//            }
//        };
//        quoteBoardTable.setRowSorter(sorter);
//        sorter.setSortsOnUpdates(true);


        final JTextField textField =
            (JTextField) ((DefaultCellEditor) quoteBoardTable.getDefaultEditor(Object.class)).getComponent();
        textField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (ipfMode == IpfMode.NO_IPF) {
                    return;
                }
                final String prefix = textField.getText() + (e.getKeyChar() <= ' ' ? "" : e.getKeyChar());
                if (suggestMenu != null && prefix.equals(suggestMenu.prefix)) {
                    return;
                }
                if (suggestMenu != null) {
                    suggestMenu.stop();
                }
                final Action onSelectAction = new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final String symbolWithDescription = e.getActionCommand();
                        final int space = symbolWithDescription.indexOf(' ');
                        final String symbol;
                        if (space < 0) {
                            symbol = symbolWithDescription;
                        } else {
                            symbol = symbolWithDescription.substring(0, space);
                        }
                        textField.setText(symbol);
                        suggestMenu.setVisible(false);
                        int lastEditedQuoteBoardRow = -1;
                        int lastEditedQuoteBoardColumn = -1;
                        if (quoteBoardTable.getEditorComponent() != null) {
                            lastEditedQuoteBoardRow = quoteBoardTable.getEditingRow();
                            lastEditedQuoteBoardColumn = quoteBoardTable.getEditingColumn();
                            quoteBoardTable.getCellEditor().stopCellEditing();
                        }
                        quoteBoardTable.setValueAt(symbol, lastEditedQuoteBoardRow, lastEditedQuoteBoardColumn);
                        quoteBoardTable.requestFocusInWindow();
                    }
                };

                if (ipfMode == IpfMode.LOCAL_FILE) {
                    suggestMenu =
                        new SuggestMenu(prefix, onSelectAction, quoteBoardTable, textField, ipfAddress, instruments);
                } else if (ipfMode == IpfMode.WEB_SERVICE) {
                    suggestMenu =
                        new SuggestMenu(prefix, onSelectAction, quoteBoardTable, textField, ipfAddress, instruments,
                            ipfWebServiceCredentials.getUsername(), ipfWebServiceCredentials.getPassword());
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });

        quoteBoardTable.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (suggestMenu != null && suggestMenu.isVisible()) {
                    suggestMenu.setVisible(false);
                }
            }
        });
    }

    private void registerQuoteBoardActions() {
        registerQuoteBoardAction(insertAction, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
        registerQuoteBoardAction(removeAction, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        registerQuoteBoardAction(upAction, KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_MASK));
        registerQuoteBoardAction(downAction, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_MASK));
        registerQuoteBoardAction(editAction, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    }

    private void registerQuoteBoardAction(Action action, KeyStroke keyStroke) {
        final String key = keyStroke.toString();
        quoteBoardTable.getInputMap().put(keyStroke, key);
        quoteBoardTable.getActionMap().put(key, action);
    }

    private void initQuoteBoardSubscriptions() {
        DXFeedSubscription<Quote> quoteSubscription = feed.createSubscription(Quote.class);
        DXFeedSubscription<Trade> tradeSubscription = feed.createSubscription(Trade.class);
        DXFeedSubscription<Summary> summarySubscription = feed.createSubscription(Summary.class);
        DXFeedSubscription<Profile> profileSubscription = feed.createSubscription(Profile.class);

        quoteSubscription.addEventListener(events -> {
            eventStats.increment(events.size());
            quoteStats.increment(events.size());
            quoteBoardTableModel.eventsReceived(events);
        });
        tradeSubscription.addEventListener(events -> {
            eventStats.increment(events.size());
            tradeStats.increment(events.size());
            quoteBoardTableModel.eventsReceived(events);
        });
        summarySubscription.addEventListener(events -> {
            eventStats.increment(events.size());
            summaryStats.increment(events.size());
            quoteBoardTableModel.eventsReceived(events);
        });
        profileSubscription.addEventListener(events -> {
            eventStats.increment(events.size());
            quoteBoardTableModel.eventsReceived(events);
        });

        quoteBoardSubscriptions.add(quoteSubscription);
        quoteBoardSubscriptions.add(tradeSubscription);
        quoteBoardSubscriptions.add(summarySubscription);
        quoteBoardSubscriptions.add(profileSubscription);

        quoteBoardTableModel.setSymbols(symbols);
    }

    private void initMarketDepth() {
        initTable(bidTable, bidTableModel);
        initTable(askTable, askTableModel);

        // bar renderer for Size
        bidSizeGraphRenderer = new BarGraphCellRenderer(true);
        bidTable.getColumnModel().getColumn(3).setCellRenderer(bidSizeGraphRenderer);

        askSizeGraphRenderer = new BarGraphCellRenderer(true);
        askTable.getColumnModel().getColumn(3).setCellRenderer(askSizeGraphRenderer);

        initMarketDepthSubscription();

        // add actions for clicking on Size header
        bidTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = bidTable.convertColumnIndexToModel(bidTable.columnAtPoint(e.getPoint()));
                if (index == 3) {
                    toggleBidSizeGraphRendererAlignment.actionPerformed(null);
                }
            }
        });
        askTable.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = askTable.convertColumnIndexToModel(askTable.columnAtPoint(e.getPoint()));
                if (index == 3) {
                    toggleAskSizeGraphRendererAlignment.actionPerformed(null);
                }
            }
        });
    }

    private void initTable(JTable table, EventTableModel<?> model) {
        table.setModel(model);
        TableColumnModel columnModel = table.getColumnModel();
        for (int i = 0; i < model.columns.length; i++) {
            columnModel.getColumn(i).setPreferredWidth(model.columns[i].getPreferredWidth());
        }
        table.setModel(model);
        setCellRenderer(table);
    }

    private void initMarketDepthSubscription() {
        orderBookModel.attach(feed);

        orderSubscription = feed.createSubscription(Order.class);
        orderSubscription.addEventListener(events -> {
            eventStats.increment(events.size());
            orderStats.increment(events.size());
            calibrateGraphRendererZoomFactors();

            tickChartRendererPanel.repaint();
        });
    }

    private void initTimeAndSales() {
        initTable(timeAndSalesTable, timeAndSalesTableModel);
        initTimeAndSalesSubscription();
        timeAndSalesTable.getSelectionModel().addListSelectionListener(e -> {
            tickChartRendererPanel.selectTicks(timeAndSalesTable.getSelectionModel().getMinSelectionIndex(),
                timeAndSalesTable.getSelectionModel().getMaxSelectionIndex());
            tickChartRendererPanel.repaint();
        });
    }

    private void initTimeAndSalesSubscription() {
        timeAndSalesSubscription = feed.createTimeSeriesSubscription(TimeAndSale.class);
        timeAndSalesSubscription.setFromTime(0);
        timeAndSalesSubscription.addEventListener(events -> {
            eventStats.increment(events.size());
            tnsStats.increment(events.size());
            timeAndSalesTableModel.eventsReceived(events);
            tickChartRendererPanel.repaint();
        });
    }

    private void initTickChartRendererPanel() {
        tickChartRendererPanel =
            new TickChartRendererPanel(timeAndSalesTableModel.events, bidTableModel.events, askTableModel.events,
                ViewerCellRenderer.DEFAULT_SCHEME);
        timeAndSalesTableModel.addTableModelListener(e -> tickChartRendererPanel.setRepaintRequired(true));
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

                int i = tickChartRendererPanel.getTickIndexByX(e.getX());
                timeAndSalesTable.getSelectionModel()
                    .setSelectionInterval(i, timeAndSalesTable.getSelectionModel().getLeadSelectionIndex());
                timeAndSalesTable.scrollRectToVisible(timeAndSalesTable.getCellRect(i, 0, true));

            }

            @Override
            public void mouseMoved(MouseEvent e) {
                tickChartRendererPanel.setCrosshair(e.getX(), e.getY());
            }
        });

        tickChartRendererPanel.addMouseListener(new MouseListener() {
            @Override
            public void mouseReleased(MouseEvent e) {
                int i = tickChartRendererPanel.getTickIndexByX(e.getX());
                timeAndSalesTable.scrollRectToVisible(timeAndSalesTable.getCellRect(i, 0, true));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int i = tickChartRendererPanel.getTickIndexByX(e.getX());
                timeAndSalesTable.getSelectionModel().setSelectionInterval(i, i);
                timeAndSalesTable.getSelectionModel().setLeadSelectionIndex(i);
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
                tpsLabel.setText(tradeStats.update(delta));
                spsLabel.setText(summaryStats.update(delta));
                opsLabel.setText(orderStats.update(delta));
                tspsLabel.setText(tnsStats.update(delta));

                if (!isInOnDemandMode) {
                    setCurrentTimeLabel(new Date(curTime));
                }

                lastTime = curTime;

                calibrateGraphRendererZoomFactors();
            }
        });
        timer.start();
    }

    private void createFrame() {
        mainFrame = new JFrame("dxFeed Market Data Viewer" + (name.length() == 0 ? "" : ": " + name));
        mainFrame.add(form);
        mainFrame.pack();
        mainFrame.setLocationByPlatform(true);

        JMenuItem menuItem;
        final JPopupMenu quoteBoardPopup = new JPopupMenu();
        menuItem = new JMenuItem("Edit");
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        menuItem.addActionListener(editAction);
        quoteBoardPopup.add(menuItem);

        menuItem = new JMenuItem("Add row");
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0));
        menuItem.addActionListener(insertAction);
        quoteBoardPopup.add(menuItem);

        menuItem = new JMenuItem("Remove row");
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
        menuItem.addActionListener(removeAction);
        quoteBoardPopup.add(menuItem);

        quoteBoardPopup.addSeparator();

        menuItem = new JMenuItem("Move up");
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_KP_UP, KeyEvent.CTRL_MASK));
        menuItem.addActionListener(upAction);
        quoteBoardPopup.add(menuItem);

        menuItem = new JMenuItem("Move down");
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_KP_DOWN, KeyEvent.CTRL_MASK));
        menuItem.addActionListener(downAction);
        quoteBoardPopup.add(menuItem);

        if (ipfMode != IpfMode.NO_IPF) {
            quoteBoardPopup.addSeparator();

            menuItem = new JMenuItem("View options: 4 strikes");
            menuItem.addActionListener(viewOptions4Strikes);
            quoteBoardPopup.add(menuItem);

            menuItem = new JMenuItem("View options: 8 strikes");
            menuItem.addActionListener(viewOptions8Strikes);
            quoteBoardPopup.add(menuItem);

            menuItem = new JMenuItem("View options: all strikes");
            menuItem.addActionListener(viewOptionsAllStrikes);
            quoteBoardPopup.add(menuItem);
        }

        quoteBoardTable.addMouseListener(new ShowPopupSupport(quoteBoardPopup));
        quoteBoardTable.getParent().addMouseListener(new ShowPopupSupport(quoteBoardPopup));

        final JPopupMenu depthTablePopup = new JPopupMenu();
        menuItem = new JMenuItem("Toggle bid size chart horizontal alignment");
        menuItem.addActionListener(toggleBidSizeGraphRendererAlignment);
        depthTablePopup.add(menuItem);

        menuItem = new JMenuItem("Toggle ask size chart horizontal alignment");
        menuItem.addActionListener(toggleAskSizeGraphRendererAlignment);
        depthTablePopup.add(menuItem);

        JMenu viewSubMenu = new JMenu("View");
        depthTablePopup.add(viewSubMenu);
        ButtonGroup viewGroup = new ButtonGroup();

        menuItem = new JRadioButtonMenuItem("Composite Only");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.COMPOSITE));
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("Regionals Only");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.REGIONAL));
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("Aggregate (Level 2 / Price Levels) Only");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.AGGREGATE));
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("Orders Only");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.ORDER));
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("Composite and Regionals");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.COMPOSITE_REGIONAL));
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("Composite, Regionals and Aggregate (Level 2 / Price Levels)");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.COMPOSITE_REGIONAL_AGGREGATE));
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("All");
        menuItem.addActionListener(new ChooseFilterAction(OrderBookModelFilter.ALL));
        menuItem.setSelected(true);
        viewSubMenu.add(menuItem);
        viewGroup.add(menuItem);

        JMenu lotSizeSubMenu = new JMenu("Set Lot Size");
        depthTablePopup.add(lotSizeSubMenu);
        ButtonGroup lotSizeGroup = new ButtonGroup();

        menuItem = new JRadioButtonMenuItem("1");
        menuItem.addActionListener(new ChooseLotSizeAction(1));
        menuItem.setSelected(true);
        lotSizeSubMenu.add(menuItem);
        lotSizeGroup.add(menuItem);

        menuItem = new JRadioButtonMenuItem("100");
        menuItem.addActionListener(new ChooseLotSizeAction(100));
        lotSizeSubMenu.add(menuItem);
        lotSizeGroup.add(menuItem);

        JMenu colorSubMenu = new JMenu("Set color scheme");
        depthTablePopup.add(colorSubMenu);

        menuItem = new JMenuItem("Depth");
        menuItem.addActionListener(new ChooseSchemeAction(OrderCellSupport.DEPTH_SCHEME));
        colorSubMenu.add(menuItem);

        menuItem = new JMenuItem("Zebra");
        menuItem.addActionListener(new ChooseSchemeAction(OrderCellSupport.ZEBRA_SCHEME));
        colorSubMenu.add(menuItem);

        menuItem = new JMenuItem("Colorful");
        menuItem.addActionListener(new ChooseSchemeAction(OrderCellSupport.COLORFUL_SCHEME));
        colorSubMenu.add(menuItem);

        menuItem = new JMenuItem("Monochrome");
        menuItem.addActionListener(new ChooseSchemeAction(OrderCellSupport.MONOCHROME_SCHEME));
        colorSubMenu.add(menuItem);

        menuItem = new JMenuItem("No Color");
        menuItem.addActionListener(new ChooseSchemeAction(OrderCellSupport.NO_SCHEME));
        colorSubMenu.add(menuItem);

        bidTable.addMouseListener(new ShowPopupSupport(depthTablePopup));
        bidTable.getParent().addMouseListener(new ShowPopupSupport(depthTablePopup));
        askTable.addMouseListener(new ShowPopupSupport(depthTablePopup));
        askTable.getParent().addMouseListener(new ShowPopupSupport(depthTablePopup));


        final JPopupMenu tnsTablePopup = new JPopupMenu();
        menuItem = new JMenuItem("Replay from selected time");
        menuItem.addActionListener(replayFromSelectedTimeAndSale);
        tnsTablePopup.add(menuItem);

        timeAndSalesTable.addMouseListener(new ShowPopupSupport(tnsTablePopup));
        tickChartRendererPanel.addMouseListener(new ShowPopupSupport(tnsTablePopup));


        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                saveConfiguration(configFile);
                System.exit(0);
            }
        });

        pauseButton.addActionListener(pause);
        playButton.addActionListener(play);


        connectionAddressEdit.setText(address);
        connectionAddressEdit.addActionListener(e -> {
            if (isInOnDemandMode) {
                modeComboBox.setSelectedIndex(0);
                isInOnDemandMode = false;
            }
            address = connectionAddressEdit.getText();
            createFeed();
        });

        tzComboBoxSupport = new TimezoneComboBoxSupport(tzComboBox);
        tzComboBox.addActionListener(e -> {
            setOnDemandTimeSpinnerTimeZone();
            quoteBoardTableModel.setTimeZone(TimeZone.getTimeZone(tzComboBoxSupport.getSelectedTimezoneID()));
            askTableModel.setTimeZone(TimeZone.getTimeZone(tzComboBoxSupport.getSelectedTimezoneID()));
            bidTableModel.setTimeZone(TimeZone.getTimeZone(tzComboBoxSupport.getSelectedTimezoneID()));
            timeAndSalesTableModel.setTimeZone(TimeZone.getTimeZone(tzComboBoxSupport.getSelectedTimezoneID()));
        });

        initOnDemandTimeSpinner();
        onDemandSpeed = 1;
        onDemandSpeedSlider.addChangeListener(e -> {
            int value = onDemandSpeedSlider.getValue();
            if (value <= 5) {
                // below 5 (mid) we go for fractional speed multipliers of 1/2, 1/3, 1/4...
                onDemandSpeed = 1.0 / (6 - value);
                if (value == 5) {
                    onDemandSpeedLabel.setText("x 1");
                } else {
                    onDemandSpeedLabel.setText("x 1/" + (6 - value));
                }
            } else {
                // above 5 we go for multipliers of 2,3,4,5,6
                onDemandSpeed = value - 4;
                onDemandSpeedLabel.setText("x " + (value - 4));
            }
            onDemand.setSpeed(onDemandSpeed);
        });
        onDemandTimeSpinner.addChangeListener(e -> onDemand.replay((Date) onDemandTimeSpinner.getValue(), onDemandSpeed));

        modeComboBox.addItem("Datafeed");
        modeComboBox.addItem("onDemand Market Replay");
        modeComboBox.addActionListener(e -> switchMode());

        onDemandToolbar.setVisible(false);

        connectAsButton.addActionListener(e -> showOnDemandPasswordDialog());

        setComponentSizes();

        mainFrame.setMinimumSize(new Dimension(800, 600));
        mainFrame.setExtendedState(mainFrame.getExtendedState() | Frame.MAXIMIZED_BOTH);

        mainFrame.setVisible(true);
    }

    private void switchMode() {
        if (modeComboBox.getSelectedIndex() == 1) {
            onDemandToolbar.setVisible(true);
            boolean goOnDemand = true;
            if (onDemandCredentials == null) {
                goOnDemand = showOnDemandPasswordDialog();
            }
            if (goOnDemand) {
                isInOnDemandMode = true;
                onDemand.replay((Date) onDemandTimeSpinner.getValue(), onDemandSpeed);
                forceSelectSymbol(selectedSymbol);
            } else {
                modeComboBox.setSelectedIndex(0);
            }
        } else {
            onDemandToolbar.setVisible(false);
            onDemand.stopAndResume();
            forceSelectSymbol(selectedSymbol);
            isInOnDemandMode = false;
        }
    }

    private boolean showOnDemandPasswordDialog() {
        boolean result = false;
        Credentials c;
        if (onDemandCredentials != null) {
            c = onDemandCredentials;
        } else {
            c = new Credentials("demo", "demo");
        }


        c = PasswordDialog.showPasswordDialog("Accessing onDemand cloud", c, mainFrame);
        if (c != null) {
            onDemandCredentials = new Credentials(c.getUsername(), c.getPassword());
            endpoint.user(onDemandCredentials.getUsername());
            endpoint.password(onDemandCredentials.getPassword());
            onDemandUsernameLabel.setText("on demand: " + onDemandCredentials.getUsername().toLowerCase());
            result = true;
        }
        return result;
    }

    private void setComponentSizes() {
        mpsLabel.setPreferredSize(new Dimension(70, 14));
        qpsLabel.setPreferredSize(new Dimension(70, 14));
        tpsLabel.setPreferredSize(new Dimension(70, 14));
        spsLabel.setPreferredSize(new Dimension(70, 14));
        opsLabel.setPreferredSize(new Dimension(70, 14));
        tspsLabel.setPreferredSize(new Dimension(70, 14));

        currentTimeLabel.setPreferredSize(new Dimension(100, 14));
        onDemandUsernameLabel.setPreferredSize(new Dimension(100, 14));
        ipfWebUsernameLabel.setPreferredSize(new Dimension(100, 14));

        onDemandConnectionStatusLabel.setPreferredSize(new Dimension(120, 14));
        ipfWebConnectionStatusLabel.setPreferredSize(new Dimension(120, 14));

        onDemandTimeSpinner.setPreferredSize(new Dimension(170, 20));
        onDemandTimeSpinner.setMaximumSize(new Dimension(170, 30));

        modeComboBox.setMinimumSize(new Dimension(170, 20));
        modeComboBox.setPreferredSize(new Dimension(170, 20));
        modeComboBox.setMaximumSize(new Dimension(170, 30));

        tzComboBox.setMinimumSize(new Dimension(180, 20));
        tzComboBox.setPreferredSize(new Dimension(180, 20));
        tzComboBox.setMaximumSize(new Dimension(180, 30));
    }

    private void initOnDemandTimeSpinner() {
        Calendar defaultDate = GregorianCalendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        Calendar start = GregorianCalendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        Calendar end = GregorianCalendar.getInstance();

        start.clear();
        defaultDate.clear();

        start.set(Calendar.YEAR, 2010);

        // set default date to flashcrash on 2010-05-06
        defaultDate.set(Calendar.YEAR, 2010);
        defaultDate.set(Calendar.MONTH, Calendar.MAY);
        defaultDate.set(Calendar.DAY_OF_MONTH, 6);
        defaultDate.set(Calendar.HOUR, 14);
        defaultDate.set(Calendar.MINUTE, 46);
        defaultDate.set(Calendar.SECOND, 55);

        SpinnerDateModel spinnerDateModel =
            new SpinnerDateModel(defaultDate.getTime(), start.getTime(), end.getTime(), Calendar.SECOND);
        onDemandTimeSpinner.setModel(spinnerDateModel);
        setOnDemandTimeSpinnerTimeZone();
    }

    private void setOnDemandTimeSpinnerTimeZone() {
        if (tzComboBoxSupport != null) {
            JSpinner.DateEditor editor = (JSpinner.DateEditor) onDemandTimeSpinner.getEditor();
            SimpleDateFormat format = editor.getFormat();
            TimeZone tz = TimeZone.getTimeZone(tzComboBoxSupport.getSelectedTimezoneID());
            timeFormatSelectedTZ.setTimeZone(tz);
            format.setTimeZone(tz);
            format.applyPattern(timeFormatSelectedTZ.toPattern());
            editor.getTextField().setText(format.format(onDemandTimeSpinner.getValue()));
        }
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
        onDemandConnectionStatusLabel = new JLabel();
        onDemandConnectionStatusLabel.setEnabled(true);
        Font onDemandConnectionStatusLabelFont = this.$$$getFont$$$(null, Font.BOLD, 10, onDemandConnectionStatusLabel.getFont());
        if (onDemandConnectionStatusLabelFont != null)
            onDemandConnectionStatusLabel.setFont(onDemandConnectionStatusLabelFont);
        onDemandConnectionStatusLabel.setText("Label");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel4.add(onDemandConnectionStatusLabel, gbc);
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridBagLayout());
        Font panel5Font = this.$$$getFont$$$(null, Font.BOLD, 10, panel5.getFont());
        if (panel5Font != null) panel5.setFont(panel5Font);
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel4.add(panel5, gbc);
        currentTimeLabel = new JLabel();
        Font currentTimeLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, currentTimeLabel.getFont());
        if (currentTimeLabelFont != null) currentTimeLabel.setFont(currentTimeLabelFont);
        currentTimeLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel5.add(currentTimeLabel, gbc);
        final JToolBar.Separator toolBar$Separator1 = new JToolBar.Separator();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel5.add(toolBar$Separator1, gbc);
        tzComboBox = new JComboBox();
        tzComboBox.setEditable(true);
        Font tzComboBoxFont = this.$$$getFont$$$(null, Font.PLAIN, 10, tzComboBox.getFont());
        if (tzComboBoxFont != null) tzComboBox.setFont(tzComboBoxFont);
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        panel5.add(tzComboBox, gbc);
        final JSeparator separator1 = new JSeparator();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel5.add(separator1, gbc);
        final JToolBar.Separator toolBar$Separator2 = new JToolBar.Separator();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel4.add(toolBar$Separator2, gbc);
        onDemandUsernameLabel = new JLabel();
        Font onDemandUsernameLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, onDemandUsernameLabel.getFont());
        if (onDemandUsernameLabelFont != null) onDemandUsernameLabel.setFont(onDemandUsernameLabelFont);
        onDemandUsernameLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel4.add(onDemandUsernameLabel, gbc);
        ipfWebConnectionStatusLabel = new JLabel();
        ipfWebConnectionStatusLabel.setEnabled(true);
        Font ipfWebConnectionStatusLabelFont = this.$$$getFont$$$(null, Font.BOLD, 10, ipfWebConnectionStatusLabel.getFont());
        if (ipfWebConnectionStatusLabelFont != null)
            ipfWebConnectionStatusLabel.setFont(ipfWebConnectionStatusLabelFont);
        ipfWebConnectionStatusLabel.setText("Label");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel4.add(ipfWebConnectionStatusLabel, gbc);
        ipfWebUsernameLabel = new JLabel();
        Font ipfWebUsernameLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, ipfWebUsernameLabel.getFont());
        if (ipfWebUsernameLabelFont != null) ipfWebUsernameLabel.setFont(ipfWebUsernameLabelFont);
        ipfWebUsernameLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel4.add(ipfWebUsernameLabel, gbc);
        final JSeparator separator2 = new JSeparator();
        separator2.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator2, gbc);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridBagLayout());
        panel6.setToolTipText("Total events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel6, gbc);
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
        panel6.add(mpsLabel, gbc);
        final JSeparator separator3 = new JSeparator();
        separator3.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator3, gbc);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridBagLayout());
        panel7.setToolTipText("Quote events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel7, gbc);
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
        panel7.add(qpsLabel, gbc);
        final JSeparator separator4 = new JSeparator();
        separator4.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 5;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator4, gbc);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridBagLayout());
        panel8.setToolTipText("Trade events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 6;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel8, gbc);
        tpsLabel = new JLabel();
        Font tpsLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, tpsLabel.getFont());
        if (tpsLabelFont != null) tpsLabel.setFont(tpsLabelFont);
        tpsLabel.setHorizontalAlignment(0);
        tpsLabel.setHorizontalTextPosition(0);
        tpsLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel8.add(tpsLabel, gbc);
        final JSeparator separator5 = new JSeparator();
        separator5.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 7;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator5, gbc);
        final JPanel panel9 = new JPanel();
        panel9.setLayout(new GridBagLayout());
        panel9.setToolTipText("Daily summary (OHLC) events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 8;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel9, gbc);
        spsLabel = new JLabel();
        Font spsLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, spsLabel.getFont());
        if (spsLabelFont != null) spsLabel.setFont(spsLabelFont);
        spsLabel.setHorizontalAlignment(0);
        spsLabel.setHorizontalTextPosition(0);
        spsLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel9.add(spsLabel, gbc);
        final JSeparator separator6 = new JSeparator();
        separator6.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 9;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator6, gbc);
        final JPanel panel10 = new JPanel();
        panel10.setLayout(new GridBagLayout());
        panel10.setToolTipText("Order events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 10;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel10, gbc);
        opsLabel = new JLabel();
        Font opsLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, opsLabel.getFont());
        if (opsLabelFont != null) opsLabel.setFont(opsLabelFont);
        opsLabel.setHorizontalAlignment(0);
        opsLabel.setHorizontalTextPosition(0);
        opsLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel10.add(opsLabel, gbc);
        final JSeparator separator7 = new JSeparator();
        separator7.setOrientation(1);
        gbc = new GridBagConstraints();
        gbc.gridx = 11;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(separator7, gbc);
        final JPanel panel11 = new JPanel();
        panel11.setLayout(new GridBagLayout());
        panel11.setToolTipText("Time&Sale events per second");
        gbc = new GridBagConstraints();
        gbc.gridx = 12;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel3.add(panel11, gbc);
        tspsLabel = new JLabel();
        Font tspsLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 10, tspsLabel.getFont());
        if (tspsLabelFont != null) tspsLabel.setFont(tspsLabelFont);
        tspsLabel.setHorizontalAlignment(0);
        tspsLabel.setHorizontalTextPosition(0);
        tspsLabel.setText("");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel11.add(tspsLabel, gbc);
        final JPanel panel12 = new JPanel();
        panel12.setLayout(new GridBagLayout());
        form.add(panel12, BorderLayout.CENTER);
        final JSplitPane splitPane1 = new JSplitPane();
        splitPane1.setOneTouchExpandable(true);
        splitPane1.setOrientation(0);
        splitPane1.setResizeWeight(0.3);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel12.add(splitPane1, gbc);
        final JScrollPane scrollPane1 = new JScrollPane();
        scrollPane1.setPreferredSize(new Dimension(800, 200));
        splitPane1.setLeftComponent(scrollPane1);
        quoteBoardTable = new JTable();
        scrollPane1.setViewportView(quoteBoardTable);
        final JSplitPane splitPane2 = new JSplitPane();
        splitPane2.setOneTouchExpandable(true);
        splitPane2.setOrientation(0);
        splitPane2.setResizeWeight(0.5);
        splitPane1.setRightComponent(splitPane2);
        final JPanel panel13 = new JPanel();
        panel13.setLayout(new GridBagLayout());
        splitPane2.setRightComponent(panel13);
        final JSplitPane splitPane3 = new JSplitPane();
        splitPane3.setOneTouchExpandable(true);
        splitPane3.setOrientation(0);
        splitPane3.setResizeWeight(1.0);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel13.add(splitPane3, gbc);
        tickChartPanel = new JPanel();
        tickChartPanel.setLayout(new GridBagLayout());
        tickChartPanel.setPreferredSize(new Dimension(800, 200));
        splitPane3.setLeftComponent(tickChartPanel);
        timeAndSalesScrollPane = new JScrollPane();
        timeAndSalesScrollPane.setPreferredSize(new Dimension(800, 100));
        splitPane3.setRightComponent(timeAndSalesScrollPane);
        timeAndSalesScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), null, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        timeAndSalesTable = new JTable();
        timeAndSalesScrollPane.setViewportView(timeAndSalesTable);
        final JPanel panel14 = new JPanel();
        panel14.setLayout(new GridBagLayout());
        splitPane2.setLeftComponent(panel14);
        final JPanel panel15 = new JPanel();
        panel15.setLayout(new GridBagLayout());
        panel15.setPreferredSize(new Dimension(800, 200));
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel14.add(panel15, gbc);
        final JLabel label1 = new JLabel();
        label1.setText("Bid");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel15.add(label1, gbc);
        final JPanel spacer1 = new JPanel();
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel15.add(spacer1, gbc);
        final JLabel label2 = new JLabel();
        label2.setText("Ask");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 0;
        panel15.add(label2, gbc);
        final JScrollPane scrollPane2 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel15.add(scrollPane2, gbc);
        bidTable = new JTable();
        scrollPane2.setViewportView(bidTable);
        final JScrollPane scrollPane3 = new JScrollPane();
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel15.add(scrollPane3, gbc);
        askTable = new JTable();
        scrollPane3.setViewportView(askTable);
        bookModeLabel = new JLabel();
        Font bookModeLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 9, bookModeLabel.getFont());
        if (bookModeLabelFont != null) bookModeLabel.setFont(bookModeLabelFont);
        bookModeLabel.setHorizontalAlignment(2);
        bookModeLabel.setText("Book display mode: All orders");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel14.add(bookModeLabel, gbc);
        lotSizeLabel = new JLabel();
        Font lotSizeLabelFont = this.$$$getFont$$$(null, Font.PLAIN, 9, lotSizeLabel.getFont());
        if (lotSizeLabelFont != null) lotSizeLabel.setFont(lotSizeLabelFont);
        lotSizeLabel.setHorizontalAlignment(4);
        lotSizeLabel.setText("Book display lot size: 1");
        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.VERTICAL;
        panel14.add(lotSizeLabel, gbc);
        final JPanel panel16 = new JPanel();
        panel16.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel12.add(panel16, gbc);
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
        panel16.add(addressToolbar, gbc);
        playButton = new JButton();
        playButton.setEnabled(false);
        playButton.setIcon(new ImageIcon(getClass().getResource("/com/dxfeed/viewer/icons/play-icon.png")));
        playButton.setInheritsPopupMenu(true);
        playButton.setText("");
        playButton.setToolTipText("Connect/Replay");
        playButton.putClientProperty("hideActionText", Boolean.FALSE);
        addressToolbar.add(playButton);
        pauseButton = new JButton();
        pauseButton.setIcon(new ImageIcon(getClass().getResource("/com/dxfeed/viewer/icons/pause-icon.png")));
        pauseButton.setText("");
        pauseButton.setToolTipText("Pause");
        addressToolbar.add(pauseButton);
        final JToolBar.Separator toolBar$Separator3 = new JToolBar.Separator();
        addressToolbar.add(toolBar$Separator3);
        final JLabel label3 = new JLabel();
        Font label3Font = this.$$$getFont$$$(null, -1, -1, label3.getFont());
        if (label3Font != null) label3.setFont(label3Font);
        label3.setText("Connect to: ");
        addressToolbar.add(label3);
        modeComboBox = new JComboBox();
        Font modeComboBoxFont = this.$$$getFont$$$(null, -1, -1, modeComboBox.getFont());
        if (modeComboBoxFont != null) modeComboBox.setFont(modeComboBoxFont);
        modeComboBox.setForeground(new Color(-1));
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        modeComboBox.setModel(defaultComboBoxModel1);
        modeComboBox.setToolTipText("Connection mode");
        addressToolbar.add(modeComboBox);
        connectionAddressEdit = new JTextField();
        connectionAddressEdit.setColumns(0);
        connectionAddressEdit.setEditable(true);
        Font connectionAddressEditFont = this.$$$getFont$$$(null, -1, -1, connectionAddressEdit.getFont());
        if (connectionAddressEditFont != null) connectionAddressEdit.setFont(connectionAddressEditFont);
        connectionAddressEdit.setForeground(new Color(-1));
        connectionAddressEdit.setToolTipText("Connection address");
        addressToolbar.add(connectionAddressEdit);
        connectAsButton = new JButton();
        connectAsButton.setText("  Connect as... ");
        addressToolbar.add(connectAsButton);
        onDemandToolbar = new JToolBar();
        onDemandToolbar.setEnabled(false);
        onDemandToolbar.setFloatable(false);
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel16.add(onDemandToolbar, gbc);
        final JLabel label4 = new JLabel();
        Font label4Font = this.$$$getFont$$$(null, -1, -1, label4.getFont());
        if (label4Font != null) label4.setFont(label4Font);
        label4.setText("Go to: ");
        onDemandToolbar.add(label4);
        onDemandTimeSpinner = new JSpinner();
        Font onDemandTimeSpinnerFont = this.$$$getFont$$$(null, -1, -1, onDemandTimeSpinner.getFont());
        if (onDemandTimeSpinnerFont != null) onDemandTimeSpinner.setFont(onDemandTimeSpinnerFont);
        onDemandToolbar.add(onDemandTimeSpinner);
        final JToolBar.Separator toolBar$Separator4 = new JToolBar.Separator();
        onDemandToolbar.add(toolBar$Separator4);
        final JLabel label5 = new JLabel();
        Font label5Font = this.$$$getFont$$$(null, -1, -1, label5.getFont());
        if (label5Font != null) label5.setFont(label5Font);
        label5.setText("Replay speed:");
        onDemandToolbar.add(label5);
        final JToolBar.Separator toolBar$Separator5 = new JToolBar.Separator();
        onDemandToolbar.add(toolBar$Separator5);
        onDemandSpeedLabel = new JLabel();
        onDemandSpeedLabel.setEnabled(true);
        Font onDemandSpeedLabelFont = this.$$$getFont$$$(null, -1, -1, onDemandSpeedLabel.getFont());
        if (onDemandSpeedLabelFont != null) onDemandSpeedLabel.setFont(onDemandSpeedLabelFont);
        onDemandSpeedLabel.setText("x 1");
        onDemandToolbar.add(onDemandSpeedLabel);
        final JToolBar.Separator toolBar$Separator6 = new JToolBar.Separator();
        onDemandToolbar.add(toolBar$Separator6);
        onDemandSpeedSlider = new JSlider();
        Font onDemandSpeedSliderFont = this.$$$getFont$$$(null, Font.PLAIN, 8, onDemandSpeedSlider.getFont());
        if (onDemandSpeedSliderFont != null) onDemandSpeedSlider.setFont(onDemandSpeedSliderFont);
        onDemandSpeedSlider.setMajorTickSpacing(1);
        onDemandSpeedSlider.setMaximum(10);
        onDemandSpeedSlider.setMinimum(1);
        onDemandSpeedSlider.setPaintLabels(false);
        onDemandSpeedSlider.setPaintTicks(false);
        onDemandSpeedSlider.setPaintTrack(true);
        onDemandSpeedSlider.setSnapToTicks(true);
        onDemandSpeedSlider.setValue(5);
        onDemandSpeedSlider.putClientProperty("JSlider.isFilled", Boolean.TRUE);
        onDemandSpeedSlider.putClientProperty("Slider.paintThumbArrowShape", Boolean.TRUE);
        onDemandToolbar.add(onDemandSpeedSlider);
        mpsLabel.setLabelFor(timeAndSalesScrollPane);
        label3.setLabelFor(modeComboBox);
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

    private class ChooseSchemeAction extends AbstractAction {
        private final int colorScheme;

        private ChooseSchemeAction(int colorScheme) {
            this.colorScheme = colorScheme;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            bidTableModel.setScheme(colorScheme);
            askTableModel.setScheme(colorScheme);
            tickChartRendererPanel.setScheme(colorScheme);
        }
    }

    private String getBookModeLabelText(OrderBookModelFilter filter) {
        String text = "";
        switch (filter) {
        case ALL:
            text = "All orders";
            break;
        case COMPOSITE:
            text = "Composite only";
            break;
        case REGIONAL:
            text = "Regionals only";
            break;
        case AGGREGATE:
            text = "Aggregate only";
            break;
        case ORDER:
            text = "Orders only";
            break;
        case COMPOSITE_REGIONAL:
            text = "Composite & Regionals";
            break;
        case COMPOSITE_REGIONAL_AGGREGATE:
            text = "Composite, Regionals & Aggregate";
            break;
        }
        return "Book display mode: " + text;
    }

    private class ChooseFilterAction extends AbstractAction {
        private final OrderBookModelFilter filter;

        private ChooseFilterAction(OrderBookModelFilter filter) {
            this.filter = filter;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            orderBookModel.setFilter(filter);
            bookModeLabel.setText(getBookModeLabelText(filter));
        }
    }

    private class ChooseLotSizeAction extends AbstractAction {
        private final int lotSize;

        private ChooseLotSizeAction(int lotSize) {
            this.lotSize = lotSize;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            orderBookModel.setLotSize(lotSize);
            lotSizeLabel.setText("Book display lot size: " + lotSize);
        }
    }

    private static class ShowPopupSupport extends MouseAdapter {
        private final JPopupMenu menu;

        private ShowPopupSupport(JPopupMenu menu) {
            this.menu = menu;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            maybeShowPopup(e);
        }

        private void maybeShowPopup(MouseEvent e) {
            if (e.isPopupTrigger()) {
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    private static class LabelFlashSupport {
        private JLabel label;
        private Color baseColor;
        private Color darkerColor;
        private boolean darker = false;

        LabelFlashSupport(JLabel label) {
            setLabel(label);
        }

        private void setLabel(JLabel label) {
            this.label = label;
            this.baseColor = label.getForeground();
            this.darkerColor = baseColor.darker();
        }

        private void startFlashing() {
            timer.start();
        }

        private void stopFlashing() {
            timer.stop();
        }

        private final Timer timer = new Timer(200, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Color c;
                if (darker) {
                    c = darkerColor;
                } else {
                    c = baseColor;
                }
                darker = !darker;
                label.setForeground(c);
            }
        });
    }

    private static class TimezoneComboBoxSupport {
        private final ArrayList<String> tzDisplayNames = new ArrayList<>();
        private final ArrayList<String> tzIDs = new ArrayList<>();
        private final JComboBox<String> tzComboBox;
        private final JTextField tzComboBoxTextField;
        private int selectedIndex = -1;

        TimezoneComboBoxSupport(JComboBox<String> tzComboBox) {
            this.tzComboBox = tzComboBox;
            this.tzComboBoxTextField = (JTextField) tzComboBox.getEditor().getEditorComponent();
            TimeZone defaultTz = TimeZone.getDefault();
            for (String tzID : TimeZone.getAvailableIDs()) {
                if (!(tzID.startsWith("Etc") || tzID.startsWith("SystemV"))) {
                    if (tzID.equals(defaultTz.getID()))
                        selectedIndex = tzIDs.size();
                    addTimeZone(TimeZone.getTimeZone(tzID));
                }
            }
            if (selectedIndex < 0) {
                selectedIndex = tzIDs.size();
                addTimeZone(TimeZone.getDefault());
            }
            tzComboBox.setSelectedIndex(selectedIndex);

            tzComboBoxTextField.addActionListener(e -> doSearch());

            tzComboBoxTextField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    doSearch();
                    super.focusLost(e);
                }

                @Override
                public void focusGained(FocusEvent e) {
                    tzComboBoxTextField.selectAll();
                    super.focusGained(e);
                }
            });
        }

        private void addTimeZone(TimeZone tz) {
            int offset = tz.getRawOffset() / 1000;
            int hour = offset / 3600;
            int minutes = (offset % 3600) / 60;
            String tzDisplayName = String.format("(GMT%+03d:%02d) %s", hour, minutes, tz.getID().replace("_", " "));
            tzComboBox.addItem(tzDisplayName);
            tzDisplayNames.add(tzDisplayName);
            tzIDs.add(tz.getID());
        }

        private String getSelectedTimezoneID() {
            return tzIDs.get(selectedIndex);
        }

        private void doSearch() {
            String searchText = tzComboBoxTextField.getText().toLowerCase();
            for (int i = 0; i < tzDisplayNames.size(); i++) {
                String tzDisplayName = tzDisplayNames.get(i).toLowerCase();
                if (tzDisplayName.contains(searchText) || searchText.contains(tzDisplayName)) {
                    selectedIndex = i;
                    break;
                }
            }
            tzComboBox.setSelectedIndex(selectedIndex);
            tzComboBoxTextField.setText(tzDisplayNames.get(selectedIndex));
            tzComboBoxTextField.selectAll();
        }
    }

    enum IpfMode {
        WEB_SERVICE,
        LOCAL_FILE,
        NO_IPF
    }
}
