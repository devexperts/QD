// JDMK API STUB

package com.sun.jdmk.comm;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistration;
import javax.management.ReflectionException;

public class HtmlAdaptorServer extends CommunicatorServer implements MBeanRegistration, DynamicMBean {
    public HtmlAdaptorServer() { super(0); }

    public HtmlAdaptorServer(int var1) { super(0); }

    public HtmlAdaptorServer(int var1, AuthInfo[] var2) { super(0); }

    public String getProtocol() { return null; }

    public String getLastConnectedClient() { return null; }

    public int getServedClientCount() { return 0; }

    public int getActiveClientCount() { return 0; }

    public int getMaxActiveClientCount() { return 0; }

    public void setMaxActiveClientCount(int var1) throws IllegalStateException {}

    public void addUserAuthenticationInfo(AuthInfo var1) {}

    public void removeUserAuthenticationInfo(AuthInfo var1) {}

    public boolean isAuthenticationOn() { return false; }

    public MBeanInfo getMBeanInfo() { return null; }

    public Object getAttribute(String var1) throws AttributeNotFoundException, MBeanException, ReflectionException {
        return null;
    }

    public AttributeList getAttributes(String[] var1) { return null; }

    public Object invoke(String var1, Object[] var2, String[] var3) throws MBeanException, ReflectionException {
        return null;
    }

    public void setAttribute(Attribute var1)
        throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {}

    public AttributeList setAttributes(AttributeList var1) { return null; }

    protected void doError(Exception var1) throws CommunicationException {}

    protected void doBind() throws CommunicationException, InterruptedException {}

    protected void doUnbind() throws CommunicationException, InterruptedException {}

    protected void doReceive() throws CommunicationException, InterruptedException {}

    protected void doProcess() throws CommunicationException, InterruptedException {}

    // public void stop() { }
    // public ObjectName preRegister(MBeanServer var1, ObjectName var2) throws Exception { return null; }
    // public void postRegister(Boolean var1) { }
    // public void preDeregister() throws Exception { }
    // public void postDeregister() { }
    // public void setParser(ObjectName var1) throws InstanceNotFoundException, ServiceNotFoundException { }
    // public ObjectName getParser() { return null; }
    // public void resetParser() { }
    // public void createParser(String var1, String var2, String var3)
    //     throws MalformedObjectNameException, ReflectionException, InstanceAlreadyExistsException,
    //     MBeanRegistrationException, MBeanException, NotCompliantMBeanException, InstanceNotFoundException { }
    // boolean checkChallengeResponse(String var1) { return false; }
    // String makeDebugTag() { return null; }
}
