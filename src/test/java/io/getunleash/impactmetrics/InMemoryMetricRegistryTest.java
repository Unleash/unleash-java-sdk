package io.getunleash.impactmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class InMemoryMetricRegistryTest {

    @Test
    public void should_increment_by_default_value() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("test_counter", "test help"));

        counter.inc();

        List<CollectedMetric> metrics = registry.collect();
        assertThat(metrics).hasSize(1);
        CollectedMetric result = metrics.get(0);

        assertThat(result.getName()).isEqualTo("test_counter");
        assertThat(result.getType()).isEqualTo(MetricType.COUNTER);
        assertThat(result.getSamples()).containsExactly(sample(1L));
    }

    @Test
    public void should_increment_with_custom_value_and_labels() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("labeled_counter", "with labels"));

        counter.inc(3, Map.of("foo", "bar"));
        counter.inc(2, Map.of("foo", "bar"));

        List<CollectedMetric> metrics = registry.collect();
        assertThat(metrics).hasSize(1);

        CollectedMetric metric = metrics.get(0);
        assertThat(metric.getName()).isEqualTo("labeled_counter");
        assertThat(metric.getHelp()).isEqualTo("with labels");
        assertThat(metric.getType()).isEqualTo(MetricType.COUNTER);
        assertThat(metric.getSamples()).containsExactly(sample(Map.of("foo", "bar"), 5L));
    }

    @Test
    public void should_store_different_label_combinations_separately() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("multi_label", "label test"));

        counter.inc(1, Map.of("a", "x"));
        counter.inc(2, Map.of("b", "y"));
        counter.inc(3);

        List<CollectedMetric> metrics = registry.collect();
        assertThat(metrics).hasSize(1);
        CollectedMetric result = metrics.get(0);

        assertThat(result.getSamples())
                .containsExactlyInAnyOrder(
                        sample(Map.of("a", "x"), 1L), sample(Map.of("b", "y"), 2L), sample(3L));
    }

    @Test
    public void should_return_zero_value_when_empty() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        registry.counter(new MetricOptions("empty_counter", "empty"));

        List<CollectedMetric> metrics = registry.collect();

        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0).getSamples()).containsExactly(sample(0L));
    }

    @Test
    public void should_reset_after_collect() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("reset_test", "reset"));

        counter.inc(5);
        List<CollectedMetric> firstBatch = registry.collect();
        assertThat(firstBatch.get(0).getSamples()).containsExactly(sample(5L));

        List<CollectedMetric> secondBatch = registry.collect();
        assertThat(secondBatch.get(0).getSamples()).containsExactly(sample(0L));
    }

    @Test
    public void should_register_same_counter_twice_returns_same_instance() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter c1 = registry.counter(new MetricOptions("shared", "help"));
        Counter c2 = registry.counter(new MetricOptions("shared", "help"));

        c1.inc(1);
        c2.inc(2);

        List<CollectedMetric> metrics = registry.collect();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0).getSamples()).containsExactly(sample(3L));
    }

    private NumericMetricSample sample(long value) {
        return sample(Collections.emptyMap(), value);
    }

    private NumericMetricSample sample(Map<String, String> labels, long value) {
        return new NumericMetricSample(labels, value);
    }
}
