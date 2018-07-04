/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.utils;

import java.io.IOException;
import java.net.URL;
/**
 * A url split class. Presents a url as a set of 3 components: host, path, file name
 * 
 * @author Arkadi Kosmynin
 *
 */
public class URLSplit
{
 public String host ;
 public String path ;
 public String name ;
 
/**
 * Default constructor
 */
 public URLSplit() {}
 
 public static void main( String[] args )
 {
  URLSplit s ;
  try
   { 
     s = URLSplit.newURLSplit( "/aa/bb/", null, "f" ) ;
     System.out.println( s.path + " " + s.name ) ;
     s = URLSplit.newURLSplit( "/aa/bb/", null, "d" ) ;
     System.out.println( s.path + " " + s.name ) ;
     s = URLSplit.newURLSplit( "/aa/bb/c.html", null, "f" ) ;
     System.out.println( s.path + " " + s.name ) ;
     s = URLSplit.newURLSplit( "/aa/bb", null, "d" ) ;
     System.out.println( s.path + " " + s.name ) ;
     s = URLSplit.newURLSplit( "/", null, "f" ) ;
     System.out.println( s.path + " " + s.name ) ;
     s = URLSplit.newURLSplit( "/", null, "d" ) ;
     System.out.println( s.path + " " + s.name ) ;
   } catch( IOException e ) {} ;
 }
 
/**
 * URLSplit factory
 * 
 * @param url url to split
 * @param used  a URLSplit object to reuse, to avoid memory fragmentation
 * @param type  type of the url: 'd' - directory, 'f' - file
 * @return a new URLSplit object based on the url passed
 * @throws Exception 
 *
 */
 public static URLSplit newURLSplit( String url, URLSplit used, String type ) throws IOException 
 {
  // Examples of splits
  //                        Path                Name
  // file /aa/bb/           /aa/bb              /
  // directory /aa/bb/      /aa                 bb
  // file /aa/bb/c.html     /aa/bb              c.html
  // file /                 ""                  /
  // directory              ""                  ""
  
  URLSplit sp = used ;
  if ( sp == null ) sp = new URLSplit() ; 
  
  URL u = null ; 
  try // full url
   {
	u = new URL( url ) ;
    sp.host = u.getHost() ;
    sp.name = u.getPath() ;
   } catch( Exception e ) // just path
    {
     sp.host = null ;
     sp.name = url ;
    }
  do
   { int i = sp.name.lastIndexOf( '/' ) ;
     if ( i == 0 && sp.name.length() == 1 ) // url == "/" 
      {
        if ( type.equals( "f"  ) ) { sp.path = "/" ; sp.name = "/" ; }
                            else { sp.path = sp.name = "" ; }
        break ;
      } else if ( i == -1 ) // file or folder in top folder
      { 
        sp.path = "" ;
        if ( type.equals( "f" ) ) sp.name = "/" ; else sp.name = "" ;
        break ;
      } else if ( i == sp.name.length() - 1 ) // ends with '/'
      {
        sp.name = sp.name.substring( 0, sp.name.length() - 1 ) ;
        if ( type.equals( "f" ) ) { sp.path = sp.name ; sp.name = "/" ; break ; } 
        continue ;
      } else
      {
        sp.path = sp.name.substring( 0, i ) ;
        sp.name = sp.name.substring( i+1 ) ;
        break ;
      }
   }
   while( true ) ;
   if ( u != null && u.getQuery() != null ) sp.name += "?" + u.getQuery() ; 
  return sp ;
 }

 // Getters and setters below
 
 public String getHost()
  {
   return host;
  }

 public void setHost( String host )
  {
   this.host = host;
  }

 public String getName()
  {
   return name;
  }

 public void setName( String name )
  {
   this.name = name;
  }

 public String getPath()
  {
   return path;
  }

 public void setPath( String path )
  {
   this.path = path;
  }
 

}
