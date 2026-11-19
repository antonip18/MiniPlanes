package pl.miniplanes.model;

public record AirportSpec(
    String code,
    String name,
    String city,
    String country,
    double latitude,
    double longitude,
    Shape shape) {}
