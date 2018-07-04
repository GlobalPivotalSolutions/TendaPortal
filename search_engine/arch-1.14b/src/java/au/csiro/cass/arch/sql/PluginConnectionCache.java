/*
 * Maintains a cache of RDMS connections based on Nutch plugin architecture.
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */


package au.csiro.cass.arch.sql;

import org.apache.hadoop.conf.Configuration;

import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Implements Nutch-side connection cache factory. Configuration objects
 * are passed via root database because arch configuration tree may not be
 * available at the time of running on a Hadoop cluster. 
 * 
 */
public class PluginConnectionCache extends ConnectionCache
{
  protected static PluginConnectionCache    one ; // one existing object
  Configuration                            conf ; // Nutch configuration object
  DBInterfaceFactory                    factory ;
	
  /**
   * Connection cache factory.
   * @param conf - Hadoop configuration object.
   * @param servletMode - should be always false here.
   * @return Connection cache object.
   * @throws Exception
   */
  public static synchronized ConnectionCache newPluginConnectionCache( Configuration conf, boolean servletMode )
  throws Exception 
  {
	if ( one != null ) return one ;
	PluginConnectionCache one0 = new PluginConnectionCache() ;
    one0.rootDB = Utils.connect( conf, servletMode ) ;
    one0.init() ;
    one0.conf = conf ;
    one0.factory = DBInterfaceFactory.get( conf ) ;
	one0.rootTargetDB = one0.rootDB.getCfg().get( "target.db", null ) ;
	one = one0 ;
    return one ;
  }
  
  /**
   * Create and return connection for a particular site, given a configuration object.
   * @param cfg - Arch configuration object.
   * @param site - site name for which create connection.
   * @return IndexSiteDB object for the site.
   * @throws Exception
   */  
  protected IndexSiteDB newSiteDB( ConfigList cfg, String site ) throws Exception 
  {
    String database = cfg.getInherited( "database", "MySQL" ) ;
    DBInterface intrf = factory.get( database ) ;
    if ( intrf == null )
    	throw new Exception( "Can't create interface for " + database ) ;
    IndexSiteDB con = intrf.newIndexSiteDB( cfg, site, false ) ;
    if ( con == null )
    	throw new Exception( "Can't create db connection for site " + site ) ;
    return con ;
  }
  
  
  @Override
  protected ConfigList getSiteConfig(String site) throws Exception
  {
	return rootDB.readConfig( site ) ;
  }
  
  @Override
  public synchronized void destroy() throws Exception
  {
    super.destroy() ;
    one = null ;
  }

  
}
