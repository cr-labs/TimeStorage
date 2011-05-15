package com.challengeandresponse.timedconcurrenthashmap;

/**
 * This class is only used by others in the package to manage the internal storage of objects....
 * It wraps the provided object to give it an expiration timestamp, and the methods of TimedConcurrentHashMap
 * check that timestamp when accessing the object. Otherwise, behaviours are like a regular ConcurrentHashMap.
 * 
 * @author jim
 *
 * @param <V> the Object type of the value held by this timed object. could be anything
 */
class TimedObject <V> {
	V value;
	long expiration;
	
	public TimedObject(V value, long expiration) {
		this.value = value;
		this.expiration = expiration;
	}

	/**
	 * Two TimedObjects are equal if the equals() method of o's value member returns true, and the expiration times are the same
	 */
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) {
		if (! (o instanceof TimedObject))
			return false;
		TimedObject to = (TimedObject) o;
		return ( (to.value.equals(this.value)) && (to.expiration == this.expiration));
	}
	
	/**
	 * hashCode() must be overridden to correspond to the updated equals() method. Hashcodes need not be
	 * unique but must return the same result if two objects are equal()
	 */
	@Override
	public int hashCode() {
		return (this.value.hashCode() | (int) this.expiration);
	}
	
	/**
	 * @return true if 'expiration' was before the current system time, false if it is equal to or greater (later) than the current system time
	 */
	boolean expired() {
		return (this.expiration < System.currentTimeMillis());
	}



	public static void main(String[] args) {
		TimedObject <Integer> to1 = new TimedObject <Integer> (new Integer(114),System.currentTimeMillis()+1000L);
		TimedObject <Integer> to2 = new TimedObject <Integer> (new Integer(114),System.currentTimeMillis()+1000L);
		
		System.out.println(to1.equals(to2));
	}


}
