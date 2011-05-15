package com.challengeandresponse.timedtokencache;

import java.util.Enumeration;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Timed Token Cache... 
 * Can be used by a transactional application to store 
 * cached token triples consisting of: (token,owner,expiretime) for detecting replays. Lighter-weight than 'TimedConcurrentHashMap' 
 * but doesn't do a good job of storing data. Basically it's the KEY and the TIMEOUT TIME and that's it.
 * Use 'TimedConcurrentHashMap' if you want to store an Object with a key AND an expiration time, all together.
 * 
 * <p>The key methods (validToken() and remove()) are synchronized. This is not nearly granular enough but 
 * will do the job adequately for a low-throughput cache like that to be used
 * with the XMPPCA certificate authority application for which this was designed. This class could be rewritten
 * to reduce granularity to lock on (token+owner) and because it's encapsulated
 * here, classes that use this won't need to change.</p>
 * 
 * <p>The class also can (should) have a cache cleaner to remove expired entries (recommended if you prefer
 * caches that don't grow infinitely large!):<br />
 * Call startCleaner(interval) to start the cache-cleaning thread and remove expired entries<br />
 * Then for an orderly shutdown, call stopCleaner() to cancel the cache-cleaning thread.<br />
 * The cleaner can be stopped and started as desired.
 * </p>
 * 
 * @author jim
 *
 */
public class TimedTokenCache  {

	// the String part is the key = Token+"."+OWNER (to token and who it belongs to -- it's a one-shot, and is how replays are detected and avoided)
	private ConcurrentHashMap <String,Long> tokenCache;
	// the cache cleaning timer
	private Timer timer;

	public static final long MINIMUM_CLEANER_INTERVAL_SEC = 1;

	
	/**
	 * Init a TimedTokenCache and prep (but do not start) a periodic cleaner task to remove expired entries. 
	 * Caller must invoke startCleaner() method to start the cache cleaner, and stopCleaner() when shutting down.
	 */
	public TimedTokenCache() {
		tokenCache = new ConcurrentHashMap <String,Long> ();
		timer = null;
	}


	/**
	 * Start the cache cleaning periodic task. It will delay execution before the first run, for cleaningIntervalSec seconds.
	 * After that, it will run every cleaningInterval seconds. This can be called multiple times (startCleaner(), stopCleaner(), startCleaner() is legal)...
	 * 
	 * @param cleaningIntervalSec number of SECONDS to wait between runs of the cleaner. Probably can be a multiple of the ordinary or max cache time. Interval must be >= 1 (MINIMUM_CLEANER_INTERVAL_SEC) seconds, but don't make this too small - scale cleaning periods to follow typical token lifespans - at least equal to 1x token life, and probably this can be a multiple
	 * @throws IllegalArgumentException if cleaningIntervalSec is negative
	 * @throws IllegalStateException if the cleaner is already started
	 */
	public void startCleaner(int cleaningIntervalSec, String threadName)
	throws IllegalStateException, IllegalArgumentException {
		if (timer != null) 
			throw new IllegalStateException("Cleaner is already running");
		if (cleaningIntervalSec < MINIMUM_CLEANER_INTERVAL_SEC)
			throw new IllegalArgumentException("Cleaning interval "+cleaningIntervalSec+" sec is too short. Must be >= "+MINIMUM_CLEANER_INTERVAL_SEC+" sec");

		// all ok; start the cleaner
		long cleaningIntervalMsec = ((long) cleaningIntervalSec) * 1000L;
		timer = new Timer(threadName);
		timer.scheduleAtFixedRate(new TimedTokenCacheCleaner (this), cleaningIntervalMsec, cleaningIntervalMsec);
	}

	public void startCleaner(int cleaningIntervalSec)
	throws IllegalStateException, IllegalArgumentException {
		startCleaner(cleaningIntervalSec,"TimedTokenCacheCleaner");
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
	 * Checks to see if a token has NOT been seen before, to prevent replays.
	 * 
	 * If the proffered token is unique for the sender, it caches the token to prevent replay until 'expireTime' and returns true.<br />
	 * If the token has been seen before but its message TTL has expired (reaper hasn't remove it yet), the token is updated in the cache and true is returned.<br />
	 * If the token is found and is unexpired, false is returned -- a replay has been detected.<br />
	 * 
	 * @param token the token to check
	 * @param expireTime the expiration time of the presented token, to be used if the token is valid and thus cached, to control its expiration from the cache, in System.currentTimeMillis() form
	 * @return
	 */
	public synchronized boolean tokenIsUnique(TimedTokenCacheObject token, long expireTime) {
		// attempt to cache the token. if successful, it's unique. if not, then it's already there..
		// a token found to be already in the cache could be expired, so expiration has to be tested for separately
		Long foundValue = tokenCache.putIfAbsent(token.getKey(), expireTime);
		// if this is a new entry, then all is well and it's now in the cache
		if (foundValue == null)
			return true;
		// otherwise the token was found. See if the found value is unexpired. If so, then it's an active token and replay is prohibited.
		if (foundValue.longValue() > System.currentTimeMillis()) {
			return false;
		}
		// the found value is expired. Update it with this one
		tokenCache.replace(token.getKey(),foundValue,expireTime);
		return true;
	}

	/**
	 * Remove a key/token/expiretime, but only if they are unchanged. If they've been updated by something else, or if either is not provided, this method does nothing.
	 * @param token
	 * @param owner
	 * @param expireTime
	 */	
	public void remove(TimedTokenCacheObject token, Long expireTime) {
		if (token == null) {
			return;
		}
		remove(token.getKey(),expireTime);
	}


	/**
	 * Remove a key/token/expiretime, but only if they are unchanged. If they've been updated by something else, this method does nothing.
	 * This is for package use only, as outside code is not trusted to compute the key in the same way this method does, and the key() method is private.
	 * @param key the composite key of the item - should be generated by a call to the key() method
	 * @param expireTime the expire time of the object. if it does not match, the record will not be removed (indicating it's been revised by another thread)
	 */	
	synchronized void remove(String key, Long expireTime) {
		// key cannot be absent or blank
		if (key == null) {
			return;
		}
		tokenCache.remove(key,expireTime);
	}



	/**
	 * Remove a key/token/expiretime, whether changed by another process or not (the [token.getKey()] keyed element is removed if present, regardless of expiretime).
	 * This is for package use only, as outside code is not trusted to glindly remove objects w/o confirmation that the expiry is unchanged.
	 * This method is really for the TimedTokenCacheCleaner
	 * @param key the composite key of the item - should be generated by a call to the key() method
	 */	
	synchronized void remove(TimedTokenCacheObject token) {
		// key cannot be absent or blank
		if (token == null) {
			return;
		}
		tokenCache.remove(token.getKey());
	}



	/**
	 * Used by TimedTokenCacheCleaner to step through the entire cache
	 * @return
	 */
	Enumeration <String> enumeration() {
		return tokenCache.keys();
	}

	/**
	 * Used by TimedTokenCacheCleaner when stepping through the entire cache
	 * @return
	 */
	Long get(String key) {
		return tokenCache.get(key);
	}


	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		stopCleaner();
	}

	

}


