package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm 
{
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() 
    {
	    Machine.timer().setInterruptHandler(new Runnable() {
		    public void run() { timerInterrupt(); }
	     });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        boolean current_status = Machine.interrupt().disable();

        while(!wait.isEmpty() && wait.peek().wakeTime <= Machine.timer().getTime()){
            threadTime thread_time = wait.poll();
            if (thread_time.thread!=null) thread_time.thread.ready();
        }

        // KThread.currentThread().yield();
        Machine.interrupt().restore(current_status);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	    if (x < 0) return;      //Cannot wait on previous values. Time doesnt work that way
        // for now, cheat just to get something working (busy waiting is bad)
    	long wakeTime = Machine.timer().getTime() + x;
    	//Entering a critical state, so we disable the interrupt, and return
        //the current state prior. 
        boolean current_status = Machine.interrupt().disable(); 
        threadTime thread_time = new threadTime(wakeTime, KThread.currentThread());
        wait.add(thread_time);
        KThread.sleep();
        //thread_time.thread.sleep();
        Machine.interrupt().restore(current_status); 
    }

    private static class threadTime implements Comparable<threadTime> {
        public threadTime(){}
        public threadTime(long wakeTime, KThread thread){
            this.wakeTime = wakeTime; 
            this.thread = thread; 
        }
        @Override
        public int compareTo(threadTime threadTime) throws NullPointerException{
            if (threadTime == null) throw new NullPointerException();
            return Long.compare(this.wakeTime, threadTime.wakeTime);
        }

        public long getWakeTime(){ return this.wakeTime; }
        public KThread getThread(){ return this.thread; }
        public void setWakeTime(long wakeTime) { this.wakeTime = wakeTime;}
        public void setThread(KThread thread) { this.thread = thread; }
        
        private KThread thread = null; 
        private long wakeTime = 0; 
    }
    public PriorityQueue<threadTime> returnPriorityQueue(){return this.wait;}
    private final PriorityQueue<threadTime> wait = new PriorityQueue<>();
}
