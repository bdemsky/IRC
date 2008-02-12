public class JGFSORBench extends SOR{ 

	private int size; 
	private int[] datasize;
	private final int JACOBI_NUM_ITER;
	private final long RANDOM_SEED;
	public int nthreads;
	Random R;

	public JGFSORBench() {
		JACOBI_NUM_ITER = 100;
		RANDOM_SEED = 10101010;
		R = new Random(RANDOM_SEED);
	}

	public JGFSORBench(int nthreads){
		this.nthreads = nthreads;
		int datasizes[] = new int[3];
		datasizes[0]= 1000;
		datasizes[1]= 1500;
		datasizes[2]= 2000;
	}

	public void JGFsetsize(int size){
		this.size = size;
	}

	public void JGFinitialise(){

	}

	public void JGFkernel(){

		double G[][] = RandomMatrix(datasizes[size], datasizes[size],R);

		SORrun(1.25, G, JACOBI_NUM_ITER, nthreads);


	}

	public void JGFvalidate(){

		double refval[] = new double[3];
		refval[0] = 0.498574406322512;
		refval[1] = 1.1234778980135105;
		refval[2] = 1.9954895063582696;
		double dev = Math.abs(Gtotal - refval[size]);
		if (dev > 1.0e-12 ){
			System.out.println("Validation failed");
			System.out.println("Gtotal = " + Gtotal + "  " + dev + "  " + size);
		}
	}

	public void JGFtidyup(){
		//System.gc();
	}  

	public void JGFrun(int size){


		JGFInstrumentor.addTimer("Section2:SOR:Kernel", "Iterations",size);

		JGFsetsize(size); 
		JGFinitialise(); 
		JGFkernel(); 
		JGFvalidate(); 
		JGFtidyup(); 


		JGFInstrumentor.addOpsToTimer("Section2:SOR:Kernel", (double) (JACOBI_NUM_ITER));

		JGFInstrumentor.printTimer("Section2:SOR:Kernel"); 
	}

	private static double[][] RandomMatrix(int M, int N, java.util.Random R)
	{
		double A[][] = new double[M][N];

		for (int i=0; i<N; i++)
			for (int j=0; j<N; j++)
			{
				A[i][j] = R.nextDouble() * 1e-6;
			}      
		return A;
	}


}
