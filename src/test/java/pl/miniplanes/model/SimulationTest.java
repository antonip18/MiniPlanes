package pl.miniplanes.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SimulationTest {
  static GameData fixture(Point... points) {
    List<AirportSpec> specs = new ArrayList<>();
    for (int i = 0; i < Math.max(3, points.length); i++)
      specs.add(
          new AirportSpec("A" + i, "Airport " + i, "City " + i, "XX", 0, i, Shape.values()[i % 3]));
    GameData game =
        new GameData(
            new MapDefinition("test", "Test", "", -10, 10, -10, 10, 100000, specs, List.of()), 42);
    game.airports.clear();
    for (int i = 0; i < points.length; i++) game.airports.add(new Airport(specs.get(i), points[i]));
    game.unlockedLines = 8;
    game.sparePlanes = 8;
    game.rebuild();
    return game;
  }

  private static void advance(GameData game, double seconds) {
    Updater u = new Updater(game);
    for (int i = 0; i < (int) Math.ceil(seconds / Updater.STEP); i++) u.step(Updater.STEP);
  }

  @Test
  void passengerTransfersAndAccountingSurviveEveryTick() {
    GameData g = fixture(new Point(100, 100), new Point(200, 100), new Point(300, 100));
    assertNull(g.editRoute(0, g.airports.subList(0, 2), false));
    assertNull(g.editRoute(1, g.airports.subList(1, 3), false));
    Passenger p = new Passenger(++g.generated, Shape.SQUARE);
    g.airports.getFirst().waiting.add(p);
    Updater u = new Updater(g);
    for (int i = 0; i < 1500; i++) {
      u.step(Updater.STEP);
      assertEquals(g.generated(), g.delivered() + g.waitingCount() + g.airborneCount());
    }
    assertTrue(g.delivered() > 0);
    assertTrue(g.airports.stream().noneMatch(a -> a.waiting.contains(p)));
    assertTrue(g.airplanes.stream().noneMatch(a -> a.passengers.contains(p)));
  }

  @Test
  void disconnectedPassengersWaitInsteadOfBoardingAnUnusableFlight() {
    GameData g = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0));
    g.editRoute(0, g.airports.subList(0, 2), false);
    Passenger p = new Passenger(++g.generated, Shape.SQUARE);
    g.airports.getFirst().waiting.add(p);
    advance(g, 2);
    assertTrue(g.airports.getFirst().waiting.contains(p));
    assertTrue(g.airplanes.getFirst().passengers.isEmpty());
  }

  @Test
  void routeDeletionReturnsFleetAndUpgradesWithoutLosingPassengers() {
    GameData g = fixture(new Point(0, 0), new Point(200, 0), new Point(400, 0));
    List<Airport> route = g.airports();
    g.editRoute(0, route, false);
    Airplane plane = g.airplanes.getFirst();
    g.upgradeAirplane(plane.id());
    g.airports.getFirst().waiting.add(new Passenger(++g.generated, Shape.SQUARE));
    advance(g, 2);
    assertFalse(plane.passengers.isEmpty());
    assertNull(g.editRoute(0, List.of(), false));
    assertEquals(8, g.sparePlanes());
    assertEquals(1, g.widebodies());
    assertTrue(g.airplanes.isEmpty());
    assertEquals(g.generated(), g.delivered() + g.waitingCount());
  }

  @Test
  void editingInFlightMaintainsPassengerConservation() {
    GameData g = fixture(new Point(0, 0), new Point(300, 0), new Point(600, 0));
    g.editRoute(0, g.airports(), false);
    g.airports.getFirst().waiting.add(new Passenger(++g.generated, Shape.SQUARE));
    advance(g, 5);
    assertNull(g.editRoute(0, List.of(g.airports.get(1), g.airports.get(2)), false));
    assertEquals(g.generated(), g.delivered() + g.waitingCount() + g.airborneCount());
    assertEquals(0, g.airplanes.getFirst().index());
  }

  @Test
  void addingAndMovingPlanesAlwaysStartsAtFirstAirport() {
    GameData g = fixture(new Point(0, 0), new Point(200, 0), new Point(400, 0));
    g.editRoute(0, g.airports(), false);
    advance(g, 4);
    g.addAirplane(0);
    Airplane added = g.airplanes.getLast();
    assertEquals(0, added.index());
    assertEquals(0, added.progress());
    g.editRoute(1, List.of(g.airports.getLast(), g.airports.getFirst()), false);
    g.moveAirplane(added.id(), 1);
    assertSame(g.airports.getLast(), added.currentAirport());
    assertEquals(0, added.progress());
  }

  @Test
  void widebodyDoublesCapacityAndBoardingRespectsIt() {
    GameData g = fixture(new Point(0, 0), new Point(200, 0), new Point(400, 0));
    g.editRoute(0, g.airports(), false);
    Airplane plane = g.airplanes.getFirst();
    assertNull(g.upgradeAirplane(plane.id()));
    assertEquals(12, plane.capacity());
    for (int i = 0; i < 18; i++)
      g.airports.getFirst().waiting.add(new Passenger(++g.generated, Shape.TRIANGLE));
    advance(g, 1.5);
    assertEquals(12, plane.passengers.size());
    assertEquals(6, g.airports.getFirst().waitingCount());
  }

  @Test
  void continuousLoopNeverReverses() {
    GameData g = fixture(new Point(0, 0), new Point(50, 0), new Point(25, 40));
    g.editRoute(0, g.airports(), true);
    Airplane plane = g.airplanes.getFirst();
    boolean seenLast = false, returned = false;
    Updater u = new Updater(g);
    for (int i = 0; i < 1000; i++) {
      u.step(Updater.STEP);
      if (plane.index() == 2) seenLast = true;
      if (seenLast && plane.index() == 0) returned = true;
      assertEquals(1, plane.direction());
    }
    assertTrue(returned);
  }

  @Test
  void aWeeklyRewardFreezesTimeAndCannotBeClaimedTwice() {
    GameData g = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0));
    g.passengerClock = -1000;
    advance(g, 84);
    assertEquals(2, g.week());
    assertTrue(g.rewardPending());
    assertEquals(9, g.sparePlanes());
    double time = g.elapsed();
    advance(g, 10);
    assertEquals(time, g.elapsed());
    assertNull(g.chooseUpgrade(Upgrade.CAPACITY));
    assertEquals(2, g.widebodies());
    assertNotNull(g.chooseUpgrade(Upgrade.CAPACITY));
    assertEquals(2, g.widebodies());
  }

  @Test
  void crowdingRecoversAndHubPreventsLoss() {
    GameData g = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0));
    g.passengerClock = -1000;
    Airport a = g.airports.getFirst();
    for (int i = 0; i < 16; i++) a.waiting.add(new Passenger(++g.generated, Shape.TRIANGLE));
    advance(g, 10);
    assertFalse(g.gameOver());
    assertTrue(a.crowding() > .28);
    assertNull(g.upgradeAirport(a));
    advance(g, 6);
    assertEquals(0, a.crowding());
    assertEquals(28, a.capacity());
  }

  @Test
  void prolongedCrowdingEndsGameAndLocksMutations() {
    GameData g = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0));
    g.passengerClock = -1000;
    Airport a = g.airports.getFirst();
    for (int i = 0; i < 16; i++) a.waiting.add(new Passenger(++g.generated, Shape.TRIANGLE));
    advance(g, 36);
    assertTrue(g.gameOver());
    double time = g.elapsed();
    advance(g, 1);
    assertEquals(time, g.elapsed());
    assertNotNull(g.editRoute(0, g.airports(), false));
  }

  @Test
  void longHaulPermitsAreTransactionalAndRefunded() {
    var specs =
        List.of(
            new AirportSpec("AAA", "A", "A", "XX", 0, 0, Shape.CIRCLE),
            new AirportSpec("BBB", "B", "B", "XX", 0, 30, Shape.TRIANGLE),
            new AirportSpec("CCC", "C", "C", "XX", 0, 60, Shape.SQUARE));
    GameData g =
        new GameData(
            new MapDefinition("test", "test", "", -10, 70, -10, 10, 2000, specs, List.of()), 1);
    assertNull(g.editRoute(0, g.airports(), false));
    assertEquals(0, g.permits());
    int spare = g.sparePlanes();
    assertNotNull(g.editRoute(1, g.airports.subList(0, 2), false));
    assertEquals(spare, g.sparePlanes());
    assertEquals(0, g.line(1).size());
    assertNull(g.editRoute(0, List.of(), false));
    assertEquals(2, g.permits());
  }

  @Test
  void invalidRouteCannotPartiallyConsumeResources() {
    GameData g = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0));
    int spare = g.sparePlanes();
    assertNotNull(
        g.editRoute(0, List.of(g.airports.get(0), g.airports.get(1), g.airports.get(0)), false));
    assertEquals(spare, g.sparePlanes());
    assertTrue(g.airplanes.isEmpty());
    assertEquals(0, g.line(0).size());
  }

  @Test
  void simulationIsDeterministicForTheSameSeed() {
    GameData a = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0)),
        b = fixture(new Point(0, 0), new Point(80, 0), new Point(160, 0));
    a.editRoute(0, a.airports(), false);
    b.editRoute(0, b.airports(), false);
    advance(a, 70);
    advance(b, 70);
    assertEquals(a.generated(), b.generated());
    assertEquals(a.delivered(), b.delivered());
    assertEquals(a.waitingCount(), b.waitingCount());
  }

  @Test
  void extendingAnUnchangedFlightDoesNotTeleportItOrUnloadItsPassengers() {
    GameData g = fixture(new Point(0, 0), new Point(300, 0), new Point(600, 0));
    g.editRoute(0, g.airports.subList(0, 2), false);
    Passenger p = new Passenger(++g.generated, Shape.TRIANGLE);
    g.airports.getFirst().waiting.add(p);
    advance(g, 5);
    Airplane plane = g.airplanes.getFirst();
    double progress = plane.progress();
    assertTrue(progress > .5);
    assertTrue(plane.passengers.contains(p));
    assertNull(g.editRoute(0, g.airports(), false));
    assertEquals(progress, plane.progress());
    assertTrue(plane.passengers.contains(p));
    assertEquals(0, g.delivered());
  }

  @Test
  void withdrawingAFlightCannotScoreAnUnvisitedDestination() {
    GameData g = fixture(new Point(0, 0), new Point(300, 0), new Point(600, 0));
    g.editRoute(0, g.airports.subList(0, 2), false);
    Passenger p = new Passenger(++g.generated, Shape.TRIANGLE);
    g.airports.getFirst().waiting.add(p);
    advance(g, 5);
    assertTrue(g.airplanes.getFirst().progress() > .5);
    g.editRoute(0, List.of(g.airports.get(0), g.airports.get(2)), false);
    assertEquals(0, g.delivered());
    assertTrue(g.airports.getFirst().waiting.contains(p));
  }

  @Test
  void mixedEditsMovesAndSimulationNeverLoseOrDuplicatePeople() {
    GameData g =
        fixture(new Point(0, 0), new Point(120, 0), new Point(240, 50), new Point(350, 150));
    Random r = new Random(5);
    Updater u = new Updater(g);
    for (int tick = 0; tick < 6000; tick++) {
      if (tick % 90 == 0) {
        List<Airport> path = new ArrayList<>(g.airports());
        Collections.shuffle(path, r);
        g.editRoute(r.nextInt(3), path.subList(0, 2 + r.nextInt(path.size() - 1)), false);
      }
      if (tick % 240 == 0 && !g.airplanes.isEmpty())
        g.moveAirplane(g.airplanes.getFirst().id(), r.nextInt(3));
      if (g.rewardPending()) g.chooseUpgrade(Upgrade.CAPACITY);
      u.step(Updater.STEP);
      assertEquals(g.generated(), g.delivered() + g.waitingCount() + g.airborneCount());
      Set<Long> ids = new HashSet<>();
      for (Airport a : g.airports) for (Passenger p : a.waiting) assertTrue(ids.add(p.id()));
      for (Airplane a : g.airplanes) for (Passenger p : a.passengers) assertTrue(ids.add(p.id()));
    }
  }
}
