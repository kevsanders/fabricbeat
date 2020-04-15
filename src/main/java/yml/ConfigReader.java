package yml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by kevsa on 07/03/2018.
 */
public class ConfigReader {
    public static final String METRICS = "metrics";
    public static final String INCLUDE = "include";

    public Config readConfig(String resource){
        Map<String,String> configMap = (Map<String,String>)YmlReader.readFromResourceAsMap(resource);
        return buildConfig(configMap);
    }
    private Config buildConfig(Map configMap) {
        Config.ConfigBuilder builder = Config.builder();
        List<Map> mbeansConfig = (List<Map>) configMap.get("mbeans");
        List<MBean> mBeans;
        if(mbeansConfig==null){
            mBeans = Collections.emptyList();
        }else {
            mBeans = new ArrayList<>(mbeansConfig.size());
            for (Map mbeanMap : mbeansConfig) {
                MBean.MBeanBuilder mBeanBuilder = MBean.builder().objectName((String)mbeanMap.get("objectName"));
                Map configMetrics = (Map)mbeanMap.get(METRICS);
                List includeMetrics = (List)configMetrics.get(INCLUDE);
                //TODO: add exclusions
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
                        props.exclude(false);
                        setProps(configMap, props); //global level
                        setProps(localMetaData, props); //local level
                        metricProperties.add(props.build());
                    }
                }
                mBeanBuilder.metrics(metricProperties);
                mBeans.add(mBeanBuilder.build());
            }
        }
        builder.mbeans(mBeans);
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
}
