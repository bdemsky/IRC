// Loops.java, created Wed Jun 13 17:38:43 1998 by bdemsky
// Copyright (C) 1999 Brian Demsky <bdemsky@mit.edu>
// Licensed under the terms of the GNU GPL; see COPYING for details.

package Analysis.Loops;

import java.util.Set;
/**
 * <code>Loops</code> contains the interface to be implemented by objects
 * generating nested loops trees.
 *
 *
 * @author  Brian Demsky <bdemsky@mit.edu>
 * @version $Id: Loops.java,v 1.1 2009/03/27 00:59:36 bdemsky Exp $
 */

public interface Loops {

    /** Returns entrances to the Loop.
     *  This is a <code>Set</code> of <code>HCodeElement</code>s.*/
    public Set loopEntrances();

    /** Returns elements of this loops and all nested loop.
     *  This is a <code>Set</code> of <code>HCodeElement</code>s.*/
    public Set loopIncElements();
    
    /** Returns elements of this loop not in any nested loop.
     *  This is a <code>Set</code> of <code>HCodeElement</code>s.*/
    public Set loopExcElements();
    
    /** Returns a <code>Set</code> containing <code>Loops</code> that are
     *  nested.*/
    public Set nestedLoops();
    
    /** Returns the loop immediately nesting this loop.
     *  If this is the highest level loop, returns a null pointer.*/
    public Loops parentLoop();
}
