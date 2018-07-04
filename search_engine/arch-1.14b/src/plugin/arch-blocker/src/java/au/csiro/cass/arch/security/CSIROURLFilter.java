package au.csiro.cass.arch.security;
/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.net.URLFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is provided as an example of using a site-specific URL filter
 * class to normalise URLs and block unwanted ones.
 * As it is, it is not relevant to your organisation, unless your organisation
 * is CSIRO Australia.
 * 
 */
public class CSIROURLFilter implements URLFilter
{

  public static final Logger LOG = LoggerFactory.getLogger( CSIROURLFilter.class ) ;
  
  static private Configuration           conf ;
    
  public CSIROURLFilter() throws Exception { }

  public synchronized String filter( String url )
  {
    try { 
    	  String lowCase = url.replace( ' ', '+' ).toLowerCase() ;
    	  boolean peopleFinder = lowCase.startsWith( "http://peoplefinder.csiro.au/" ) ;
    	  boolean cms = lowCase.indexOf( "://my.csiro.au/") > 0 || lowCase.indexOf( "://www.csiro.au/") > 0 ;
    	  // Most CSIRO URLs are case insensitive, but this may be different for other sites
    	  // Be careful not to change case of parameters as case may be essential there
          url = peopleFinder ? url.replace( ' ', '+' ) : lowCase ; // there should not be spaces in URLs
      	  // AAA url = url.replace( ".csiro.au/en/", ".csiro.au/" ) ;
      	  if ( cms )
      	  {
            // AAA if ( url.endsWith( ".aspx" ) ) url = url.substring( 0, url.length() - 5 ) ;
            // AAA if ( url.endsWith( "/" ) ) url = url.substring( 0, url.length() - 1 ) ;
            url = url.replace( "/%7e", "/~" ) ;
            int i = url.lastIndexOf( "/~" ) ;
            if ( i > 0 && false ) // AAA
            {
              int j = url.indexOf( '/', 10 ) ;
              url = url.substring( 0, j ) + url.substring( i ) ;
            }
      	  }
          boolean res = ! ( url.indexOf( ".ashx?" ) > 0 ) ; // block URLs of media files
// AAA          res &= ! ( url.indexOf( ".csiro.au/en." ) > 0 ) ;
// AAA          res &= ! ( peopleFinder && url.indexOf( "&pdf=true" ) > 0 ) ;
          String s = res ? " passed " : " blocked " ;
          if ( LOG.isDebugEnabled() )
                                    LOG.debug( "URL in CSIROURLFilter: " + s + url ) ;
          if ( res ) return url ; 
                               else return null ;
        } catch ( Exception e )
        {
          LOG.error( url + " " + e.getMessage() ) ;  
        }
    return null ;
  }
    
  public void setConf(Configuration conf ) 
  {
//    if ( action.equals( "Fetch" ) ) filterActive = false ;
    this.conf = conf;
  }
    
  public Configuration getConf() {
    return this.conf;
  }
    
  public Logger getLOG()
   {
    return LOG;
   }
  
  
}
