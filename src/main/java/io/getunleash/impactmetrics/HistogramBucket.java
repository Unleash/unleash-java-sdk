package io.getunleash.impactmetrics;

import java.util.Objects;

public class HistogramBucket {
    private final double le;
    private final long count;

    HistogramBucket(double le, long count) {
        this.le = le;
        this.count = count;
    }

    double getLe() {
        return le;
    }

    long getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistogramBucket that = (HistogramBucket) o;
        return Double.compare(that.le, le) == 0 && count == that.count;
    }

    @Override
    public int hashCode() {
        return Objects.hash(le, count);
    }

    @Override
    public String toString() {
        return "HistogramBucket{" + "le=" + le + ", count=" + count + '}';
    }
}
