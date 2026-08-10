package com.flwolfy.starrylist.gui;

import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.data.state.StarryListDisplayProfile;
import com.flwolfy.starrylist.scoreboard.StarryListBoardDefinition;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/** Provides the fixed six-board visual settings menu opened by {@code /starry}. */
public final class StarryListPlayerGui extends SimpleGui {

  private static final int[] BOARD_START_SLOTS = {9, 12, 15, 27, 30, 33};

  private final StarryListRuntime runtime;
  private final String locale;

  private StarryListPlayerGui(ServerPlayer player, StarryListRuntime runtime) {
    super(MenuType.GENERIC_9x6, player, false);
    this.runtime = runtime;
    this.locale = player.clientInformation().language();
    setTitle(text("starrylist.gui.title"));
    setLockPlayerInventory(true);
    render();
  }

  /**
   * Opens the visual settings menu for a player.
   *
   * @param player player receiving the menu
   * @param runtime active server services
   */
  public static void open(ServerPlayer player, StarryListRuntime runtime) {
    new StarryListPlayerGui(player, runtime).open();
  }

  private void render() {
    GuiElementBuilder filler = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
        .setName(Component.empty());
    for (int slot = 0; slot < getVirtualSize(); slot++) setSlot(slot, filler.build());

    StarryListDisplayProfile saved = runtime.state().profile(player.getUUID());
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> enabled = new ArrayList<>(editable.boards());
    List<StarryListBoardDefinition> ordered = orderedBoards(enabled);

    setSlot(4, new GuiElementBuilder(Items.NETHER_STAR)
        .setName(text("starrylist.gui.status").copy().withStyle(ChatFormatting.GOLD))
        .addLoreLine(text("starrylist.gui.status.mode", modeName(saved.mode())))
        .addLoreLine(text(
            "starrylist.gui.status.visible",
            runtime.display().effective(player.getUUID()).hidden()
                ? text("starrylist.gui.state.hidden").getString()
                : text("starrylist.gui.state.visible").getString()
        )).build());

    for (int index = 0; index < ordered.size(); index++) {
      StarryListBoardDefinition board = ordered.get(index);
      int start = BOARD_START_SLOTS[index];
      boolean selected = enabled.contains(board.id());
      int selectedIndex = enabled.indexOf(board.id());
      setSlot(start, moveButton(board, selectedIndex, -1));
      setSlot(start + 1, boardButton(board, selected, selectedIndex));
      setSlot(start + 2, moveButton(board, selectedIndex, 1));
    }

    setSlot(45, new GuiElementBuilder(Items.CLOCK)
        .setName(text("starrylist.gui.use_default").copy().withStyle(ChatFormatting.AQUA))
        .addLoreLine(text("starrylist.gui.use_default.description"))
        .setCallback(() -> {
          runtime.state().resetProfile(player.getUUID());
          applyAndRender();
        }).build());

    boolean hidden = saved.mode() == StarryListDisplayProfile.Mode.HIDDEN;
    setSlot(47, new GuiElementBuilder(hidden ? Items.LIME_DYE : Items.GRAY_DYE)
        .setName(text(hidden ? "starrylist.gui.show" : "starrylist.gui.hide").copy()
            .withStyle(hidden ? ChatFormatting.GREEN : ChatFormatting.GRAY))
        .setCallback(() -> {
          if (hidden) {
            saveCustom(runtime.display().editableProfile(player.getUUID()));
          } else {
            runtime.state().setProfile(player.getUUID(), new StarryListDisplayProfile(
                StarryListDisplayProfile.Mode.HIDDEN, List.of(), false,
                editable.rotationIntervalSeconds()
            ));
          }
          applyAndRender();
        }).build());

    setSlot(49, new GuiElementBuilder(editable.rotationEnabled() ? Items.LIME_DYE : Items.RED_DYE)
        .setName(text("starrylist.gui.rotation").copy().withStyle(ChatFormatting.YELLOW))
        .addLoreLine(text(
            "starrylist.gui.rotation.state",
            stateName(editable.rotationEnabled())
        ))
        .setCallback(() -> {
          saveCustom(new StarryListDisplayProfile(
              StarryListDisplayProfile.Mode.CUSTOM,
              editable.boards(),
              !editable.rotationEnabled(),
              editable.rotationIntervalSeconds()
          ));
          applyAndRender();
        }).build());

    setSlot(51, new GuiElementBuilder(Items.REPEATER)
        .setName(text("starrylist.gui.interval").copy().withStyle(ChatFormatting.YELLOW))
        .addLoreLine(text("starrylist.gui.interval.value", editable.rotationIntervalSeconds()))
        .addLoreLine(text("starrylist.gui.interval.description"))
        .setCallback(() -> openIntervalInput(editable)).build());

    setSlot(53, new GuiElementBuilder(Items.BARRIER)
        .setName(text("starrylist.gui.close").copy().withStyle(ChatFormatting.RED))
        .setCallback(() -> close()).build());
  }

  private GuiElementBuilder boardButton(
      StarryListBoardDefinition board,
      boolean selected,
      int selectedIndex
  ) {
    GuiElementBuilder builder = new GuiElementBuilder(board.icon())
        .setName(board.displayName(player).copy().withStyle(
            selected ? ChatFormatting.GREEN : ChatFormatting.GRAY
        ))
        .addLoreLine(text(
            selected ? "starrylist.gui.board.enabled" : "starrylist.gui.board.disabled"
        ));
    if (selected) {
      builder.addLoreLine(text("starrylist.gui.board.position", selectedIndex + 1)).glow();
    }
    return builder.addLoreLine(text("starrylist.gui.board.toggle"))
        .setCallback(() -> toggle(board.id()));
  }

  private eu.pb4.sgui.api.elements.GuiElement moveButton(
      StarryListBoardDefinition board,
      int selectedIndex,
      int offset
  ) {
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    boolean available = selectedIndex >= 0
        && selectedIndex + offset >= 0
        && selectedIndex + offset < editable.boards().size();
    GuiElementBuilder builder = new GuiElementBuilder(available ? Items.ARROW : Items.BLACK_STAINED_GLASS_PANE)
        .setName(text(offset < 0 ? "starrylist.gui.move_up" : "starrylist.gui.move_down").copy()
            .withStyle(available ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
    if (available) builder.setCallback(() -> move(board.id(), offset));
    return builder.build();
  }

  private void toggle(String boardId) {
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> boards = new ArrayList<>(editable.boards());
    if (boards.contains(boardId)) {
      if (boards.size() == 1) {
        player.sendSystemMessage(text("starrylist.gui.board.last_enabled"));
        return;
      }
      boards.remove(boardId);
    } else {
      boards.add(boardId);
    }
    saveCustom(copy(editable, boards));
    applyAndRender();
  }

  private void move(String boardId, int offset) {
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> boards = new ArrayList<>(editable.boards());
    int source = boards.indexOf(boardId);
    int target = source + offset;
    if (source < 0 || target < 0 || target >= boards.size()) return;
    boards.remove(source);
    boards.add(target, boardId);
    saveCustom(copy(editable, boards));
    applyAndRender();
  }

  private void openIntervalInput(StarryListDisplayProfile editable) {
    AnvilInputGui input = new AnvilInputGui(player, false) {
      /** {@inheritDoc} */
      @Override
      public void onInput(String value) {
        renderResult(this, value, editable);
      }
    };
    input.setTitle(text("starrylist.gui.interval.title"));
    input.setDefaultInputValue(Integer.toString(editable.rotationIntervalSeconds()));
    renderResult(input, input.getInput(), editable);
    input.open();
  }

  private void renderResult(
      AnvilInputGui input,
      String value,
      StarryListDisplayProfile editable
  ) {
    Integer seconds = parseInterval(value);
    GuiElementBuilder result = new GuiElementBuilder(seconds == null ? Items.BARRIER : Items.LIME_DYE)
        .setName(text(seconds == null
            ? "starrylist.gui.interval.invalid" : "starrylist.gui.interval.confirm"));
    if (seconds != null) {
      result.setCallback(() -> {
        saveCustom(new StarryListDisplayProfile(
            StarryListDisplayProfile.Mode.CUSTOM,
            editable.boards(),
            editable.rotationEnabled(),
            seconds
        ));
        runtime.display().update(player, true);
        StarryListPlayerGui.open(player, runtime);
      });
    }
    input.setSlot(2, result.build());
  }

  private Integer parseInterval(String value) {
    try {
      int parsed = Integer.parseInt(value.trim());
      return parsed >= 1 && parsed <= 3600 ? parsed : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private List<StarryListBoardDefinition> orderedBoards(List<String> enabled) {
    return runtime.registry().all().stream()
        .sorted(Comparator.comparingInt(board -> {
          int index = enabled.indexOf(board.id());
          return index < 0 ? enabled.size() + runtime.registry().all().indexOf(board) : index;
        }))
        .toList();
  }

  private StarryListDisplayProfile copy(StarryListDisplayProfile source, List<String> boards) {
    return new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        boards,
        source.rotationEnabled(),
        source.rotationIntervalSeconds()
    );
  }

  private void saveCustom(StarryListDisplayProfile profile) {
    runtime.state().setProfile(player.getUUID(), new StarryListDisplayProfile(
        StarryListDisplayProfile.Mode.CUSTOM,
        profile.boards(),
        profile.rotationEnabled(),
        profile.rotationIntervalSeconds()
    ));
  }

  private void applyAndRender() {
    runtime.display().update(player, true);
    render();
  }

  private String stateName(boolean enabled) {
    return text(enabled ? "starrylist.gui.state.enabled" : "starrylist.gui.state.disabled")
        .getString();
  }

  private String modeName(StarryListDisplayProfile.Mode mode) {
    return text("starrylist.gui.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)).getString();
  }

  private Component text(String key, Object... arguments) {
    return StarryListLangManager.getInstance().textFor(locale, key, arguments);
  }
}
