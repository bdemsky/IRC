@LATTICE("L<M,M<H")
@METHODDEFAULT("ML<MM,MM<MH,THISLOC=M,RETURNLOC=M")
public class test {

  @LOC("H")
  Foo foo;

  public static void main(@LOC("H") String args[]) {
    @LOC("H") test t = new test();

    SSJAVA: while (true) {
      t.memAllocTest();
    }

  }

  public void memAllocTest() {

	@Loc("MH") Foo fooH=new Foo();  
    for(int i=0;i<10;i++){
    	@Loc("MM") FooTemp=fooH;
    	fooM.v=50;
    }
    @Loc("ML") Foo fooL=fooM;
    fooL.v=100;
    
  }

}

@LATTICE("L<M,M<H,H<C,C*,M*")
@METHODDEFAULT("T")
class Foo {
  @LOC("H")
  Bar bar1;
  @LOC("H")
  Bar bar2;
  @LOC("H")
  int v;
}

@LATTICE("L<M,M<H,H<C,C*,M*")
@METHODDEFAULT("T")
class Bar {
  @LOC("M")
  int v1;
  @LOC("M")
  int v2;
}
