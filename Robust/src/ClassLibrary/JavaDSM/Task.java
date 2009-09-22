public class Task {
  Queue todoList;
	Queue doneList;

	Task() {}

	public void init();	
	public void execute(Object work);
	public void done(Object work);

	public Object grabTask() {
		Object o;
		atomic {
			o = todoList.pop();
		}
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
}
