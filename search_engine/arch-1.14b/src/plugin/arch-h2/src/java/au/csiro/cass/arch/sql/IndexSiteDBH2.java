/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.IndexSiteDBBaseImpl;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * A set of db related functions needed by IndexSite and LogSite objects
 * 
 */
public class IndexSiteDBH2 extends IndexSiteDBBaseImpl
{
  public static final Logger LOG = LoggerFactory.getLogger( IndexSiteDBH2.class );

  /**
   * Constructor
   * 
   * @param cfg
   *          configuration parameters
   * 
   */
  IndexSiteDBH2( ConfigList cfg )
  {
    super( cfg );
  };

  /**
   * Factory, returns initialised and connected object.
   * 
   * @param cfg
   *          configuration parameters
   * @param site
   *          site name
   * 
   */
  public static IndexSiteDBH2 newIndexSiteDBH2( ConfigList cfg, String site, boolean servletMode ) throws Exception
  {
    String targetDB = cfg.get( "target.db", null );
    if ( servletMode && targetDB != null && targetDB.contains( "h2:embedded" ) )
      return null;
    IndexSiteDBH2 dd = new IndexSiteDBH2( cfg );
    dd.site = site;
    dd.table = "site_" + site;
    dd.servletMode = servletMode;
    dd.connect();
    // dd.init() ; This is being called from connect() now
    dd.url = cfg.get( "url", "" );
    if ( dd.url.length() == 0 )
      LOG.warn( "URL is missing for site " + site + ", ignore if the site is being dropped." );

    return dd;
  }

}
