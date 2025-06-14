package org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DistanceCalculator {
    private static final double EARTH_RADIUS = 6371.0; // Promień Ziemi w kilometrach
    private static final String NOMINATIM_API = "https://nominatim.openstreetmap.org/search?q=%s&format=json&limit=1";
    private final LocationCacheManager cacheManager;

    public DistanceCalculator(LocationCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    // Pobiera współrzędne geograficzne dla miasta
    public double[] getCoordinates(String city) {
        if (city == null || city.isEmpty()) {
            return null;
        }

        // Sprawdź cache
        double[] cachedCoords = cacheManager.getCoordinates(city);
        if (cachedCoords != null) {
            return cachedCoords;
        }

        // Jeśli nie ma w cache’u, pobierz z Nominatim API
        try {
            String encodedCity = java.net.URLEncoder.encode(city + ", Polska", "UTF-8");
            URL url = new URL(String.format(NOMINATIM_API, encodedCity));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "iFlip/1.0 (contact@example.com)");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parsowanie JSON
                JSONArray jsonArray = new JSONArray(response.toString());
                if (!jsonArray.isEmpty()) {
                    JSONObject json = jsonArray.getJSONObject(0);
                    double lat = json.getDouble("lat");
                    double lon = json.getDouble("lon");
                    // Zapisz do cache’a
                    cacheManager.addLocation(city, lat, lon);
                    return new double[]{lat, lon};
                } else {
                    System.err.println("Brak wyników dla miasta: " + city);
                    return null;
                }
            } else {
                System.err.println("Błąd Nominatim API: HTTP " + responseCode + " dla miasta: " + city);
                return null;
            }
        } catch (Exception e) {
            System.err.println("Błąd pobierania współrzędnych dla miasta " + city + ": " + e.getMessage());
            return null;
        }
    }

    // Oblicza odległość w linii prostej za pomocą wzoru Haversine’a
    public double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        lat1 = Math.toRadians(lat1);
        lon1 = Math.toRadians(lon1);
        lat2 = Math.toRadians(lat2);
        lon2 = Math.toRadians(lon2);

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
}