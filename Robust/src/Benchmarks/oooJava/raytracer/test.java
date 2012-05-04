public class test {

	public static void main(String argv[]) {
	  boolean vtest=false;
		int size=1;
		if( argv.length>0 ){
		    size=Integer.parseInt(argv[0]);
		    if(argv.length>1){
		      vtest=true;
		    }
		}
    JGFInstrumentor instr = new JGFInstrumentor();
    if(!vtest){
      instr.printHeader(3, 0);
    }

    JGFRayTracerBench rtb = new JGFRayTracerBench(instr);
		rtb.JGFrun(size, instr,vtest);
	}

}
