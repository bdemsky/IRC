public class test{
    locdef{
	F<D,F<E,D<B,E<C,B<A,C<A,A<T,T<U
	//C<B,B<A,F<E,E<D,A<T,D<T
    }

    @A int a;
    @B int b;
    @C int c;
    @D int d;
    @E int e;
    @T foo f;
    @T bar bar;
    @U foo u;

    public static void main (String args[]){
	@T test t=new test();
	t.doit();
    }

    public void doit(){
	//f=d+e;

	@delta("LOC(d2result)") int d3result;
	@delta("D,FC") int result;
	@delta("LOC(result)") int d2result;

	d3result=d2result+a;

	//e=a+c;
	
	//result = f.a + f.b; // glb(f.a,f.b)=[T,FB]
                            // then, compare LOC(result) and [T,FB]

	//result=f.a+u.b;

	//f.b=u.a; // u.a gives us a location: delta(U,FA)
                 // f.b is at least lower than delta(U,FA)
	         // by base comparison,
                 // if LOC(f)<LOC(u) & LOC(foo.b)<LOC(foo.a)
                 // then, it's okay

	//bar.b=u.a; // u.a gives a new location: delta(U,FA)
	           // bar.b is at least lower than delta(U,FA)
                   // by base comparison
                   // if LOC(bar)<LOC(u) 
                   // but no ordering relation between LOC(bar.b) and LOC(foo.a)
	           // is it okay to allow it?
	           // seems to be okay because there is no way to
	           // get it back to u.b or something else.	
    }
}

class foo{
    
    locdef{
	FC<FB,FB<FA
    }
    
    @FA int a;
    @FB int b;
    @FC int c;
    
}

class bar{

    locdef{
	BC<BB,BB<BA
    }

    @BA int a;
    @BB int b;
    @BC int c;

}
