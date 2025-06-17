package org.example;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
        put("iPhone 11–13", Arrays.asList(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
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
    private static final int MAX_CONCURRENT_COMBINATIONS = 4; // Maksymalna liczba równoległych kombinacji
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(MAX_CONCURRENT_COMBINATIONS);

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean continueSearching = true;

        LocationCacheManager locationCacheManager = new LocationCacheManager();
        DistanceCalculator distanceCalculator = new DistanceCalculator(locationCacheManager);

        System.out.println("Ustawiona lokalizacja użytkownika: Sochaczew, Mazowieckie");

        while (continueSearching) {
            System.out.println("\n=== iFlip ===");
            System.out.println("\n=== Wyszukiwarka ofert iPhone na OLX ===");
            System.out.println("Wybierz tryb wyszukiwania:");
            System.out.println("1. Codzienna rekomendacja (tylko oferty z dzisiaj, wybrane modele i pojemności)");
            System.out.println("2. Standardowe wyszukiwanie");
            System.out.print("Wpisz numer (1–2) lub 'q' aby wyjść: ");

            String modeInput = scanner.nextLine().trim();
            if (modeInput.equalsIgnoreCase("q")) {
                System.out.println("Zakończono program.");
                EXECUTOR.shutdown();
                break;
            }

            int modeChoice;
            try {
                modeChoice = Integer.parseInt(modeInput);
                if (modeChoice < 1 || modeChoice > 2) {
                    System.out.println("Proszę wpisać numer od 1 do 2.");
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Proszę wpisać poprawny numer.");
                continue;
            }

            if (modeChoice == 1) {
                // Codzienna rekomendacja
                handleDailyRecommendation(scanner, locationCacheManager, distanceCalculator);
            } else {
                // Standardowe wyszukiwanie
                handleStandardSearch(scanner, locationCacheManager, distanceCalculator);
            }

            System.out.print("\nCzy chcesz wyszukać ponownie? (tak/nie): ");
            continueSearching = scanner.nextLine().trim().toLowerCase().equals("tak");
        }

        System.out.println("\nDziękujemy za skorzystanie z wyszukiwarki OLX!");
        scanner.close();
        EXECUTOR.shutdown();
    }

    private static class CombinationResult {
        private final String model;
        private final String storage;
        private final List<Offer> allOffers;
        private final List<Offer> todayOffers;
        private final PriceStats overallStats;
        private final PriceStats statsWithProtection;
        private final PriceStats statsWithoutProtection;
        private final List<Offer> recommendedOffersWithoutProtection;
        private final List<Offer> recommendedOffersWithProtection;
        private final List<Offer> lowPriceOutlierOffers;
        private final Map<Offer, Double> zScoresWithoutProtection;
        private final Map<Offer, Double> zScoresWithProtection;

        public CombinationResult(String model, String storage, List<Offer> allOffers, List<Offer> todayOffers,
                                 PriceStats overallStats, PriceStats statsWithProtection, PriceStats statsWithoutProtection,
                                 List<Offer> recommendedOffersWithoutProtection, List<Offer> recommendedOffersWithProtection,
                                 List<Offer> lowPriceOutlierOffers, Map<Offer, Double> zScoresWithoutProtection,
                                 Map<Offer, Double> zScoresWithProtection) {
            this.model = model;
            this.storage = storage;
            this.allOffers = allOffers;
            this.todayOffers = todayOffers;
            this.overallStats = overallStats;
            this.statsWithProtection = statsWithProtection;
            this.statsWithoutProtection = statsWithoutProtection;
            this.recommendedOffersWithoutProtection = recommendedOffersWithoutProtection;
            this.recommendedOffersWithProtection = recommendedOffersWithProtection;
            this.lowPriceOutlierOffers = lowPriceOutlierOffers;
            this.zScoresWithoutProtection = zScoresWithoutProtection;
            this.zScoresWithProtection = zScoresWithProtection;
        }
    }

    private static void handleDailyRecommendation(Scanner scanner, LocationCacheManager locationCacheManager, DistanceCalculator distanceCalculator) {
        List<String> selectedModels = selectModels(scanner);
        if (selectedModels == null) return;

        List<String> selectedStorages = selectStorageCapacities(scanner);
        if (selectedStorages == null) return;

        List<String> selectedStates = selectStates(scanner);
        if (selectedStates == null) return;

        String location = selectLocation(scanner);
        if (location == null) return;

        System.out.println("\n=== Podsumowanie wyborów ===");
        System.out.printf("Modele: %s\n", String.join(", ", selectedModels));
        System.out.printf("Pojemności: %s\n", String.join(", ", selectedStorages));
        System.out.printf("Stan: %s\n", selectedStates.isEmpty() ? "Wszystkie" : String.join(", ", selectedStates.stream()
                .map(s -> DEVICE_STATES.get(Arrays.asList("new", "used", "damaged").indexOf(s)))
                .collect(Collectors.toList())));
        System.out.printf("Lokalizacja: %s\n", location.isEmpty() ? "Cała Polska" : location);
        System.out.println("Tylko oferty z dzisiaj: Tak");
        System.out.print("\nCzy chcesz kontynuować z tymi ustawieniami? (tak/nie): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!confirm.equals("tak")) {
            return;
        }

        OlxScraper scraper = new OlxScraper();
        PriceHistoryManager historyManager = new PriceHistoryManager();

        // Lista przechowująca wyniki dla wszystkich kombinacji
        List<CompletableFuture<CombinationResult>> futures = new ArrayList<>();
        List<CombinationResult> combinationResults = new ArrayList<>();

        // Równoległe scrapowanie dla każdej kombinacji model + pojemność
        for (String model : selectedModels) {
            for (String storage : selectedStorages) {
                String finalModel = model;
                String finalStorage = storage;
                String statesDisplay = selectedStates.isEmpty() ? "wszystkie stany" : String.join(", ", selectedStates.stream()
                        .map(s -> DEVICE_STATES.get(Arrays.asList("new", "used", "damaged").indexOf(s)))
                        .collect(Collectors.toList()));
                System.out.println("\nPlanuję skanowanie ofert dla: " + finalModel + " " + finalStorage + " (" + statesDisplay + ")");

                futures.add(CompletableFuture.supplyAsync(() -> {
                    System.out.println("Skanuję oferty dla: " + finalModel + " " + finalStorage + " (" + statesDisplay + ")");
                    List<Offer> allOffers = scraper.scrapeOffers(finalModel, finalStorage, location, selectedStates);
                    List<Offer> todayOffers = allOffers.stream()
                            .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                            .collect(Collectors.toList());

                    System.out.printf("[Skanowanie] Model: %s, Pojemność: %s, Stany: %s, Liczba ofert z dzisiaj: %d, Wszystkie oferty: %d\n",
                            finalModel, finalStorage, statesDisplay, todayOffers.size(), allOffers.size());

                    if (todayOffers.isEmpty()) {
                        return new CombinationResult(finalModel, finalStorage, allOffers, todayOffers,
                                new PriceStats(0, 0, 0, 0, 0),
                                new PriceStats(0, 0, 0, 0, 0),
                                new PriceStats(0, 0, 0, 0, 0),
                                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                                new HashMap<>(), new HashMap<>());
                    }

                    historyManager.savePrices(allOffers);

                    PriceAnalyzer analyzer = new PriceAnalyzer(allOffers);
                    List<Offer> lowPriceOutlierOffers = new ArrayList<>();

                    PriceStats overallStats = analyzer.getOverallPriceStats(lowPriceOutlierOffers);
                    PriceStats statsWithProtection = analyzer.getPriceStatsWithProtection(lowPriceOutlierOffers);
                    PriceStats statsWithoutProtection = analyzer.getPriceStatsWithoutProtection(lowPriceOutlierOffers);

                    Map<Offer, Double> zScoresWithoutProtection = analyzer.calculateZScores(
                            allOffers.stream()
                                    .filter(offer -> !offer.hasProtectionPackage())
                                    .filter(offer -> offer.getPrice() > 0)
                                    .collect(Collectors.toList()),
                            statsWithoutProtection
                    );
                    Map<Offer, Double> zScoresWithProtection = analyzer.calculateZScores(
                            allOffers.stream()
                                    .filter(Offer::hasProtectionPackage)
                                    .filter(offer -> offer.getPrice() > 0)
                                    .collect(Collectors.toList()),
                            statsWithProtection
                    );

                    List<Offer> recommendedOffersWithoutProtection = analyzer.getRecommendedOffersWithoutProtection(-0.5, location.isEmpty() ? null : location, historyManager)
                            .stream()
                            .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                            .collect(Collectors.toList());
                    List<Offer> recommendedOffersWithProtection = analyzer.getRecommendedOffersWithProtection(-0.5, location.isEmpty() ? null : location, historyManager)
                            .stream()
                            .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                            .collect(Collectors.toList());

                    List<Offer> todayLowPriceOutlierOffers = lowPriceOutlierOffers.stream()
                            .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                            .collect(Collectors.toList());

                    return new CombinationResult(finalModel, finalStorage, allOffers, todayOffers,
                            overallStats, statsWithProtection, statsWithoutProtection,
                            recommendedOffersWithoutProtection, recommendedOffersWithProtection,
                            todayLowPriceOutlierOffers, zScoresWithoutProtection, zScoresWithProtection);
                }, EXECUTOR));
            }
        }

        // Oczekiwanie na zakończenie wszystkich futures
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        combinationResults = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Agregacja wyników do globalnych list
        List<Offer> allTodayOffers = new ArrayList<>();
        List<Offer> allRecommendedWithoutProtection = new ArrayList<>();
        List<Offer> allRecommendedWithProtection = new ArrayList<>();
        List<Offer> allLowPriceOutlierOffers = new ArrayList<>();
        Map<String, PriceStats> overallStatsMap = new HashMap<>();
        Map<String, PriceStats> statsWithProtectionMap = new HashMap<>();
        Map<String, PriceStats> statsWithoutProtectionMap = new HashMap<>();
        Map<String, Map<Offer, Double>> zScoresWithoutProtectionMap = new HashMap<>();
        Map<String, Map<Offer, Double>> zScoresWithProtectionMap = new HashMap<>();

        boolean hasOffers = false;
        for (CombinationResult result : combinationResults) {
            String key = result.model + " " + result.storage;
            if (!result.todayOffers.isEmpty()) {
                hasOffers = true;
                allTodayOffers.addAll(result.todayOffers);
                allRecommendedWithoutProtection.addAll(result.recommendedOffersWithoutProtection);
                allRecommendedWithProtection.addAll(result.recommendedOffersWithProtection);
                allLowPriceOutlierOffers.addAll(result.lowPriceOutlierOffers);
                overallStatsMap.put(key, result.overallStats);
                statsWithProtectionMap.put(key, result.statsWithProtection);
                statsWithoutProtectionMap.put(key, result.statsWithoutProtection);
                zScoresWithoutProtectionMap.put(key, result.zScoresWithoutProtection);
                zScoresWithProtectionMap.put(key, result.zScoresWithProtection);

                // Wyświetlanie statystyk dla każdej kombinacji
                String statesDisplay = selectedStates.isEmpty() ? "wszystkie stany" : String.join(", ", selectedStates.stream()
                        .map(s -> DEVICE_STATES.get(Arrays.asList("new", "used", "damaged").indexOf(s)))
                        .collect(Collectors.toList()));
                System.out.println("\n=== Statystyki dla: " + result.model + " " + result.storage + " (" + statesDisplay + ") ===");
                displayStats(result.model, result.storage, result.overallStats, result.statsWithoutProtection, result.statsWithProtection);
            }
        }

        if (!hasOffers) {
            String statesDisplay = selectedStates.isEmpty() ? "wszystkie stany" : String.join(", ", selectedStates.stream()
                    .map(s -> DEVICE_STATES.get(Arrays.asList("new", "used", "damaged").indexOf(s)))
                    .collect(Collectors.toList()));
            System.out.println("\nBrak ofert z dzisiaj dla wybranych modeli: " + String.join(", ", selectedModels) +
                    ", pojemności: " + String.join(", ", selectedStorages) + ", w stanie: " + statesDisplay);
            return;
        }

        // Wyświetlanie globalnych tabel
        displayResults(allTodayOffers, selectedModels, selectedStorages, location, true,
                overallStatsMap, statsWithoutProtectionMap, statsWithProtectionMap,
                allRecommendedWithoutProtection, allRecommendedWithProtection,
                allLowPriceOutlierOffers, zScoresWithoutProtectionMap, zScoresWithProtectionMap,
                historyManager, distanceCalculator, scanner);
    }

    private static void handleStandardSearch(Scanner scanner, LocationCacheManager locationCacheManager, DistanceCalculator distanceCalculator) {
        String selectedModel = selectModel(scanner);
        if (selectedModel == null) return;

        String selectedStorage = selectStorage(scanner);
        if (selectedStorage == null) return;

        List<String> selectedStates = selectStates(scanner);
        if (selectedStates == null) return;

        String location = selectLocation(scanner);
        if (location == null) return;

        Boolean todayOnly = selectTodayOnly(scanner);
        if (todayOnly == null) return;

        System.out.println("\n=== Podsumowanie wyborów ===");
        System.out.printf("Model: %s\n", selectedModel);
        System.out.printf("Pojemność: %s\n", selectedStorage);
        System.out.printf("Stan: %s\n", selectedStates.isEmpty() ? "Wszystkie" : String.join(", ", selectedStates.stream()
                .map(s -> DEVICE_STATES.get(Arrays.asList("new", "used", "damaged").indexOf(s)))
                .collect(Collectors.toList())));
        System.out.printf("Lokalizacja: %s\n", location.isEmpty() ? "Cała Polska" : location);
        System.out.printf("Tylko oferty z dzisiaj: %s\n", todayOnly ? "Tak" : "Nie");
        System.out.print("\nCzy chcesz kontynuować z tymi ustawieniami? (tak/nie): ");
        String confirm = scanner.nextLine().trim().toLowerCase();
        if (!confirm.equals("tak")) {
            return;
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

        Map<String, PriceStats> overallStatsMap = new HashMap<>();
        Map<String, PriceStats> statsWithProtectionMap = new HashMap<>();
        Map<String, PriceStats> statsWithoutProtectionMap = new HashMap<>();
        Map<String, Map<Offer, Double>> zScoresWithoutProtectionMap = new HashMap<>();
        Map<String, Map<Offer, Double>> zScoresWithProtectionMap = new HashMap<>();
        String key = selectedModel + " " + selectedStorage;
        overallStatsMap.put(key, overallStats);
        statsWithProtectionMap.put(key, statsWithProtection);
        statsWithoutProtectionMap.put(key, statsWithoutProtection);
        zScoresWithoutProtectionMap.put(key, zScoresWithoutProtection);
        zScoresWithProtectionMap.put(key, zScoresWithProtection);

        displayResults(offers, Collections.singletonList(selectedModel), Collections.singletonList(selectedStorage), location, todayOnly,
                overallStatsMap, statsWithoutProtectionMap, statsWithProtectionMap,
                recommendedOffersWithoutProtection, recommendedOffersWithProtection,
                lowPriceOutlierOffers, zScoresWithoutProtectionMap, zScoresWithProtectionMap,
                historyManager, distanceCalculator, scanner);
    }

    private static List<String> selectModels(Scanner scanner) {
        System.out.println("=== Wybór modeli iPhone’a ===");
        System.out.println("Możesz wybrać kilka modeli, wpisując numery oddzielone spacją (np. '1 2').");
        System.out.println("Wpisz przynajmniej jeden model.");
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

        System.out.println("\nWybierz modele z grupy " + selectedGroup + ":");
        for (int i = 0; i < modelIndices.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, IPHONE_MODELS.get(modelIndices.get(i)));
        }
        System.out.print("Wpisz numery modeli (1–" + modelIndices.size() + ") lub 'q' aby wyjść: ");

        input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        if (input.isEmpty()) {
            System.out.println("Proszę wybrać przynajmniej jeden model.");
            return null;
        }

        try {
            List<Integer> modelChoices = Arrays.stream(input.split("\\s+"))
                    .map(Integer::parseInt)
                    .filter(i -> i >= 1 && i <= modelIndices.size())
                    .distinct()
                    .collect(Collectors.toList());

            if (modelChoices.isEmpty()) {
                System.out.println("Nie wybrano żadnych poprawnych modeli.");
                return null;
            }

            List<String> selectedModels = modelChoices.stream()
                    .map(i -> IPHONE_MODELS.get(modelIndices.get(i - 1)))
                    .collect(Collectors.toList());

            System.out.println("Wybrane modele: " + String.join(", ", selectedModels));
            return selectedModels;
        } catch (NumberFormatException e) {
            System.out.println("Proszę wpisać poprawne numery oddzielone spacją.");
            return null;
        }
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

    private static List<String> selectStorageCapacities(Scanner scanner) {
        System.out.println("\n=== Wybór pojemności pamięci ===");
        System.out.println("Możesz wybrać kilka pojemności, wpisując numery oddzielone spacją (np. '1 2').");
        System.out.println("Wpisz przynajmniej jedną pojemność.");
        for (int i = 0; i < STORAGE_CAPACITIES.size(); i++) {
            System.out.printf("%d. %s\n", i + 1, STORAGE_CAPACITIES.get(i));
        }
        System.out.print("Wpisz numery pojemności (1–" + STORAGE_CAPACITIES.size() + ") lub 'q' aby wyjść: ");

        String input = scanner.nextLine().trim();
        if (input.equalsIgnoreCase("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        if (input.isEmpty()) {
            System.out.println("Proszę wybrać przynajmniej jedną pojemność.");
            return null;
        }

        try {
            List<Integer> storageChoices = Arrays.stream(input.split("\\s+"))
                    .map(Integer::parseInt)
                    .filter(i -> i >= 1 && i <= STORAGE_CAPACITIES.size())
                    .distinct()
                    .collect(Collectors.toList());

            if (storageChoices.isEmpty()) {
                System.out.println("Nie wybrano żadnych poprawnych pojemności.");
                return null;
            }

            List<String> selectedStorages = storageChoices.stream()
                    .map(i -> STORAGE_CAPACITIES.get(i - 1))
                    .collect(Collectors.toList());

            System.out.println("Wybrane pojemności: " + String.join(", ", selectedStorages));
            return selectedStorages;
        } catch (NumberFormatException e) {
            System.out.println("Proszę wpisać poprawne numery oddzielone spacją.");
            return null;
        }
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

    private static Boolean selectTodayOnly(Scanner scanner) {
        System.out.println("\n=== Wybór ofert z dzisiaj ===");
        System.out.println("Czy chcesz zobaczyć tylko oferty dodane dzisiaj? (tak/nie)");
        System.out.print("Wpisz 'tak', 'nie' lub 'q' aby wyjść: ");

        String input = scanner.nextLine().trim().toLowerCase();
        if (input.equals("q")) {
            System.out.println("Anulowano wybór.");
            return null;
        }

        if (!input.equals("tak") && !input.equals("nie")) {
            System.out.println("Proszę wpisać 'tak' lub 'nie'.");
            return null;
        }

        return input.equals("tak");
    }

    private static void displayStats(String model, String storage, PriceStats overallStats,
                                     PriceStats statsWithoutProtection, PriceStats statsWithProtection) {
        System.out.println("\nStatystyki cen (po odfiltrowaniu wartości odstających - ceny poniżej 5.0 i powyżej 95.0 percentyla, na podstawie wszystkich ofert):");
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
    }

    private static void displayResults(List<Offer> offers, List<String> models, List<String> storages, String location, Boolean todayOnly,
                                       Map<String, PriceStats> overallStatsMap, Map<String, PriceStats> statsWithoutProtectionMap,
                                       Map<String, PriceStats> statsWithProtectionMap, List<Offer> recommendedWithout,
                                       List<Offer> recommendedWith, List<Offer> lowPriceOutlierOffers,
                                       Map<String, Map<Offer, Double>> zScoresWithoutProtectionMap,
                                       Map<String, Map<Offer, Double>> zScoresWithProtectionMap,
                                       PriceHistoryManager historyManager, DistanceCalculator distanceCalculator, Scanner scanner) {
        // Filtrowanie ofert, jeśli wybrano tylko dzisiejsze
        List<Offer> filteredOffers = todayOnly ?
                offers.stream()
                        .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                        .collect(Collectors.toList()) :
                offers;

        List<Offer> filteredRecommendedWithout = todayOnly ?
                recommendedWithout.stream()
                        .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                        .collect(Collectors.toList()) :
                recommendedWithout;

        List<Offer> filteredRecommendedWith = todayOnly ?
                recommendedWith.stream()
                        .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                        .collect(Collectors.toList()) :
                recommendedWith;

        List<Offer> filteredLowPriceOutlierOffers = todayOnly ?
                lowPriceOutlierOffers.stream()
                        .filter(offer -> offer.getDate().isEqual(LocalDate.now()))
                        .collect(Collectors.toList()) :
                lowPriceOutlierOffers;

        System.out.println("\n=== Wyniki wyszukiwania ===");
        System.out.printf("Znaleziono %d ofert dla modeli: %s, pojemności: %s, Lokalizacja: %s%s\n",
                filteredOffers.size(), String.join(", ", models), String.join(", ", storages),
                location.isEmpty() ? "Cała Polska" : location, todayOnly ? " (tylko dzisiaj)" : "");
        System.out.println("----------------------------------------");

        System.out.println("\nNotatka: Rekomendacje uwzględniają oferty z ceną poniżej mediany i z-score poniżej -0.5. " +
                "Oferty zgodne z trendem cenowym są oznaczone w kolumnie 'Rekomendacja'.");
        displayRecommendations("Oferty bez pakietu ochronnego", filteredRecommendedWithout, statsWithoutProtectionMap, zScoresWithoutProtectionMap, overallStatsMap, historyManager, distanceCalculator);
        displayRecommendations("Oferty z pakietem ochronnym", filteredRecommendedWith, statsWithProtectionMap, zScoresWithProtectionMap, overallStatsMap, historyManager, distanceCalculator);
        displayLowPriceOutliers("Podejrzane tanie oferty (ceny poniżej 5.0 percentyla)", filteredLowPriceOutlierOffers, statsWithoutProtectionMap, statsWithProtectionMap, zScoresWithoutProtectionMap, zScoresWithProtectionMap, overallStatsMap, historyManager, distanceCalculator);

        // Informacja o najlepszych ofertach
        promptOpenOffers(filteredRecommendedWithout, filteredRecommendedWith, filteredLowPriceOutlierOffers, statsWithoutProtectionMap, statsWithProtectionMap, zScoresWithoutProtectionMap, zScoresWithProtectionMap, historyManager, scanner);
    }

    private static void displayRecommendations(String title, List<Offer> recommendations, Map<String, PriceStats> statsMap, Map<String, Map<Offer, Double>> zScoresMap, Map<String, PriceStats> overallStatsMap, PriceHistoryManager historyManager, DistanceCalculator distanceCalculator) {
        if (recommendations.isEmpty()) {
            System.out.println("\n" + title + ":");
            System.out.println("Brak rekomendowanych ofert (cena poniżej mediany i z-score poniżej -0.5).");
            System.out.println("----------------------------------------");
            return;
        }

        double shippingCost = 20.0;
        double listingFee = 10.0;

        // Sortowanie według marży (malejąco)
        List<Offer> sortedRecommendations = recommendations.stream()
                .sorted((o1, o2) -> {
                    String key1 = o1.getModel() + " " + o1.getStorageCapacity();
                    String key2 = o2.getModel() + " " + o2.getStorageCapacity();
                    double sellingPrice1 = overallStatsMap.getOrDefault(key1, new PriceStats(0, 0, 0, 0, 0)).getPercentile25();
                    double sellingPrice2 = overallStatsMap.getOrDefault(key2, new PriceStats(0, 0, 0, 0, 0)).getPercentile25();
                    double margin1 = sellingPrice1 - (o1.getPrice() + shippingCost + listingFee);
                    double margin2 = sellingPrice2 - (o2.getPrice() + shippingCost + listingFee);
                    return Double.compare(margin2, margin1); // Malejąco
                })
                .collect(Collectors.toList());

        System.out.println("\n" + title + ":");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-48s | %-10s | %-19s | %-24s | %-23s | %-7s | %-15s | %-17s | %-26s | %-142s |\n",
                "Tytuł oferty", "Cena (PLN)", "Rekomendacja", "Data", "Lokalizacja", "Z-Score", "Cena sprzedaży", "Marża", "Trend cenowy", "URL");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");

        for (Offer offer : sortedRecommendations) {
            String key = offer.getModel() + " " + offer.getStorageCapacity();
            PriceStats stats = statsMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0));
            Map<Offer, Double> zScores = zScoresMap.getOrDefault(key, new HashMap<>());
            double sellingPrice = overallStatsMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0)).getPercentile25();

            String shortTitle = String.format("%s %s %s", offer.getModel(), offer.getStorageCapacity(), offer.getTitle());
            if (shortTitle.length() > 48) {
                shortTitle = shortTitle.substring(0, 45) + "...";
            }
            double zScore = zScores.getOrDefault(offer, 0.0);
            String trendAnalysis = historyManager.analyzePriceTrend(offer.getModel(), offer.getStorageCapacity(), offer.hasProtectionPackage(), offer.getPrice());
            RecommendationAssessment assessment = getRecommendationAssessment(offer.getPrice(), stats, zScore, trendAnalysis);

            double purchasePrice = offer.getPrice();
            double totalCosts = purchasePrice + shippingCost + listingFee;
            double profitMargin = sellingPrice - totalCosts;
            double profitMarginPercentage = sellingPrice != 0 ? (profitMargin / sellingPrice) * 100 : 0;
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

    private static void displayLowPriceOutliers(String title, List<Offer> lowPriceOutlierOffers, Map<String, PriceStats> statsWithoutProtectionMap, Map<String, PriceStats> statsWithProtectionMap, Map<String, Map<Offer, Double>> zScoresWithoutProtectionMap, Map<String, Map<Offer, Double>> zScoresWithProtectionMap, Map<String, PriceStats> overallStatsMap, PriceHistoryManager historyManager, DistanceCalculator distanceCalculator) {
        if (lowPriceOutlierOffers.isEmpty()) {
            System.out.println("\n" + title + ":");
            System.out.println("Brak podejrzanych tanich ofert (ceny poniżej 5.0 percentyla).");
            System.out.println("----------------------------------------");
            return;
        }

        double shippingCost = 20.0;
        double listingFee = 10.0;

        // Sortowanie według marży (malejąco)
        List<Offer> sortedOutliers = lowPriceOutlierOffers.stream()
                .sorted((o1, o2) -> {
                    String key1 = o1.getModel() + " " + o1.getStorageCapacity();
                    String key2 = o2.getModel() + " " + o2.getStorageCapacity();
                    double sellingPrice1 = overallStatsMap.getOrDefault(key1, new PriceStats(0, 0, 0, 0, 0)).getPercentile25();
                    double sellingPrice2 = overallStatsMap.getOrDefault(key2, new PriceStats(0, 0, 0, 0, 0)).getPercentile25();
                    double margin1 = sellingPrice1 - (o1.getPrice() + shippingCost + listingFee);
                    double margin2 = sellingPrice2 - (o2.getPrice() + shippingCost + listingFee);
                    return Double.compare(margin2, margin1); // Malejąco
                })
                .collect(Collectors.toList());

        System.out.println("\n" + title + ":");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");
        System.out.printf("| %-48s | %-10s | %-19s | %-24s | %-23s | %-7s | %-15s | %-17s | %-26s | %-142s |\n",
                "Tytuł oferty", "Cena (PLN)", "Rekomendacja", "Data", "Lokalizacja", "Z-Score", "Cena sprzedaży", "Marża", "Trend cenowy", "URL");
        System.out.println("+--------------------------------------------------+------------+---------------------+------------------------+-------------------------+---------+-----------------+-------------------+----------------------------+------------------------------------------------------------------------------------------------------------------------------------------------+");

        for (Offer offer : sortedOutliers) {
            String key = offer.getModel() + " " + offer.getStorageCapacity();
            PriceStats relevantStats = offer.hasProtectionPackage() ?
                    statsWithProtectionMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0)) :
                    statsWithoutProtectionMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0));
            Map<Offer, Double> relevantZScores = offer.hasProtectionPackage() ?
                    zScoresWithProtectionMap.getOrDefault(key, new HashMap<>()) :
                    zScoresWithoutProtectionMap.getOrDefault(key, new HashMap<>());
            double sellingPrice = overallStatsMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0)).getPercentile25();

            String shortTitle = String.format("%s %s %s", offer.getModel(), offer.getStorageCapacity(), offer.getTitle());
            if (shortTitle.length() > 48) {
                shortTitle = shortTitle.substring(0, 45) + "...";
            }
            double zScore = relevantZScores.getOrDefault(offer, 0.0);
            String trendAnalysis = historyManager.analyzePriceTrend(offer.getModel(), offer.getStorageCapacity(), offer.hasProtectionPackage(), offer.getPrice());
            RecommendationAssessment assessment = getRecommendationAssessment(offer.getPrice(), relevantStats, zScore, trendAnalysis);

            double purchasePrice = offer.getPrice();
            double totalCosts = purchasePrice + shippingCost + listingFee;
            double profitMargin = sellingPrice - totalCosts;
            double profitMarginPercentage = sellingPrice != 0 ? (profitMargin / sellingPrice) * 100 : 0;
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

    private static void promptOpenOffers(List<Offer> recommendedWithout, List<Offer> recommendedWith,
                                         List<Offer> lowPriceOutlierOffers, Map<String, PriceStats> statsWithoutProtectionMap,
                                         Map<String, PriceStats> statsWithProtectionMap, Map<String, Map<Offer, Double>> zScoresWithoutProtectionMap,
                                         Map<String, Map<Offer, Double>> zScoresWithProtectionMap, PriceHistoryManager historyManager,
                                         Scanner scanner) {
        // Filter offers with "Świetna" recommendation from low price outliers
        List<Offer> superbOutliers = lowPriceOutlierOffers.stream()
                .filter(offer -> {
                    String key = offer.getModel() + " " + offer.getStorageCapacity();
                    PriceStats relevantStats = offer.hasProtectionPackage() ?
                            statsWithProtectionMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0)) :
                            statsWithoutProtectionMap.getOrDefault(key, new PriceStats(0, 0, 0, 0, 0));
                    Map<Offer, Double> relevantZScores = offer.hasProtectionPackage() ?
                            zScoresWithProtectionMap.getOrDefault(key, new HashMap<>()) :
                            zScoresWithoutProtectionMap.getOrDefault(key, new HashMap<>());
                    double zScore = relevantZScores.getOrDefault(offer, 0.0);
                    String trendAnalysis = historyManager.analyzePriceTrend(offer.getModel(), offer.getStorageCapacity(), offer.hasProtectionPackage(), offer.getPrice());
                    RecommendationAssessment assessment = getRecommendationAssessment(offer.getPrice(), relevantStats, zScore, trendAnalysis);
                    return assessment.getStatus().startsWith("Świetna");
                })
                .collect(Collectors.toList());

        // Informacja o ofertach z rekomendacją "Świetna"
        if (superbOutliers.isEmpty()) {
            System.out.println("\nBrak ofert z rekomendacją 'Świetna' w tabeli 'Podejrzane tanie oferty'.");
        } else {
            System.out.printf("\nZnaleziono %d ofert z rekomendacją 'Świetna' w tabeli 'Podejrzane tanie oferty'. Sprawdź szczegóły i linki w tabeli powyżej.\n", superbOutliers.size());
        }
    }
}