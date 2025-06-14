package org.example;

import java.util.*;
import java.util.stream.Collectors;

public class PriceAnalyzer {
    private final List<Offer> offers;
    private final double LOWER_PERCENTILE = 0.05; // 5. percentyl
    private final double UPPER_PERCENTILE = 0.95; // 95. percentyl

    public PriceAnalyzer(List<Offer> offers) {
        this.offers = offers != null ? offers : new ArrayList<>();
    }

    private PriceStats calculatePriceStats(List<Double> prices, List<Offer> sourceOffers, List<Offer> lowPriceOutlierOffers) {
        if (prices.isEmpty()) {
            return new PriceStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        // Obliczanie percentyli dla filtrowania wartości odstających
        List<Double> sortedPrices = prices.stream().sorted().collect(Collectors.toList());
        double percentile5 = calculatePercentile(sortedPrices, LOWER_PERCENTILE);
        double percentile95 = calculatePercentile(sortedPrices, UPPER_PERCENTILE);

        // Filtrowanie cen w przedziale [5. percentyl, 95. percentyl] i zbieranie tanich ofert odstających
        List<Double> filteredPrices = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            double price = prices.get(i);
            Offer offer = sourceOffers.get(i);
            if (price >= percentile5 && price <= percentile95) {
                filteredPrices.add(price);
            } else if (price < percentile5) {
                lowPriceOutlierOffers.add(offer); // Dodajemy tylko tanie oferty do listy odstających
            }
        }

        if (filteredPrices.isEmpty()) {
            return new PriceStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        // Obliczanie statystyk na podstawie przefiltrowanych cen
        double average = filteredPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double variance = filteredPrices.stream()
                .mapToDouble(price -> Math.pow(price - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        List<Double> sortedFilteredPrices = filteredPrices.stream().sorted().collect(Collectors.toList());
        double percentile25 = calculatePercentile(sortedFilteredPrices, 0.25);
        double percentile50 = calculatePercentile(sortedFilteredPrices, 0.50);
        double percentile75 = calculatePercentile(sortedFilteredPrices, 0.75);

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

    public PriceStats getOverallPriceStats(List<Offer> lowPriceOutlierOffers) {
        List<Double> prices = offers.stream()
                .map(Offer::getPrice)
                .filter(price -> price > 0)
                .collect(Collectors.toList());
        List<Offer> sourceOffers = offers.stream()
                .filter(offer -> offer.getPrice() > 0)
                .collect(Collectors.toList());
        return calculatePriceStats(prices, sourceOffers, lowPriceOutlierOffers);
    }

    public PriceStats getPriceStatsWithProtection(List<Offer> lowPriceOutlierOffers) {
        List<Double> prices = offers.stream()
                .filter(Offer::hasProtectionPackage)
                .map(Offer::getPrice)
                .filter(price -> price > 0)
                .collect(Collectors.toList());
        List<Offer> sourceOffers = offers.stream()
                .filter(Offer::hasProtectionPackage)
                .filter(offer -> offer.getPrice() > 0)
                .collect(Collectors.toList());
        return calculatePriceStats(prices, sourceOffers, lowPriceOutlierOffers);
    }

    public PriceStats getPriceStatsWithoutProtection(List<Offer> lowPriceOutlierOffers) {
        List<Double> prices = offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .map(Offer::getPrice)
                .filter(price -> price > 0)
                .collect(Collectors.toList());
        List<Offer> sourceOffers = offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .filter(offer -> offer.getPrice() > 0)
                .collect(Collectors.toList());
        return calculatePriceStats(prices, sourceOffers, lowPriceOutlierOffers);
    }

    public List<Offer> getRecommendedOffersWithoutProtection(double zScoreThreshold, String location, PriceHistoryManager historyManager) {
        PriceStats stats = getPriceStatsWithoutProtection(new ArrayList<>());
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
        PriceStats stats = getPriceStatsWithProtection(new ArrayList<>());
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