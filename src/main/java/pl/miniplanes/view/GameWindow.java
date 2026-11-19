package pl.miniplanes.view;

import java.util.*;
import javafx.animation.AnimationTimer;
import javafx.beans.binding.Bindings;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import pl.miniplanes.model.*;
import pl.miniplanes.viewmodel.GameViewModel;

/** Composes JavaFX controls, observes the ViewModel, and forwards user intentions. */
public final class GameWindow {
  private final Stage stage;
  private final List<MapDefinition> maps;
  private final GameViewModel vm;
  private final MapCanvas map;
  private final StackPane root = new StackPane();
  private final BorderPane shell = new BorderPane();
  private final StackPane overlay = new StackPane();
  private final HBox regionControls = new HBox(5);
  private final Label outsideView = new Label();
  private final VBox airlines = new VBox(6), fleet = new VBox(7);
  private final Label delivered = new Label(),
      week = new Label(),
      clock = new Label(),
      waiting = new Label(),
      event = new Label(),
      routeSummary = new Label();
  private final Button pause, speed, planes, capacity, hubs, permits;
  private final Label selectedTitle = new Label();
  private String fleetSignature = "", airlineSignature = "";
  private boolean menu = true, help, rewardVisible, endVisible;
  private int shownWeek, shownPlanes = -1, shownCapacity = -1, shownHubs = -1, shownPermits = -1;
  private AnimationTimer timer;

  public GameWindow(Stage stage, List<MapDefinition> maps) {
    this.stage = stage;
    this.maps = List.copyOf(maps);
    vm = new GameViewModel(maps.getFirst(), System.nanoTime());
    map = new MapCanvas(vm);
    pause = button("Resume", () -> vm.togglePause());
    pause.setId("pause");
    pause
        .textProperty()
        .bind(Bindings.when(vm.pausedProperty()).then("▶  Resume").otherwise("Ⅱ  Pause"));
    speed = button("1×", () -> vm.setSpeed(vm.speed() % 3 + 1));
    speed.textProperty().bind(vm.speedProperty().asString("%d×"));
    speed.setId("speed");
    planes = resourceButton("Aircraft", GameViewModel.Tool.AIRCRAFT);
    capacity = resourceButton("Widebody", GameViewModel.Tool.WIDEBODY);
    hubs = resourceButton("Hub", GameViewModel.Tool.HUB);
    permits = button("Long-haul", () -> showHelp());
    permits.getStyleClass().add("resource");
    permits.setTooltip(
        new Tooltip(
            "Each leg longer than "
                + (int) vm.game().map().longHaulKm()
                + " km uses one permit. Deleting that leg returns it."));
    buildShell();
    root.getChildren().addAll(shell, overlay);
    overlay.setPickOnBounds(false);
    Scene scene = new Scene(root, 1440, 920);
    scene
        .getStylesheets()
        .add(
            Objects.requireNonNull(getClass().getResource("/pl/miniplanes/style.css"))
                .toExternalForm());
    scene.setOnKeyPressed(this::key);
    stage.setTitle("Mini Planes — build a world of connections");
    stage.setMinWidth(1100);
    stage.setMinHeight(740);
    stage.setScene(scene);
    vm.revisionProperty().addListener((o, a, b) -> refresh());
    vm.selectedColorProperty().addListener((o, a, b) -> refresh());
    vm.toolProperty().addListener((o, a, b) -> refresh());
    refresh();
    showMenu();
    stage.setOnCloseRequest(e -> stop());
  }

  public GameViewModel viewModel() {
    return vm;
  }

  public MapCanvas mapCanvas() {
    return map;
  }

  public Scene scene() {
    return stage.getScene();
  }

  public void show() {
    stage.show();
    timer =
        new AnimationTimer() {
          long last;

          @Override
          public void handle(long now) {
            double dt = last == 0 ? 0 : (now - last) / 1e9;
            last = now;
            if (!menu && !help) vm.tick(dt);
            refresh();
            map.draw();
          }
        };
    timer.start();
  }

  public void stop() {
    if (timer != null) timer.stop();
  }

  private void buildShell() {
    VBox sidebar = new VBox(12);
    sidebar.setPrefWidth(260);
    sidebar.setMinWidth(260);
    sidebar.setPadding(new Insets(22, 22, 22, 24));
    sidebar.getStyleClass().add("sidebar");
    Label brand = label("mini planes", "brand");
    Label tag = label("MAKE THE WORLD CONNECT.", "eyebrow");
    HBox title =
        new HBox(
            8, label("YOUR AIRLINES", "section-label"), spacer(), label("01—08", "muted-small"));
    Label instructions =
        label("Pick a colour. Connect airports.\nKeep the world moving.", "sidebar-copy");
    selectedTitle.getStyleClass().add("selected-title");
    routeSummary.getStyleClass().add("sidebar-copy");
    routeSummary.setWrapText(true);
    routeSummary.setMinHeight(Region.USE_PREF_SIZE);
    routeSummary.setMaxWidth(208);
    Button add = button("+  Add aircraft to airline", () -> vm.addAirplane(vm.selectedColor()));
    add.setId("add-aircraft");
    add.getStyleClass().add("sidebar-button");
    add.setMaxWidth(Double.MAX_VALUE);
    HBox edits = new HBox(6);
    Button open = button("Open loop", vm::openLoop), clear = button("Clear route", vm::clearLine);
    open.getStyleClass().add("subtle-button");
    clear.getStyleClass().add("subtle-button");
    edits.getChildren().addAll(open, clear);
    ScrollPane roster = new ScrollPane(fleet);
    roster.setFitToWidth(true);
    roster.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    roster.getStyleClass().add("fleet-scroll");
    roster.setPrefViewportHeight(120);
    roster.setMinHeight(60);
    VBox.setVgrow(roster, Priority.ALWAYS);
    Label hint =
        label("Drag aircraft cards onto another\nairline to reassign your fleet.", "muted-small");
    Button guide = button("How to play   ↗", this::showHelp);
    guide.setId("help");
    guide.getStyleClass().add("sidebar-button");
    guide.setMaxWidth(Double.MAX_VALUE);
    sidebar
        .getChildren()
        .addAll(
            new VBox(5, brand, tag),
            instructions,
            title,
            airlines,
            new Separator(),
            selectedTitle,
            routeSummary,
            add,
            edits,
            label("FLEET", "section-label"),
            roster,
            hint,
            guide);
    ScrollPane sidebarScroll = new ScrollPane(sidebar);
    sidebarScroll.setFitToWidth(true);
    sidebarScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    sidebarScroll.setPrefWidth(260);
    sidebarScroll.setMinWidth(260);
    sidebarScroll.setMaxWidth(260);
    sidebarScroll.getStyleClass().add("sidebar-scroll");
    shell.setLeft(sidebarScroll);
    shell.setMinSize(0, 0);

    VBox main = new VBox();
    main.setMinSize(0, 0);
    HBox header = new HBox(12);
    header.setAlignment(Pos.CENTER_LEFT);
    header.setPadding(new Insets(19, 25, 17, 25));
    header.getStyleClass().add("header");
    Label location = label("", "location");
    location.setId("location");
    delivered.textProperty().bind(vm.deliveredProperty().asString("%,d"));
    delivered.getStyleClass().add("metric");
    week.getStyleClass().add("metric-small");
    clock.getStyleClass().add("muted");
    waiting.getStyleClass().add("metric-small");
    Button menuButton =
        button(
            "Maps",
            () -> {
              vm.pause();
              showMenu();
            });
    menuButton.setId("maps");
    header
        .getChildren()
        .addAll(
            new VBox(3, label("FLIGHT NETWORK", "eyebrow-dark"), location),
            spacer(),
            new VBox(1, delivered, label("PASSENGERS DELIVERED", "eyebrow-dark")),
            new VBox(4, week, clock),
            pause,
            speed,
            menuButton);
    StackPane mapArea = new StackPane(map);
    regionControls.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
    regionControls.getStyleClass().add("region-controls");
    for (MapCanvas.RegionPreset region : MapCanvas.RegionPreset.values()) {
      String regionTitle =
          switch (region) {
            case OVERVIEW -> "All Americas";
            case NORTH -> "North";
            case SOUTH -> "South";
          };
      Button b =
          button(
              regionTitle,
              () -> {
                map.focus(region);
                map.draw();
                refresh();
              });
      b.setUserData(region);
      b.setId("region-" + region.name().toLowerCase(Locale.ROOT));
      regionControls.getChildren().add(b);
    }
    outsideView.getStyleClass().add("outside-view");
    regionControls.getChildren().add(outsideView);
    regionControls.setAlignment(Pos.CENTER_LEFT);
    StackPane.setAlignment(regionControls, Pos.TOP_LEFT);
    StackPane.setMargin(regionControls, new Insets(14));
    mapArea.getChildren().add(regionControls);
    VBox.setVgrow(mapArea, Priority.ALWAYS);
    FlowPane resourceRow = new FlowPane(8, 8);
    resourceRow.setAlignment(Pos.CENTER_LEFT);
    resourceRow.setPadding(new Insets(14, 22, 12, 22));
    resourceRow.getStyleClass().add("resource-row");
    VBox resourcesTitle =
        new VBox(4, label("OPERATIONS", "eyebrow-dark"), label("Select or drag", "muted"));
    resourcesTitle.setMinWidth(105);
    Button route = button("Draw routes", () -> vm.selectTool(GameViewModel.Tool.ROUTE));
    route.getStyleClass().add("resource");
    route.setId("draw-routes");
    resourceRow
        .getChildren()
        .addAll(resourcesTitle, route, planes, capacity, hubs, permits, waiting);
    Label status = new Label();
    status.textProperty().bind(vm.statusProperty());
    status.setWrapText(true);
    status.getStyleClass().add("status");
    status.setMaxWidth(Double.MAX_VALUE);
    status.setMinHeight(43);
    status.setPadding(new Insets(5, 24, 10, 24));
    event.getStyleClass().add("event");
    event.setPadding(new Insets(8, 24, 8, 24));
    event.setMaxWidth(Double.MAX_VALUE);
    main.getChildren().addAll(header, mapArea, event, resourceRow, status);
    shell.setCenter(main);
  }

  private Button resourceButton(String title, GameViewModel.Tool tool) {
    Button b = button(title, () -> vm.selectTool(tool));
    b.setId("resource-" + tool.name().toLowerCase(Locale.ROOT));
    b.getStyleClass().add("resource");
    b.setOnDragDetected(
        e -> {
          Dragboard db = b.startDragAndDrop(TransferMode.COPY);
          ClipboardContent c = new ClipboardContent();
          c.putString(MapCanvas.DRAG_RESOURCE + tool.name());
          db.setContent(c);
          e.consume();
        });
    b.setTooltip(
        new Tooltip(
            switch (tool) {
              case AIRCRAFT ->
                  "Select then click a route, or drag onto it. Starts at its first airport.";
              case WIDEBODY -> "Select then click an aircraft, or drag onto it. Capacity: 6 → 12.";
              case HUB -> "Select then click an airport, or drag onto it. Capacity: 14 → 28.";
              default -> "Draw routes";
            }));
    return b;
  }

  private void refresh() {
    GameData game = vm.game();
    regionControls.setVisible(game.map().id().equals("americas"));
    regionControls.setManaged(regionControls.isVisible());
    int outside = map.airportsOutsideView();
    outsideView.setText(outside == 0 ? "" : outside + " airports outside view");
    for (var node : regionControls.getChildren()) {
      if (node instanceof Button button)
        button.pseudoClassStateChanged(
            javafx.css.PseudoClass.getPseudoClass("selected"),
            button.getUserData() == map.preset());
    }
    ((Label) scene().lookup("#location")).setText(game.map().title());
    week.setText("Week " + game.week());
    String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    int day = (int) (game.elapsed() / 12) % 7;
    clock.setText(days[day] + (vm.isPaused() ? " · paused" : " · " + vm.speed() + "×"));
    waiting.setText(game.waitingCount() + " waiting");
    event.setText(game.lastEvent());
    selectedTitle.setText(game.line(vm.selectedColor()).color().label + " airline");
    Line line = game.line(vm.selectedColor());
    routeSummary.setText(
        line.size() == 0
            ? "No route yet. Drag between two open airports to launch this airline."
            : line.size()
                + " stops  ·  "
                + (line.isLoop() ? "continuous loop" : "out-and-back")
                + "\n"
                + game.permitCost(line)
                + " long-haul permits in use");
    String sig =
        game.unlockedLines()
            + ":"
            + vm.selectedColor()
            + ":"
            + game.lines().stream().map(l -> l.size() + "/" + l.isLoop()).toList();
    if (!sig.equals(airlineSignature)) {
      airlineSignature = sig;
      airlines.getChildren().clear();
      for (int i = 0; i < Math.min(game.lines().size(), game.unlockedLines() + 1); i++) {
        final int index = i;
        Line l = game.line(i);
        Button b =
            button(
                String.format(
                    "%02d     %-7s     %s",
                    i + 1,
                    l.color().label,
                    i >= game.unlockedLines()
                        ? "locked"
                        : l.size() == 0 ? "+ new" : l.size() + " stops"),
                () -> vm.selectColor(index));
        b.setId("airline-" + i);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.getStyleClass().add("airline");
        b.setDisable(i >= game.unlockedLines());
        b.setStyle(
            "-fx-border-color: "
                + l.color().hex
                + "; -fx-background-color: "
                + (i == vm.selectedColor() ? "#345760" : "#21444d")
                + ";");
        airlines.getChildren().add(b);
      }
    }
    String f =
        game.airplanes().stream()
            .map(
                a -> a.id() + ":" + a.line().color() + ":" + a.type() + ":" + a.passengers().size())
            .toList()
            .toString();
    if (!f.equals(fleetSignature)) {
      fleetSignature = f;
      fleet.getChildren().clear();
      if (game.airplanes().isEmpty())
        fleet
            .getChildren()
            .add(label("Your first aircraft launches\nwith your first route.", "sidebar-copy"));
      for (Airplane a : game.airplanes()) {
        Button card =
            button(
                "MP "
                    + String.format("%03d", a.id())
                    + "    "
                    + a.line().color().label
                    + "    "
                    + a.passengers().size()
                    + "/"
                    + a.capacity(),
                () -> vm.upgradeAirplane(a.id()));
        card.getStyleClass().add("fleet-card");
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-border-color: " + a.line().color().hex + ";");
        card.setTooltip(
            new Tooltip("Click to use a widebody upgrade. Drag onto a route to reassign."));
        card.setOnDragDetected(
            e -> {
              Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
              ClipboardContent c = new ClipboardContent();
              c.putString(MapCanvas.DRAG_AIRCRAFT + a.id());
              db.setContent(c);
              e.consume();
            });
        fleet.getChildren().add(card);
      }
    }
    if (shownPlanes != game.sparePlanes()) {
      shownPlanes = game.sparePlanes();
      planes.setText("Aircraft  ·  " + shownPlanes);
    }
    if (shownCapacity != game.widebodies()) {
      shownCapacity = game.widebodies();
      capacity.setText("Widebody  ·  " + shownCapacity);
    }
    if (shownHubs != game.hubs()) {
      shownHubs = game.hubs();
      hubs.setText("Hub  ·  " + shownHubs);
    }
    if (shownPermits != game.permits()) {
      shownPermits = game.permits();
      permits.setText("Long-haul  ·  " + shownPermits);
    }
    planes.pseudoClassStateChanged(
        javafx.css.PseudoClass.getPseudoClass("selected"),
        vm.tool() == GameViewModel.Tool.AIRCRAFT);
    capacity.pseudoClassStateChanged(
        javafx.css.PseudoClass.getPseudoClass("selected"),
        vm.tool() == GameViewModel.Tool.WIDEBODY);
    hubs.pseudoClassStateChanged(
        javafx.css.PseudoClass.getPseudoClass("selected"), vm.tool() == GameViewModel.Tool.HUB);
    if (!menu && !help && game.rewardPending() && (!rewardVisible || shownWeek != game.week()))
      showReward();
    if (!menu && !help && game.gameOver() && !endVisible) showEnd();
  }

  public void startMap(int index, long seed) {
    menu = false;
    help = false;
    rewardVisible = false;
    endVisible = false;
    overlay.getChildren().clear();
    overlay.setPickOnBounds(false);
    vm.start(maps.get(index), seed);
    permits.setTooltip(
        new Tooltip(
            "Each leg longer than "
                + (int) vm.game().map().longHaulKm()
                + " km uses one permit. Deleting that leg returns it."));
    map.resetCamera();
    airlineSignature = "";
    fleetSignature = "";
    refresh();
    map.requestFocus();
  }

  private void showMenu() {
    menu = true;
    help = false;
    overlay.setPickOnBounds(true);
    VBox card = new VBox(20);
    card.setMaxWidth(760);
    card.setMaxHeight(Region.USE_PREF_SIZE);
    card.setPadding(new Insets(40));
    card.getStyleClass().add("modal");
    Label kicker = label("A LITTLE NETWORK. A WORLD OF POSSIBILITIES.", "eyebrow-dark");
    Label title = label("mini planes", "hero-title");
    Label desc =
        label(
            "Build an airline network that brings people together.\n"
                + "Connect airports, grow your fleet and keep every terminal moving.",
            "intro");
    HBox choices = new HBox(16);
    for (int i = 0; i < maps.size(); i++) {
      final int index = i;
      MapDefinition def = maps.get(i);
      VBox choice = new VBox(10);
      choice.getStyleClass().add("map-choice");
      choice.setPadding(new Insets(22));
      HBox.setHgrow(choice, Priority.ALWAYS);
      choice.setPrefWidth(330);
      Label number = label("0" + (i + 1) + "  /  35 AIRPORTS", "eyebrow-dark"),
          name = label(def.title(), "map-title"),
          sub = label(def.subtitle(), "muted");
      sub.setWrapText(true);
      Label geography =
          label(
              i == 0 ? "London → Kraków → Istanbul" : "Vancouver → Bogotá → Buenos Aires",
              "map-caption");
      geography.setWrapText(true);
      Button play =
          button("Start in " + def.title() + "   →", () -> startMap(index, System.nanoTime()));
      play.setId("start-" + def.id());
      play.getStyleClass().add("primary");
      play.setMaxWidth(Double.MAX_VALUE);
      choice.getChildren().addAll(number, name, sub, geography, play);
      choices.getChildren().add(choice);
    }
    Label note =
        label("Start paused with three airports. More open as your network grows.", "muted");
    HBox footer = new HBox(12);
    footer.setAlignment(Pos.CENTER_LEFT);
    Button how = button("How to play", this::showHelp);
    footer
        .getChildren()
        .addAll(how, spacer(), label("JAVA 21  /  JAVAFX  /  MVVM", "eyebrow-dark"));
    card.getChildren().addAll(kicker, title, desc, choices, note, footer);
    showOverlay(card);
  }

  private void showHelp() {
    if (help) return;
    help = true;
    VBox card = modal("Your network, your rules", "A quick guide to Mini Planes");
    GridPane grid = new GridPane();
    grid.setHgap(25);
    grid.setVgap(14);
    String[][] rows = {
      {
        "01  Connect",
        "Pick an airline colour. Drag between airports to start a route. A plane launches"
            + " automatically."
      },
      {
        "02  Edit",
        "Drag a T handle to extend either end. Drag it over a terminal to remove that terminal."
            + " Drag a segment through a stop to insert or reorder it."
      },
      {
        "03  Loop",
        "Connect a route's ends to close a loop. Use Open loop to break it. Right-click a stop to"
            + " remove it; Clear route refunds its resources."
      },
      {
        "04  Carry",
        "Passengers want the matching shape. Aircraft serve routes automatically, including"
            + " transfers. Add aircraft at the first stop; drag fleet cards to move them."
      },
      {
        "05  Grow",
        "Widebodies: 6 → 12 seats. Hubs: 14 → 28 waiting. Each long leg uses a permit (Europe >"
            + " 2,200 km; Americas > 4,000 km). Each week: one aircraft and one upgrade."
      },
      {
        "06  Survive",
        "An airport over capacity for 35 seconds ends the season. Red rings show the countdown;"
            + " reducing the queue lets it recover. Your score counts delivered passengers."
      },
      {
        "Keys",
        "Space pause · 1 / 2 / 3 speed · Q / E airline · Esc cancel / close · Delete clear route ·"
            + " H help · R reset camera"
      },
      {
        "Camera",
        "Scroll to zoom. Drag empty space to pan, or Shift-drag / middle-drag anywhere. Hover an"
            + " airport for its name and queue."
      }
    };
    for (int i = 0; i < rows.length; i++) {
      Label a = label(rows[i][0], "guide-label"), b = label(rows[i][1], "guide-copy");
      b.setWrapText(true);
      b.setMaxWidth(510);
      grid.add(a, 0, i);
      grid.add(b, 1, i);
    }
    Button back =
        button(
            "Got it",
            () -> {
              help = false;
              if (menu) showMenu();
              else {
                overlay.getChildren().clear();
                overlay.setPickOnBounds(false);
                rewardVisible = false;
                endVisible = false;
                refresh();
                map.requestFocus();
              }
            });
    back.setId("close-help");
    back.getStyleClass().add("primary");
    card.getChildren().addAll(grid, back);
    showOverlay(card);
  }

  private void showReward() {
    rewardVisible = true;
    shownWeek = vm.game().week();
    VBox card =
        modal(
            "A new week takes flight.",
            "Week " + shownWeek + " · One aircraft has joined your reserve. Choose one upgrade.");
    for (Upgrade u : vm.game().upgradeChoices()) {
      Button b =
          button(
              u.title + "   —   " + u.description,
              () -> {
                vm.chooseUpgrade(u);
                rewardVisible = false;
                overlay.getChildren().clear();
                overlay.setPickOnBounds(false);
                map.requestFocus();
              });
      b.setId("upgrade-" + u.name());
      b.getStyleClass().add("upgrade-choice");
      b.setMaxWidth(Double.MAX_VALUE);
      card.getChildren().add(b);
    }
    showOverlay(card);
  }

  private void showEnd() {
    endVisible = true;
    VBox card = modal("Every connection counted.", "A terminal stayed overcrowded for too long.");
    card.getChildren()
        .add(label(String.format("%,d passengers delivered", vm.game().delivered()), "end-score"));
    card.getChildren()
        .add(
            label(
                "Week "
                    + vm.game().week()
                    + "  ·  "
                    + vm.game().airports().size()
                    + " airports  ·  "
                    + vm.game().map().title(),
                "muted"));
    Button restart =
        button("Fly again", () -> startMap(maps.indexOf(vm.game().map()), System.nanoTime()));
    restart.setId("restart");
    restart.getStyleClass().add("primary");
    Button choose = button("Choose a map", this::showMenu);
    card.getChildren().add(new HBox(12, restart, choose));
    showOverlay(card);
  }

  private void showOverlay(VBox card) {
    StackPane dim = new StackPane(card);
    dim.getStyleClass().add("scrim");
    dim.setPadding(new Insets(25));
    overlay.getChildren().setAll(dim);
    overlay.setPickOnBounds(true);
  }

  private VBox modal(String title, String subtitle) {
    VBox v = new VBox(18);
    v.getStyleClass().add("modal");
    v.setMaxWidth(750);
    v.setMaxHeight(Region.USE_PREF_SIZE);
    v.setPadding(new Insets(36));
    v.getChildren().addAll(label(title, "modal-title"), label(subtitle, "muted"));
    return v;
  }

  private void key(KeyEvent e) {
    if (e.getCode() == KeyCode.ESCAPE) {
      if (help) {
        help = false;
        if (menu) showMenu();
        else {
          overlay.getChildren().clear();
          overlay.setPickOnBounds(false);
          rewardVisible = false;
          endVisible = false;
        }
      } else vm.cancelEdit();
      e.consume();
      return;
    }
    if (menu || help || vm.game().rewardPending() || vm.game().gameOver()) return;
    switch (e.getCode()) {
      case SPACE -> vm.togglePause();
      case DIGIT1, NUMPAD1 -> vm.setSpeed(1);
      case DIGIT2, NUMPAD2 -> vm.setSpeed(2);
      case DIGIT3, NUMPAD3 -> vm.setSpeed(3);
      case Q ->
          vm.selectColor(
              (vm.selectedColor() + vm.game().unlockedLines() - 1) % vm.game().unlockedLines());
      case E -> vm.selectColor((vm.selectedColor() + 1) % vm.game().unlockedLines());
      case DELETE, BACK_SPACE -> vm.clearLine();
      case H -> showHelp();
      case R -> map.resetCamera();
      default -> {
        return;
      }
    }
    e.consume();
  }

  private static Region spacer() {
    Region r = new Region();
    HBox.setHgrow(r, Priority.ALWAYS);
    return r;
  }

  private static Label label(String text, String style) {
    Label l = new Label(text);
    l.setMinHeight(Region.USE_PREF_SIZE);
    l.getStyleClass().add(style);
    return l;
  }

  private static Button button(String text, Runnable action) {
    Button b = new Button(text);
    b.setAlignment(Pos.CENTER);
    b.setFocusTraversable(false);
    b.setOnAction(e -> action.run());
    return b;
  }
}
