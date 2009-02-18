task start(StartupObject s {initialstate}) {
    Dispatch f=new Dispatch(){run};
   
    taskexit(s{!initialstate});
}

task DoOperation(Dispatch f{run}) {
    if (f.count==1000000)
	taskexit(f{!run});
    else {
	f.count++;
	taskexit(f{run});
    }
}

public class Dispatch {
    flag run;
    int count;
    public Dispatch() {
    }
}