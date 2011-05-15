package com.challengeandresponse.timedconcurrenthashmap;

import java.util.Enumeration;
import java.util.TimerTask;

/**
 * Delete expired entires from a TimedConcurrentHashMap
 * This class is automatically instantiated and used by the TimedConcurrentHashMap class.
 * 
 * This is externally initiated and then its run() is called periodically... 
 * 
 * @author jim
 *
 */
public class TimedConcurrentHashMapCleaner <K,V>
extends TimerTask {


	private TimedConcurrentHashMap <K,V> tchm;

	TimedConcurrentHashMapCleaner(TimedConcurrentHashMap <K,V> t) {
		this.tchm = t;
	}

	/**
	 * Walk through the TimedConcurrentHashMap and reap any entries that are expired.
	 * Uses remove(key,value,expiration) to avoid concurrency issues, should the entry be updated
	 * between the get() and the remove() that occur here.
	 */
	@Override
	public void run() {
		Enumeration <K> en = tchm.keys();
		while (en.hasMoreElements()) {
			K key = en.nextElement();
			TimedObject <V> to = tchm.getTimedObject(key);
			if (to.expired()) {
				tchm.remove(key,to.value,to.expiration);
			}
		}
	}


	@Override
	public boolean cancel() {
		return super.cancel();
	}
	@Override
	public long scheduledExecutionTime() {
		return super.scheduledExecutionTime();
	}

}



