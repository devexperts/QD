/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.samples;

import java.awt.*;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.List;
import javax.swing.*;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.socket.SocketMessageAdapterFactory;
import com.devexperts.qd.sample.*;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;

public class SampleClient {
	public static void main(String[] args) {
		initClient(args.length <= 0 ? "127.0.0.1:5555" : args[0], 1236);
	}

	public static final String[] symbols = {
		"SPX", "NDX", "DJX", "QQQ", "XEO",
		"C", "T", "IBM", "BEA", "AOL",
		"MSFT", "INTC", "SUNW", "ORCL", "YHOO",
		"/LNY", "/SOY", "/GS3U", "/SP3UG", "Very Long"
	};

	static void initClient(String address, int jmxHtmlPort) {
		DataScheme scheme = SampleScheme.getInstance();
		QDEndpoint endpoint = QDEndpoint.newBuilder()
			.withName("client")
			.withScheme(scheme)
			.withContracts(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
			.withProperties(Sample.getMonitoringProps(jmxHtmlPort))
			.build();
		endpoint.getStream().setEnableWildcards(true);

		// Client QD connection.
		ClientAdapterFactory factory = new ClientAdapterFactory(endpoint);
//		endpoint.addConnectors(MessageConnectors.createMessageConnectors(
//			factory, address, endpoint.getRootStats()));
		RMIEndpoint clientRMI = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, endpoint, factory, null);
		endpoint.initializeConnectorsForAddress(address);
		endpoint.startConnectors();

		endpoint.startConnectors();

		// Ticker GUI creation.
		List<String> ticker_symbols = new ArrayList<>(Arrays.asList(symbols));
		TickerModel ticker_model = new TickerModel(endpoint.getTicker(), createColumns(new DataRecord[] {
			scheme.getRecord(0), scheme.getRecord(1) }), ticker_symbols.toArray(new String[ticker_symbols.size()]));
		JPanel ticker_panel = new JPanel(new BorderLayout());
		ticker_panel.add(new ActivatorCheckBox(ticker_model), BorderLayout.SOUTH);
		ticker_panel.add(new JLabel("Ticker"), BorderLayout.NORTH);
		ticker_panel.add(new JScrollPane(new JTable(ticker_model)), BorderLayout.CENTER);

		// Stream GUI creation.
		StreamModel stream_model = new StreamModel(endpoint.getStream(), createColumns(scheme.getRecord(1)), symbols);
		JPanel stream_panel = new JPanel(new BorderLayout());
		stream_panel.add(new ActivatorCheckBox(stream_model), BorderLayout.SOUTH);
		stream_panel.add(new JLabel("Stream subscribe by list"), BorderLayout.NORTH);
		stream_panel.add(new JScrollPane(new JTable(stream_model)), BorderLayout.CENTER);

		// Stream GUI creation 2
		StreamModel stream_model2 = new StreamModel(endpoint.getStream(), createColumns(scheme.getRecord(1)), new String[] {"*"});
		JPanel stream_panel2 = new JPanel(new BorderLayout());
		stream_panel2.add(new ActivatorCheckBox(stream_model2), BorderLayout.SOUTH);
		stream_panel2.add(new JLabel("Stream subscribe by *"), BorderLayout.NORTH);
		stream_panel2.add(new JScrollPane(new JTable(stream_model2)), BorderLayout.CENTER);

		// History GUI creation.
		HistoryModel history_model = new HistoryModel(endpoint.getHistory(), createColumns(scheme.getRecord(2)), symbols[0]);
		JPanel history_panel = new JPanel(new BorderLayout());
		history_panel.add(new ActivatorCheckBox(history_model), BorderLayout.SOUTH);
		history_panel.add(new JLabel("History for " + symbols[0]), BorderLayout.NORTH);
		history_panel.add(new JScrollPane(new JTable(history_model)), BorderLayout.CENTER);

		// Frame packaging.
		JFrame frame = new JFrame();
		JPanel panel = new JPanel();
		panel.setLayout(new GridLayout(2, 2));
		panel.add(ticker_panel);
		panel.add(stream_panel);
		panel.add(stream_panel2);
		panel.add(history_panel);
		frame.getContentPane().add(panel);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(1000, 800);
		frame.setVisible(true);
	}

	private static GUIColumn[] createColumns(DataRecord record) {
		return createColumns(new DataRecord[] { record });
	}

	private static GUIColumn[] createColumns(DataRecord[] records) {
		ArrayList<GUIColumn> result = new ArrayList<>();
		for (DataRecord record : records) {
			int nint = record.getIntFieldCount();
			int nobj = record.getObjFieldCount();
			for (int j = 0; j < nint; j++)
				result.add(new SampleColumn.IntColumn(record.getIntField(j)));
			for (int j = 0; j < nobj; j++)
				result.add(new SampleColumn.ObjColumn(record.getObjField(j)));
		}
		return result.toArray(new GUIColumn[result.size()]);
	}

	private static class ClientAdapterFactory extends DistributorAdapter.Factory
		implements SocketMessageAdapterFactory
	{

		private ClientAdapterFactory(QDEndpoint endpoint) {
			super(endpoint, null);
		}

		@Override
		public MessageAdapter createAdapterWithSocket(Socket socket, QDStats stats)
		throws SecurityException, IOException
		{
			// Send auth token to the other side.
			return createAdapter(stats);
		}

		@Override
		public MessageAdapter createAdapter(QDStats stats) {
			return new SwingDistributorAdapter(stats);
		}

		private class SwingDistributorAdapter extends DistributorAdapter {
			SwingDistributorAdapter(QDStats stats) {
				super(ClientAdapterFactory.this.endpoint, ticker, stream, history, null, stats);
			}

			@Override
			protected void processData(DataIterator iterator, final MessageType message) {
				final RecordBuffer buf = new RecordBuffer();
				buf.processData(iterator);
				SwingUtilities.invokeLater(() -> SwingDistributorAdapter.super.processData(buf, message));
			}
		}
	}
}
