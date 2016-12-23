/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.connector.codec.ssl;

import java.io.IOException;
import java.security.*;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.net.ssl.*;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.*;
import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.SystemProperties;

import static com.devexperts.util.SystemProperties.getIntProperty;

public class SSLConnectionFactory extends CodecConnectionFactory {
	private static Executor defaultExecutor;

	private static final ExecutorProvider DEFAULT_EXECUTOR_PROVIDER =
		createExecutorProvider(getIntProperty("com.devexperts.qd.qtp.ssl.executorThreadsNumber",
			Runtime.getRuntime().availableProcessors()));

	@Nonnull
	private static ExecutorProvider createExecutorProvider(int nThreads) {
		return new ExecutorProvider(nThreads, "SSLTasksExecutor", Logging.getLogging(SSLConnection.class));
	}

	private final SSLContext context;

	private String keyStore = SystemProperties.getProperty("javax.net.ssl.keyStore", null);
	private String keyStorePassword = SystemProperties.getProperty("javax.net.ssl.keyStorePassword", null);
	private String keyStoreProvider = SystemProperties.getProperty("javax.net.ssl.keyStoreProvider", null);
	private String keyStoreType = SystemProperties.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
	private String trustStore = SystemProperties.getProperty("javax.net.ssl.trustStore", null);
	private String trustStorePassword = SystemProperties.getProperty("javax.net.ssl.trustStorePassword", null);
	private String trustStoreProvider = SystemProperties.getProperty("javax.net.ssl.trustStoreProvider", null);
	private String trustStoreType = SystemProperties.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());

	private boolean isServer = false;
	private boolean needClientAuth;
	private boolean wantClientAuth;

	private int taskThreads;
	private Executor taskExecutor;
	private ExecutorProvider taskExecutorProvider;

	private volatile boolean isInitialized;

	SSLConnectionFactory(ApplicationConnectionFactory delegate) {
		super(delegate);
		try {
			context = SSLContext.getInstance("TLS");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	public String getKeyStore() {
		return keyStore;
	}

	@Configurable
	public void setKeyStore(String keyStore) {
		this.keyStore = keyStore;
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	@Configurable
	public void setKeyStorePassword(String keyStorePassword) {
		this.keyStorePassword = keyStorePassword;
	}

	public String getKeyStoreProvider() {
		return keyStoreProvider;
	}

	@Configurable
	public void setKeyStoreProvider(String keyStoreProvider) {
		this.keyStoreProvider = keyStoreProvider;
	}

	public String getKeyStoreType() {
		return keyStoreType;
	}

	@Configurable
	public void setKeyStoreType(String keyStoreType) {
		this.keyStoreType = keyStoreType;
	}

	public String getTrustStore() {
		return trustStore;
	}

	@Configurable
	public void setTrustStore(String trustStore) {
		this.trustStore = trustStore;
	}

	public String getTrustStorePassword() {
		return trustStorePassword;
	}

	@Configurable
	public void setTrustStorePassword(String trustStorePassword) {
		this.trustStorePassword = trustStorePassword;
	}

	public String getTrustStoreProvider() {
		return trustStoreProvider;
	}

	@Configurable
	public void setTrustStoreProvider(String trustStoreProvider) {
		this.trustStoreProvider = trustStoreProvider;
	}

	public String getTrustStoreType() {
		return trustStoreType;
	}

	@Configurable
	public void setTrustStoreType(String trustStoreType) {
		this.trustStoreType = trustStoreType;
	}

	public boolean isServer() {
		return isServer;
	}

	@Configurable(name = "isServer")
	public void setServer(boolean server) {
		isServer = server;
	}

	public boolean isNeedClientAuth() {
		return needClientAuth;
	}

	@Configurable
	public void setNeedClientAuth(boolean needClientAuth) {
		this.needClientAuth = needClientAuth;
	}

	public boolean isWantClientAuth() {
		return wantClientAuth;
	}

	@Configurable
	public void setWantClientAuth(boolean wantClientAuth) {
		this.wantClientAuth = wantClientAuth;
	}

	public int getTaskThreads() {
		return taskThreads;
	}

	@Configurable
	public synchronized void setTaskThreads(int taskThreads) {
		this.taskThreads = taskThreads;
	}

	public Executor getTaskExecutor() {
		return taskExecutor;
	}

	@Configurable
	public synchronized void setTaskExecutor(Executor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	private synchronized ExecutorProvider getExecutorProvider() {
		if (taskExecutorProvider != null)
			return taskExecutorProvider;
		if (taskExecutor != null)
			return taskExecutorProvider = new ExecutorProvider(taskExecutor);
		if (taskThreads > 0)
			return taskExecutorProvider = createExecutorProvider(taskThreads);
		return DEFAULT_EXECUTOR_PROVIDER;
	}

	private synchronized void init() throws GeneralSecurityException, IOException {
		KeyManagerFactory keyManagerFactory = null;
		if (keyStore != null) {
			KeyStore keyStore = getKeyStore(keyStoreType, keyStoreProvider, this.keyStore, keyStorePassword);
			keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keyStore, keyStorePassword == null ? null : keyStorePassword.toCharArray());
		}

		TrustManagerFactory trustManagerFactory = null;
		if (trustStore != null) {
			KeyStore trustStore = getKeyStore(trustStoreType, trustStoreProvider, this.trustStore, trustStorePassword);
			trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			trustManagerFactory.init(trustStore);
		}

		context.init(
			keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null,
			trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null, null);
		isInitialized = true;
	}

	private static KeyStore getKeyStore(String type, String provider, String url, String password) throws GeneralSecurityException, IOException {
		assert url != null;
		KeyStore result = provider == null ?
			KeyStore.getInstance(type) :
			KeyStore.getInstance(type, provider);
		try (URLInputStream in = new URLInputStream(url)) {
			result.load(in, password == null ? null : password.toCharArray());
		}
		return result;
	}

	@Override
	public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
		if (!isInitialized)
			try {
				init();
			} catch (GeneralSecurityException e) {
				throw new IOException("Failed to initialize ssl engine: " +  e.getMessage());
			}
		SSLEngine engine = context.createSSLEngine();
		if (isServer) {
			engine.setUseClientMode(false);
			engine.setWantClientAuth(needClientAuth);
			engine.setNeedClientAuth(wantClientAuth);
		} else
			engine.setUseClientMode(true);
		return new SSLConnection(getDelegate(), this, transportConnection, engine, getExecutorProvider().newReference());
	}

	public String toString() {
		return "ssl+" + getDelegate().toString();
	}
}
