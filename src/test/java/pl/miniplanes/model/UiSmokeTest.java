package pl.miniplanes.model;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.PixelFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.miniplanes.data.MapRepository;
import pl.miniplanes.view.AircraftRenderChecks;
import pl.miniplanes.view.GameWindow;
import pl.miniplanes.view.MapCanvas;

/** Native pointer input and real JavaFX rendering. Run with a display, e.g. xvfb-run. */
@EnabledIfSystemProperty(named = "miniplanes.ui", matches = "true")
class UiSmokeTest {
  @Test
  void menusDragEditingResourcesAndBothMapsWorkAtTwoWindowSizes() throws Exception {
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(ready::countDown);
    assertTrue(ready.await(15, TimeUnit.SECONDS));
    GameWindow w =
        fx(
            () -> {
              GameWindow window = new GameWindow(new Stage(), new MapRepository().loadAll());
              window.show();
              window.stop();
              return window;
            });
    try {
      pulse();
      fx(
          () -> {
            layout(w);
            snapshot(w, "menu");
            checkButtonCenters(w);
            AircraftRenderChecks.verify();
            ((Button) w.scene().lookup("#start-europe")).fire();
            assertEquals("europe", w.viewModel().game().map().id());
            w.startMap(0, 42);
            return null;
          });
      pulse();
      var vm = w.viewModel();
      var a = fx(() -> vm.game().airports());
      drag(w, a.get(0).position(), a.get(1).position());
      fx(
          () -> {
            assertEquals(2, vm.game().line(0).size());
            assertEquals(1, vm.game().airplanes().size());
            ((Button) w.scene().lookup("#add-aircraft")).fire();
            assertEquals(0, vm.game().airplanes().getLast().index());
            return null;
          });
      Point2D last = screen(w, a.get(1).position()), first = screen(w, a.get(0).position());
      Point2D handle = last.add(last.subtract(first).normalize().multiply(27));
      dragScreen(handle, last);
      fx(
          () -> {
            assertEquals(0, vm.game().line(0).size());
            vm.game().editRoute(0, List.of(a.get(0), a.get(2)), false);
            w.mapCanvas().draw();
            return null;
          });
      drag(w, a.get(0).position().lerp(a.get(2).position(), .5), a.get(1).position());
      fx(
          () -> {
            assertEquals(a, vm.game().line(0).path());
            vm.clearLine();
            preparePreview(vm.game());
            vm.addAirplane(0);
            vm.selectColor(0);
            layout(w);
            w.mapCanvas().draw();
            snapshot(w, "europe");
            checkPassengersInFlight(w);
            checkButtonCenters(w);
            return null;
          });
      fx(
          () -> {
            Stage stage = (Stage) w.scene().getWindow();
            stage.setWidth(1100);
            stage.setHeight(740);
            return null;
          });
      pulse();
      fx(
          () -> {
            layout(w);
            w.mapCanvas().draw();
            snapshot(w, "compact");
            checkButtonCenters(w);
            assertTrue(w.mapCanvas().getWidth() > 600);
            assertTrue(w.mapCanvas().getHeight() > 300);
            Stage stage = (Stage) w.scene().getWindow();
            stage.setWidth(1440);
            stage.setHeight(920);
            w.startMap(1, 42);
            return null;
          });
      pulse();
      fx(
          () -> {
            preparePreview(vm.game());
            vm.selectColor(0);
            layout(w);
            w.mapCanvas().draw();
            snapshot(w, "americas");
            assertEquals(35, vm.game().map().airports().size());
            assertFalse(vm.game().geometry().segments().isEmpty());
            ((Button) w.scene().lookup("#region-overview")).fire();
            assertEquals(0, w.mapCanvas().airportsOutsideView());
            snapshot(w, "americas-overview", false);
            ((Button) w.scene().lookup("#region-south")).fire();
            assertEquals(MapCanvas.RegionPreset.SOUTH, w.mapCanvas().preset());
            snapshot(w, "americas-south", false);
            ((Button) w.scene().lookup("#region-north")).fire();
            var plane = vm.game().airplanes().get(2);
            Point before = w.mapCanvas().screen(vm.game().geometry().aircraftPosition(plane));
            double normalLength = w.mapCanvas().aircraftScreenLength(plane);
            w.mapCanvas().zoomAt(2, before.x(), before.y());
            assertEquals(normalLength * 2, w.mapCanvas().aircraftScreenLength(plane), .001);
            assertTrue(
                before.distance(w.mapCanvas().screen(vm.game().geometry().aircraftPosition(plane)))
                    < .001);
            snapshot(w, "aircraft-zoom", false);
            w.mapCanvas().resetCamera();
            ((Button) w.scene().lookup("#help")).fire();
            layout(w);
            snapshot(w, "help");
            checkButtonCenters(w);
            ((Button) w.scene().lookup("#close-help")).fire();
            vm.game().rewardPending = true;
            vm.game().week = 2;
            vm.selectColor(0);
            layout(w);
            snapshot(w, "reward");
            checkButtonCenters(w);
            ((Button) w.scene().lookup("#upgrade-CAPACITY")).fire();
            assertFalse(vm.game().rewardPending());
            vm.game().gameOver = true;
            vm.selectColor(0);
            layout(w);
            snapshot(w, "end");
            checkButtonCenters(w);
            ((Button) w.scene().lookup("#restart")).fire();
            assertFalse(vm.game().gameOver());
            assertEquals(3, vm.game().airports().size());
            return null;
          });
    } finally {
      fx(
          () -> {
            w.stop();
            w.scene().getWindow().hide();
            return null;
          });
      Platform.exit();
    }
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    FutureTask<T> task = new FutureTask<>(action);
    Platform.runLater(task);
    return task.get(20, TimeUnit.SECONDS);
  }

  private static void pulse() throws InterruptedException {
    Thread.sleep(140);
  }

  private static Point2D screen(GameWindow w, Point world) throws Exception {
    return fx(
        () -> {
          Point p = w.mapCanvas().screen(world);
          return w.mapCanvas().canvas().localToScreen(p.x(), p.y());
        });
  }

  private static void drag(GameWindow w, Point a, Point b) throws Exception {
    dragScreen(screen(w, a), screen(w, b));
  }

  private static void dragScreen(Point2D a, Point2D b) throws Exception {
    fx(
        () -> {
          new Robot().mouseMove(a);
          return null;
        });
    pulse();
    fx(
        () -> {
          new Robot().mousePress(MouseButton.PRIMARY);
          return null;
        });
    pulse();
    for (int i = 1; i <= 5; i++) {
      Point2D p = a.add(b.subtract(a).multiply(i / 5.0));
      fx(
          () -> {
            new Robot().mouseMove(p);
            return null;
          });
      pulse();
    }
    fx(
        () -> {
          new Robot().mouseRelease(MouseButton.PRIMARY);
          return null;
        });
    pulse();
  }

  private static void preparePreview(GameData game) {
    while (game.airports.size() < 14) game.spawnAirport();
    game.permits = 12;
    game.sparePlanes = 8;
    var sorted =
        game.airports().stream().sorted(Comparator.comparingDouble(a -> a.position().x())).toList();
    List<Airport> a = new ArrayList<>(), b = new ArrayList<>(), c = new ArrayList<>();
    for (int i = 0; i < sorted.size(); i++)
      switch (i % 3) {
        case 0 -> a.add(sorted.get(i));
        case 1 -> b.add(sorted.get(i));
        default -> c.add(sorted.get(i));
      }
    b.add(2, a.get(2));
    c.add(1, a.get(1));
    game.editRoute(0, a, false);
    game.editRoute(1, b, false);
    game.editRoute(2, c, false);
    game.upgradeAirport(a.get(2));
    game.upgradeAirplane(game.airplanes.getFirst().id());
    Updater updater = new Updater(game);
    game.airportClock = -1000;
    for (int i = 0; i < 2000; i++) updater.step(Updater.STEP);
  }

  private static void layout(GameWindow w) {
    w.scene().getRoot().applyCss();
    w.scene().getRoot().layout();
  }

  private static void checkButtonCenters(GameWindow w) {
    for (Node node : w.scene().getRoot().lookupAll(".button"))
      if (node instanceof Button button && button.isVisible()) {
        Node text = button.lookup(".text");
        if (!(text instanceof Text) || button.getHeight() < 2) continue;
        Bounds b = button.localToScene(button.getBoundsInLocal()),
            t = text.localToScene(text.getBoundsInLocal());
        assertTrue(
            Math.abs((b.getMinY() + b.getMaxY() - t.getMinY() - t.getMaxY()) / 2) < 3,
            "Off-centre button: " + button.getText());
      }
  }

  private static void checkPassengersInFlight(GameWindow w) {
    var plane =
        w.viewModel().game().airplanes().stream()
            .filter(a -> a.isFlying() && !a.passengers().isEmpty())
            .findFirst()
            .orElseThrow();
    var passengers = plane.passengers();
    w.mapCanvas().draw();
    var occupied = w.mapCanvas().canvas().snapshot(null, null);
    try {
      plane.passengers.clear();
      w.mapCanvas().draw();
      var empty = w.mapCanvas().canvas().snapshot(null, null);
      int changed = 0;
      for (int y = 0; y < (int) occupied.getHeight(); y++)
        for (int x = 0; x < (int) occupied.getWidth(); x++)
          if (occupied.getPixelReader().getArgb(x, y) != empty.getPixelReader().getArgb(x, y))
            changed++;
      assertTrue(
          changed > 20, "The map must render onboard passengers while the aircraft is flying");
    } finally {
      plane.passengers.addAll(passengers);
      w.mapCanvas().draw();
    }
  }

  private static void snapshot(GameWindow w, String name) throws Exception {
    snapshot(w, name, true);
  }

  private static void snapshot(GameWindow w, String name, boolean resetCamera) throws Exception {
    if (resetCamera) w.mapCanvas().resetCamera();
    w.mapCanvas().draw();
    var image = w.scene().snapshot(null);
    int width = (int) image.getWidth(), height = (int) image.getHeight();
    int[] data = new int[width * height];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), data, 0, width);
    BufferedImage png = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    png.setRGB(0, 0, width, height, data, 0, width);
    Path output = Path.of("target/ui-" + name + ".png");
    Files.createDirectories(output.getParent());
    ImageIO.write(png, "png", output.toFile());
  }
}
