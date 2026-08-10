package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the local-only Cloth Config editor exposed through ModMenu. */
final class StarryListClothConfigGUI {

  private static final SystemToast.SystemToastId SAVE_RESULT = new SystemToast.SystemToastId();

  private StarryListClothConfigGUI() {}

  static Screen create(Screen parent) {
    StarryListConfigData current = StarryListConfigManager.getInstance().data();
    StarryListClothConfigBuilder values = new StarryListClothConfigBuilder(current);
    ConfigBuilder builder = ConfigBuilder.create()
        .setParentScreen(parent)
        .setTitle(Component.translatable("starrylist.config.title"))
        .setDoesConfirmSave(true);
    ConfigEntryBuilder entries = builder.entryBuilder();

    ConfigCategory general = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.general")
    );
    general.addEntry(entries.startTextDescription(
        Component.translatable("starrylist.config.local_only").withStyle(ChatFormatting.GOLD)
    ).build());
    general.addEntry(entries.startEnumSelector(
        Component.translatable("starrylist.config.language"),
        StarryListLang.class,
        values.language
    ).setDefaultValue(StarryListConfigData.DEFAULT.general().language())
        .setSaveConsumer(value -> values.language = value).build());
    general.addEntry(entries.startIntSlider(
        Component.translatable("starrylist.config.admin_permission"),
        values.adminPermissionLevel,
        0,
        4
    ).setDefaultValue(2).setSaveConsumer(value -> values.adminPermissionLevel = value).build());

    ConfigCategory display = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.display")
    );
    display.addEntry(entries.startBooleanToggle(
        Component.translatable("starrylist.config.hidden_default"), values.hiddenByDefault
    ).setDefaultValue(false).setSaveConsumer(value -> values.hiddenByDefault = value).build());
    display.addEntry(entries.startBooleanToggle(
        Component.translatable("starrylist.config.rotation"), values.rotationEnabled
    ).setDefaultValue(true).setSaveConsumer(value -> values.rotationEnabled = value).build());
    display.addEntry(entries.startIntField(
        Component.translatable("starrylist.config.rotation_interval"), values.rotationIntervalSeconds
    ).setMin(1).setMax(3600).setDefaultValue(20)
        .setSaveConsumer(value -> values.rotationIntervalSeconds = value).build());
    display.addEntry(entries.startStrList(
        Component.translatable("starrylist.config.default_boards"), values.defaultBoards
    ).setDefaultValue(StarryListConfigData.DEFAULT.display().defaultBoards())
        .setSaveConsumer(value -> values.defaultBoards = List.copyOf(value)).build());

    ConfigCategory all = builder.getOrCreateCategory(Component.translatable("starrylist.config.all"));
    all.addEntry(entries.startTextDescription(
        Component.translatable("starrylist.config.all_summary",
            values.language.getLangKey(),
            values.adminPermissionLevel,
            values.rotationEnabled,
            values.rotationIntervalSeconds,
            String.join(", ", values.defaultBoards))
    ).build());

    builder.setSavingRunnable(() -> save(values));
    return builder.build();
  }

  private static void save(StarryListClothConfigBuilder values) {
    try {
      StarryListConfigData replacement = values.build();
      List<String> errors = replacement.validate();
      if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(", ", errors));
      if (!applyUpdate(replacement)) {
        throw new IllegalStateException("The configuration could not be applied");
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

  private static boolean applyUpdate(StarryListConfigData replacement) {
    var integratedServer = Minecraft.getInstance().getSingleplayerServer();
    if (integratedServer == null || integratedServer.isSameThread()) {
      return StarryListConfigManager.getInstance().update(replacement);
    }
    CompletableFuture<Boolean> result = new CompletableFuture<>();
    integratedServer.execute(() -> result.complete(
        StarryListConfigManager.getInstance().update(replacement)
    ));
    return result.join();
  }
}
