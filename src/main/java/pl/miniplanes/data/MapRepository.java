package pl.miniplanes.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import pl.miniplanes.model.*;

/** Validated classpath resources: launching from another directory does not break map loading. */
public final class MapRepository {
  private static final ObjectMapper JSON = new ObjectMapper();

  public List<MapDefinition> loadAll() throws IOException {
    return List.of(load("europe"), load("americas"));
  }

  public MapDefinition load(String id) throws IOException {
    if (!Set.of("europe", "americas").contains(id)) throw new IOException("Unknown map: " + id);
    JsonNode root = read(id + ".json");
    AirportSpec[] specs = JSON.treeToValue(read(id + "-airports.json"), AirportSpec[].class);
    Set<String> codes = new HashSet<>();
    for (AirportSpec a : specs) {
      if (a.code() == null
          || !codes.add(a.code())
          || a.shape() == null
          || a.city() == null
          || !Double.isFinite(a.latitude())
          || !Double.isFinite(a.longitude())
          || a.latitude() < root.path("south").asDouble()
          || a.latitude() > root.path("north").asDouble()
          || a.longitude() < root.path("west").asDouble()
          || a.longitude() > root.path("east").asDouble())
        throw new IOException("Invalid airport in " + id + ": " + a);
    }
    if (specs.length < 3
        || Arrays.stream(specs).limit(3).map(AirportSpec::shape).distinct().count() != 3)
      throw new IOException("Each map must start with three different destination shapes");
    List<MapDefinition.Country> countries = new ArrayList<>();
    for (JsonNode country : root.path("countries")) {
      List<List<Point>> rings = new ArrayList<>();
      for (JsonNode ring : country.path("rings")) {
        List<Point> points = new ArrayList<>();
        for (JsonNode point : ring)
          points.add(new Point(point.get(0).asDouble(), point.get(1).asDouble()));
        rings.add(List.copyOf(points));
      }
      countries.add(new MapDefinition.Country(country.path("name").asText(), List.copyOf(rings)));
    }
    return new MapDefinition(
        id,
        root.path("title").asText(),
        root.path("subtitle").asText(),
        root.path("west").asDouble(),
        root.path("east").asDouble(),
        root.path("south").asDouble(),
        root.path("north").asDouble(),
        root.path("longHaulKm").asDouble(),
        List.of(specs),
        countries);
  }

  private JsonNode read(String file) throws IOException {
    try (InputStream in = MapRepository.class.getResourceAsStream("/pl/miniplanes/maps/" + file)) {
      if (in == null) throw new IOException("Missing map resource: " + file);
      return JSON.readTree(in);
    }
  }
}
