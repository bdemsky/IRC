public class VInt {
  public int num;
  
  public VInt() {
    this.num = 0;
  }
}

public class VolatileTest  extends Thread {
  volatile VInt vi;   
  String name;
  
  public VolatileTest(String name, VInt vi) {
    this.name = name;
    this.vi = vi;
  }

  public void run(){
    if(name.equals("Thread1")){  
      vi.num=10;  
    }  
    else{  
      System.out.println("value of num is :"+vi.num);  
    }     
  }  

  public static void main(String args[]){  
    VInt vi = new VInt();
    
    Thread t1 = new VolatileTest("Thread1", vi);   
    t1.start();  

    Thread.sleep(1000);  

    Thread t2 = new VolatileTest("Thread2", vi);   
    t2.start();  
  }  
}  