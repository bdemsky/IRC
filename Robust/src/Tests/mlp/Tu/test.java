public class test {

    public static void main(String args[]) {
	System.out.println("# it starts");	
	test t = new test();
	t.doSomeWork();
    }    
    
    public void doSomeWork(){
	
	int numpoints = 100000000;
	int numberOfWorker = 10;
	int numpointsEachWorker = numpoints/numberOfWorker;
	int numOfPointsInCircle = 0;
	
	


	double side = 10;//side of the square
	double x_conner = 0;
	double y_conner = 0; //lower left conner of the square
	double midpoint = 5;//(midpoint, midpoint) = (5,5)
	    for (int i = 0; i < numberOfWorker; i++)
            {
		sese parallel{
		    //each worker has its own random object
		    Random r = new Random();
		    r.random_alloc();
		    int countPointInCircle = 0;//keep track of points in circle
                for (int j = 0; j < numpointsEachWorker; j++)
                {
		    int x =  (r.random_generate())%10;
		    int y =  (r.random_generate())%10;
		    //if the random point is outside the square then pick again
		    while ((x > side) || (y > side))
		    {
			x = (r.random_generate())%10;
			y = (r.random_generate())%10;
		    }
		    //calculate the distance between the random point and the center of circle
		    double distance = (double)Math.sqrt(((x-midpoint)*(x-midpoint)) + ((y-midpoint)*(y-midpoint)));
		    //note: each worker has one countPointInCircle variable
                    if (distance <= 5.0) // side/2 is the radius of the circle
		    {
		        countPointInCircle++;
		    }
                }
	    
	    }
	    //add all the countPointInCircle together
             sese serial{
		 numOfPointsInCircle += countPointInCircle;
             }
         }

	    //calculate PI   
            double PI = (double)(4*(double)numOfPointsInCircle)/(double)(numpoints);  

	    System.out.println("PI="+PI);
    }

    public test(){}
    
}

