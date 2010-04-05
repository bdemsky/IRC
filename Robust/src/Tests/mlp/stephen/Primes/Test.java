public class Test
{
    //Apparently global variables are not yet supported
    //private int MAX = 100000;

    public Test(){}

    public static void main(String args[]) {

        System.out.println("# it starts");
        Test t = new Test();
        t.doSomeWork();

    }

    public void doSomeWork()
    {
        int MAX = 100000;
	int ittr = 100;

        long sum = 0;
        long time = System.currentTimeMillis();
        
        //I did the for loop this way so that each parallel thread would take
        //about the same time 
        for(int i = 0; i < MAX/2 + 1; i += ittr)
        {
		int innerSum = 0;
		
		sese a 
		{
		    for(int j = i; (j < i + ittr) && j < MAX/2 + 1; j++)
			{
		
			    int oppositeNum = MAX - j;
        		
			    if(isPrime(j))
	        		innerSum += j;
	        	
			    if(i != oppositeNum && isPrime(oppositeNum))
	        		innerSum += oppositeNum;
			}
	    	}
            
	    	sese b 
		{
        		sum += innerSum;
	    	}
        }

        System.out.println("The sum of primes from 1 to " + MAX + " is " + sum + ".");
        System.out.println("Note: 1 is counted as a prime.");
        System.out.println("Time Consumed: " + (System.currentTimeMillis() - time) + " ms");

    }
    

    private boolean isPrime(int number)
    {
    	//handles special cases
        if(number < 1)
            return false;

        if (number < 3)
        	return true;

        //Tests the rest of the numbers
        for(int i = 2; i < number; i++)
        {
            if(number%i == 0)
                return false;
        }
        
        return true;
    }

}
