public class test {

	public static void main(String argv[]) {

	    long x=0;
	    long count=500000;

	    if(argv.length>0){
		count=count * Integer.parseInt(argv[0]);
	    }

            long s=System.currentTimeMillis();

	    for(long i=0;i<count;i++){
		rblock rchild{
		}
	    }

            long e1=System.currentTimeMillis();
            System.out.println( "x="+x );
            long e2=System.currentTimeMillis();

            double dt1 = ((double)e1-s)/(Math.pow( 10.0, 3.0 ) );
            double dt2 = ((double)e2-s)/(Math.pow( 10.0, 3.0 ) );
            System.out.println( "dt1="+dt1+"s" );
            System.out.println( "dt2="+dt2+"s" );
	}

}
