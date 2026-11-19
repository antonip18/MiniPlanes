package pl.miniplanes.view;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import pl.miniplanes.model.MapDefinition;
import pl.miniplanes.model.Point;

/** Cached geographic paths; re-rasterized at screen resolution whenever the camera changes. */
final class MapPainter {
  private record Outline(List<List<Point>> rings) {}

  private record Place(String text, double latitude, double longitude, boolean water) {}

  private final List<Outline> outlines = new ArrayList<>();
  private final MapDefinition map;

  MapPainter(MapDefinition map) {
    this.map = map;
    for (var country : map.countries()) {
      outlines.add(
          new Outline(
              country.rings().stream()
                  .map(r -> r.stream().map(p -> map.project(p.y(), p.x())).toList())
                  .toList()));
    }
  }

  void draw(GraphicsContext g, double width, double height, Function<Point, Point> screen) {
    g.setFill(Color.web("#DFEAE8"));
    g.fillRect(0, 0, width, height);
    g.setFillRule(FillRule.EVEN_ODD);
    for (Outline outline : outlines) {
      g.beginPath();
      for (var ring : outline.rings) {
        for (int i = 0; i < ring.size(); i++) {
          Point p = screen.apply(ring.get(i));
          if (i == 0) g.moveTo(p.x(), p.y());
          else g.lineTo(p.x(), p.y());
        }
        g.closePath();
      }
      g.setFill(Color.web("#F8F4E9"));
      g.fill();
      g.setStroke(Color.web("#A6B6A9"));
      g.setLineWidth(.8);
      g.stroke();
    }
    if (map.id().equals("americas")) {
      List<Place> labels =
          List.of(
              new Place("C A N A D A", 56, -108, false),
              new Place("U N I T E D   S T A T E S", 39, -101, false),
              new Place("M E X I C O", 26, -106, false),
              new Place("B R A Z I L", -10, -52, false),
              new Place("A R G E N T I N A", -40, -66, false),
              new Place("P A C I F I C   O C E A N", 7, -125, true),
              new Place("A T L A N T I C   O C E A N", 6, -40, true));
      for (Place place : labels) {
        Point p = screen.apply(map.project(place.latitude, place.longitude));
        if (p.x() < 100 || p.x() > width - 100 || p.y() < 65 || p.y() > height - 45) continue;
        g.setFill(Color.web(place.water ? "#7F9D9B" : "#A1AC9D", .8));
        g.setFont(Font.font("SansSerif", FontWeight.NORMAL, place.water ? 10 : 9));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(place.text, p.x(), p.y());
      }
    }
  }
}
