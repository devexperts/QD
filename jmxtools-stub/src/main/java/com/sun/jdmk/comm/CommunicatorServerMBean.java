// JDMK API STUB

package com.sun.jdmk.comm;

public interface CommunicatorServerMBean {
    void start();

    void stop();

    boolean isActive();

    boolean waitState(int var1, long var2);

    int getState();

    String getStateString();

    String getHost();

    int getPort();

    void setPort(int var1) throws IllegalStateException;

    String getProtocol();
}
