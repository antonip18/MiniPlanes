package pl.miniplanes.model;

import java.util.*;

/**
 * Reverse Dijkstra from each destination shape. Only decreasing-distance flights accept passengers.
 */
public final class PassengerRouter {
  private record Edge(Airport origin, double cost) {}

  private record Visit(Airport airport, double cost) {}

  private final Map<Shape, Map<Airport, Double>> distances = new EnumMap<>(Shape.class);

  public void rebuild(List<Airport> airports, List<Line> lines) {
    Map<Airport, List<Edge>> reverse = new HashMap<>();
    for (Airport a : airports) reverse.put(a, new ArrayList<>());
    for (Line l : lines)
      for (int i = 0; i < l.segmentCount(); i++) {
        Airport a = l.get(i), b = l.segmentEnd(i);
        double distance = a.position().distance(b.position());
        reverse.get(b).add(new Edge(a, distance));
        if (!l.isLoop()) reverse.get(a).add(new Edge(b, distance));
      }
    distances.clear();
    for (Shape shape : Shape.values()) {
      Map<Airport, Double> dist = new HashMap<>();
      PriorityQueue<Visit> queue = new PriorityQueue<>(Comparator.comparingDouble(Visit::cost));
      for (Airport airport : airports)
        if (airport.shape() == shape) {
          dist.put(airport, 0.0);
          queue.add(new Visit(airport, 0));
        }
      while (!queue.isEmpty()) {
        Visit v = queue.remove();
        if (v.cost > dist.getOrDefault(v.airport, Double.POSITIVE_INFINITY)) continue;
        for (Edge edge : reverse.get(v.airport)) {
          double next = v.cost + edge.cost;
          if (next + 1e-6 < dist.getOrDefault(edge.origin, Double.POSITIVE_INFINITY)) {
            dist.put(edge.origin, next);
            queue.add(new Visit(edge.origin, next));
          }
        }
      }
      distances.put(shape, dist);
    }
  }

  public boolean shouldBoard(Passenger p, Airport from, Airport next) {
    Map<Airport, Double> dist = distances.getOrDefault(p.destination(), Map.of());
    double a = dist.getOrDefault(from, Double.POSITIVE_INFINITY);
    double b = dist.getOrDefault(next, Double.POSITIVE_INFINITY);
    return Double.isFinite(a)
        && Double.isFinite(b)
        && b < a
        && b + from.position().distance(next.position()) <= a + 1e-5;
  }
}
