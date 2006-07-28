public class String {
    char string[];

    public String(char string[]) {
	this.string=string;
    }

    public static String valueOf(Object o) {
	return o.toString();
    }

    /*    public static String valueOf(int x) {
	  int length=0;
	  int tmp=x;
	  do {
	  tmp=tmp/10;
	  length=length+1;
	  } while(tmp!=0);
	  char chararray[]=new chararray[length];
	  do {
	  length--;
	  chararray[length]=x%10;
	  x=x/10;
	  } while (length!=0);
	  return new String(chararray);
	  }*/

}
