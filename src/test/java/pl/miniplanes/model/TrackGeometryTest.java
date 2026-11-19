package pl.miniplanes.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class TrackGeometryTest {
  @Test
  void verticalRoutesStaySeparatedInBothDirections() {
    GameData g =
        SimulationTest.fixture(new Point(100, 0), new Point(100, 200), new Point(100, 400));
    for (int i = 0; i < 4; i++)
      assertNull(g.editRoute(i, i % 2 == 0 ? g.airports() : g.airports().reversed(), false));
    Set<Double> offsets = new HashSet<>();
    for (int i = 0; i < 4; i++) offsets.add(g.geometry().segment(g.line(i), 0).a().x());
    assertEquals(4, offsets.size());
    List<Double> sorted = offsets.stream().sorted().toList();
    for (int i = 1; i < 4; i++)
      assertEquals(TrackGeometry.LANE_SPACING, sorted.get(i) - sorted.get(i - 1), 1e-6);
    int reserve = g.sparePlanes();
    assertNotNull(g.editRoute(4, g.airports(), false));
    assertEquals(0, g.line(4).size());
    assertEquals(reserve, g.sparePlanes());
  }

  @Test
  void partialOverlapsAlsoCountTowardsLimit() {
    GameData g =
        SimulationTest.fixture(
            new Point(0, 0), new Point(100, 0), new Point(200, 0), new Point(300, 0));
    for (int i = 0; i < 4; i++)
      assertNull(g.editRoute(i, List.of(g.airports.get(0), g.airports.get(3)), false));
    assertNotNull(g.editRoute(4, List.of(g.airports.get(1), g.airports.get(2)), false));
  }

  @Test
  void disjointCollinearLegsDoNotConsumeEachOthersLanes() {
    GameData g =
        SimulationTest.fixture(
            new Point(0, 0), new Point(100, 0), new Point(200, 0), new Point(300, 0));
    for (int i = 0; i < 4; i++)
      assertNull(g.editRoute(i, List.of(g.airports.get(0), g.airports.get(1)), false));
    assertNull(g.editRoute(4, List.of(g.airports.get(2), g.airports.get(3)), false));
  }

  @Test
  void diagonalOffsetsArePerpendicularAndDoNotBendTheLeg() {
    GameData g = SimulationTest.fixture(new Point(0, 0), new Point(120, 80), new Point(300, 100));
    g.editRoute(0, g.airports.subList(0, 2), false);
    g.editRoute(1, g.airports.subList(0, 2).reversed(), false);
    var a = g.geometry().segment(g.line(0), 0);
    var b = g.geometry().segment(g.line(1), 0);
    assertEquals(TrackGeometry.LANE_SPACING, a.a().distance(b.b()), 1e-6);
    assertEquals(0, a.b().subtract(a.a()).cross(new Point(120, 80)), 1e-6);
  }

  @Test
  void crossingsAreNotParallelCorridors() {
    GameData g =
        SimulationTest.fixture(
            new Point(0, 0), new Point(200, 200), new Point(0, 200), new Point(200, 0));
    for (int i = 0; i < 4; i++)
      assertNull(g.editRoute(i, List.of(g.airports.get(0), g.airports.get(1)), false));
    assertNull(g.editRoute(4, List.of(g.airports.get(2), g.airports.get(3)), false));
  }

  @Test
  void almostCoincidentCorridorsUseTheSameReferenceNormal() {
    GameData g =
        SimulationTest.fixture(
            new Point(0, 0),
            new Point(200, 0),
            new Point(0, 2),
            new Point(200, 2),
            new Point(0, 4),
            new Point(200, 4));
    for (int i = 0; i < 3; i++)
      assertNull(g.editRoute(i, g.airports.subList(i * 2, i * 2 + 2), false));
    List<Double> lanes = g.geometry().segments().stream().map(s -> s.a().y()).sorted().toList();
    assertEquals(9, lanes.get(1) - lanes.get(0), 1e-6);
    assertEquals(9, lanes.get(2) - lanes.get(1), 1e-6);
  }
}
