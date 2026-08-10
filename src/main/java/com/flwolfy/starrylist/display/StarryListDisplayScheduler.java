package com.flwolfy.starrylist.display;

/** Named facade retained to keep display scheduling isolated from event registration. */
public record StarryListDisplayScheduler(StarryListDisplayManager manager) {
  public void tick() {
    manager.tick();
  }
}
