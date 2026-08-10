package com.flwolfy.starrylist.data.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Provides unrestricted native shell access to trusted configuration scripts. */
public final class StarryListShellExecutor {

  private static final Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();

  private StarryListShellExecutor() {}

  public static ShellResult execute(String command) {
    Process process;
    try {
      process = new ProcessBuilder(shellCommand(command)).start();
      PROCESSES.add(process);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to start shell command", exception);
    }
    CompletableFuture<String> stdout = readAsync(process.getInputStream());
    CompletableFuture<String> stderr = readAsync(process.getErrorStream());
    try {
      int exitCode = process.waitFor();
      return new ShellResult(exitCode, stdout.join(), stderr.join());
    } catch (InterruptedException exception) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Shell command was interrupted", exception);
    } finally {
      PROCESSES.remove(process);
    }
  }

  public static String run(String command) {
    ShellResult result = execute(command);
    if (result.exitCode() != 0) {
      throw new IllegalStateException(
          "Shell command exited with code " + result.exitCode() + ": " + result.stderr().strip()
      );
    }
    return result.stdout();
  }

  public static Integer runInt(String command) {
    return parseInt(run(command));
  }

  public static void shutdown() {
    PROCESSES.forEach(Process::destroyForcibly);
    PROCESSES.clear();
  }

  private static Integer parseInt(String value) {
    try {
      return Integer.valueOf(value.strip());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Shell output is not one valid integer: " + value.strip(), exception);
    }
  }

  private static List<String> shellCommand(String command) {
    return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
        ? List.of("cmd.exe", "/c", command)
        : List.of("/bin/sh", "-c", command);
  }

  private static CompletableFuture<String> readAsync(java.io.InputStream stream) {
    return CompletableFuture.supplyAsync(() -> {
      try (stream) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw new IllegalStateException("Failed to read shell output", exception);
      }
    }, task -> Thread.ofVirtual().start(task));
  }

  public record ShellResult(int exitCode, String stdout, String stderr) {
    public Integer stdoutInt() {
      return parseInt(stdout);
    }
  }
}
