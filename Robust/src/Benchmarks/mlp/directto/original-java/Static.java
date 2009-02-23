
// This class memorizes the static data (besides fixes)


import java.util.*;

class Static
{
    public static double width, height; // the dimensions of the given area 
    public static double iterationStep, noIterations;    
    public static double radius, distance;

    public static void setMapSize(StringTokenizer st)
    {
	width=Double.parseDouble(st.nextToken());
	height=Double.parseDouble(st.nextToken());
    }

    public static void setCylinder(StringTokenizer st)
    {
	radius=Double.parseDouble(st.nextToken());
        distance=Double.parseDouble(st.nextToken());
    }    

    public static void setIterationStep(StringTokenizer st)
    {
	iterationStep=Double.parseDouble(st.nextToken());
    }

    public static void setNumberOfIterations(StringTokenizer st)
    {
	noIterations=Integer.parseInt(st.nextToken());
    }

    public static void printInfo()
    // this is a test procedure
    {
	System.out.println("\n\nStatic Data:");
	System.out.println("Width:"+width+"        Height:"+height);
        System.out.println("Radius of safety/unsafety:"+radius);
	System.out.println("Distance of safety/unsafety:"+distance);
	System.out.println("Iteration step:"+iterationStep+"     No. of Iterations:"+noIterations);

			   
    }
  
}





