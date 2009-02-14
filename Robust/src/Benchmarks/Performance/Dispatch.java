task start(StartupObject s {initialstate}) {
    Foo f=new Foo(){run};
    
    taskexit(s{!initialstate});
}

task DoOperation(Foo f{run}) {
    if (f.count==1000000)
	taskexit(f{!run});
    else {
	f.count++;
	taskexit(f{run});
    }
}

public class Foo {
    flag run;
    int count;
    public Foo() {
    }
}