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
package com.devexperts.connector.codec.ssl;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.URLInputStream;
import com.devexperts.logging.Logging;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import static com.devexperts.util.SystemProperties.getIntProperty;

public class SSLConnectionFactory extends CodecConnectionFactory {

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
    private String protocols = SystemProperties.getProperty("com.devexperts.connector.codec.ssl.protocols", null);
    private String cipherSuites = SystemProperties.getProperty("com.devexperts.connector.codec.ssl.cipherSuites", null);
    private TrustManager trustManager;
    private String[] protocolsArr;
    private String[] cipherSuitesArr;

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
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    @Configurable
    public void setKeyStorePassword(String keyStorePassword) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    @Configurable
    public void setKeyStoreProvider(String keyStoreProvider) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.keyStoreProvider = keyStoreProvider;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    @Configurable
    public void setKeyStoreType(String keyStoreType) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.keyStoreType = keyStoreType;
    }

    public String getTrustStore() {
        return trustStore;
    }

    @Configurable
    public void setTrustStore(String trustStore) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    @Configurable
    public void setTrustStorePassword(String trustStorePassword) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreProvider() {
        return trustStoreProvider;
    }

    @Configurable
    public void setTrustStoreProvider(String trustStoreProvider) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.trustStoreProvider = trustStoreProvider;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    @Configurable
    public void setTrustStoreType(String trustStoreType) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.trustStoreType = trustStoreType;
    }

    public boolean isServer() {
        return isServer;
    }

    @Configurable(name = "isServer")
    public void setServer(boolean server) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        isServer = server;
    }

    public boolean isNeedClientAuth() {
        return needClientAuth;
    }

    @Configurable
    public void setNeedClientAuth(boolean needClientAuth) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.needClientAuth = needClientAuth;
    }

    public boolean isWantClientAuth() {
        return wantClientAuth;
    }

    @Configurable
    public void setWantClientAuth(boolean wantClientAuth) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.wantClientAuth = wantClientAuth;
    }

    public int getTaskThreads() {
        return taskThreads;
    }

    @Configurable
    public void setTaskThreads(int taskThreads) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.taskThreads = taskThreads;
    }

    public Executor getTaskExecutor() {
        return taskExecutor;
    }

    @Configurable
    public void setTaskExecutor(Executor taskExecutor) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.taskExecutor = taskExecutor;
    }

    public String getProtocols() {
        return protocols;
    }

    @Configurable
    public void setProtocols(String protocols) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.protocols = protocols;
    }

    public String getCipherSuites() {
        return cipherSuites;
    }

    @Configurable
    public void setCipherSuites(String cipherSuites) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.cipherSuites = cipherSuites;
    }

    @Override
    public void setTrustManager(TrustManager trustManager) {
        if (isInitialized)
            throw new IllegalStateException("Factory has already been initialized.");
        this.trustManager = trustManager;
    }

    private ExecutorProvider getExecutorProvider() {
        if (taskExecutorProvider != null)
            return taskExecutorProvider;
        if (taskExecutor != null)
            return taskExecutorProvider = new ExecutorProvider(taskExecutor);
        if (taskThreads > 0)
            return taskExecutorProvider = createExecutorProvider(taskThreads);
        return DEFAULT_EXECUTOR_PROVIDER;
    }

    private void init() throws GeneralSecurityException, IOException {
        KeyManagerFactory keyManagerFactory = null;
        if (keyStore != null) {
            KeyStore keyStore = getKeyStore(keyStoreType, keyStoreProvider, this.keyStore, keyStorePassword);
            keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword == null ? null : keyStorePassword.toCharArray());
        }

        TrustManager[] trustManagers = null;
        if (trustManager != null) {
                trustManagers = new TrustManager[] {trustManager};
        } else if (trustStore != null){
            KeyStore trustStore = getKeyStore(trustStoreType, trustStoreProvider, this.trustStore, trustStorePassword);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }

        context.init(keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null,
            trustManagers, null);
        SSLEngine engine = context.createSSLEngine();

        if (protocols != null) {
            protocolsArr = protocols.trim().split(";");
            try {
                engine.setEnabledProtocols(protocolsArr);
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage() + " protocols are not supported. Available protocols: "
                    + Arrays.toString(engine.getSupportedProtocols()));
                throw new GeneralSecurityException(e);
            }
        }
        if (cipherSuites != null) {
            cipherSuitesArr = cipherSuites.trim().split(";");

            try {
                engine.setEnabledCipherSuites(cipherSuitesArr);
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage() + " cipher suites are not supported. Available cipher suites: "
                    + Arrays.toString(engine.getSupportedCipherSuites()));
                throw new GeneralSecurityException(e);
            }
        }
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
        if (protocolsArr != null)
            engine.setEnabledProtocols(protocolsArr);
        if (cipherSuitesArr != null)
            engine.setEnabledCipherSuites(cipherSuitesArr);
        engine.setUseClientMode(!isServer);
        if (isServer) {
            // SSLEngine javadoc says that the below 2 setters override each other to update common ternary state.
            // To best suit any engine implementation we always make calls to each of them in a proper order,
            // selected to enforce strictest auth requirement.
            if (needClientAuth) {
                engine.setWantClientAuth(wantClientAuth);
                engine.setNeedClientAuth(true);
            } else {
                engine.setNeedClientAuth(false);
                engine.setWantClientAuth(wantClientAuth);
            }
        }
        return new SSLConnection(getDelegate(), this, transportConnection, engine, getExecutorProvider().newReference());
    }

    public String toString() {
        return "ssl+" + getDelegate().toString();
    }
}
