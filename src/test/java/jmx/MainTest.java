package jmx;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import org.junit.Test;
import yml.YmlReader;

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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by kevsa on 24/02/2018.
 */
//@Slf4j
public class MainTest {

    public static final String METRICS = "metrics";
    public static final String INCLUDE = "include";

    @Test
    public void canReadAttributes() throws IOException {

        String resource = "/config.yml";
        //resource="/unfiltered.yml";
        Map configMap = YmlReader.readFromFileAsMap(new File(this.getClass().getResource(resource).getFile()));
        Config config = buildConfig(configMap);

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
            Optional<List<MetricProperty>> filter = filterFor(instance, config.getMbeans());
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


  //build as required
    private Config buildConfig(Map configMap) {
        Config.ConfigBuilder builder = Config.builder();
        List<Map> mbeans = (List<Map>) configMap.get("mbeans");
        List<MBean> beans = new ArrayList<>(mbeans.size());
        for (Map mbean : mbeans) {
            MBean.MBeanBuilder mBeanBuilder = MBean.builder().objectName((String)mbean.get("objectName"));
            Map configMetrics = (Map)mbean.get(METRICS);
            List includeMetrics = (List)configMetrics.get(INCLUDE);
            List<MetricProperty> metricProperties = new ArrayList<>();
            if(includeMetrics != null){
                for(Object metad : includeMetrics){
                    Map localMetaData = (Map)metad;
                    Map.Entry entry = (Map.Entry)localMetaData.entrySet().iterator().next();
                    String metricName = entry.getKey().toString();
                    String alias = entry.getValue().toString();
                    MetricProperty.MetricPropertyBuilder props = MetricProperty.builder();
                    props.alias(alias);
                    props.name(metricName);
                    setProps(configMap,props); //global level
                    setProps(localMetaData, props); //local level
                    metricProperties.add(props.build());
                }
            }
            mBeanBuilder.metrics(metricProperties);
            beans.add(mBeanBuilder.build());
        }
        builder.mbeans(beans);
        return builder.build();
    }
    private void setProps(Map metadata, MetricProperty.MetricPropertyBuilder props) {
        if(metadata.get("convert") != null){
            props.conversionMap((Map)metadata.get("convert"));
        }else {
            props.conversionMap(Collections.emptyMap());
        }
        if(metadata.get("delta") != null){
            props.delta(Boolean.parseBoolean(metadata.get("delta").toString()));
        }else {
            props.delta(false);
        }
    }

    private Optional<List<MetricProperty>> filterFor(ObjectInstance instance, List<MBean> mbeans) {
        if(mbeans==null){
            return Optional.of(Collections.emptyList());
        }
        for (MBean mbean : mbeans) {
            String name = mbean.getObjectName();
            try {
                ObjectName filter = new ObjectName(name);
                if(filter.apply(instance.getObjectName())){
                    return Optional.of(mbean.getMetrics());
                }
            } catch (MalformedObjectNameException e) {
                System.out.println("bad objectName: " + name);;
            }
        }
        return Optional.empty();
    }

    private Beat scrapeBean(MBeanServerConnection beanConn, ObjectName mbeanName, List<MetricProperty> metricsFilter) {
        Map<String, MBeanAttributeInfo> name2AttrInfo = readInfo(beanConn, mbeanName);

        Set<String> readableNames = name2AttrInfo.keySet();
        List<String> namesToBeExtracted = applyFilters(metricsFilter,readableNames);

        final AttributeList attributes = readAttributes(beanConn, mbeanName, namesToBeExtracted.toArray(new String[0]));

        Map<String,String> tags = new LinkedHashMap<>();
        for (Map.Entry<String, String> tag: mbeanName.getKeyPropertyList().entrySet()) {
            tags.put(tag.getKey(), tag.getValue());
        }
        JsonObject metrics = new JsonObject();
        Beat.BeatBuilder builder = Beat.builder();
        builder.name(mbeanName.getDomain());
        for (Attribute attribute : attributes.asList()) {
            Object value = applyConvert(attribute.getName(), attribute.getValue(), metricsFilter);
            writeValue(attribute.getName(), value, metrics, tags);
        }
        builder.metrics(metrics);
        builder.tags(tags);

        return builder.build();
    }

    private List<String> applyFilters(List<MetricProperty> configMetrics, Set<String> readableNames) {
        if(configMetrics.isEmpty()){
            return Lists.newArrayList(readableNames);
        }
        Set<String> filteredSet = Sets.newHashSet();
        List<String> includes = configMetrics.stream()
                .filter(p -> !p.isExclude())
                .map(MetricProperty::getName)
                .collect(Collectors.toList());
        List<String> excludes = configMetrics.stream()
                .filter(p -> p.isExclude())
                .map(MetricProperty::getName)
                .collect(Collectors.toList());

        new ExcludeFilter(excludes).apply(filteredSet,readableNames);
        new IncludeFilter(includes).apply(filteredSet,readableNames);
        return Lists.newArrayList(filteredSet);
    }

    private Object applyConvert(String metricName, Object metricValue, List<MetricProperty> configMetrics){
        //get converted values if configured
        for (MetricProperty metricProperty : configMetrics) {
            if(metricProperty.getName().equals(metricName) && !metricProperty.isExclude()){
                Map conversionValues = metricProperty.getConversionMap();
                Object convertedValue = conversionValues.get(metricValue);
                if (convertedValue != null) {
                    //logger.debug("Applied conversion on {} and replaced value {} with {}", metricName, metricValue, convertedValue);
                    return Double.valueOf(String.valueOf(convertedValue));
                }
            }
        }
        return metricValue;
    }

    private void writeValue(String name, Object value, JsonObject metrics, Map<String, String> tags) {
        if(value instanceof Number){
            metrics.addProperty(name, (Number) value);
        }else if(value instanceof String){
            tags.put(name, (String) value); //TODO some Number fields may need to also be indexed eg StorageEnabled
        }else if(value instanceof Boolean){
            metrics.addProperty(name, (Boolean) value);
        }else if(value instanceof Date){
            DateTimeFormatter f = DateTimeFormatter.ofPattern( "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            metrics.addProperty(name, ((Date)value).toInstant().atOffset(ZoneOffset.UTC).format(f));
        } else if (value instanceof CompositeData) {
            CompositeData composite = (CompositeData) value;
            CompositeType type = composite.getCompositeType();
            for(String key : type.keySet()) {
                Object valu = composite.get(key);
                JsonObject child = (JsonObject)metrics.get(name);
                if(child==null){
                    metrics.add(name, child = new JsonObject());
                }
                writeValue( key, valu, child, tags);
            }
        } else {
            if(value != null){
                System.out.println(name + " of type " + value.getClass().getSimpleName() + " not supported");
            }
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