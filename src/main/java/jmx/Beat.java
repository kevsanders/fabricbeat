package jmx;

import lombok.Builder;
import lombok.Value;

import javax.json.JsonObject;
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
