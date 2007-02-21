public class Object {
    public native int hashCode();
    private Object nextlockobject;
    private Object prevlockobject;


    /* DON'T USE THIS METHOD UNLESS NECESSARY */
    /* WE WILL DEPRECATE IT AS SOON AS INSTANCEOF WORKS */
    public native int getType();

    public native int MonitorEnter();
    public native int MonitorExit();

    public String toString() {
	return String.valueOf(this);
    }

    public boolean equals(Object o) {
	if (o==this)
	    return true;
	return false;
    }
}
