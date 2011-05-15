package com.challengeandresponse.timedtokencache;

import java.util.Enumeration;
import java.util.TimerTask;

/**
 * Delete expired entires from the Nonce Cache
 * This class is automatically instantiated and used by the TimedTokenCache class.
 * 
 * @author jim
 *
 */
 class TimedTokenCacheCleaner extends TimerTask {

	private TimedTokenCache ttcache;

	TimedTokenCacheCleaner(TimedTokenCache t) {
		this.ttcache = t;
	}

	@Override
	public void run() {
		Enumeration <String> en = ttcache.enumeration();
		while (en.hasMoreElements()) {
			// for each entry - if it's expired, whack it. NonceCache takes care of synchronization.
			String key = en.nextElement();
			long expireTime = ttcache.get(key);
			if (expireTime < System.currentTimeMillis())
				ttcache.remove(key,expireTime);
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
