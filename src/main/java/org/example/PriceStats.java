package org.example;

public class PriceStats {
    private final double average;
    private final double standardDeviation;
    private final double percentile25;
    private final double percentile50;
    private final double percentile75;

    public PriceStats(double average, double standardDeviation, double percentile25, double percentile50, double percentile75) {
        this.average = average;
        this.standardDeviation = standardDeviation;
        this.percentile25 = percentile25;
        this.percentile50 = percentile50;
        this.percentile75 = percentile75;
    }

    public double getAverage() {
        return average;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }

    public double getPercentile25() {
        return percentile25;
    }

    public double getPercentile50() {
        return percentile50;
    }

    public double getPercentile75() {
        return percentile75;
    }
}
