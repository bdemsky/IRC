public class Drinker extends Task {
  int ownTotal;
	
	public Drinker(int num_threads,Queue todo,Queue done) {
    ownTotal = 0;
    todoList = todo;
    doneList = done;
	}

   // fill up the Work Pool
	public void fillTodoList(Queue todoList, int size) {
    Segment seg;
    int i;

    for(i = 0; i < size; i += 10) {
      seg = global new Segment(10);
      todoList.push(seg);
    }
    
    System.out.println("TodoSIZE = " + todoList.size());
	}
  
	public Object grabTask() {
    atomic {
  		if (todoList.size() != 0) {
        return (Segment)todoList.pop();
  		}
    }
    return null;
	}

	public void execute() {
    Segment s;
    atomic {
      s = (Segment)myWork;
      ownTotal += s.x;    
    }
  }

	public void done(Object work) {
			doneList.push(work);
	}

  public static void main(String[] args) {
		int NUM_THREADS;
    int i,j;
    int size = Integer.parseInt(args[1]);
		Work[] work;
		Drinker[] drinkers;
    Segment[] currentWorkList;
    Queue todoList;
    Queue doneList;

		if (args.length > 0) {
			NUM_THREADS = Integer.parseInt(args[0]);
		}

		int[] mid = new int[NUM_THREADS];
		mid[0] = (128<<24)|(195<<16)|(180<<8)|21; //dw-2
		mid[1] = (128<<24)|(195<<16)|(180<<8)|24; //dw-5
/*		mid[0] = (128<<24)|(195<<16)|(136<<8)|164; //dc3
		mid[1] = (128<<24)|(195<<16)|(136<<8)|165; //dc4
		mid[2] = (128<<24)|(195<<16)|(136<<8)|166; //dc5
		mid[3] = (128<<24)|(195<<16)|(136<<8)|167; //dc6
		mid[4] = (128<<24)|(195<<16)|(136<<8)|168; //dc7
		mid[5] = (128<<24)|(195<<16)|(136<<8)|169; //dc8*/

		atomic {
			drinkers = global new Drinker[NUM_THREADS];
      todoList = global new Queue(500);
      doneList = global new Queue(500);

      work = global new Work[NUM_THREADS];
      currentWorkList = global new Segment[NUM_THREADS];

      drinkers[0] = global new Drinker(NUM_THREADS,todoList,doneList);
      drinkers[0].fillTodoList(todoList,size);
      work[0] = global new Work(drinkers[0], NUM_THREADS, 0 , currentWorkList);

			for(i = 1; i < NUM_THREADS; i++) {
        drinkers[i] = global new Drinker(NUM_THREADS,todoList,doneList);
				work[i] = global new Work(drinkers[i], NUM_THREADS, i,currentWorkList);
			}
		}

    System.out.println("Finished initialization");
		Work tmp;
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = work[i];
			}
			Thread.myStart(tmp,mid[i]);
		}

    System.out.println("Finished Starting Threads");
		for (i = 0; i < NUM_THREADS; i++) {
			atomic {
				tmp = work[i];
			}
      System.out.println("Thread id " + i + " is joining");
			tmp.join();
		}
    
    System.printString("Finished\n");
	}
}

public class Segment {
  int x;

	Segment (int x) {
    this.x = x;
	}

  public String toString()
  {
    return "lol";
  }
}

