package pl.miniplanes.viewmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.miniplanes.data.MapRepository;

class GameViewModelTest {
  GameViewModel create() throws Exception {
    return new GameViewModel(new MapRepository().load("europe"), 42);
  }

  @Test
  void pausedFramesNeverAccumulateCatchUpTime() throws Exception {
    var vm = create();
    vm.tick(100);
    assertEquals(0, vm.game().elapsed());
    vm.togglePause();
    vm.tick(.1);
    assertEquals(.1, vm.game().elapsed(), 1e-6);
    vm.pause();
    vm.tick(1000);
    vm.togglePause();
    vm.tick(.1);
    assertEquals(.2, vm.game().elapsed(), 1e-6);
  }

  @Test
  void tHandleRemovesTerminalWhenDraggedOverIt() throws Exception {
    var vm = create();
    var airports = vm.game().airports();
    vm.game().editRoute(0, airports, false);
    vm.beginHandle(0, false);
    vm.hoverAirport(airports.getLast());
    vm.finishEdit();
    assertEquals(airports.subList(0, 2), vm.game().line(0).path());
    vm.beginHandle(0, true);
    vm.hoverAirport(airports.getFirst());
    vm.finishEdit();
    assertEquals(0, vm.game().line(0).size());
    assertEquals(3, vm.game().sparePlanes());
  }

  @Test
  void cancelIsAtomicAndRestoresRunningState() throws Exception {
    var vm = create();
    var a = vm.game().airports();
    vm.game().editRoute(0, a, false);
    vm.togglePause();
    vm.beginHandle(0, false);
    assertTrue(vm.isPaused());
    vm.hoverAirport(a.getLast());
    vm.cancelEdit();
    assertEquals(3, vm.game().line(0).size());
    assertFalse(vm.isPaused());
  }

  @Test
  void endpointsCanCreateAndOpenALoop() throws Exception {
    var vm = create();
    var a = vm.game().airports();
    vm.game().editRoute(0, a, false);
    vm.beginHandle(0, false);
    vm.hoverAirport(a.getFirst());
    vm.finishEdit();
    assertTrue(vm.game().line(0).isLoop());
    vm.openLoop();
    assertFalse(vm.game().line(0).isLoop());
    assertEquals(3, vm.game().line(0).size());
  }

  @Test
  void middleDragInsertsAndCanReorderWithoutDuplicates() throws Exception {
    var vm = create();
    var a = vm.game().airports();
    vm.game().editRoute(0, List.of(a.get(0), a.get(2)), false);
    vm.beginSegment(0, 0);
    vm.hoverAirport(a.get(1));
    vm.finishEdit();
    assertEquals(a, vm.game().line(0).path());
    vm.beginSegment(0, 0);
    vm.hoverAirport(a.get(2));
    vm.finishEdit();
    assertEquals(List.of(a.get(0), a.get(2), a.get(1)), vm.game().line(0).path());
  }

  @Test
  void endDragDoesNotDeleteAirportUsedToStartTheGesture() throws Exception {
    var vm = create();
    var a = vm.game().airports();
    vm.game().editRoute(0, List.of(a.get(0), a.get(1)), false);
    vm.beginAirport(a.get(1));
    vm.hoverAirport(a.get(1));
    vm.hoverAirport(a.get(2));
    vm.finishEdit();
    assertEquals(a, vm.game().line(0).path());
  }
}
