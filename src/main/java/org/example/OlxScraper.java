package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class OlxScraper {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("pl"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TODAY_PATTERN = Pattern.compile("Dzisiaj o (\\d{2}:\\d{2})");
    private static final Pattern REFRESHED_DATE_PATTERN = Pattern.compile("Odświeżono dnia (\\d+ \\p{L}+ \\d{4})");
    private static final Pattern SIMPLE_DATE_PATTERN = Pattern.compile("(\\d+ \\p{L}+ \\d{4})$");
    private static final int REQUEST_DELAY_MS = 2000; // 2 seconds delay between batches
    private static final int MAX_RETRIES = 3; // Maximum number of retries for rate-limited requests
    private static final int RETRY_DELAY_MS = 5000; // 5 seconds delay between retries
    private static final int CONCURRENT_PAGES = 4; // Number of pages to fetch concurrently
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(CONCURRENT_PAGES); // Thread pool for async tasks

    public List<Offer> scrapeOffers(String model, String storageCapacity, String location, List<String> states) {
        List<Offer> offers = new ArrayList<>();
        String modelQuery = model.toLowerCase().replaceAll("iphone\\s*", "").replaceAll("\\s+", "-");
        String storageQuery = storageCapacity.toLowerCase().replace("tb", "tb").replace("gb", "gb");
        String baseUrl;

        // Budowanie parametrów filtra
        StringBuilder filters = new StringBuilder();
        filters.append("search%5Bfilter_enum_phonemodel%5D%5B0%5D=iphone-")
                .append(URLEncoder.encode(modelQuery, StandardCharsets.UTF_8));
        filters.append("&search%5Bfilter_enum_builtinmemory_phones%5D%5B0%5D=")
                .append(URLEncoder.encode(storageQuery, StandardCharsets.UTF_8));

        // Dodanie parametru lokalizacji
        if (location != null && !location.isEmpty()) {
            filters.insert(0, "search%5Bdist%5D=50&");
        }

        // Dodanie parametrów stanu
        System.out.println("Przekazane stany: " + (states != null ? states : "null"));
        if (states != null && !states.isEmpty()) {
            for (int i = 0; i < states.size(); i++) {
                filters.append("&search%5Bfilter_enum_state%5D%5B")
                        .append(i)
                        .append("%5D=")
                        .append(URLEncoder.encode(states.get(i), StandardCharsets.UTF_8));
            }
        }

        // Budowanie pełnego URL
        if (location != null && !location.isEmpty()) {
            String locationSlug = location.trim().toLowerCase().replaceAll("\\s+", "-");
            baseUrl = "https://www.olx.pl/elektronika/telefony/smartfony-telefony-komorkowe/" +
                    URLEncoder.encode(locationSlug, StandardCharsets.UTF_8) + "/q-iphone/?" + filters;
        } else {
            baseUrl = "https://www.olx.pl/elektronika/telefony/smartfony-telefony-komorkowe/q-iphone/?" + filters;
        }

        int page = 1;
        boolean hasNextPage = true;
        List<CompletableFuture<List<Offer>>> futures = new ArrayList<>();

        while (hasNextPage) {
            // Prepare a batch of pages to fetch concurrently
            List<Integer> pageBatch = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_PAGES && hasNextPage; i++) {
                pageBatch.add(page + i);
            }

            // Fetch pages in the batch asynchronously
            futures.clear();
            for (int currentPage : pageBatch) {
                String url = baseUrl + (currentPage > 1 ? "&page=" + currentPage : "");
                System.out.println("Planuję pobieranie danych z URL (strona " + currentPage + "): " + url);
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        Document doc = fetchWithRetry(url);
                        if (doc == null) {
                            System.err.println("Nie udało się pobrać danych z URL po kilku próbach: " + url);
                            return new ArrayList<Offer>();
                        }

                        Elements offerElements = doc.select("div.css-qfzx1y");
                        if (offerElements.isEmpty()) {
                            System.out.println("Nie znaleziono ofert na stronie " + currentPage + ".");
                            return new ArrayList<Offer>();
                        }

                        System.out.println("Znaleziono " + offerElements.size() + " ofert na stronie " + currentPage);
                        List<Offer> pageOffers = new ArrayList<>();
                        for (Element element : offerElements) {
                            Offer offer = parseOffer(element, model, storageCapacity);
                            if (offer != null) {
                                pageOffers.add(offer);
                            }
                        }

                        // Check for next page
                        boolean hasNext = doc.selectFirst("a[data-testid=pagination-forward]") != null;
                        System.out.println("Czy jest następna strona po stronie " + currentPage + "? " + hasNext);
                        return new PageResult(pageOffers, hasNext).getOffers();
                    } catch (IOException e) {
                        System.err.println("Błąd podczas pobierania danych z URL: " + url + ", szczegóły: " + e.getMessage());
                        return new ArrayList<Offer>();
                    }
                }, EXECUTOR));
            }

            // Wait for all futures in the batch to complete
            List<Offer> batchOffers = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            offers.addAll(batchOffers);

            // Check if any page in the batch has a next page
            int finalPage = page;
            hasNextPage = futures.stream()
                    .map(future -> {
                        try {
                            // Re-fetch the document to check for next page (simplified check)
                            String url = baseUrl + (finalPage > 1 ? "&page=" + finalPage : "");
                            Document doc = fetchWithRetry(url);
                            return doc != null && doc.selectFirst("a[data-testid=pagination-forward]") != null;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .anyMatch(Boolean::booleanValue);

            page += CONCURRENT_PAGES;

            // Delay between batches to avoid rate-limiting
            try {
                Thread.sleep(REQUEST_DELAY_MS);
            } catch (InterruptedException e) {
                System.err.println("Przerwano działanie podczas opóźnienia: " + e.getMessage());
                Thread.currentThread().interrupt();
                hasNextPage = false;
            }
        }

        // Shutdown the executor (optional, depending on application lifecycle)
        // EXECUTOR.shutdown();

        return offers;
    }

    private Document fetchWithRetry(String url) throws IOException {
        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 429) {
                    System.err.println("Otrzymano kod HTTP 429 (Too Many Requests). Ponawiam próbę po opóźnieniu...");
                    retries++;
                    if (retries >= MAX_RETRIES) {
                        System.err.println("Przekroczono maksymalną liczbę prób dla URL: " + url);
                        return null;
                    }
                    Thread.sleep(RETRY_DELAY_MS);
                    continue;
                } else if (responseCode != 200) {
                    System.err.println("Otrzymano kod HTTP: " + responseCode + " dla URL: " + url);
                    return null;
                }

                return Jsoup.parse(connection.getInputStream(), "UTF-8", url);

            } catch (IOException | InterruptedException e) {
                System.err.println("Błąd podczas próby połączenia (próba " + (retries + 1) + "): " + e.getMessage());
                retries++;
                if (retries >= MAX_RETRIES) {
                    throw new IOException("Nie udało się pobrać strony po " + MAX_RETRIES + " próbach", e);
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Przerwano podczas oczekiwania na ponowienie", ie);
                }
            }
        }
        return null;
    }

    private Offer parseOffer(Element element, String model, String storageCapacity) {
        try {
            Element titleElement = element.selectFirst("h4.css-1g61gc2");
            String title = titleElement != null ? titleElement.text() : "";
            if (title.isEmpty()) {
                System.err.println("Brak tytułu publikacji.");
                return null;
            }

            Element priceElement = element.selectFirst("p[data-testid=ad-price]");
            String priceText = priceElement != null ? priceElement.text() : "";
            double price = parsePrice(priceText);
            if (price <= 0) {
                return null; // Cicho pomijamy oferty z nieprawidłową ceną
            }

            Element linkElement = element.selectFirst("a.css-1tqlkj0");
            String offerUrl = linkElement != null ? linkElement.attr("href") : "";
            if (offerUrl.isEmpty()) {
                System.err.println("Brak URL dla publikacji: " + title);
                return null;
            }
            if (!offerUrl.startsWith("https")) {
                offerUrl = "https://www.olx.pl" + offerUrl;
            }

            Element dateLocationElement = element.selectFirst("p[data-testid=location-date]");
            String dateLocationText = dateLocationElement != null ? dateLocationElement.text() : "";
            String locationText = parseLocation(dateLocationText);
            if (locationText.isEmpty()) {
                System.err.println("Brak lokalizacji dla publikacji: " + title);
                return null;
            }
            LocalDate date = parseDate(dateLocationText);
            if (date == null) {
                System.err.println("Nieprawidłowa data dla publikacji: " + title);
                return null;
            }

            // Ustalanie statusu daty
            String dateStatus = "";
            Matcher todayMatcher = TODAY_PATTERN.matcher(dateLocationText);
            Matcher refreshedMatcher = REFRESHED_DATE_PATTERN.matcher(dateLocationText);
            if (todayMatcher.find()) {
                dateStatus = "dzisiaj";
            } else if (refreshedMatcher.find()) {
                dateStatus = "odświeżono";
            }

            Element protectionElement = element.selectFirst("span[data-testid=btr-label-wrapper]");
            boolean hasProtectionPackage = protectionElement != null;

            return new Offer(title, price, offerUrl, date, dateStatus, locationText, hasProtectionPackage, model, storageCapacity);
        } catch (Exception e) {
            System.err.println("Błąd podczas parsowania oferty: " + e.getMessage());
            return null;
        }
    }

    private double parsePrice(String priceText) {
        if (priceText == null || priceText.trim().isEmpty()) {
            return 0.0; // Cicho pomijamy puste ceny
        }

        String trimmedPriceText = priceText.trim().toLowerCase();
        if (trimmedPriceText.equals("zamienię") || trimmedPriceText.equals("do negocjacji")) {
            return 0.0; // Cicho pomijamy oferty z "Zamienię" lub "Do negocjacji"
        }

        try {
            String cleanedPrice = priceText.replaceAll("[^0-9,.]", "").replace(",", ".");
            if (cleanedPrice.isEmpty()) {
                return 0.0; // Cicho pomijamy brak kwoty liczbowej
            }
            return Double.parseDouble(cleanedPrice);
        } catch (NumberFormatException e) {
            return 0.0; // Cicho pomijamy nieprawidłowe ceny
        }
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isEmpty()) {
            System.err.println("Brak daty w tekście: " + dateText);
            return null;
        }

        try {
            Matcher todayMatcher = TODAY_PATTERN.matcher(dateText);
            if (todayMatcher.find()) {
                String time = todayMatcher.group(1);
                TIME_FORMATTER.parse(time);
                return LocalDate.now();
            }

            Matcher refreshedMatcher = REFRESHED_DATE_PATTERN.matcher(dateText);
            if (refreshedMatcher.find()) {
                String datePart = refreshedMatcher.group(1);
                return LocalDate.parse(datePart, DATE_FORMATTER);
            }

            Matcher simpleDateMatcher = SIMPLE_DATE_PATTERN.matcher(dateText);
            if (simpleDateMatcher.find()) {
                String datePart = simpleDateMatcher.group(1);
                return LocalDate.parse(datePart, DATE_FORMATTER);
            }

            String[] parts = dateText.split("-");
            if (parts.length > 1) {
                String potentialDate = parts[1].trim();
                potentialDate = potentialDate.replace("Odświeżono dnia", "").trim();
                return LocalDate.parse(potentialDate, DATE_FORMATTER);
            }

            System.err.println("Nie znaleziono daty w tekście: " + dateText);
            return null;

        } catch (DateTimeParseException e) {
            System.err.println("Błąd parsowania daty: " + dateText + ", szczegóły: " + e.getMessage());
            return null;
        }
    }

    private String parseLocation(String dateLocationText) {
        if (dateLocationText == null || dateLocationText.isEmpty()) {
            System.err.println("Brak lokalizacji w tekście: " + dateLocationText);
            return "";
        }

        try {
            String[] parts = dateLocationText.split("-");
            String locationPart = parts[0].trim();
            return locationPart.split(",")[0].trim();
        } catch (Exception e) {
            System.err.println("Błąd parsowania lokalizacji: " + dateLocationText);
            return "";
        }
    }

    // Helper class to hold page results
    private static class PageResult {
        private final List<Offer> offers;
        private final boolean hasNextPage;

        public PageResult(List<Offer> offers, boolean hasNextPage) {
            this.offers = offers;
            this.hasNextPage = hasNextPage;
        }

        public List<Offer> getOffers() {
            return offers;
        }

        public boolean hasNextPage() {
            return hasNextPage;
        }
    }
}