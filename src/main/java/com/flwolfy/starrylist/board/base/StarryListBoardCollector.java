package com.flwolfy.starrylist.board.base;

/** Installs permanent event collectors for a bundled Java board. */
public interface StarryListBoardCollector {

  /**
   * Registers the board's statistic collectors during common mod initialization.
   *
   * <p>Implementations should register Fabric callbacks through
   * {@link StarryListBoardRegistrar#listen(String, net.fabricmc.fabric.api.event.Event, Object)}
   * instead of calling Fabric {@code Event.register()} directly. Managed listeners remain
   * installed once and can be suppressed when configuration hot-disables the board.</p>
   *
   * @param registrar services bound to the board
   */
  void register(StarryListBoardRegistrar registrar);
}
