package pl.miniplanes.model;

/** Fixed-step simulation. Caller controls pause/speed; there are no background mutation races. */
public final class Updater {
  public static final double STEP = 1.0 / 60;
  private final GameData game;

  public Updater(GameData game) {
    this.game = game;
  }

  public void step(double dt) {
    if (!Double.isFinite(dt) || dt <= 0 || dt > .25)
      throw new IllegalArgumentException("Use a fixed step of at most 0.25 seconds");
    if (game.gameOver || game.rewardPending) return;
    game.elapsed += dt;
    for (Airplane plane : game.airplanes) {
      if (plane.dwell > 0) {
        plane.dwell -= dt;
        if (plane.dwell <= 0) game.board(plane);
      } else {
        double distance =
            plane
                .currentAirport()
                .position()
                .distance(plane.line.get(plane.nextIndex()).position());
        plane.progress += dt * 42 / Math.max(1, distance);
        if (plane.progress >= 1) {
          plane.index = plane.nextIndex();
          plane.progress = 0;
          plane.dwell = 1.2;
          if (!plane.line.isLoop()) {
            if (plane.index == 0) plane.direction = 1;
            if (plane.index == plane.line.size() - 1) plane.direction = -1;
          }
          game.unload(plane, plane.currentAirport());
        }
      }
    }
    game.passengerClock += dt;
    game.airportClock += dt;
    if (game.passengerClock >= 2) {
      game.passengerClock -= 2;
      game.spawnPassengers();
    }
    if (game.airportClock >= 26) {
      game.airportClock -= 26;
      game.spawnAirport();
    }
    for (Airport airport : game.airports) {
      airport.crowdedSeconds =
          airport.waiting.size() > airport.capacity()
              ? airport.crowdedSeconds + dt
              : Math.max(0, airport.crowdedSeconds - 2 * dt);
      if (airport.crowdedSeconds >= GameData.OVERCROWD_SECONDS) game.gameOver = true;
    }
    if (!game.gameOver && game.elapsed + 1e-6 >= game.week * GameData.WEEK_SECONDS) {
      game.week++;
      game.sparePlanes++;
      game.rewardPending = true;
    }
  }
}
