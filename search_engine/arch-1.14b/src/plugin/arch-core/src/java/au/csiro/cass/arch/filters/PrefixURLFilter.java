/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.filters;

import java.util.ArrayList;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.net.URLFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.SiteAreas;
import au.csiro.cass.arch.index.SiteMatcher;
import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.utils.Utils;

/**
 * Filters URLs based on lists of roots and exclusions of the areas being crawled.
 */
public class PrefixURLFilter implements URLFilter, DBConnected
{

  public static final Logger LOG = LoggerFactory.getLogger( PrefixURLFilter.class ) ;
  
  static private Configuration           conf ;
  static private SiteAreas               known ;
  static private SiteMatcher          matcher ;
  static private boolean  filterActive = true ;
  static private boolean    connected = false ;
  static private boolean  loglinks, bookmarks ;
  static private int             maxUrlLength ;
    
  public PrefixURLFilter() throws Exception { }

  public synchronized String filter( String url )
  {
    try { 
          url = url.replace( ' ', '+' ) ; // there should not be spaces in URLs
    	  if ( !filterActive )
            { 
           	  // if ( LOG.isDebugEnabled() ) LOG.debug( "URL in PrefixURLFilter: " + url ) ;
              return url ;
            }
          if ( !connected ) { connect() ; if ( !filterActive ) return url ; }

          if ( maxUrlLength > 0 && url.length() > maxUrlLength ) return null ;
          String url1 = url.toLowerCase() ;
          boolean res = false ;
          // if indexing bookmarks, allow everything
          if ( !bookmarks )
           { 
              SiteAreas sa = matcher.getSiteAreas( url1 ) ;
              if ( sa == null || !sa.hasAreas() || sa.getSite() == null ) res = false ;
              else 
              if ( ( known.getSite() != null && known.getSite().equalsIgnoreCase( sa.getSite() ) ||
              	    known.getSite() == null && sa.getSite() != null ) &&
              	  ( known.hasArea( sa.getAreas() ) || !known.hasAreas() ) ) res = true ;
           } else res = true ; 

          String s = res ? " passed " : " blocked " ;
          if ( LOG.isDebugEnabled() )
                                    LOG.debug( "URL in PrefixURLFilter: " + s + url ) ;
          if ( res ) return url ; 
                               else return null ;
        } catch ( Exception e )
        {
          LOG.error( url + " " + e.getMessage() ) ;  
        }
    return null ;
  }
    
  public static void main( String args[] ) throws Exception
  {
  }

  public void setConf(Configuration conf ) 
  {
    this.conf = conf;
    try {  disconnect() ; } 
      catch( Exception e ) {} 
  }
  
  synchronized void connect() throws Exception
  {
	String action = conf.get( "arch.action" ) ;
	if ( action == null || !action.equals( "Generate" ) ) { filterActive = false ; return ; } 
	IndexRootDB rootDb = Utils.connect( conf, false ) ;
	String site = conf.get( "arch.site" ) ;
	String area = conf.get( "arch.area" ) ;
	String maxLengthStr = conf.get( "max.url.length" ) ;
	maxUrlLength = maxLengthStr == null ? -1 : Integer.parseInt( maxLengthStr ) ;
	loglinks = area != null && area.equalsIgnoreCase( "loglinks" ) ;
	bookmarks = area != null && area.equalsIgnoreCase( "bookmarks" ) ;
	known = new SiteAreas( null ) ;
	if ( site != null && site.length() > 0 ) known.setSite( site ) ;
    if ( area != null && area.length() > 0 && !loglinks ) known.addArea( area ) ;
    
    Map<String, ArrayList<String>[]> roots = rootDb.readRoots() ;
    matcher = SiteMatcher.newSiteMatcher( roots ) ;
    rootDb.close();  
    connected = true ;
    if ( LOG.isInfoEnabled() )
      LOG.info( "Configured PrefixURLFilter, filter active=" + filterActive ) ;
    Utils.prefixFilter = this ;
  }
  
  public Configuration getConf() {
    return this.conf;
  }
    
  public synchronized void disconnect() throws Exception
  {
    matcher = null ;
	connected = false ;
	filterActive = true ;
  }

  public Logger getLOG()
   {
    return LOG;
   }
  
  
}
