package nachos.threads;

import java.util.LinkedList;

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
      myLock = new Lock();
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
        
        myLock.acquire();
        int theMessage = word;

        if(listOfListeners.isEmpty()) {
            Condition2 thisThreadCond = new Condition2(myLock);
            modelData thisThread = new modelData(theMessage, thisThreadCond);
            listOfSpeakers.add(thisThread);
            thisThread.myCondition.sleep();
        }
        else {
            listOfListeners.getFirst().myMessage = word;
            listOfListeners.remove().myCondition.wake();
        }
        myLock.release();
        return;
    
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        myLock.acquire();
        int theMessage;

        if(listOfSpeakers.isEmpty()) {
            Condition2 thisThreadCond = new Condition2(myLock);
            modelData thisThread = new modelData(0, thisThreadCond);
            listOfListeners.add(thisThread);
            thisThread.myCondition.sleep();
            theMessage = thisThread.myMessage;
        }
        else {
            theMessage = listOfSpeakers.getFirst().myMessage;
            listOfSpeakers.remove().myCondition.wake();
        }
        
        myLock.release();
        return theMessage;
    }
    
    private Lock myLock;
    private LinkedList<modelData> listOfListeners = new LinkedList<modelData>();
    private LinkedList<modelData> listOfSpeakers = new LinkedList<modelData>();

    private class modelData {
        int myMessage;
        Condition2 myCondition;

        public modelData(int message, Condition2 cond) {
            this.myMessage = message;
            this.myCondition = cond;
        }
    }
}
