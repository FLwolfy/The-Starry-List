package com.flwolfy.starrylist.data.script;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;

/** Executes trusted JEXL Minecraft commands as the server console. */
public final class StarryListCommandExecutor {

  private final MinecraftServer server;

  public StarryListCommandExecutor(MinecraftServer server) {
    this.server = Objects.requireNonNull(server);
  }

  public int execute(String command) {
    if (server.isSameThread()) return executeOnServer(command);
    CompletableFuture<Integer> result = new CompletableFuture<>();
    server.execute(() -> {
      try {
        result.complete(executeOnServer(command));
      } catch (RuntimeException exception) {
        result.completeExceptionally(exception);
      }
    });
    return result.join();
  }

  private int executeOnServer(String command) {
    String normalized = Commands.trimOptionalPrefix(Objects.requireNonNull(command));
    CommandSourceStack source = server.createCommandSourceStack()
        .withMaximumPermission(PermissionSet.ALL_PERMISSIONS)
        .withSuppressedOutput();
    AtomicInteger result = new AtomicInteger();
    server.getCommands().performPrefixedCommand(
        source.withCallback((successful, value) -> {
          if (successful) result.set(value);
        }),
        normalized
    );
    return result.get();
  }
}
