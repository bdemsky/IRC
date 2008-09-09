/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dstm2.file.factory;

import dstm2.Defaults;


/**
 *
 * @author navid
 */
public class FileBlockManager {

    public static long getFragmentIndexofTheFile(long filesize) {
        return (filesize / Defaults.FILEFRAGMENTSIZE);
    }

    public static int getCurrentFragmentIndexofTheFile(long start) {
        return (int) ((start / Defaults.FILEFRAGMENTSIZE));
    }

    public static int getTargetFragmentIndexofTheFile(long start, int offset) {
        return (int) (((offset + start) / Defaults.FILEFRAGMENTSIZE));
    }
}
