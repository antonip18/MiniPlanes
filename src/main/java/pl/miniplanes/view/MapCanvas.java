package pl.miniplanes.view;

import java.util.*;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.*;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import pl.miniplanes.model.*;
import pl.miniplanes.viewmodel.*;

/** Drawing, camera and hit testing only. All game commands go through GameViewModel. */
public final class MapCanvas extends Region {
  public static final String DRAG_AIRCRAFT = "mini-planes:aircraft:",
      DRAG_RESOURCE = "mini-planes:resource:";
  private static final Color INK = Color.web("#263F47"), PAPER = Color.web("#F8F4E9");

  public enum RegionPreset {
    OVERVIEW,
    NORTH,
    SOUTH
  }

  private final Canvas backgroundCanvas = new Canvas();
  private final Canvas canvas = new Canvas();
  private final GameViewModel vm;
  private MapDefinition cachedMap;
  private MapPainter mapPainter;
  private double paintedZoom = Double.NaN, paintedPanX, paintedPanY, paintedWidth, paintedHeight;
  private RegionPreset preset = RegionPreset.OVERVIEW;
  private double zoom = 1, panX, panY;
  private Point cursor = new Point(0, 0), panStart;
  private double panStartX, panStartY;
  private Airport hovered;
  private int draggingPlane = -1;
  private boolean dragMoved;
  private Point pressPoint;

  private record Handle(int color, boolean first, Point center, Point direction, Point anchor) {}

  public MapCanvas(GameViewModel vm) {
    this.vm = vm;
    backgroundCanvas.setMouseTransparent(true);
    getChildren().addAll(backgroundCanvas, canvas);
    setMinSize(260, 260);
    setFocusTraversable(true);
    setId("map");
    canvas.setOnMousePressed(this::press);
    canvas.setOnMouseDragged(this::drag);
    canvas.setOnMouseReleased(this::release);
    canvas.setOnMouseMoved(
        e -> {
          cursor = world(e.getX(), e.getY());
          hovered = airportAt(cursor);
          setCursor(
              hovered != null || planeAt(cursor) != null || segmentAt(cursor) != null
                  ? Cursor.HAND
                  : Cursor.DEFAULT);
        });
    canvas.setOnMouseExited(e -> hovered = null);
    canvas.setOnScroll(
        e -> {
          zoomAt(Math.pow(1.0015, e.getDeltaY()), e.getX(), e.getY());
          e.consume();
        });
    canvas.setOnDragOver(
        e -> {
          if (e.getDragboard().hasString())
            e.acceptTransferModes(TransferMode.COPY, TransferMode.MOVE);
          e.consume();
        });
    canvas.setOnDragDropped(
        e -> {
          String value = e.getDragboard().getString();
          Point p = world(e.getX(), e.getY());
          boolean handled = drop(value, p);
          e.setDropCompleted(handled);
          e.consume();
        });
  }

  @Override
  protected void layoutChildren() {
    backgroundCanvas.setWidth(getWidth());
    backgroundCanvas.setHeight(getHeight());
    paintedZoom = Double.NaN;
    if (preset != null) focus(preset);
    canvas.setWidth(getWidth());
    canvas.setHeight(getHeight());
    draw();
  }

  public void resetCamera() {
    focus(vm.game().map().id().equals("americas") ? RegionPreset.NORTH : RegionPreset.OVERVIEW);
    hovered = null;
  }

  public RegionPreset preset() {
    return preset;
  }

  public void focus(RegionPreset region) {
    preset = region;
    zoom = 1;
    panX = panY = 0;
    if (!vm.game().map().id().equals("americas") || getWidth() < 1 || getHeight() < 1) return;
    MapDefinition map = vm.game().map();
    Point topLeft =
        switch (region) {
          case NORTH -> map.project(62, -137);
          case SOUTH -> map.project(14, -84);
          case OVERVIEW -> map.project(84, -180);
        };
    Point bottomRight =
        switch (region) {
          case NORTH -> map.project(-6, -45);
          case SOUTH -> map.project(-57, -32);
          case OVERVIEW -> map.project(-58, -10);
        };
    double fit =
        Math.min(
            (getWidth() - 100) / (bottomRight.x() - topLeft.x()),
            (getHeight() - 100) / (bottomRight.y() - topLeft.y()));
    zoom = Math.clamp(fit / baseScale(), .65, 5);
    Point center = topLeft.lerp(bottomRight, .5);
    panX = (600 - center.x()) * scale();
    panY = (390 - center.y()) * scale();
  }

  public void zoomAt(double factor, double x, double y) {
    double previous = zoom;
    zoom = Math.clamp(zoom * factor, vm.game().map().id().equals("americas") ? .65 : 1, 5);
    double actual = zoom / previous;
    panX = (panX + getWidth() / 2 - x) * actual + x - getWidth() / 2;
    panY = (panY + getHeight() / 2 - y) * actual + y - getHeight() / 2;
    preset = null;
  }

  public int airportsOutsideView() {
    return (int)
        vm.game().airports().stream()
            .filter(
                a -> {
                  Point p = screen(a.position());
                  return p.x() < 20
                      || p.y() < 20
                      || p.x() > getWidth() - 20
                      || p.y() > getHeight() - 20;
                })
            .count();
  }

  public double aircraftScreenLength(Airplane aircraft) {
    return AirplanePainter.length(aircraft.type()) * scale();
  }

  public double zoom() {
    return zoom;
  }

  public Canvas canvas() {
    return canvas;
  }

  public Point screen(Point p) {
    double s = scale();
    return new Point(
        (p.x() - 600) * s + getWidth() / 2 + panX, (p.y() - 390) * s + getHeight() / 2 + panY);
  }

  private Point world(double x, double y) {
    double s = scale();
    return new Point((x - getWidth() / 2 - panX) / s + 600, (y - getHeight() / 2 - panY) / s + 390);
  }

  private double baseScale() {
    return Math.max(.1, Math.min(getWidth() / 1200, getHeight() / 780));
  }

  private double scale() {
    return baseScale() * zoom;
  }

  private Airport airportAt(Point p) {
    return vm.game().airports().stream()
        .filter(a -> a.position().distance(p) < 17 / scale())
        .min(Comparator.comparingDouble(a -> a.position().distance(p)))
        .orElse(null);
  }

  private Airplane planeAt(Point p) {
    return vm.game().airplanes().stream()
        .filter(
            a ->
                vm.game().geometry().aircraftPosition(a).distance(p)
                    < AirplanePainter.length(a.type()) * .55 + 4 / scale())
        .min(Comparator.comparingDouble(a -> vm.game().geometry().aircraftPosition(a).distance(p)))
        .orElse(null);
  }

  private TrackGeometry.Segment segmentAt(Point p) {
    return vm.game().geometry().segments().stream()
        .filter(s -> Point.distanceToSegment(p, s.a(), s.b()) < 8 / scale())
        .min(
            Comparator.comparingDouble(
                s ->
                    Point.distanceToSegment(p, s.a(), s.b())
                        - (s.line().color().ordinal() == vm.selectedColor() ? .25 : 0)))
        .orElse(null);
  }

  private List<Handle> handles() {
    List<Handle> result = new ArrayList<>();
    for (Line line : vm.game().lines())
      if (line.size() >= 2 && !line.isLoop()) {
        var first = vm.game().geometry().segment(line, 0);
        var last = vm.game().geometry().segment(line, line.segmentCount() - 1);
        Point start = first.a().subtract(first.b()).unit(),
            end = last.b().subtract(last.a()).unit();
        addHandle(result, line, true, first.a(), start);
        addHandle(result, line, false, last.b(), end);
      }
    return result;
  }

  private void addHandle(
      List<Handle> handles, Line line, boolean first, Point anchor, Point direction) {
    Point center = anchor.add(direction.multiply(27 / scale()));
    // Stagger neighbouring caps so every parallel airline retains a distinct, clickable grip.
    for (int attempt = 0; attempt < 24; attempt++) {
      Point candidate = center;
      if (handles.stream().noneMatch(h -> h.center.distance(candidate) < 14 / scale())) break;
      center = center.add(direction.multiply(13 / scale()));
    }
    handles.add(new Handle(line.color().ordinal(), first, center, direction, anchor));
  }

  private void press(MouseEvent e) {
    requestFocus();
    cursor = world(e.getX(), e.getY());
    pressPoint = cursor;
    dragMoved = false;
    if (e.getButton() == MouseButton.MIDDLE || e.isShiftDown()) {
      panStart = new Point(e.getX(), e.getY());
      panStartX = panX;
      panStartY = panY;
      return;
    }
    Airport airport = airportAt(cursor);
    if (e.getButton() == MouseButton.SECONDARY) {
      if (airport != null) vm.removeStop(airport);
      else vm.cancelEdit();
      return;
    }
    if (e.getButton() != MouseButton.PRIMARY) return;
    if (vm.tool() == GameViewModel.Tool.HUB) {
      if (airport != null) vm.upgradeAirport(airport);
      return;
    }
    Airplane plane = planeAt(cursor);
    if (vm.tool() == GameViewModel.Tool.WIDEBODY) {
      if (plane != null) vm.upgradeAirplane(plane.id());
      return;
    }
    var segment = segmentAt(cursor);
    if (vm.tool() == GameViewModel.Tool.AIRCRAFT) {
      if (segment != null) vm.addAirplane(segment.line().color().ordinal());
      return;
    }
    Handle handle =
        handles().stream()
            .filter(h -> h.center.distance(cursor) < 11 / scale())
            .min(Comparator.comparingDouble(h -> h.center.distance(cursor)))
            .orElse(null);
    if (handle != null) {
      vm.beginHandle(handle.color, handle.first);
      return;
    }
    if (airport != null) {
      vm.beginAirport(airport);
      return;
    }
    if (plane != null) {
      draggingPlane = plane.id();
      return;
    }
    if (segment != null) {
      vm.beginSegment(segment.line().color().ordinal(), segment.index());
      return;
    }
    panStart = new Point(e.getX(), e.getY());
    panStartX = panX;
    panStartY = panY;
  }

  private void drag(MouseEvent e) {
    if (panStart != null) {
      preset = null;
      panX = panStartX + e.getX() - panStart.x();
      panY = panStartY + e.getY() - panStart.y();
      return;
    }
    cursor = world(e.getX(), e.getY());
    hovered = airportAt(cursor);
    if (pressPoint != null && pressPoint.distance(cursor) * scale() > 4) dragMoved = true;
    if (vm.editor().active()) vm.hoverAirport(hovered);
  }

  private void release(MouseEvent e) {
    cursor = world(e.getX(), e.getY());
    panStart = null;
    if (draggingPlane >= 0) {
      var segment = segmentAt(cursor);
      if (dragMoved && segment != null)
        vm.moveAirplane(draggingPlane, segment.line().color().ordinal());
      draggingPlane = -1;
    }
    if (vm.editor().active()) {
      if (dragMoved) vm.hoverAirport(airportAt(cursor));
      vm.finishEdit();
    }
  }

  private boolean drop(String value, Point p) {
    if (value == null) return false;
    var segment = segmentAt(p);
    var plane = planeAt(p);
    var airport = airportAt(p);
    if (value.startsWith(DRAG_AIRCRAFT) && segment != null) {
      try {
        vm.moveAirplane(
            Integer.parseInt(value.substring(DRAG_AIRCRAFT.length())),
            segment.line().color().ordinal());
        return true;
      } catch (NumberFormatException ignored) {
        return false;
      }
    }
    if (value.equals(DRAG_RESOURCE + "AIRCRAFT") && segment != null) {
      vm.addAirplane(segment.line().color().ordinal());
      return true;
    }
    if (value.equals(DRAG_RESOURCE + "WIDEBODY") && plane != null) {
      vm.upgradeAirplane(plane.id());
      return true;
    }
    if (value.equals(DRAG_RESOURCE + "HUB") && airport != null) {
      vm.upgradeAirport(airport);
      return true;
    }
    return false;
  }

  public void draw() {
    if (canvas.getWidth() < 1 || canvas.getHeight() < 1) return;
    if (cachedMap != vm.game().map()) {
      cachedMap = vm.game().map();
      mapPainter = new MapPainter(cachedMap);
      paintedZoom = Double.NaN;
      resetCamera();
    }
    GraphicsContext g = canvas.getGraphicsContext2D();
    double s = scale();
    if (paintedZoom != zoom
        || paintedPanX != panX
        || paintedPanY != panY
        || paintedWidth != getWidth()
        || paintedHeight != getHeight()) {
      mapPainter.draw(
          backgroundCanvas.getGraphicsContext2D(), getWidth(), getHeight(), this::screen);
      paintedZoom = zoom;
      paintedPanX = panX;
      paintedPanY = panY;
      paintedWidth = getWidth();
      paintedHeight = getHeight();
    }
    g.clearRect(0, 0, getWidth(), getHeight());
    drawInactive(g);
    for (var segment : vm.game().geometry().segments()) {
      if (vm.editor().active() && segment.line().color().ordinal() == vm.editor().color()) continue;
      Point a = screen(segment.a()), b = screen(segment.b());
      g.setStroke(Color.web(segment.line().color().hex));
      g.setLineWidth(4.6 * Math.min(s, 1.3));
      g.setLineCap(StrokeLineCap.ROUND);
      g.strokeLine(a.x(), a.y(), b.x(), b.y());
      if (MapDefinition.distanceKm(
              segment.line().get(segment.index()).spec(),
              segment.line().segmentEnd(segment.index()).spec())
          > cachedMap.longHaulKm()) {
        Point m = a.lerp(b, .5);
        g.setFill(Color.web(segment.line().color().hex));
        g.fillOval(m.x() - 4, m.y() - 4, 8, 8);
        g.setFill(PAPER);
        g.fillOval(m.x() - 1.5, m.y() - 1.5, 3, 3);
      }
    }
    drawDraft(g);
    // Stops cover route joins; two separated legs can meet at the same airport without a branch.
    for (Airport a : vm.game().airports()) drawAirport(g, a);
    for (Handle h : handles()) {
      Point p = screen(h.center),
          n = h.direction.normal().multiply(5 * Math.min(s, 1.3)),
          base = screen(h.anchor).add(h.direction.multiply(14));
      g.setStroke(Color.web(LineColor.values()[h.color].hex));
      g.setLineWidth(4 * Math.min(s, 1.3));
      g.setLineCap(StrokeLineCap.ROUND);
      g.strokeLine(base.x(), base.y(), p.x(), p.y());
      g.strokeLine(p.x() - n.x(), p.y() - n.y(), p.x() + n.x(), p.y() + n.y());
    }
    for (Airplane a : vm.game().airplanes()) drawPlane(g, a);
    if (draggingPlane >= 0 && dragMoved) {
      Point p = screen(cursor);
      g.setGlobalAlpha(.65);
      vm.game().airplanes().stream()
          .filter(a -> a.id() == draggingPlane)
          .findFirst()
          .ifPresent(
              a ->
                  AirplanePainter.draw(
                      g,
                      a.type(),
                      a.line().color(),
                      p.x(),
                      p.y(),
                      vm.game().geometry().aircraftAngle(a),
                      s,
                      a.passengers()));
      g.setGlobalAlpha(1);
    }
    g.setFill(Color.web("#607C7E"));
    g.setFont(Font.font("SansSerif", 10));
    g.setTextBaseline(VPos.CENTER);
    g.setTextAlign(TextAlignment.LEFT);
    g.fillText(
        "NATURAL EARTH  /  " + cachedMap.airports().size() + " AIRPORTS", 22, getHeight() - 21);
    g.setTextAlign(TextAlignment.RIGHT);
    g.fillText("SCROLL TO ZOOM   ·   DRAG WATER TO PAN", getWidth() - 22, getHeight() - 21);
    if (hovered != null) drawTooltip(g, hovered);
  }

  private void drawInactive(GraphicsContext g) {
    Set<String> active = new HashSet<>();
    vm.game().airports().forEach(a -> active.add(a.code()));
    for (AirportSpec spec : cachedMap.airports())
      if (!active.contains(spec.code())) {
        Point p = screen(cachedMap.project(spec.latitude(), spec.longitude()));
        g.setFill(Color.web("#AEC0B7", .65));
        g.fillOval(p.x() - 2, p.y() - 2, 4, 4);
        if (zoom > 1.8) {
          g.setFont(Font.font("SansSerif", 9));
          g.setTextAlign(TextAlignment.CENTER);
          g.setTextBaseline(VPos.CENTER);
          g.fillText(spec.code(), p.x(), p.y() - 9);
        }
      }
  }

  private void drawAirport(GraphicsContext g, Airport a) {
    Point p = screen(a.position());
    double r = 11;
    if (a == hovered) {
      g.setFill(Color.web("#FFFFFF", .5));
      g.fillOval(p.x() - 24, p.y() - 24, 48, 48);
    }
    if (a.isHub()) {
      g.setStroke(Color.web("#789B8D"));
      g.setLineWidth(3);
      g.strokeOval(p.x() - 17, p.y() - 17, 34, 34);
    }
    if (a.crowding() > 0) {
      g.setStroke(Color.web("#D25949"));
      g.setLineWidth(3);
      g.strokeArc(
          p.x() - 20, p.y() - 20, 40, 40, 90, -360 * Math.min(1, a.crowding()), ArcType.OPEN);
    }
    ShapePainter.draw(g, a.shape(), p.x(), p.y(), r, PAPER, INK, 2.4);
    g.setFont(Font.font("SansSerif", FontWeight.BOLD, 10));
    g.setTextAlign(TextAlignment.CENTER);
    g.setTextBaseline(VPos.CENTER);
    g.setFill(PAPER);
    g.fillRoundRect(p.x() - 18, p.y() - 33, 36, 16, 5, 5);
    g.setFill(INK);
    g.fillText(a.code(), p.x(), p.y() - 25);
    List<Passenger> waiting = a.waiting();
    int count = Math.min(6, waiting.size());
    for (int i = 0; i < count; i++)
      ShapePainter.draw(
          g,
          waiting.get(i).destination(),
          p.x() - ((count - 1) * 8) / 2.0 + i * 8,
          p.y() + 23,
          2.8,
          INK,
          INK,
          .6);
    if (waiting.size() > 6) {
      g.setFont(Font.font("SansSerif", FontWeight.BOLD, 9));
      g.setFill(INK);
      g.fillText("+" + (waiting.size() - 6), p.x(), p.y() + 36);
    }
  }

  private void drawPlane(GraphicsContext g, Airplane a) {
    Point p = screen(vm.game().geometry().aircraftPosition(a));
    AirplanePainter.draw(
        g,
        a.type(),
        a.line().color(),
        p.x(),
        p.y(),
        vm.game().geometry().aircraftAngle(a),
        scale(),
        a.passengers());
  }

  private void drawDraft(GraphicsContext g) {
    var editor = vm.editor();
    if (!editor.active()) return;
    List<Airport> path = editor.path();
    g.setStroke(Color.web(LineColor.values()[editor.color()].hex, .8));
    g.setLineWidth(4);
    g.setLineDashes(8, 7);
    for (int i = 1; i < path.size(); i++)
      stroke(g, screen(path.get(i - 1).position()), screen(path.get(i).position()));
    if (editor.loop() && path.size() > 2)
      stroke(g, screen(path.getLast().position()), screen(path.getFirst().position()));
    if (!path.isEmpty() && !editor.loop()) {
      Point anchor =
          editor.mode() == LineEditor.Mode.START
              ? path.getFirst().position()
              : editor.mode() == LineEditor.Mode.END
                  ? path.getLast().position()
                  : path.get(Math.min(editor.insertion(), path.size() - 1)).position();
      stroke(g, screen(anchor), screen(cursor));
    }
    g.setLineDashes();
  }

  private void drawTooltip(GraphicsContext g, Airport a) {
    Point p = screen(a.position());
    double x = Math.clamp(p.x() + 24, 8, Math.max(8, getWidth() - 228)),
        y = Math.clamp(p.y() + 16, 8, Math.max(8, getHeight() - 82));
    g.setFill(Color.web("#173E46", .96));
    g.fillRoundRect(x, y, 212, 64, 12, 12);
    g.setFill(Color.WHITE);
    g.setTextAlign(TextAlignment.LEFT);
    g.setTextBaseline(VPos.CENTER);
    g.setFont(Font.font("SansSerif", FontWeight.BOLD, 12));
    g.fillText(a.code() + "  " + a.spec().city(), x + 13, y + 20, 185);
    g.setFill(Color.web("#C5D8D3"));
    g.setFont(Font.font("SansSerif", 11));
    g.fillText(
        a.waitingCount() + " / " + a.capacity() + " waiting" + (a.isHub() ? "  ·  Hub" : ""),
        x + 13,
        y + 43);
  }

  private static void stroke(GraphicsContext g, Point a, Point b) {
    g.strokeLine(a.x(), a.y(), b.x(), b.y());
  }
}
