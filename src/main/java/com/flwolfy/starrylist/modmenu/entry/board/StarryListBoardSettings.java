package com.flwolfy.starrylist.modmenu.entry.board;

/**
 * Editable loading and default-profile state for one board.
 *
 * @param loaded whether the board is loaded into gameplay
 * @param defaultEnabled whether the DEFAULT profile shows the board
 */
public record StarryListBoardSettings(boolean loaded, boolean defaultEnabled) {}
