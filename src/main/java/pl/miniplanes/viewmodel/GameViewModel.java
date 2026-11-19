package pl.miniplanes.viewmodel;

import java.util.List;
import javafx.beans.property.*;
import pl.miniplanes.model.*;

/** Observable presentation state and commands. No reference to a view, Canvas or Stage. */
public final class GameViewModel {
  public enum Tool {
    ROUTE,
    AIRCRAFT,
    WIDEBODY,
    HUB
  }

  private GameData game;
  private Updater updater;
  private final LineEditor editor = new LineEditor();
  private final BooleanProperty paused = new SimpleBooleanProperty(true);
  private final IntegerProperty speed = new SimpleIntegerProperty(1);
  private final IntegerProperty selectedColor = new SimpleIntegerProperty(0);
  private final ObjectProperty<Tool> tool = new SimpleObjectProperty<>(Tool.ROUTE);
  private final StringProperty status = new SimpleStringProperty("");
  private final LongProperty delivered = new SimpleLongProperty();
  private final IntegerProperty revision = new SimpleIntegerProperty();
  private double accumulator;
  private boolean pauseBeforeEdit;

  public GameViewModel(MapDefinition map, long seed) {
    start(map, seed);
  }

  public void start(MapDefinition map, long seed) {
    game = new GameData(map, seed);
    updater = new Updater(game);
    editor.cancel();
    accumulator = 0;
    paused.set(true);
    speed.set(1);
    selectedColor.set(0);
    tool.set(Tool.ROUTE);
    status.set("Choose a colour, then drag from one airport to another. Press Space to fly.");
    touch();
  }

  public GameData game() {
    return game;
  }

  public LineEditor editor() {
    return editor;
  }

  public ReadOnlyBooleanProperty pausedProperty() {
    return paused;
  }

  public ReadOnlyIntegerProperty speedProperty() {
    return speed;
  }

  public ReadOnlyIntegerProperty selectedColorProperty() {
    return selectedColor;
  }

  public ReadOnlyObjectProperty<Tool> toolProperty() {
    return tool;
  }

  public ReadOnlyStringProperty statusProperty() {
    return status;
  }

  public ReadOnlyLongProperty deliveredProperty() {
    return delivered;
  }

  public ReadOnlyIntegerProperty revisionProperty() {
    return revision;
  }

  public boolean isPaused() {
    return paused.get();
  }

  public int speed() {
    return speed.get();
  }

  public int selectedColor() {
    return selectedColor.get();
  }

  public Tool tool() {
    return tool.get();
  }

  public void togglePause() {
    if (!game.gameOver() && !game.rewardPending() && !editor.active()) {
      paused.set(!paused.get());
      accumulator = 0;
    }
  }

  public void pause() {
    paused.set(true);
    accumulator = 0;
  }

  public void setSpeed(int value) {
    if (value == 1 || value == 2 || value == 3) speed.set(value);
  }

  public void selectColor(int color) {
    if (color >= 0 && color < game.unlockedLines()) {
      cancelEdit();
      selectedColor.set(color);
      tool.set(Tool.ROUTE);
      touch();
    }
  }

  public void selectTool(Tool value) {
    cancelEdit();
    tool.set(value);
    touch();
  }

  public void beginHandle(int color, boolean first) {
    if (game.gameOver()) return;
    selectColor(color);
    Line line = game.line(color);
    if (line.size() < 2 || line.isLoop()) return;
    holdForEdit();
    editor.begin(line, first ? LineEditor.Mode.START : LineEditor.Mode.END, 0, null);
  }

  public void beginAirport(Airport airport) {
    if (game.gameOver()) return;
    if (tool.get() == Tool.HUB) {
      report(game.upgradeAirport(airport), "Hub terminal built. Capacity: 28.");
      return;
    }
    Line line = game.line(selectedColor.get());
    if (tool.get() != Tool.ROUTE) return;
    if (line.size() == 0) {
      holdForEdit();
      editor.start(line, airport);
    } else if (!line.isLoop() && (line.get(0) == airport || line.get(line.size() - 1) == airport)) {
      holdForEdit();
      editor.begin(
          line, line.get(0) == airport ? LineEditor.Mode.START : LineEditor.Mode.END, 0, airport);
    } else
      status.set("Drag a route segment through an airport, or right-click a stop to remove it.");
  }

  public void beginSegment(int color, int segment) {
    if (game.gameOver()) return;
    selectColor(color);
    holdForEdit();
    editor.begin(game.line(color), LineEditor.Mode.INSERT, segment, null);
  }

  public void hoverAirport(Airport airport) {
    editor.hover(airport);
  }

  public void finishEdit() {
    if (!editor.active()) return;
    String error = editor.commit(game);
    paused.set(pauseBeforeEdit);
    accumulator = 0;
    report(error, "Route updated. Drag an end handle to extend or trim it.");
  }

  public void cancelEdit() {
    if (editor.active()) {
      editor.cancel();
      paused.set(pauseBeforeEdit);
      accumulator = 0;
    }
  }

  public void removeStop(Airport airport) {
    Line line = game.line(selectedColor.get());
    var path = new java.util.ArrayList<>(line.path());
    if (!path.remove(airport)) return;
    report(
        game.editRoute(selectedColor.get(), path, line.isLoop() && path.size() >= 3),
        "Stop removed from the selected airline.");
  }

  public void clearLine() {
    cancelEdit();
    report(
        game.editRoute(selectedColor.get(), List.of(), false),
        "Airline removed. Aircraft and permits returned.");
  }

  public void openLoop() {
    Line line = game.line(selectedColor.get());
    report(
        game.editRoute(selectedColor.get(), line.path(), false),
        "Loop opened between its last and first airport.");
  }

  public void addAirplane(int color) {
    report(game.addAirplane(color), "Aircraft added at the first airport of the airline.");
  }

  public void moveAirplane(int id, int color) {
    report(game.moveAirplane(id, color), "Aircraft reassigned to the start of the airline.");
  }

  public void upgradeAirplane(int id) {
    report(game.upgradeAirplane(id), "Widebody ready. Capacity: 12.");
  }

  public void upgradeAirport(Airport airport) {
    report(game.upgradeAirport(airport), "Hub terminal built. Capacity: 28.");
  }

  public void chooseUpgrade(Upgrade upgrade) {
    report(game.chooseUpgrade(upgrade), game.lastEvent());
  }

  public void tick(double realSeconds) {
    if (paused.get() || editor.active() || game.rewardPending() || game.gameOver()) {
      accumulator = 0;
      return;
    }
    accumulator += Math.clamp(realSeconds, 0, .25) * speed.get();
    while (accumulator + 1e-9 >= Updater.STEP) {
      updater.step(Updater.STEP);
      accumulator -= Updater.STEP;
      if (game.rewardPending() || game.gameOver()) {
        accumulator = 0;
        break;
      }
    }
    delivered.set(game.delivered());
  }

  private void holdForEdit() {
    pauseBeforeEdit = paused.get();
    paused.set(true);
    accumulator = 0;
  }

  private void report(String error, String success) {
    status.set(error == null ? success : error);
    touch();
  }

  private void touch() {
    delivered.set(game.delivered());
    revision.set(revision.get() + 1);
  }
}
