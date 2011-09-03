public class test{

    @LATTICE("OUT<IN")
	public static void main (@LOC("IN") String args[]){       
	test t=new test();	
	int i=0;
	SSJAVA:
	while(i<100){
	    t.doit();
	    i++;
	}
    }
    
    @LATTICE("")
    public void doit(){
	int a;
	int b;
	a = 5;
	b = a;
    }
    
}