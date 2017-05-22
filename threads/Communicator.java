package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {

		lock.acquire();
		if (someOneIsSpeaking == true){
			canSpeak.sleep();
		}
		someOneIsSpeaking = true;
		this.word = word;

		canListen.wake();
		acknowledge.sleep();

		lock.release();
		/*

		if (someOneIsSpeaking){
			block myself

		}
		someOneIsSpeaking = true;
		this.word = word;

		wake up a listener if they are waiting for someone to speak
		wait until a listener has acknowledge they have heard my word
		*/
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {


		lock.acquire();

		if (someOneIsSpeaking == false){
			canSpeak.wake();
			canListen.sleep();
		}

		int listenedWord = this.word;

		acknowledge.wake();
		someOneIsSpeaking = false;

		/*

		if (someOneIsSpeaking == false){
			wake up someone who is waiting to speak
			wait until someone is speaking
		}

		int heardword = this.word;

		someOneIsSpeaking = false;
		acknowledgeg to the pseaker that you have heard them
		return the word;
		*/

		lock.release();
		return listenedWord;
		
	}

	private boolean someOneIsSpeaking = false;
	private int word;

	private Lock lock = new Lock();
	private Condition canSpeak = new Condition(lock);
	private Condition canListen = new Condition(lock);
	private Condition acknowledge = new Condition(lock);

	private int speakerNum = 0;
	private int listenerNum = 0;



}
