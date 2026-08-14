package com.flwolfy.starrylist.modmenu.screen;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.board.script.StarryListScriptManager;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Coordinates the local-only Cloth Config screen lifecycle. */
public final class StarryListClothConfigScreenController {

  private static final SystemToast.SystemToastId SAVE_RESULT = new SystemToast.SystemToastId();

  private StarryListClothConfigScreenController() {}

  /**
   * Creates a configuration screen for the supplied parent.
   *
   * @param parent parent screen
   * @return newly built Cloth Config screen
   */
  public static Screen create(Screen parent) {
    inspectOnOpen();
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
    return builder.build();
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
          Minecraft.getInstance().gui.toastManager(),
          SAVE_RESULT,
          Component.translatable("starrylist.config.save_success"),
          Component.translatable("starrylist.config.save_success.detail")
      );
    } catch (Exception exception) {
      StarryListMod.LOGGER.error("Failed to save StarryList client configuration", exception);
      SystemToast.add(
          Minecraft.getInstance().gui.toastManager(),
          SAVE_RESULT,
          Component.translatable("starrylist.config.save_failed"),
          Component.literal(exception.getMessage() == null
              ? exception.getClass().getSimpleName() : exception.getMessage())
      );
    }
  }

  private static void showRefreshResult(StarryListScriptManager.OperationResult result) {
    SystemToast.add(
        Minecraft.getInstance().gui.toastManager(),
        SAVE_RESULT,
        Component.translatable(result.success()
            ? "starrylist.config.boards.custom.refresh.success"
            : "starrylist.config.boards.custom.refresh.failed"),
        Component.literal(result.message())
    );
  }
}
