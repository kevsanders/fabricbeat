package jmx;

import org.junit.Test;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;

import static org.junit.Assert.*;

/**
 * Created by kevsa on 09/01/2019.
 */
public class MockMBeanServerTest {

    @Test
    public void canAddObjectInstances() throws MalformedObjectNameException {

        MockMBeanServer mockMBeanServer = new MockMBeanServer.MockMBeanServerBuilder()
                .withObjectInstance(new ObjectInstance("Coherence:type=Cluster","com.tangosol.coherence.component.manageable.modelAdapter.ClusterMBean"))
                .withObjectInstance(new ObjectInstance("Coherence:type=Node","com.tangosol.coherence.component.manageable.modelAdapter.ClusterNodeMBean"))
                .build();

        //mockMBeanServer;


    }

}