package com.flwolfy.starrylist.data.script;

import org.apache.commons.jexl3.JexlArithmetic;

public final class StarryListScriptArithmetic extends JexlArithmetic {

  public StarryListScriptArithmetic() {
    super(true);
  }

  @Override
  public Object add(Object left, Object right) {
    if (left instanceof Number a && right instanceof Number b) {
      return a.doubleValue() + b.doubleValue();
    }
    return super.add(left, right);
  }

  @Override
  public Object subtract(Object left, Object right) {
    if (left instanceof Number a && right instanceof Number b) {
      return a.doubleValue() - b.doubleValue();
    }
    return super.subtract(left, right);
  }
}
