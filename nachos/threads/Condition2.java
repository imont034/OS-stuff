package nachos.threads;
import java.util.LinkedList; 
import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 
{
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock
     * 	the lock associated with this condition
     *	variable. The current thread must hold this
     *	lock whenever it uses <tt>sleep()</tt>,
     *	<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
        public Condition2(Lock conditionLock) 
        {
	        this.conditionLock = conditionLock;
            wait = new LinkedList<KThread>(); 
        }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() 
    {
	    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	    
        //Release the associated lock 
        conditionLock.release();
        
        //Entering a critical state, so we disable interrupts, and return the state. 
        boolean current_status = Machine.interrupt().disable(); 
        
        //Add to the linkedlist the current thread 
        wait.add(KThread.currentThread());

        //Have the thread sleep until another thread wakes it up using wake() 
        KThread.sleep();
        conditionLock.acquire();
        
        //Restore interrupt back to the previous state 
        Machine.interrupt().restore(current_status);
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() 
    {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        if (!wait.isEmpty())
        {
            //Entering a critical section, so we disable interrupts and returns the 
            //old interrupt state. 
            boolean current_status = Machine.interrupt().disable(); 
            
            //Take out the first thread from the linked list of threads
            KThread thread = wait.removeFirst();
            
            //If it is a viable thread, we wake up the thread 
            if (thread != null)
            {
                thread.ready(); 
            }

            //Restore interrupt into the specified state that was previously
            Machine.interrupt().restore(current_status);
        }
        //If the linked list of threads are empty, then we do not have 
        //any threads. Thus, there is no reason to wake up anything. Simply 
        //return. 
        else return; 
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        
        //Nothing to wake up
        if (wait.isEmpty() || wait==null || wait.peek()==null) 
            return;    

        //Wake up each thread iteratively within the linked list of threads 
        while (!wait.isEmpty()) wake(); 
    }

    private Lock conditionLock;
    LinkedList<KThread> wait = new LinkedList<KThread>();
}
