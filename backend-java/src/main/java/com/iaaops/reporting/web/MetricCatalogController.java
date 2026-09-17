package com.iaaops.reporting.web;

import com.iaaops.shared.metrics.Metric;
import com.iaaops.shared.metrics.MetricRegistry;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 指标口径目录：与计算共用 {@link MetricRegistry} 的同一份定义。
 */
@RestController
@RequestMapping("/api/v1")
public class MetricCatalogController {

    @GetMapping("/metrics")
    public Catalog metrics() {
        return new Catalog(MetricRegistry.all().stream().map(MetricCatalogController::toDefinition).toList());
    }

    private static Definition toDefinition(Metric metric) {
        return new Definition(metric.key(), metric.label(), metric.kind().name().toLowerCase(Locale.ROOT),
                metric.unit().name().toLowerCase(Locale.ROOT), metric.formula(), metric.description(),
                metric.precision());
    }

    public record Catalog(List<Definition> items) {
    }

    public record Definition(String key, String label, String kind, String unit, String formula, String description,
            int precision) {
    }
}
