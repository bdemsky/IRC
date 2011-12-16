@LATTICE("L<M,M<H,H<C,C*,M*")
@METHODDEFAULT("L<M,M<H,H<IN,THISLOC=M,RETURNLOC=M")
public class test {

  @LOC("H")
  Foo foo;
  @LOC("H")
  Foo f1;

  public static void main(@LOC("H") String args[]) {
    @LOC("H") test t = new test();

    SSJAVA: while (true) {
      t.doDelegationTest();
      t.doDelegationTest2();
    }

  }

  public void doDelegationTest() {

    @LOC("H") Foo ownedFoo = new Foo();
    delegate(ownedFoo);
    // ERROR::
    // System.out.println(ownedFoo.v);

    changeLocationTest(ownedFoo);
  }

  public void delegate(@DELEGATE @LOC("IN") Foo foo) {
    @LOC("M") Foo lowerFoo = foo; // allowed to lower

    // create a temp var to walk the subtree
    @LOC("L") Bar tempVar = lowerFoo.bar1;
    lowerFoo.bar1 = null;

  }

  public void changeLocationTest(@DELEGATE @LOC("IN") Foo foo) {

    // local variable refers to the sub object the foo
    @LOC("M") Bar tempVar = foo.bar1;

    // ERROR: not allowed to lower if there is a local var alias to the subtree
    // of the foo
    // @LOC("M") Foo lowerFoo = foo;

  }

  public void doDelegationTest2() {

    @LOC("H") Foo ownedFoo = new Foo();
    notDelegate(ownedFoo);

  }

  public Foo notDelegate(@LOC("IN") Foo notownedFoo) {

    // ERROR: not allowed to lower
    // @LOC("M") Foo lowerFoo = notownedFoo;

    // ERROR: now allowed to return not-owned-ref
    return notownedFoo;

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
