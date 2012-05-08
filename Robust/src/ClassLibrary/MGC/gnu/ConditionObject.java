public class ConditionObject implements Condition {
    /**
     * Creates a new <tt>ConditionObject</tt> instance.
     */
    public ConditionObject() { }

    // public methods

    /**
     * Moves the longest-waiting thread, if one exists, from the
     * wait queue for this condition to the wait queue for the
     * owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    public final void signal() {
	this.notifyAll();
    }

    /**
     * Moves all threads from the wait queue for this condition to
     * the wait queue for the owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    public final void signalAll() {
	this.notifyAll();
    }

    /**
     * Implements uninterruptible condition wait.
     * <ol>
     * <li> Save lock state returned by {@link #getState}
     * <li> Invoke {@link #release} with
     *      saved state as argument, throwing
     *      IllegalMonitorStateException if it fails.
     * <li> Block until signalled
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * </ol>
     */
    public final void awaitUninterruptibly() {
	this.wait();
    }

    /**
     * Implements interruptible condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException
     * <li> Save lock state returned by {@link #getState}
     * <li> Invoke {@link #release} with
     *      saved state as argument, throwing
     *      IllegalMonitorStateException  if it fails.
     * <li> Block until signalled or interrupted
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw exception
     * </ol>
     */
    public final void await() throws InterruptedException {
	this.wait();
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException
     * <li> Save lock state returned by {@link #getState}
     * <li> Invoke {@link #release} with
     *      saved state as argument, throwing
     *      IllegalMonitorStateException  if it fails.
     * <li> Block until signalled, interrupted, or timed out
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException
     * </ol>
     */
    public final long awaitNanos(long nanosTimeout) throws InterruptedException {
	long lastTime = System.nanoTime();
	this.wait();
	return nanosTimeout - (System.nanoTime() - lastTime);
    }

    /**
     * Implements absolute timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException
     * <li> Save lock state returned by {@link #getState}
     * <li> Invoke {@link #release} with
     *      saved state as argument, throwing
     *      IllegalMonitorStateException  if it fails.
     * <li> Block until signalled, interrupted, or timed out
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException
     * <li> If timed out while blocked in step 4, return false, else true
     * </ol>
     */
    public final boolean awaitUntil(Date deadline) throws InterruptedException {
	long abstime = deadline.getTime();
	boolean timedout = false;
	int interruptMode = 0;
	this.wait();
	return !timedout;
    }
}