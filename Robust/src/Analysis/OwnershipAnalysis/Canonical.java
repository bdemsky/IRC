package Analysis.OwnershipAnalysis;

import IR.*;
import IR.Flat.*;
import java.util.*;
import java.io.*;

public class Canonical {

    private static Hashtable<Canonical, Canonical> canon = new Hashtable<Canonical, Canonical>();

    public static Canonical makeCanonical( Canonical c ) {
	if( canon.containsKey( c ) ) {
	    return canon.get( c );
	}

	canon.put( c, c );
	return c;
    }
}
