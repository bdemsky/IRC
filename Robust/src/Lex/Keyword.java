package Lex;

import java.util.Hashtable;
import java_cup.runtime.Symbol;
import Parse.Sym;

class Keyword extends Token {
  String keyword;
  Keyword(String s) {
    keyword = s;
  }

  Symbol token() {
    Integer i = (Integer) key_table.get(keyword);
    return new Symbol(i.intValue());
  }
  public String toString() {
    return "Keyword <"+keyword+">";
  }

  static private final Hashtable key_table = new Hashtable();
  static {
    key_table.put("abstract", new Integer(Sym.ABSTRACT));
    key_table.put("assert", new Integer(Sym.ASSERT));
    key_table.put("boolean", new Integer(Sym.BOOLEAN));
    key_table.put("break", new Integer(Sym.BREAK));
    key_table.put("byte", new Integer(Sym.BYTE));
    key_table.put("case", new Integer(Sym.CASE));
    key_table.put("catch", new Integer(Sym.CATCH));
    key_table.put("char", new Integer(Sym.CHAR));
    key_table.put("class", new Integer(Sym.CLASS));
    key_table.put("const", new Integer(Sym.CONST));
    key_table.put("continue", new Integer(Sym.CONTINUE));
    key_table.put("default", new Integer(Sym.DEFAULT));
    key_table.put("do", new Integer(Sym.DO));
    key_table.put("double", new Integer(Sym.DOUBLE));
    key_table.put("else", new Integer(Sym.ELSE));
    key_table.put("enum", new Integer(Sym.ENUM));
    key_table.put("extends", new Integer(Sym.EXTENDS));
    key_table.put("final", new Integer(Sym.FINAL));
    key_table.put("finally", new Integer(Sym.FINALLY));
    key_table.put("float", new Integer(Sym.FLOAT));
    key_table.put("for", new Integer(Sym.FOR));
    key_table.put("genreach", new Integer(Sym.GENREACH));
    key_table.put("gendefreach", new Integer(Sym.GEN_DEF_REACH));
    key_table.put("goto", new Integer(Sym.GOTO));
    key_table.put("if", new Integer(Sym.IF));
    key_table.put("import", new Integer(Sym.IMPORT));
    key_table.put("instanceof", new Integer(Sym.INSTANCEOF));
    key_table.put("int", new Integer(Sym.INT));
    key_table.put("isavailable", new Integer(Sym.ISAVAILABLE));
    key_table.put("long", new Integer(Sym.LONG));
    key_table.put("native", new Integer(Sym.NATIVE));
    key_table.put("new", new Integer(Sym.NEW));
    key_table.put("newflag", new Integer(Sym.NEWFLAG));
    key_table.put("package", new Integer(Sym.PACKAGE));
    key_table.put("private", new Integer(Sym.PRIVATE));
    key_table.put("protected", new Integer(Sym.PROTECTED));
    key_table.put("public", new Integer(Sym.PUBLIC));
    key_table.put("return", new Integer(Sym.RETURN));
    key_table.put("short", new Integer(Sym.SHORT));
    key_table.put("static", new Integer(Sym.STATIC));
    key_table.put("strictfp", new Integer(Sym.STRICTFP));
    key_table.put("super", new Integer(Sym.SUPER));
    key_table.put("switch", new Integer(Sym.SWITCH));
    key_table.put("synchronized", new Integer(Sym.SYNCHRONIZED));
    key_table.put("this", new Integer(Sym.THIS));
    key_table.put("throw", new Integer(Sym.THROW));
    key_table.put("throws", new Integer(Sym.THROWS));
    key_table.put("transient", new Integer(Sym.TRANSIENT));
    key_table.put("try", new Integer(Sym.TRY));
    key_table.put("void", new Integer(Sym.VOID));
    key_table.put("volatile", new Integer(Sym.VOLATILE));
    key_table.put("while", new Integer(Sym.WHILE));
    //Keywords for failure aware computation
    key_table.put("flag", new Integer(Sym.FLAG));
    key_table.put("external", new Integer(Sym.EXTERNAL));
    key_table.put("optional", new Integer(Sym.OPTIONAL));
    key_table.put("tag", new Integer(Sym.TAG));
    key_table.put("task", new Integer(Sym.TASK));
    key_table.put("taskexit", new Integer(Sym.TASKEXIT));
    //Keywords for transactions
    key_table.put("atomic", new Integer(Sym.ATOMIC));
    key_table.put("global", new Integer(Sym.GLOBAL));
    key_table.put("scratch", new Integer(Sym.SCRATCH));
    //Keywords for hacking prefetch calls in java
    key_table.put("getoffset", new Integer(Sym.GETOFFSET));
    //Keywords for disjointness in Java
    key_table.put("disjoint", new Integer(Sym.DISJOINT));
    //Keywords for coarse-grain parallelization
    key_table.put("sese", new Integer(Sym.SESE));
    key_table.put("rblock", new Integer(Sym.RBLOCK));
    // Keywords for interface of mgc
    key_table.put("interface", new Integer(Sym.INTERFACE));
    key_table.put("implements", new Integer(Sym.IMPLEMENTS));
    // Keywords for Self-Stabilizing Java
    key_table.put("locdef", new Integer(Sym.LOCDEF));
  }
}
