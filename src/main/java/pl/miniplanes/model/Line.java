package pl.miniplanes.model;

import java.util.ArrayList;
import java.util.List;

/** An ordered path or cycle, never a branching graph. Only GameData may mutate it. */
public final class Line {
  private final LineColor color;
  final List<Airport> path = new ArrayList<>();
  boolean loop;

  public Line(LineColor color) {
    this.color = color;
  }

  public LineColor color() {
    return color;
  }

  public List<Airport> path() {
    return List.copyOf(path);
  }

  public int size() {
    return path.size();
  }

  public Airport get(int index) {
    return path.get(index);
  }

  public boolean isLoop() {
    return loop;
  }

  public int segmentCount() {
    return Math.max(0, size() - 1) + (loop ? 1 : 0);
  }

  public Airport segmentEnd(int segment) {
    return get((segment + 1) % size());
  }
}
