package org.example;

import java.util.*;
import java.util.stream.Collectors;

public class PriceAnalyzer {
    private final List<Offer> offers;

    public PriceAnalyzer(List<Offer> offers) {
        this.offers = offers != null ? offers : new ArrayList<>();
    }

    private PriceStats calculatePriceStats(List<Double> prices) {
        if (prices.isEmpty()) {
            return new PriceStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        double average = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double variance = prices.stream()
                .mapToDouble(price -> Math.pow(price - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        List<Double> sortedPrices = prices.stream().sorted().collect(Collectors.toList());
        double percentile25 = calculatePercentile(sortedPrices, 0.25);
        double percentile50 = calculatePercentile(sortedPrices, 0.50);
        double percentile75 = calculatePercentile(sortedPrices, 0.75);

        return new PriceStats(average, standardDeviation, percentile25, percentile50, percentile75);
    }

    private double calculatePercentile(List<Double> sortedPrices, double percentile) {
        if (sortedPrices.isEmpty()) {
            return 0.0;
        }
        int n = sortedPrices.size();
        double index = percentile * (n - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);
        if (lowerIndex == upperIndex) {
            return sortedPrices.get(lowerIndex);
        }
        double fraction = index - lowerIndex;
        return sortedPrices.get(lowerIndex) * (1 - fraction) + sortedPrices.get(upperIndex) * fraction;
    }

    public PriceStats getOverallPriceStats() {
        List<Double> prices = offers.stream()
                .map(Offer::getPrice)
                .filter(price -> price > 0)
                .collect(Collectors.toList());
        return calculatePriceStats(prices);
    }

    public PriceStats getPriceStatsWithProtection() {
        List<Double> prices = offers.stream()
                .filter(Offer::hasProtectionPackage)
                .map(Offer::getPrice)
                .filter(price -> price > 0)
                .collect(Collectors.toList());
        return calculatePriceStats(prices);
    }

    public PriceStats getPriceStatsWithoutProtection() {
        List<Double> prices = offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .map(Offer::getPrice)
                .filter(price -> price > 0)
                .collect(Collectors.toList());
        return calculatePriceStats(prices);
    }

    public double calculateAveragePrice() {
        return getOverallPriceStats().getAverage();
    }

    public double calculateAveragePriceWithProtection() {
        return getPriceStatsWithProtection().getAverage();
    }

    public double calculateAveragePriceWithoutProtection() {
        return getPriceStatsWithoutProtection().getAverage();
    }

    public List<Offer> getRecommendedOffers(double threshold, String location) {
        double averagePrice = calculateAveragePrice();
        if (averagePrice == 0.0) {
            return new ArrayList<>();
        }
        double maxPrice = averagePrice * threshold;
        return offers.stream()
                .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Offer> getRecommendedOffersWithoutProtection(double zScoreThreshold, String location, PriceHistoryManager historyManager) {
        PriceStats stats = getPriceStatsWithoutProtection();
        double medianPrice = stats.getPercentile50();
        if (medianPrice == 0.0) {
            return new ArrayList<>();
        }
        return offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .filter(offer -> offer.getPrice() <= medianPrice && offer.getPrice() > 0)
                .filter(offer -> {
                    double zScore = calculateZScore(offer.getPrice(), stats);
                    return zScore <= zScoreThreshold;
                })
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Offer> getRecommendedOffersWithProtection(double zScoreThreshold, String location, PriceHistoryManager historyManager) {
        PriceStats stats = getPriceStatsWithProtection();
        double medianPrice = stats.getPercentile50();
        if (medianPrice == 0.0) {
            return new ArrayList<>();
        }
        return offers.stream()
                .filter(Offer::hasProtectionPackage)
                .filter(offer -> offer.getPrice() <= medianPrice && offer.getPrice() > 0)
                .filter(offer -> {
                    double zScore = calculateZScore(offer.getPrice(), stats);
                    return zScore <= zScoreThreshold;
                })
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

    private double calculateZScore(double price, PriceStats stats) {
        double average = stats.getAverage();
        double standardDeviation = stats.getStandardDeviation();
        if (standardDeviation == 0.0) {
            return 0.0;
        }
        return (price - average) / standardDeviation;
    }

    public Map<Offer, Double> calculateZScores(List<Offer> offers, PriceStats stats) {
        Map<Offer, Double> zScores = new HashMap<>();
        double average = stats.getAverage();
        double standardDeviation = stats.getStandardDeviation();

        if (offers.size() < 5 || standardDeviation == 0.0) {
            offers.forEach(offer -> zScores.put(offer, 0.0));
            return zScores;
        }

        for (Offer offer : offers) {
            double price = offer.getPrice();
            if (price > 0) {
                double zScore = (price - average) / standardDeviation;
                zScores.put(offer, zScore);
            } else {
                zScores.put(offer, 0.0);
            }
        }
        return zScores;
    }
}