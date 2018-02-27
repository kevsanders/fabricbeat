package jmx;

import lombok.Builder;
import lombok.Value;

/**
 * Created by kevsa on 26/02/2018.
 */
@Value
@Builder
public class NodeBeat {
    MemoryBeat memoryBeat;
    CpuBeat cpuBeat;
}
