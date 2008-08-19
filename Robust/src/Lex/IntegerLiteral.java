package Lex;

import java_cup.runtime.Symbol;
import Parse.Sym;

class IntegerLiteral extends NumericLiteral {
  IntegerLiteral(int i) {
    this.val = new Integer(i);
  }

  Symbol token() {
    return new Symbol(Sym.INTEGER_LITERAL, val);
  }
}
