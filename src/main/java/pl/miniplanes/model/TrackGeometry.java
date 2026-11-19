package pl.miniplanes.model;

import java.util.*;

/**
 * Straight corridors with direction-independent perpendicular offsets, including vertical tracks.
 */
public final class TrackGeometry {
  public static final double LANE_SPACING = 9;
  public static final int MAX_PARALLEL = 4;

  public record Segment(Line line, int index, Point a, Point b) {}

  private final List<Segment> segments;

  public TrackGeometry(List<Line> lines) {
    List<Segment> raw = rawSegments(lines);
    List<List<Segment>> groups = groups(raw);
    List<Segment> result = new ArrayList<>();
    for (List<Segment> group : groups) {
      List<LineColor> colors = group.stream().map(s -> s.line.color()).distinct().sorted().toList();
      Point normal = canonicalDirection(group.getFirst()).normal();
      double center = group.stream().mapToDouble(s -> s.a.dot(normal)).average().orElse(0);
      for (Segment s : group) {
        double lane = colors.indexOf(s.line.color()) - (colors.size() - 1) / 2.0;
        Point offset = normal.multiply(center + lane * LANE_SPACING - s.a.dot(normal));
        result.add(new Segment(s.line, s.index, s.a.add(offset), s.b.add(offset)));
      }
    }
    segments = List.copyOf(result);
  }

  public List<Segment> segments() {
    return segments;
  }

  public Segment segment(Line line, int index) {
    return segments.stream()
        .filter(s -> s.line == line && s.index == index)
        .findFirst()
        .orElseThrow();
  }

  public Point aircraftPosition(Airplane plane) {
    int next = plane.nextIndex();
    int segment = plane.line().isLoop() ? plane.index() : Math.min(plane.index(), next);
    Segment s = segment(plane.line(), segment);
    boolean forward = plane.line().isLoop() || next > plane.index();
    Point a = forward ? s.a : s.b, b = forward ? s.b : s.a;
    return a.lerp(b, plane.progress());
  }

  public double aircraftAngle(Airplane plane) {
    Point d =
        plane.line().get(plane.nextIndex()).position().subtract(plane.currentAirport().position());
    return Math.toDegrees(Math.atan2(d.y(), d.x()));
  }

  /** Sweep each collinear corridor. Count colours only where their intervals actually overlap. */
  public static boolean exceedsParallelLimit(List<Line> lines) {
    for (List<Segment> group : groups(rawSegments(lines))) {
      Point axis = canonicalDirection(group.getFirst());
      TreeSet<Double> endpoints = new TreeSet<>();
      for (Segment s : group) {
        endpoints.add(s.a.dot(axis));
        endpoints.add(s.b.dot(axis));
      }
      List<Double> stops = new ArrayList<>(endpoints);
      for (int i = 1; i < stops.size(); i++) {
        if (stops.get(i) - stops.get(i - 1) < 1e-6) continue;
        double middle = (stops.get(i) + stops.get(i - 1)) / 2;
        Set<LineColor> active = EnumSet.noneOf(LineColor.class);
        for (Segment s : group) {
          double a = s.a.dot(axis), b = s.b.dot(axis);
          if (middle > Math.min(a, b) && middle < Math.max(a, b)) active.add(s.line.color());
        }
        if (active.size() > MAX_PARALLEL) return true;
      }
    }
    return false;
  }

  private static List<Segment> rawSegments(List<Line> lines) {
    List<Segment> result = new ArrayList<>();
    for (Line line : lines)
      for (int i = 0; i < line.segmentCount(); i++)
        result.add(new Segment(line, i, line.get(i).position(), line.segmentEnd(i).position()));
    return result;
  }

  private static Point canonicalDirection(Segment s) {
    Point d = s.b.subtract(s.a).unit();
    return d.x() < -1e-8 || (Math.abs(d.x()) < 1e-8 && d.y() < 0) ? d.multiply(-1) : d;
  }

  private static boolean sameCorridor(Segment a, Segment b) {
    Point axis = canonicalDirection(a), other = canonicalDirection(b);
    if (Math.abs(axis.cross(other)) > 1e-6) return false;
    // Also separate almost coincident parallel tracks (small map-coordinate rounding errors).
    if (Math.abs(b.a.subtract(a.a).cross(axis)) > 3) return false;
    double a0 = a.a.dot(axis), a1 = a.b.dot(axis), b0 = b.a.dot(axis), b1 = b.b.dot(axis);
    return Math.min(Math.max(a0, a1), Math.max(b0, b1))
            - Math.max(Math.min(a0, a1), Math.min(b0, b1))
        > 1e-6;
  }

  private static List<List<Segment>> groups(List<Segment> raw) {
    int[] parent = new int[raw.size()];
    for (int i = 0; i < parent.length; i++) parent[i] = i;
    for (int i = 0; i < raw.size(); i++)
      for (int j = i + 1; j < raw.size(); j++)
        if (sameCorridor(raw.get(i), raw.get(j))) parent[root(parent, i)] = root(parent, j);
    Map<Integer, List<Segment>> groups = new LinkedHashMap<>();
    for (int i = 0; i < raw.size(); i++)
      groups.computeIfAbsent(root(parent, i), k -> new ArrayList<>()).add(raw.get(i));
    return new ArrayList<>(groups.values());
  }

  private static int root(int[] p, int i) {
    while (p[i] != i) {
      p[i] = p[p[i]];
      i = p[i];
    }
    return i;
  }
}
