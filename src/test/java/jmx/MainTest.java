package jmx;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.Test;
import yml.YmlReader;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Created by kevsa on 24/02/2018.
 */
//@Slf4j
public class MainTest {

    @Test
    public void canReadAttributes() throws IOException {

        String resource = "/config.yml";
        resource="/unfiltered.yml";
        Map configMap = YmlReader.readFromFileAsMap(new File(this.getClass().getResource(resource).getFile()));
        List<Map> mbeans = (List<Map>) configMap.get("mbeans");

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

        List<Beat> beats = new ArrayList<>();
        Set<ObjectInstance> allObjects = beanConn.queryMBeans(null, null);
        for (ObjectInstance instance : allObjects) {
            Optional<Map> filter = filterFor(instance, mbeans);
            if(filter.isPresent()){
                beats.add(scrapeBean(beanConn, instance.getObjectName(), filter.get()));
            }else {
                System.out.println("excluded mbean: " + instance);
            }
        }

        for (Beat beat : beats) {
            System.out.println(beat);
        }

    }

    private Optional<Map> filterFor(ObjectInstance instance, List<Map> mbeans) {
        if(mbeans==null){
            return Optional.of(Collections.emptyMap());
        }
        for (Map mbean : mbeans) {
            String name = String.valueOf(mbean.get("objectName"));
            try {
                ObjectName filter = new ObjectName(name);
                if(filter.apply(instance.getObjectName())){
                    return Optional.of((Map) mbean.get("metrics"));
                }
            } catch (MalformedObjectNameException e) {
                System.out.println("bad objectName: " + name);;
            }
        }
        return Optional.empty();
    }

    private Beat scrapeBean(MBeanServerConnection beanConn, ObjectName mbeanName, Map metricsFilter) {
        Map<String, MBeanAttributeInfo> name2AttrInfo = readInfo(beanConn, mbeanName);

        Set<String> readableNames = name2AttrInfo.keySet();
        List<String> namesToBeExtracted = applyFilters(metricsFilter,readableNames);

        final AttributeList attributes = readAttributes(beanConn, mbeanName, namesToBeExtracted.toArray(new String[0]));

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
            writeValue(attribute.getName(), attribute.getValue(), metrics, properties);
        }
        builder.metrics(metrics.build());
        builder.properties(properties);
        builder.tags(tags);

        return builder.build();
    }

    private List<String> applyFilters(Map configMetrics, Set<String> readableNames) {
        if(configMetrics.isEmpty()){
            return Lists.newArrayList(readableNames);
        }
        Set<String> filteredSet = Sets.newHashSet();
        List includeDictionary = (List)configMetrics.get("include");
        List excludeDictionary = (List)configMetrics.get("exclude");
        new ExcludeFilter(excludeDictionary).apply(filteredSet,readableNames);
        new IncludeFilter(includeDictionary).apply(filteredSet,readableNames);
        return Lists.newArrayList(filteredSet);
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
     * For debugging.
     */
    private static void logScrape(ObjectName mbeanName, Set<String> names, String msg) {
        logScrape(mbeanName + "_" + names, msg);
    }
    private static void logScrape(String name, String msg) {
        System.out.println("scrape: '" + name + "': " + msg);
    }

}