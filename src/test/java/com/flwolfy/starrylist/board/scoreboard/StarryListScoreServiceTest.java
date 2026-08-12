package com.flwolfy.starrylist.board.scoreboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistry;
import com.flwolfy.starrylist.data.config.StarryListBlacklist;
import com.flwolfy.starrylist.data.state.StarryListState;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public final class StarryListScoreServiceTest {

  @BeforeAll
  static void bootstrapMinecraft() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @Test
  void setAutomaticReplacesVisibleScore() {
    UUID playerId = UUID.randomUUID();
    MinecraftServer server = mock(MinecraftServer.class);
    ServerScoreboard scoreboard = mock(ServerScoreboard.class);
    StarryListBoardRegistry registry = mock(StarryListBoardRegistry.class);
    StarryListState state = mock(StarryListState.class);
    StarryListBlacklist blacklist = mock(StarryListBlacklist.class);
    StarryListBoard board = mock(StarryListBoard.class);
    Objective objective = mock(Objective.class);
    ScoreAccess score = mock(ScoreAccess.class);
    ServerPlayer player = player(playerId, "Alice");

    when(server.getScoreboard()).thenReturn(scoreboard);
    when(registry.get("balance")).thenReturn(Optional.of(board));
    when(board.objectiveName()).thenReturn("sl_balance");
    when(scoreboard.getObjective("sl_balance")).thenReturn(objective);
    when(scoreboard.getOrCreatePlayerScore(any(ScoreHolder.class), eq(objective)))
        .thenReturn(score);

    StarryListScoreService service = new StarryListScoreService(
        server, () -> registry, state, blacklist
    );

    assertEquals(-12, service.setAutomatic("balance", player, -12));
    verify(score).set(-12);
    verify(state).removeArchivedScore(playerId, "balance");
  }

  @Test
  void setAutomaticLeavesBlacklistedScoreUnchanged() {
    UUID playerId = UUID.randomUUID();
    MinecraftServer server = mock(MinecraftServer.class);
    StarryListBoardRegistry registry = mock(StarryListBoardRegistry.class);
    StarryListState state = mock(StarryListState.class);
    StarryListBlacklist blacklist = mock(StarryListBlacklist.class);
    ServerPlayer player = player(playerId, "BlockedPlayer");

    when(blacklist.matches("BlockedPlayer")).thenReturn(true);
    when(state.archivedScore(playerId, "balance")).thenReturn(37);

    StarryListScoreService service = new StarryListScoreService(
        server, () -> registry, state, blacklist
    );

    assertEquals(37, service.setAutomatic("balance", player, 99));
    verify(state, never()).archiveScore(any(), any(), any(), any(Integer.class));
    verify(state, never()).removeArchivedScore(any(), any());
    verifyNoInteractions(server, registry);
  }

  @Test
  void addAutomaticLeavesBlacklistedScoreUnchanged() {
    UUID playerId = UUID.randomUUID();
    MinecraftServer server = mock(MinecraftServer.class);
    StarryListBoardRegistry registry = mock(StarryListBoardRegistry.class);
    StarryListState state = mock(StarryListState.class);
    StarryListBlacklist blacklist = mock(StarryListBlacklist.class);
    ServerPlayer player = player(playerId, "BlockedPlayer");

    when(blacklist.matches("BlockedPlayer")).thenReturn(true);
    when(state.archivedScore(playerId, "counter")).thenReturn(37);

    StarryListScoreService service = new StarryListScoreService(
        server, () -> registry, state, blacklist
    );

    assertEquals(37, service.addAutomatic("counter", player, 5));
    verify(state, never()).archiveScore(any(), any(), any(), any(Integer.class));
    verifyNoInteractions(server, registry);
  }

  @Test
  void accumulateAutomaticDoesNotMutateBlacklistedState() {
    UUID playerId = UUID.randomUUID();
    MinecraftServer server = mock(MinecraftServer.class);
    StarryListBoardRegistry registry = mock(StarryListBoardRegistry.class);
    StarryListState state = mock(StarryListState.class);
    StarryListBlacklist blacklist = mock(StarryListBlacklist.class);
    ServerPlayer player = player(playerId, "BlockedPlayer");

    when(blacklist.matches("BlockedPlayer")).thenReturn(true);
    StarryListScoreService service = new StarryListScoreService(
        server, () -> registry, state, blacklist
    );

    assertEquals(0, service.accumulateAutomatic("travel", player, "remainder", 75, 100));
    verify(state, never()).boardState(any(), any());
    verifyNoInteractions(server, registry);
  }

  private static ServerPlayer player(UUID playerId, String name) {
    ServerPlayer player = mock(ServerPlayer.class);
    GameProfile profile = mock(GameProfile.class);
    when(player.getUUID()).thenReturn(playerId);
    when(player.getGameProfile()).thenReturn(profile);
    when(profile.name()).thenReturn(name);
    return player;
  }
}
