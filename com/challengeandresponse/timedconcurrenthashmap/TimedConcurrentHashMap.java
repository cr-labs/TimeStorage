package com.challengeandresponse.timedconcurrenthashmap;

import java.util.Enumeration;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Heavier-duty (and more processing intensive) than the TimedTokenCache.
 * Use 'TimedCache' if you want to store an Object with a key AND an expiration time, all together.
 * Use 'TimedTokenCache' if you just have a key and a timestamp after which the key is invalid.
 * TimedTokenCache is good for detecting replays and not a lot more.
 * TimedConcurrenHashmap is good for storing data and then making it unavailable after the expiry time.
 * 
 * Provides ONLY a SUBSET of ConcurrentHashMap methods.
 * 
 * The fundamental extension is that every object is stored with an expiration time.
 * If get(key) is called and the stored object is present but its timestamp is expired, get(key)
 * returns null just as if the object were not present at all.
 * 
 * The concurrency-supporting methods like addIfAbsent() and others are not yet
 * provided by this class. just the basic get() and put() methods are here.
 * 
 * 
 * @author jim
 *
 */


public class TimedConcurrentHashMap <K,V> {
	private static final long serialVersionUID = 1L;

	private ConcurrentHashMap <K,TimedObject <V>> hashmap;

	// if globalTimeout is set (value > 0) then the simple methods (sans expiration time) can be used just like in the regular ConcurrentHashMap
	// if it is not set, then calling those methods (e.g. put(Key,Value)) will thrown an exception
	private long globalTimeoutMsec;

	private Timer timer;

	public static final long MINIMUM_CLEANER_INTERVAL_SEC = 1;



	public TimedConcurrentHashMap() {
		this(0L);
	}

	/**
	 * @param globalTimeoutMsec number of milliseconds for object expiration by default, if the simple put(key,value) method is used
	 */
	public TimedConcurrentHashMap(long globalTimeoutMsec) {
		super();
		this.hashmap = new ConcurrentHashMap <K,TimedObject<V>> ();
		this.globalTimeoutMsec = globalTimeoutMsec;
		timer = null;
	}


	public boolean containsKey(Object key) {
		TimedObject <V> to = hashmap.get(key);
		return ( (to != null) && (! to.expired()));
	}


	public V get(K key) {
		TimedObject <V> to = hashmap.get(key);
		if ( (to != null) && (! to.expired()) )
			return to.value;
		else
			return null;
	}

		
	
	
	/**
	 * For package use only -- for the TimedConcurrentHashMapCleaner
	 * NO timer checking is performed. the stored TimedObject is merely returned to the caller. for PACKAGE USE ONLY
	 */
	TimedObject <V> getTimedObject(K key) {
		return hashmap.get(key);
	}

	/**
	 * If the object stored with 'key' is unexpired, return the time remaining before it times out.
	 * If the object is not present, or is expired, return 0.
	 * @param key
	 * @return
	 */
	public long getTimeRemaining(K key) {
		TimedObject<V> to = hashmap.get(key);
		if (to == null)
			return 0L;
		else
			return Math.max(0L,(to.expiration - System.currentTimeMillis()));
	}

	/**
	 * If a global timeout was specified in the constructor, then this shorthand method may be used nad the object
	 * will be stored with its expiration set to (now + globalTimeout). If a global timeout was not set in the
	 * constructor, then an UnsupportedOperationException is thrown.
	 * @param key
	 * @param value
	 * @return
	 */
	public V put(K key, V value) {
		if (globalTimeoutMsec <= 0L)
			throw new UnsupportedOperationException("put(key,value) cannot be called in TimedConcurrentHashMap() because no global timeout default was set in the constructor");
		else
			return put(key, value, System.currentTimeMillis()+globalTimeoutMsec);
	}

	/**
	 * 
	 * @param key
	 * @param value
	 * @param expiration the System.currentTimeMillis() at which this object will expire. This is an ABSOLUTEL TIME, *NOT* a relative offset
	 * @return
	 */
	public V put(K key, V value, long expiration) {
		TimedObject <V> to = new TimedObject <V> (value,expiration);
		TimedObject <V> result = (hashmap.put(key, to));
		if (result == null)
			return null;
		else 
			return result.value;
	}


	/**
	 * Follows the charter of ConcurrentHashMap.remove(key,value)
	 * 
	 * Careful removal -- only if Key, Value and expiration match, is the record removed
	 * @param key
	 * @param value
	 * @param expiration
	 * @return true if a match was found and removed, false otherwise
	 */
	public boolean remove(K key, V value, long expiration) {
		TimedObject <V> to = new TimedObject <V> (value,expiration);
		return hashmap.remove(key,to);
	}

	
	/**
	 * Follows the charter of ConcurrentHashMap.remove(key)
	 * 
	 * <p>Return the Value referenced by Key, if it exists, and remove it from the collection so no other caller can 'get' it.
	 * This is very useful for e.g. callbacks in RPCHelper, where the key is a nonce from some incoming message, and the
	 * Value is a method that should be invoked exactly once. We want to retrieve the registered method, un-register the handler
	 * so the method won't be called again in the event of a replay, and then invoke the method. Using remove(key) will take
	 * care of cleaning up the callback table so that once a caller is given the method, subsequent calls will return null
	 * to indicate that there is no registered method.</p>
	 * 
	 * Yes the above paragraph is not really needed since it's not about the method but how to use it. Anyway, it's already written...
	 * 
	 * @param key
	 * @return
	 */
	public V remove(K key) {
		TimedObject <V> to = hashmap.remove(key);
		if ( (to != null) && (! to.expired()) )
			return to.value;
		else
			return null;
	}
	




	/**
	 * Start the cache cleaning periodic task. It will delay execution before the first run, for cleaningIntervalSec seconds.
	 * After that, it will run every cleaningInterval seconds. This can be called multiple times (startCleaner(), stopCleaner(), startCleaner() is legal)...
	 * 
	 * @param cleaningIntervalSec number of SECONDS to wait between runs of the cleaner. Probably can be a multiple of the ordinary or max cache time. Interval must be >= 1 (MINIMUM_CLEANER_INTERVAL_SEC) seconds, but don't make this too small - scale cleaning periods to follow typical token lifespans - at least equal to 1x token life, and probably this can be a multiple
	 * @param a label for the thread this spawns, to assist debugging/tracing
	 * @throws IllegalArgumentException if cleaningIntervalSec is negative
	 * @throws IllegalStateException if the cleaner is already started
	 */
	public void startCleaner(int cleaningIntervalSec, String threadName)
	throws IllegalStateException, IllegalArgumentException {
		if (timer != null) 
			throw new IllegalStateException("Cleaner is already running");
		if (cleaningIntervalSec < MINIMUM_CLEANER_INTERVAL_SEC)
			throw new IllegalArgumentException("Cleaning interval must be >= "+MINIMUM_CLEANER_INTERVAL_SEC+" sec");

		// all ok; start the cleaner
		long cleaningIntervalMsec = ((long) cleaningIntervalSec) * 1000L;
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimedConcurrentHashMapCleaner <K,V> (this), cleaningIntervalMsec, cleaningIntervalMsec);
	}
	
	public void startCleaner(int cleaningIntervalSec) 
	throws IllegalStateException, IllegalArgumentException {
		startCleaner(cleaningIntervalSec,"TimedConcurrentHashMap.startCleaner");
	}


	/**
	 * Call stopCleaner() when shutting down an app that uses TimedTokenCache with a cleaner, to stop the task that runs the periodic cache cleaner.
	 */
	public void stopCleaner() {
		if (timer != null)
			timer.cancel();
		timer = null; // subsequent calls to cancel() don't break anything, but not calling cancel is even cheaper and also allows a subsequent restart
	}

	/**
	 * For use by the TimedConcurrentHashMapCleaner, which walks through all objects looking for expired ones
	 * @return
	 */
	Enumeration <K> keys() {
		return hashmap.keys();
	}



	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		stopCleaner();
	}

	/// for testing
	public static void main(String[] args) {
		long TIMEOUT = 1000L;
		TimedConcurrentHashMap <String,Integer> tchm = new TimedConcurrentHashMap <String,Integer> (TIMEOUT);
		System.out.println(tchm.put("key1", new Integer(12),System.currentTimeMillis()+1000));
		System.out.println(tchm.put("key1", new Integer(21),System.currentTimeMillis()+10000L));

		System.out.println(tchm.put("key2", new Integer(30)));
		System.out.println(tchm.put("key2", new Integer(31)));

		System.out.println("get key1: "+tchm.get("key1"));
		System.out.println("get key2: "+tchm.get("key2"));

		System.out.println("Sleeping 1/2 TIMEOUT="+(TIMEOUT/2)+"msec. Objects should not have expired");
		try {
			Thread.sleep(TIMEOUT/2L);
		}
		catch (InterruptedException e) { }

		System.out.println("get key1: "+tchm.get("key1")+" time remaining:"+tchm.getTimeRemaining("key1"));
		System.out.println("get key2: "+tchm.get("key2")+" time remaining:"+tchm.getTimeRemaining("key2"));

		System.out.println("Sleeping TIMEOUT+2000="+(TIMEOUT+2000)+"msec to let objects expire");
		try {
			Thread.sleep(TIMEOUT+2000L);
		}
		catch (InterruptedException e) { }

		System.out.println("get key1: "+tchm.get("key1")+" time remaining:"+tchm.getTimeRemaining("key1"));
		System.out.println("get key2: "+tchm.get("key2")+" time remaining:"+tchm.getTimeRemaining("key2"));

		System.out.println(tchm.hashmap);
		tchm.startCleaner(2);
		System.out.println("Waiting 4 seconds for cleaner to run");
		System.out.println("A");
		try {
			Thread.sleep(4000L);
		}
		catch (InterruptedException e) { }
		System.out.println("B");
		System.out.println(tchm.hashmap);
		tchm.stopCleaner();
	}


}
