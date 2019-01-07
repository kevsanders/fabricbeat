package jmx;

import org.junit.Test;

import javax.management.*;
import javax.management.remote.JMXConnector;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    public void doTest() throws IOException, MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException {

        beanConn = mock(MBeanServerConnection.class);
        jmxConnector = mock(JMXConnector.class);

        Set<ObjectInstance> objectInstances = JMXDataProviderUtil.getSetWithClusterObjInstance();
        when(beanConn.queryMBeans(eq(null), eq(null))).thenReturn(objectInstances);
        when(jmxConnector.getMBeanServerConnection()).thenReturn(beanConn);
        when(beanConn.queryMBeans(any(ObjectName.class),any(QueryExp.class))).thenReturn(objectInstances);

        {
            String[] names = new String[]{"Coherence:type=Cluster"};
            for (String name : names) {
                MBeanInfo mBeanInfo = getMBeanInfoForCluster(name);
                when(beanConn.getMBeanInfo(eq(new ObjectName(name)))).thenReturn(mBeanInfo);
                String[] attrNames = new String[]{"TaskBacklog"};
                AttributeList attList = new AttributeList();
                attList.add(new Attribute("TaskBacklog",34));
                when(beanConn.getAttributes(new ObjectName(name), attrNames)).thenReturn(attList);
            }
        }

        {
            String[] names = new String[]{"Coherence:type=Node"};
            for (String name : names) {
                MBeanInfo mBeanInfo = getMBeanInfoForNode(name);
                when(beanConn.getMBeanInfo(eq(new ObjectName(name)))).thenReturn(mBeanInfo);
                String[] attrNames = new String[]{"Memory","CPU","TaskBacklog"};
                AttributeList attList = new AttributeList();
                attList.add(new Attribute("Memory",34));
                attList.add(new Attribute("CPU",34));
                attList.add(new Attribute("TaskBacklog",34));
                when(beanConn.getAttributes(new ObjectName(name), attrNames)).thenReturn(attList);
            }
        }


        Set<ObjectInstance> allObjects = beanConn.queryMBeans(null, null);
        for (ObjectInstance instance : allObjects) {
            System.out.println("instance: " + instance);

            List<String> names = new ArrayList<>();
            MBeanInfo info = beanConn.getMBeanInfo(instance.getObjectName());
            for (MBeanAttributeInfo mBeanAttributeInfo : info.getAttributes()) {
                System.out.println("mBeanAttributeInfo:" + mBeanAttributeInfo);
                if(mBeanAttributeInfo.isReadable()){
                    names.add(mBeanAttributeInfo.getName());
                }
            }

            AttributeList attributeList =
                    beanConn.getAttributes(instance.getObjectName(), names.toArray(new String[0]));

            for (Attribute attribute : attributeList.asList()) {
                System.out.println("attribute: " + attribute);
            }

        }


    }

    private MBeanInfo getMBeanInfoForCluster(String name) {
        MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[1];
        attributes[0] = new MBeanAttributeInfo("TaskBacklog","TaskBacklog:type","TaskBacklog:description", true, false, false);
        return new MBeanInfo(name,"com.tangosol.coherence.component.manageable.modelAdapter.ClusterMBean", attributes, null,null,null);
    }

    private MBeanInfo getMBeanInfoForNode(String name) {
        MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[3];
        attributes[0] = new MBeanAttributeInfo("Memory","TaskBacklog:type","TaskBacklog:description", true, false, false);
        attributes[1] = new MBeanAttributeInfo("CPU","TaskBacklog:type","TaskBacklog:description", true, false, false);
        attributes[2] = new MBeanAttributeInfo("TaskBacklog","TaskBacklog:type","TaskBacklog:description", true, false, false);
        return new MBeanInfo(name,"com.tangosol.coherence.component.manageable.modelAdapter.ClusterNodeMBean", attributes, null,null,null);
    }


}
