//@LATTICE("data<proc,proc<c,c<in,in*,c*,proc*,data*")
@LATTICE("V<C, V<O")
@DEFAULTMETHOD("O<V,V<I,V<C,THIS=O")
public class String {

  @LOC("V") char value[];
  @LOC("C") int count;
  @LOC("O") int offset;
  @LOC("V") private int cachedHashcode;

  private String() {
  }
  
  
}
