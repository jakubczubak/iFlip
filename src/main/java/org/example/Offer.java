package org.example;

import java.time.LocalDate;

public class Offer {
    private final String title;
    private final double price;
    private final String url;
    private final LocalDate date;
    private final String location;
    private final boolean hasProtectionPackage;
    private final String model;
    private final String storageCapacity;

    public Offer(String title, double price, String url, LocalDate date, String location, boolean hasProtectionPackage, String model, String storageCapacity) {
        this.title = title;
        this.price = price;
        this.url = url;
        this.date = date;
        this.location = location;
        this.hasProtectionPackage = hasProtectionPackage;
        this.model = model != null ? model : "Nieznany";
        this.storageCapacity = storageCapacity != null ? storageCapacity : "Nieznana";
    }

    public String getTitle() {
        return title;
    }

    public double getPrice() {
        return price;
    }

    public String getUrl() {
        return url;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getLocation() {
        return location;
    }

    public boolean hasProtectionPackage() {
        return hasProtectionPackage;
    }

    public String getModel() {
        return model;
    }

    public String getStorageCapacity() {
        return storageCapacity;
    }
}