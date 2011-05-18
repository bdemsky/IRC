@LATTICE("testL<testM,testM<testH")
@METHODDEFAULT("methodL<methodH,methodH<methodT,THISLOC=methodT")
public class test{

    @LOC("testH") int fieldH;
    @LOC("testM") int fieldM;
    @LOC("testL") int fieldL;
    @LOC("testM") Foo fooM;

    public static void main (@LOC("methodH") String args[]){       
	@LOC("methodH") test t=new test();
	t.doit();
    }

    public void doit(){
	@LOC("methodH") int localH; // LOC=[local.methodH]
	fooM=new Foo(); // LOC=[local.methodT, field.testM]
	fooM.doSomething();

	// here, callee expects that arg1 is higher than arg2
	// so caller should respects callee's constraints
	fooM.doSomethingArgs(fieldH,fieldM);

	doit2();
	doOwnLattice();
	doDelta();
    }
    
    public void doit2(){
	@LOC("methodH,testL") int localVarL;
	
	// value flows from the field [local.methodH,field.testH]
	// to the local variable [local.methodL]
	localVarL=fieldH;
    }

    // method has its own local variable lattice 
    @LATTICE("mL<mH,THISLOC=mH")
    public void doOwnLattice(){
	@LOC("mL") int varL;
	@LOC("mH") int varH;
	varL=varH;
    }
    
      
    @LATTICE("mL<mH,THISLOC=mH")
    public void doDelta(){
	@LOC("DELTA(mH,testH)") int varDelta;
	// LOC(varDelta) is slightly lower than [mH, testH]

	@LOC("DELTA(DELTA(mH,testH))") int varDeltax2;
	// applying double delta to [mH,testH]
	
	varDelta=fieldH; // [mH,testH] -> DELTA[mh,testH]
	varDeltax2=varDelta; //DELTA[mh,testH] -> DELTA[DELTA[mh,testH]]
	
	fieldM=varDeltax2; //  DELTA[DELTA[mh,testH]] -> [mH,testM]	
    }

}


@LATTICE("FC<FB,FB<FA")
@METHODDEFAULT("fm_L<fm_M1,fm_L<fm_M2,fm_M1<fm_H,fm_M2<fm_H,THISLOC=fm_H")
class Foo{
	
    @LOC("FA") int a;
    @LOC("FB") int b;
    @LOC("FC") int c;
	
    public Foo(){
    }

    public void doSomething(){
	b=a; // value flows from [fm_H,FA] to [fm_H,FB]
    }

    // callee has a constraint that arg1 is higher than arg2
    public int doSomethingArgs(@LOC("fm_H")int argH,
			       @LOC("fm_M1")int argL){	
	argL=argH+50;
	return argL;
    }

    public int doSomethingRtn(){
	return a+b; // going to return LOC[local.t,field.FB]
    }
        
}
