/*
 * Maintains a cache of RDMS connections. Designed to be subclassed. Subclasses must
 * implement newRootDB amd newSiteDB methods. 
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.sql;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import au.csiro.cass.arch.utils.ConfigList;

public abstract class ConnectionCache
{
  IndexRootDB                          rootDB ; // root db interface
  Map<String, IndexSiteDB>            site2db ; // site -> db interface map
  String                         rootTargetDB ; // root db target string
  String                             lastSite ; // site for which last connection was requested 
  IndexSiteDB                  lastConnection ; // connection returned for the last site
  public String                           msg ;

 
  
  /**
   * Create and init the maps.
   * @throws Exception
   */
  protected void init() throws Exception 
  {
	site2db = new HashMap<String, IndexSiteDB>() ;
  }
  
  
  /**
   * Return root DB connection.
   * @return IndexRootDB connection object.
   * @throws Exception.
   */
  public IndexRootDB getRootConnection() throws Exception
  { return rootDB ; }
  
  
  
  /**
   * Return connection for a particular site.
   * @param site - site name for which get connection.
   * @return IndexSiteDB object for the site.
   * @throws Exception
   */
  public IndexSiteDB getSiteConnection( String site ) throws Exception
  {
	if ( lastSite != null && lastSite.equals( site ) ) return lastConnection ;
	IndexSiteDB db = site2db.get( site ) ;
	if ( db == null )
	{
	  ConfigList siteCfg = getSiteConfig( site  ) ;
	  db = newSiteDB( siteCfg, site ) ;
	  site2db.put( site, db ) ;
	}
	lastSite = site ; lastConnection = db ;
    return db ;
  }
  

  /**
   * Close connections.
   * @throws Exception
   */
  public void destroy() throws Exception
  {
    if ( rootDB != null ) 
        { try { rootDB.commit() ; rootDB.close() ; rootDB = null ; } catch( Exception e ) {} }
    for ( IndexSiteDB con : site2db.values() ) 
    	{ try { con.commit() ; con.close() ; } catch( Exception e ) {} }
    site2db.clear() ; 
  }
  
  
  /**
   * Create and return connection for a particular site, given a configuration object.
   * @param cfg - Arch configuration object.
   * @param site - site name for which create connection.
   * @return IndexSiteDB object for the site.
   * @throws Exception
   */  
  protected abstract IndexSiteDB newSiteDB( ConfigList cfg, String site ) throws Exception ;

  
  /**
   * Return configuration object for a particular site.
   * @param site - site name for which return configuration.
   * @return ConfigList object for the site.
   * @throws Exception
   */
  protected abstract ConfigList getSiteConfig( String site ) throws Exception ;
  
  
}
