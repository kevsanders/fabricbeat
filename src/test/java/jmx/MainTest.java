package jmx;

import org.junit.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by kevsa on 24/02/2018.
 */
//@Slf4j
public class MainTest {

    public static interface MBeanReceiver {
        void recordBean(
                String domain,
                LinkedHashMap<String, String> beanProperties,
                LinkedList<String> attrKeys,
                String attrName,
                String attrType,
                String attrDescription,
                Object value);
    }

    private MBeanReceiver receiver = new StdoutWriter();
    private JmxMBeanPropertyCache jmxMBeanPropertyCache = new JmxMBeanPropertyCache();

    @Test
    public void canReadAttributes() throws IOException {
        String jmxUrl = "service:jmx:rmi:///jndi/rmi://localhost:9010/jmxrmi";
        Map<String, Object> environment = new HashMap<String, Object>();
        String username = null;
        String password = null;
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
        MBeanServerConnection beanConn = jmxConnector.getMBeanServerConnection();


        Set<ObjectInstance> allObjects = beanConn.queryMBeans(null, null);
        for (ObjectInstance instance : allObjects) {
            //System.out.println(instance.getObjectName());
            scrapeBean(beanConn, instance.getObjectName());
        }





//        Beat beat = new Beat("bob");
//        System.out.println(beat.getName());

        //receiver.metricFamilySamplesMap.values()
    }
    private void scrapeBean(MBeanServerConnection beanConn, ObjectName mbeanName) {
        Map<String, MBeanAttributeInfo> name2AttrInfo = readInfo(beanConn, mbeanName);
        final AttributeList attributes = readAttributes(beanConn, mbeanName, name2AttrInfo.keySet().toArray(new String[0]));

        Map<String,String> properties = new LinkedHashMap<>();
        Map<String,String> tags = new LinkedHashMap<>();
        for (Map.Entry<String, String> tag: mbeanName.getKeyPropertyList().entrySet()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        JsonObjectBuilder metrics = Json.createObjectBuilder();
        Beat.BeatBuilder builder = Beat.builder();
        builder.name(mbeanName.getDomain());
        for (Attribute attribute : attributes.asList()) {
            MBeanAttributeInfo attr = name2AttrInfo.get(attribute.getName());
/*
            processBeanValue(
                    mbeanName.getDomain(),
                    jmxMBeanPropertyCache.getKeyPropertyList(mbeanName),
                    new LinkedList<String>(),
                    attr.getName(),
                    attr.getType(),
                    attr.getDescription(),
                    attribute.getValue()
            );
*/

            writeValue(attribute.getName(), attribute.getValue(), metrics, properties);

        }
        builder.metrics(metrics.build());
        builder.properties(properties);
        builder.tags(tags);

        System.out.println(builder.build());
    }

    private void writeValue(String name, Object value, JsonObjectBuilder metrics, Map<String, String> properties) {
        if(value instanceof Number){
            metrics.add(name, ((Number) value).doubleValue());
        }else if(value instanceof String){
            properties.put(name, (String) value);
        } else if (value instanceof CompositeData) {
            CompositeData composite = (CompositeData) value;
            CompositeType type = composite.getCompositeType();
            Map<String,JsonObjectBuilder> children = new HashMap<>();
            for(String key : type.keySet()) {
                Object valu = composite.get(key);

                JsonObjectBuilder child = children.get(name);
                if(child==null){
                    child = Json.createObjectBuilder();
                    children.put(name, child);
                }else {
                    JsonObject jsonObject = metrics.build().getJsonObject(name);
                    for (String s : jsonObject.keySet()) {
                            child.add(s, jsonObject.get(s));
                    }
                }
                writeValue( key, valu, child, properties);
                metrics.add(name, child);
                children.put(name, child);
                //System.out.println(child);
            }
/*
        } else if (value instanceof TabularData) {
            TabularData tds = (TabularData) value;
            TabularType tt = tds.getTabularType();
            List<String> rowKeys = tt.getIndexNames();
            CompositeType type = tt.getRowType();
            Set<String> valueKeys = new TreeSet<String>(type.keySet());
            valueKeys.removeAll(rowKeys);
            for (Object key : tds.keySet()) {
                writeValue(name + "_" + key, tds.get(key), metrics, properties);
            }
*/
        }
    }

    private AttributeList readAttributes(MBeanServerConnection beanConn, ObjectName mbeanName, String[] names) {
        try {
            return beanConn.getAttributes(mbeanName, names);
        } catch (Exception e) {
            logScrape(mbeanName, new HashSet<>(Arrays.asList(names)), "Fail: " + e);
            return new AttributeList();
        }
    }

    private Map<String, MBeanAttributeInfo> readInfo(MBeanServerConnection beanConn, ObjectName mbeanName) {
        Map<String, MBeanAttributeInfo> name2AttrInfo = new LinkedHashMap<>();
        try {
            MBeanInfo info = beanConn.getMBeanInfo(mbeanName);
            for (MBeanAttributeInfo attr : info.getAttributes()) {
                if(attr.isReadable()){
                    name2AttrInfo.put(attr.getName(), attr);
                }
            }
            return name2AttrInfo;
        } catch (Exception e) {
            logScrape(mbeanName.toString(), "getMBeanInfo Fail: " + e);
            return name2AttrInfo;
        }
    }

    /**
     * Recursive function for exporting the values of an mBean.
     * JMX is a very open technology, without any prescribed way of declaring mBeans
     * so this function tries to do a best-effort pass of getting the values/names
     * out in a way it can be processed elsewhere easily.
     */
    private void processBeanValue(
            String domain,
            LinkedHashMap<String, String> beanProperties,
            LinkedList<String> attrKeys,
            String attrName,
            String attrType,
            String attrDescription,
            Object value) {
        if (value == null) {
            logScrape(domain + beanProperties + attrName, "null");
        } else if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            logScrape(domain + beanProperties + attrName, value.toString());
            this.receiver.recordBean(
                    domain,
                    beanProperties,
                    attrKeys,
                    attrName,
                    attrType,
                    attrDescription,
                    value);
        } else if (value instanceof CompositeData) {
            logScrape(domain + beanProperties + attrName, "compositedata");
            CompositeData composite = (CompositeData) value;
            CompositeType type = composite.getCompositeType();
            attrKeys = new LinkedList<String>(attrKeys);
            attrKeys.add(attrName);
            for(String key : type.keySet()) {
                String typ = type.getType(key).getTypeName();
                Object valu = composite.get(key);
                processBeanValue(
                        domain,
                        beanProperties,
                        attrKeys,
                        key,
                        typ,
                        type.getDescription(),
                        valu);
            }
        } else if (value instanceof TabularData) {
            // I don't pretend to have a good understanding of TabularData.
            // The real world usage doesn't appear to match how they were
            // meant to be used according to the docs. I've only seen them
            // used as 'key' 'value' pairs even when 'value' is itself a
            // CompositeData of multiple values.
            logScrape(domain + beanProperties + attrName, "tabulardata");
            TabularData tds = (TabularData) value;
            TabularType tt = tds.getTabularType();

            List<String> rowKeys = tt.getIndexNames();
            LinkedHashMap<String, String> l2s = new LinkedHashMap<String, String>(beanProperties);

            CompositeType type = tt.getRowType();
            Set<String> valueKeys = new TreeSet<String>(type.keySet());
            valueKeys.removeAll(rowKeys);

            LinkedList<String> extendedAttrKeys = new LinkedList<String>(attrKeys);
            extendedAttrKeys.add(attrName);
            for (Object valu : tds.values()) {
                if (valu instanceof CompositeData) {
                    CompositeData composite = (CompositeData) valu;
                    for (String idx : rowKeys) {
                        l2s.put(idx, composite.get(idx).toString());
                    }
                    for(String valueIdx : valueKeys) {
                        LinkedList<String> attrNames = extendedAttrKeys;
                        String typ = type.getType(valueIdx).getTypeName();
                        String name = valueIdx;
                        if (valueIdx.toLowerCase().equals("value")) {
                            // Skip appending 'value' to the name
                            attrNames = attrKeys;
                            name = attrName;
                        }
                        processBeanValue(
                                domain,
                                l2s,
                                attrNames,
                                name,
                                typ,
                                type.getDescription(),
                                composite.get(valueIdx));
                    }
                } else {
                    logScrape(domain, "not a correct tabulardata format");
                }
            }
        } else if (value.getClass().isArray()) {
            logScrape(domain, "arrays are unsupported");
        } else {
            logScrape(domain + beanProperties, attrType + " is not exported");
        }
    }

    /**
     * For debugging.
     */
    private static void logScrape(ObjectName mbeanName, Set<String> names, String msg) {
        logScrape(mbeanName + "_" + names, msg);
    }
    private static void logScrape(ObjectName mbeanName, MBeanAttributeInfo attr, String msg) {
        logScrape(mbeanName + "'_'" + attr.getName(), msg);
    }
    private static void logScrape(String name, String msg) {
        System.out.println("scrape: '" + name + "': " + msg);
    }

    private static class StdoutWriter implements MBeanReceiver {
        public void recordBean(
                String domain,
                LinkedHashMap<String, String> beanProperties,
                LinkedList<String> attrKeys,
                String attrName,
                String attrType,
                String attrDescription,
                Object value) {
            System.out.println(domain +
                    beanProperties +
                    attrKeys +
                    attrName +
                    ": " + value);
        }
    }


}