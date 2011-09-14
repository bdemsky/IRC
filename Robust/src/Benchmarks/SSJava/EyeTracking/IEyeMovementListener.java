/*
 * Copyright 2009 (c) Florian Frankenberger (darkblue.de)
 * 
 * This file is part of LEA.
 * 
 * LEA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * LEA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with LEA. If not, see <http://www.gnu.org/licenses/>.
 */


import de.darkblue.lea.model.Deviation;

/**
 * Implementations of eye movements and face detections should
 * look like this.
 * s
 * @author Florian Frankenberger
 */
public interface IEyeMovementListener {

	/**
	 * Is called whenever a face has been detected. Only
	 * if a face was detected <code>onEyeMoved</code> will be
	 * called (no face = no eye movement)
	 */
    public void onFaceDetected();
    
    /**
     * If the face was lost this method is being called
     */
    public void onFaceLost();

    /**
     * Gets called whenever an eye movement has been recognized
     * 
     * @param deviation the calculated deviation
     */
    public void onEyeMoved(Deviation deviation);

}
