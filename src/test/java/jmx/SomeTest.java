package jmx;

import org.junit.Test;

import javax.management.*;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by kevin on 30/03/2018.
 */
public class SomeTest {

    JMXConnector jmxConnector;
    MBeanServerConnection beanConn;

    @Test
    public void doTest() throws IOException, MalformedObjectNameException {

        beanConn = mock(MBeanServerConnection.class);
        jmxConnector = mock(JMXConnector.class);

        Set<ObjectInstance> objectInstances = JMXDataProviderUtil.getSetWithClusterObjInstance();
        when(beanConn.queryMBeans(eq(null), eq(null))).thenReturn(objectInstances);
        when(jmxConnector.getMBeanServerConnection()).thenReturn(beanConn);
        when(beanConn.queryMBeans(any(ObjectName.class),any(QueryExp.class))).thenReturn(objectInstances);

        Set<ObjectInstance> allObjects = beanConn.queryMBeans(null, null);
        for (ObjectInstance instance : allObjects) {
            System.out.println("instance: " + instance);
        }


    }



}
