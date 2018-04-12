package com.challengeandresponse.timedtokencache;

/**
 * Provides a simple wrapper around String as an aid to classes that want to use a TimedTokenCache
 * whose key objects are simply strings.
 *
 * @author jim
 *
 */
public class TimedTokenCacheString
implements TimedTokenCacheObject {

	private String s;

	public TimedTokenCacheString(String s) {
		this.s = s;
	}

	public String getString() {
		return s;
	}

	public String getKey() {
		return s;
	}


}
