package com.flwolfy.starrylist.modmenu.entry.blacklist;

import com.flwolfy.starrylist.modmenu.model.StarryListConfigEditorModel;
import java.util.ArrayList;
import java.util.List;

/** Synchronizes independent blacklist widget trees through the shared configuration model. */
public final class StarryListBlacklistEditorModel {

  private static final String PATH = "blacklist.playerNamePatterns";

  private final StarryListConfigEditorModel model;
  private final List<StarryListBlacklistListEntry> editors = new ArrayList<>();

  /**
   * Creates a blacklist view model.
   *
   * @param model shared configuration editor model
   */
  public StarryListBlacklistEditorModel(StarryListConfigEditorModel model) {
    this.model = model;
  }

  /**
   * Registers an independent blacklist widget tree.
   *
   * @param editor blacklist entry
   */
  public void register(StarryListBlacklistListEntry editor) {
    editors.add(editor);
  }

  /**
   * Publishes one entry's latest values to the shared model and sibling views.
   *
   * @param source publishing entry
   * @param replacement replacement expressions
   */
  public void publish(
      StarryListBlacklistListEntry source,
      List<String> replacement
  ) {
    List<String> copied = List.copyOf(replacement);
    if (copied.equals(values())) {
      return;
    }
    model.set(PATH, copied);
    for (StarryListBlacklistListEntry editor : editors) {
      if (editor != source) {
        editor.receive(copied);
      }
    }
  }

  /** Publishes values supplied by Cloth Config's save callback. */
  void publish(List<String> replacement) {
    publish(null, replacement);
  }

  /**
   * Returns current blacklist expressions.
   *
   * @return immutable expression list
   */
  @SuppressWarnings("unchecked")
  public List<String> values() {
    return (List<String>) model.get(PATH, List.class);
  }

  /**
   * Publishes pending edits from every registered widget tree.
   */
  public void flush() {
    editors.forEach(StarryListBlacklistListEntry::publishPendingChanges);
  }
}
