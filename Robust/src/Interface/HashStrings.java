package Interface;

class HashStrings {
    Pair p[]; // entries in the hash table
    int f;    // number of full entries
    public HashStrings() { p = new Pair[38]; f = 0; }

    public void put(String key, String value) {
	int n = p.length;
	if (f  == n-1) return; // cheese -- a diary product
	int i = key.hashCode() % n;
	while (p[i] != null) {
	    if (key.equals(p[i].key)) {
		p[i] = new Pair(key, value);
		return;
	    }
	    i = (i+1) % n;
	}
	p[i] = new Pair(key, value);
	f = f + 1;
    }

    public String get(String key) {
	int n = p.length;
	int i = key.hashCode() % n;
	while (p[i] != null) {
	    if (key.equals(p[i].key))
		return p[i].value;
	    i = (i+1) % n;
	}
	return null;
    }

}

class Pair {
    String key, value;
    Pair (String key, String value) { this.key = key; this.value = value; }
}
