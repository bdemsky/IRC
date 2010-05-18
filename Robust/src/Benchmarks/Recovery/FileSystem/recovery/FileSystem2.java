/*
   System.out.println(key);
Usage :
  ./FileSystem.bin <num thread> <datafile prefix>
*/

public class FileSystem extends Thread {
  DistributedHashMap dir;		// Directory 
  DistributedHashMap fs;		// File 
  DistributedLinkedList dir_list;
  GlobalString inputfile;
  int mid;
  int threadid;

  public FileSystem(DistributedHashMap dir, DistributedHashMap fs, DistributedLinkedList dir_list) {
    this.dir = dir;
    this.fs = fs;
    this.dir_list = dir_list;
  }

  public FileSystem(DistributedHashMap dir, DistributedHashMap fs, DistributedLinkedList dir_list, String filename, int mid,int threadid) {
    this.dir = dir;
    this.fs = fs;
    this.dir_list = dir_list;
    this.mid = mid;
    this.threadid = threadid;
    this.inputfile = global new GlobalString("../data/"+filename + mid);
  }


  public void setInputFileName(String filename, int mid) {
    this.mid = mid;
    this.inputfile = global new GlobalString("../data/"+filename + mid);
  }

  public void init() {
    fillHashTable();
  }

  public void fillHashTable() {
    GlobalString path;
    DistributedLinkedList list; 

    atomic {
      path = global new GlobalString("/home/adash/");			// root is 'home'
      list = global new DistributedLinkedList();
      dir.put(path, list);
    }
  }

  public static void fillTodoList(String file, LinkedList todoList) {
    FileInputStream fis;
    String comm;
    char c;
    String key;
    Transaction t;

    fis = new FileInputStream(file);

    while ((comm = fis.readLine()) != null) {			// 'command' 'path'
      c = comm.charAt(0);													// ex) w /home/abc.c 
      key = comm.subString(2);
      t = new Transaction(c, key);
      todoList.add(t);
    }
  }

  public void run() {
    Transaction t;
    String file;
    atomic {
      file = inputfile.toLocalString();
    }
    System.out.println("file= " + file);
    LinkedList todoList = new LinkedList();
    fillTodoList(file, todoList);
    long st = System.currentTimeMillis();
    long fi;
    long tot1=0, tot2=0;

    if(todoList.isEmpty())
      System.out.println("todoList is Empty\n");

    while (!todoList.isEmpty()) {
      int count = 10;
      atomic {
        System.out.println("trans1, count= "+ count);
        while(count>0 && !todoList.isEmpty()) { //commit 10 transactions
          t = (Transaction)(todoList.removeFirst());
          if(t==null) {
            count--;
            continue;
          }
          char command = t.getCommand();
          String key = t.getKey();
          GlobalString gkey = global new GlobalString(key);

          int index = key.lastindexOf('/');
          boolean isDir;
          if (index+1 == key.length()) 
            isDir = true;
          else 
            isDir = false;

          if (command == 'r') {
            long st1 = System.currentTimeMillis();
            System.out.println("["+command+"] ["+key+"]");
            if (isDir != true) {
              readFile(gkey);
            }
            long fi1 = System.currentTimeMillis();
            tot1 += fi1 - st1;
          }

          if (command == 'c') {
            long st2 = System.currentTimeMillis();
            System.out.println("["+command+"] ["+key+"]");
            if (isDir != true) {
              String val = "Testrun";
              GlobalString gval = global new GlobalString(val);
              writetoFile(gkey, gval);
            }
            long fi2 = System.currentTimeMillis();
            tot2 += fi2 - st2;
          }
          count--;
        }//end of inside loop
      }//end of atomic
    }
    fi = System.currentTimeMillis();
    RecoveryStat.printRecoveryStat();

    System.out.println("\n\n\n I'm done - Time Elapse : "+ ((double)(fi-st)/1000) + "\n\n\n");
    System.out.println("\n Reading - Time Elapse : "+ ((double)tot1/1000) + "\n");
    System.out.println("\n Creating - Time Elapse : "+ ((double)tot2/1000) + "\n");
    while(true) {
      sleep(100000);
    }
  }

  public void readFile(GlobalString gkey) {
    GlobalString gval=null;
    String val=null;
    int FILE_SIZE = 4096;

    gval = (GlobalString)(fs.get(gkey));
    if(gval!=null) {
      val = gval.toLocalString();
      //System.out.println("readFile(): ["+gkey.toLocalString()+"] ");
      //Add some useless extra work for now
      //to increase read time
      int[] b = new int[4096];
      for(int i = 0; i< 4096; i++) {
        b[i] = 0;
      }
      /*
      String filename = gkey.toLocalString();
      FileInputStream inputFile = new FileInputStream(filename);
      int n;
      byte b[] = new byte[FILE_SIZE];
      while ((n = inputFile.read(b)) != 0) {
        for(int x=0; x<n; x++) {
          byte buf = b[x];
        }
      }
      inputFile.close();
      */
      /*
      int hashVal = val.hashCode();
      int a=0;
      for(int t=0; t<hashVal; t++) {
          a = a + t;
      }
      */
    }
    if (val == null) {
      System.out.println("No such file or directory");
    }
  }

  public void readDirectory(GlobalString gkey) {
    DistributedLinkedList list;
    Iterator iter;
    GlobalString gval;

    list = (DistributedLinkedList)(dir.get(gkey));

    if (list != null) {
      iter = list.iterator();
      while (iter.hasNext() == true) {
        gval = (GlobalString)(iter.next());
      }
    }
    else {
      System.out.println("No such file or directory");
    }
  }

  public void writetoFile(GlobalString gkey, GlobalString gval) {
    int index = gkey.lastindexOf('/');
    GlobalString gpath = gkey.subString(0, index+1);
    GlobalString gtarget = gkey.subString(index+1);
    /*
    String s = gpath.toLocalString()+gtarget.toLocalString();
    FileInputStream inputFile = new FileInputStream(s);
    int n;
    byte b[] = new byte[10];
    while ((n = inputFile.read(b)) != 0) {
      for(int x=0; x<10; x++) {
        b[x] = (byte)(x+1);
      }
    }
    inputFile.close();
    */
    if (dir.containsKey(gpath)) {
      fs.put(gkey, gval);
    }
  }

  public void createFile(GlobalString gkey, GlobalString gval) {
    String path;
    String target;
    GlobalString gpath;
    GlobalString gtarget;
    int index;
    DistributedLinkedList list;

    index = gkey.lastindexOf('/');
    gpath = gkey.subString(0, index+1);
    gtarget = gkey.subString(index+1);
    String s = gpath.toLocalString()+gtarget.toLocalString();
    /*
    FileOutputStream fos = new FileOutputStream(s);
    fos.FileOutputStream(s);
    byte[] b = new byte[1];
    for(int i=0; i<4096; i++) {
      b[0] = (byte) i;
      fos.write(b);
      fos.flush();
    }
    fos.close();
    */

    if(dir==null)
      System.out.println("dir is null");

    if (dir.containsKey(gpath)) {
      fs.put(gkey, gval);
    }
    else {
      System.out.println("Cannot create file");
    }
  }

  public void createDirectory(GlobalString gkey) {
    int index;
    GlobalString gpath;
    GlobalString gtarget;
    DistributedLinkedList list;

    index = gkey.lastindexOf('/', gkey.length()-2);

    if (index != -1) {
      gpath = gkey.subString(0, index+1);
      gtarget = gkey.subString(index+1);
      if (dir.containsKey(gpath)) {
        list = global new DistributedLinkedList();
        dir.put(gkey, list);
      }
      else {
        System.out.println("Cannot create directory- HERE1");
      }
    }
    else {
      System.out.println("Cannot create directory-- HERE2");
    }
  }

  public static void main(String[] args) {
    int NUM_THREADS = 3;
    String filename = new String();

    if (args.length == 2) {
      NUM_THREADS = Integer.parseInt(args[0]);
      filename = args[1];
      System.out.println("filename= " + filename);
    }
    else {
      System.out.println("./FileSystem.bin master <num_thread> <data>");
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
    FileSystem initLus;
    atomic {
      DistributedHashMap fs = global new DistributedHashMap(500, 0.75f);
      DistributedHashMap dir = global new DistributedHashMap(500, 0.75f);
      DistributedLinkedList dir_list = global new DistributedLinkedList();
      initLus = global new FileSystem(dir, fs, dir_list);
      initLus.init();

      String filename1 = "../data/creates.txt";
      FileInputStream fis = new FileInputStream(filename1);
      String comm;
      //Create and populate the distributed hash map
      boolean isDir;
      while((comm = fis.readLine()) != null) {
        char command = comm.charAt(0);
        String key = comm.subString(2);
        GlobalString gkey = global new GlobalString(key);
        int index = key.lastindexOf('/');
        if (index+1 == key.length()) 
          isDir = true;
        else 
          isDir = false;
        if(command == 'c') {
          if(isDir == true) {
            initLus.createDirectory(gkey);
          } else {
            GlobalString target = gkey.subString(index+1);
            String val = new String(target.toLocalString());
            GlobalString gval = global new GlobalString(val);
            //System.out.println("Creating file: " + key);
            initLus.createFile(gkey, gval);
          }
        }
      }
      fis.close();

      lus = global new FileSystem[NUM_THREADS];
      for(int i = 0; i < NUM_THREADS; i++) {
        lus[i] = global new FileSystem(initLus.dir, initLus.fs, initLus.dir_list, filename, i,mid[i]);
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

public class Transaction {  // object for todoList
  char command;				// r: read, w: write
  String key;
  String val;

  Transaction (char c, String key) {
    command = c;

    this.key = new String(key);
    this.val = new String();
  }

  Transaction (char c, String key, String val) {
    command = c;

    this.key = new String(key);
    this.val = new String(val);
  }

  public char getCommand() {
    return command;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return val;
  }
}
