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
        speakCondition = new Condition2(communicationLock);
        listenCondition = new Condition2(communicationLock);
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
        //We begin by acquriing the lock
        communicationLock.acquire(); 

        while(buffer!=null) 
            speakCondition.sleep();
        this.buffer = word; 
        listenCondition.wake(); 
        communicationLock.release();
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        int returnWord = 0; 
        communicationLock.acquire();

       // while(buffer!=null) listenCondition.sleep();
        while(buffer == null){
            listenCondition.sleep();
        }

        returnWord = buffer.intValue(); 
        buffer = null; 
        speakCondition.wake(); 
        communicationLock.release();
	    return returnWord;
    }

    public Condition2 getSpeakCondition(){
        return this.speakCondition; 
    }
    public Condition2 getListenCondition(){
        return this.listenCondition; 
    }
    
    public Lock returnLock(){
        return communicationLock; 
    }

    public Integer returnBuffer(){
        return this.buffer;
    }

    public int returnBufferValue(){
        return this.buffer.intValue(); 
    }
    
    //In this implementation, we are not using sempahores, but condition variables. Therefore, 
    // we will be using Condition2 objects instead of Condition. We will have three conditions, 
    // one for speaking, one for receiving, and one for listening. 
    private Condition2 speakCondition; 
    private Condition2 listenCondition;
    private static Lock communicationLock; 
    private Integer buffer; 
}
