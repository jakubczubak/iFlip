package org.example;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final List<String> IPHONE_MODELS = Arrays.asList(
            "iPhone X", "iPhone XS", "iPhone XS Max", "iPhone XR",
            "iPhone 11", "iPhone 11 Pro", "iPhone 11 Pro Max",
            "iPhone 12", "iPhone 12 Mini", "iPhone 12 Pro", "iPhone 12 Pro Max",
            "iPhone 13", "iPhone 13 Mini", "iPhone 13 Pro", "iPhone 13 Pro Max",
            "iPhone SE", "iPhone 14", "iPhone 14 Plus", "iPhone 14 Pro", "iPhone 14 Pro Max",
            "iPhone 15", "iPhone 15 Plus", "iPhone 15 Pro", "iPhone 15 Pro Max",
            "iPhone 16", "iPhone 16 Plus", "iPhone 16 Pro", "iPhone 16 Pro Max"
    );

    private static final List<String> STORAGE_CAPACITIES = Arrays.asList(
            "32GB", "64GB", "128GB", "256GB", "512GB", "1TB"
    );

    private static final List<String> DEVICE_STATES = Arrays.asList(
            "Nowy", "Używany", "Uszkodzony"
    );

    private static final Map<String, List<Integer>> MODEL_GROUPS = new LinkedHashMap<>() {{
        put("iPhone X–XR", Arrays.asList(0, 1, 2, 3));
        put("iPhone 11–13", Arrays.asList(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15));
        put("iPhone 14–16", Arrays.asList(16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27));
    }};

    private static class RecommendationAssessment {
        private final String status;

        public RecommendationAssessment(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        @Override
        public String toString() {
            return status;
        }
    }

    private static RecommendationAssessment getRecommendationAssessment(double price, PriceStats stats, double zScore, String trendAnalysis) {
        double median = stats.getPercentile50();

        if (median == 0.0) {
            return new RecommendationAssessment("Brak danych");
        }

        double priceToMedianRatio = price / median;
        boolean isTrendCompliant = trendAnalysis.contains("taniej");

        if (priceToMedianRatio <= 0.8 && zScore <= -1.0) {
            return new RecommendationAssessment("Świetna (" + (isTrendCompliant ? "z trendem)" : "bez trendu)"));
        } else if (priceToMedianRatio <= 0.95 && zScore <= -0.5) {
            return new RecommendationAssessment("Dobra (" + (isTrendCompliant ? "z trendem)" : "bez trendu)"));
        } else {
            return new RecommendationAssessment("Przeciętna");
        }
    }

    private static final double SOCHACZEW_LAT = 52.2294;
    private static final double SOCHACZEW_LON = 20.2384;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueSearching = true;

        LocationCacheManager locationCacheManager = new LocationCacheManager();
        DistanceCalculator distanceCalculator = new DistanceCalculator(locationCacheManager);

        System.out.println("Ustawiona lokalizacja użytkownika: Sochaczew, Mazowieckie");

        while (continueSearching) {
            System.out.println("\n=== iFlip ===");
            System.out.println("\n=== Wyszukiwarka ofert iPhone na OLX ===");
            System.out.println("Wybierz opcje poniżej, aby znaleźć najlepsze oferty.\n");

            String selectedModel = selectModel(scanner);
            if (selectedModel == null) continue;

            String selectedStorage = selectStorage(scanner);
            if (selectedStorage == null) continue;

            List<String> selectedStates = selectStates(scanner);
            if (selectedStates == null) continue;

            String location = selectLocation(scanner);
            if (location == null) continue;

            System.out.println("\n=== Podsumowanie wyborów ===");
            System.out.printf("Model: %s\n", selectedModel);
            System.out.printf("Pojemność: %s\n", selectedStorage);
            System.out.printf("Stan: %s\n", selectedStates.isEmpty() ? "Wszystkie" : String.join(", ", selectedStates));
            System.out.printf("Lokalizacja: %s\n", location.isEmpty() ? "Cała Polska" : location);
            System.out.print("\nCzy chcesz kontynuować z tymi ustawieniami? (tak/nie): ");
            String confirm = scanner.nextLine().trim().toLowerCase();
            if (!confirm.equals("tak")) {
                continue;
            }

            System.out.println("Wybrane stany przed scrapowaniem: " + selectedStates);

            OlxScraper scraper = new OlxScraper();
            List<Offer> offers = scraper.scrapeOffers(selectedModel, selectedStorage, location, selectedStates);
            PriceAnalyzer analyzer = new PriceAnalyzer(offers);
            PriceHistoryManager historyManager = new PriceHistoryManager();
            historyManager.savePrices(offers);

            List<Offer> lowPriceOutlierOffers = new ArrayList<>();

            PriceStats overallStats = analyzer.getOverallPriceStats(lowPriceOutlierOffers);
            PriceStats statsWithProtection = analyzer.getPriceStatsWithProtection(lowPriceOutlierOffers);
            PriceStats statsWithoutProtection = analyzer.getPriceStatsWithoutProtection(lowPriceOutlierOffers);

            Map<Offer, Double> zScoresWithoutProtection = analyzer.calculateZScores(
                    offers.stream()
                            .filter(offer -> !offer.hasProtectionPackage())
                            .filter(offer -> offer.getPrice() > 0)
                            .collect(Collectors.toList()),
                    statsWithoutProtection
            );
            Map<Offer, Double> zScoresWithProtection = analyzer.calculateZScores(
                    offers.stream()
                            .filter(Offer::hasProtectionPackage)
                            .filter(offer -> offer.getPrice() > 0)
                            .collect(Collectors.toList()),
                    statsWithProtection
            );

            List<Offer> recommendedOffersWithoutProtection = analyzer.getRecommendedOffersWithoutProtection(-0.5, location.isEmpty() ? null : location, historyManager);
            List<Offer> recommendedOffersWithProtection = analyzer.getRecommendedOffersWithProtection(-0.5, location.isEmpty() ? null : location, historyManager);
            recommendedOffersWithoutProtection.sort(Comparator.comparingDouble(Offer::getPrice));
            recommendedOffersWithProtection.sort(Comparator.comparingDouble(Offer::getPrice));

            displayResults(offers, selectedModel, selectedStorage, location,
                    overallStats, statsWithoutProtection, statsWithProtection,
                    recommendedOffersWithoutProtection, recommendedOffersWithProtection,
                    lowPriceOutlierOffers, zScoresWithoutProtection, zScoresWithProtection,
                    historyManager, distanceCalculator, scanner);

            System.out.print("\nCzy chcesz wyszukać ponownie z innymi ustawieniami? (tak/nie): ");
            continueSearching = scanner.nextLine().trim().toLowerCase().equals("tak");
        }

        System.out.println("\nDziękujemy za skorzystanie z wyszukiwarki OLX!");
        scanner.close();
    }

    private static String selectModel(Scanner scanner) {
        System.out.println("=== Wybór modelu iPhone’a ===");
        System.out.println("Wybierz grupę modeli:");
        int groupIndex = 1;
        for (String group : MODEL_GROUPS.keySet()) {
            System.out.printf("%d. %s\n", groupIndex++, group);
        }
        System.out.print("Wpisz numer grupy (1–" + MODEL_GROUPS.size() + ") lub 'q' aby wyjść: ");

        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        int groupChoice;
        try {
            groupChoice = Integer.parseInt(input);
            if (groupChoice < 1 || groupChoice > MODEL_GROUPS.size()) {
                System.out.println("Proszę wpisać numer od 1 do " + MODEL_GROUPS.size() + ".");
                return null;
            }
        } catch (NumberFormatException e) {
            System.out.println("Proszę wpisać poprawny numer.");
            return null;
        }

        String selectedGroup = new ArrayList<>(MODEL_GROUPS.keySet()).get(groupChoice - 1);
        List<Integer> modelIndices = MODEL_GROUPS.get(selectedGroup);

        System.out.println("\nWybierz model z grupy " + selectedGroup + ":");
        for (int i = 0; i < modelIndices.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, IPHONE_MODELS.get(modelIndices.get(i)));
        }
        System.out.print("Wpisz numer modelu (1–" + modelIndices.size() + ") lub 'q' aby wyjść: ");

        input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        int modelChoice;
        try {
            modelChoice = Integer.parseInt(input);
            if (modelChoice < 1 || modelChoice > modelIndices.size()) {
                System.out.println("Proszę wpisać numer od 1 do " + modelIndices.size() + ".");
                return null;
            }
        } catch (NumberFormatException e) {
            System.out.println("Proszę wpisać poprawny numer.");
            return null;
        }

        return IPHONE_MODELS.get(modelIndices.get(modelChoice - 1));
    }

    private static String selectStorage(Scanner scanner) {
        System.out.println("\n=== Wybór pojemności pamięci ===");
        for (int i = 0; i < STORAGE_CAPACITIES.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, STORAGE_CAPACITIES.get(i));
        }
        System.out.print("Wpisz numer pojemności (1–" + STORAGE_CAPACITIES.size() + ") lub 'q' aby wyjść: ");

        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        int storageChoice;
        try {
            storageChoice = Integer.parseInt(input);
            if (storageChoice < 1 || storageChoice > STORAGE_CAPACITIES.size()) {
                System.out.println("Proszę wpisać numer od 1 do " + STORAGE_CAPACITIES.size() + ".");
                return null;
            }
        } catch (NumberFormatException e) {
            System.out.println("Proszę wpisać poprawny numer.");
            return null;
        }

        return STORAGE_CAPACITIES.get(storageChoice - 1);
    }

    private static List<String> selectStates(Scanner scanner) {
        System.out.println("\n=== Wybór stanu urządzenia ===");
        System.out.println("Możesz wybrać kilka stanów, wpisując numery oddzielone spacją (np. '1 2').");
        System.out.println("Zostaw puste, aby wybrać wszystkie stany.");
        for (int i = 0; i < DEVICE_STATES.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, DEVICE_STATES.get(i));
        }
        System.out.print("Wpisz numery stanów (1–" + DEVICE_STATES.size() + ") lub 'q' aby wyjść: ");

        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        if (input.isEmpty()) {
            return Arrays.asList("new", "used", "damaged");
        }

        try {
            List<Integer> stateChoices = Arrays.stream(input.split("\\s+"))
                    .map(Integer::parseInt)
                    .filter(i -> i >= 1 && i <= DEVICE_STATES.size())
                    .distinct()
                    .collect(Collectors.toList());

            if (stateChoices.isEmpty()) {
                System.out.println("Nie wybrano żadnych poprawnych stanów. Używam wszystkich stanów.");
                return Arrays.asList("new", "used", "damaged");
            }

            List<String> selectedStates = stateChoices.stream()
                    .map(i -> {
                        switch (i) {
                            case 1: return "new";
                            case 2: return "used";
                            case 3: return "damaged";
                            default: return null;
                        }
                    })
                    .filter(s -> s != null)
                    .collect(Collectors.toList());

            System.out.println("Wybrane stany: " + selectedStates.stream()
                    .map(s -> DEVICE_STATES.get(Arrays.asList("new", "used", "damaged").indexOf(s)))
                    .collect(Collectors.joining(", ")));
            return selectedStates;
        } catch (NumberFormatException e) {
            System.out.println("Proszę wpisać poprawne numery oddzielone spacją. Używam wszystkich stanów.");
            return Arrays.asList("new", "used", "damaged");
        }
    }

    private static String selectLocation(Scanner scanner) {
        System.out.println("\n=== Wybór lokalizacji ===");
        System.out.println("Wpisz nazwę miasta (np. Warszawa, Kraków) lub zostaw puste dla całej Polski.");
        System.out.print("Lokalizacja lub 'q' aby wyjść: ");

        String location = scanner.nextLine().trim();
        if (location.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        if (!location.isEmpty() && !location.matches("[a-zA-ZąęłńóśźżĄĘŁŃÓŚŹŻ\\s-]{2,}")) {
            System.out.println("Proszę wpisać poprawną nazwę miasta (tylko litery, min. 2 znaki).");
            return null;
        }

        return location;
    }

    private static void displayResults(List<Offer> offers, String model, String storage, String location,
                                       PriceStats overallStats, PriceStats statsWithoutProtection,
                                       PriceStats statsWithProtection, List<Offer> recommendedWithout,
                                       List<Offer> recommendedWith, List<Offer> lowPriceOutlierOffers,
                                       Map<Offer, Double> zScoresWithoutProtection,
                                       Map<Offer, Double> zScoresWithProtection, PriceHistoryManager historyManager,
                                       DistanceCalculator distanceCalculator, Scanner scanner) {
        System.out.println("\n=== Wyniki wyszukiwania ===");
        System.out.printf("Znaleziono %d ofert dla: %s %s, Lokalizacja: %s\n",
                offers.size(), model, storage, location.isEmpty() ? "Cała Polska" : location);
        System.out.println("----------------------------------------");

        System.out.println("\nStatystyki cen (po odfiltrowaniu wartości odstających - ceny poniżej 5.0 i powyżej 95.0 percentyla):");
        System.out.println("• Ogólne:");
        System.out.printf("  - Średnia: %.2f PLN\n", overallStats.getAverage());
        System.out.printf("  - Odchylenie standardowe: %.2f PLN\n", overallStats.getStandardDeviation());
        System.out.printf("  - Percentyle: Q1=%.2f PLN, Mediana=%.2f PLN, Q3=%.2f PLN\n",
                overallStats.getPercentile25(), overallStats.getPercentile50(), overallStats.getPercentile75());
        System.out.println("• Bez pakietu ochronnego:");
        System.out.printf("  - Średnia: %.2f PLN\n", statsWithoutProtection.getAverage());
        System.out.printf("  - Odchylenie standardowe: %.2f PLN\n", statsWithoutProtection.getStandardDeviation());
        System.out.printf("  - Percentyle: Q1=%.2f PLN, Mediana=%.2f PLN, Q3=%.2f PLN\n",
                statsWithoutProtection.getPercentile25(), statsWithoutProtection.getPercentile50(), statsWithoutProtection.getPercentile75());
        System.out.println("• Z pakietem ochronnym:");
        System.out.printf("  - Średnia: %.2f PLN\n", statsWithProtection.getAverage());
        System.out.printf("  - Odchylenie standardowe: %.2f PLN\n", statsWithProtection.getStandardDeviation());
        System.out.printf("  - Percentyle: Q1=%.2f PLN, Mediana=%.2f PLN, Q3=%.2f PLN\n",
                statsWithProtection.getPercentile25(), statsWithProtection.getPercentile50(), statsWithProtection.getPercentile75());
        System.out.println("----------------------------------------");

        System.out.println("\nNotatka: Rekomendacje uwzględniają oferty z ceną poniżej mediany i z-score poniżej -0.5. " +
                "Oferty zgodne z trendem cenowym są oznaczone w kolumnie 'Rekomendacja'.");
        displayRecommendations("Oferty bez pakietu ochronnego", recommendedWithout, statsWithoutProtection, zScoresWithoutProtection, overallStats, historyManager, distanceCalculator);
        displayRecommendations("Oferty z pakietem ochronnym", recommendedWith, statsWithProtection, zScoresWithProtection, overallStats, historyManager, distanceCalculator);
        displayLowPriceOutlierOffers("Podejrzane tanie oferty (ceny poniżej 5.0 percentyla)", lowPriceOutlierOffers, statsWithoutProtection, statsWithProtection, zScoresWithoutProtection, zScoresWithProtection, overallStats, historyManager, distanceCalculator);

        // Prompt user to open links for "Świetna" recommendations from low price outliers
        promptOpenSuperbOfferLinks(recommendedWithout, recommendedWith, lowPriceOutlierOffers, statsWithoutProtection, statsWithProtection, zScoresWithoutProtection, zScoresWithProtection, historyManager, scanner);
    }

    private static void displayRecommendations(String title, List<Offer> recommendations, PriceStats stats, Map<Offer, Double> zScores, PriceStats overallStats, PriceHistoryManager historyManager, DistanceCalculator distanceCalculator) {
        if (recommendations.isEmpty()) {
            System.out.println("\n" + title + ":");
            System.out.println("Brak rekomendowanych ofert (cena poniżej mediany i z-score poniżej -0.5).");
            System.out.println("----------------------------------------");
            return;
        }

        double shippingCost = 20.0;
        double listingFee = 10.0;
        double sellingPrice = overallStats.getPercentile25();

        System.out.println("\n" + title + ":");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-48s | %-10s | %-19s | %-24s | %-23s | %-7s | %-15s | %-17s | %-26s | %-142s |\n",
                "Tytuł oferty", "Cena (PLN)", "Rekomendacja", "Data", "Lokalizacja", "Z-Score", "Cena sprzedaży", "Marża", "Trend cenowy", "URL");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");

        for (Offer offer : recommendations) {
            String shortTitle = offer.getTitle().length() > 48 ? offer.getTitle().substring(0, 45) + "..." : offer.getTitle();
            double zScore = zScores.getOrDefault(offer, 0.0);
            String trendAnalysis = historyManager.analyzePriceTrend(offer.getModel(), offer.getStorageCapacity(), offer.hasProtectionPackage(), offer.getPrice());
            RecommendationAssessment assessment = getRecommendationAssessment(offer.getPrice(), stats, zScore, trendAnalysis);

            double purchasePrice = offer.getPrice();
            double totalCosts = purchasePrice + shippingCost + listingFee;
            double profitMargin = sellingPrice - totalCosts;
            double profitMarginPercentage = (profitMargin / sellingPrice) * 100;
            String marginText = String.format("%.2f (%.2f%%)", profitMargin, profitMarginPercentage);

            String dateDisplay = offer.getDate().toString();
            if (!offer.getDateStatus().isEmpty()) {
                dateDisplay += " (" + offer.getDateStatus() + ")";
            }

            String locationDisplay = offer.getLocation().isEmpty() ? "Cała Polska" : offer.getLocation();
            if (!offer.getLocation().isEmpty()) {
                double[] offerCoordinates = distanceCalculator.getCoordinates(offer.getLocation());
                if (offerCoordinates != null) {
                    double distance = distanceCalculator.calculateHaversineDistance(
                            SOCHACZEW_LAT, SOCHACZEW_LON,
                            offerCoordinates[0], offerCoordinates[1]
                    );
                    locationDisplay = String.format("%s (%.2f km)", offer.getLocation(), distance);
                } else {
                    locationDisplay = offer.getLocation() + " (Brak danych)";
                }
            }

            if (locationDisplay.length() > 23) {
                locationDisplay = locationDisplay.substring(0, 20) + "...";
            }

            System.out.printf("| %-48s | %-10.2f | %-19s | %-24s | %-23s | %-7.2f | %-15.2f | %-17s | %-26s | %-142s |\n",
                    shortTitle, offer.getPrice(), assessment.toString(), dateDisplay,
                    locationDisplay, zScore, sellingPrice, marginText, trendAnalysis, offer.getUrl());
        }
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.println("----------------------------------------");
    }

    private static void displayLowPriceOutlierOffers(String title, List<Offer> lowPriceOutlierOffers, PriceStats statsWithoutProtection, PriceStats statsWithProtection, Map<Offer, Double> zScoresWithoutProtection, Map<Offer, Double> zScoresWithProtection, PriceStats overallStats, PriceHistoryManager historyManager, DistanceCalculator distanceCalculator) {
        if (lowPriceOutlierOffers.isEmpty()) {
            System.out.println("\n" + title + ":");
            System.out.println("Brak podejrzanych tanich ofert (ceny poniżej 5.0 percentyla).");
            System.out.println("----------------------------------------");
            return;
        }

        double shippingCost = 20.0;
        double listingFee = 10.0;
        double sellingPrice = overallStats.getPercentile25();

        lowPriceOutlierOffers.sort(Comparator.comparingDouble(Offer::getPrice));

        System.out.println("\n" + title + ":");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-48s | %-10s | %-19s | %-24s | %-23s | %-7s | %-15s | %-17s | %-26s | %-142s |\n",
                "Tytuł oferty", "Cena (PLN)", "Rekomendacja", "Data", "Lokalizacja", "Z-Score", "Cena sprzedaży", "Marża", "Trend cenowy", "URL");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");

        for (Offer offer : lowPriceOutlierOffers) {
            String shortTitle = offer.getTitle().length() > 48 ? offer.getTitle().substring(0, 45) + "..." : offer.getTitle();
            PriceStats relevantStats = offer.hasProtectionPackage() ? statsWithProtection : statsWithoutProtection;
            Map<Offer, Double> relevantZScores = offer.hasProtectionPackage() ? zScoresWithProtection : zScoresWithoutProtection;
            double zScore = relevantZScores.getOrDefault(offer, 0.0);
            String trendAnalysis = historyManager.analyzePriceTrend(offer.getModel(), offer.getStorageCapacity(), offer.hasProtectionPackage(), offer.getPrice());
            RecommendationAssessment assessment = getRecommendationAssessment(offer.getPrice(), relevantStats, zScore, trendAnalysis);

            double purchasePrice = offer.getPrice();
            double totalCosts = purchasePrice + shippingCost + listingFee;
            double profitMargin = sellingPrice - totalCosts;
            double profitMarginPercentage = (profitMargin / sellingPrice) * 100;
            String marginText = String.format("%.2f (%.2f%%)", profitMargin, profitMarginPercentage);

            String dateDisplay = offer.getDate().toString();
            if (!offer.getDateStatus().isEmpty()) {
                dateDisplay += " (" + offer.getDateStatus() + ")";
            }

            String locationDisplay = offer.getLocation().isEmpty() ? "Cała Polska" : offer.getLocation();
            if (!offer.getLocation().isEmpty()) {
                double[] offerCoordinates = distanceCalculator.getCoordinates(offer.getLocation());
                if (offerCoordinates != null) {
                    double distance = distanceCalculator.calculateHaversineDistance(
                            SOCHACZEW_LAT, SOCHACZEW_LON,
                            offerCoordinates[0], offerCoordinates[1]
                    );
                    locationDisplay = String.format("%s (%.2f km)", offer.getLocation(), distance);
                } else {
                    locationDisplay = offer.getLocation() + " (Brak danych)";
                }
            }

            if (locationDisplay.length() > 23) {
                locationDisplay = locationDisplay.substring(0, 20) + "...";
            }

            System.out.printf("| %-48s | %-10.2f | %-19s | %-24s | %-23s | %-7.2f | %-15.2f | %-17s | %-26s | %-142s |\n",
                    shortTitle, offer.getPrice(), assessment.toString(), dateDisplay,
                    locationDisplay, zScore, sellingPrice, marginText, trendAnalysis, offer.getUrl());
        }
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.println("----------------------------------------");
    }

    private static void promptOpenSuperbOfferLinks(List<Offer> recommendedWithout, List<Offer> recommendedWith,
                                                   List<Offer> lowPriceOutlierOffers, PriceStats statsWithoutProtection,
                                                   PriceStats statsWithProtection, Map<Offer, Double> zScoresWithoutProtection,
                                                   Map<Offer, Double> zScoresWithProtection, PriceHistoryManager historyManager,
                                                   Scanner scanner) {
        // Filter offers with "Świetna" recommendation from low price outliers
        List<Offer> superbOutliers = lowPriceOutlierOffers.stream()
                .filter(offer -> {
                    PriceStats relevantStats = offer.hasProtectionPackage() ? statsWithProtection : statsWithoutProtection;
                    Map<Offer, Double> relevantZScores = offer.hasProtectionPackage() ? zScoresWithProtection : zScoresWithoutProtection;
                    double zScore = relevantZScores.getOrDefault(offer, 0.0);
                    String trendAnalysis = historyManager.analyzePriceTrend(offer.getModel(), offer.getStorageCapacity(), offer.hasProtectionPackage(), offer.getPrice());
                    RecommendationAssessment assessment = getRecommendationAssessment(offer.getPrice(), relevantStats, zScore, trendAnalysis);
                    return assessment.getStatus().startsWith("Świetna");
                })
                .collect(Collectors.toList());

        // Check if there are any superb offers in low price outliers
        if (superbOutliers.isEmpty()) {
            System.out.println("\nBrak ofert z rekomendacją 'Świetna' w tabeli 'Podejrzane tanie oferty'.");
            return;
        }

        System.out.printf("\nZnaleziono %d ofert z rekomendacją 'Świetna' w tabeli 'Podejrzane tanie oferty'. Czy chcesz je automatycznie otworzyć? (tak/nie): ", superbOutliers.size());
        String input = scanner.nextLine().trim().toLowerCase();

        if (!input.equals("tak")) {
            System.out.println("Powrót do wyszukiwania.");
            return;
        }

        // Open the superb offer links from low price outliers
        System.out.printf("Otwieram %d linków do ofert 'Świetna' z tabeli 'Podejrzane tanie oferty'...\n", superbOutliers.size());
        for (Offer offer : superbOutliers) {
            try {
                Desktop.getDesktop().browse(new URI(offer.getUrl()));
                // Add a small delay to avoid overwhelming the browser
                Thread.sleep(500);
            } catch (IOException | URISyntaxException e) {
                System.err.println("Błąd podczas otwierania linku: " + offer.getUrl() + " - " + e.getMessage());
            } catch (InterruptedException e) {
                System.err.println("Przerwano otwieranie linków: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Zakończono otwieranie linków.");
    }
}