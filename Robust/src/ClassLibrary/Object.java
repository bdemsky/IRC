public class Object {
    public native int hashcode();

    public boolean equals(Object o) {
	if (o==this)
	    return true;
	return false;
    }
}
