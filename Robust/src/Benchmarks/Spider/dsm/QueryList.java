public class QueryList extends Queue {
	Queue queries;

  public QueryList() {
		queries = global new Queue();
  }

  public boolean checkQuery(GlobalString x) {
		boolean set = false;;
		for (int i = 0 ; i < size; i++) {
			if (x.equals((GlobalString)elements[i])) {
				set = true;
				break;
			}
		}
		return set;
  }

	public void addQuery(GlobalString x) {
		queries.push(x);
	}
}
