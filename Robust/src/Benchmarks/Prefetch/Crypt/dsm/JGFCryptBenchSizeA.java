public class JGFCryptBenchSizeA{
    public static void main(String argv[]){
        int nthreads;
        if(argv.length != 0 ) {
            nthreads = Integer.parseInt(argv[0]);
        } else {
            System.printString("The no of threads has not been specified, defaulting to 1");
            System.printString("  ");
            nthreads = 1;
        }

        JGFInstrumentor instr = new JGFInstrumentor();
        instr.printHeader(2,0,nthreads);

        JGFCryptBench cb = new JGFCryptBench(nthreads, instr);
        cb.JGFrun(0);

    }
}

