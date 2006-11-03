public class Object {
    public native int hashCode();

    public String toString() {
	return String.valueOf(this);
    }


    public boolean equals(Object o) {
	if (o==this)
	    return true;
	return false;
    }
}
