package jmx;

import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Created by kevsa on 24/02/2018.
 */
@Value
@Builder
public class Beat {
    String name;
    Metricset metricset;
    Map<String,String> tags;
    JsonObject metrics;

    @Value
    @Builder
    public static class Metricset {
        String host;
        String module;
        String name;
    }

}
