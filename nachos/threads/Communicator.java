package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        communicationLock = new Lock(); 
        speaker = new Condition2(communicationLock);
        listener = new Condition2(communicationLock);
        inAction = false;
        buffer = null; 
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        //We begin by acquiring the lock
        communicationLock.acquire();

        boolean current_status = Machine.interrupt().disabled();

        //While the buffer still has values in it, we take in the value for word
        while(inAction && buffer !=null) speaker.sleep();

        inAction = true;

        //Assign the requested word to be sent out to the buffer
        this.buffer = word;

        //We then transfer the word to the listener
        listener.wake();

        Machine.interrupt().restore(current_status);

        //Release the lock that was previously acquired
        communicationLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        //Begin by acquiring the lock
        communicationLock.acquire();

        boolean current_status = Machine.interrupt().disabled();

        //returned message
        int word = 0;

        //We put the listener communicator to sleep while the speaking communicator is acquiring the word
        while(!inAction && buffer == null)  listener.sleep();

        inAction = false;

        word=this.buffer;

        speaker.wake();

        //Release the lock that was previously acquired
        communicationLock.release();

        Machine.interrupt().restore(current_status);

        return word;
    }

    //getters and setters of our private variables
    public Lock getLock(){
        return communicationLock;
    }

    public Integer getBuffer(){
        return this.buffer;
    }

    public int getBufferValue(){
        return this.buffer;
    }

    public Condition2 getSpeaker(){return this.speaker;}
    public Condition2 getListener(){return this.listener;}
    public boolean returnInAction(){return this.inAction; }


    //In this implementation, we are not using semaphores, but condition variables. Therefore,
    // we will be using Condition2 objects instead of Condition. We will have two different
    //condition communicators. One for speaking and one for listening
    private boolean inAction;
    private final Condition2 speaker;
    private final Condition2 listener;
    private static Lock communicationLock;
    private Integer buffer; 
}
