package pl.miniplanes.model;

import java.util.List;

public record MapDefinition(
    String id,
    String title,
    String subtitle,
    double west,
    double east,
    double south,
    double north,
    double longHaulKm,
    List<AirportSpec> airports,
    List<Country> countries) {
  public record Country(String name, List<List<Point>> rings) {}

  public MapDefinition {
    airports = List.copyOf(airports);
    countries = List.copyOf(countries);
  }

  /** Equirectangular projection, with longitude scaled at the map's central latitude. */
  public Point project(double latitude, double longitude) {
    double cos = Math.cos(Math.toRadians((north + south) * .5));
    double width = (east - west) * cos, height = north - south;
    double scale = Math.min(1120 / width, 700 / height);
    return new Point(
        600 + (longitude - (east + west) * .5) * cos * scale,
        390 - (latitude - (north + south) * .5) * scale);
  }

  public static double distanceKm(AirportSpec a, AirportSpec b) {
    double lat = Math.toRadians(b.latitude() - a.latitude());
    double lon = Math.toRadians(b.longitude() - a.longitude());
    double h =
        Math.pow(Math.sin(lat / 2), 2)
            + Math.cos(Math.toRadians(a.latitude()))
                * Math.cos(Math.toRadians(b.latitude()))
                * Math.pow(Math.sin(lon / 2), 2);
    return 12742 * Math.asin(Math.sqrt(Math.clamp(h, 0, 1)));
  }
}
