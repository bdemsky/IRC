public class LookUpService extends Task {
	DistributedHashMap dir;
	DistributedHashMap fs;
	GlobalString error[];			// String for incorrect path, etc.
	
	public LookUpService(Queue todoList, DistributedHashMap dir, DistributedHashMap fs) {
		this.todoList = todoList;
		this.dir = dir;
		this.fs = fs;
	}
	
	public void init() {
		makeErrorStatement();
		fillHashTable();
		fillTodoList();
	}

	public void makeErrorStatement() {
		int num = 4;
		int i = 0;

		atomic {
			error = global new GlobalString[num];

			error[i++] = global new GlobalString("/hkhang/error/");						// w: no root (hkhang),	r: non existed path
			error[i++] = global new GlobalString("/home/abc/def/ghi/");				// w: create multiple directories, r: non existed path
			error[i++] = global new GlobalString("/home/hkhang/abc");					// w: create directory and file together
			error[i++] = global new GlobalString("/home/hkhang/abc/def");			// w: create directory and file together
		}
	}
	
	public void fillHashTable() {
		GlobalString path;
		DistributedLinkedList list; 

		atomic {
			path = global new GlobalString("/home/");			// root is home
			list = global new DistributedLinkedList();

			dir.put(path, list);
		}
	}
	
	public void fillTodoList() {
		GlobalString directory;
		GlobalString file;
		GlobalString val;
		String str;
		String str2;
		Transaction t;
		char c;

		atomic {
			c = 'w';

			directory = global new GlobalString("/home/folder/");
			str = new String("/home/folder/");
			t = global new Transaction(c, directory);
			todoList.push(t);

			for (int i = 0; i < 1000; i++) {
				file = global new GlobalString(str+"file_"+i);
				str2 = new String(str+"file_"+i);
				val = global new GlobalString("This is "+str2);
				t = global new Transaction(c, file, val);
				todoList.push(t);
			}
		}

		int rdprob = 93;
		int wrtprob = 98;
		int dirprob = 90;
		int rdwr;
		int isdir;
		int findex;
		Random rand = new Random(0);

		atomic {
			for (int i = 0; i < 10000; i++) {
				rdwr = rand.nextInt(100);
				isdir = rand.nextInt(100);
				findex = rand.nextInt(1000);
	
				if (rdwr < rdprob) {				// read
					c = 'r';
					if (isdir < dirprob) {		// file
						file = global new GlobalString(str+"file_"+findex);
						t = global new Transaction(c, file);
					}
					else {										// dir
						directory = global new GlobalString(str);
						t = global new Transaction(c, directory);
					}
				}
				else if (rdwr >= rdprob && rdwr < wrtprob) {		// write
					c = 'w';
					if (isdir < dirprob) {		// file
						file = global new GlobalString(str+"file_"+findex);
						str2 = new String(str+"file_"+findex);
						val = global new GlobalString(str2+" has been modified!!");
						t = global new Transaction(c, file, val);
					}
					else {										// dir
						directory = global new GlobalString(str+"new_dir_"+findex+"/");
						t = global new Transaction(c, directory);
					}
				}
				else {			// error
					int err = rand.nextInt(4);
					file = error[err];
					val = global new GlobalString("It is error path!!");
					t = global new Transaction(c, file, val);
				}
				todoList.push(t);
			}
		}
	}
	
	public void execute() {
		char command;
		boolean isDir;
		GlobalString gkey;
		GlobalString gval;
		int index;

		String key;
		String val;

		atomic {
			command = ((Transaction)myWork).getCommand();
			gkey = ((Transaction)myWork).getKey();

			key = gkey.toLocalString();
			index = gkey.lastindexOf('/');
			if (index+1 == gkey.length()) 
				isDir = true;
			else 
				isDir = false;
		}

		if (command == 'r') {	
			System.out.println("["+command+"] ["+key+"]");
			if (isDir == true) {
				atomic {
					readDirectory(gkey);
				}
			}
			else {
				atomic {
					readFile(gkey);
				}
			}
		}
		else if (command == 'w') {	
			if (isDir == true) {
				System.out.println("["+command+"] ["+key+"]");
				atomic {
					createDirectory(gkey);
				}
			}
			else {
				atomic {
					gval = ((Transaction)myWork).getValue();
					val = gval.toLocalString();
				}
				System.out.println("["+command+"] ["+key+"] ["+val+"]");
				atomic {
					createFile(gkey, gval);
				}
			}
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
			}
			else {
				System.out.println("Cannot create directory");
			}
		}
	}
	
	public void createFile(GlobalString gkey) {
	}

	public Object read(DistributedHashMap mydhmap, GlobalString key) {
		Object obj = mydhmap.get(key); 
		
		return obj;
	}
	
	public static void main(String[] args) {
		int NUM_THREADS = 3;

		NUM_THREADS = Integer.parseInt(args[0]);
		
		int[] mid = new int[NUM_THREADS];
//		mid[0] = (128<<24)|(195<<16)|(180<<8)|21;//dw-2
//		mid[0] = (128<<24)|(195<<16)|(180<<8)|24;//dw-5
//		mid[1] = (128<<24)|(195<<16)|(180<<8)|26;//dw-7
		mid[0] = (128<<24)|(195<<16)|(136<<8)|166;//dc-5
		mid[1] = (128<<24)|(195<<16)|(136<<8)|167;//dc-6
		mid[2] = (128<<24)|(195<<16)|(136<<8)|168;//dc-7
		
		LookUpService[] lus;
		LookUpService initLus;

		Work[] works;
		Transaction[] currentWorkList;		// type might be something else
		
		atomic {
			Queue todoList = global new Queue();
			
			currentWorkList = global new Transaction[NUM_THREADS];		// something else
			works = global new Work[NUM_THREADS];
			
			DistributedHashMap fs = global new DistributedHashMap(500, 500, 0.75f);
			DistributedHashMap dir = global new DistributedHashMap(500, 500, 0.75f);
		
			initLus = global new LookUpService(todoList, dir, fs);
			initLus.init();

			lus = global new LookUpService[NUM_THREADS];
			for(int i = 0; i < NUM_THREADS; i++) {
				lus[i] = global new LookUpService(initLus.todoList, initLus.dir, initLus.fs);
				works[i] = global new Work(lus[i], NUM_THREADS, i, currentWorkList);
			}
		}

		Work tmp;
		/* Start threads */
		for(int i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			Thread.myStart(tmp, mid[i]);
		}
		
		/* Join threads */
		for(int i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = works[i];
			}
			tmp.join();
		}
		
		System.printString("Finished\n");
	}
}

public class Transaction {			// object for todoList
	char command;		// 'r'ead, 'w'rite
	GlobalString key;
	GlobalString val;
	
	Transaction (char c, GlobalString key) {
		command = c;
		
		atomic {
			this.key = global new GlobalString(key);
		}
	}
	
	Transaction (char c, GlobalString key, GlobalString val) {
		command = c;
		
		atomic {
			this.key = global new GlobalString(key);
			this.val = global new GlobalString(val);
		}
	}
	
	public char getCommand() {
		return command;
	}
	
	public GlobalString getKey() {
		return key;
	}
	
	public GlobalString getValue() {
		return val;
	}
}
