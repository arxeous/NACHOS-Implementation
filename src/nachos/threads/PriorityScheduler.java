package nachos.threads;
import nachos.machine.*;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer priority
	 *                         from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			KThread nextThread = priorityQueue.peek();

			// Next thread simply gets the next thread from our priority queue, 
			// and checks if it its null or not.
			// if it isnt we restore this next threads priority, remove it from our queue and return it.
			if (nextThread == null)
				return null;
			getThreadState(nextThread).effectivePriority = getThreadState(nextThread).getPriority();
			priorityQueue.remove(nextThread);
			return nextThread;
			

		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			// Picking next thread is a simple peek into our priority queue.
			return getThreadState(priorityQueue.peek());
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting threads to
		 * the owning thread.
		 */
		public boolean transferPriority;
		// List of threads dependent on this resource
		private java.util.PriorityQueue<KThread> priorityQueue = new java.util.PriorityQueue<KThread>(10,
				new KThreadComparator());
		// Current owner of the resource 
		ThreadState currentOwner = null;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's priority,
	 * its effective priority, any objects it owns, and the queue it's waiting for,
	 * if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */

	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// Check what exactly it is we are waiting on
			// If our effective priority is larger than the resource owners effective priority (EP)
			// we donate our EP to the resource owner and then calculate the EP for any resource its waiting on.
			// Finally we return the resources priority. This is done to ensure that not only are we donating our high priority to lower threads, but we are
			// also recieving the lower priority so those threads are ensured to finish. 
			if(waitingResource != null) {
				if(waitingResource.currentOwner != null) {
					if(effectivePriority <= waitingResource.currentOwner.effectivePriority)
						return waitingResource.currentOwner.effectivePriority;
					else {
						waitingResource.currentOwner.effectivePriority = effectivePriority;
						waitingResource.currentOwner.getEffectivePriority();
						return waitingResource.currentOwner.priority;
					}
			}		
		}
			return effectivePriority;
	}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			
			this.priority = priority;
			this.effectivePriority = priority;
			if(waitingResource != null) {
				getEffectivePriority();
			}

		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the
		 * associated thread) is invoked on the specified priority queue. The associated
		 * thread is therefore waiting for access to the resource guarded by
		 * <tt>waitQueue</tt>. This method is only called if the associated thread
		 * cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			// We store waitQueue in waitingResource to indicate that this thread is now waiting for this specific resource
			// we also add the thread associated with this threadstate to the priority queue.
			waitingResource = waitQueue;
			waitQueue.priorityQueue.add(thread);
			// Then we call getEP to check current owner of the resource we are waiting ons effective priority. This is essentially where donation occurs
			// and where we traverse the graph donating to any threads necessary. 
			effectivePriority = getEffectivePriority();
		}

		/**
		 * Called when the associated thread has acquired access to whatever is guarded
		 * by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			Lib.assertTrue(Machine.interrupt().disabled());
			// Make the current owner of the resource this thread state
			waitQueue.currentOwner = this;
			// This thread is no longer waiting for this resource. It now owns it so we remove it 
			// from our priority queue.
			waitQueue.priorityQueue.remove(thread); 
			// We then add the resource to the list of resources this thread owns.
			myResources.add(waitQueue);	
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		// The cache for our EffectivePriority.
		protected int effectivePriority;
		// Resource that this thread is currently waiting on
		protected PriorityQueue waitingResource; 
		// List of "Resources" that the thread owns
		protected LinkedList<PriorityQueue> myResources = new LinkedList<PriorityQueue>();
	}

	class KThreadComparator implements Comparator<KThread> {
		@Override
		public int compare(KThread o1, KThread o2) {
			if (getThreadState(o1).effectivePriority > getThreadState(o2).effectivePriority)
				return 1;
			else if (getThreadState(o1).effectivePriority < getThreadState(o2).effectivePriority)
				return -1;
			return 0;
		}
	}
}
