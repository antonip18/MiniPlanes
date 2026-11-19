package pl.miniplanes.model;

import java.util.ArrayList;
import java.util.List;

public final class Airport {
  private final AirportSpec spec;
  private final Point position;
  final List<Passenger> waiting = new ArrayList<>();
  double crowdedSeconds;
  boolean hub;

  public Airport(AirportSpec spec, Point position) {
    this.spec = spec;
    this.position = position;
  }

  public AirportSpec spec() {
    return spec;
  }

  public String code() {
    return spec.code();
  }

  public Shape shape() {
    return spec.shape();
  }

  public Point position() {
    return position;
  }

  public List<Passenger> waiting() {
    return List.copyOf(waiting);
  }

  public int waitingCount() {
    return waiting.size();
  }

  public int capacity() {
    return hub ? 28 : 14;
  }

  public boolean isHub() {
    return hub;
  }

  public double crowding() {
    return crowdedSeconds / GameData.OVERCROWD_SECONDS;
  }
}
