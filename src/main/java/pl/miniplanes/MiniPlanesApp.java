package pl.miniplanes;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import pl.miniplanes.data.MapRepository;
import pl.miniplanes.view.GameWindow;

public final class MiniPlanesApp extends Application {
  private GameWindow window;

  @Override
  public void start(Stage stage) {
    try {
      window = new GameWindow(stage, new MapRepository().loadAll());
      window.show();
    } catch (Exception e) {
      e.printStackTrace();
      Alert alert =
          new Alert(Alert.AlertType.ERROR, "Mini Planes could not start: " + e.getMessage());
      alert.setHeaderText("Check your JavaFX installation and map resources");
      alert.showAndWait();
      javafx.application.Platform.exit();
    }
  }

  @Override
  public void stop() {
    if (window != null) window.stop();
  }
}
