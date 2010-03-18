public class Foo {
  public Foo() {}
  public Foo f;
}

public class Test {

    
    static public void main( String[] args ) {
	Foo a=getAFoo();
	f1(a);
    }
    
    static public void f1( Foo c ) {
	c.f = getAFoo();
    }
    
    static public Foo getAFoo(){
	return disjoint NEW new Foo();
    } 
  
}
