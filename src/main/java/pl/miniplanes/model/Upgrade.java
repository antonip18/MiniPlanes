package pl.miniplanes.model;

public enum Upgrade {
  LINE("New airline", "Unlock another route colour"),
  CAPACITY("Widebody", "Upgrade one aircraft from 6 to 12 seats"),
  PERMITS("2 long-haul permits", "Open two more long-distance legs"),
  HUB("Hub terminal", "Double one airport's waiting capacity");
  public final String title, description;

  Upgrade(String title, String description) {
    this.title = title;
    this.description = description;
  }
}
