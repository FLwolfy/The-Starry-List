package com.flwolfy.starrylist.scoreboard;

/**
 * Identifies one of the six fixed leaderboards and its vanilla scoreboard objective.
 *
 * @param id command and configuration identifier
 * @param objectiveName vanilla scoreboard objective name
 * @param displayName translated sidebar title
 */
public record StarryListBoardDefinition(
    String id,
    String objectiveName,
    String displayName
) {}
