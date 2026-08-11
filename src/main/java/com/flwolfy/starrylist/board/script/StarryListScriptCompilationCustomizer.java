package com.flwolfy.starrylist.board.script;

import net.fabricmc.fabric.api.event.Event;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

final class StarryListScriptCompilationCustomizer extends CompilationCustomizer {

  private static final ClassNode EVENT_TYPE = ClassHelper.make(Event.class);

  StarryListScriptCompilationCustomizer() {
    super(CompilePhase.INSTRUCTION_SELECTION);
  }

  @Override
  public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
    classNode.visitContents(new ClassCodeVisitorSupport() {
      @Override
      protected SourceUnit getSourceUnit() {
        return source;
      }

      @Override
      public void visitMethodCallExpression(MethodCallExpression expression) {
        if (isDirectEventRegister(expression)) {
          addError(
              "Fabric Event.register() is not reload-safe; use registrar.listen()",
              expression
          );
        }

        super.visitMethodCallExpression(expression);
      }
    });
  }

  private static boolean isDirectEventRegister(MethodCallExpression expression) {
    if (!"register".equals(expression.getMethodAsString())) {
      return false;
    }

    MethodNode target = expression.getMethodTarget();
    if (target != null && isEventType(target.getDeclaringClass())) {
      return true;
    }

    ClassNode receiver = expression.getObjectExpression().getType();
    return isEventType(receiver)
        || target == null && (receiver == null
            || ClassHelper.isDynamicTyped(receiver)
            || ClassHelper.isObjectType(receiver));
  }

  private static boolean isEventType(ClassNode type) {
    if (type == null) {
      return false;
    }

    ClassNode resolved = type.redirect();
    return resolved.equals(EVENT_TYPE) || resolved.isDerivedFrom(EVENT_TYPE);
  }
}
