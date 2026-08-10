package com.flwolfy.starrylist.modmenu;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

/** Read-only Cloth Config entry that renders the current unsaved configuration summary. */
final class StarryListSummaryEntry extends AbstractConfigListEntry<String> {

  private final Supplier<Component> message;
  private final Button summary = Button.builder(Component.empty(), ignored -> {}).build();

  StarryListSummaryEntry(Supplier<Component> message) {
    super(Component.translatable("starrylist.config.all"), false);
    this.message = message;
    summary.active = false;
  }

  /** {@inheritDoc} */
  @Override
  public String getValue() {
    return "";
  }

  /** {@inheritDoc} */
  @Override
  public Optional<String> getDefaultValue() {
    return Optional.empty();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEdited() {
    return false;
  }

  /** {@inheritDoc} */
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
      float tickDelta
  ) {
    super.extractRenderState(
        graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta
    );
    summary.setRectangle(entryWidth, 20, x, y);
    summary.setMessage(message.get());
    summary.extractRenderState(graphics, mouseX, mouseY, tickDelta);
  }

  /** {@inheritDoc} */
  @Override
  public List<? extends GuiEventListener> children() {
    return List.of(summary);
  }

  /** {@inheritDoc} */
  @Override
  public List<? extends NarratableEntry> narratables() {
    return List.of(summary);
  }
}
