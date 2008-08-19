package Lex;

import java_cup.runtime.Symbol;
import Parse.Sym;

class FloatLiteral extends NumericLiteral {
  FloatLiteral(float f) {
    this.val = new Float(f);
  }

  Symbol token() {
    return new Symbol(Sym.FLOATING_POINT_LITERAL, val);
  }
}
