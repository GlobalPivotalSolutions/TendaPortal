/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "special" area, containing urls found by log processing, but not by the crawler
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 */
public class LogLinks extends IndexArea
{
  public static final Logger LOG = LoggerFactory.getLogger( LogLinks.class );

  /**
   * Default parameterless constructor
   *
   */
  public LogLinks()
  {}

  /**
   * LogLinks factory
   * 
   * @param site
   *          parent site object
   * @return LogLinks object, a special case of IndexArea
   * @throws Exception
   *
   */
  static public LogLinks newLogLinks( IndexSite site ) throws Exception
  {
    LogLinks area = new LogLinks();

    LOG.info( "New index area: loglinks " );
    area.name = "loglinks";
    area.setEnabled( site.getCfg().getInherited( "enabled.loglinks", true ) );
    area.site = site;
    area.interval = site.getCfg().get( "interval.loglinks", 0 );
    area.areaClause = " where site=\'" + site.name + "\' and area=\'loglinks\'";
    area.exclusions = site.getCfg().getAll( "exclude.loglinks", true );
    area.inclusions = site.getCfg().getAll( "include.loglinks", true );
    area.setCrawlingDays();
    area.site.indexer.getDb().readDbValues( area );
    area.depth = site.getCfg().getInherited( "depth.loglinks", 1 );

    return area;
  }

  // Only reindex loglinks when time comes
  boolean changed()
  {
    return false;
  }

  /**
   * Overrides the IndexArea injector. Takes URLs to inject from the database. One URL per line.
   * 
   */
  boolean inject() throws Exception
  {
    int lines = site.db.readUnindexedURLs( this );

    if ( lines == 0 )
    {
      return false;
    }
    return true;
  }

}
