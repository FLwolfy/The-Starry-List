package com.flwolfy.starrylist.modmenu;

import com.flwolfy.starrylist.StarryListMod;
import com.flwolfy.starrylist.data.config.StarryListConfigData;
import com.flwolfy.starrylist.data.config.StarryListConfigManager;
import com.flwolfy.starrylist.data.lang.StarryListLang;
import com.flwolfy.starrylist.data.lang.StarryListLangAdapter;
import com.flwolfy.starrylist.data.script.StarryListScript;
import com.flwolfy.starrylist.data.script.StarryListScriptAdapter;
import com.flwolfy.starrylist.data.script.StarryListScript;
import com.flwolfy.starrylist.event.StarryListEventType;
import com.flwolfy.starrylist.modmenu.entrybuilder.StarryListScriptEntryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the local-only Cloth Config editor exposed through ModMenu. */
public final class StarryListClothConfigGUI {

  private static final SystemToast.SystemToastId SAVE_RESULT = new SystemToast.SystemToastId();
  private static final Gson GSON = new GsonBuilder()
      .registerTypeAdapter(StarryListLang.class, new StarryListLangAdapter())
      .registerTypeAdapter(StarryListScript.class, new StarryListScriptAdapter())
      .create();

  private StarryListClothConfigGUI() {}

  public static Screen create(Screen parent) {
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

    ConfigCategory custom = builder.getOrCreateCategory(
        Component.translatable("starrylist.config.custom_boards")
    );
    custom.addEntry(entries.startTextDescription(
        Component.translatable("starrylist.config.script_warning").withStyle(ChatFormatting.RED)
    ).build());
    List<String> serialized = current.customBoards().stream().map(GSON::toJson).toList();
    custom.addEntry(entries.startStrList(
        Component.translatable("starrylist.config.custom_board_entries"), serialized
    ).setTooltip(Component.translatable("starrylist.config.custom_board_entries.tooltip"))
        .setDefaultValue(List.of())
        .setSaveConsumer(value -> {
          values.customBoards = parseBoards(value);
          values.customBoardStructureChanged = !value.equals(serialized);
        }).build());
    addStructuredBoardEditors(custom, entries, values, current);

    ConfigCategory all = builder.getOrCreateCategory(Component.translatable("starrylist.config.all"));
    all.addEntry(entries.startTextDescription(
        Component.translatable("starrylist.config.all_summary",
            values.language.getLangKey(),
            values.adminPermissionLevel,
            values.rotationEnabled,
            values.rotationIntervalSeconds,
            values.customBoards.size())
    ).build());

    builder.setSavingRunnable(() -> save(values));
    return builder.build();
  }

  private static List<StarryListConfigData.CustomBoard> parseBoards(List<String> serialized) {
    List<StarryListConfigData.CustomBoard> boards = new ArrayList<>();
    for (String value : serialized) {
      boards.add(GSON.fromJson(value, StarryListConfigData.CustomBoard.class));
    }
    return List.copyOf(boards);
  }

  private static void addStructuredBoardEditors(
      ConfigCategory category,
      ConfigEntryBuilder entries,
      StarryListClothConfigBuilder values,
      StarryListConfigData current
  ) {
    StarryListScriptEntryBuilder scripts = new StarryListScriptEntryBuilder();
    for (int boardIndex = 0; boardIndex < current.customBoards().size(); boardIndex++) {
      int selectedBoard = boardIndex;
      StarryListConfigData.CustomBoard board = current.customBoards().get(boardIndex);
      SubCategoryBuilder boardCategory = entries.startSubCategory(
          Component.literal(board.id() + " — " + board.displayName())
      ).setExpanded(false);
      boardCategory.add(entries.startBooleanToggle(
          Component.translatable("starrylist.config.custom.enabled"), board.enabled()
      ).setSaveConsumer(value -> values.updateBoard(selectedBoard, previous -> copyBoard(
          previous, previous.id(), previous.objectiveName(), previous.displayName(), value
      ))).build());
      boardCategory.add(entries.startStrField(
          Component.translatable("starrylist.config.custom.id"), board.id()
      ).setSaveConsumer(value -> values.updateBoard(selectedBoard, previous -> copyBoard(
          previous, value, previous.objectiveName(), previous.displayName(), previous.enabled()
      ))).build());
      boardCategory.add(entries.startStrField(
          Component.translatable("starrylist.config.custom.objective"), board.objectiveName()
      ).setSaveConsumer(value -> values.updateBoard(selectedBoard, previous -> copyBoard(
          previous, previous.id(), value, previous.displayName(), previous.enabled()
      ))).build());
      boardCategory.add(entries.startStrField(
          Component.translatable("starrylist.config.custom.display_name"), board.displayName()
      ).setSaveConsumer(value -> values.updateBoard(selectedBoard, previous -> copyBoard(
          previous, previous.id(), previous.objectiveName(), value, previous.enabled()
      ))).build());

      for (int sourceIndex = 0; sourceIndex < board.sources().size(); sourceIndex++) {
        int selectedSource = sourceIndex;
        StarryListConfigData.Source source = board.sources().get(sourceIndex);
        SubCategoryBuilder sourceCategory = entries.startSubCategory(
            Component.translatable("starrylist.config.custom.source", sourceIndex + 1)
        ).setExpanded(false);
        sourceCategory.add(entries.startEnumSelector(
            Component.translatable("starrylist.config.custom.trigger"),
            StarryListEventType.class,
            source.trigger()
        ).setSaveConsumer(value -> values.updateSource(
            selectedBoard, selectedSource, previous -> copySource(
                previous, value, previous.update(), previous.intervalSeconds(), previous.script()
            )
        )).build());
        sourceCategory.add(entries.startEnumSelector(
            Component.translatable("starrylist.config.custom.update"),
            StarryListConfigData.UpdateMode.class,
            source.update()
        ).setSaveConsumer(value -> values.updateSource(
            selectedBoard, selectedSource, previous -> copySource(
                previous, previous.trigger(), value, previous.intervalSeconds(), previous.script()
            )
        )).build());
        sourceCategory.add(entries.startIntField(
            Component.translatable("starrylist.config.custom.interval"), source.intervalSeconds()
        ).setMin(0).setMax(86400).setSaveConsumer(value -> values.updateSource(
            selectedBoard, selectedSource, previous -> copySource(
                previous, previous.trigger(), previous.update(), value, previous.script()
            )
        )).build());
        var scriptField = scripts.create(
            entries,
            source.script(),
            Component.translatable("starrylist.config.custom.script")
        );
        scriptField.setDefaultValue(new StarryListScript("0"));
        scriptField.setSaveConsumer(value -> values.updateSource(
            selectedBoard, selectedSource, previous -> copySource(
                previous,
                previous.trigger(),
                previous.update(),
                previous.intervalSeconds(),
                value
            )
        ));
        sourceCategory.add(scriptField.build());
        boardCategory.add(sourceCategory.build());
      }
      category.addEntry(boardCategory.build());
    }
  }

  private static StarryListConfigData.CustomBoard copyBoard(
      StarryListConfigData.CustomBoard source,
      String id,
      String objective,
      String displayName,
      boolean enabled
  ) {
    return new StarryListConfigData.CustomBoard(
        id, objective, displayName, enabled, source.sources()
    );
  }

  private static StarryListConfigData.Source copySource(
      StarryListConfigData.Source source,
      StarryListEventType trigger,
      StarryListConfigData.UpdateMode update,
      int interval,
      StarryListScript script
  ) {
    return new StarryListConfigData.Source(trigger, update, interval, script);
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
