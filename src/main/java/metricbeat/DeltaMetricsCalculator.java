package metricbeat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.TimeUnit;

public class DeltaMetricsCalculator {
    private final Cache<String, Number> deltaCache;

    public DeltaMetricsCalculator(int durationInSeconds) {
        this.deltaCache = CacheBuilder.newBuilder().expireAfterWrite((long)durationInSeconds, TimeUnit.MINUTES).build();
    }

    public Number calculateDelta(String metricPath, Number currentValue) {
        if(currentValue != null) {
            Number prev = (Number)this.deltaCache.getIfPresent(metricPath);
            this.deltaCache.put(metricPath, currentValue);
            if(prev != null) {
                return currentValue.doubleValue() - (prev.doubleValue());
            }
        }

        return 0;
    }
}

