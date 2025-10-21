package io.getunleash;

import io.getunleash.repository.ToggleBootstrapFileProvider;
import io.getunleash.util.UnleashConfig;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 3, timeUnit = TimeUnit.MILLISECONDS, time = 2000)
@Measurement(iterations = 5, timeUnit = TimeUnit.MILLISECONDS, time = 5000)
public class UnleashClientBenchmark {

    @State(Scope.Benchmark)
    public static class MyState {

        public Unleash unleash;
        public UnleashContext context;

        @Setup(Level.Trial)
        public void doSetup() {
            System.out.println("dosetup");
            unleash = new DefaultUnleash(UnleashConfig.builder().unleashAPI("https://localhost:1500")
                    .apiKey("irrelevant").appName("UnleashBenchmarks")
                    .toggleBootstrapProvider(
                            new ToggleBootstrapFileProvider("classpath:./unleash-repo-v2-with-impression-data.json"))
                    .fetchTogglesInterval(0).disablePolling().disableMetrics().build());
            context = new UnleashContext.Builder().environment("benchmarking").build();
        }

        @TearDown(Level.Trial)
        public void doTearDown() {
            System.out.println("Do TearDown");
        }

    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(UnleashClientBenchmark.class.getSimpleName()).forks(1).build();
        new Runner(opt).run();
    }

    @Benchmark
    public void isEnabled(MyState myState, Blackhole bh) {
        bh.consume(myState.unleash.isEnabled("Test.impressionDataPresent"));
    }

    @Benchmark
    public void isEnabledWithContext(MyState myState, Blackhole bh) {
        bh.consume(myState.unleash.isEnabled("Test.impressionDataPresent", myState.context));
    }

/*
    @Benchmark
    public void getDefaultVariant(MyState myState, Blackhole bh) {
        bh.consume(myState.unleash.getVariant("Test.impressionDataPresent"));
    }

    @Benchmark
    public void getVariant(MyState myState, Blackhole bh) {
        bh.consume(myState.unleash.getVariant("Test.variants"));
    }
*/

}
