package com.flwolfy.starrylist.board.script;

import groovy.lang.Closure;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.event.Event;

record StarryListScriptSubscription(
    String boardId,
    String key,
    Event<?> event,
    Class<?> callbackType,
    Method callbackMethod,
    Object inactiveResult,
    Closure<?> callback
) {

  String identity() {
    return boardId + "/" + key;
  }
}
