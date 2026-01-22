package io.getunleash.impactmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MetricTypeTest {

    @Test
    public void should_increment_by_default_value() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("test_counter", "testing"));

        counter.inc();

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
                new CollectedMetric(
                        "test_counter", "testing", MetricType.COUNTER, List.of(sample(1.0)));

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
                        List.of(sample(Map.of("foo", "bar"), 5.0)));

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
                        List.of(sample(Map.of("env", "prod"), 10.0)));

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
                        sample(Map.of("a", "x"), 1.0), sample(Map.of("b", "y"), 2.0), sample(3.0));
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
                        sample(Map.of("env", "prod"), 5.0),
                        sample(Map.of("env", "dev"), -2.0),
                        sample(Map.of("env", "test"), 10.0));
    }

    @Test
    public void should_return_zero_value_when_empty() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        registry.counter(new MetricOptions("noop_counter", "noop"));
        registry.gauge(new MetricOptions("noop_gauge", "noop"));

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
                new CollectedMetric(
                        "noop_counter", "noop", MetricType.COUNTER, List.of(sample(0.0)));

        assertThat(metrics).containsExactly(expected);
    }

    @Test
    public void should_return_zero_value_after_flushing() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Counter counter = registry.counter(new MetricOptions("flush_test", "flush"));

        counter.inc(1);
        List<CollectedMetric> firstBatch = registry.collect();

        CollectedMetric expectedBatch1 =
                new CollectedMetric(
                        "flush_test", "flush", MetricType.COUNTER, List.of(sample(1.0)));
        assertThat(firstBatch).containsExactly(expectedBatch1);

        List<CollectedMetric> secondBatch = registry.collect();
        CollectedMetric expectedBatch2 =
                new CollectedMetric(
                        "flush_test", "flush", MetricType.COUNTER, List.of(sample(0.0)));
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
        assertThat(afterFlush.get(0).getSamples()).containsExactly(sample(0.0));

        registry.restore(flushed);

        List<CollectedMetric> restored = registry.collect();
        assertThat(restored.get(0).getSamples())
                .containsExactlyInAnyOrder(
                        sample(Map.of("tag", "a"), 5.0), sample(Map.of("tag", "b"), 2.0));
    }

    @Test
    public void should_observe_histogram_values() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Histogram histogram =
                registry.histogram(
                        new BucketMetricOptions(
                                "test_histogram",
                                "testing histogram",
                                List.of(0.1, 0.5, 1.0, 2.5, 5.0)));

        histogram.observe(0.05, Map.of("env", "prod"));
        histogram.observe(0.75, Map.of("env", "prod"));
        histogram.observe(3, Map.of("env", "prod"));

        List<CollectedMetric> metrics = registry.collect();

        CollectedMetric expected =
                new CollectedMetric(
                        "test_histogram",
                        "testing histogram",
                        MetricType.HISTOGRAM,
                        List.of(
                                new BucketMetricSample(
                                        Map.of("env", "prod"),
                                        3L,
                                        3.8,
                                        List.of(
                                                bucket(0.1, 1),
                                                bucket(0.5, 1),
                                                bucket(1.0, 2),
                                                bucket(2.5, 2),
                                                bucket(5.0, 3),
                                                bucket(Double.POSITIVE_INFINITY, 3)))));

        assertThat(metrics).containsExactly(expected);
    }

    @Test
    public void should_track_different_label_combinations_separately_in_histogram() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Histogram histogram =
                registry.histogram(
                        new BucketMetricOptions(
                                "multi_label_histogram",
                                "histogram with multiple labels",
                                List.of(1.0, 10.0)));

        histogram.observe(0.5, Map.of("method", "GET"));
        histogram.observe(5, Map.of("method", "POST"));
        histogram.observe(15);

        List<CollectedMetric> metrics = registry.collect();
        CollectedMetric result = metrics.get(0);

        assertThat(result.getName()).isEqualTo("multi_label_histogram");
        assertThat(result.getSamples())
                .containsExactlyInAnyOrder(
                        new BucketMetricSample(
                                Map.of("method", "GET"),
                                1L,
                                0.5,
                                List.of(
                                        bucket(1.0, 1),
                                        bucket(10.0, 1),
                                        bucket(Double.POSITIVE_INFINITY, 1))),
                        new BucketMetricSample(
                                Map.of("method", "POST"),
                                1L,
                                5.0,
                                List.of(
                                        bucket(1.0, 0),
                                        bucket(10.0, 1),
                                        bucket(Double.POSITIVE_INFINITY, 1))),
                        new BucketMetricSample(
                                Collections.emptyMap(),
                                1L,
                                15.0,
                                List.of(
                                        bucket(1.0, 0),
                                        bucket(10.0, 0),
                                        bucket(Double.POSITIVE_INFINITY, 1))));
    }

    @Test
    public void should_preserve_exact_data_when_restoring_histogram() {
        InMemoryMetricRegistry registry = new InMemoryMetricRegistry();
        Histogram histogram =
                registry.histogram(
                        new BucketMetricOptions(
                                "restore_histogram",
                                "testing histogram restore",
                                List.of(0.1, 1.0, 10.0)));

        histogram.observe(0.05, Map.of("method", "GET"));
        histogram.observe(0.5, Map.of("method", "GET"));
        histogram.observe(5, Map.of("method", "POST"));
        histogram.observe(15, Map.of("method", "POST"));

        List<CollectedMetric> firstCollect = registry.collect();
        assertThat(firstCollect).hasSize(1);

        List<CollectedMetric> emptyCollect = registry.collect();

        CollectedMetric expectedEmpty =
                new CollectedMetric(
                        "restore_histogram",
                        "testing histogram restore",
                        MetricType.HISTOGRAM,
                        List.of(
                                new BucketMetricSample(
                                        Collections.emptyMap(),
                                        0L,
                                        0.0,
                                        List.of(
                                                bucket(0.1, 0),
                                                bucket(1.0, 0),
                                                bucket(10.0, 0),
                                                bucket(Double.POSITIVE_INFINITY, 0)))));

        assertThat(emptyCollect).containsExactly(expectedEmpty);

        registry.restore(firstCollect);

        List<CollectedMetric> restoredCollect = registry.collect();

        assertThat(restoredCollect).hasSize(1);
        assertThat(restoredCollect.get(0).getName()).isEqualTo("restore_histogram");
        assertThat(restoredCollect.get(0).getSamples())
                .containsExactlyInAnyOrderElementsOf(firstCollect.get(0).getSamples());
    }

    private HistogramBucket bucket(double le, long count) {
        return new HistogramBucket(le, count);
    }

    private NumericMetricSample sample(double value) {
        return sample(Collections.emptyMap(), value);
    }

    private NumericMetricSample sample(Map<String, String> labels, double value) {
        return new NumericMetricSample(labels, value);
    }
}
