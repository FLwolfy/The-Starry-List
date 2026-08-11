package com.flwolfy.starrylist.board.leaderboard;

import com.flwolfy.starrylist.board.base.StarryListBoard;
import com.flwolfy.starrylist.board.base.StarryListBoardRegistrar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;

/** Counts successful player block placements from vanilla block-place game events. */
public final class PlacingBoard extends StarryListBoard {

  private final Map<UUID, ListenerBinding> listeners = new HashMap<>();

  @Override
  public String id() {
    return "placing";
  }

  @Override
  public String objectiveName() {
    return "sl_placing";
  }

  @Override
  public int order() {
    return 1;
  }

  @Override
  public ItemStack icon() {
    return Items.BRICKS.getDefaultInstance();
  }

  @Override
  public void register(StarryListBoardRegistrar registrar) {
    registrar.onActiveStateChanged(
        () -> registrar.server().getPlayerList().getPlayers()
            .forEach(player -> attach(player, registrar)),
        this::detachAll
    );
    registrar.listen(
        "player_join",
        ServerPlayerEvents.JOIN,
        player -> attach(player, registrar)
    );
    registrar.listen("player_leave", ServerPlayerEvents.LEAVE, this::detach);

    registrar.listen(
        "player_respawn",
        ServerPlayerEvents.AFTER_RESPAWN,
        (oldPlayer, newPlayer, alive) -> {
          detach(oldPlayer);
          attach(newPlayer, registrar);
        }
    );

    registrar.listen(
        "server_tick",
        ServerTickEvents.END_SERVER_TICK,
        server -> server.getPlayerList().getPlayers().forEach(player -> update(player, registrar))
    );
  }

  private void attach(ServerPlayer player, StarryListBoardRegistrar registrar) {
    detach(player);

    ServerLevel level = player.level();
    DynamicGameEventListener<PlacementListener> listener = new DynamicGameEventListener<>(
        new PlacementListener(player, registrar)
    );
    listener.add(level);
    listeners.put(player.getUUID(), new ListenerBinding(level, listener));
  }

  private void detach(ServerPlayer player) {
    ListenerBinding binding = listeners.remove(player.getUUID());
    if (binding != null) {
      binding.listener().remove(binding.level());
    }
  }

  private void detachAll() {
    listeners.values().forEach(binding -> binding.listener().remove(binding.level()));
    listeners.clear();
  }

  private void update(ServerPlayer player, StarryListBoardRegistrar registrar) {
    ListenerBinding binding = listeners.get(player.getUUID());
    if (binding == null || binding.listener().getListener().player != player
        || binding.level() != player.level()) {
      attach(player, registrar);
      return;
    }

    binding.listener().move(binding.level());
  }

  private record ListenerBinding(
      ServerLevel level,
      DynamicGameEventListener<PlacementListener> listener
  ) {}

  private static final class PlacementListener implements GameEventListener {
    private final ServerPlayer player;
    private final StarryListBoardRegistrar registrar;
    private final EntityPositionSource position;

    private PlacementListener(ServerPlayer player, StarryListBoardRegistrar registrar) {
      this.player = player;
      this.registrar = registrar;
      this.position = new EntityPositionSource(player, 0.0F);
    }

    @Override
    public EntityPositionSource getListenerSource() {
      return position;
    }

    @Override
    public int getListenerRadius() {
      return GameEvent.DEFAULT_NOTIFICATION_RADIUS;
    }

    @Override
    public boolean handleGameEvent(
        ServerLevel level,
        Holder<GameEvent> event,
        GameEvent.Context context,
        Vec3 position
    ) {
      if (event.value() != GameEvent.BLOCK_PLACE.value()
          || context.sourceEntity() != player) {
        return false;
      }

      registrar.addAutomatic(player, 1);
      return true;
    }
  }
}
