package org.jboss.seam.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.servlet.ServletContext;

import org.jboss.seam.Seam;
import org.jboss.seam.log.LogProvider;
import org.jboss.seam.log.Logging;

public class Resources 
{
    private static final LogProvider log = Logging.getLogProvider(Resources.class);

   public static InputStream getResourceAsStream(String resource, ServletContext servletContext) 
   {
      String stripped = resource.startsWith("/") ? 
            resource.substring(1) : resource;
   
      InputStream stream = null; 

      if (servletContext!=null) {
         try {
            stream = servletContext.getResourceAsStream(resource);
            if (stream!=null) {
                log.debug("Loaded resource from servlet context: " + resource);
            }
         } catch (Exception e) {       
             //
         }
      }
      
      if (stream==null) {
         stream = getResourceAsStream(resource, stripped);
      }
      
      return stream;
   }

   public static URL getResource(String resource, ServletContext servletContext) 
   {
      String stripped = resource.startsWith("/") ? 
            resource.substring(1) : resource;
   
      URL url  = null; 

      if (servletContext!=null)
      {
         try {
            url = servletContext.getResource(resource);
            log.debug("Loaded resource from servlet context: " + url);
         } catch (Exception e) {
             //
         }
      }
      
      if (url==null)
      {
        url = getResource(resource, stripped);
      }
      
      return url;
   }
   
   static InputStream getResourceAsStream(String resource, String stripped)
   {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      InputStream stream = null;
      if (classLoader!=null) {
         stream = classLoader.getResourceAsStream(stripped);
         if (stream !=null) {
             log.debug("Loaded resource from context classloader: " + stripped);
         }
      }
      
      if (stream == null) {
         stream = Seam.class.getResourceAsStream(resource);
         if (stream !=null) {
             log.debug("Loaded resource from Seam classloader: " + resource);
         }
      }
      
      if (stream == null) {
         stream = Seam.class.getClassLoader().getResourceAsStream(stripped);
         if (stream!=null) {
             log.debug("Loaded resource from Seam classloader: " + stripped);
         }
      }
      
      return stream;
   }
   
   static URL getResource(String resource, String stripped)
   {
       ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
       URL url = null;
       if (classLoader!=null) {
           url = classLoader.getResource(stripped);
           if (url!=null) {
               log.debug("Loaded resource from context classloader: " + url);
           }
       }

       if (url == null) {
           url = Seam.class.getResource(resource);
           if (url!=null) {
               log.debug("Loaded resource from Seam classloader: " + url);
           }
       }

       if (url == null) {
           url = Seam.class.getClassLoader().getResource(stripped);
           if (url!=null) {
               log.debug("Loaded resource from Seam classloader: " + url);
           }           
       }
       
       return url;
   }

   public static void closeStream(InputStream inputStream) {
       if (inputStream == null) {
           return;
       }
       
       try {
           inputStream.close();
       } catch (IOException e) {
          // 
       }       
   }

}
