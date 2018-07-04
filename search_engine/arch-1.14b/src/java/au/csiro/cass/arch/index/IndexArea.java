package au.csiro.cass.arch.index;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.utils.Utils;

/**
 * Implements index area - a part of a web site defined by root, include and exclude paths
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class IndexArea
{
  public static final Logger LOG = LoggerFactory.getLogger( IndexArea.class );

  public static final int STATUS_NEW = 0;
  public static final int STATUS_FAILED = 1;
  public static final int STATUS_CRAWLING_DONE = 2;
  public static final int STATUS_MERGING_DONE = 3;
  public static final int STATUS_FINISHED = 9;
  public static String days[] = { "sun", "mon", "tue", "wed", "thu", "fri", "sat" };
  public static final int MAX_AREA_NAME_LENGTH = 30;

  /** reference to parent site object */
  protected IndexSite site;
  /** name of the area */
  protected String name;
  /** re-indexing interval */
  protected int interval;
  /** processing status */
  private int status;
  /** build number */
  private int build;
  /** crawling depth, when sequential indexing */
  protected int depth;
  /** last Date this area was indexed */
  private Date lastIndexed;
  /** expiration query for this area */
  private String expire;
  /** full path to new data directory */
  private String newPath;
  /** full path to old data directory */
  private String oldPath;
  /** starting points for indexing this area */
  protected String[] roots;
  /** starting points for indexing this area used before */
  private String[] oldRoots;
  /** sub-trees to exclude from indexing used before */
  private String[] oldExclusions;
  /** sub-trees to exclude from indexing */
  protected String[] exclusions;
  /** sub-trees to include in the area used before */
  private String[] oldInclusions;
  /** sub-trees to include in the area */
  protected String[] inclusions;
  /** good days for indexing this area, if any */
  private String[] doDays;
  /** internal message container, for exceptions etc. */
  private String msg;
  /** condition clause identifying this area in SQL statements */
  protected String areaClause;
  /** true - normal (default), false - disabled */
  private boolean enabled;
  /** build number */
  private int buildNumber;
  /** 1 if this area needs to be crawled */
  private int markedForCrawling;
  /** true if this area is a stub */
  private boolean isStab;

  /**
   * Default constructor
   *
   */
  public IndexArea()
  {}

  /**
   * Do best for releasing resources
   *
   */
  public void close()
  {
    name = newPath = oldPath = msg = areaClause = null;
    roots = oldRoots = oldExclusions = exclusions = oldInclusions = inclusions = doDays = null;
  }

  /**
   * Interim area factory, creates area object based on interim site URLs and db contents.
   * 
   * @param site
   *          InterimIndexSite object of the site that this area is a part of
   * @param roots
   *          ArrayList of crawling roots
   * @throws Exception
   */
  static public IndexArea[] newInterimIndexArea( IndexSite site, ArrayList<String> roots ) throws Exception
  {
    IndexArea area = new IndexArea();
    IndexArea[] areas = new IndexArea[ 1 ];
    areas[ 0 ] = area;

    LOG.info( "New interim index area of site: " + site.name );
    area.name = "main";
    site.getCfg().add( "area", "main" );
    site.getCfg().add( "enabled.main", "true" );
    area.site = site;
    area.areaClause = " where site=\'" + site.name + "\' and area=\'" + area.name + "\'";

    area.exclusions = null;
    area.inclusions = new String[] { site.url };
    site.getCfg().add( "include.main", site.url );
    area.roots = roots.toArray( new String[ roots.size() ] );
    site.getCfg().add( "root.main", area.roots );
    area.setCrawlingDays();
    area.setExpire();
    area.setEnabled( true );
    area.site.indexer.getDb().readDbValues( area );
    area.depth = site.getCfg().getInherited( "depth." + area.name, site.getDepth(), "CRAWLING_DEPTH" );

    return areas;
  }

  /**
   * Area factory, creates area object based on site configuration and db contents
   * 
   * @param site
   *          IndexSite object of the site that this area is a part of
   * @param name
   *          area name
   * @throws Exception
   */
  static public IndexArea newIndexArea( IndexSite site, String name ) throws Exception
  {
    IndexArea area = new IndexArea();

    LOG.info( "New index area: " + name );
    area.name = name;
    area.site = site;
    area.areaClause = " where site=\'" + site.name + "\' and area=\'" + area.name + "\'";

    if ( !Utils.isProperName( name ) )
    {
      throw new Exception( "Bad name: " + name + " - must be alphanumeric starting with a letter." );
    }
    if ( name.length() > MAX_AREA_NAME_LENGTH )
    {
      throw new Exception( "Too long name: " + name + " - must be up to " + MAX_AREA_NAME_LENGTH + " characters." );
    }
    area.exclusions = site.getCfg().getAll( "exclude." + area.name, false );
    area.inclusions = site.getCfg().getAll( "include." + area.name, false );
    area.roots = site.getCfg().getAll( "root." + area.name );
    if ( area.roots == null )
    {
      area.roots = site.getCfg().getAll( "root." + area.name, "SITE_URL" );
    }
    if ( area.roots == null || area.roots.length == 0 )
    {
      throw new Exception( "No root URLs specified for area " + area.name + ", site " + site.name + "." );
    }
    area.setCrawlingDays();
    area.setExpire();
    area.setEnabled( site.getCfg().get( "enabled." + area.name, true ) );
    area.site.indexer.getDb().readDbValues( area );
    area.depth = site.getCfg().getInherited( "depth." + area.name, site.getDepth(), "CRAWLING_DEPTH" );

    return area;
  }

  /**
   * Creates all area objects for this site
   * 
   * @param site
   *          site object
   * @return array of created area objects
   * @throws Exception
   */

  static public IndexArea[] newIndexAreas( IndexSite site ) throws Exception
  {
    String[] areaNames = site.getCfg().getAll( "area" );
    if ( areaNames == null || areaNames.length == 0 )
    {
      throw new Exception( "No indexing areas defined." );
    }
    IndexArea[] areas = new IndexArea[ areaNames.length ];
    for ( int i = 0; i < areas.length; i++ )
    {
      if ( areaNames[ i ].equals( "loglinks" ) )
      {
        areas[ i ] = LogLinks.newLogLinks( site );
      } else if ( areaNames[ i ].equals( "bookmarks" ) )
      {
        areas[ i ] = Bookmarks.newBookmarks( site );
      } else
      {
        areas[ i ] = newIndexArea( site, areaNames[ i ] );
      }
    }
    return areas;
  }

  /**
   * Prepares the area for crawling and does crawling if parallel indexing is off.
   * 
   * @return true if there were any URLs written to indexer's seed URL file.
   * @throws Exception
   * 
   */
  public boolean prepare() throws Exception
  {
    if ( ( !site.indexer.isWatchMode() && !needToCrawl() ) || !isEnabled() )
    {
      return false;
    }
    String areaToMark = name.equals( "loglinks" ) ? null : name;
    String siteToMark = site.name.equals( "bookmarks" ) ? null : site.name;
    if ( !name.equals( "bookmarks" ) && !( name.equals( "loglinks" ) && site.indexer.isParallelIndexing() ) )
    {
      site.indexer.getDb().markForCrawl( siteToMark, areaToMark, 1 );
    }
    if ( !site.indexer.isWatchMode() || name.equals( "loglinks" ) )
    {
      if ( !inject() )
      {
        return false;
      }
    }

    // if parallel indexing is being done, there is nothing else to do
    markedForCrawling = 1;
    oldExclusions = exclusions;
    oldInclusions = inclusions;
    oldRoots = roots;
    site.indexer.getDb().writeDbValues( this, true );
    buildNumber = site.indexer.getBuildNumber();
    setLastIndexed( site.indexer.getIndexingStart() );// in advance, as it is marked for crawl
    if ( site.indexer.isParallelIndexing() )
    {
      return true;
    }
    int res = site.indexer.crawl( site.name, name, depth );
    if ( !name.equals( "bookmarks" ) )
    {
      site.indexer.getDb().markForCrawl( siteToMark, areaToMark, 0 );
    }
    // finalise if success
    if ( res != -1 )
    {
      finalise();
    }
    return false;
  }

  /**
   * Finalises area crawling, saves state changes to the database in case of sequential indexing
   * 
   * @throws Exception
   * 
   */
  void finalise() throws Exception
  {
    markedForCrawling = 0;
    site.indexer.getDb().writeDbValues( this, false );
  }

  /**
   * Injects URLs for crawling. Note the delta parameter. Used it to experiment with injecting very large numbers of
   * URLs.
   * 
   * @return true if something was injected
   */
  boolean inject() throws Exception
  {
    if ( roots == null || roots.length == 0 )
    {
      return false;
    }
    for ( int i = 0; i < roots.length; i++ )
    {
      if ( roots[ i ] != null && roots[ i ].length() > 0 )
      {
        site.indexer.getCrawlRoots().write( /* site.baseUrl + */roots[ i ] + "\n" );
      }
    }
    return true;
  }

  /**
   * Checks if this area needs to be crawled, based on the last time it had been crawled and configured crawling period.
   * 
   * @return true if the area has to be crawled
   */
  boolean needToCrawl()
  {
    if ( !isEnabled() )
    {
      return false;
    }
    if ( markedForCrawling == 1 )
    {
      return true; // have not finished last time
    }
    if ( buildNumber == site.indexer.getBuildNumber() )
    {
      return false;
    }
    // if list of roots or exclusions has been changed - need to recrawl
    if ( changed() )
    {
      return true;
    }

    Date nextIndexing = new Date( getLastIndexed().getTime() + (long)interval * 24 * 3600 * 1000 - 3600 * 1000 );
    Date now = site.indexer.getIndexingStart();
    // if there are defined preferred days and this is a repeat crawl
    if ( doDays != null && doDays.length > 1 && buildNumber > 0 )
    {
      String day = now.toString().substring( 0, 3 );
      if ( Utils.in( doDays, day ) < 0 )
      {
        return false; // a bad day to crawl
      }
    }
    if ( nextIndexing.before( now ) )
    {
      return true;
    }
    return false;
  }

  /**
   * Checks if this area has changed since last crawl crawled and configured crawling period.
   * 
   * @return true if the area has changed
   */
  boolean changed()
  {
    if ( !Utils.isSame( roots, oldRoots ) || !Utils.isSame( exclusions, oldExclusions )
        || !Utils.isSame( inclusions, oldInclusions ) )
    {
      return true;
    } else
    {
      return false;
    }
  }

  /**
   * Parses configuration crawling days parameter and saves the result
   * 
   * @throws Exception
   */
  void setCrawlingDays() throws Exception
  {
    String indexTimes = site.getCfg().get( "interval." + name, "0" );
    doDays = indexTimes.split( "[ ,]+" );
    try
    {
      interval = Integer.parseInt( doDays[ 0 ] );
    } catch ( Exception e )
    {
      throw new Exception( "First parameter must be number of days in interval, area " + name );
    }
    for ( int i = 1; i < doDays.length; i++ )
    {
      if ( Utils.in( days, doDays[ i ] ) < 0 )
      {
        throw new Exception( doDays[ i ] + " is not a recognized week day in area " + name );
      }
    }
  }

  void setExpire() throws Exception
  {
    SimpleDateFormat format = new SimpleDateFormat( "yyyy-MM-dd'T'kk:mm:ss.SSS'Z'" );
    Date now = new Date();
    expire = "";

    String expire = site.getCfg().get( "expire." + name, "-1" );
    long aa = -1;
    boolean multiply = expire.charAt( 0 ) == 'x';
    if ( multiply )
    {
      expire = expire.substring( 1 );
    }
    try
    {
      aa = Integer.parseInt( expire );
    } catch ( Exception e )
    {
      throw new Exception( "Error in expire period: " + expire + " in area " + name );
    }
    if ( multiply )
    {
      aa = aa * interval;
    }
    if ( aa < 0 )
    {
      return; // never expires
    }
  }

  /*
   * Getters and setters below
   */
  public String getAreaClause()
  {
    return areaClause;
  }

  public void setAreaClause( String areaClause )
  {
    this.areaClause = areaClause;
  }

  public int getBuild()
  {
    return build;
  }

  public void setBuild( int build )
  {
    this.build = build;
  }

  public String[] getExclusions()
  {
    return exclusions;
  }

  public void setExclusions( String[] exclusions )
  {
    this.exclusions = exclusions;
  }

  public int getInterval()
  {
    return interval;
  }

  public void setInterval( int interval )
  {
    this.interval = interval;
  }

  public Date getLastIndexed()
  {
    if ( lastIndexed == null )
    {
      lastIndexed = new Date();
      lastIndexed.setTime( lastIndexed.getTime() - 365 * 24 * 360 * 1000 );
    }
    return lastIndexed;
  }

  public void setLastIndexed( Date lastIndexed )
  {
    this.lastIndexed = lastIndexed;
  }

  public String getMsg()
  {
    return msg;
  }

  public void setMsg( String msg )
  {
    this.msg = msg;
  }

  public String getName()
  {
    return name;
  }

  public void setName( String name )
  {
    this.name = name;
  }

  public String getNewPath()
  {
    return newPath;
  }

  public void setNewPath( String newPath )
  {
    this.newPath = newPath;
  }

  public String[] getOldExclusions()
  {
    return oldExclusions;
  }

  public void setOldExclusions( String[] oldExclusions )
  {
    this.oldExclusions = oldExclusions;
  }

  public String getOldPath()
  {
    return oldPath;
  }

  public void setOldPath( String oldPath )
  {
    this.oldPath = oldPath;
  }

  public String[] getOldRoots()
  {
    return oldRoots;
  }

  public void setOldRoots( String[] oldRoots )
  {
    this.oldRoots = oldRoots;
  }

  public String[] getRoots()
  {
    return roots;
  }

  public void setRoots( String[] roots )
  {
    this.roots = roots;
  }

  public IndexSite getSite()
  {
    return site;
  }

  public void setSite( IndexSite site )
  {
    this.site = site;
  }

  public int getStatus()
  {
    return status;
  }

  public void setStatus( int status )
  {
    this.status = status;
  }

  public void setOldInclusions( String[] oldInclusions )
  {
    this.oldInclusions = oldInclusions;
  }

  public String[] getOldInclusions()
  {
    return oldInclusions;
  }

  public String[] getInclusions()
  {
    return inclusions;
  }

  public void setInclusions( String[] inclusions )
  {
    this.inclusions = inclusions;
  }

  public int getBuildNumber()
  {
    return buildNumber;
  }

  public void setBuildNumber( int buildNumber )
  {
    this.buildNumber = buildNumber;
  }

  public boolean isStab()
  {
    return isStab;
  }

  public int getMarkedForCrawling()
  {
    return markedForCrawling;
  }

  public void setMarkedForCrawling( int markedForCrawling )
  {
    this.markedForCrawling = markedForCrawling;
  }

  /**
   * @return the expire
   */
  public String getExpire()
  {
    return expire;
  }

  /**
   * @param expire
   *          the expire to set
   */
  public void setExpire( String expire )
  {
    this.expire = expire;
  }

  public boolean isEnabled()
  {
    return enabled;
  }

  public void setEnabled( boolean enabled )
  {
    this.enabled = enabled;
  }

}
