package pl.miniplanes.viewmodel;

import java.util.*;
import pl.miniplanes.model.*;

/** A cancellable route-edit transaction. Pointer coordinates and JavaFX events stay in the view. */
public final class LineEditor {
  public enum Mode {
    START,
    END,
    INSERT
  }

  private int color, insertion;
  private Mode mode;
  private List<Airport> draft;
  private boolean loop;
  private Airport previousHover;

  public boolean active() {
    return draft != null;
  }

  public int color() {
    return color;
  }

  public List<Airport> path() {
    return active() ? List.copyOf(draft) : List.of();
  }

  public boolean loop() {
    return loop;
  }

  public Mode mode() {
    return mode;
  }

  public int insertion() {
    return insertion;
  }

  public void begin(Line line, Mode mode, int insertion, Airport initialHover) {
    color = line.color().ordinal();
    draft = new ArrayList<>(line.path());
    loop = line.isLoop();
    this.mode = mode;
    this.insertion = insertion;
    previousHover = initialHover;
    // Dragging a loop segment retains the loop; endpoints are only available on open routes.
  }

  public void start(Line line, Airport airport) {
    begin(line, Mode.END, 0, airport);
    draft.clear();
    draft.add(airport);
    loop = false;
  }

  public void hover(Airport airport) {
    if (!active() || airport == previousHover) return;
    previousHover = airport;
    if (airport == null || (loop && mode != Mode.INSERT)) return;
    int index = draft.indexOf(airport);
    if (mode == Mode.INSERT) {
      Airport anchor = draft.get(insertion);
      Airport next = draft.get((insertion + 1) % draft.size());
      if (airport == anchor || airport == next) return;
      draft.remove(airport); // An existing stop is moved, not duplicated into a branch.
      insertion = draft.indexOf(anchor) + 1;
      draft.add(insertion, airport);
    } else if (index >= 0) {
      boolean opposite = mode == Mode.END ? index == 0 : index == draft.size() - 1;
      if (opposite && draft.size() >= 3) loop = true;
      else if (mode == Mode.END) draft.subList(index, draft.size()).clear();
      else draft.subList(0, index + 1).clear();
    } else if (mode == Mode.START) draft.addFirst(airport);
    else draft.add(airport);
  }

  public String commit(GameData game) {
    if (!active()) return null;
    String error = game.editRoute(color, draft, loop);
    cancel();
    return error;
  }

  public void cancel() {
    draft = null;
    previousHover = null;
  }
}
