public class Work extends Thread {
  Task tasks;
	Object[] currentWorkList;
	int MY_MID;
	int NUM_THREADS;

	Work (Task tasks, int num_threads, int mid, Object[] currentWorkList) {
		this.tasks = tasks;
		this.currentWorkList = currentWorkList;
		NUM_THREADS = num_threads;
		MY_MID = mid;
	}

	public void run() {
    int workMID;
    atomic {
      workMID = MY_MID;
    }

    Task localTask;
    int chk; 
    int result;
    int i,j;
    int cc;
		boolean isEmpty; 

    while(true) {
      atomic {
					isEmpty = tasks.isTodoListEmpty();		// flag > !keep assigning 
			
          if (!isEmpty) {
      			currentWorkList[workMID] = tasks.grabTask();	/* grab the work from work pool */
            chk = 1;
          }
		    	else {
            chk = Work.checkCurrentWorkList(this);
        }
      }

      if(chk == 1) {    // still have work
        atomic {
          tasks.setWork(currentWorkList[workMID]);
          localTask = tasks;
        }

        /* compute */
        localTask.execution();

        atomic {
          /* push into done list */
          tasks.done(currentWorkList[workMID]);
					currentWorkList[workMID] = null;
          cc = ((Drinker)tasks).ownTotal;
        }
      }
      else if(chk  == -1) {    // finished all work
        break;
      }
      else {    // wait for other thread
				sleep(5000000); 
      }

    }

    /* for debugging purpose */
    atomic {
      System.out.println("\n\nDoneSize = " + tasks.doneList.size());
    }
    System.out.println("\n\n\n I'm done");

  }

	public static int checkCurrentWorkList(Work mywork) {		
    int i;
    int index = -1;
    int myID;
		int num_threads; 
    int status;
    boolean chk = false;
    Object s;

		atomic {
	    myID = mywork.MY_MID;
			num_threads = mywork.NUM_THREADS;
		}

    for(i = 0 ; (i < num_threads) && (index < 0); i++) {
      if(myID == i) {
        continue;
      }
			status = Thread.getStatus(i);

      atomic {

        s = mywork.currentWorkList[i];

        if(status == -1 && null != s) {
          mywork.currentWorkList[myID] = mywork.currentWorkList[i];
          mywork.currentWorkList[i] = null;
          index = 0;
        }
        else if(null != s) {
          chk = true;
        }
      }
			
    }

    if(index == 0)  // grabbed dead machine's work
      return 1;
    else if(i == num_threads && index < 0 && chk != true)  // wait for other machine's work
      return -1;
    else
      return 0; // others are still working wait until they finish work
  }
}

