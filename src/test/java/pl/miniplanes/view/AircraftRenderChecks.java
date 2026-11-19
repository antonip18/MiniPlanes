package pl.miniplanes.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.IntStream;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import pl.miniplanes.model.AirplaneType;
import pl.miniplanes.model.LineColor;
import pl.miniplanes.model.Passenger;
import pl.miniplanes.model.Shape;

/** Pixel checks executed by the existing UI test, on its JavaFX application thread. */
public final class AircraftRenderChecks {
  private AircraftRenderChecks() {}

  public static void verify() {
    for (AirplaneType type : AirplaneType.values()) {
      for (LineColor line : LineColor.values()) {
        for (double angle : new double[] {0, 37.5, 90, 172.8}) {
          WritableImage normal = render(type, line, angle, 1);
          WritableImage zoomed = render(type, line, angle, 3);
          Color expected = Color.web(line.hex);
          Color actual = zoomed.getPixelReader().getColor(150, 150);
          assertEquals(expected.getRed(), actual.getRed(), .005);
          assertEquals(expected.getGreen(), actual.getGreen(), .005);
          assertEquals(expected.getBlue(), actual.getBlue(), .005);
          int[] a = bounds(normal), b = bounds(zoomed);
          assertTrue(a[0] <= 24 && a[1] <= 24, "Aircraft must retain the smaller base size");
          assertEquals(
              3.0, (double) b[0] / a[0], .16, "Zoom must scale width, not just move the icon");
          assertEquals(3.0, (double) b[1] / a[1], .16, "Zoom must scale height at every rotation");
          // A freshly rasterized vector edge has a narrow antialiasing fringe, not a scaled
          // block of partially transparent pixels inherited from a low-resolution sprite.
          assertTrue(edgePixels(zoomed) < edgePixels(normal) * 4.5);
        }
      }
      for (int count : new int[] {1, type.capacity}) {
        List<Passenger> passengers =
            IntStream.range(0, count)
                .mapToObj(i -> new Passenger(i, Shape.values()[i % Shape.values().length]))
                .toList();
        for (double angle : new double[] {0, 90, 217}) {
          for (double scale : new double[] {1, 3}) {
            WritableImage occupied = render(type, LineColor.CORAL, angle, scale, passengers);
            int below = (int) Math.ceil(150.25 + AirplanePainter.length(type) * scale * .55);
            assertEquals(
                count,
                passengerComponents(occupied, below),
                "Every onboard passenger must be visible at any heading and zoom");
          }
        }
      }
    }
  }

  private static WritableImage render(
      AirplaneType type, LineColor line, double angle, double scale) {
    return render(type, line, angle, scale, List.of());
  }

  private static WritableImage render(
      AirplaneType type, LineColor line, double angle, double scale, List<Passenger> passengers) {
    Canvas canvas = new Canvas(300, 300);
    AirplanePainter.draw(
        canvas.getGraphicsContext2D(), type, line, 150.25, 150.25, angle, scale, passengers);
    SnapshotParameters options = new SnapshotParameters();
    options.setFill(Color.TRANSPARENT);
    return canvas.snapshot(options, null);
  }

  private static int passengerComponents(WritableImage image, int below) {
    boolean[] ink = new boolean[300 * 300];
    for (int y = below; y < 300; y++)
      for (int x = 0; x < 300; x++) {
        int pixel = image.getPixelReader().getArgb(x, y);
        ink[y * 300 + x] =
            (pixel >>> 24) > 200
                && ((pixel >>> 16) & 255) < 110
                && ((pixel >>> 8) & 255) < 110
                && (pixel & 255) < 110;
      }
    int components = 0;
    ArrayDeque<Integer> pending = new ArrayDeque<>();
    for (int seed = 0; seed < ink.length; seed++) {
      if (!ink[seed]) continue;
      components++;
      ink[seed] = false;
      pending.add(seed);
      while (!pending.isEmpty()) {
        int p = pending.removeFirst(), x = p % 300, y = p / 300;
        for (int dy = -1; dy <= 1; dy++)
          for (int dx = -1; dx <= 1; dx++) {
            int nx = x + dx, ny = y + dy;
            if (nx < 0 || nx >= 300 || ny < below || ny >= 300) continue;
            int next = ny * 300 + nx;
            if (ink[next]) {
              ink[next] = false;
              pending.add(next);
            }
          }
      }
    }
    return components;
  }

  private static int[] bounds(WritableImage image) {
    int minX = 300, minY = 300, maxX = 0, maxY = 0;
    for (int y = 0; y < 300; y++)
      for (int x = 0; x < 300; x++) {
        if ((image.getPixelReader().getArgb(x, y) >>> 24) > 127) {
          minX = Math.min(minX, x);
          maxX = Math.max(maxX, x);
          minY = Math.min(minY, y);
          maxY = Math.max(maxY, y);
        }
      }
    return new int[] {maxX - minX + 1, maxY - minY + 1};
  }

  private static int edgePixels(WritableImage image) {
    int count = 0;
    for (int y = 0; y < 300; y++)
      for (int x = 0; x < 300; x++) {
        int alpha = image.getPixelReader().getArgb(x, y) >>> 24;
        if (alpha > 5 && alpha < 250) count++;
      }
    return count;
  }
}
