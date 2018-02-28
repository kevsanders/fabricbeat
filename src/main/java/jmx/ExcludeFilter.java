/*
 *  Copyright 2014. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package jmx;


import java.util.List;
import java.util.Set;

public class ExcludeFilter {

    private List dictionary;

    public ExcludeFilter(List excludeDictionary) {
        this.dictionary = excludeDictionary;
    }

    public void apply(Set<String> filteredSet, Set<String> allMetrics){
        if(allMetrics == null || dictionary == null){
            return;
        }
        for(String metric : allMetrics){
            if(!dictionary.contains(metric)){
                filteredSet.add(metric);
            }
        }
    }
}
