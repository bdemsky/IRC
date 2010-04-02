public class Test 
{
    private final int MAX = 1000;

    //Is needed because the default doesn't work that well...
    public Test(){}
    
    public static void main(String args[]) {
	
	System.out.println("# it starts");	
	Test t = new Test();
	t.doSomeWork();
	
    }    
    
    public void doSomeWork() 
    {

    }

    public boolean isPrime(int num)
    {
	//Did abs this way because I didn't want to import Math
	int number = (num >= 0) ? num:-num;

	if(number <= 1)
	    return false;

	for(int i = 2; i < number; i++)
	{
	    if(number%i == 0)
		return false;
	}
	
	return true;
    }
   
}

