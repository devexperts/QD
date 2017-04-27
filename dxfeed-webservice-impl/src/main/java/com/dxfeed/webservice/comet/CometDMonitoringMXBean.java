package com.dxfeed.webservice.comet;

import com.devexperts.annotation.Description;

public interface CometDMonitoringMXBean {

    @Description("Current number of sessions")
    public int getNumSessions();

    @Description("Flag indicating whether sessions statistics are reported")
    public boolean isDetailed();

    public void setDetailed(boolean detailed);

    @Description("Dump all sessions statistics averaged by total running time")
    public String dumpAllSessionsAverage();

    @Description("Dump sessions statistics averaged by total running time")
    public String dumpSessionsAverage(
        @Description("Order by column ('id', 'queue', 'read_mps', 'read', 'write_mps', 'write', 'time, 'inactivity')")
        String sortColumn,
        @Description("Limit number of sessions")
        int limit);

    @Description("Dump all sessions statistics (total values)")
    public String dumpAllSessionsTotal();

    @Description("Dump sessions statistics (total values)")
    public String dumpSessionsTotal(
        @Description("Order by column ('id', 'queue', 'read_mps', 'read', 'write_mps', 'write', 'time, 'inactivity')")
        String sortColumn,
        @Description("Limit number of sessions")
        int limit);

    @Description("Terminate session by ID")
    public void terminateSession(
        @Description("Session ID") String sessionId);
}
