package jmx;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import yml.Config;
import yml.MBean;
import yml.MetricProperty;

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
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Created by kevsa on 09/01/2019.
 */
@Value
@Builder
@Slf4j
public class JmxBeatCollector implements BeatCollector {

    MBeanServerConnection beanConn;
    Config config;

    @Override
    public List<Beat> collectBeats() throws IOException {
        List<Beat> beats = new ArrayList<>();
        Set<ObjectInstance> allObjects = beanConn.queryMBeans(null, null);
        for (ObjectInstance instance : allObjects) {
            Optional<List<MetricProperty>> filter = filterFor(instance, config.getMbeans());
            if(filter.isPresent()){
                beats.add(scrapeBean(beanConn, instance.getObjectName(), filter.get()));
            }else {
                log.info("excluded mbean: " + instance);
            }
        }
        return beats;
    }

    private Optional<List<MetricProperty>> filterFor(ObjectInstance instance, List<MBean> mbeans) {
        if(mbeans==null||mbeans.isEmpty()){
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
                log.info("bad objectName: " + name);
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
        for (Attribute attribute : attributes.asList()) {
            Object value = applyConvert(attribute.getName(), attribute.getValue(), metricsFilter);
            writeValue(attribute.getName(), value, metrics, tags, mbeanName);
        }
        //.getDomain()

        Beat.Metricset metricset = Beat.Metricset.builder()
                .host("")
                .module(mbeanName.getDomain())
                .name(mbeanName.getKeyProperty("type"))
                .build();

        return Beat.builder()
                .name(String.valueOf(mbeanName))
                .metrics(metrics)
                .tags(tags)
                .metricset(metricset)
                .build();
    }

    private List<String> applyFilters(List<MetricProperty> configMetrics, Set<String> readableNames) {
        if(configMetrics.isEmpty()){
            return Lists.newArrayList(readableNames);
        }
        Set<String> filteredSet = Sets.newHashSet();
        List<String> includes = configMetrics.stream()
                .filter(MetricProperty::isExclude)
                .map(MetricProperty::getName)
                .collect(Collectors.toList());
        List<String> excludes = configMetrics.stream()
                .filter(MetricProperty::isExclude)
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
                    log.debug("Applied conversion on {} and replaced value {} with {}", metricName, metricValue, convertedValue);
                    return Double.valueOf(String.valueOf(convertedValue));
                }
            }
        }
        return metricValue;
    }

    private void writeValue(String name, Object value, JsonObject metrics, Map<String, String> tags, ObjectName mbeanName) {
        if(value instanceof Number){
            metrics.addProperty(name, (Number) value);//TODO some Number fields may need to also be indexed eg StorageEnabled
        }else if(value instanceof String){
            metrics.addProperty(name, (String) value);
        }else if(value instanceof String[]){
            String[] array = (String[])value;
            JsonArray child = new JsonArray();
            for(int n=0; n< array.length; n++){
                child.add(new JsonPrimitive(array[n]));
            }
            metrics.add(name, child);
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
                writeValue( key, valu, child, tags, mbeanName);
            }
        } else if (value instanceof TabularData) {
            TabularData tds = (TabularData) value;
            TabularType tt = tds.getTabularType();
            List<String> rowKeys = tt.getIndexNames();

            CompositeType type = tt.getRowType();
            Set<String> valueKeys = new TreeSet<>(type.keySet());
            valueKeys.removeAll(rowKeys);

            for (Object valu : tds.values()) {
                if (valu instanceof CompositeData) {
                    CompositeData composite = (CompositeData) valu;
                    for (String idx : rowKeys) {
                        String key = String.valueOf(composite.get(idx));
                        JsonObject child = (JsonObject)metrics.get(name);
                        if(child==null){
                            metrics.add(name, child = new JsonObject());
                        }
                        writeValue( key, valu, child, tags, mbeanName);
                    }
                } else {
                    log.info("{} of type {} from {} not a correct tabulardata format", name, value.getClass().getSimpleName(), mbeanName);
                }
            }
        } else {
            if(value != null){
                log.info("{} of type {} from {} not supported", name, value.getClass().getSimpleName(), mbeanName);
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
        log.info("scrape: '" + name + "': " + msg);
    }

}
