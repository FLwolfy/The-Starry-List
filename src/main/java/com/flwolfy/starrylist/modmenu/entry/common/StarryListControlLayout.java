package com.flwolfy.starrylist.modmenu.entry.common;

/** Provides the shared geometry used by right-aligned configuration controls. */
public final class StarryListControlLayout {

  private static final int TOTAL_WIDTH = 150;
  private static final int GAP = 2;

  private StarryListControlLayout() {}

  /**
   * Returns the left edge of the value-control region.
   *
   * @param x entry left edge
   * @param entryWidth entry width
   * @return value-control left edge
   */
  public static int valueX(int x, int entryWidth) {
    return x + entryWidth - TOTAL_WIDTH;
  }

  /**
   * Returns the width available to a value control before its reset button.
   *
   * @param resetWidth reset button width
   * @return value-control width
   */
  public static int valueWidth(int resetWidth) {
    return TOTAL_WIDTH - resetWidth - GAP;
  }

  /**
   * Returns the left edge of a right-aligned reset button.
   *
   * @param x entry left edge
   * @param entryWidth entry width
   * @param resetWidth reset button width
   * @return reset button left edge
   */
  public static int resetX(int x, int entryWidth, int resetWidth) {
    return x + entryWidth - resetWidth;
  }

  /**
   * Returns the standard horizontal gap between adjacent controls.
   *
   * @return horizontal gap in pixels
   */
  public static int gap() {
    return GAP;
  }
}
