import java.io.*;

public class buildprefix {

  public static void main(String xstr[]) {
    buildprefix bf=new buildprefix();
    bf.foo(Integer.valueOf(xstr[0]));
  }
  boolean prefix=true;
  int skipbyte=42;
  int allocunits=2;
  int max;
  int maxbits;
  public void foo(int maxbit) {
    maxbits=maxbit;
    max=1<<maxbits;
    int x[]=new int[max];
    int value=0;
    int lastcount=0;
    int lastindex=0;
    int lastvalue=0;
    for(int count=0;count<max;count++) {
      int numbits=bits(value);
      int mask=mask(numbits);
      if ((lastcount&mask)!=(count&mask))
	value=increment(value);
      else {
	lastindex=count;
	lastvalue=value;
      }

      x[count]=value;
      lastcount=count;
    }
    System.out.println("lastindex="+lastindex+"   lastvalue="+lastvalue);
    System.out.print("int markmappingarray[]={");
    for(int count=0;count<max;count++) {
      if (count!=0) {
	System.out.print(", ");
	
	if ((count%16)==0) {
	  System.out.println("");
	  System.out.print("                    ");
	}
      }
      System.out.print(x[count]);
    }
    System.out.println("};");
    value=0;
    for(int count=0;count<max;count++) {
      if (x[count]>=value) {
	System.out.println(value+": "+count);
	if (value==0&&!prefix)
	  value=2;
	else
	  value++;
      }
    }

    System.out.print("int revmarkmappingarray[]={");
    value=0;
    int printed=0;
    for(int count=0;count<max;count++) {
      if (x[count]>=value) {
	while(x[count]!=value) {
	  if (value!=0) {
	    System.out.print(", ");
	    if ((printed%16)==0) {
	      System.out.println("");
	      System.out.print("                       ");
	    }
	  }
	  System.out.print("0");
	  printed++;
	  value++;
	}
	if (value!=0) {
	  System.out.print(", ");

	  if ((printed%16)==0) {
	    System.out.println("");
	    System.out.print("                       ");
	  }
	}
	System.out.print(count);
	printed++;
	if (value==0&&!prefix)
	  value=2;
	else
	  value++;
      }
    }
    System.out.println("};");

    System.out.print("int revmarkmappingarray[]={");
    value=0;
    printed=0;
    for(int count=0;count<max;count++) {
      if (x[count]>=value) {
	while(x[count]!=value) {
	  if (value!=0) {
	    System.out.print(", ");
	    if ((printed%16)==0) {
	      System.out.println("");
	      System.out.print("                       ");
	    }
	  }
	  System.out.print("0x0");
	  printed++;
	  value++;
	}
	if (value!=0) {
	  System.out.print(", ");

	  if ((printed%16)==0) {
	    System.out.println("");
	    System.out.print("                       ");
	  }
	}
	long valcount=count;
	System.out.print("0x"+Long.toHexString(valcount<<(32-maxbits)));
	printed++;
	if (value==0&&!prefix)
	  value=2;
	else
	  value++;
      }
    }
    System.out.println("};");
  }

  int mask(int x) {
    return ((max-1)>>(maxbits-x))<<(maxbits-x);
  }

  int bits(int x) {
    if (x<2)
      return 2;
    else {
      x=x*allocunits;
      if (x>maxbits)
	return maxbits;
      else return x;
    }
  }

  int increment(int x) {
    if (x==0&&!prefix)
      return x+2;
    if (x==skipbyte)
      return x+2;
    else
      return x+1;
  }
}
