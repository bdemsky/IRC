public class Counter{
     
  long count;
  
  public Counter() {
    this.count = 0;
  }

  public add(long value){
	synchronized (Counter.class) {
      this.count += value;
	}
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
      System.printString(" " + counter.getCounter() + "\n");
    }
  }
}

public class CounterS{
  
  static long count = 1;

  public CounterS() {
  }

  public add(long value){
    synchronized (CounterS.class) {
      CounterS.count += value;
    }
  }
  
  public synchronized static long getCounter() {
    return CounterS.count;
  }
}

public class CounterSThread extends Thread{

  String name;
  protected CounterS counter;

  public CounterSThread(String name, CounterS counter){
    this.name = name;
    this.counter = counter;
  }

  public void run() {
    for(int i=0; i<10; i++){
      System.printString(this.name);
      counter.add(i);
      System.printString("  " + counter.getCounter() + "\n");
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

    CounterS countersA = new CounterS();
    CounterS countersB = new CounterS();
    Thread  threadsA = new CounterSThread("C\n",countersA);
    Thread  threadsB = new CounterSThread("D\n",countersB);

    threadsA.start();
    threadsB.start(); 
  }
}
