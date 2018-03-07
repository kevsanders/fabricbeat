package jmx;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Created by kevsa on 05/03/2018.
 */
@Value
@Builder
public class Config {
    //List<ClusterInstance> instances;
    List<MBean> mbeans;
}
