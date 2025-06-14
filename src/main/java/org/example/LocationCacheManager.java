package org.example;

import org.json.JSONObject;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class LocationCacheManager {
    private static final String CACHE_FILE = "location_cache.json";
    private final Map<String, double[]> locationCache;

    public LocationCacheManager() {
        locationCache = loadCache();
    }

    // Ładuje cache z pliku JSON
    private Map<String, double[]> loadCache() {
        Map<String, double[]> cache = new HashMap<>();
        File file = new File(CACHE_FILE);
        if (!file.exists()) {
            return cache;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }

            if (jsonContent.length() > 0) {
                JSONObject json = new JSONObject(jsonContent.toString());
                for (String city : json.keySet()) {
                    JSONObject coords = json.getJSONObject(city);
                    double latitude = coords.getDouble("latitude");
                    double longitude = coords.getDouble("longitude");
                    cache.put(city, new double[]{latitude, longitude});
                }
            }
        } catch (Exception e) {
            System.err.println("Błąd podczas ładowania cache’a lokalizacji: " + e.getMessage());
        }
        return cache;
    }

    // Zapisuje cache do pliku JSON
    private void saveCache() {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, double[]> entry : locationCache.entrySet()) {
            JSONObject coords = new JSONObject();
            coords.put("latitude", entry.getValue()[0]);
            coords.put("longitude", entry.getValue()[1]);
            json.put(entry.getKey(), coords);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CACHE_FILE))) {
            writer.write(json.toString(2)); // Format JSON z wcięciami
        } catch (IOException e) {
            System.err.println("Błąd podczas zapisywania cache’a lokalizacji: " + e.getMessage());
        }
    }

    // Pobiera współrzędne z cache’a
    public double[] getCoordinates(String city) {
        return locationCache.get(city);
    }

    // Dodaje miasto i jego współrzędne do cache’a
    public void addLocation(String city, double latitude, double longitude) {
        if (city != null && !city.isEmpty()) {
            locationCache.put(city, new double[]{latitude, longitude});
            saveCache();
        }
    }
}