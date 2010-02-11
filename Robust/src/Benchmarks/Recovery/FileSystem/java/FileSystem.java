/*
Usage :
  ./FileSystem.bin <num thread> <datafile prefix>
*/



public class FileSystem {
	HashMap dir;		// Directory 
	HashMap fs;		// File system
	LinkedList dir_list;
	String inputfile;
	int mid;
	
	public FileSystem(HashMap dir, HashMap fs, LinkedList dir_list, String filename) {
		this.dir = dir;
		this.fs = fs;
		this.dir_list = dir_list;
		this.inputfile = new String("../data/"+filename + "0");
	}
	
	public void init() {
		fillHashTable();
	}
	
	public void fillHashTable() {
		String path;
		LinkedList list; 

		path = new String("/home/");			// root is 'home'
		list = new LinkedList();

		dir.put(path, list);
		dir_list.add(path);
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

	public void execute() {
		Transaction t;

		char command;
		String key;
		String val;
		boolean isDir;

		int index;

		LinkedList todoList = new LinkedList();
		fillTodoList(inputfile, todoList);

		while (!todoList.isEmpty()) {
			t = (Transaction)(todoList.removeFirst());

			command = t.getCommand();
			key = t.getKey();

			index = key.lastindexOf('/');
			if (index+1 == key.length()) 
				isDir = true;
			else 
				isDir = false;
		
			if (command == 'r') {
		  		System.out.println("["+command+"] ["+key+"]");
			  	if (isDir == true) {
				  		readDirectory(key);
  				}
	  			else {
						readFile(key);
				  }
      }
			else if (command == 'c') {
	  				System.out.println("["+command+"] ["+key+"]");
  				if (isDir == true) {
		  				createDirectory(key);
    			}
		  		else {
			  		val = t.getValue();
						val = new String(val);
						createFile(key, val);
				  }
	  	}
    }

		output();

//    RecoveryStat.printRecoveryStat();
	}

	public void output() { 
		Iterator iter;
		String str;

		iter = dir_list.iterator();

		while (iter.hasNext()) {
			str = (String)(iter.next());
			System.printString(str + "\n");
		}
	}

	public void readFile(String key) {
		String val;

		val = (String)(fs.get(key));
		if (val != null) {
//			System.out.println("<"+val+">");
		}
		else {
			System.out.println("No such file or directory");
		}
	}

	public void readDirectory(String key) {
		LinkedList list;
		Iterator iter;
		String val;

		list = (LinkedList)(dir.get(key));

		if (list != null) {
			iter = list.iterator();
			while (iter.hasNext() == true) {
				val = (String)(iter.next());
//				System.out.print("["+val+"] ");
			}
//			System.out.println("");
		}
		else {
			System.out.println("No such file or directory");
		}
	}

	public void createFile(String key, String val) {
		String path;
		String target;
		int index;
		LinkedList list;

		index = key.lastindexOf('/');
		path = key.subString(0, index+1);
		target = key.subString(index+1);

		if (dir.containsKey(path)) {
			list = (LinkedList)(dir.get(path));
			list.push(target);
			dir.put(path, list);
			fs.put(key, val);
		}
		else {
			System.out.println("Cannot create file");
		}
	}

	public void createDirectory(String key) {
		int index;
		String path;
		String target;
		LinkedList list;

		index = key.lastindexOf('/', key.length()-2);

		if (index != -1) {
			path = key.subString(0, index+1);
			target = key.subString(index+1);

			if (dir.containsKey(path)) {
				list = (LinkedList)(dir.get(path));
				list.push(target);
				dir.put(path, list);

				list = new LinkedList();
				dir.put(key, list);
				dir_list.add(key);
			}
			else {
				System.out.println("Cannot create directory");
			}
		}
	}
	
	public Object read(HashMap mydhmap, String key) {
		Object obj = mydhmap.get(key); 
		
		return obj;
	}
	
	public static void main(String[] args) {
		String filename;

		if (args.length == 1) {
			filename = args[0];
		}
		else {
			System.out.println("./FileSystem.bin <data>");
			System.exit(0);
		}
		
		FileSystem file;

		HashMap fs = new HashMap(500, 0.75f);			// file system
		HashMap dir = new HashMap(500, 0.75f);			// directory
		LinkedList dir_list = new LinkedList();
		
		file = new FileSystem(dir, fs, dir_list, filename);
		file.init();

		file.execute();
		
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
