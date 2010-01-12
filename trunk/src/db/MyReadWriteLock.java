package db;

/**
 * The read-write lock similar to the ReadWriteLock interface.
 * The implementation uses three counters: numbers of active writers, active
 * readers and pending writers. The read-lock is locked only if there are no other
 * active writers or pending writers. The write-lock is locked only if there are no
 * other active writers or active readers. If a writer is waiting, then new read-locks
 * won't be given, but it will wait for all read-locks to release, then give the 
 * lock to writer. (Writer is preferred). Alternative implementation could make 
 * preference for reader -- if reader wants to lock, check only whether writer 
 * doesn't have lock, and allocate to reader even if writer is pending.
 * 
 * Modified for lab6 based on comment from lab3: simplified the implementation
 * to have getReadLock, getWriteLock, releaseReadLock and releaseWriteLock.
 * 
 * @author Mamta
 */
public class MyReadWriteLock {

	private int writersActive = 0; // valid values are only 0 and 1.
	private int readersActive = 0; // if readers locked, then count of active readers
	private int writersPending = 0; // pending writers that want to grab write lock
    
	/**
	 * Implement the read lock.
	 */
	public synchronized void getReadLock() throws InterruptedException {
		while (writersActive > 0 || writersPending > 0) {
			wait();
		}
		++readersActive;
	}

	public synchronized void releaseReadLock() {
		--readersActive;
		notifyAll();
	}
	
	/**
	 * Implement the write lock.
	 */
	public synchronized void getWriteLock() throws InterruptedException {
		++writersPending;
		
		while (readersActive > 0 || writersActive > 0) {
			wait();
		}
		--writersPending;
		++writersActive;
	}

	public synchronized void releaseWriteLock() {
		--writersActive;
		notifyAll();
	}
}
