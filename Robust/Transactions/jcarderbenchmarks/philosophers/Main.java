

public class Main {
    public static void main(String[] args) throws Exception {
    //    for (int i=0; i<1000; i++){
        long start = System.currentTimeMillis();
        System.out.println("Start time" + start);
        Chopstick stick1 = new Chopstick();
        Chopstick stick2 = new Chopstick();
        Chopstick stick3 = new Chopstick();
        Chopstick stick4 = new Chopstick();
        Chopstick stick5 = new Chopstick();

        Philosopher phil1 = new Philosopher("Philosopher1", 1, stick1, stick2);
        Philosopher phil2 = new Philosopher("Philosopher2", 2, stick2, stick3);
        Philosopher phil3 = new Philosopher("Philosopher3", 3, stick3, stick4);
        Philosopher phil4 = new Philosopher("Philosopher4", 4, stick4, stick5);
        Philosopher phil5 = new Philosopher("Philosopher5", 5, stick5, stick1);

        phil1.start();
        phil2.start();
        phil3.start();
        phil4.start();
        phil5.start();
        
        phil1.join();
        phil2.join();
        phil3.join();
        phil4.join();
        phil5.join();
        
        long end = System.currentTimeMillis();
        System.out.println("End time" + end);
        System.out.println("Elapsed Time " + (end - start));
                
        
        
        System.out.println("Program finished successfully");
        }
  //  }
}
