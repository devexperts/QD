// JDMK API STUB

package com.sun.jdmk.comm;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.NotificationBroadcaster;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

public abstract class CommunicatorServer
    implements /*Runnable,*/ MBeanRegistration, NotificationBroadcaster, CommunicatorServerMBean
{
    public CommunicatorServer(int var1) throws IllegalArgumentException {}

    public void start() {}

    public void stop() {}

    public boolean isActive() { return false; }

    public boolean waitState(int var1, long var2) { return false; }

    public int getState() { return 0; }

    public String getStateString() { return null; }

    public String getHost() { return null; }

    public int getPort() { return 0; }

    public void setPort(int var1) throws IllegalStateException {}

    public abstract String getProtocol();

    protected abstract void doError(Exception var1) throws CommunicationException;

    protected abstract void doBind() throws CommunicationException, InterruptedException;

    protected abstract void doReceive() throws CommunicationException, InterruptedException;

    protected abstract void doProcess() throws CommunicationException, InterruptedException;

    protected abstract void doUnbind() throws CommunicationException, InterruptedException;

    public void addNotificationListener(NotificationListener var1, NotificationFilter var2, Object var3)
        throws IllegalArgumentException {}

    public void removeNotificationListener(NotificationListener var1) throws ListenerNotFoundException {}

    public MBeanNotificationInfo[] getNotificationInfo() { return null; }

    public ObjectName preRegister(MBeanServer var1, ObjectName var2) throws Exception { return null; }

    public void postRegister(Boolean var1) {}

    public void preDeregister() throws Exception {}

    public void postDeregister() {}

    // public MBeanServer getMBeanServer() { return null; }
    // public void setMBeanServer(MBeanServer var1) throws IllegalArgumentException, IllegalStateException { }
    // ObjectName getObjectName() { return null; }
    // void changeState(int var1) { }
    // String makeDebugTag() { return null; }
    // String makeThreadName() { return null; }
    // int getServedClientCount() { return 0; }
    // int getActiveClientCount() { return 0; }
    // int getMaxActiveClientCount() { return 0; }
    // void setMaxActiveClientCount(int var1) throws IllegalStateException { }
    // void notifyClientHandlerCreated(ClientHandler var1) { }
    // void notifyClientHandlerDeleted(ClientHandler var1) { }
    // public void run() { }
}
