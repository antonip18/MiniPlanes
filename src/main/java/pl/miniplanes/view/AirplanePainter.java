package pl.miniplanes.view;

import java.util.List;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineJoin;
import pl.miniplanes.model.AirplaneType;
import pl.miniplanes.model.LineColor;
import pl.miniplanes.model.Passenger;

/**
 * Resolution-independent silhouettes, drawn at their final size and rotation. Nose points right.
 */
public final class AirplanePainter {
  // One outline, with generous wings and no subpixel decorative details at overview scale.
  static final String SMALL =
      "M 31,0 C 31,-2.2 25,-4 19,-4 L 5,-4 L -8,-24 "
          + "Q -9,-25 -12,-25 L -15,-25 L -7,-4 L -23,-3 L -29,-12 L -33,-12 "
          + "L -29,0 L -33,12 L -29,12 L -23,3 L -7,4 L -15,25 L -12,25 "
          + "Q -9,25 -8,24 L 5,4 L 19,4 C 25,4 31,2.2 31,0 Z";
  static final String WIDE =
      "M 32,0 C 32,-3 25,-5 19,-5 L 6,-5 L -9,-28 "
          + "Q -10,-29 -13,-29 L -17,-29 L -8,-5 L -24,-4 L -30,-14 L -34,-14 "
          + "L -30,0 L -34,14 L -30,14 L -24,4 L -8,5 L -17,29 L -13,29 "
          + "Q -10,29 -9,28 L 6,5 L 19,5 C 25,5 32,3 32,0 Z";
  private static final Color OUTLINE = Color.web("#FFFDF6");
  private static final Color COCKPIT = Color.web("#173E47");
  private static final Color PASSENGER_INK = Color.web("#263F47");

  private AirplanePainter() {}

  public static double length(AirplaneType type) {
    return type == AirplaneType.WIDEBODY ? 21.5 : 17.5;
  }

  public static void draw(
      GraphicsContext g,
      AirplaneType type,
      LineColor color,
      double x,
      double y,
      double angle,
      double cameraScale) {
    draw(g, type, color, x, y, angle, cameraScale, List.of());
  }

  public static void draw(
      GraphicsContext g,
      AirplaneType type,
      LineColor color,
      double x,
      double y,
      double angle,
      double cameraScale,
      List<Passenger> passengers) {
    g.save();
    g.translate(x, y);
    g.rotate(angle);
    double factor = length(type) * cameraScale / 66;
    g.scale(factor, factor);
    g.setLineJoin(StrokeLineJoin.ROUND);
    g.beginPath();
    g.appendSVGPath(type == AirplaneType.WIDEBODY ? WIDE : SMALL);
    g.setFill(Color.web(color.hex));
    g.fill();
    g.setStroke(OUTLINE);
    g.setLineWidth(2.1);
    g.stroke();
    // The dark cockpit remains legible without the old sprite's dense window/wing textures.
    g.beginPath();
    g.appendSVGPath("M 22,-2.8 Q 27,-2.2 28,0 Q 27,2.2 22,2.8 L 23,0 Z");
    g.setFill(COCKPIT);
    g.fill();
    if (type == AirplaneType.WIDEBODY) {
      g.setFill(Color.web(color.hex).deriveColor(0, 1, .82, 1));
      g.fillRoundRect(-3, -13, 10, 4, 3, 3);
      g.fillRoundRect(-3, 9, 10, 4, 3, 3);
    }
    g.restore();
    drawPassengers(g, type, x, y, cameraScale, passengers);
  }

  /** Keep every onboard destination legible and upright, following the aircraft in screen space. */
  private static void drawPassengers(
      GraphicsContext g,
      AirplaneType type,
      double x,
      double y,
      double cameraScale,
      List<Passenger> passengers) {
    if (passengers.isEmpty()) return;
    double radius = Math.max(2.4, 1.6 * cameraScale);
    double spacing = radius * 2.8;
    int columns = Math.min(6, passengers.size());
    int rows = (passengers.size() + 5) / 6;
    double width = (columns - 1) * spacing + radius * 3.4;
    double height = (rows - 1) * spacing + radius * 3.4;
    double top = y + length(type) * cameraScale * .55 + 3;
    g.save();
    g.setFill(Color.web("#FFFDF6", .96));
    g.fillRoundRect(x - width / 2, top, width, height, radius * 2, radius * 2);
    for (int i = 0; i < passengers.size(); i++) {
      int row = i / 6;
      int rowSize = Math.min(6, passengers.size() - row * 6);
      ShapePainter.draw(
          g,
          passengers.get(i).destination(),
          x + (i % 6 - (rowSize - 1) / 2.0) * spacing,
          top + radius * 1.7 + row * spacing,
          radius,
          PASSENGER_INK,
          PASSENGER_INK,
          .55);
    }
    g.restore();
  }
}
