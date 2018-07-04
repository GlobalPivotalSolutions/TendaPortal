/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.index;

import java.io.File;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.auth.Permissions;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Models an indexed web site, part of a multi-site index
 * 
 */
public class IndexSite
{

  public static final Logger LOG = LoggerFactory.getLogger( IndexSite.class );

  /** root process running everything */
  protected Indexer indexer;
  /** site configuration parameters */
  private ConfigList cfg;
  /** base url of this site */
  protected String url;
  /** short name/alias given to this site */
  protected String name;
  /** name of used db table */
  private String dbname;
  /** this site's directory */
  private File dir;
  /** this site's root directory */
  private File root;
  /** database interface */
  protected IndexSiteDB db;
  /** site areas defined in configuration */
  protected IndexArea[] areas;
  /** crawling depth */
  private int depth;
  /** true if use indexer object to send email */
  protected boolean emailViaIndexer;
  /** email body to send */
  protected StringBuffer email;
  /** min email level */
  protected int emailLevel;
  /** max email length */
  protected int maxEmailLength;

  /**
   * creates a new IndexSite instance
   * 
   * @param dir
   *          directory containing site data
   * @param boss
   *          the Indexer object
   * @return created IndexSite object
   * @throws Exception
   */
  static IndexSite newIndexSite( String siteName, Indexer indexer ) throws Exception
  {
    File site = new File( indexer.getDataDir() + "/sites/" + siteName );
    if ( site.exists() )
      return newIndexSite( site, indexer );
    else
      return InterimIndexSite.newInterimIndexSite( siteName, indexer );
  }

  /**
   * creates a new IndexSite instance
   * 
   * @param dir
   *          directory containing site data
   * @param boss
   *          the Indexer object
   * @return created IndexSite object
   * @throws Exception
   */
  static IndexSite newIndexSite( File dir, Indexer indexer ) throws Exception
  {

    IndexSite site = new IndexSite();
    site.indexer = indexer;
    site.dir = dir;
    site.email = new StringBuffer();
    site.name = dir.getName();
    if ( !Utils.isProperName( site.name ) )
    {
      throw new Exception( "Bad name: " + site.name + " - must be alphanumeric starting with a letter." );
    }
    site.setCfg( ConfigList.newConfigList( dir.getCanonicalPath() + "/config.txt", indexer.getCfg() ) );
    String emailRecipient = site.getCfg().get( "mail.recipient", "" );
    if ( emailRecipient.trim().length() == 0 )
    {
      site.emailViaIndexer = true;
    } else
    {
      site.emailViaIndexer = false;
    }

    String levelLimit = site.getCfg().getInherited( "mail.level", "INFO" );
    for ( site.emailLevel = 0; site.emailLevel < Indexer.codes.length; site.emailLevel++ )
    {
      if ( Indexer.codes[ site.emailLevel ].equalsIgnoreCase( levelLimit ) )
      {
        break;
      }
    }
    if ( site.emailLevel == Indexer.codes.length )
    {
      throw new Exception( "Wrong mail.level parameter." );
    }
    site.maxEmailLength = site.getCfg().getInherited( "max.email.length", 100000 );

    // save configuration to the database
    site.url = site.getCfg().get( "URL", null );
    if ( site.url == null )
    {
      Utils.missingParam( "Base URL for site " + site.name );
    }
    indexer.getDb().regSite( site.name, site.url );
    indexer.getDb().writeConfig( site.name, site.getCfg() );
    site.setDepth( site.getCfg().getInherited( "depth", 30, "CRAWLING_DEPTH" ) );
    if ( !site.url.endsWith( "/" ) )
    {
      site.url += "/";
    }
    // LOG.trace( "New index site: " + site.name ) ;
    Configuration nutchConf = NutchConfiguration.create();
    DBInterfaceFactory factory = DBInterfaceFactory.get( nutchConf );
    String database = site.getCfg().getInherited( "database", "MySQL" );
    DBInterface intrf = factory.get( database );
    site.db = intrf.newIndexSiteDB( site.getCfg(), site.name, false );
    site.areas = IndexArea.newIndexAreas( site );
    // Clean working directories...
    String dd = dir.getCanonicalPath() + "/areas";
    Utils.rmdir( dd, null );

    return site;
  }

  /**
   * Does everything to release resources
   * 
   * @throws Exception
   */
  public void close() throws Exception
  {
    indexer = null;
    setCfg( null );
    url = name = setDbname( null );
    dir = root = null;
    root = null;
    if ( db != null )
    {
      db.close();
    }
    db = null;
    if ( areas != null )
    {
      for ( int i = 0; i < areas.length; i++ )
      {
        areas[ i ].close();
        areas[ i ] = null;
      }
    }
    areas = null;
  }

  /**
   * Prepares all areas or a given area for crawling
   * 
   * @param area
   *          name of area to prepare if to prepare all areas
   * @param firstCall
   *          true if this is first area to prepare
   * @return true if there is something to fetch, else false
   * @throws Exception
   */
  boolean prepare( String area, boolean firstCall ) throws Exception
  {
    String line = "site " + name;
    if ( area != null )
    {
      line += ", area " + area;
    } else
    {
      line += ", all areas.";
    }
    System.out.println( "Preparing " + line );
    appendEmail( Indexer.INFO, "Preparing " + line );
    if ( firstCall )
    {
      Permissions[] permissions = getCfg().getPermissions();
      db.applyPermissions( permissions );
      db.execute( "update " + db.getTable() + " set cached=0 " );
      // reset alias information - it will be set by the removing duplicates process
      if ( indexer.getCfg().get( "remove.duplicates", true ) && !indexer.isWatchMode() )
      {
        db.resetAliasof() ;
      }
    }
    boolean result = false;
    // if parallel indexing and call to process loglinks => at the end of processing and
    // can just mark everything unindexed for the next run and return
    if ( !indexer.isWatchMode() && indexer.isParallelIndexing() && area != null && area.equals( "loglinks" ) )
    {
      db.markIndexed( false );
    } else
    {
      for ( int i = 0; i < areas.length; i++ )
      {
        String n = areas[ i ].getName();
        // have a particular area to process and this one is not it
        if ( area != null && !area.equals( n ) )
        {
          continue;
        }
        // bookmarks are processed separately in any case
        if ( area == null && "bookmarks".equals( n ) )
        {
          continue;
        }
        // this is loglinks - ignore it, will be processed separately
        // if ( indexer.watchMode && area == null && "loglinks".equals( n ) ) continue ;
        if ( !indexer.isParallelIndexing() && area == null && "loglinks".equals( n ) )
        {
          continue;
        }
        boolean crawlArea = areas[ i ].prepare(); // Only if time to
        result |= crawlArea;
      }
    }
    System.out.println( "Finished preparing " + line );
    appendEmail( Indexer.INFO, "Finished preparing " + line );
    sendEmail( null );
    return result;
  }

  /**
   * Checks if any of the areas needs to be crawled.
   * 
   * @return true if at least one of the areas is due for crawling
   */
  boolean needToCrawl()
  {
    for ( int i = 0; i < areas.length; i++ )
    {
      if ( areas[ i ].isEnabled() && areas[ i ].needToCrawl() )
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Add a string to email message that will be sent when sendEmail is called
   * 
   * @param level
   *          reporting level: 0 - DEBUG, 1 - INFO, 2 - WARN, 3 - ERROR, 4 - OFF
   * @param text
   *          text to append
   * 
   * @throws Exception
   */
  public void appendEmail( int level, String text ) throws Exception
  {
    if ( level < emailLevel )
    {
      return;
    }
    if ( emailViaIndexer )
    {
      indexer.appendEmail( level, text, Indexer.maxEmailLength );
    } else
    {
      Indexer.appendEmail( level, email, text, maxEmailLength );
    }
  }

  /**
   * Send email if configured
   * 
   * @param subject
   *          email subject
   * 
   * @throws Exception
   */
  public void sendEmail( String subject ) throws Exception
  {
    if ( !emailViaIndexer && email.length() > 0 )
    {
      Indexer.sendEmail( email.toString(), subject, getCfg() );
    }
  }

  // Getters and setters below

  public String getUrl()
  {
    return url;
  }

  public void setBaseUrl( String url )
  {
    this.url = url;
  }

  public Indexer getIndexer()
  {
    return indexer;
  }

  public void setIndexer( Indexer boss )
  {
    this.indexer = boss ;
  }

  public File getDir()
  {
    return dir;
  }

  public File getRoot()
  {
    return root;
  }

  public void setRoot( File root )
  {
    this.root = root;
  }

  public String getName()
  {
    return name;
  }

  public IndexSiteDB getDb()
  {
    return db;
  }

  public void setName( String name )
  {
    this.name = name;
  }

  public String getDbname()
  {
    return dbname;
  }

  public String setDbname( String dbname )
  {
    this.dbname = dbname;
    return dbname;
  }

  public int getDepth()
  {
    return depth;
  }

  public void setDepth( int depth )
  {
    this.depth = depth;
  }

  public ConfigList getCfg()
  {
    return cfg;
  }

  public void setCfg( ConfigList cfg )
  {
    this.cfg = cfg;
  }

}
