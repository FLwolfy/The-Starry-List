package com.flwolfy.starrylist.modmenu.screen;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.board.script.StarryListScriptManager;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import java.lang.ref.WeakReference;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.gui.AbstractConfigScreen;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Coordinates the local-only Cloth Config screen lifecycle. */
public final class StarryListClothConfigScreenController {

  private static final SystemToast.SystemToastId SAVE_RESULT = new SystemToast.SystemToastId();
  private static WeakReference<Screen> activeScreen = new WeakReference<>(null);
  private static WeakReference<ClothConfigScreen> pendingScrollScreen = new WeakReference<>(null);
  private static Screen activeParent;
  private static long activeRevision;
  private static double pendingScrollAmount;
  private static int pendingScrollAttempts;

  private StarryListClothConfigScreenController() {}

  /**
   * Creates a configuration screen for the supplied parent.
   *
   * @param parent parent screen
   * @return newly built Cloth Config screen
   */
  public static Screen create(Screen parent) {
    inspectOnOpen();
    return create(parent, 0);
  }

  /**
   * Rebuilds the active screen when the external board catalog revision changes.
   */
  public static void refreshIfRegistryChanged() {
    restorePendingScroll();
    Minecraft client = Minecraft.getInstance();
    Screen screen = activeScreen.get();
    long revision = StarryListBoardRegistry.getInstance().revision();
    if (screen == null || client.screen != screen || activeRevision == revision) {
      return;
    }

    int category = screen instanceof AbstractConfigScreen configScreen
        ? configScreen.selectedCategoryIndex : 0;
    double scroll = screen instanceof ClothConfigScreen configScreen
        && configScreen.listWidget != null ? configScreen.listWidget.getScroll() : 0.0;
    showWithScroll(client, create(activeParent, category), scroll);
  }

  private static Screen create(Screen parent, int selectedCategory) {
    ConfigBuilder builder = ConfigBuilder.create()
        .setParentScreen(parent)
        .setTitle(Component.translatable("starrylist.config.title"))
        .setDoesConfirmSave(true);
    StarryListClothConfigLayout layout = new StarryListClothConfigLayout(
        builder,
        StarryListConfigManager.getInstance().loadForEditing(),
        StarryListClothConfigScreenController::showRefreshResult
    );
    layout.build();
    builder.setSavingRunnable(() -> {
      layout.flush();
      save(layout.model());
    });

    Screen screen = builder.build();
    if (screen instanceof AbstractConfigScreen configScreen) {
      configScreen.selectedCategoryIndex = selectedCategory;
    }
    activeScreen = new WeakReference<>(screen);
    activeParent = parent;
    activeRevision = StarryListBoardRegistry.getInstance().revision();
    return screen;
  }

  private static void inspectOnOpen() {
    try {
      inspectScripts();
    } catch (RuntimeException exception) {
      StarryListMod.LOGGER.error(
          "Failed to refresh Groovy board previews while opening Cloth Config",
          exception
      );
    }
  }

  private static StarryListScriptManager.OperationResult inspectScripts() {
    return StarryListScriptManager.getInstance().preview(
        StarryListBoardRegistry.getInstance(),
        Minecraft.getInstance().getSingleplayerServer() != null
    );
  }

  private static void save(StarryListConfigEditorModel model) {
    try {
      if (!model.invalidFields().isEmpty()) {
        throw new IllegalArgumentException(String.join(", ", model.invalidFields()));
      }
      if (!StarryListConfigManager.getInstance().savePending(model.build())) {
        throw new IllegalStateException("The configuration could not be saved");
      }
      SystemToast.add(
          Minecraft.getInstance().getToastManager(),
          SAVE_RESULT,
          Component.translatable("starrylist.config.save_success"),
          Component.translatable("starrylist.config.save_success.detail")
      );
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to save StarryList client configuration", exception);
      SystemToast.add(
          Minecraft.getInstance().getToastManager(),
          SAVE_RESULT,
          Component.translatable("starrylist.config.save_failed"),
          Component.literal(exception.getMessage() == null
              ? exception.getClass().getSimpleName() : exception.getMessage())
      );
    }
  }

  private static void showRefreshResult(StarryListScriptManager.OperationResult result) {
    SystemToast.add(
        Minecraft.getInstance().getToastManager(),
        SAVE_RESULT,
        Component.translatable(result.success()
            ? "starrylist.config.boards.custom.refresh.success"
            : "starrylist.config.boards.custom.refresh.failed"),
        Component.literal(result.message())
    );
  }

  private static void showWithScroll(Minecraft client, Screen screen, double scrollAmount) {
    client.setScreen(screen);
    if (screen instanceof ClothConfigScreen configScreen) {
      pendingScrollScreen = new WeakReference<>(configScreen);
      pendingScrollAmount = scrollAmount;
      pendingScrollAttempts = 4;
      if (configScreen.listWidget != null) {
        configScreen.listWidget.scrollTo(scrollAmount, false);
      }
    }
  }

  private static void restorePendingScroll() {
    ClothConfigScreen screen = pendingScrollScreen.get();
    if (screen == null || Minecraft.getInstance().screen != screen
        || pendingScrollAttempts <= 0) {
      pendingScrollScreen = new WeakReference<>(null);
      return;
    }
    if (screen.listWidget == null) {
      return;
    }
    if (Math.abs(screen.listWidget.getScroll() - pendingScrollAmount) < 0.5) {
      pendingScrollScreen = new WeakReference<>(null);
      return;
    }
    screen.listWidget.scrollTo(pendingScrollAmount, false);
    pendingScrollAttempts--;
  }
}
