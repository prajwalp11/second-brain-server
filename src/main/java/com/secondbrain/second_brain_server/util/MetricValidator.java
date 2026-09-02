package com.secondbrain.second_brain_server.util;

import com.secondbrain.second_brain_server.entities.DomainMetricDefinition;
import com.secondbrain.second_brain_server.exception.ValidationException;

import java.util.List;
import java.util.Map;
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

    /**
     * Clamps submitted metric values to their allowed bounds.
     * Bounds are inferred, never required from the user:
     *   - declared min_value / max_value on the metric definition take priority
     *   - otherwise, percentage-style metrics (unit "%" / "percent") are clamped to 0..100
     *   - otherwise, all metrics are floored at 0 (values can't go negative) with no ceiling
     * Returns a new map with clamped values; keys with no matching definition pass through unchanged.
     */
    public static Map<String, Double> clampValues(Map<String, Double> submitted, List<DomainMetricDefinition> defined) {
        Map<String, DomainMetricDefinition> byKey = defined.stream()
                .collect(Collectors.toMap(DomainMetricDefinition::getMetricKey, m -> m, (a, b) -> a));

        return submitted.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> clamp(e.getValue(), byKey.get(e.getKey()))
                ));
    }

    /** Clamp a single value against a metric's (declared or inferred) bounds. */
    public static Double clamp(Double value, DomainMetricDefinition metric) {
        if (value == null) {
            return null;
        }

        Double min = resolveMin(metric);
        Double max = resolveMax(metric);

        double result = value;
        if (min != null && result < min) {
            result = min;
        }
        if (max != null && result > max) {
            result = max;
        }
        return result;
    }

    private static Double resolveMin(DomainMetricDefinition metric) {
        if (metric == null) {
            return 0.0; // unknown metric: floor at 0, no ceiling
        }
        if (metric.getMinValue() != null) {
            return metric.getMinValue();
        }
        return 0.0; // default floor — values shouldn't go negative
    }

    private static Double resolveMax(DomainMetricDefinition metric) {
        if (metric == null) {
            return null;
        }
        if (metric.getMaxValue() != null) {
            return metric.getMaxValue();
        }
        if (isPercentageUnit(metric.getUnit())) {
            return 100.0; // inferred ceiling for percentage metrics
        }
        return null; // open-ended (reps, weight, km, minutes...)
    }

    private static boolean isPercentageUnit(String unit) {
        if (unit == null) {
            return false;
        }
        String u = unit.trim().toLowerCase();
        return u.equals("%") || u.contains("percent");
    }
}
