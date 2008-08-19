package Lex;

import java_cup.runtime.Symbol;
import Parse.Sym;

class NullLiteral extends Literal {
  NullLiteral() {
  }

  Symbol token() {
    return new Symbol(Sym.NULL_LITERAL);
  }

  public String toString() {
    return "NullLiteral <null>";
  }
}
