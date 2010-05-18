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
     {
      current=root.makeDirectory( new GlobalString(String.valueOf(mid)));
    }
    Random r=new Random();
    char ptr[]=new char[1024];
    for(int i=0;i<40000;i++) {
       {
	for(int count=0;count<10;count++) {
	  int value=r.nextInt(100);
	  GlobalString filename= new GlobalString(String.valueOf(r.nextInt(200)));
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
    System.out.println("\n\n\n I'm done - Time Elapse : "+ ((double)(fi-st)/1000) + "\n\n\n");
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

    FileSystem[] lus;
     {
      Directory root= new Directory(null);
      lus =  new FileSystem[NUM_THREADS];
      for(int i = 0; i < NUM_THREADS; i++) {
        lus[i] =  new FileSystem(root, i);
      }
    }

    FileSystem tmp;
    /* Start threads */
    for(int i = 0; i < NUM_THREADS; i++) {
      {
        tmp = lus[i];
      }
      tmp.run();
    }

    System.printString("Finished\n");
  }
}
