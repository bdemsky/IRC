public class Counter{
     
  long count;
  
  public Counter() {
    this.count = 0;
  }

  public synchronized void add(long value){
    this.count += value;
    System.printString(" " + this.count + "\n");
  }
  
  public synchronized long getCounter() {
    return this.count;
  }
}

public class CounterThread extends Thread{

  String name;
  protected Counter counter;

  public CounterThread(String name, Counter counter){
    this.name = name;
    this.counter = counter;
  }

  public void run() {
    for(int i=0; i<10; i++){
      System.printString(this.name);
      counter.add(i);
    }
  }
}

public class SynchonizedTest {
  public SynchonizedTest() {
  }

  public static void main(String[] args){
    Counter counter = new Counter();
    Thread  threadA = new CounterThread("A\n",counter);
    Thread  threadB = new CounterThread("B\n",counter);

    threadA.start();
    threadB.start(); 
  }
}
