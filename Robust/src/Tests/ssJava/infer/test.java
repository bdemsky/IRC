public class test{
    
    int fieldA;
    int fieldB;
    int fieldC;

    public static void main (@LOC("IN") String args[]){       
	test t=new test();	
	SSJAVA:
	while(true){
	    t.doit();	    
	}
    }
    
    public void doit(){
	int localA = 0;
	int localB = 0;
	int localC = 0;

	if(localA>100){
	    int localD=50;
	    localC=localD;
	}else{
	    paramTest(localA,localB,new Foo());
	}
    }

    public void paramTest(int paramA, int paramB, Foo paramFooC){
	int localD = 100;
	if(paramA>paramB){
	    paramFooC.fooFieldA=50;
	}else{
	    paramFooC.fooFieldB=localD;
	}
    }
    
}

class Foo{

    int fooFieldA;
    int fooFieldB;

    public int getA(){
	return fooFieldA;
    }

    public int getB(){
	return fooFieldB;
    }

}