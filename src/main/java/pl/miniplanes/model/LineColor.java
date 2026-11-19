package pl.miniplanes.model;

/** Domain colours are strings, with no dependency on javafx.scene.paint. */
public enum LineColor {
  CORAL("Coral", "#E76A55"),
  TEAL("Teal", "#168D91"),
  BLUE("Blue", "#507CD4"),
  AMBER("Amber", "#D59A24"),
  VIOLET("Violet", "#9463BB"),
  GREEN("Green", "#5B9564"),
  ROSE("Rose", "#C76898"),
  SLATE("Slate", "#63788E");
  public final String label, hex;

  LineColor(String label, String hex) {
    this.label = label;
    this.hex = hex;
  }
}
