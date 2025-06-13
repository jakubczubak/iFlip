package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PriceHistoryManager {
    private static final String HISTORY_FILE = "price_history.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static class PriceRecord {
        private final LocalDate date;
        private final double price;
        private final String model;
        private final String storageCapacity;
        private final boolean hasProtectionPackage;

        public PriceRecord(LocalDate date, double price, String model, String storageCapacity, boolean hasProtectionPackage) {
            this.date = date;
            this.price = price;
            this.model = model;
            this.storageCapacity = storageCapacity;
            this.hasProtectionPackage = hasProtectionPackage;
        }

        public JSONObject toJSON() {
            JSONObject json = new JSONObject();
            json.put("date", date.format(DATE_FORMATTER));
            json.put("price", price);
            json.put("model", model);
            json.put("storageCapacity", storageCapacity);
            json.put("hasProtectionPackage", hasProtectionPackage);
            return json;
        }

        public static PriceRecord fromJSON(JSONObject json) {
            LocalDate date = LocalDate.parse(json.getString("date"), DATE_FORMATTER);
            double price = json.getDouble("price");
            String model = json.getString("model");
            String storageCapacity = json.getString("storageCapacity");
            boolean hasProtectionPackage = json.getBoolean("hasProtectionPackage");
            return new PriceRecord(date, price, model, storageCapacity, hasProtectionPackage);
        }
    }

    public void savePrices(List<Offer> offers) {
        JSONArray jsonArray;
        File file = new File(HISTORY_FILE);

        if (file.exists()) {
            jsonArray = readJsonArray();
        } else {
            jsonArray = new JSONArray();
        }

        for (Offer offer : offers) {
            PriceRecord record = new PriceRecord(
                    offer.getDate(),
                    offer.getPrice(),
                    offer.getModel(),
                    offer.getStorageCapacity(),
                    offer.hasProtectionPackage()
            );
            jsonArray.put(record.toJSON());
        }

        try (FileWriter fileWriter = new FileWriter(HISTORY_FILE)) {
            fileWriter.write(jsonArray.toString(2));
        } catch (IOException e) {
            System.err.println("Błąd podczas zapisu do pliku JSON: " + e.getMessage());
        }
    }

    private JSONArray readJsonArray() {
        try (FileReader fileReader = new FileReader(HISTORY_FILE)) {
            StringBuilder content = new StringBuilder();
            int c;
            while ((c = fileReader.read()) != -1) {
                content.append((char) c);
            }
            return content.length() > 0 ? new JSONArray(content.toString()) : new JSONArray();
        } catch (IOException e) {
            System.err.println("Błąd podczas odczytu pliku JSON: " + e.getMessage());
            return new JSONArray();
        }
    }

    public List<PriceRecord> getHistoricalPrices(String model, String storageCapacity, boolean hasProtectionPackage) {
        List<PriceRecord> records = new ArrayList<>();
        JSONArray jsonArray = readJsonArray();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            if (json.getString("model").equalsIgnoreCase(model) &&
                    json.getString("storageCapacity").equalsIgnoreCase(storageCapacity) &&
                    json.getBoolean("hasProtectionPackage") == hasProtectionPackage) {
                records.add(PriceRecord.fromJSON(json));
            }
        }
        return records;
    }

    public String analyzePriceTrend(String model, String storageCapacity, boolean hasProtectionPackage, double currentPrice) {
        List<PriceRecord> records = getHistoricalPrices(model, storageCapacity, hasProtectionPackage);
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(30);

        List<Double> recentPrices = records.stream()
                .filter(record -> !record.date.isBefore(thirtyDaysAgo) && !record.date.isAfter(today))
                .map(record -> record.price)
                .filter(price -> price > 0)
                .sorted()
                .toList();

        if (recentPrices.isEmpty()) {
            return "Brak danych";
        }

        double median = calculateMedian(recentPrices);
        String medianText = String.format("%d PLN", (int) median);

        if (currentPrice < median * 0.9) {
            return String.format("Znacznie taniej (%s)", medianText);
        } else if (currentPrice < median) {
            return String.format("Taniej (%s)", medianText);
        } else {
            return String.format("Średnio (%s)", medianText);
        }
    }

    private double calculateMedian(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        int n = prices.size();
        if (n % 2 == 0) {
            return (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0;
        } else {
            return prices.get(n / 2);
        }
    }
}