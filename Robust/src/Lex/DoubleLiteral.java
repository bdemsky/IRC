package Lex;

import java_cup.runtime.Symbol;
import Parse.Sym;

class DoubleLiteral extends NumericLiteral {
  DoubleLiteral(double d) {
    this.val = new Double(d);
  }

  Symbol token() {
    return new Symbol(Sym.FLOATING_POINT_LITERAL, val);
  }
}
