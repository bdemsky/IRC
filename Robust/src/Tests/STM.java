/* This test case tests the thread joining for a JavaSTM classlibrary */ 
public class STM extends Thread {
  Data x;
  public STM(Data x) {
    this.x=x;
  }
  public static void main(String[] st) {
    Data d=new Data();
    STM s1=new STM(d);
    STM s2=new STM(d);
    
    s1.start();
    s2.start();
    s1.join();
    s2.join();
  }
  
  public void run() {
    int i;
    //    for(int j=0;j<10;j++) {
	atomic {
	    i=x.a++;
	}
	//    }
    System.out.println("Initial value:"+i);
  }
}

public class Data {
  int a;
  public Data() {
  }
}
