package pl.miniplanes.view;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import pl.miniplanes.model.Shape;

public final class ShapePainter {
  private ShapePainter() {}

  public static void draw(
      GraphicsContext g,
      Shape shape,
      double x,
      double y,
      double r,
      Color fill,
      Color stroke,
      double width) {
    g.setFill(fill);
    g.setStroke(stroke);
    g.setLineWidth(width);
    if (shape == Shape.CIRCLE) {
      g.fillOval(x - r, y - r, r * 2, r * 2);
      g.strokeOval(x - r, y - r, r * 2, r * 2);
      return;
    }
    if (shape == Shape.CROSS) {
      double[] a = {-1, -.34, -.34, .34, .34, 1, 1, .34, .34, -.34, -.34, -1};
      double[] b = {-.34, -.34, -1, -1, -.34, -.34, .34, .34, 1, 1, .34, .34};
      for (int i = 0; i < a.length; i++) {
        a[i] = x + a[i] * r;
        b[i] = y + b[i] * r;
      }
      g.fillPolygon(a, b, a.length);
      g.strokePolygon(a, b, a.length);
      return;
    }
    int n =
        switch (shape) {
          case TRIANGLE -> 3;
          case SQUARE, DIAMOND -> 4;
          case PENTAGON -> 5;
          case HEXAGON -> 6;
          case STAR -> 10;
          default -> 4;
        };
    double[] xs = new double[n], ys = new double[n];
    double angle = shape == Shape.SQUARE ? Math.PI / 4 : -Math.PI / 2;
    double radius = shape == Shape.SQUARE ? r * 1.1 : r;
    for (int i = 0; i < n; i++) {
      double k = shape == Shape.STAR && i % 2 == 1 ? .46 : 1;
      xs[i] = x + Math.cos(angle + i * 2 * Math.PI / n) * radius * k;
      ys[i] = y + Math.sin(angle + i * 2 * Math.PI / n) * radius * k;
    }
    g.fillPolygon(xs, ys, n);
    g.strokePolygon(xs, ys, n);
  }
}
