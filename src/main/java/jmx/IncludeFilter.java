/*
 *  Copyright 2014. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package jmx;


import java.util.List;
import java.util.Map;
import java.util.Set;

public class IncludeFilter {

    private List dictionary;

    public IncludeFilter(List includeDictionary) {
        this.dictionary = includeDictionary;
    }

    public void apply(Set<String> filteredSet, Set<String> allMetrics){
        if(allMetrics == null || dictionary == null){
            return;
        }
        for(Object inc : dictionary){
            Map metric = (Map) inc;
            //Get the First Entry which is the metric
            Map.Entry firstEntry = (Map.Entry) metric.entrySet().iterator().next();
            String metricName = firstEntry.getKey().toString();
            if(allMetrics.contains(metricName)) {
                filteredSet.add(metricName); //to get jmx metrics
            }
        }
    }
}
