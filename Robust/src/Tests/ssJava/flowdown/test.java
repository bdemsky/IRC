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

    public void doit3(){
	@LOC("methodT,testL")int localVar=fooM.a+fooM.b;
	// GLB(fooM.a,fooM.b)=LOC[methodT,testM,FB]
	// LOC[lovalVar]=[methodT,testL] < GLB(fooM.a,fooM.b)
    }

    // creating composite location by object references
    public void doit4(){
	@LOC("methodT,testM,FC,BB") int localVar=fooM.bar.a; 
	//LOC(fooM.bar.a)=[methodT,testM,FC,BA]
	//localVar can flow into lower location of fooM.bar.a	
	fooM.bar.c=localVar; //[methodT,testM,FC,BB] < [methodT,testM,FC,BA]
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

    @LATTICE("mL<mSpin,mSpin<mH,mSpin*")
    public void doSpinLoc(){
	// if loc is defined with the suffix *, 
	//value can be flowing around the same location
	
	@LOC("mH") int varH;
	@LOC("mSpin") int varSpin;
	@LOC("mL") int varL;

	varH=10; 
	while(varSpin>50000){
	    // value can be flowing back to the varSpin
	    // since 'varSpin' is location allowing value to move within
	    // the same abstract location
	    varSpin=varSpin+varH;     
	}
	varL=varSpin;
    }

    @LATTICE("mL<mH,THISLOC=mL")
    public void doSpinField(){
	//only last element of the composite location can be the spinning loc

	@LOC("mH") int varH;
	@LOC("mL") int varL;

	@LOC("mH") Bar localBar=new Bar();

	localBar.b2=localBar.b1; 
	// LOC(localBar.b1)=[mH,BB]
	// LOC(localBar.b2)=[mH,BB]
	// b1 and b2 share the same abstract loc BB
	// howerver, location BB allows values to be moving within itself
	
	localBar.b1++; // value can be moving among the same location
    }

    public void uniqueReference(){
	
	@LOC("methodH") Foo f_1=new Foo();
	f_1.bar=new Bar();
	@LOC("methodL") Bar newBar_2;
	newBar_2=f_1.bar;
	f_1.bar=null; // should assign null here 
	
    }

}


@LATTICE("FC<FB,FB<FA")
@METHODDEFAULT("fm_L<fm_M1,fm_L<fm_M2,fm_M1<fm_H,fm_M2<fm_H,THISLOC=fm_H")
class Foo{
	
    @LOC("FA") int a;
    @LOC("FB") int b;
    @LOC("FC") int c;
    @LOC("FC") Bar bar;
	
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

@LATTICE("BC<BB,BB<BA,BB*")
@METHODDEFAULT("BARMD_L<BARMD_H,THISLOC=BARMD_H")
class Bar{
    @LOC("BA") int a;
    @LOC("BB") int b2;
    @LOC("BB") int b1;
    @LOC("BC") int c;   

}
