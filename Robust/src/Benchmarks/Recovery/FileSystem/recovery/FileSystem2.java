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
      path = global new GlobalString("/tmp/");			// root is 'tmp'
      list = global new DistributedLinkedList();
      dir.put(path, list);
      dir_list.add(path);
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
    long tot1;

    if(todoList.isEmpty())
      System.out.println("todoList is Empty\n");

    while (!todoList.isEmpty()) {
      int count = 5;
      atomic {
        while(count>0 && !todoList.isEmpty()) { //commit 5 transactions
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

          long st1 = 0L;
          long fi1 = 0L;
          if (command == 'r') {
            st1 = System.currentTimeMillis();
            //System.out.println("["+command+"] ["+key+"]");
            if (isDir == true) {
              readDirectory(gkey);
            }
            else {
              readFile(gkey);
            }
            fi1 = System.currentTimeMillis();
          }
          tot1 += fi1 - st1;
          if (command == 'c') {
            //System.out.println("["+command+"] ["+key+"]");
            if (isDir == true) {
              createDirectory(gkey);
            }
            else {
              String val = t.getValue();
              GlobalString gval = global new GlobalString(val);
              createFile(gkey, gval);
            }
          }
          count--;
        }//end of inside loop
      }//end of atomic
    }
    fi = System.currentTimeMillis();
    RecoveryStat.printRecoveryStat();

    System.out.println("\n\n\n I'm done - Time Elapse : "+ ((double)(fi-st)/1000) + "\n\n\n");
    System.out.println("\n Reading - Time Elapse : "+ ((double)tot1/1000) + "\n");
    while(true) {
      sleep(100000);
    }
  }

  public void readFile(GlobalString gkey) {
    GlobalString gval=null;
    String val=null;

    gval = (GlobalString)(fs.get(gkey));
    if(gval!=null) {
      val = gval.toLocalString();
      //Add some useless extra work for now
      //to increase read time
      int hashVal = gval.hashCode();
      int a=0;
      for(int t=0; t<hashVal; t++) {
        for(int z=0; z<val.hashCode(); z++) {
          a = a + t + z;
        }
      }
      System.out.println("a= " + a);
    }
    if (val != null) {
      //System.out.println("<"+val+">");
    }
    else {
      //System.out.println("No such file or directory");
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
        //System.out.print("["+gval.toLocalString()+"] ");
        //Add some useless extra work for now
        int hashVal = gval.hashCode();
        int a=0;
        for(int t=0; t<hashVal; t++) {
          a = a + t;
        }
        System.out.println("a= " + a);
      }
    }
    else {
      //System.out.println("No such file or directory");
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
    FileOutputStream fos = new FileOutputStream(gpath.toLocalString()+gtarget.toLocalString());
    fos.FileOutputStream(gpath.toLocalString()+gtarget.toLocalString());
    for(int i=0; i<10; i++) {
      byte[] b = new byte[1];
      b[0] = (byte) i;
      fos.write(i);
      fos.flush();
    }
    fos.close();

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
    //System.out.println("index= " + index + " gkey= " + gkey.toLocalString());

    if (index != -1) {
      gpath = gkey.subString(0, index+1);
      gtarget = gkey.subString(index+1);
      if (dir.containsKey(gpath)) {
        list = global new DistributedLinkedList();
        dir.put(gkey, list);
      }
      else {
        //System.out.println("Cannot create directory- HERE1");
      }
    }
    else {
      //System.out.println("Cannot create directory-- HERE2");
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
        System.out.println("comm= " + comm);
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
            String val = new String();
            GlobalString gval = global new GlobalString(val);
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
