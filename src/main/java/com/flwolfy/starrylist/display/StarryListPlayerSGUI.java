package com.flwolfy.starrylist.display;

import com.flwolfy.starrylist.StarryListRuntime;
import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.data.lang.StarryListLangManager;
import com.flwolfy.starrylist.data.state.StarryListDisplayProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/** Provides the paginated board settings menu opened by {@code /starry}. */
public final class StarryListPlayerSGUI extends SimpleGui {

  private static final int BOARD_START_SLOT = 19;
  private static final int BOARDS_PER_PAGE = 7;

  private final StarryListRuntime runtime;
  private int page;

  private StarryListPlayerSGUI(ServerPlayer player, StarryListRuntime runtime) {
    super(MenuType.GENERIC_9x5, player, false);
    this.runtime = runtime;
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
    new StarryListPlayerSGUI(player, runtime).open();
  }

  private void render() {
    GuiElementBuilder filler = new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
        .setName(Component.empty());
    for (int slot = 0; slot < getVirtualSize(); slot++) {
      setSlot(slot, filler.build());
    }

    GuiElementBuilder edge = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
        .setName(Component.empty());
    for (int slot = 0; slot < 9; slot++) {
      setSlot(slot, edge.build());
    }
    for (int slot = 36; slot < 45; slot++) {
      setSlot(slot, edge.build());
    }

    StarryListDisplayProfile saved = runtime.state().profile(player.getUUID());
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> enabled = new ArrayList<>(editable.boards());

    setSlot(4, new GuiElementBuilder(Items.NETHER_STAR)
        .setName(text("starrylist.gui.status").copy().withStyle(ChatFormatting.GOLD))
        .addLoreLine(text("starrylist.gui.status.mode", modeName(saved.mode())))
        .addLoreLine(text(
            "starrylist.gui.status.visible",
            runtime.display().effective(player.getUUID()).hidden()
                ? text("starrylist.gui.state.hidden").getString()
                : text("starrylist.gui.state.visible").getString()
        )).build());

    int pageCount = Math.max(1, (runtime.registry().all().size() + BOARDS_PER_PAGE - 1)
        / BOARDS_PER_PAGE);
    page = Math.floorMod(page, pageCount);
    int firstBoard = page * BOARDS_PER_PAGE;
    int lastBoard = Math.min(firstBoard + BOARDS_PER_PAGE, runtime.registry().all().size());
    for (int index = firstBoard; index < lastBoard; index++) {
      StarryListBoard board = runtime.registry().all().get(index);
      boolean selected = enabled.contains(board.id());
      setSlot(BOARD_START_SLOT + index - firstBoard, boardButton(board, selected));
    }

    if (pageCount > 1) {
      setSlot(18, new GuiElementBuilder(Items.ARROW)
          .setName(text("starrylist.gui.page.previous").copy().withStyle(ChatFormatting.AQUA))
          .addLoreLine(text("starrylist.gui.page.value", page + 1, pageCount))
          .setCallback(() -> changePage(-1, pageCount)).build());
      setSlot(26, new GuiElementBuilder(Items.ARROW)
          .setName(text("starrylist.gui.page.next").copy().withStyle(ChatFormatting.AQUA))
          .addLoreLine(text("starrylist.gui.page.value", page + 1, pageCount))
          .setCallback(() -> changePage(1, pageCount)).build());
    }

    setSlot(36, new GuiElementBuilder(Items.CLOCK)
        .setName(text("starrylist.gui.use_default").copy().withStyle(ChatFormatting.AQUA))
        .addLoreLine(text("starrylist.gui.use_default.description"))
        .setCallback(() -> {
          runtime.state().resetProfile(player.getUUID());
          applyAndRender();
        }).build());

    boolean hidden = runtime.display().hiddenBySetting(player.getUUID());
    setSlot(38, new GuiElementBuilder(hidden ? Items.LIME_DYE : Items.GRAY_DYE)
        .setName(text(hidden ? "starrylist.gui.show" : "starrylist.gui.hide").copy()
            .withStyle(hidden ? ChatFormatting.GREEN : ChatFormatting.GRAY))
        .setCallback(() -> {
          if (hidden) {
            saveCustom(runtime.display().editableProfile(player.getUUID()));
          } else {
            runtime.state().setProfile(player.getUUID(), new StarryListDisplayProfile(
                StarryListDisplayProfile.Mode.HIDDEN,
                editable.boards(),
                editable.rotationEnabled(),
                editable.rotationIntervalSeconds()
            ));
          }
          applyAndRender();
        }).build());

    setSlot(40, new GuiElementBuilder(editable.rotationEnabled() ? Items.LIME_DYE : Items.RED_DYE)
        .setName(text("starrylist.gui.rotation").copy().withStyle(ChatFormatting.YELLOW))
        .addLoreLine(text(
            "starrylist.gui.rotation.state",
            stateName(editable.rotationEnabled())
        ))
        .setCallback(() -> {
          saveSettings(new StarryListDisplayProfile(
              StarryListDisplayProfile.Mode.CUSTOM,
              editable.boards(),
              !editable.rotationEnabled(),
              editable.rotationIntervalSeconds()
          ));
          applyAndRender();
        }).build());

    setSlot(42, new GuiElementBuilder(Items.REPEATER)
        .setName(text("starrylist.gui.interval").copy().withStyle(ChatFormatting.YELLOW))
        .addLoreLine(text("starrylist.gui.interval.value", editable.rotationIntervalSeconds()))
        .addLoreLine(text("starrylist.gui.interval.description"))
        .setCallback(() -> openIntervalInput(editable)).build());

    setSlot(44, new GuiElementBuilder(Items.BARRIER)
        .setName(text("starrylist.gui.close").copy().withStyle(ChatFormatting.RED))
        .setCallback(() -> close()).build());
  }

  private GuiElementBuilder boardButton(
      StarryListBoard board,
    boolean selected
  ) {
    GuiElementBuilder builder = new GuiElementBuilder(board.iconForGui())
        .setName(board.displayName().copy().withStyle(
            selected ? ChatFormatting.GREEN : ChatFormatting.GRAY
        ));
    for (Component line : board.lore()) {
      builder.addLoreLine(line.copy().withStyle(ChatFormatting.GRAY));
    }
    builder.addLoreLine(text(
            selected ? "starrylist.gui.board.enabled" : "starrylist.gui.board.disabled"
        ));
    if (selected) {
      builder.glow();
    }
    return builder.addLoreLine(text("starrylist.gui.board.toggle"))
        .setCallback(() -> toggle(board.id()));
  }

  private void changePage(int offset, int pageCount) {
    page = Math.floorMod(page + offset, pageCount);
    render();
  }

  private void toggle(String boardId) {
    StarryListDisplayProfile editable = runtime.display().editableProfile(player.getUUID());
    List<String> boards = new ArrayList<>(editable.boards());
    if (boards.contains(boardId)) {
      boards.remove(boardId);
    } else {
      boards.add(boardId);
    }

    saveSettings(copy(editable, boards));
    applyAndRender();
  }

  private void openIntervalInput(StarryListDisplayProfile editable) {
    AnvilInputGui input = new AnvilInputGui(player, false) {
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
    input.setSlot(1, new GuiElementBuilder(Items.BARRIER)
        .setName(text("starrylist.gui.interval.cancel").copy().withStyle(ChatFormatting.RED))
        .setCallback(() -> {
          input.close();
          StarryListPlayerSGUI.open(player, runtime);
        }).build());
    Integer seconds = parseInterval(value);
    GuiElementBuilder result = new GuiElementBuilder(seconds == null ? Items.BARRIER : Items.LIME_DYE)
        .setName(text(seconds == null
            ? "starrylist.gui.interval.invalid" : "starrylist.gui.interval.confirm"));
    if (seconds != null) {
      result.setCallback(() -> {
        saveSettings(new StarryListDisplayProfile(
            StarryListDisplayProfile.Mode.CUSTOM,
            editable.boards(),
            editable.rotationEnabled(),
            seconds
        ));
        runtime.display().update(player, true);
        playSuccessSound();
        StarryListPlayerSGUI.open(player, runtime);
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

  private void saveSettings(StarryListDisplayProfile profile) {
    StarryListDisplayProfile.Mode mode = runtime.state().profile(player.getUUID()).mode()
        == StarryListDisplayProfile.Mode.HIDDEN
            ? StarryListDisplayProfile.Mode.HIDDEN
            : StarryListDisplayProfile.Mode.CUSTOM;
    runtime.state().setProfile(player.getUUID(), new StarryListDisplayProfile(
        mode,
        profile.boards(),
        profile.rotationEnabled(),
        profile.rotationIntervalSeconds()
    ));
  }

  private void applyAndRender() {
    runtime.display().update(player, true);
    playSuccessSound();
    render();
  }

  private void playSuccessSound() {
    player.connection.send(new ClientboundSoundEntityPacket(
        Holder.direct(SoundEvents.EXPERIENCE_ORB_PICKUP),
        SoundSource.PLAYERS,
        player,
        0.8F,
        1.35F,
        player.getRandom().nextLong()
    ));
  }

  private String stateName(boolean enabled) {
    return text(enabled ? "starrylist.gui.state.enabled" : "starrylist.gui.state.disabled")
        .getString();
  }

  private String modeName(StarryListDisplayProfile.Mode mode) {
    return text("starrylist.gui.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)).getString();
  }

  private Component text(String key, Object... arguments) {
    return StarryListLangManager.getInstance().text(key, arguments);
  }
}
