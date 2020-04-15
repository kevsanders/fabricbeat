package jmx.beats;

import lombok.Builder;
import lombok.Value;

/**
 * Created by kevsa on 26/02/2018.
 */
@Value
@Builder
public class MemoryBeat {
    long total;
    long free;
    UsedMemory usedMemory;
    MemoryBeat swap;
}
