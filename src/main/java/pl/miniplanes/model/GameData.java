package pl.miniplanes.model;

import java.util.*;

/** Authoritative state and atomic commands. No UI objects, clocks, threads or filesystem access. */
public final class GameData {
  public static final double WEEK_SECONDS = 84, OVERCROWD_SECONDS = 35;
  private final MapDefinition map;
  private final Random random;
  private final AirportSupplier supplier;
  final PassengerRouter router = new PassengerRouter();
  final List<Airport> airports = new ArrayList<>();
  final List<Line> lines = new ArrayList<>();
  final List<Airplane> airplanes = new ArrayList<>();
  int sparePlanes = 3, widebodies = 1, hubs = 1, permits = 2, unlockedLines = 3;
  long generated, delivered;
  int nextPlaneId = 1, week = 1;
  double elapsed, passengerClock, airportClock;
  boolean rewardPending, gameOver;
  private TrackGeometry geometry;
  private String lastEvent = "Your first three airports are open. Draw a route to connect them.";

  public GameData(MapDefinition map, long seed) {
    this.map = map;
    random = new Random(seed);
    supplier = new AirportSupplier(map.airports(), random);
    for (LineColor color : LineColor.values()) lines.add(new Line(color));
    for (int i = 0; i < Math.min(3, map.airports().size()); i++) spawnAirport();
    rebuild();
  }

  public MapDefinition map() {
    return map;
  }

  public List<Airport> airports() {
    return List.copyOf(airports);
  }

  public List<Line> lines() {
    return List.copyOf(lines);
  }

  public List<Airplane> airplanes() {
    return List.copyOf(airplanes);
  }

  public Line line(int color) {
    return lines.get(color);
  }

  public int sparePlanes() {
    return sparePlanes;
  }

  public int widebodies() {
    return widebodies;
  }

  public int hubs() {
    return hubs;
  }

  public int permits() {
    return permits;
  }

  public int unlockedLines() {
    return unlockedLines;
  }

  public long delivered() {
    return delivered;
  }

  public long generated() {
    return generated;
  }

  public double elapsed() {
    return elapsed;
  }

  public int week() {
    return week;
  }

  public boolean rewardPending() {
    return rewardPending;
  }

  public boolean gameOver() {
    return gameOver;
  }

  public TrackGeometry geometry() {
    return geometry;
  }

  public String lastEvent() {
    return lastEvent;
  }

  public int waitingCount() {
    return airports.stream().mapToInt(Airport::waitingCount).sum();
  }

  public int airborneCount() {
    return airplanes.stream().mapToInt(a -> a.passengers.size()).sum();
  }

  public List<Upgrade> upgradeChoices() {
    return Arrays.stream(Upgrade.values())
        .filter(u -> u != Upgrade.LINE || unlockedLines < lines.size())
        .toList();
  }

  /** Validate a complete candidate before committing any resources or changing aircraft. */
  public String editRoute(int color, List<Airport> proposed, boolean loop) {
    if (gameOver) return "Start a new flight season to edit the network.";
    if (color < 0 || color >= unlockedLines) return "This airline is still locked.";
    if (!airports.containsAll(proposed)) return "Only open airports can join a route.";
    if (new HashSet<>(proposed).size() != proposed.size())
      return "A route cannot branch or visit an airport twice.";
    if (loop && proposed.size() < 3) return "A loop needs at least three airports.";
    List<Airport> path = proposed.size() < 2 ? List.of() : List.copyOf(proposed);
    if (path.isEmpty()) loop = false;
    Line old = lines.get(color), candidate = new Line(old.color());
    candidate.path.addAll(path);
    candidate.loop = loop;
    if (old.path.equals(path) && old.loop == loop) return null;
    List<Line> check = new ArrayList<>(lines);
    check.set(color, candidate);
    if (TrackGeometry.exceedsParallelLimit(check))
      return "A corridor can carry at most four parallel airlines.";
    int permitChange = permitCost(candidate) - permitCost(old);
    if (permitChange > permits) return "Not enough long-haul permits for this route.";
    boolean creating = old.size() == 0 && candidate.size() >= 2;
    if (creating && sparePlanes == 0) return "A new airline needs a spare aircraft.";

    record Flight(Airplane plane, Airport from, Airport to) {}
    List<Flight> affected =
        airplanes.stream()
            .filter(a -> a.line == old)
            .map(a -> new Flight(a, a.currentAirport(), old.get(a.nextIndex())))
            .toList();
    old.path.clear();
    old.path.addAll(path);
    old.loop = loop;
    permits -= permitChange;
    for (Flight flight : affected) {
      Airplane plane = flight.plane();
      int from = path.indexOf(flight.from()), to = path.indexOf(flight.to());
      boolean legSurvives =
          from >= 0
              && to >= 0
              && (loop ? (from + 1) % path.size() == to : Math.abs(from - to) == 1);
      if (legSurvives) {
        plane.index = from;
        plane.direction = loop || to > from ? 1 : -1;
      } else {
        // A withdrawn flight returns passengers to its last actual stop, never to an
        // unvisited destination. Unchanged flights keep their progress and their passengers.
        unload(plane, flight.from());
        if (old.size() < 2) {
          airplanes.remove(plane);
          sparePlanes++;
          if (plane.type == AirplaneType.WIDEBODY) widebodies++;
        } else {
          plane.reset(old);
          if (from >= 0) {
            plane.index = from;
            plane.direction = !loop && from == path.size() - 1 ? -1 : 1;
          }
        }
      }
    }
    if (creating) {
      sparePlanes--;
      airplanes.add(new Airplane(nextPlaneId++, old));
    }
    rebuild();
    return null;
  }

  public String addAirplane(int color) {
    if (gameOver) return "The season has ended.";
    if (color < 0 || color >= unlockedLines || lines.get(color).size() < 2)
      return "Draw a route first.";
    if (sparePlanes <= 0) return "No spare aircraft. Another arrives next week.";
    sparePlanes--;
    Airplane plane = new Airplane(nextPlaneId++, lines.get(color));
    // New aircraft still start at stop zero, but stagger departures instead of stacking forever.
    plane.dwell +=
        airplanes.stream()
                .filter(a -> a.line == plane.line && a.index == 0 && a.progress == 0)
                .count()
            * .8;
    airplanes.add(plane);
    return null;
  }

  public String moveAirplane(int id, int color) {
    if (gameOver) return "The season has ended.";
    Airplane plane = findPlane(id);
    if (plane == null || color < 0 || color >= unlockedLines || lines.get(color).size() < 2)
      return "Drop the aircraft onto an active airline.";
    if (plane.line == lines.get(color)) return null;
    unload(plane, plane.currentAirport());
    plane.reset(lines.get(color));
    return null;
  }

  public String upgradeAirplane(int id) {
    if (gameOver) return "The season has ended.";
    Airplane plane = findPlane(id);
    if (plane == null) return "Choose an aircraft to upgrade.";
    if (plane.type == AirplaneType.WIDEBODY) return "This aircraft already has 12 seats.";
    if (widebodies <= 0) return "No widebody upgrades available.";
    widebodies--;
    plane.type = AirplaneType.WIDEBODY;
    return null;
  }

  public String upgradeAirport(Airport airport) {
    if (gameOver) return "The season has ended.";
    if (!airports.contains(airport)) return "Choose an open airport.";
    if (airport.hub) return "This airport already has a hub terminal.";
    if (hubs <= 0) return "No hub terminals available.";
    hubs--;
    airport.hub = true;
    return null;
  }

  public String chooseUpgrade(Upgrade upgrade) {
    if (!rewardPending || !upgradeChoices().contains(upgrade))
      return "No upgrade is available right now.";
    switch (upgrade) {
      case LINE -> unlockedLines++;
      case CAPACITY -> widebodies++;
      case PERMITS -> permits += 2;
      case HUB -> hubs++;
    }
    rewardPending = false;
    lastEvent = "Week " + week + ": " + upgrade.title + " added to your resources.";
    return null;
  }

  public int permitCost(Line line) {
    int cost = 0;
    for (int i = 0; i < line.segmentCount(); i++)
      if (MapDefinition.distanceKm(line.get(i).spec(), line.segmentEnd(i).spec())
          > map.longHaulKm()) cost++;
    return cost;
  }

  private Airplane findPlane(int id) {
    return airplanes.stream().filter(a -> a.id() == id).findFirst().orElse(null);
  }

  void rebuild() {
    router.rebuild(airports, lines);
    geometry = new TrackGeometry(lines);
  }

  void spawnAirport() {
    if (!supplier.hasNext()) return;
    AirportSpec spec = supplier.next(airports);
    airports.add(new Airport(spec, map.project(spec.latitude(), spec.longitude())));
    lastEvent = spec.city() + " (" + spec.code() + ") is now open.";
    rebuild();
  }

  void spawnPassengers() {
    List<Shape> shapes = airports.stream().map(Airport::shape).distinct().toList();
    if (shapes.size() < 2) return;
    for (Airport airport : airports) {
      if (random.nextDouble() > Math.min(.72, .27 + week * .025)) continue;
      List<Shape> destinations = shapes.stream().filter(s -> s != airport.shape()).toList();
      airport.waiting.add(
          new Passenger(++generated, destinations.get(random.nextInt(destinations.size()))));
    }
  }

  void unload(Airplane plane, Airport airport) {
    for (Passenger p : plane.passengers) {
      if (p.destination() == airport.shape()) delivered++;
      else airport.waiting.add(p);
    }
    plane.passengers.clear();
  }

  void board(Airplane plane) {
    Airport airport = plane.currentAirport(), next = plane.line.get(plane.nextIndex());
    Iterator<Passenger> it = airport.waiting.iterator();
    while (it.hasNext() && plane.passengers.size() < plane.capacity()) {
      Passenger p = it.next();
      if (router.shouldBoard(p, airport, next)) {
        plane.passengers.add(p);
        it.remove();
      }
    }
  }
}
