public class test {

	public static void main(String argv[]) {

	    int x=0;
	    int count=500000;

	    if(argv.length>0){
		count=count * Integer.parseInt(argv[0]);
	    }
	    for(int i=0;i<count;i++){
		rblock rchild{
		    x++;
		}
	    }

	}

}
