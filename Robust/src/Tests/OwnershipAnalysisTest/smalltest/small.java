class small {
    tiny t1;
    foo[] f;

    public small(tiny t) {
	t1=t;
	f=new foo[100];
    }

    public static void main(String x[]) {
	tiny t=new tiny();
	small s=new small(t);
	s.process();
    }

    void process() {
	while(true) {
	    createfoo();
	}
    }
    
    public void createfoo() {
	foo f=disjoint test new foo();
	this.f[0]=f;
	f.t1=t1;
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