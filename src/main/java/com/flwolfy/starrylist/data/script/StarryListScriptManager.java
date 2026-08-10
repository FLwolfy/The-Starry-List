package com.flwolfy.starrylist.data.script;

import com.flwolfy.starrylist.data.script.context.StarryListBlock;
import com.flwolfy.starrylist.data.script.context.StarryListBoard;
import com.flwolfy.starrylist.data.script.context.StarryListContext;
import com.flwolfy.starrylist.data.script.context.StarryListEntity;
import com.flwolfy.starrylist.data.script.context.StarryListItem;
import com.flwolfy.starrylist.data.script.context.StarryListPlayer;
import com.flwolfy.starrylist.data.script.context.StarryListPosition;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.JexlScript;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;

/** Compiles and evaluates trusted custom leaderboard scripts. */
public final class StarryListScriptManager {

  private static final StarryListScriptManager INSTANCE = new StarryListScriptManager();

  private final JexlEngine engine;
  private final Map<String, JexlScript> cache = new ConcurrentHashMap<>();

  private StarryListScriptManager() {
    engine = new JexlBuilder()
        .strict(true)
        .silent(false)
        .arithmetic(new StarryListScriptArithmetic())
        .permissions(new JexlPermissions.ClassPermissions(
            JexlPermissions.SECURE,
            includeNestMembers(
                StarryListBlock.class,
                StarryListBoard.class,
                StarryListContext.class,
                StarryListEntity.class,
                StarryListItem.class,
                StarryListPlayer.class,
                StarryListPosition.class,
                StarryListShellExecutor.class,
                StarryListCommandExecutor.class,
                Math.class
            )
        ))
        .namespaces(Map.of("math", Math.class, "shell", StarryListShellExecutor.class))
        .create();
  }

  public static StarryListScriptManager getInstance() {
    return INSTANCE;
  }

  public boolean validate(StarryListScript script) {
    try {
      compile(script);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  public int evaluate(
      StarryListScript script,
      Map<String, ?> namespaces,
      Map<String, ?> arguments
  ) {
    ScriptContext context = new ScriptContext(namespaces);
    arguments.forEach(context::set);
    Object result = compile(script).execute(context);
    if (!(result instanceof Number number)) {
      throw new IllegalArgumentException("Expected an integer script result");
    }
    double value = number.doubleValue();
    if (!Double.isFinite(value) || value != Math.rint(value)
        || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Script result is outside the 32-bit integer range");
    }
    return (int) value;
  }

  public void clearCache() {
    cache.clear();
  }

  private JexlScript compile(StarryListScript script) {
    return cache.computeIfAbsent(script.source(), engine::createScript);
  }

  private static Class<?>[] includeNestMembers(Class<?>... roots) {
    return Arrays.stream(roots)
        .flatMap(root -> Arrays.stream(root.getNestMembers()))
        .distinct()
        .toArray(Class<?>[]::new);
  }

  private static final class ScriptContext extends MapContext
      implements JexlContext.NamespaceResolver {

    private final Map<String, ?> namespaces;

    private ScriptContext(Map<String, ?> namespaces) {
      this.namespaces = Map.copyOf(namespaces);
    }

    @Override
    public Object resolveNamespace(String name) {
      return namespaces.get(name);
    }
  }
}
