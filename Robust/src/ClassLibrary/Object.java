public class Object {
    public native int nativehashCode();
    private int cachedCode;//first field has to be a primitive
    private boolean cachedHash;

    /* DO NOT USE ANY OF THESE - THEY ARE FOR IMPLEMENTING TAGS */
    private Object tags;

    public int hashCode() {
	if (!cachedHash) {
	    cachedCode=nativehashCode();
	    cachedHash=true;
	}
	return cachedCode;
    }

    /* DON'T USE THIS METHOD UNLESS NECESSARY */
    /* WE WILL DEPRECATE IT AS SOON AS INSTANCEOF WORKS */
    public native int getType();

    public String toString() {
	return String.valueOf(this);
    }

    public boolean equals(Object o) {
	if (o==this)
	    return true;
	return false;
    }
}
