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

    private List<String> dictionary;

    public IncludeFilter(List<String> includeDictionary) {
        this.dictionary = includeDictionary;
    }

    public void apply(Set<String> filteredSet, Set<String> allMetrics){
        if(allMetrics == null || dictionary == null){
            return;
        }
        for(String metricName : dictionary){
            if(allMetrics.contains(metricName)) {
                filteredSet.add(metricName); //to get jmx metrics
            }
        }
    }
}
