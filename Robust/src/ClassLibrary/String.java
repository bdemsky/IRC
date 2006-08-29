public class String {
    char string[];

    public String(char string[]) {
	this.string=string;
    }

    public static String valueOf(Object o) {
	return o.toString();
    }

    public static String valueOf(int x) {
	int length=0;
	int tmp;
	if (x<0)
	    tmp=-x;
	else
	    tmp=x;
	do {
	    tmp=tmp/10;
	    length=length+1;
	} while(tmp!=0);
	
	char chararray[];
	if (x<0)
	    chararray=new char[length+1];
	else
	    chararray=new char[length];
	int offset;
	if (x<0) {
	    chararray[0]='-';
	    offset=1;
	    x=-x;
	} else
	    offset=0;
       	
	do {
	    chararray[--length+offset]=(char)(x%10+'0');
	    x=x/10;
	} while (length!=0);
	return new String(chararray);
    }
}
