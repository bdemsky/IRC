import java.io.*;

class analyze {
  public static int NUM=30;
  public static void main(String files[]) throws Exception {
    int M=files.length-1;
    String[][] names=new String[NUM][M];
    long[][] times=new long[NUM][M];
    long[][] aborts=new long[NUM][M];
    long[][] commits=new long[NUM][M];
    long[][] stalltime=new long[NUM][M];
    long[][] backofftime=new long[NUM][M];
    long[][] abortedtime=new long[NUM][M];
    for(int i=0;i<(files.length-1);i++) {
      BufferedReader br=new BufferedReader(new FileReader(files[i+1]));
      String x;
      int count=0;
      while((x=br.readLine())!=null) {
	int index=x.indexOf('=');
	names[count][i]=x.substring(0, index);
	String n=x.substring(index+1, x.length());
	times[count][i]=Long.parseLong(n);
	{
	  x=br.readLine();
	  int i1=x.indexOf('=');
	  int i2=x.indexOf(' ');
	  int i3=x.indexOf('=', i2);
	  String saborts=x.substring(i1+1, i2);
	  String scommits=x.substring(i3+1, x.length());
	  aborts[count][i]=Long.parseLong(saborts);
	  commits[count][i]=Long.parseLong(scommits);
	}
	{
	  x=br.readLine();
	  int i1=x.indexOf('=');
	  int i2=x.indexOf(' ');
	  int i3=x.indexOf('=', i2);
	  String stall=x.substring(i1+1, i2);
	  String backoff=x.substring(i3+1, x.length());
	  stalltime[count][i]=Long.parseLong(stall)/(2<<i);
	  backofftime[count][i]=Long.parseLong(backoff)/(2<<i);
	}

	{
	  x=br.readLine();
	  int i1=x.indexOf('=');
	  String abortedt=x.substring(i1+1, x.length());
	  abortedtime[count][i]=Long.parseLong(abortedt)/(2<<i);
	}

	count++;
      }
    }
    String names2[]={"Lazy", "Fast", "Aggressive", "Suicide", "Timestamp", "Random", "Karma", "Polite", "Eruption", "AggressiveTime", "Fixed", "AggressiveFixed"};
    if (files[0].equals("combined")) {
      for(int j=0;j<M;j++) {
	int numthreads=2<<j;
	FileWriter fw=new FileWriter("file"+numthreads+".dat");
	fw.write("version aborttime stalltime backofftime baseline\n");
	for(int i=0;i<names2.length;i++) {
	  long totaltime=times[i][j];
	  long abortt=abortedtime[i][j];
	  long stallt=stalltime[i][j];
	  long backofft=backofftime[i][j];
	  long baset=totaltime-abortt-stallt-backofft;
	  fw.write(names2[i]+" "+abortt+" "+stallt+" "+backofft+" "+baset+"\n");
	}
	fw.close();
      }
    } else {
      /* Do individual printing. */
      System.out.print("X");
      for(int i=0;i<names2.length;i++) {
	System.out.print(" "+names2[i]);
      }
      System.out.println("");
      int x=2;
      for(int j=0;j<M;j++) {
	System.out.print(x);x*=2;
	for(int i=0;names[i][0]!=null;i++) {
	  if (files[0].equals("time")) {
	    System.out.print(" "+times[i][j]);
	  } else if (files[0].equals("abortpercent")) {
	    double percent=((double)aborts[i][j])/((double)(commits[i][j]+aborts[i][j]));
	    System.out.print(" "+percent);
	  } else if (files[0].equals("aborttime")) {
	    System.out.print(" "+abortedtime[i][j]);
	  } else if (files[0].equals("stalltime")) {
	    System.out.print(" "+stalltime[i][j]);
	  } else if (files[0].equals("backofftime")) {
	    System.out.print(" "+backofftime[i][j]);
	  }
	}
	System.out.println("");
      }
    }
  }
}