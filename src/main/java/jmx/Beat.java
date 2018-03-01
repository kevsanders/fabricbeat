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
    Map<String,String> tags;
    Map<String,String> properties;
    JsonObject metrics;
}
