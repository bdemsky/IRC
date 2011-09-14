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


/**
 * An array with just a static size. If you add more than
 * the capacity holds the oldest element gets removed.
 * <p>
 * This List is implemented as an ring buffer array.
 * <p>
 * TODO: implement the <code>List</code> interface
 * 
 * @author Florian Frankenberger
 */
public class StaticSizeArrayList<T> {

	private Object[] buffer;
	private int startPos = 0;
	private int pos = 0;
	private int size = 0;
	
	public StaticSizeArrayList(int size) {
		this.buffer = new Object[size];
	}
	
	public synchronized void add(T item) {
		this.buffer[pos] = item;
		pos = ++pos % buffer.length;
		if (size < buffer.length) {
			size++;
		} else {
			this.startPos = ++this.startPos % this.buffer.length;
		}
	}
	
	@SuppressWarnings("unchecked")
	public synchronized T get(int i) {
		if (i >= this.size)
			throw new ArrayIndexOutOfBoundsException("Size is "+this.size+" but tried to access item "+i);
		
		int acPos = (this.startPos + i) % this.buffer.length;
		
		return (T)this.buffer[acPos];
	}
	
	public synchronized void clear() {
		this.startPos = 0;
		this.pos = 0;
		this.size = 0;
	}
	
	public synchronized int size() {
		return this.size;
	}
	
}
