package com.flwolfy.starrylist.modmenu.entry.common;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Expanded subcategory whose entries can be replaced without rebuilding its screen. */
public final class StarryListRefreshableSubCategoryEntry extends StarryListSubCategoryEntry {

  private final Button refreshButton;
  private final int resetSlotWidth;

  /**
   * Creates a refreshable expanded subcategory.
   *
   * @param builder Cloth Config entry builder
   * @param title localized subcategory title
   * @param entries initial child entries
   * @param refresh refresh callback
   */
  public StarryListRefreshableSubCategoryEntry(
      ConfigEntryBuilder builder,
      Component title,
      List<AbstractConfigListEntry<?>> entries,
      Runnable refresh
  ) {
    super(builder, title, entries, true, false);
    refreshButton = Button.builder(
        Component.translatable("starrylist.config.boards.custom.refresh.button"),
        ignored -> refresh.run()
    ).bounds(0, 0, 0, 20).build();
    resetSlotWidth = net.minecraft.client.Minecraft.getInstance().font.width(
        builder.getResetButtonKey()
    ) + 6;
    refreshButton.setTooltip(Tooltip.create(Component.translatable(
        "starrylist.config.boards.custom.refresh.tooltip"
    )));
  }

  /**
   * Replaces child entries in place while preserving this subcategory instance.
   *
   * @param replacement replacement child entries
   */
  public void replaceEntries(List<AbstractConfigListEntry<?>> replacement) {
    setFocused(null);
    List<AbstractConfigListEntry<?>> entries = getValue();
    entries.clear();
    entries.addAll(replacement);
    rebuildReferences();
  }

  @Override
  public void extractRenderState(
      GuiGraphicsExtractor graphics,
      int index,
      int y,
      int x,
      int entryWidth,
      int entryHeight,
      int mouseX,
      int mouseY,
      boolean hovered,
      float delta
  ) {
    super.extractRenderState(
        graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta
    );
    refreshButton.active = isEditable();
    refreshButton.setX(StarryListControlLayout.valueX(x, entryWidth));
    refreshButton.setY(y);
    refreshButton.setWidth(StarryListControlLayout.valueWidth(resetSlotWidth));
    refreshButton.extractRenderState(graphics, mouseX, mouseY, delta);
  }

  @Override
  public List<? extends GuiEventListener> children() {
    List<GuiEventListener> children = new ArrayList<>(super.children());
    children.add(refreshButton);
    return children;
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    return refreshButton.mouseClicked(event, doubleClick)
        || super.mouseClicked(event, doubleClick);
  }

  @Override
  public List<? extends NarratableEntry> narratables() {
    List<NarratableEntry> entries = new ArrayList<>(super.narratables());
    entries.add(refreshButton);
    return entries;
  }
}
