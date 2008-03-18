public class MapReduceBase {

    public static void map(String key, String value, OutputCollector output) {
	int n = value.length();
	for (int i = 0; i < n; ) {
	    // Skip past leading whitespace
	    while ((i < n) && isspace(value.charAt(i))) {
		++i;
	    }

	    // Find word end
	    int start = i;
	    while ((i < n) && !isspace(value.charAt(i))) {
		i++;
	    }

	    if (start < i) {
		output.emit(value.subString(start, i), "1");
		//System.printString(value.subString(start,i) + "\n");
	    }
	}
    }

    public static void reduce(String key, Vector values, OutputCollector output) {
	// Iterate over all entries with the
	// // same key and add the values
	int value = 0;
	for(int i = 0; i < values.size(); ++i) {
	    value += Integer.parseInt((String)values.elementAt(i));
	}

	// Emit sum for input->key()
	output.emit(key, String.valueOf(value));
    }

    static boolean isspace(char c) {
	if((c == ' ') || 
		(c == ',') ||
		(c == '.') ||
		(c == '!') ||
		(c == '?') ||
		(c == '"') ||
		(c == '(') ||
		(c == ')') ||
		(c == '[') ||
		(c == ']') ||
		(c == '{') ||
		(c == '}') ||
		(c == '\n')) {
	    return true;
	}
	return false;
    }
}

