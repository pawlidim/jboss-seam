/*
 * JBoss, Home of Professional Open Source  * Distributable under LGPL license.  * See terms of
 * license at gnu.org.  
 */
package org.jboss.seam;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A conversation context is a logical context that last longer than 
 * a request but shorter than a login session
 * 
 * @author Gavin King
 * @author <a href="mailto:theute@jboss.org">Thomas Heute</a>
 * @version $Revision$
 */
public class ConversationContext implements Context {

	private Context loginContext;

	private String conversationId;

	private static AtomicInteger uniqueId = new AtomicInteger(0);

	public ConversationContext(Context loginContext, String conversationId) {
		this.loginContext = loginContext;
		this.conversationId = conversationId;
	}

	public Object get(String name) {
		return loginContext.get( name + '$' + conversationId );
	}

	public void set(String name, Object value) {
		loginContext.set( name + '$' + conversationId, value );
	}

	public boolean isSet(String name) {
		return get( name ) != null;
	}

	public void destroy() {
		String[] names = loginContext.getNames();
      Context eventContext = Contexts.getEventContext();
		for ( int i = 0; i < names.length; i++ ) {
			if ( names[i].endsWith( '$' + conversationId ) ) {
            //demote components to request context
            if ( !eventContext.isSet( names[i] ) ) {
               String name = names[i].substring( 0, names[i].lastIndexOf('$') );
               eventContext.set( name, loginContext.get( names[i] ) );
            }
            loginContext.remove( names[i] );
			}
		}
	}

	public void remove(String name) {
		loginContext.remove( '$' + name );
	}

	public String[] getNames() {
		throw new UnsupportedOperationException( "NYI" );
	}

	static String generateConversationId() {
		return Integer.toString( uniqueId.incrementAndGet() );
	}

}
