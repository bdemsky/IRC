public class QueryList extends Queue {
  public QueryList() {
		Queue();			// ??
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
}
