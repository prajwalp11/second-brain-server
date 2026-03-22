package com.secondbrain.second_brain_server.util;

import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.exception.ValidationException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MetricValidator {

    public static void validateKeys(Set<String> submitted, List<DomainMetricDefinition> defined) {
        Set<String> definedKeys = defined.stream()
                .map(DomainMetricDefinition::getMetricKey)
                .collect(Collectors.toSet());

        for (String key : submitted) {
            if (!definedKeys.contains(key)) {
                throw new ValidationException("Invalid metric key submitted: " + key);
            }
        }
    }
}
