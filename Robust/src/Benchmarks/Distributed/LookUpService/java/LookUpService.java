public class LookUpService {
  public static int main(String arg[]) {
    /**
     * The initial capacity of hashmap
     **/
    int initCapacity = 100;

    /**
     * The second capacity of hashmap
     **/
    int secondCapacity = 100;

    /**
     * The loadFactor 
     **/
    float loadFactor = 0.75f;

    /**
     * Number of threads 
     **/
    int nthreads = 1;

    /**
     * Number of objects in the hash table 
     **/
    int nobjs = 110;

    /**
     * Create shared hashmap and put values
     **/
    DistributedHashMap dhmap;
    dhmap = new DistributedHashMap(initCapacity,secondCapacity,loadFactor)
    for(int i = 0; i<nobjs; i++) {
      Integer key = new Integer(i);
      Integer val = new Integer(i*i);
      dhmap.put(key,val);
    }

    //Create New ServerSocket
    System.println("Starting main\n");
    ServerSocket ss = new ServerSocket(9000);
    acceptConnection(ss);
  }

  public static void acceptConnection(ServerSocket ss, DistributedHashMap dhmap, int nthreads) {
    LookUpService[] lus;
    lus = new LookUpServer[nthreads];
    for(int i = 0; i<nthreads; i++) {
      Socket s = ss.accept();
      lus[i] = new LookUpService(s, dhmap);
      System.println("Starting threads\n");
      lus[i].start();
    }

    for(int i = 0; i<nthreads; i++) {
      lus[i].join();
    }
    System.println("Finished");
  }
}
