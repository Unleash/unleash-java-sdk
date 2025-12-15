package io.getunleash.impactmetrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class CounterImplTest {

    @Test
    public void should_increment_by_default_value() {
        CounterImpl counter = new CounterImpl("test_counter", "test help");

        counter.inc();

        CollectedMetric result = counter.collect();

        assertThat(result.getName()).isEqualTo("test_counter");
        assertThat(result.getHelp()).isEqualTo("test help");
        assertThat(result.getType()).isEqualTo(MetricType.COUNTER);
        assertThat(result.getSamples()).hasSize(1);
        assertThat(result.getSamples().get(0).getValue()).isEqualTo(1L);
        assertThat(result.getSamples().get(0).getLabels()).isEmpty();
    }

    @Test
    public void should_increment_with_custom_value() {
        CounterImpl counter = new CounterImpl("test_counter", "test help");

        counter.inc(5);
        counter.inc(3);

        CollectedMetric result = counter.collect();

        assertThat(result.getSamples()).hasSize(1);
        assertThat(result.getSamples().get(0).getValue()).isEqualTo(8L);
    }

    @Test
    public void should_increment_with_labels() {
        CounterImpl counter = new CounterImpl("labeled_counter", "with labels");

        counter.inc(3, Map.of("foo", "bar"));
        counter.inc(2, Map.of("foo", "bar"));

        CollectedMetric result = counter.collect();

        assertThat(result.getSamples()).hasSize(1);
        assertThat(result.getSamples().get(0).getValue()).isEqualTo(5L);
        assertThat(result.getSamples().get(0).getLabels()).containsEntry("foo", "bar");
    }

    @Test
    public void should_store_different_label_combinations_separately() {
        CounterImpl counter = new CounterImpl("multi_label", "label test");

        counter.inc(1, Map.of("a", "x"));
        counter.inc(2, Map.of("b", "y"));
        counter.inc(3);

        CollectedMetric result = counter.collect();

        assertThat(result.getSamples()).hasSize(3);

        NumericMetricSample sampleAX =
                result.getSamples().stream()
                        .filter(s -> s.getLabels().containsKey("a"))
                        .findFirst()
                        .orElseThrow();
        NumericMetricSample sampleBY =
                result.getSamples().stream()
                        .filter(s -> s.getLabels().containsKey("b"))
                        .findFirst()
                        .orElseThrow();
        NumericMetricSample sampleEmpty =
                result.getSamples().stream()
                        .filter(s -> s.getLabels().isEmpty())
                        .findFirst()
                        .orElseThrow();

        assertThat(sampleAX.getValue()).isEqualTo(1L);
        assertThat(sampleBY.getValue()).isEqualTo(2L);
        assertThat(sampleEmpty.getValue()).isEqualTo(3L);
    }

    @Test
    public void should_return_zero_value_when_empty() {
        CounterImpl counter = new CounterImpl("empty_counter", "empty");

        CollectedMetric result = counter.collect();

        assertThat(result.getSamples()).hasSize(1);
        assertThat(result.getSamples().get(0).getValue()).isEqualTo(0L);
        assertThat(result.getSamples().get(0).getLabels()).isEmpty();
    }

    @Test
    public void should_reset_after_collect() {
        CounterImpl counter = new CounterImpl("reset_test", "reset");

        counter.inc(5);
        CollectedMetric first = counter.collect();
        assertThat(first.getSamples().get(0).getValue()).isEqualTo(5L);

        CollectedMetric second = counter.collect();
        assertThat(second.getSamples()).hasSize(1);
        assertThat(second.getSamples().get(0).getValue()).isEqualTo(0L);
    }


}
