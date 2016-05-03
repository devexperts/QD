/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorListener;
import com.devexperts.qd.util.SymbolSet;
import com.devexperts.services.Services;
import com.devexperts.tools.*;
import com.devexperts.util.InvalidFormatException;

public class QDTools extends Tools {
	private static final List<Class<? extends AbstractQDTool>> TOOLS = Services.loadServiceClasses(AbstractQDTool.class, null);

	private static class ToolArgs {
		private final AbstractTool tool;
		private final String[] args;

		ToolArgs(AbstractTool tool, String[] args) {
			this.tool = tool;
			this.args = args;
		}

		public void parse() {
			tool.parse(args);
		}

		void execute() {
			tool.execute();
		}
	}

	// It is designed to be used by tests.
	public static boolean invoke(String... args) {
		try {
			List<QDTools.ToolArgs> tools = new ArrayList<>();
			int i = 0;
			for (int j = 0; j <= args.length; j++) {
				if ((j >= args.length) || (args[j].equals("+"))) {
					if (i == j) {
						if (j == args.length) {
							throw new BadToolParametersException("Wrong format: '+' at the end of arguments list");
						} else {
							throw new BadToolParametersException("Wrong format: two consecutive '+' in arguments list");
						}
					}
					String[] singleToolArgs = new String[j - i - 1];
					System.arraycopy(args, i + 1, singleToolArgs, 0, singleToolArgs.length);
					String toolName = args[i];
					AbstractQDTool tool = getTool(toolName);
					if (tool == null)
						throw new BadToolParametersException("Unknown tool \"" + toolName + "\"");
					tools.add(new QDTools.ToolArgs(tool, singleToolArgs));
					i = j + 1;
				}
			}
			// parse all options first
			for (QDTools.ToolArgs ta : tools)
				try {
					ta.parse();
				} catch (Throwable t) {
					handleToolError(ta, t);
					return false;
				}
			// during parsing we might have modified System Properties.
			// Now we can initialize all startups
			Services.startup();
			// then execute all tools
			for (QDTools.ToolArgs ta : tools)
				try {
					ta.execute();
				} catch (Throwable t) {
					handleToolError(ta, t);
					return false;
				}
			// wait while tools have active connections
			// Note: this wait may be interrupted (will return from it with interruption flag set)
			boolean waitAgainToMakeSure;
			do {
				waitAgainToMakeSure = false;
				for (QDTools.ToolArgs ta : tools)
					if (waitWhileActive(((AbstractQDTool) ta.tool).mustWaitWhileActive()))
						waitAgainToMakeSure = true;
			} while (waitAgainToMakeSure);
			// wait for thread(s) to finish if needed
			// Note: this wait may be interrupted (will return from it with interruption flag set)
			for (QDTools.ToolArgs ta : tools)
				waitForThread(ta.tool.mustWaitForThread());
			// clean up tools (close all their resources)
			for (QDTools.ToolArgs ta : tools)
				closeOnExit(ta.tool.closeOnExit());
		} catch (Throwable t) {
			QDLog.log.error(t.toString(), t);
			return false;
		}
		return true; // success
	}

	private static void handleToolError(QDTools.ToolArgs ta, Throwable t) {
		String name = ta.tool.getClass().getSimpleName();
		if (t instanceof NoArgumentsException) {
			// Special signal to print detailed help
			System.err.println(name + ": " + ta.tool.generateHelpSummary(QDHelpProvider.DEFAULT_WIDTH));
			System.err.println("Use \"com.devexperts.tools.Help " + name + "\" for more detailed help.");
		} else if (t instanceof InvalidFormatException) {
			// Log with stack trace first, then show help message on the screen as last line
			// See [QD-251] Better logging when QD-filter can't be created/loaded
			QDLog.log.error(t.getMessage(), t);
			System.err.println();
			System.err.println(name + ": " + t.getMessage());
			System.err.println("Use \"com.devexperts.tools.Help " + name + "\" for usage info.");
		} else
			QDLog.log.error(t.toString(), t);
	}

	private static boolean waitWhileActive(List<MessageConnector> messageConnectors) {
		if (messageConnectors == null)
			return false;
		boolean waitAgainToMakeSure = false;
		for (MessageConnector connector : messageConnectors)
			if (connector.isActive()) {
				Waiter waiter = new Waiter(connector);
				connector.addMessageConnectorListener(waiter);
				waiter.waitWhileActive();
				if (Thread.currentThread().isInterrupted())
					return false;
				connector.removeMessageConnectorListener(waiter);
				waitAgainToMakeSure = true;
			}
		return waitAgainToMakeSure;
	}

	private static void waitForThread(Thread thread) {
		if (thread != null)
			try {
				thread.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // reassert interruption flag
			}
	}

	private static void closeOnExit(List<Closeable> list) {
		if (list == null)
			return;
		for (Closeable closeable : list) {
			QDLog.log.info("Closing " + closeable);
			try {
				closeable.close();
			} catch (IOException e) {
				QDLog.log.error("Failed to close " + closeable, e);
			}
		}
	}

	/**
	 * Creates and returns new instance of a tool with specified name.
	 * @param name tool name.
	 * @return new instance of a tool with specified name, or null if couldn't
	 * find such tool or failed to create instance.
	 */
	public static AbstractQDTool getTool(String name) {
		for (Class<? extends AbstractQDTool> tool : TOOLS) {
			if (name.equalsIgnoreCase(tool.getSimpleName()))
				try {
					return tool.newInstance();
				} catch (InstantiationException e) {
					return null;
				} catch (IllegalAccessException e) {
					return null;
				}
		}
		return null;
	}

	//======== Some static util methods ========

	public static DataRecord[] parseRecords(String recordList, DataScheme scheme) {
		RecordOnlyFilter filter = RecordOnlyFilter.valueOf(recordList, scheme);
		List<DataRecord> result = new ArrayList<>();
		for (int i = 0, n = scheme.getRecordCount(); i < n; i++) {
			DataRecord record = scheme.getRecord(i);
			if (filter.acceptRecord(record))
				result.add(record);
		}
		return result.toArray(new DataRecord[result.size()]);
	}

	public static String[] parseSymbols(String symbolList, DataScheme scheme) {
		SymbolSet set = SymbolSetFilter.valueOf(symbolList, scheme).getSymbolSet();
		final List<String> result = new ArrayList<>();
		final SymbolCodec codec = scheme.getCodec();
		set.examine(new SymbolReceiver() {
			@Override
			public void receiveSymbol(int cipher, String symbol) {
				result.add(codec.decode(cipher, symbol));
			}
		});
		return result.toArray(new String[result.size()]);
	}

	private static class Waiter implements MessageConnectorListener {
		private final MessageConnector connector;
		private final Thread waitingThread;

		Waiter(MessageConnector connector) {
			this.connector = connector;
			this.waitingThread = Thread.currentThread();
		}

		@Override
		public void stateChanged(MessageConnector connector) {
			if (!this.connector.isActive())
				LockSupport.unpark(waitingThread);
		}

		public void waitWhileActive() {
			while (connector.isActive() && !Thread.currentThread().isInterrupted())
				LockSupport.parkNanos(1000000000L);
		}
	}
}
