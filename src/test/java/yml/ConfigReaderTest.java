package yml;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by kevin on 11/03/2018.
 */
public class ConfigReaderTest {

    @Test
    public void canReadConfig() {

        ConfigReader reader = new ConfigReader();
        Config config = reader.readConfig("conf/config.yml");
        assertNotNull("config should not be null", config);
        assertEquals("expected 7 mbeans but got " + config.getMbeans(), 7, config.getMbeans().size());

        assertMBean(config, 0,
                buildMBean("Coherence:type=Cluster",
                        buildSimpleMetricProperties("Members", "ClusterSize")
                )
        );

        List<MetricProperty> propertiesWithDelta = buildSimpleMetricProperties("CacheHits", "CacheMisses", "CachePrunes", "TotalGets", "TotalPuts", "UnitFactor", "Units", "Size");
        propertiesWithDelta = updateDelta(propertiesWithDelta, 0, true);
        assertMBean(config, 1,
                buildMBean("Coherence:type=Cache,service=*,name=*,nodeId=*,tier=*",
                        propertiesWithDelta
                )
        );

        assertMBean(config, 2,
                buildMBean("Coherence:type=Node,nodeId=*",
                        buildSimpleMetricProperties("MemoryAvailableMB", "MemoryMaxMB")
                )
        );

        List<MetricProperty> propertiesWithConvertMap = buildSimpleMetricProperties("TaskBacklog", "TaskAverageDuration", "RequestAverageDuration", "PartitionsAll", "PartitionsUnbalanced", "PartitionsEndangered", "ThreadCount", "ThreadIdleCount", "ThreadUtilizationPercentage", "StatusHA");
        LinkedHashMap<String, Object> convertMap = new LinkedHashMap<>();
        convertMap.put("ENDANGERED", "1");
        convertMap.put("NODE-SAFE", "2");
        convertMap.put("MACHINE-SAFE", "3");
        updateConvertMap(propertiesWithConvertMap, 9, convertMap);
        assertMBean(config, 3,
                buildMBean("Coherence:type=Service,name=*,nodeId=*",
                        propertiesWithConvertMap
                )
        );

    }

    private void updateConvertMap(List<MetricProperty> metricProperties, int index, Map<String, Object> convertMap) {
        MetricProperty metricProperty = metricProperties.remove(index);
        metricProperty = MetricProperty.builder().alias(metricProperty.getAlias()).name(metricProperty.getName())
                .delta(metricProperty.isDelta()).exclude(metricProperty.isExclude())
                .conversionMap(convertMap).build();
        metricProperties.add(metricProperty);
    }

    private List<MetricProperty> updateDelta(List<MetricProperty> template, int index, boolean delta) {
        List<MetricProperty> updated = new ArrayList<>(template.size());
        for (int n = 0; n < template.size(); n++) {
            MetricProperty metricProperty = template.get(n);
            if(index==n){
                metricProperty = MetricProperty.builder().alias(metricProperty.getAlias()).name(metricProperty.getName())
                        .delta(delta).exclude(metricProperty.isExclude())
                        .conversionMap(metricProperty.getConversionMap()).build();
            }
            updated.add(metricProperty);
        }
        return updated;
    }

    private void assertMBean(Config config, int index, MBean expected) {
        assertEquals(
                expected
                ,config.getMbeans().get(index)
        );
    }

    private MBean buildMBean(String objectName, List<MetricProperty> metricProperties) {
        return MBean.builder()
                .objectName(objectName)
                .metrics(metricProperties)
        .build();
    }

    private List<MetricProperty> buildSimpleMetricProperties(String... attributes) {
        List<MetricProperty> metricProperties = new ArrayList<>(attributes.length);
        for (String attribute : attributes) {
            metricProperties.add(
                    MetricProperty.builder()
                            .name(attribute)
                            .alias(attribute)
                            .delta(false)
                            .exclude(false)
                            .conversionMap(Collections.emptyMap())
                            .build()
            );
        }
        return metricProperties;
    }

}