package jmx;

import java.io.IOException;
import java.util.List;

/**
 * a beat collector will collect beats
 */
public interface BeatCollector {
    List<Beat> collectBeats() throws IOException;
}
