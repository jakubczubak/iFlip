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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OlxScraper {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("pl"));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TODAY_PATTERN = Pattern.compile("Dzisiaj o (\\d{2}:\\d{2})");
    private static final Pattern REFRESHED_DATE_PATTERN = Pattern.compile("Odświeżono dnia (\\d+ \\p{L}+ \\d{4})");
    private static final Pattern SIMPLE_DATE_PATTERN = Pattern.compile("(\\d+ \\p{L}+ \\d{4})$");
    private static final int REQUEST_DELAY_MS = 2000; // 2 seconds delay between requests
    private static final int MAX_RETRIES = 3; // Maximum number of retries for rate-limited requests
    private static final int RETRY_DELAY_MS = 5000; // 5 seconds delay between retries

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

        while (hasNextPage) {
            String url = baseUrl + (page > 1 ? "&page=" + page : "");
            System.out.println("Pobieram dane z URL (strona " + page + "): " + url);

            try {
                Document doc = fetchWithRetry(url);
                if (doc == null) {
                    System.err.println("Nie udało się pobrać danych z URL po kilku próbach: " + url);
                    hasNextPage = false;
                    break;
                }

                Elements offerElements = doc.select("div.css-qfzx1y");

                if (offerElements.isEmpty()) {
                    System.out.println("Nie znaleziono ofert na stronie " + page + ". Kończę paginację.");
                    hasNextPage = false;
                    break;
                }

                System.out.println("Znaleziono " + offerElements.size() + " ofert na stronie " + page);

                for (Element element : offerElements) {
                    Offer offer = parseOffer(element, model, storageCapacity);
                    if (offer != null) {
                        offers.add(offer);
                    }
                }

                Element nextPageElement = doc.selectFirst("a[data-testid=pagination-forward]");
                hasNextPage = nextPageElement != null;
                System.out.println("Czy jest następna strona? " + hasNextPage);
                page++;

                // Delay between requests to avoid rate-limiting
                Thread.sleep(REQUEST_DELAY_MS);

            } catch (InterruptedException e) {
                System.err.println("Przerwano działanie podczas opóźnienia: " + e.getMessage());
                Thread.currentThread().interrupt(); // Restore interrupted status
                hasNextPage = false;
            } catch (IOException e) {
                System.err.println("Błąd podczas pobierania danych z URL: " + url);
                System.err.println("Szczegóły błędu: " + e.getMessage());
                hasNextPage = false;
            }
        }

        return offers;
    }

    private Document fetchWithRetry(String url) throws IOException, InterruptedException {
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

            } catch (IOException e) {
                System.err.println("Błąd podczas próby połączenia (próba " + (retries + 1) + "): " + e.getMessage());
                retries++;
                if (retries >= MAX_RETRIES) {
                    throw e;
                }
                Thread.sleep(RETRY_DELAY_MS);
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

            Element protectionElement = element.selectFirst("span[data-testid=btr-label-wrapper]");
            boolean hasProtectionPackage = protectionElement != null;

            return new Offer(title, price, offerUrl, date, locationText, hasProtectionPackage, model, storageCapacity);
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
}