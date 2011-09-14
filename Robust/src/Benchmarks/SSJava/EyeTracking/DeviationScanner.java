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


import java.awt.geom.Rectangle2D;

/**
 * No description given.
 * 
 * @author Florian Frankenberger
 */
public class DeviationScanner {

	private StaticSizeArrayList<EyePosition> eyePositions = new StaticSizeArrayList<EyePosition>(3);
	
	public DeviationScanner() {
	}

	public void addEyePosition(EyePosition eyePosition) {
		eyePositions.add(eyePosition);
	}
	
	public Deviation scanForDeviation(Rectangle2D faceRect) {
		Deviation deviation = Deviation.NONE;
		if (eyePositions.size() >= 3) {
			double deviationX = 0;
			double deviationY = 0;
			
			EyePosition lastEyePosition = null;
			for (int i = 0; i < 3; ++i) {
				EyePosition eyePosition = this.eyePositions.get(i);
				if (lastEyePosition != null) {
					deviationX += (eyePosition.getX() - lastEyePosition.getX());
					deviationY += (eyePosition.getY() - lastEyePosition.getY());
				}
				lastEyePosition = eyePosition; 
			}
			
			final double deviationPercentX = 0.04;
			final double deviationPercentY = 0.04;
			
			deviationX /= faceRect.getWidth();
			deviationY /= faceRect.getWidth();
			
			int deviationAbsoluteX = 0;
			int deviationAbsoluteY = 0;
			if (deviationX > deviationPercentX) deviationAbsoluteX = 1;
			if (deviationX < -deviationPercentX) deviationAbsoluteX = -1;
			if (deviationY > deviationPercentY) deviationAbsoluteY = 1;
			if (deviationY < -deviationPercentY) deviationAbsoluteY = -1;
			
			deviation = Deviation.getDirectionFor(deviationAbsoluteX, deviationAbsoluteY);
			if (deviation != Deviation.NONE) this.eyePositions.clear();
			//System.out.println(String.format("%.2f%% | %.2f%% => %d and %d >>> %s", deviationX*100, deviationY*100, deviationAbsoluteX, deviationAbsoluteY, deviation.toString()));
			
		}
		
		return deviation;
	}
	
	public void clear() {
		System.out.println("CLEAR");
		this.eyePositions.clear();
	}
}
