public class VolatileTest  extends Thread {
  volatile int num;   
  String name;
  
  {
    num = 0;
  }
  
  public VolatileTest(String name) {
    this.name = name;
  }

  public void run(){
    if(name.equals("Thread1")){  
      num=10;  
    }  
    else{  
      System.out.println("value of num is :"+num);  
    }     
  }  

  public static void main(String args[]){  
    Thread t1 = new VolatileTest("Thread1");   
    t1.start();  

    Thread.sleep(1000);  

    Thread t2 = new VolatileTest("Thread2");   
    t2.start();  
  }  
}  