public class QueryQueue {
    HashSet queries;

    public QueryQueue() {
	queries=new HashSet();
    }
    public synchronized Query getQuery() {
	if (queries.isEmpty())
	    return null;
	Query q=(Query) queries.iterator().next();
	queries.remove(q);
	return q;
    }
    public synchronized void addQuery(Query x) {
	queries.add(x);
    }
}
