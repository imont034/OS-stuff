package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Iterator;
/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fashion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler. Default Constructor
     */
    public PriorityScheduler() {
	}
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
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
		       
		Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
		getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		       
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
	    	return false;

		setPriority(thread, priority+1);

		Machine.interrupt().restore(intStatus);
		return true;
    }

    public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();
		       
		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
	    	return false;

		setPriority(thread, priority-1);

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
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
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
			//Entering critical section, so we must assure that interrupt is disabled
	    	Lib.assertTrue(Machine.interrupt().disabled());

	    	if (this.wait.isEmpty()) {
                System.out.println("The queue is empty...Terminating\n");
                return null;
            }

		    ThreadState thread_state = this.pickNextThread();

            if (thread_state == null) {
                System.out.println("Next thread is null...Terminating\n"); 
                return null; 
            }

            //Pick out the next thread. Remove it from the tree set, have it acquire access And then return
			//the recently removed thread.
            wait.remove(thread_state);

            thread_state.acquire(this);
            return thread_state.thread;
        }

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
	 	* without modifying the state of this queue.
		 *
	 	* @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			//We want to return the next thread with the highest priority. So, we return the last element
			//currently in the set.
	    	if (!wait.isEmpty()) return wait.last();

	    	//If empty, nothing can be returned. So, we return null.
	    	return null;
		}
	
		public void print() {
	    	Lib.assertTrue(Machine.interrupt().disabled());
	    	for(ThreadState state : wait){
            	System.out.println("State thread:" + state.thread + "\nState Priority: "
                    	+ state.getPriority() + "\nState Effective Priority: " + 
							state.getEffectivePriority() + "\nState Time: " + state.time); 
			}
    }

		/**
	 	* <tt>true</tt> if this queue should transfer priority from waiting
	 	* threads to the owning thread.
		*/
		public boolean transferPriority;
		public ThreadState waiting;
		public TreeSet<ThreadState> wait = new TreeSet<ThreadState>();
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState implements Comparable<ThreadState>{
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
		this.thread = thread;
		this.waitedSet  = new HashSet<>();
		this.effectivePriority = priorityDefault;
		this.time = Machine.timer().getTime(); 
		this.setPriority(priorityDefault);
	}
    
    public int compareTo(ThreadState threadState){
       if (threadState == null) return -1;
       //We compare threadStates over their priority.
	   if (this.getEffectivePriority() !=- threadState.getEffectivePriority())
	   		return Integer.compare(this.getEffectivePriority(), threadState.getEffectivePriority());
	   //However, if the threadStates were to have the same priority, we compare over age. The one that is older
	   //will have greater priority as a result of FIFO
       else return Long.compare(this.time, threadState.time);
    }

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return this.priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
		this.changePriority();
		return this.effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
		//Setting priority for values that are of the same magnitude or < 0 constitutes
		// for the function to terminate.
	    if (this.priority == priority) return;
		if(priority < 0) return;

		//After we assign the priority, we need to take into consideration for donors.
	    this.priority = priority;
	    changePriority();
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
		Lib.assertTrue(Machine.interrupt().disabled());
		if(priorityQueue!=null) priorityQueue.wait.remove(this);
		this.priorityQueue = waitQueue;
		waitQueue.wait.add(this);
		if(waitQueue.transferPriority){
			if(waitQueue.waiting != null && waitQueue.waiting.effectivePriority < this.effectivePriority)
				changePriority();
		}
	}

	/*
	* This function is used to calculate the effective priority of a thread and any donors. It also changes
	* the priority on the thread that currently holds the resource this.thread is waiting on .
	 */
	public void changePriority(){
		//Initial variables
		int initialPriority = this.getPriority();
		int maxPriority = 0;

		//Want to iterate over the entire thing. If its empty, nothing can be done so we simply return.
		if (waitedSet.size() != 0){
			for (int i = 0; i < waitedSet.size(); i++){
				Iterator<PriorityQueue> iter = waitedSet.iterator();
				PriorityQueue current = iter.next();
				ThreadState donor= current.pickNextThread();

				if (donor!= null) {
					if ((donor.effectivePriority > maxPriority) && current.transferPriority)
						maxPriority = donor.effectivePriority;
				}
			}
		}

		if (initialPriority > maxPriority) maxPriority = initialPriority;
		this.effectivePriority = maxPriority;

		//Used to change the priority on the thread that this.thread may be waiting on
		if(this.waitedSet!=null && this.waitedSet.iterator().next().waiting != null){
			if(this.effectivePriority != this.waitedSet.iterator().next().waiting.effectivePriority)
				this.waitedSet.iterator().next().waiting.changePriority();
		}

	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
		Lib.assertTrue(Machine.interrupt().disabled());
		if(waitQueue.waiting != null){
			waitQueue.waiting.waitedSet.remove(waitQueue);
			if(waitQueue.transferPriority) changePriority();
		}
		waitQueue.waiting = this;
		this.waitedSet.add(waitQueue);
		if(waitQueue.transferPriority) changePriority();
	}

	/** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority, effectivePriority;
	//The age of each ThreadState
	private long time;
	//Each individual priorityQueue of one priority
	private PriorityQueue priorityQueue;
	//The conceptualization of differing priority queue of all unique priorities,
	// as a result of uniqueness, a HashSet is used.
	private HashSet<PriorityQueue> waitedSet;
	}
}
