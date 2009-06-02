/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package TransactionalIO.core;

//import Defaults;









/**
 *
 * @author navid
 */
public class FileBlockManager {

    public static long getFragmentIndexofTheFile(long filesize) {
        return (filesize / MyDefaults.FILEFRAGMENTSIZE);
    }

    public static int getCurrentFragmentIndexofTheFile(long start) {
        return (int) ((start / MyDefaults.FILEFRAGMENTSIZE));
    }

    public static int getTargetFragmentIndexofTheFile(long start, long offset) {
        return (int) (((offset + start) / MyDefaults.FILEFRAGMENTSIZE));
    }
}
