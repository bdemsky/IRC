@LATTICE("L<M,M<H")
@METHODDEFAULT("L<M,M<H,H<C,THISLOC=M,C*,H*")
public class test{

    @LOC("H") int fieldH;
    @LOC("M") int fieldM;
    @LOC("L") int fieldL;

    public static void main(@LOC("H") String args[]){       
	@LOC("M") test test=new test();

	@LOC("M") int localM;
	@LOC("L") int localL;
	@LOC("M") Foo foo=new Foo();
	@LOC("C") int count=0;
	SSJAVA:
	while(count<50){
	    count++;	 
	    test.doit();
	    if(true){
		// read followed by write: problem!
		localL=test.fieldH;
		//test.fieldH=50;	 
	    }
	}
    }

    public void doit(){
	@LOC("L") int local=fieldH;
	fieldH=50;
    }

}

@LATTICE("L<M,M<H")
@METHODDEFAULT("L<M,M<H,H<C,THISLOC=M,C*,H*")
class test2 extends test{
    
    public void doit(){
	if(true){
	    fieldH=50;
	}else{
	    fieldL=50;	    
	}
    }

}

@LATTICE("L<M,M<H")
class Foo{    
    @LOC("H") int a;
}