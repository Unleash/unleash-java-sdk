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
        Counter counter = registry.counter(new MetricOptions("test_counter", "testing"));

        counter.inc();

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
            new CollectedMetric(
                "test_counter", "testing", MetricType.COUNTER, List.of(sample(1L)));

        assertThat(metrics).containsExactly(expected);
    }

    @Test
    public void should_increment_with_custom_value_and_labels() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("labeled_counter", "with labels"));

        counter.inc(3, Map.of("foo", "bar"));
        counter.inc(2, Map.of("foo", "bar"));

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
            new CollectedMetric(
                "labeled_counter",
                "with labels",
                MetricType.COUNTER,
                List.of(sample(Map.of("foo", "bar"), 5L)));

        assertThat(metrics).containsExactly(expected);
    }

    @Test
    public void should_support_gauge_inc_dec_and_set() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Gauge gauge = registry.gauge(new MetricOptions("test_gauge", "gauge test"));

        gauge.inc(5, Map.of("env", "prod"));
        gauge.dec(2, Map.of("env", "prod"));
        gauge.set(10, Map.of("env", "prod"));

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
            new CollectedMetric(
                "test_gauge",
                "gauge test",
                MetricType.GAUGE,
                List.of(sample(Map.of("env", "prod"), 10L)));

        assertThat(metrics).containsExactly(expected);
    }

    @Test
    public void should_store_different_label_combinations_separately() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("multi_label", "label test"));

        counter.inc(1, Map.of("a", "x"));
        counter.inc(2, Map.of("b", "y"));
        counter.inc(3);

        List<CollectedMetric> metrics = registry.collect();
        CollectedMetric result = metrics.get(0);

        assertThat(result.getName()).isEqualTo("multi_label");
        assertThat(result.getSamples())
            .containsExactlyInAnyOrder(
                sample(Map.of("a", "x"), 1L), sample(Map.of("b", "y"), 2L), sample(3L));
    }

    @Test
    public void should_track_gauge_values_separately_per_label_set() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Gauge gauge = registry.gauge(new MetricOptions("multi_env_gauge", "tracks multiple envs"));

        gauge.inc(5, Map.of("env", "prod"));
        gauge.dec(2, Map.of("env", "dev"));
        gauge.set(10, Map.of("env", "test"));

        List<CollectedMetric> metrics = registry.collect();
        CollectedMetric result = metrics.get(0);

        assertThat(result.getName()).isEqualTo("multi_env_gauge");
        assertThat(result.getSamples())
            .containsExactlyInAnyOrder(
                sample(Map.of("env", "prod"), 5L),
                sample(Map.of("env", "dev"), -2L),
                sample(Map.of("env", "test"), 10L));
    }

    @Test
    public void should_return_zero_value_when_empty() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        registry.counter(new MetricOptions("noop_counter", "noop"));
        registry.gauge(new MetricOptions("noop_gauge", "noop"));

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
            new CollectedMetric(
                "noop_counter", "noop", MetricType.COUNTER, List.of(sample(0L)));

        assertThat(metrics).containsExactly(expected);
    }

    @Test
    public void should_return_zero_value_after_flushing() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("flush_test", "flush"));

        counter.inc(1);
        List<CollectedMetric> firstBatch = registry.collect();

        CollectedMetric expectedBatch1 =
            new CollectedMetric("flush_test", "flush", MetricType.COUNTER, List.of(sample(1L)));
        assertThat(firstBatch).containsExactly(expectedBatch1);

        List<CollectedMetric> secondBatch = registry.collect();
        CollectedMetric expectedBatch2 =
            new CollectedMetric("flush_test", "flush", MetricType.COUNTER, List.of(sample(0L)));
        assertThat(secondBatch).containsExactly(expectedBatch2);
    }

    @Test
    public void should_restore_collected_metrics() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("restore_test", "testing restore"));

        counter.inc(5, Map.of("tag", "a"));
        counter.inc(2, Map.of("tag", "b"));

        List<CollectedMetric> flushed = registry.collect();

        List<CollectedMetric> afterFlush = registry.collect();
        assertThat(afterFlush.get(0).getSamples()).containsExactly(sample(0L));

        registry.restore(flushed);

        List<CollectedMetric> restored = registry.collect();
        assertThat(restored.get(0).getSamples())
            .containsExactlyInAnyOrder(
                sample(Map.of("tag", "a"), 5L), sample(Map.of("tag", "b"), 2L));
    }

    private NumericMetricSample sample(long value) {
        return sample(Collections.emptyMap(), value);
    }

    private NumericMetricSample sample(Map<String, String> labels, long value) {
        return new NumericMetricSample(labels, value);
    }
}
