package org.example;

import java.util.*;
import java.util.stream.Collectors;

public class PriceAnalyzer {
    private final List<Offer> offers;

    public PriceAnalyzer(List<Offer> offers) {
        this.offers = offers != null ? offers : new ArrayList<>();
    }




    // Obliczanie statystyk dla listy cen
    private PriceStats calculatePriceStats(List<Double> prices) {
        if (prices.isEmpty()) {
            return new PriceStats(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        // Średnia
        double average = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // Odchylenie standardowe
        double variance = prices.stream()
                .mapToDouble(price -> Math.pow(price - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);

        // Percentyle
        List<Double> sortedPrices = prices.stream().sorted().collect(Collectors.toList());
        double percentile25 = calculatePercentile(sortedPrices, 0.25);
        double percentile50 = calculatePercentile(sortedPrices, 0.50);
        double percentile75 = calculatePercentile(sortedPrices, 0.75);

        return new PriceStats(average, standardDeviation, percentile25, percentile50, percentile75);
    }

    // Obliczanie percentyla
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

    public Map<String, PriceStats> getPriceStatsByModel() {
        Map<String, List<Offer>> offersByModel = offers.stream()
                .filter(offer -> offer.getModel() != null)
                .collect(Collectors.groupingBy(Offer::getModel));
        Map<String, PriceStats> statsByModel = new HashMap<>();
        for (Map.Entry<String, List<Offer>> entry : offersByModel.entrySet()) {
            List<Double> prices = entry.getValue().stream()
                    .map(Offer::getPrice)
                    .filter(price -> price > 0)
                    .collect(Collectors.toList());
            statsByModel.put(entry.getKey(), calculatePriceStats(prices));
        }
        return statsByModel;
    }

    public Map<String, PriceStats> getPriceStatsByModelWithProtection() {
        Map<String, List<Offer>> offersByModel = offers.stream()
                .filter(offer -> offer.getModel() != null && offer.hasProtectionPackage())
                .collect(Collectors.groupingBy(Offer::getModel));
        Map<String, PriceStats> statsByModel = new HashMap<>();
        for (Map.Entry<String, List<Offer>> entry : offersByModel.entrySet()) {
            List<Double> prices = entry.getValue().stream()
                    .map(Offer::getPrice)
                    .filter(price -> price > 0)
                    .collect(Collectors.toList());
            statsByModel.put(entry.getKey(), calculatePriceStats(prices));
        }
        return statsByModel;
    }

    public Map<String, Map<String, PriceStats>> getPriceStatsByModelAndStorage() {
        Map<String, Map<String, List<Offer>>> offersByModelAndStorage = offers.stream()
                .filter(offer -> offer.getModel() != null && offer.getStorageCapacity() != null)
                .collect(Collectors.groupingBy(
                        Offer::getModel,
                        Collectors.groupingBy(Offer::getStorageCapacity)
                ));
        Map<String, Map<String, PriceStats>> statsByModelAndStorage = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Offer>>> modelEntry : offersByModelAndStorage.entrySet()) {
            Map<String, PriceStats> storageStats = new HashMap<>();
            for (Map.Entry<String, List<Offer>> storageEntry : modelEntry.getValue().entrySet()) {
                List<Double> prices = storageEntry.getValue().stream()
                        .map(Offer::getPrice)
                        .filter(price -> price > 0)
                        .collect(Collectors.toList());
                storageStats.put(storageEntry.getKey(), calculatePriceStats(prices));
            }
            if (!storageStats.isEmpty()) {
                statsByModelAndStorage.put(modelEntry.getKey(), storageStats);
            }
        }
        return statsByModelAndStorage;
    }

    public Map<String, Map<String, PriceStats>> getPriceStatsByModelAndStorageWithProtection() {
        Map<String, Map<String, List<Offer>>> offersByModelAndStorage = offers.stream()
                .filter(offer -> offer.getModel() != null && offer.getStorageCapacity() != null && offer.hasProtectionPackage())
                .collect(Collectors.groupingBy(
                        Offer::getModel,
                        Collectors.groupingBy(Offer::getStorageCapacity)
                ));
        Map<String, Map<String, PriceStats>> statsByModelAndStorage = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Offer>>> modelEntry : offersByModelAndStorage.entrySet()) {
            Map<String, PriceStats> storageStats = new HashMap<>();
            for (Map.Entry<String, List<Offer>> storageEntry : modelEntry.getValue().entrySet()) {
                List<Double> prices = storageEntry.getValue().stream()
                        .map(Offer::getPrice)
                        .filter(price -> price > 0)
                        .collect(Collectors.toList());
                storageStats.put(storageEntry.getKey(), calculatePriceStats(prices));
            }
            if (!storageStats.isEmpty()) {
                statsByModelAndStorage.put(modelEntry.getKey(), storageStats);
            }
        }
        return statsByModelAndStorage;
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

    public Map<String, Double> calculateAveragePriceByModel() {
        return getPriceStatsByModel().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getAverage()
                ));
    }

    public Map<String, Double> calculateAveragePriceByModelWithProtection() {
        return getPriceStatsByModelWithProtection().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getAverage()
                ));
    }

    public Map<String, Map<String, Double>> calculateAveragePriceByModelAndStorage() {
        return getPriceStatsByModelAndStorage().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        modelEntry -> modelEntry.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        storageEntry -> storageEntry.getValue().getAverage()
                                ))
                ));
    }

    public Map<String, Map<String, Double>> calculateAveragePriceByModelAndStorageWithProtection() {
        return getPriceStatsByModelAndStorageWithProtection().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        modelEntry -> modelEntry.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        storageEntry -> storageEntry.getValue().getAverage()
                                ))
                ));
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

    // Metoda getRecommendedOffersWithoutProtection
    public List<Offer> getRecommendedOffersWithoutProtection(double threshold, String location) {
        PriceStats stats = getPriceStatsWithoutProtection();
        double medianPrice = stats.getPercentile50();
        if (medianPrice == 0.0) {
            return new ArrayList<>();
        }
        return offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .filter(offer -> offer.getPrice() <= medianPrice && offer.getPrice() > 0)
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

    // Metoda getRecommendedOffersWithProtection
    public List<Offer> getRecommendedOffersWithProtection(String location) {
        PriceStats stats = getPriceStatsWithProtection();
        double medianPrice = stats.getPercentile50();
        if (medianPrice == 0.0) {
            return new ArrayList<>();
        }
        return offers.stream()
                .filter(Offer::hasProtectionPackage)
                .filter(offer -> offer.getPrice() <= medianPrice && offer.getPrice() > 0)
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

}