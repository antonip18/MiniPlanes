package pl.miniplanes;

/**
 * Separate launcher supports both Maven and a conventional classpath without JavaFX launcher magic.
 */
public final class Main {
  private Main() {}

  public static void main(String[] args) {
    javafx.application.Application.launch(MiniPlanesApp.class, args);
  }
}
