class small {
    tiny t1;

    public small(tiny t) {
	t1=t;
    }

    public static void main(String x[]) {
	tiny t=new tiny();
	small s=new small(t);
	foo[] farray=s.process();
    }

    public foo[] process() {
	foo []farray=new foo[30];
	while(true) {
	    foo f=createfoo();
	    farray[0]=f;
	}
	return farray;
    }
    
    public foo createfoo() {
	foo f=disjoint test new foo();
	f.t1=t1;
	return f;
    }


}

public class tiny {
    int x;
    public tiny() {

    }
}

public class foo {
    tiny t1;
    public foo() {


    }
}