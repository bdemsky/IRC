public class QueryList {
    flag initialized;
    HashSet queries;

    public QueryList() {
	queries=new HashSet();
    }
    public boolean checkQuery(String x) {
	return queries.contains(x);
    }
    public void addQuery(String x) {
	queries.add(x);
    }
}
