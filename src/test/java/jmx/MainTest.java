package jmx;

import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import yml.Config;
import yml.ConfigReader;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by kevsa on 24/02/2018.
 */
@Slf4j
public class MainTest {

    private MBeanServerConnection beanConn;
    private Config config;

    @Before
    public void setUp() throws Exception {
        String resource = "config.yml";
        resource = "unfiltered.yml";
        config = new ConfigReader().readConfig(resource);
        beanConn = createMockBeanConnection();
    }

    private MBeanServerConnection createMockBeanConnection() throws IOException, MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException {
        MBeanServerConnection beanConn = mock(MBeanServerConnection.class);
        when(beanConn.queryMBeans(any(), any())).thenReturn(JMXDataProviderUtil.getSetWithClusterObjInstance());
        when(beanConn.getMBeanInfo(eq(new ObjectName("Coherence:type=Cluster")))).thenReturn(JMXDataProviderUtil.getMBeanInfoForCluster("Coherence:type=Cluster"));
        when(beanConn.getMBeanInfo(eq(new ObjectName("Coherence:type=Node")))).thenReturn(JMXDataProviderUtil.getMBeanInfoForNode("Coherence:type=Node"));

        {
            AttributeList attList = new AttributeList();
            attList.add(new Attribute("TaskBacklog",34));
            when(beanConn.getAttributes(
                    new ObjectName("Coherence:type=Cluster"),
                    new String[]{"TaskBacklog"}))
                    .thenReturn(attList);

        }
        {
            AttributeList attList = new AttributeList();
            attList.add(new Attribute("Memory",34));
            attList.add(new Attribute("CPU",34));
            attList.add(new Attribute("TaskBacklog",34));
            when(beanConn.getAttributes(
                    new ObjectName("Coherence:type=Node"),
                    new String[]{"Memory","CPU","TaskBacklog"}))
                    .thenReturn(attList);

        }
        return beanConn;
    }

    @Test
    public void canReadUnfilteredMockBeats() throws IOException {

        JmxBeatCollector beatCollector = JmxBeatCollector.builder()
                .beanConn(beanConn)
                .config(config)
                .build();

        List<Beat> beats = beatCollector.collectBeats();

        for (Beat beat : beats) {
            log.info(String.valueOf(beat));
        }

        {
            Map<String, String> clusterTags = new HashMap<>();
            clusterTags.put("type", "Cluster");
            JsonObject metrics = new JsonObject();
            metrics.addProperty("TaskBacklog", 34);
            assertEquals("wrong beat ", beats.get(0),
                    new Beat("Coherence:type=Cluster", new Beat.Metricset("", "Coherence", "Cluster"), clusterTags, metrics));
        }
        {
            Map<String,String> nodeTags = new HashMap<>();
            nodeTags.put("type", "Node");
            JsonObject metrics = new JsonObject();
            metrics.addProperty("Memory", 34);
            metrics.addProperty("CPU", 34);
            metrics.addProperty("TaskBacklog", 34);
            assertEquals("wrong beat ", beats.get(1),
                    new Beat("Coherence:type=Node", new Beat.Metricset("", "Coherence", "Node"), nodeTags, metrics));

        }

    }

    @Test
    @Ignore("for occasional manual test\n")
    public void canReadFilteredRealBeats() throws IOException {

        beanConn = createRealConnection("service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi", null, null);
        config = new ConfigReader().readConfig("config.yml");
        config = new ConfigReader().readConfig("unfiltered.yml");
        config = new ConfigReader().readConfig("tabular.yml");

        JmxBeatCollector beatCollector = JmxBeatCollector.builder()
                .beanConn(beanConn)
                .config(config)
                .build();

        List<Beat> beats = beatCollector.collectBeats();

        for (Beat beat : beats) {
            log.info(String.valueOf(beat));
        }

    }

    private MBeanServerConnection createRealConnection(String jmxUrl, String username, String password) throws IOException {
        Map<String, Object> environment = new HashMap<>();
        if (username != null && username.length() != 0 && password != null && password.length() != 0) {
            String[] credent = new String[] {username, password};
            environment.put(javax.management.remote.JMXConnector.CREDENTIALS, credent);
        }
        boolean ssl = false;
        if (ssl) {
            environment.put(Context.SECURITY_PROTOCOL, "ssl");
            SslRMIClientSocketFactory clientSocketFactory = new SslRMIClientSocketFactory();
            environment.put(RMIConnectorServer.RMI_CLIENT_SOCKET_FACTORY_ATTRIBUTE, clientSocketFactory);
            environment.put("com.sun.jndi.rmi.factory.socket", clientSocketFactory);
        }
        JMXConnector jmxConnector = JMXConnectorFactory.connect(new JMXServiceURL(jmxUrl), environment);
        return jmxConnector.getMBeanServerConnection();
    }


}