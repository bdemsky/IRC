/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import java.io.IOException;

/**
 *
 * @author navid
 */
public abstract class TupleStreamTransactional {
    public abstract boolean hasNext() throws IOException;
    public abstract RowTransactional next() throws IOException;
}
