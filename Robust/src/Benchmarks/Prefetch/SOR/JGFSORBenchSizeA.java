public class JGFSORBenchSizeA{ 
	public int nthreads;
	public static void main(String argv[]){

		if(argv.length != 0 ) {
			nthreads = Integer.parseInt(argv[0]);
		} else {
			System.printString("The no of threads has not been specified, defaulting to 1");
			System.printString("  ");
			nthreads = 1;
		}

		JGFInstrumentor.printHeader(2,0,nthreads);
		JGFSORBench sor = null;
		atomic {
			sor = global new JGFSORBench(nthreads); 
			sor.JGFrun(0);
		}

	}
}

