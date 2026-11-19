package pl.miniplanes.model;

import java.util.*;

/** Seeded version of the prototype's distance-weighted airport supplier. */
public final class AirportSupplier {
  private final List<AirportSpec> remaining;
  private final Random random;

  public AirportSupplier(List<AirportSpec> specs, Random random) {
    remaining = new ArrayList<>(specs);
    this.random = random;
  }

  public boolean hasNext() {
    return !remaining.isEmpty();
  }

  public AirportSpec next(List<Airport> active) {
    if (active.size() < 3) return remaining.removeFirst();
    double[] weights = new double[remaining.size()];
    double sum = 0;
    for (int i = 0; i < remaining.size(); i++) {
      AirportSpec s = remaining.get(i);
      double nearest =
          active.stream()
              .mapToDouble(a -> MapDefinition.distanceKm(a.spec(), s))
              .min()
              .orElse(1000);
      weights[i] = 1 / Math.max(350, nearest);
      sum += weights[i];
    }
    double pick = random.nextDouble() * sum;
    for (int i = 0; i < weights.length; i++) {
      pick -= weights[i];
      if (pick <= 0) return remaining.remove(i);
    }
    return remaining.removeLast();
  }
}
