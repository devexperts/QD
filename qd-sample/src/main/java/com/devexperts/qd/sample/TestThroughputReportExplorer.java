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
package com.devexperts.qd.sample;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.DefaultButtonModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;

public class TestThroughputReportExplorer {
    private static final Pattern SYSTEM_PATTERN = Pattern.compile("SYSTEM: (.+) \\{(.+)\\}");
    private static final Pattern CONFIG_PATTERN = Pattern.compile("CONFIG: (\\w+) = (\\S+)");
    private static final Pattern RESULT_PATTERN = Pattern.compile("\\s*(\\w+): .* \\[\\d+ - (\\d+) - \\d+\\] .*");

    private static final String ANY_FILTER = "*";

    public static void main(String[] args) throws IOException {
        new TestThroughputReportExplorer().go(args.length <= 0 ? "throughput.report" : args[0]);
    }

    private void go(String reportName) throws IOException {
        Report report = parseFile(reportName);
        JFrame frame = makeFrame(report);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.pack();
        frame.setVisible(true);
    }

    private JFrame makeFrame(Report report) {
        JFrame frame = new JFrame("TestThroughputReportExplorer");
        FilterModel fm = new FilterModel(report);
        frame.add(makePropGrid(fm), BorderLayout.CENTER);
        frame.add(new FileWritePanel(fm), BorderLayout.SOUTH);
        return frame;
    }

    private Component makePropGrid(FilterModel fm) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 5, 1, 5);
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.EAST;
        panel.add(crateHeaderLabel("KEY"), gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(crateHeaderLabel("FILTER"), gbc);
        gbc.gridx = 2;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(crateHeaderLabel("VALUE"), gbc);

        for (String key : fm.props.keySet()) {
            gbc.gridy++;
            gbc.gridx = 0;
            gbc.anchor = GridBagConstraints.EAST;
            panel.add(createPlainLabel(key), gbc);
            gbc.gridx = 1;
            gbc.anchor = GridBagConstraints.CENTER;
            panel.add(createFilterToggle(key, fm), gbc);
            gbc.gridx = 2;
            gbc.anchor = GridBagConstraints.WEST;
            panel.add(createValueSelection(key, fm), gbc);
        }
        panel.setBorder(createSectionBorder(2, 2, 1, 2));
        return panel;
    }

    private static Border createSectionBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(top, left, bottom, right),
            BorderFactory.createEtchedBorder());
    }

    private JLabel createPlainLabel(String key) {
        JLabel label = new JLabel(key);
        label.setFont(label.getFont().deriveFont(0));
        return label;
    }

    private Component crateHeaderLabel(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 2));
        return label;
    }

    private Component createFilterToggle(String key, FilterModel fm) {
        Set<String> values = fm.props.get(key);
        if (values.size() == 1)
            return new JLabel(""); // empty
        JCheckBox checkBox = new JCheckBox("");
        checkBox.setModel(new ItemFilterToggleModel(key, fm));
        return checkBox;
    }

    private Component createValueSelection(String key, FilterModel fm) {
        Set<String> values = fm.props.get(key);
        if (values.size() == 1)
            return createPlainLabel(values.iterator().next());
        JComboBox comboBox = new JComboBox(new ItemFilterValuesModel(key, fm));
        comboBox.setPreferredSize(comboBox.getPreferredSize());
        return comboBox;
    }

    private Report parseFile(String reportName) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(reportName));
        try {
            return parseStream(in);
        } finally {
            in.close();
        }
    }

    private Report parseStream(BufferedReader in) throws IOException {
        Report report = new Report();
        Item item = null;
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("SYSTEM:")) {
                // new item
                item = new Item();
                parseSystemLine(line, item);
            }
            if (line.startsWith("CONFIG:"))
                parseConfigLine(line, item);
            if (line.startsWith(" DIST:") || line.startsWith("AGENT:"))
                parseResultLine(line, item);
            if (line.startsWith("  SUM:")) {
                //parseResultLine(line, item); // don't need sum
                item.map.put("CEIL RPS",
                    String.valueOf(Long.parseLong(item.map.get("agents")) *
                        Long.parseLong(item.map.get("DIST RPS"))));
                // last line for this item
                report.items.add(item);
                item = null;
            }
        }
        return report;
    }

    private void parseSystemLine(String line, Item item) {
        Matcher m = SYSTEM_PATTERN.matcher(line);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid line: " + line);
        item.map.put("version", m.group(1));
        Scanner scan = new Scanner(m.group(2));
        scan.useDelimiter(", ");
        while (scan.hasNext()) {
            String[] kv = scan.next().split("=", 2);
            if (kv.length != 2)
                throw new IllegalArgumentException("Invalid line: " + line);
            item.map.put(kv[0], kv[1]);
        }
    }

    private void parseConfigLine(String line, Item item) {
        Matcher m = CONFIG_PATTERN.matcher(line);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid line: " + line);
        item.map.put(m.group(1), m.group(2));
    }

    private void parseResultLine(String line, Item item) {
        Matcher m = RESULT_PATTERN.matcher(line);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid line: " + line);
        item.map.put(m.group(1) + " RPS", m.group(2));
    }

    static class Item {
        final Map<String, String> map = new LinkedHashMap<String, String>();

        boolean matches(Map<String, String> filter) {
            for (Map.Entry<String, String> fentry : filter.entrySet())
                if (fentry.getValue() != null && !fentry.getValue().equals(map.get(fentry.getKey())))
                    return false;
            return true;
        }
    }

    static class Report {
        final List<Item> items = new ArrayList<Item>();

        Map<String, Set<String>> refilter(Map<String, String> filter) {
            Map<String, Set<String>> props = new LinkedHashMap<String, Set<String>>();
            for (Item item : items)
                if (item.matches(filter))
                    for (Map.Entry<String, String> ientry : item.map.entrySet()) {
                        String key = ientry.getKey();
                        String value = ientry.getValue();
                        Set<String> vset = props.get(key);
                        if (vset == null)
                            props.put(key, vset = new LinkedHashSet<String>());
                        vset.add(value);
                    }
            return props;
        }
    }

    static class FilterModel {
        final Report report;
        final Map<String, String> filter = new HashMap<String, String>();
        final List<ItemFilterToggleModel> itemToggleModels = new ArrayList<ItemFilterToggleModel>();
        final List<ItemFilterValuesModel> itemValuesModels = new ArrayList<ItemFilterValuesModel>();
        final Map<String, Set<String>> allProps;
        Map<String, Set<String>> props;

        FilterModel(Report report) {
            this.report = report;
            allProps = props = report.refilter(filter);
        }

        void setFilter(String key, String value) {
            filter.put(key, value);
            props = report.refilter(filter);
            for (ItemFilterToggleModel ifm : itemToggleModels)
                ifm.setSelected(filter.get(ifm.key) != null);
            for (ItemFilterValuesModel ifm : itemValuesModels)
                if (!ifm.key.equals(key))
                    ifm.rebuild();
        }
    }

    static class ItemFilterToggleModel extends DefaultButtonModel {
        final String key;
        final FilterModel fm;

        public ItemFilterToggleModel(String key, FilterModel fm) {
            this.key = key;
            this.fm = fm;
            fm.itemToggleModels.add(this);
        }
    }

    static class ItemFilterValuesModel extends DefaultComboBoxModel {
        boolean rebuilding;
        final String key;
        final FilterModel fm;

        ItemFilterValuesModel(String key, FilterModel fm) {
            super(getItems(fm.props.get(key)).toArray());
            this.key = key;
            this.fm = fm;
            fm.itemValuesModels.add(this);
        }

        void rebuild() {
            rebuilding = true;
            removeAllElements();
            String filtered = fm.filter.get(key);
            Set<String> values = filtered == null ?
                fm.props.get(key) : fm.allProps.get(key);
            List<String> items = getItems(values);
            for (String item : items)
                addElement(item);
            setSelectedItem(
                filtered != null ? filtered :
                (values.size() == 1 ? values.iterator().next() : items.iterator().next()));
            rebuilding = false;
        }

        static List<String> getItems(Set<String> values) {
            List<String> items = new ArrayList<String>();
            items.add(ANY_FILTER);
            items.addAll(values);
            return items;
        }

        @Override
        public void setSelectedItem(Object item) {
            super.setSelectedItem(item);
            if (!rebuilding)
                fm.setFilter(key, ANY_FILTER == item ? null : item.toString());
        }

    }

    enum Format {
        WIKI("||", "||", "|", "|"),
        CSV("", ",", "", ",");

        String thAround;
        String thBetween;
        String trAround;
        String trBetween;

        Format(String thAround, String thBetween, String trAround, String trBetween) {
            this.thAround = thAround;
            this.thBetween = thBetween;
            this.trAround = trAround;
            this.trBetween = trBetween;
        }
    }

    static class FileWritePanel extends JPanel implements ActionListener {
        final FilterModel fm;
        final JButton writeButton;
        final JTextField fileName;
        final JComboBox fileFormat;

        FileWritePanel(FilterModel fm) {
            this.fm = fm;
            add(writeButton = new JButton("Write"));
            add(new JLabel("file"));
            add(fileName = new JTextField(20));
            add(new JLabel("format"));
            add(fileFormat = new JComboBox(new Object[] { Format.WIKI , Format.CSV}));
            fileName.setText("report.out");
            writeButton.addActionListener(this);
            setBorder(createSectionBorder(1, 2, 2, 2));
        }

        public void actionPerformed(ActionEvent e) {
            writeToFile(fileName.getText(), (Format) fileFormat.getSelectedItem());
        }

        private void writeToFile(String fileName, Format fileFormat) {
            try {
                PrintWriter out = new PrintWriter(new FileWriter(fileName));
                try {
                    writeToStream(out, fileFormat);
                } finally {
                    out.close();
                }
            } catch (IOException e) {
                JOptionPane.showConfirmDialog(this, e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void writeToStream(PrintWriter out, Format fileFormat) {
            ArrayList<String> keys = new ArrayList<String>();
            for (Map.Entry<String, Set<String>> entry : fm.props.entrySet())
                if (entry.getValue().size() > 1)
                    keys.add(entry.getKey());
            out.print(fileFormat.thAround);
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0)
                    out.print(fileFormat.thBetween);
                out.print(keys.get(i));
            }
            out.println(fileFormat.thAround);
            for (Item item : fm.report.items)
                if (item.matches(fm.filter)) {
                    out.print(fileFormat.trAround);
                    for (int i = 0; i < keys.size(); i++) {
                        if (i > 0)
                            out.print(fileFormat.trBetween);
                        out.print(item.map.get(keys.get(i)));
                    }
                    out.println(fileFormat.trAround);
                }
        }
    }
}
