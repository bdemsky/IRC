public class test {

	public static void main(String argv[]) {
		JGFInstrumentor instr = new JGFInstrumentor();
		instr.printHeader(3, 0);

		JGFRayTracerBench rtb = new JGFRayTracerBench(instr);
		int size=0;
		if( argv.length>0 ){
		    size=Integer.parseInt(argv[0]);
		}
		rtb.JGFrun(size, instr);
	}

}
