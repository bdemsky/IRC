/**************************************************************************
 * * Java Grande Forum Benchmark Suite - Version 2.0 * * produced by * * Java
 * Grande Benchmarking Project * * at * * Edinburgh Parallel Computing Centre *
 * * email: epcc-javagrande@epcc.ed.ac.uk * * * This version copyright (c) The
 * University of Edinburgh, 1999. * All rights reserved. * *
 **************************************************************************/

public class JGFRayTracerBench extends RayTracer {
	JGFInstrumentor instr;

	public JGFRayTracerBench(JGFInstrumentor instr) {
		super();
		this.instr = instr;
	}

	public void JGFsetsize(int size) {
		this.size = size;
	}

	public void JGFinitialise() {

//		instr.startTimer("Section3:RayTracer:Init");

		// set image size
//		width = height = datasizes[size];
		width = datasizes[size];
		height = datasizes[size];

		// create the objects to be rendered
		scene = createScene();

		// get lights, objects etc. from scene.
		setScene(scene);

		numobjects = scene.getObjects();

//		instr.stopTimer("Section3:RayTracer:Init");

	}

	public void JGFapplication() {

//		instr.startTimer("Section3:RayTracer:Run");

		// Set interval to be rendered to the whole picture
		// (overkill, but will be useful to retain this for parallel versions)
		Interval interval = new Interval(0, width, height, 0, height, 1);

		// Do the business!
		render(interval);
//		System.out.println("DONE");
//		instr.stopTimer("Section3:RayTracer:Run");

	}

	public void JGFvalidate() {
		// long refval[] = {2676692,29827635};
		long refval[] = new long[2];
		refval[0] = 2676692;
		refval[1] = 29827635;
		long dev = checksum - refval[size];
		if (dev != 0) {
			System.out.println("Validation failed");
			System.out.println("Pixel checksum = " + checksum);
			System.out.println("Reference value = " + refval[size]);
		}
	}

	public void JGFtidyup() {
		scene = null;
		lights = null;
		prim = null;
		tRay = null;
		inter = null;

		// System.gc();
	}

	public void JGFrun(int size, JGFInstrumentor instr) {

//		instr.addTimer("Section3:RayTracer:Total", "Solutions", size);
//		instr.addTimer("Section3:RayTracer:Init", "Objects", size);
//		instr.addTimer("Section3:RayTracer:Run", "Pixels", size);

		JGFsetsize(size);

//		instr.startTimer("Section3:RayTracer:Total");

		JGFinitialise();
		JGFapplication();
//		JGFvalidate();
//		JGFtidyup();

//		instr.stopTimer("Section3:RayTracer:Total");
//
//		instr.addOpsToTimer("Section3:RayTracer:Init", (double) numobjects);
//		instr.addOpsToTimer("Section3:RayTracer:Run",(double) (width * height));
//		instr.addOpsToTimer("Section3:RayTracer:Total", 1);
//
//		instr.printTimer("Section3:RayTracer:Init");
//		instr.printTimer("Section3:RayTracer:Run");
//		instr.printTimer("Section3:RayTracer:Total");
	}

}
