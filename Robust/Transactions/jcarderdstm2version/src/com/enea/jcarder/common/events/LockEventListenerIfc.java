/*
 * JCarder -- cards Java programs to keep threads disentangled
 *
 * Copyright (C) 2006-2007 Enea AB
 * Copyright (C) 2007 Ulrik Svensson
 * Copyright (C) 2007 Joel Rosdahl
 *
 * This program is made available under the GNU GPL version 2, with a special
 * exception for linking with JUnit. See the accompanying file LICENSE.txt for
 * details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.enea.jcarder.common.events;

import java.io.IOException;
import java.util.Vector;

public interface LockEventListenerIfc {

    void onLockEvent(int lockId,
                     int lockingContextId,
                     int lastTakenLockId,
                     int lastTakenLockingContextId,
                     long threadId)throws IOException;
    
    void tronLockEvent(Vector srgs)throws IOException;
}



