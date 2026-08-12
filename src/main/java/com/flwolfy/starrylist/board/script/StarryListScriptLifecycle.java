package com.flwolfy.starrylist.board.script;

record StarryListScriptLifecycle(
    String boardId,
    Runnable activated,
    Runnable deactivated
) {
}
