public class Foo {
  public Foo() {}
  public Foo f;
  public Foo g;
    public int a;
}

public class Test {

  static public void main( String[] args ) {

    Foo a = new Foo();
    Foo b = new Foo();
    Foo bbb = new Foo();
    
    bbb.f=new Foo();
    rblock r1 {
	
	a.f=new Foo();
	a.a=2;
	/*
	while(1==1){	
	    Foo yyy = b.f; 
	    rblock rr1{
		b.f=new Foo();
	    }
	    
	    rblock rr2{
		b.f=new Foo();
	    }
	    
	}    
	*/
    }
    Foo xxx = a.f;
    //xxx.a=100;
    xxx.f=new Foo();
    Foo zzz=xxx.f;
    zzz.a=100;

  }
   
  static void doSomething( Foo a, Foo b ) {

    a.g = new Foo();
    
    a.f.f = a.g;

    Foo f = doStuff( a, b );
  }   

  static Foo doStuff( Foo m, Foo n ) {
      
      m.f.g = n.f;
      return new Foo();

  }
}
