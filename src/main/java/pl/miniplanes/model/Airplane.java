package pl.miniplanes.model;

import java.util.ArrayList;
import java.util.List;

/** The original out-and-back flight model, now advanced with seconds on one simulation thread. */
public final class Airplane {
  private final int id;
  Line line;
  int index, direction = 1;
  double progress, dwell = 1.2;
  AirplaneType type = AirplaneType.SMALL;
  final List<Passenger> passengers = new ArrayList<>();

  Airplane(int id, Line line) {
    this.id = id;
    this.line = line;
  }

  public int id() {
    return id;
  }

  public Line line() {
    return line;
  }

  public int index() {
    return index;
  }

  public int direction() {
    return direction;
  }

  public double progress() {
    return progress;
  }

  public boolean isFlying() {
    return dwell <= 0;
  }

  public AirplaneType type() {
    return type;
  }

  public int capacity() {
    return type.capacity;
  }

  public List<Passenger> passengers() {
    return List.copyOf(passengers);
  }

  public int nextIndex() {
    if (line.isLoop()) return (index + 1) % line.size();
    int d = index == 0 ? 1 : index == line.size() - 1 ? -1 : direction;
    return index + d;
  }

  public Airport currentAirport() {
    return line.get(index);
  }

  void reset(Line target) {
    line = target;
    index = 0;
    direction = 1;
    progress = 0;
    dwell = 1.2;
  }
}
