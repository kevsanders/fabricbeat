package yml;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Created by kevsa on 05/03/2018.
 */
@Value
@Builder
public class MetricProperty {
    String name;
    String alias;
    Map<String, Object> conversionMap;
    boolean delta;
    boolean exclude;
}
