/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.tools.test;

import java.io.*;

import com.devexperts.tools.Tools;
import junit.framework.TestCase;

/**
 * Tests simple transformations with instruments tool.
 */
public class InstrumentsTransformTest extends TestCase {
	private static final File IN_FILE = new File("InstrumentsTransformTest.in.ipf");
	private static final File OUT_FILE = new File("InstrumentsTransformTest.out.ipf");

	@Override
	protected void tearDown() throws Exception {
		IN_FILE.delete();
		OUT_FILE.delete();
	}

	public void testReplaceType() throws IOException {
		writeFile(IN_FILE,
			"#ETF::=TYPE,SYMBOL,DESCRIPTION,COUNTRY,OPOL,CURRENCY,TRADING_HOURS,BIST_ISSUE_SUBTYPE,BIST_STOCK_MARKET",
			"#OTHER::=TYPE,SYMBOL,COUNTRY,OPOL,CURRENCY,TRADING_HOURS,BIST_ISSUE_SUBTYPE,BIST_STOCK_MARKET",
			"#STOCK::=TYPE,SYMBOL,DESCRIPTION,COUNTRY,OPOL,CURRENCY,TRADING_HOURS,BIST_ISSUE_SUBTYPE,BIST_STOCK_MARKET",
			"STOCK,ACSEL:TR,ACIPAYAM SELULOZ,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,L",
			"STOCK,ADANA:TR,ADANA CIMENTO (A),TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"STOCK,ADBGR:TR,ADANA CIMENTO (B),TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"STOCK,ADEL:TR,ADEL KALEMCILIK,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"STOCK,ADESE:TR,ADESE ALISVERIS TICARET,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,L",
			"STOCK,ADNAC:TR,ADANA CIMENTO (C),TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"STOCK,AEFES:TR,ANADOLU EFES,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N");
		boolean ok = Tools.invoke("instruments",
			"-r", IN_FILE.toString(),
			"-t", "if (TYPE == \"STOCK\") TYPE = \"BLAHBLAH\";",
			"-w", OUT_FILE.toString());
		assertTrue(ok);
		checkFile(OUT_FILE,
			"#BLAHBLAH::=TYPE,SYMBOL,DESCRIPTION,COUNTRY,OPOL,CURRENCY,TRADING_HOURS,BIST_ISSUE_SUBTYPE,BIST_STOCK_MARKET",
			"BLAHBLAH,ACSEL:TR,ACIPAYAM SELULOZ,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,L",
			"BLAHBLAH,ADANA:TR,ADANA CIMENTO (A),TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"BLAHBLAH,ADBGR:TR,ADANA CIMENTO (B),TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"BLAHBLAH,ADEL:TR,ADEL KALEMCILIK,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"BLAHBLAH,ADESE:TR,ADESE ALISVERIS TICARET,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,L",
			"BLAHBLAH,ADNAC:TR,ADANA CIMENTO (C),TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N",
			"BLAHBLAH,AEFES:TR,ANADOLU EFES,TR,XIST,TRY,BIST(name=BIST;tz=Asia/Istanbul;hd=US;sd=US;td=12345;de=+0000;0=0915123014001740),E,N");
	}

	private void writeFile(File file, String... lines) throws IOException {
		try (PrintWriter out = new PrintWriter(file)) {
			for (String line : lines)
				out.println(line);
		}
	}

	private void checkFile(File file, String... lines) throws IOException {
		try (BufferedReader in = new BufferedReader(new FileReader(file))) {
			for (String line : lines)
				assertEquals(line, in.readLine());
			assertEquals(null, in.readLine());
		}
	}
}
