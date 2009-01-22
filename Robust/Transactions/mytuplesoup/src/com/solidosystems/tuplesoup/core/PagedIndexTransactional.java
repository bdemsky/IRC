/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.solidosystems.tuplesoup.core;

import dstm2.AtomicArray;
import dstm2.atomic;

/**
 *
 * @author navid
 */
public class PagedIndexTransactional {

       public @atomic interface PageIndexTSInf{
       Long getStat_read();
       Long getStat_write();
       Long getStat_create_page();
       Long getStat_page_next();
       Long getStat_page_branch();
       AtomicArray<TableIndexPageTransactional> getRoots();
       
       void setRoots(AtomicArray<TableIndexPageTransactional> roots);
       void setStat_read(Long val);
       void setStat_write(Long val);
       void setStat_create_page(Long val);
       void setStat_page_next(Long val);
       void setStat_page_branch(Long val);
    }
    
    protected static final int INITIALPAGEHASH=1024;
    protected static final int PAGESIZE=2048;
}
