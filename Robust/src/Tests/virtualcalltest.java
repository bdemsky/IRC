class A {
    A() {
	;
    }

   void foo(int x) {
       System.printInt(x);
       System.printString("\n");
   }
}


class B extends A {
    B() {
	;
    }

   void foo(int x) {
   }
}


class C extends A {
    C() {
	;
    }

   void foo(int x) {
   }
}

public class virtualcalltest {
    public static void main() {

	A a=null;
	B b=new B();
	C c=new C();
	for(int i=0;i<1000000;i++) {
		if (i%2==0)
			a=b;
		else
			a=c;

		a.foo(20);
	}
    }
}
