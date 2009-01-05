/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.enea.jcarder.util;

import com.enea.jcarder.transactionalinterfaces.Intif;
import com.enea.jcarder.util.logging.Logger;


/**
 *
 * @author navid
 */
public class TransactionalCounter {

    final int mLogIntervall;
    final String mName;
    final Logger mLogger;
    Intif.positionif mValue;

    public TransactionalCounter(String name, Logger logger, int logInterval) {
        mValue = Intif.factory.create();
        mValue.setPosition(0);
        mName = name;
        mLogger = logger;
        mLogIntervall = logInterval;
    }

    public void increment() {
        mValue.setPosition(mValue.getPosition()+1);
        if (((mValue.getPosition()) % mLogIntervall) == 0) {
            mLogger.fine(mName + ": " + mValue);
        } else if (mLogger.isLoggable(Logger.Level.FINEST)) {
            mLogger.finest(mName + ": " + mValue);
        }
    }
}
