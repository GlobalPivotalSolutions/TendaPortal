/*
 * Maintains a cache of RDMS connections based on Nutch plugin architecture.
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */


package au.csiro.cass.arch.sql;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;

import au.csiro.cass.arch.utils.ConfigList;

/**
 * Implements Solr-side connection cache factory. Configuration objects
 * are created using Arch configuration directory tree.
 * 
 */
public class SolrConnectionCache extends ConnectionCache
{
  protected static SolrConnectionCache    one ; // one existing object
  DBInterface                     dbInterface ;
  SolrParams                           params ;
  ConfigList                          rootCfg ;
	
  
  /**
   * Connection cache factory.
   * @param core - Solr core.
   * @param params - Solr params object.
   * @param servletMode - should be always true.
   * @return Connection cache object.
   * @throws Exception
   */
  public static synchronized ConnectionCache newSolrConnectionCache( SolrCore core, SolrParams params,
          boolean servletMode ) throws Exception 
  {
    if ( one != null ) return one ;
    SolrConnectionCache one0 = new SolrConnectionCache() ;
    one0.init() ;
    String solrHome = SolrResourceLoader.locateSolrHome().toString();
    String archDir = solrHome + "/conf/arch" ;
    
    one0.rootCfg = ConfigList.newConfigList( archDir + "/config.txt" ) ;
    String database = one0.rootCfg.get( "database", "MySQL" ) ;
    String defClazz = "au.csiro.cass.arch.sql.DBInterfaceMySQL" ;
    if ( database.equals( "Derby" ) )
      defClazz = "au.csiro.cass.arch.sql.DBInterfaceDerby" ;
    if ( database.equalsIgnoreCase( "H2" ) )
      defClazz = "au.csiro.cass.arch.sql.DBInterfaceH2" ;
    @SuppressWarnings( "unchecked" )
    Class<DBInterface> expected = (Class<DBInterface>)Class.forName( defClazz ) ;
    String clazz = one0.rootCfg.get( "db.interface", defClazz ) ;
    SolrResourceLoader resourceLoader = core.getResourceLoader() ;
    one0.dbInterface = (DBInterface)resourceLoader.newInstance( clazz, expected ) ;
    one0.rootTargetDB = one0.rootCfg.get( "target.db", null ) ;
    if ( one0.rootTargetDB.contains( ":embedded" ) ) 
    	{ one0.rootDB = null ;
    	  one0.msg = "Arch database is embedded and not available to servlets." ;
    	}
       else one0.rootDB = one0.dbInterface.newIndexRootDB( one0.rootCfg, servletMode ) ;
    one0.init() ;
    one0.params = params ;
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
    IndexSiteDB con = dbInterface.newIndexSiteDB( cfg, site, false ) ;
    if ( con == null )
    	throw new Exception( "Can't create db connection for site " + site ) ;
    return con ;
  }
  
  
  @Override
  protected ConfigList getSiteConfig(String site) throws Exception
  {
	String solrHome = SolrResourceLoader.locateSolrHome().toString();
	String archDir = solrHome + "/conf/arch" ;
	return ConfigList.newConfigList( archDir + "/sites/" + site + "/config.txt", rootCfg ) ;
  }
  
  @Override
  public synchronized void destroy() throws Exception
  {
    super.destroy() ;
    one = null ;
  }

  
}
