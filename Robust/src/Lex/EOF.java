package Lex;

import java_cup.runtime.Symbol;
import Parse.Sym;

class EOF extends Token {
  EOF() {
  }
  Symbol token() {
    return new Symbol(Sym.EOF);
  }
  public String toString() {
    return "EOF";
  }
}
