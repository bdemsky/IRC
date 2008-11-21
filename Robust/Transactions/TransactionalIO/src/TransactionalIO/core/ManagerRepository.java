/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

import TransactionalIO.interfaces.ContentionManager;


/**
 *
 * @author navid
 */
public class ManagerRepository {
    private static ContentionManager blockcm;
    private static ContentionManager offsetcm;

    public synchronized static ContentionManager getBlockcm() {
        return blockcm;
    }

    public synchronized static void setBlockcm(ContentionManager blockcm) {
        ManagerRepository.blockcm = blockcm;
    }

    public synchronized static ContentionManager getOffsetcm() {
        return offsetcm;
    }

    public synchronized static void setOffsetcm(ContentionManager offsetcm) {
        ManagerRepository.offsetcm = offsetcm;
    }
    

}
