package org.jboss.seam.remoting.gwt;

import static org.jboss.seam.ScopeType.APPLICATION;
import static org.jboss.seam.annotations.Install.BUILT_IN;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.jboss.seam.annotations.Create;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.Startup;
import org.jboss.seam.annotations.intercept.BypassInterceptors;

import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamReader;
import com.google.gwt.user.server.rpc.impl.ServerSerializationStreamWriter;

/**
 * 
 * @author Shane Bryzak
 */
@Startup
@Scope(APPLICATION)
@Name("org.jboss.seam.remoting.gwt.gwtRemoteService")
@Install(precedence = BUILT_IN, classDependencies = {"com.google.gwt.user.client.rpc.RemoteService"})
@BypassInterceptors
public class GWT14Service extends GWTService
{
   private static final String SERIALIZATION_POLICY_PROVIDER_CLASS = "com.google.gwt.user.server.rpc.SerializationPolicyProvider";
   
   private static final String SERIALIZATION_POLICY_CLASS = "com.google.gwt.user.server.rpc.SerializationPolicy";
   private static final String LEGACY_SERIALIZATION_POLICY_CLASS = "com.google.gwt.user.server.rpc.impl.LegacySerializationPolicy";
   
   
   private Constructor streamReaderConstructor;
   private Constructor streamWriterConstructor;   
   
   private Object legacySerializationPolicy;
   
   @Create
   public void startup() throws Exception
   {
      Class policyProviderClass = Class.forName(SERIALIZATION_POLICY_PROVIDER_CLASS);
      Class serializationPolicyClass = Class.forName(SERIALIZATION_POLICY_CLASS);
      
      streamReaderConstructor = getConstructor(ServerSerializationStreamReader.class.getName(), 
            new Class[] { ClassLoader.class, policyProviderClass } );
      streamWriterConstructor = getConstructor(ServerSerializationStreamWriter.class.getName(), 
            new Class[] { serializationPolicyClass } );

      Class legacySerializationPolicyClass = Class.forName(LEGACY_SERIALIZATION_POLICY_CLASS);
      Method m = legacySerializationPolicyClass.getDeclaredMethod("getInstance");
      legacySerializationPolicy = m.invoke(null);
   } 
   
   private Constructor getConstructor(String className, Class[] paramTypes)
   {
      try
      {
         Class cls = Class.forName(className);
         return cls.getConstructor(paramTypes);
      }
      catch (Exception ex)
      {
         log.error(String.format("Error initializing GWT14Service - class %s " +
               "not found. Please ensure the GWT 1.4 libraries are in the classpath.",
               className));
         throw new RuntimeException("Unable to create GWT14Service", ex);
      }
   }   
   
   @Override
   protected String createResponse(ServerSerializationStreamWriter stream,
         Class responseType, Object responseObj, boolean isException)
   {
      stream.prepareToWrite();
      if (responseType != void.class)
      {
         try
         {
            stream.serializeValue(responseObj, responseType);
         } catch (SerializationException e)
         {
            responseObj = e;
            isException = true;
         }
      }

      return (isException ? "//EX" : "//OK") + stream.toString();      
   }   
   
   @Override
   public ServerSerializationStreamReader getStreamReader()
   {
      try
      {
         return (ServerSerializationStreamReader) streamReaderConstructor.newInstance(
               Thread.currentThread().getContextClassLoader(), null);
      }
      catch (Exception ex) 
      { 
         throw new RuntimeException("Unable to create stream reader", ex);
      }
   }
   
   @Override
   public ServerSerializationStreamWriter getStreamWriter()
   {
      try
      {
         return (ServerSerializationStreamWriter) streamWriterConstructor.newInstance(legacySerializationPolicy);
      }
      catch (Exception ex)
      {
         throw new RuntimeException("Unable to create stream writer", ex);
      }
   }   
}
