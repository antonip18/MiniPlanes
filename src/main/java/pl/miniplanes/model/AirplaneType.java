package pl.miniplanes.model;

public enum AirplaneType {
  SMALL(6),
  WIDEBODY(12);
  public final int capacity;

  AirplaneType(int capacity) {
    this.capacity = capacity;
  }
}
