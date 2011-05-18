/*
   System.out.println(key);
Usage :
  ./FileSystem.bin <num thread> <datafile prefix>
*/

public class FileSystem extends Thread {
  Directory root;
  Directory current;
  int mid;

  public FileSystem(Directory root, int mid) {
    this.root=root;
    this.mid = mid;
  }

  public void getRoot() {
    current=root;
  }

  public DFile getFile(GlobalString name) {
    return current.getFile(name);
  }

  public DFile createFile(GlobalString name) {
    return current.createFile(name);
  }

  public void run() {
    long st = System.currentTimeMillis();
    long fi;
    atomic {
      current=root.makeDirectory(global new GlobalString(String.valueOf(mid)));
    }
    Random r=new Random();
    System.out.println("Starting FileSystem");
    char ptr[]=new char[1024];
    //for(int i=0;i<40000;i++) {
    for(int i=0;i<15000;i++) {
      //System.out.println("i= " + i + "\n");
      atomic {
	for(int count=0;count<10;count++) {
	  int value=r.nextInt(100);
	  GlobalString filename=global new GlobalString(String.valueOf(r.nextInt(200)));
	  if (value<10) {//10% writes
        //System.out.println("Write: ");
	    //Do write
	    DFile f=getFile(filename);
	    if (f==null) {
	      f=createFile(filename);
	    }
	    f.write(10,ptr);
	  } else {
        //System.out.println("Read: ");
	    //Do read
	    DFile f=getFile(filename);
        if(f!=null)
          f.read();
	  }
        }
      }
    }
    fi = System.currentTimeMillis();
    RecoveryStat.printRecoveryStat();
    System.out.println("\n\n\n I'm done - Time Elapse : "+ ((double)(fi-st)/1000) + "\n\n\n");
    while(true) {
      sleep(100000);
    }
  }

  public static void main(String[] args) {
    int NUM_THREADS = 3;

    if (args.length == 1) {
      NUM_THREADS = Integer.parseInt(args[0]);
    }
    else {
      System.out.println("./FileSystem.bin master <num_thread>");
      System.exit(0);
    }

    int[] mid = new int[8];
    mid[0] = (128<<24)|(195<<16)|(136<<8)|162;//dc-1
    mid[1] = (128<<24)|(195<<16)|(136<<8)|163;//dc-2
    mid[2] = (128<<24)|(195<<16)|(136<<8)|164;//dc-3
    mid[3] = (128<<24)|(195<<16)|(136<<8)|165;//dc-4
    mid[4] = (128<<24)|(195<<16)|(136<<8)|166;//dc-5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167;//dc-6
    mid[6] = (128<<24)|(195<<16)|(136<<8)|168;//dc-7
    mid[7] = (128<<24)|(195<<16)|(136<<8)|169;//dc-8

    FileSystem[] lus;
    atomic {
      Directory root=global new Directory(null);
      lus = global new FileSystem[NUM_THREADS];
      for(int i = 0; i < NUM_THREADS; i++) {
        lus[i] = global new FileSystem(root, i);
      }
    }

    FileSystem tmp;
    /* Start threads */
    for(int i = 0; i < NUM_THREADS; i++) {
      atomic {
        tmp = lus[i];
      }
      Thread.myStart(tmp, mid[i]);
    }

    /* Join threads */
    for(int i = 0; i < NUM_THREADS; i++) {
      atomic {
        tmp = lus[i];
      }
      tmp.join();
    }

    System.printString("Finished\n");
  }
}
