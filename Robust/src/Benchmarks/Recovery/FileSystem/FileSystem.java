/*
Usage :
  ./FileSystem.bin <num thread> <datafile prefix>
*/



public class FileSystem extends Thread {
	DistributedHashMap dir;		// Directory 
	DistributedHashMap fs;		// File 
	DistributedLinkedList dir_list;
	GlobalString inputfile;
	int mid;
	
	public FileSystem(DistributedHashMap dir, DistributedHashMap fs, DistributedLinkedList dir_list) {
		this.dir = dir;
		this.fs = fs;
		this.dir_list = dir_list;
	}
	
	public FileSystem(DistributedHashMap dir, DistributedHashMap fs, DistributedLinkedList dir_list, String filename, int mid) {
		this.dir = dir;
		this.fs = fs;
		this.dir_list = dir_list;
		this.mid = mid;
		this.inputfile = global new GlobalString("data/"+filename + mid);
	}


	public void setInputFileName(String filename, int mid) {
		this.mid = mid;
		this.inputfile = global new GlobalString("data/"+filename + mid);
	}

	public void init() {
		fillHashTable();
	}
	
	public void fillHashTable() {
		GlobalString path;
		DistributedLinkedList list; 

		atomic {
			path = global new GlobalString("/home/");			// root is 'home'
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
		String val;
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

		char command;
		String key;
		String val;
		boolean isDir;

		GlobalString gkey;
		GlobalString gval;

		int index;
		String file;
		atomic {
			file = inputfile.toLocalString();
		}

		LinkedList todoList = new LinkedList();
		fillTodoList(file, todoList);

		while (!todoList.isEmpty()) {
			t = (Transaction)(todoList.removeFirst());

			command = t.getCommand();
			key = t.getKey();

			index = key.lastindexOf('/');
			if (index+1 == key.length()) 
				isDir = true;
			else 
				isDir = false;
		
			atomic {
				gkey = global new GlobalString(key);
			}

			if (command == 'r') {
		  		System.out.println("["+command+"] ["+key+"]");
    		atomic {
			  	if (isDir == true) {
				  		readDirectory(gkey);
  				}
	  			else {
						readFile(gkey);
				  }
			  }
      }
			else if (command == 'c') {
	  				System.out.println("["+command+"] ["+key+"]");
				atomic {
  				if (isDir == true) {
		  				createDirectory(gkey);
    			}
		  		else {
			  		val = t.getValue();
						gval = global new GlobalString(val);
						createFile(gkey, gval);
				  }
  			}
	  	}
    }

		sleep(3000000);
		atomic {
			output();
		}

    RecoveryStat.printRecoveryStat();
	}

	public void output() { 
		Iterator iter;
		GlobalString gstr;

		iter = dir_list.iterator();

		while (iter.hasNext()) {
			gstr = (GlobalString)(iter.next());
			System.printString(gstr.toLocalString() + "\n");
		}
	}

	public void readFile(GlobalString gkey) {
		GlobalString gval;

		gval = (GlobalString)(fs.get(gkey));
		if (gval != null) {
//			System.out.println("<"+gval.toLocalString()+">");
		}
		else {
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
//				System.out.print("["+gval.toLocalString()+"] ");
			}
//			System.out.println("");
		}
		else {
			System.out.println("No such file or directory");
		}
	}

	public void createFile(GlobalString gkey, GlobalString gval) {
		GlobalString path;
		GlobalString target;
		int index;
		DistributedLinkedList list;

		index = gkey.lastindexOf('/');
		path = gkey.subString(0, index+1);
		target = gkey.subString(index+1);

		if (dir.containsKey(path)) {
			list = (DistributedLinkedList)(dir.get(path));
			list.push(target);
			dir.put(path, list);
			fs.put(gkey, gval);
		}
		else {
			System.out.println("Cannot create file");
		}
	}

	public void createDirectory(GlobalString gkey) {
		int index;
		GlobalString path;
		GlobalString target;
		DistributedLinkedList list;

		index = gkey.lastindexOf('/', gkey.length()-2);

		if (index != -1) {
			path = gkey.subString(0, index+1);
			target = gkey.subString(index+1);

			if (dir.containsKey(path)) {
				list = (DistributedLinkedList)(dir.get(path));
				list.push(target);
				dir.put(path, list);

				list = global new DistributedLinkedList();
				dir.put(gkey, list);
				dir_list.add(gkey);
			}
			else {
				System.out.println("Cannot create directory");
			}
		}
	}
	
	public Object read(DistributedHashMap mydhmap, GlobalString key) {
		Object obj = mydhmap.get(key); 
		
		return obj;
	}
	
	public static void main(String[] args) {
		int NUM_THREADS = 3;
		String filename;

		NUM_THREADS = Integer.parseInt(args[0]);
		filename = args[1];
		
		int[] mid = new int[8];
		mid[0] = (128<<24)|(195<<16)|(180<<8)|21;//dw-2
		mid[1] = (128<<24)|(195<<16)|(180<<8)|26;//dw-7
/*
		mid[0] = (128<<24)|(195<<16)|(136<<8)|162;//dc-1
		mid[1] = (128<<24)|(195<<16)|(136<<8)|163;//dc-2
		mid[2] = (128<<24)|(195<<16)|(136<<8)|164;//dc-3
		mid[3] = (128<<24)|(195<<16)|(136<<8)|165;//dc-4
		mid[4] = (128<<24)|(195<<16)|(136<<8)|166;//dc-5
    mid[5] = (128<<24)|(195<<16)|(136<<8)|167;//dc-6
		mid[6] = (128<<24)|(195<<16)|(136<<8)|168;//dc-7
		mid[7] = (128<<24)|(195<<16)|(136<<8)|169;//dc-8
	*/
		FileSystem[] lus;
		FileSystem initLus;

		Work[] works;
		Transaction[] currentWorkList;		// type might be something else
		
		atomic {
			currentWorkList = global new Transaction[NUM_THREADS];		// something else
			works = global new Work[NUM_THREADS];
			
			DistributedHashMap fs = global new DistributedHashMap(500, 500, 0.75f);
			DistributedHashMap dir = global new DistributedHashMap(500, 500, 0.75f);
			DistributedLinkedList dir_list = global new DistributedLinkedList();
		
			initLus = global new FileSystem(dir, fs, dir_list);
			initLus.init();

			lus = global new FileSystem[NUM_THREADS];
			for(int i = 0; i < NUM_THREADS; i++) {
//				lus[i] = initLus;
//				lus[i].setInputFileName(filename, i);
				lus[i] = global new FileSystem(initLus.dir, initLus.fs, initLus.dir_list, filename, i);
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

public class Transaction {			// object for todoList
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
