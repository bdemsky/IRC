public class test {

	public static void main(String argv[]) {

		JGFInstrumentor instr = new JGFInstrumentor();

		instr.printHeader(3, 0);

		JGFRayTracerBench rtb = new JGFRayTracerBench(instr);
		rtb.JGFrun(0, instr);

	}

}
