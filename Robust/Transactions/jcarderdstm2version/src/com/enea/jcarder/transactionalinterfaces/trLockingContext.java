/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.transactionalinterfaces;

import dstm2.atomic;

/**
 *
 * @author navid
 */
public class trLockingContext {
    
    
    @atomic public interface holder{
        public String getThreadName();
        public void setThreadName(String name);
        public String getMethodName();
        public void setMethodName(String method);
        public String getLockRef();
        public void setLockRef(String name);
    }

}
