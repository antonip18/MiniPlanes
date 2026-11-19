package pl.miniplanes.model;

/** Immutable map coordinates; evolved from the prototype's mutable Point. */
public record Point(double x, double y) {
  public Point add(Point p) {
    return new Point(x + p.x, y + p.y);
  }

  public Point subtract(Point p) {
    return new Point(x - p.x, y - p.y);
  }

  public Point multiply(double k) {
    return new Point(x * k, y * k);
  }

  public double dot(Point p) {
    return x * p.x + y * p.y;
  }

  public double cross(Point p) {
    return x * p.y - y * p.x;
  }

  public double length() {
    return Math.hypot(x, y);
  }

  public double distance(Point p) {
    return subtract(p).length();
  }

  public Point unit() {
    double l = length();
    return l < 1e-9 ? new Point(1, 0) : multiply(1 / l);
  }

  public Point normal() {
    return new Point(-y, x);
  }

  public Point lerp(Point p, double t) {
    return multiply(1 - t).add(p.multiply(t));
  }

  public static double distanceToSegment(Point p, Point a, Point b) {
    Point d = b.subtract(a);
    double t = d.dot(d) < 1e-9 ? 0 : Math.clamp(p.subtract(a).dot(d) / d.dot(d), 0, 1);
    return p.distance(a.lerp(b, t));
  }
}
