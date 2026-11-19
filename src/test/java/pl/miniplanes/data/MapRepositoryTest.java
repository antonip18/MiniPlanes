package pl.miniplanes.data;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class MapRepositoryTest {
  @Test
  void americasContainCompleteNorthernCoastlinesWithoutEuropeOrAfrica() throws Exception {
    var map = new MapRepository().load("americas");
    assertTrue(
        map.countries().stream()
            .noneMatch(
                c -> java.util.Set.of("Germany", "Spain", "Morocco", "Russia").contains(c.name())));
    var coordinates =
        map.countries().stream()
            .flatMap(c -> c.rings().stream())
            .flatMap(java.util.List::stream)
            .toList();
    assertTrue(coordinates.stream().allMatch(p -> p.x() >= -180 && p.x() <= -10));
    assertTrue(coordinates.stream().anyMatch(p -> p.y() > 82));
    assertTrue(coordinates.stream().anyMatch(p -> p.x() < -165 && p.y() > 50));
    var greenland =
        map.countries().stream()
            .filter(c -> c.name().equals("Greenland"))
            .findFirst()
            .orElseThrow();
    assertTrue(
        greenland.rings().stream().flatMap(java.util.List::stream).anyMatch(p -> p.x() > -15));
  }

  @Test
  void bothMapsHave35DistinctAirportsAndRealBorders() throws Exception {
    var maps = new MapRepository().loadAll();
    assertEquals(2, maps.size());
    for (var map : maps) {
      assertEquals(35, map.airports().size());
      assertEquals(35, map.airports().stream().map(a -> a.code()).distinct().count());
      assertTrue(map.countries().size() > 30);
      assertTrue(
          map.countries().stream()
                  .flatMap(c -> c.rings().stream())
                  .mapToInt(java.util.List::size)
                  .sum()
              > 1000);
    }
    var eu = maps.getFirst();
    var krk = eu.airports().stream().filter(a -> a.code().equals("KRK")).findFirst().orElseThrow();
    assertEquals(50.08, krk.latitude(), .05);
    var am = maps.getLast();
    assertTrue(am.airports().stream().anyMatch(a -> a.latitude() < -30));
    assertTrue(am.airports().stream().anyMatch(a -> a.latitude() > 45));
  }

  @Test
  void modelHasNoDependencyOnTheUiOrViewModel() throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java/pl/miniplanes/model"))) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path);
        assertFalse(
            source.matches("(?s).*import (javafx|pl\\.miniplanes\\.(view|viewmodel|data))\\..*"),
            path.toString());
      }
    }
  }
}
