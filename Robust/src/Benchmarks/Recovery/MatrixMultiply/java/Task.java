public class Task {
  GlobalQueue todoList;
  Object myWork;

  Task() {}

  public void init();	
  public void execution();
  public void execute() {
    System.out.println("Sad");
  }

  public void done(Object work);

  public void setWork(Object work)
  {
    {
      this.myWork = work;
    }
  }

  public Object grabTask() {
    Object o;
    {
      o = todoList.pop();
    }
    //		System.out.println("Size of TodoList : " + todoList.size());
    return o;
  }

  public boolean isTodoListEmpty() {
    if (todoList.size() == 0) {
      return true;
    }
    else {
      return false;
    }
  }
  public void output() {}
}
