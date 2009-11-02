public class QueryQueue {
	HashSet queries;
	int size;
  int ddddddddddd;

	public QueryQueue() {
		queries = new HashSet();
		size = 0;
	}

	public LocalQuery pop() {
		if (queries.isEmpty())
			return null;
		LocalQuery q = (LocalQuery) queries.iterator().next();
		queries.remove(q);
		size--;
		return q;
	}

	public void push(LocalQuery x) {
		queries.add(x);
		size++;
	}
	
	public int size() {
		return size;
	}

	public boolean isEmpty() {
		if (size == 0)
			return true;
		else 
			return false;
	}
}
