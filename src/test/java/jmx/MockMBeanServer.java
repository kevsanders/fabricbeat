package jmx;

import com.google.common.collect.Sets;
import lombok.Builder;
import lombok.Value;

import javax.management.AttributeList;
import javax.management.MBeanInfo;
import javax.management.ObjectInstance;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by kevsa on 09/01/2019.
 */
@Value
public class MockMBeanServer {
    Set<ObjectInstance> objectInstanceSet;
    Map<ObjectInstance, MBeanInfo> mBeanInfoByObjectInstanceMap;
    Map<ObjectInstance, AttributeList> attributeListByObjectInstanceMap;

    private MockMBeanServer(Set<ObjectInstance> objectInstanceSet, Map<ObjectInstance, MBeanInfo> mBeanInfoByObjectInstanceMap, Map<ObjectInstance, AttributeList> attributeListByObjectInstanceMap) {
        this.objectInstanceSet = objectInstanceSet;
        this.mBeanInfoByObjectInstanceMap = mBeanInfoByObjectInstanceMap;
        this.attributeListByObjectInstanceMap = attributeListByObjectInstanceMap;
    }

    public static class MockMBeanServerBuilder{
        private Set<ObjectInstance> objectInstanceSet = Sets.newHashSet();;
        private Map<ObjectInstance, MBeanInfo> mBeanInfoByObjectInstanceMap = new HashMap<>();
        private Map<ObjectInstance, AttributeList> attributeListByObjectInstanceMap = new HashMap<>();

        public MockMBeanServerBuilder withObjectInstance(ObjectInstance objectInstance){
            objectInstanceSet.add(objectInstance);
            return this;
        }

        public MockMBeanServer build(){
            return new MockMBeanServer(objectInstanceSet, mBeanInfoByObjectInstanceMap, attributeListByObjectInstanceMap);
        }
    }

}
