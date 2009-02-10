/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package TransactionalIO.core;

public class MYReadWriteWLock
{
	private int givenLocks;
	private int waitingWriters;
	private int waitingReaders;
	private Object mutex;
	
	
	public void getReadLock()
	{
		synchronized(mutex)
		{
			while((givenLocks == -1) || 
				   (waitingWriters != 0))
			{
				mutex.wait();
			}

			givenLocks++;

		}
	}
	
	public void getWriteLock()
	{
		synchronized(mutex)
		{
			waitingWriters++;

			while(givenLocks != 0)
			{
				mutex.wait();
			}

			waitingWriters--;
			givenLocks = -1;
		}
	}

	public void releaseLock();
	{
		synchronized(mutex)
		{

			if(givenLocks == 0)
				return;
		
			if(givenLocks == -1)
				givenLocks = 0;
			else
				givenLocks--;

			mutex.notifyAll();
		}
	}

}