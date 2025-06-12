package org.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PriceAnalyzer {
    private final List<Offer> offers;

    public PriceAnalyzer(List<Offer> offers) {
        this.offers = offers != null ? offers : new ArrayList<>();
    }

    public double calculateAveragePrice() {
        if (offers.isEmpty()) {
            return 0.0;
        }
        return offers.stream()
                .mapToDouble(Offer::getPrice)
                .average()
                .orElse(0.0);
    }

    public double calculateAveragePriceWithProtection() {
        List<Offer> protectedOffers = offers.stream()
                .filter(Offer::hasProtectionPackage)
                .collect(Collectors.toList());
        if (protectedOffers.isEmpty()) {
            return 0.0;
        }
        return protectedOffers.stream()
                .mapToDouble(Offer::getPrice)
                .average()
                .orElse(0.0);
    }

    public double calculateAveragePriceWithoutProtection() {
        List<Offer> nonProtectedOffers = offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .collect(Collectors.toList());
        if (nonProtectedOffers.isEmpty()) {
            return 0.0;
        }
        return nonProtectedOffers.stream()
                .mapToDouble(Offer::getPrice)
                .average()
                .orElse(0.0);
    }

    public Map<String, Double> calculateAveragePriceByModel() {
        Map<String, List<Offer>> offersByModel = offers.stream()
                .filter(offer -> offer.getModel() != null)
                .collect(Collectors.groupingBy(Offer::getModel));
        Map<String, Double> averagePrices = new HashMap<>();
        for (Map.Entry<String, List<Offer>> entry : offersByModel.entrySet()) {
            double avg = entry.getValue().stream()
                    .mapToDouble(Offer::getPrice)
                    .average()
                    .orElse(0.0);
            if (avg > 0) {
                averagePrices.put(entry.getKey(), avg);
            }
        }
        return averagePrices;
    }

    public Map<String, Double> calculateAveragePriceByModelWithProtection() {
        Map<String, List<Offer>> offersByModel = offers.stream()
                .filter(offer -> offer.getModel() != null && offer.hasProtectionPackage())
                .collect(Collectors.groupingBy(Offer::getModel));
        Map<String, Double> averagePrices = new HashMap<>();
        for (Map.Entry<String, List<Offer>> entry : offersByModel.entrySet()) {
            double avg = entry.getValue().stream()
                    .mapToDouble(Offer::getPrice)
                    .average()
                    .orElse(0.0);
            if (avg > 0) {
                averagePrices.put(entry.getKey(), avg);
            }
        }
        return averagePrices;
    }

    public Map<String, Map<String, Double>> calculateAveragePriceByModelAndStorage() {
        Map<String, Map<String, List<Offer>>> offersByModelAndStorage = offers.stream()
                .filter(offer -> offer.getModel() != null && offer.getStorageCapacity() != null)
                .collect(Collectors.groupingBy(
                        Offer::getModel,
                        Collectors.groupingBy(Offer::getStorageCapacity)
                ));
        Map<String, Map<String, Double>> averagePrices = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Offer>>> modelEntry : offersByModelAndStorage.entrySet()) {
            Map<String, Double> storagePrices = new HashMap<>();
            for (Map.Entry<String, List<Offer>> storageEntry : modelEntry.getValue().entrySet()) {
                double avg = storageEntry.getValue().stream()
                        .mapToDouble(Offer::getPrice)
                        .average()
                        .orElse(0.0);
                if (avg > 0) {
                    storagePrices.put(storageEntry.getKey(), avg);
                }
            }
            if (!storagePrices.isEmpty()) {
                averagePrices.put(modelEntry.getKey(), storagePrices);
            }
        }
        return averagePrices;
    }

    public Map<String, Map<String, Double>> calculateAveragePriceByModelAndStorageWithProtection() {
        Map<String, Map<String, List<Offer>>> offersByModelAndStorage = offers.stream()
                .filter(offer -> offer.getModel() != null && offer.getStorageCapacity() != null && offer.hasProtectionPackage())
                .collect(Collectors.groupingBy(
                        Offer::getModel,
                        Collectors.groupingBy(Offer::getStorageCapacity)
                ));
        Map<String, Map<String, Double>> averagePrices = new HashMap<>();
        for (Map.Entry<String, Map<String, List<Offer>>> modelEntry : offersByModelAndStorage.entrySet()) {
            Map<String, Double> storagePrices = new HashMap<>();
            for (Map.Entry<String, List<Offer>> storageEntry : modelEntry.getValue().entrySet()) {
                double avg = storageEntry.getValue().stream()
                        .mapToDouble(Offer::getPrice)
                        .average()
                        .orElse(0.0);
                if (avg > 0) {
                    storagePrices.put(storageEntry.getKey(), avg);
                }
            }
            if (!storagePrices.isEmpty()) {
                averagePrices.put(modelEntry.getKey(), storagePrices);
            }
        }
        return averagePrices;
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

    public List<Offer> getRecommendedOffersWithoutProtection(double threshold, String location) {
        double averagePrice = calculateAveragePriceWithoutProtection();
        if (averagePrice == 0.0) {
            return new ArrayList<>();
        }
        double maxPrice = averagePrice * threshold;
        return offers.stream()
                .filter(offer -> !offer.hasProtectionPackage())
                .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<Offer> getRecommendedOffersWithProtection(String location) {
        double averagePrice = calculateAveragePriceWithProtection();
        if (averagePrice == 0.0) {
            return new ArrayList<>();
        }
        double maxPrice = averagePrice * 0.8; // Stosujemy próg 80%
        return offers.stream()
                .filter(Offer::hasProtectionPackage)
                .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                .collect(Collectors.toList());
    }

    public Map<String, List<Offer>> getRecommendedOffersByModel(String location) {
        Map<String, Double> averagePrices = calculateAveragePriceByModel();
        Map<String, List<Offer>> recommendations = new HashMap<>();
        for (Map.Entry<String, Double> entry : averagePrices.entrySet()) {
            String model = entry.getKey();
            double maxPrice = entry.getValue() * 0.8; // Próg 80%
            List<Offer> modelOffers = offers.stream()
                    .filter(offer -> model.equals(offer.getModel()))
                    .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                    .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                    .collect(Collectors.toList());
            if (!modelOffers.isEmpty()) {
                recommendations.put(model, modelOffers);
            }
        }
        return recommendations;
    }

    public Map<String, List<Offer>> getRecommendedOffersByModelWithProtection(String location) {
        Map<String, Double> averagePrices = calculateAveragePriceByModelWithProtection();
        Map<String, List<Offer>> recommendations = new HashMap<>();
        for (Map.Entry<String, Double> entry : averagePrices.entrySet()) {
            String model = entry.getKey();
            double maxPrice = entry.getValue() * 0.8; // Próg 80%
            List<Offer> modelOffers = offers.stream()
                    .filter(offer -> model.equals(offer.getModel()) && offer.hasProtectionPackage())
                    .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                    .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                    .collect(Collectors.toList());
            if (!modelOffers.isEmpty()) {
                recommendations.put(model, modelOffers);
            }
        }
        return recommendations;
    }

    public Map<String, Map<String, List<Offer>>> getRecommendedOffersByModelAndStorage(String location) {
        Map<String, Map<String, Double>> averagePrices = calculateAveragePriceByModelAndStorage();
        Map<String, Map<String, List<Offer>>> recommendations = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> modelEntry : averagePrices.entrySet()) {
            String model = modelEntry.getKey();
            Map<String, List<Offer>> storageRecommendations = new HashMap<>();
            for (Map.Entry<String, Double> storageEntry : modelEntry.getValue().entrySet()) {
                String storage = storageEntry.getKey();
                double maxPrice = storageEntry.getValue() * 0.8; // Próg 80%
                List<Offer> storageOffers = offers.stream()
                        .filter(offer -> model.equals(offer.getModel()) && storage.equals(offer.getStorageCapacity()))
                        .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                        .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                        .collect(Collectors.toList());
                if (!storageOffers.isEmpty()) {
                    storageRecommendations.put(storage, storageOffers);
                }
            }
            if (!storageRecommendations.isEmpty()) {
                recommendations.put(model, storageRecommendations);
            }
        }
        return recommendations;
    }

    public Map<String, Map<String, List<Offer>>> getRecommendedOffersByModelAndStorageWithProtection(String location) {
        Map<String, Map<String, Double>> averagePrices = calculateAveragePriceByModelAndStorageWithProtection();
        Map<String, Map<String, List<Offer>>> recommendations = new HashMap<>();
        for (Map.Entry<String, Map<String, Double>> modelEntry : averagePrices.entrySet()) {
            String model = modelEntry.getKey();
            Map<String, List<Offer>> storageRecommendations = new HashMap<>();
            for (Map.Entry<String, Double> storageEntry : modelEntry.getValue().entrySet()) {
                String storage = storageEntry.getKey();
                double maxPrice = storageEntry.getValue() * 0.8; // Próg 80%
                List<Offer> storageOffers = offers.stream()
                        .filter(offer -> model.equals(offer.getModel()) && storage.equals(offer.getStorageCapacity()) && offer.hasProtectionPackage())
                        .filter(offer -> offer.getPrice() <= maxPrice && offer.getPrice() > 0)
                        .filter(offer -> location == null || offer.getLocation().toLowerCase().contains(location.toLowerCase()))
                        .collect(Collectors.toList());
                if (!storageOffers.isEmpty()) {
                    storageRecommendations.put(storage, storageOffers);
                }
            }
            if (!storageRecommendations.isEmpty()) {
                recommendations.put(model, storageRecommendations);
            }
        }
        return recommendations;
    }
}