package jmx.beats;

import lombok.Builder;
import lombok.Value;

/**
 * Created by kevsa on 26/02/2018.
 */
@Builder
@Value
public class UsedMemory {
    long bytes;
    double pct;
}
