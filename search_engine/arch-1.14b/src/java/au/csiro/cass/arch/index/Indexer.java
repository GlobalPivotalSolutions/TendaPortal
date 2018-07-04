/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.index;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.logProcessing.LogProcessor;
import au.csiro.cass.arch.logProcessing.LogSite;
import au.csiro.cass.arch.security.Reporter;
import au.csiro.cass.arch.security.ReporterFactory;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexDB;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Main class that controls indexing operations, including logs processing
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class Indexer
{
  public static final Logger LOG = LoggerFactory.getLogger( Indexer.class );
  static int NUM_ARGS = 11;
  /** Email levels */
  public static final int DEBUG = 0, INFO = 1, WARN = 2, ERROR = 3, OFF = 4;
  /** Email levels */
  static public String[] codes = { "DEBUG", "INFO", "WARN", "ERROR", "OFF" };
  /** default minimal level of email messages */
  static int emailLevel = INFO;
  /** default maximal email length */
  static int maxEmailLength;

  /** path to configuration file used */
  private String configPath;
  /** NUTCH_HOME */
  private String nutchHome;
  /** configuration parameters */
  private ConfigList cfg;
  /** map site name -> host */
  private Map<String, String> sites;
  /** site directories */
  private File[] siteDirs;
  /** interface to a relational database */
  private IndexRootDB db;
  /** data directory: root of all sites directories */
  private String dataDir;
  /** temporary working directory to be used by Nutch */
  private String workDir;
  /** Solr URL to use for submission */
  private String solrURL;
  /** Used index switch */
  private File used2;
  /** false if picking up interrupted build */
  private boolean isNewBuild;
  /** fork Nutch parts in new Java instance */
  private boolean fork;
  /** true if to index sites in parallel */
  private boolean parallelIndexing;
  /** true if working in watch mode */
  private boolean watchMode;
  /** last build number */
  private int buildNumber;
  /** parallel threads to use for crawling */
  private int threads;
  /** crawling depth */
  private int depth;
  /** loglinks crawling depth */
  private int loglinksDepth;
  /** number of URLs to fetch at each iteration */
  private int topN;
  /** Hadoop configuration */
  private Configuration conf;
  /** Hadoop file system */
  private FileSystem fs;
  /** email body */
  private StringBuffer email;
  /** destination of URLs to crawl in parallel */
  private BufferedWriter crawlRoots;
  /** date of start of indexing */
  private Date indexingStart;
  /** Space separated list of names of sites to drop */
  private String sitesToDrop;
  /** URLs of sites to crawl defined by CRAWLING_SEED variable */
  ArrayList<String> crawlingSeed;

  /**
   * @param args
   *          string[] containing run parameters where args[0] is (optional) site name to process
   *
   */
  public static void main( String[] args )
  {

    if ( args.length > 1 )
    {
      System.out.println( "Usage: Indexer [site name]" );
      return;
    }
    Indexer indexer = null;
    try
    {
      indexer = doMain( args );
    } catch ( Exception e )
    {
      System.out.println( "Indexer: " + e.getMessage() );
      e.printStackTrace();
      LOG.error( "Indexer: ", e );
      try
      {
        if ( indexer != null )
        {
          indexer.appendEmail( ERROR, e.getMessage(), Indexer.maxEmailLength );
        }
      } catch ( Exception ee )
      {}
      if ( e.getMessage().indexOf( "ock file" ) < 0 )
        try
        {
          lock( false );
        } catch ( Exception eee )
        {}
    }
    try
    {
      if ( indexer != null && indexer.email.length() > 0 )
      {
        sendEmail( indexer.email.toString(), null, indexer.getCfg() );
      }
    } catch ( Exception e )
    {
      LOG.error( "Could not send email in Indexer: " + e.getMessage() );
    }
  }

  /**
   * @param args
   *          string[] containing run parameters where args[0] is path to configuration file to use and and args[1] is
   *          (optional) site name to process
   *
   */
  static Indexer doMain( String args[] ) throws Exception
  {
    IndexSite oneSite = null;
       
    // Get configuration file from NUTCH_HOME that has to be set anyway
    String nutchHome = System.getenv( "NUTCH_HOME" );
    if ( nutchHome == null )
    {
      throw new Exception( "Stopped: environment variable NUTCH_HOME is not set." );
    }

    String configFile = nutchHome + "/conf/arch/config.txt";
    String siteToProcess = args.length == 1 ? args[ 0 ] : null;
    lock( true );

    Indexer indexer = new Indexer( configFile );
    indexer.init();

    if ( siteToProcess != null )
    {
      oneSite = IndexSite.newIndexSite( siteToProcess, indexer );
    }
    LogProcessor logProcessor = LogProcessor.newLogProcessor( configFile );
    if ( oneSite != null )
    {
      logProcessor.process( "mi", siteToProcess ); // update list of ignored IPs
      logProcessor.process( "lw", siteToProcess ); // update document weights
    } else if ( indexer.siteDirs != null && indexer.siteDirs.length > 0 )
    {
      for ( int i = 0; i < indexer.siteDirs.length; i++ )
      {
        if ( indexer.siteDirs[ i ] == null )
          continue;
        String name = indexer.siteDirs[ i ].getName();
        logProcessor.process( i > 0, "mi", name ); // update list of ignored IPs
        logProcessor.process( true, "lw", name ); // update document weights
      }
    }
    LogSite.closeAll();
    indexer.db.eraseAlerts();
    indexer.crawl( oneSite );
    // now, that we have crawled everything, time to cleanUp
    indexer.cleanUp();
    // Here is the place to touch Nutch war file
    indexer.appendEmail( INFO, "New index created.", Indexer.maxEmailLength );
    return indexer;
  }

  /**
   * Manages crawling when parallel indexing is switched on.
   * 
   * @param oneSite
   *          either null to crawl all sites, or the site to crawl
   * 
   */
  private void crawl( IndexSite oneSite ) throws Exception
  {
    boolean todo = false;
    // For each site, prepare/crawl everything except bookmarks and log links
    if ( oneSite != null )
    {
      todo |= oneSite.prepare( null, true );
    } else
    {
      for ( String siteName : getSites().values() )
      {
        IndexSite site = IndexSite.newIndexSite( siteName, this );
        todo |= site.prepare( null, true );
        site.close();
      }
    }
    if ( todo )
    {
      crawl( null, null, depth );
    }
    todo = false;

    if ( !isWatchMode() ) // in this mode loglinks will have been processed already
    {
      // For each site, prepare missed log links.
      if ( oneSite != null )
      {
        todo |= oneSite.prepare( "loglinks", false );
      } else
      {
        for ( String siteName : getSites().values() )
        {
          IndexSite site = IndexSite.newIndexSite( siteName, this );
          todo |= site.prepare( "loglinks", false );
          site.close();
        }
      }
      // Crawl combined missed log links
      if ( todo )
      {
        crawl( "", "loglinks", loglinksDepth );
      }
      todo = false;
    }

    // For each site, crawl bookmarks and process alerts
    ArrayList<Reporter> reporters = ReporterFactory.getReporters( conf );

    if ( oneSite != null )
    {
      if ( !isWatchMode() && oneSite.prepare( "bookmarks", false ) )
      {
        crawl( oneSite.name, "bookmarks", 1 );
      }
      for ( Reporter reporter : reporters )
      {
        reporter.report( this, oneSite );
      }
    } else
    {
      for ( String siteName : getSites().values() )
      {
        IndexSite site = IndexSite.newIndexSite( siteName, this );
        if ( !isWatchMode() && site.prepare( "bookmarks", false ) )
        {
          crawl( site.name, "bookmarks", 1 );
        }
        for ( Reporter reporter : reporters )
        {
          reporter.report( this, site );
        }
        site.close();
      }
    }
  }

  /**
   * Does actual crawling. Prerequisites: a) Seed URLs must be added to the crawlRoots; b) All areas involved in crawl
   * must be marked in the 'areas.status' field in the DB.
   * 
   * @param siteName
   *          name of site to crawl or null to crawl all sites
   * @param areaName
   *          name of area to crawl or null to crawl all areas
   * @param depth
   *          crawling depth
   * @return -1 if failed, else 0
   * @throws Exception
   */
  int crawl( String siteName, String areaName, int depth ) throws Exception
  {
    String aa = siteName == null ? "" : "site " + siteName;
    if ( aa.length() > 0 )
    {
      if ( areaName != null )
      {
        aa += ", area " + areaName;
      }
    } else if ( areaName != null )
    {
      aa = " area " + areaName;
    } else
    {
      aa = "all sites and areas";
    }
    LOG.info( "Crawling " + aa );
    crawlRoots.close();
    Configuration conf = NutchConfiguration.create();
    String[] args = { workDir + "/urls", "-solr", solrURL, "-dir", workDir, "-threads", "" + threads, "-depth",
        "" + depth, "-topN", "" + topN };
    if ( areaName != null && areaName.equals( "bookmarks" ) )
    {
      args[ 8 ] = "1";
      args[ 10 ] = "-1";
      conf.set( "db.update.additions.allowed", "false" );
    }

    if ( siteName != null )
    {
      conf.set( "arch.site", siteName );
    }
    if ( areaName != null )
    {
      conf.set( "arch.area", areaName );
    }
    conf.set( "arch.database", getCfg().get( "database", "MySQL" ) );
    conf.set( "max.url.length", getCfg().get( "max.url.length", "-1" ) );
    conf.set( "arch.target.db", getCfg().get( "target.db", "" ) );
    conf.set( "arch.db.driver", getCfg().get( "db.driver", "" ) );
    conf.set( "hadoop.tmp.dir", workDir + "/tmp" );
    conf.set( "solr.server.url", solrURL );
    if ( getCfg().get( "remove.duplicates", true ) )
       conf.set(  "remove.duplicates", "true" ) ;

    int res = ToolRunner.run( conf, new ArchCrawl(), args );
    if ( res < 0 )
    {
      throw new Exception( " Crawl failed for " + aa );
    }
    // clean and prepare for next crawl
    Utils.rmdir( workDir, null );
    Utils.mkdir( workDir );
    Utils.mkdir( workDir + "/urls" );
    FileWriter fstream = new FileWriter( new File( workDir + "/urls" ).getCanonicalPath() + "/urls.txt" );
    crawlRoots = new BufferedWriter( fstream );
    // to get around an apparent Nutch bug
    crawlRoots.write( "http://www.atnf.csiro.au/computing/software/arch/nutchBug.gif\n" );
    db.markIndexed( getBuildNumber() );
    LOG.info( "Finished crawling " + aa );
    return res;
  }

  static int I1 = 1;
  static int I2 = 2;

  /**
   * Cleans the working and temp directories
   * 
   */
  private void cleanUp() throws Exception
  {
    Utils.rmdir( workDir, null );
    Utils.mkdir( dataDir + "/done" );
    String sql = "update areas set status=0, build=" + getBuildNumber() + ",lastIndexed='"
        + DateFormat.getDateTimeInstance().format( new Date() ) + "' where status!=0";
    db.execute( sql );
    // Update selects text
    String areaSelect = db.getAreaSelect( null );
    String siteSelect = db.getSiteSelect( null );
    String groupSelect = db.getGroupSelect( null, null );
    if ( areaSelect != null )
    {
      saveSelect( nutchHome + "/conf/arch/areaSelect.txt", areaSelect );
    }
    if ( siteSelect != null )
    {
      saveSelect( nutchHome + "/conf/arch/siteSelect.txt", siteSelect );
    }
    if ( groupSelect != null )
    {
      saveSelect( nutchHome + "/conf/arch/groupSelect.txt", groupSelect );
    }

    // Update list of existing sites
    String list = "";
    for ( String host : getSites().keySet() )
    {
      if ( list.length() > 0 )
      {
        list += " | ";
      }
      list += getSites().get( host ) + " " + host;
    }
    db.write( "update system set value='" + list + "' where name = 'sitelist'",
        "insert into system values ('sitelist','" + list + "')" );
    // Drop sites that are not in configuration anymore
    String[] toDrop = sitesToDrop != null && sitesToDrop.trim().length() > 0 ? sitesToDrop.split( " " ) : null;
    if ( toDrop != null )
    {
      for ( String siteName : toDrop )
      {
        if ( siteName.length() > 0 )
        {
          dropSite( siteName );
        }
      }
    }
    lock( false );
  }

  /**
   * Saves dynamic select statement body for inclusion in HTML search form
   * 
   * @param fileName
   *          name of file to save to
   * @param select
   *          select body
   */
  private void saveSelect( String fileName, String select )
  {
    try
    {
      PrintWriter out = new PrintWriter( fileName );
      out.print( select );
      out.close();
    } catch ( Exception e )
    {
      LOG.warn( "Could not update select text file " + fileName + ", is it read/only?" );
    }
  }

  /**
   * Default parameterless constructor
   *
   */
  public Indexer()
  {}

  /**
   * A constructor. Stores path to configuration in a member variable.
   * 
   * @param config
   *          string path to configuration file.
   */
  public Indexer( String config )
  {
    configPath = config;
    nutchHome = System.getenv( "NUTCH_HOME" );

  }

  /**
   * Performs initializing based on the configuration parameters.
   * 
   * @throws Exception
   */
  public void init() throws Exception
  {
     try {
            System.loadLibrary( "hadoop" ) ;
            LOG.info( "Loaded the native-hadoop library" ) ;
         } catch ( Throwable t ) // Ignore failure to load 
         {
            LOG.info( "Failed to load native-hadoop with error: " + t ) ;
            LOG.info( "java.library.path=" + System.getProperty( "java.library.path" ) ) ;
         }

    setIndexingStart( new Date() );
    email = new StringBuffer();
    setCfg( ConfigList.newConfigList( configPath ) );
    conf = NutchConfiguration.create();
    // Override solr.server.url while not too late
    solrURL = getCfg().getInherited( "solr.url", "http://localhost:8993/arch", "SOLR_URL" );
    conf.set( "solr.server.url", solrURL );
    DBInterfaceFactory factory = DBInterfaceFactory.get( conf );
    String database = getCfg().get( "database", "MySQL" );
    setFork( getCfg().get( "fork.nutch", true ) );
    topN = getCfg().getInherited( "max.urls", 100000, "CRAWLING_MAX_URLS" );
    depth = getCfg().getInherited( "depth", 30, "CRAWLING_DEPTH" );
    loglinksDepth = getCfg().get( "depth.loglinks", 1 );
    threads = getCfg().getInherited( "threads", 10, "CRAWLING_THREADS" );
    setParallelIndexing( getCfg().get( "parallel.indexing", false ) );
    String levelLimit = getCfg().get( "mail.level", "INFO" );
    for ( emailLevel = 0; emailLevel < codes.length; emailLevel++ )
    {
      if ( codes[ emailLevel ].equalsIgnoreCase( levelLimit ) )
      {
        break;
      }
    }
    if ( emailLevel == codes.length )
    {
      throw new Exception( "Wrong mail.level parameter." );
    }

    maxEmailLength = getCfg().get( "max.email.length", 100000 );
    DBInterface intrf = factory.get( database );
    db = intrf.newIndexRootDB( getCfg(), false );
    // create a list of sites
    dataDir = nutchHome + "/conf/arch";
    String sitesDir = dataDir + "/sites";
    workDir = getCfg().get( "temp.dir", nutchHome + "/temp" );
    new File( workDir ).mkdirs();
    File dir = new File( sitesDir );
    SiteFilter filter = new SiteFilter();
    siteDirs = dir.listFiles( filter );
    crawlingSeed = getCrawlingSeed( null, null );
    setSites( getSites( siteDirs ) );
    if ( getSites() == null || getSites().size() == 0 )
    {
      throw new Exception( "No sites to crawl found." );
    }
    resetEverything(); // Reset everything if the trigger is set
    setBuildNumber( db.readInt( "select value from system where name='buildNumber'" ) );
    if ( getBuildNumber() == -1 )
    {
      setBuildNumber( 1 );
    }
    if ( Utils.exists( dataDir + "/done" ) ) // previous work has been finished successfully
    {
      setBuildNumber( getBuildNumber() + 1 );
      setNewBuild( true );
      db.execute( "delete from system where name='buildNumber'" );
      db.write( "insert into system values( 'buildNumber', '" + getBuildNumber() + "' )", null );
    } else
    {
      setNewBuild( false ); // picking up an unfinished build
    }

    Utils.rmdir( workDir, null );
    Utils.mkdir( workDir );
    String urlsDir = workDir + "/urls";
    Utils.mkdir( urlsDir );
    conf.set( "hadoop.tmp.dir", workDir + "/tmp" );
    FileWriter fstream = new FileWriter( new File( urlsDir ).getCanonicalPath() + "/urls.txt" );
    crawlRoots = new BufferedWriter( fstream );
    // to get around an apparent Nutch bug
    crawlRoots.write( "http://www.atnf.csiro.au/computing/software/arch/nutchBug.gif\n" );

    // Are we working in watch mode
    setWatchMode( getCfg().get( "watch.mode", false ) );
    if ( isWatchMode() )
    {
      depth = 1;
      loglinksDepth = 1;
      setParallelIndexing( true );
    }

  }

  /**
   * Send email if configured
   * 
   * @param content
   *          - message body
   * @param cfg
   *          - configuration
   * 
   * @throws Exception
   */
  static public void sendEmail( String content, String subject, ConfigList cfg ) throws Exception
  {
    if ( cfg.getInherited( "mail.level", "info" ).equalsIgnoreCase( "off" ) )
    {
      return;
    }
    Properties props = new Properties();
    props.setProperty( "mail.transport.protocol", cfg.getInherited( "mail.transport.protocol", "smtp" ) );
    if ( cfg.getInherited( "mail.host", "" ).length() == 0 )
    {
      throw new Exception( "Can't send email: mail.host is not defined." );
    }
    props.setProperty( "mail.host", cfg.getInherited( "mail.host", "" ) );
    props.setProperty( "mail.user", cfg.getInherited( "mail.user", "" ) );
    props.setProperty( "mail.password", cfg.getInherited( "mail.password", "" ) );
    if ( subject == null )
    {
      subject = cfg.getInherited( "mail.subject", "" );
    }
    if ( subject.length() == 0 )
    {
      subject = "Arch indexing report";
    }
    String addresses = cfg.getInherited( "mail.recipient", "" );
    if ( addresses.length() == 0 )
    {
      throw new Exception( "Can't send email: mail.recipient is not defined." );
    }
    String[] to = addresses.split( "[;,:]" );

    Session mailSession = Session.getDefaultInstance( props, null );
    Transport transport = mailSession.getTransport();

    MimeMessage message = new MimeMessage( mailSession );
    message.setSubject( subject );
    message.setContent( content, "text/plain" );
    for ( int i = 0; i < to.length; i++ )
    {
      String address = to[ i ].trim();
      if ( address.length() > 0 )
      {
        message.addRecipient( Message.RecipientType.TO, new InternetAddress( address ) );
      }
    }
    if ( message.getAllRecipients() == null || message.getAllRecipients().length == 0 )
    {
      throw new Exception( "Can't send email: mail recipient list is invalid." );
    }

    transport.connect();
    transport.sendMessage( message, message.getRecipients( Message.RecipientType.TO ) );
    transport.close();
  }

  /**
   * Add a string to email message that will be sent when sendEmail is called
   * 
   * @param level
   *          reporting level: 0 - DEBUG, 1 - INFO, 2 - WARN, 3 - ERROR, 4 - OFF
   * @param body
   *          email body to append to
   * @param text
   *          text to append
   * 
   * @throws Exception
   */
  static public void appendEmail( int level, StringBuffer body, String text ) throws Exception
  {
    if ( level < emailLevel )
    {
      return;
    }
    appendEmail( level, body, text, maxEmailLength );
  }

  /**
   * Add a string to email message that will be sent when sendEmail is called
   * 
   * @param level
   *          reporting level: 0 - DEBUG, 1 - INFO, 2 - WARN, 3 - ERROR, 4 - OFF
   * @param body
   *          email body to append to
   * @param text
   *          text to append
   * @param maxLength
   *          maximal email body length
   * 
   * @throws Exception
   */
  static public void appendEmail( int level, StringBuffer body, String text, int maxLength ) throws Exception
  {
    if ( body.length() + text.length() >= maxLength )
    {
      if ( body.lastIndexOf( "\r\nTruncated...\r\n" ) + 20 < body.length() )
      {
        body.append( "\r\nTruncated...\r\n" );
      }
      return;
    }
    if ( level > codes.length - 1 )
    {
      level = codes.length - 2;
    }
    String date = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss " ).format( new Date( System.currentTimeMillis() ) );
    body.append( date );
    body.append( codes[ level ] );
    body.append( " " );
    body.append( text );
    body.append( "\r\n" );
  }

  /**
   * Add a string to email message that will be sent when sendEmail is called
   * 
   * @param level
   *          reporting level: 0 - DEBUG, 1 - INFO, 2 - WARN, 3 - ERROR, 4 - OFF
   * @param text
   *          text to append
   * @param maxLength
   *          limit on email length
   * 
   * @throws Exception
   */
  void appendEmail( int level, String text, int maxLength ) throws Exception
  {
    appendEmail( level, email, text, maxLength );
  }

  /**
   * Check, create or remove a lock file that indicates that Arch is in progress
   * 
   * @param lockIt
   *          if true, try to create the lock. Error if it exists. If false, remove the lock.
   * 
   * @throws Exception
   */
  public static void lock( boolean lockIt ) throws Exception
  {
    String nutchHome = System.getenv( "NUTCH_HOME" );
    String lockName = nutchHome + "/conf/arch/lockFile";
    File lockFile = new File( lockName );
    if ( lockIt )
    {
      if ( lockFile.exists() )
      {
        throw new Exception( "Lock file " + lockName + " exists: is another copy of Arch running?" );
      }
      try
      {
        lockFile.createNewFile();
      } catch ( IOException ioe )
      {
        throw new Exception( "Can't create lock file " + lockName + ", is the directory wriateable?" );
      }
    } else
    {
      if ( lockFile.exists() && !lockFile.delete() )
      {
        throw new Exception( "Can't delete lock file " + lockName + ". Delete it befeore starting Arch again." );
      }
    }
  }

  /**
   * Checks for the reset trigger and resets the data if it exists. The trigger is file "reset" in the configuration
   * directory.
   * 
   * @throws Exception
   */
  public void resetEverything() throws Exception
  {
    File trigger = new File( dataDir + "/reset" );
    if ( trigger.exists() )
    { // Generally, an overkill, but sites allowed to have different databases
      for ( String fsite : getSites().values() )
      {
        IndexSite site = IndexSite.newIndexSite( fsite, this );
        dropTable( site.db, "site_" + site.name );
        site.close();
      }
      dropTable( db, "areas" );
      dropTable( db, "logs" );
      dropTable( db, "roots" );
      dropTable( db, "sites" );
      dropTable( db, "system" );
      db.close();
      DBInterfaceFactory factory = DBInterfaceFactory.get( conf );
      String database = getCfg().get( "database", "MySQL" );
      DBInterface intrf = factory.get( database );
      db = intrf.newIndexRootDB( getCfg(), false );
      trigger.delete();
      Thread.sleep( 10 * 1000 );
      HttpSolrClient server = new HttpSolrClient.Builder( solrURL ).build() ;
      server.deleteByQuery( "*:*" ) ;
      server.commit() ;
      server.close() ;
    }
  }

  public void dropTable( IndexDB db, String table )
  {
    try
    {
      db.execute( "drop table " + table );
    } catch ( Exception e )
    {
      LOG.warn( "Dropping table " + table + " caused exception: " + e.getMessage() );
    }
  }

  public void dropSite( String name ) throws Exception
  {
    IndexSite site = IndexSite.newIndexSite( name, this );
    dropTable( site.db, "site_" + site.name );
    site.close();
    HttpSolrClient server = new HttpSolrClient.Builder( solrURL ).build() ;
    server.deleteByQuery( "*:*" );
    server.commit();
    server.close() ;
  }

  static int SEC_TO_SLEEP = 10 ;
  static int MILLS = 1000 ;
  /**
   * @param existing
   *          a set of existing configured sites
   * @return a map host->site name of proper site names, including interim and configured
   */
  Map<String, String> getSites( File[] existing ) throws Exception
  {
    // make domains of configured interim sites
    // domain->name
    Map<String, String> interim = extractDomains( crawlingSeed );

    // read domains of pre-existing sites
    String existingSites = db.readString( "select value from system where name = 'sitelist'" );
    // name -> domain
    Map<String, String> oldSites = parseNames( existingSites );
    Map<String, String> oldSitesR = invert( oldSites );

    // read domains of existing configured sites
    // name -> domain
    Map<String, String> configuredSites = readSites( existing );
    Map<String, String> configuredSitesR = invert( configuredSites );
    if ( configuredSites.keySet().size() != configuredSitesR.keySet().size() )
    {
      throw new Exception( "No 1:1 correspondence between site names and hosts. Do different sites have same hosts?" );
    }

    // Make sure that hosts do not overlap
    for ( String host : configuredSites.values() )
    {
      if ( interim.containsKey( host ) )
      {
        throw new Exception( "Host " + host + " appears in interim crawling seeds (see bin/arch) and "
            + "in a configured site conf/arch/sites/" + configuredSitesR.get( host )
            + ".\n The crawling has been aborted. Please fix the conflict and try again." );
      }
    }

    // Detect sites to drop
    String sitesToDrop = "";
    for ( String host : oldSites.values() )
    {
      if ( !interim.containsKey( host ) && !configuredSitesR.containsKey( host ) )
      {
        System.out.println( "Site " + oldSitesR.get( host ) + " does not appear in configuration.\n"
            + "It will be droppedd after successfull crawling. If you don't want to "
            + "drop it, terminate indexing immediately - press ^C." );
        Indexer.LOG.warn( "Site " + oldSitesR.get( host ) + " does not appear in configuration.\n"
            + "It will be droppedd after successfull crawling. If you don't want to "
            + "drop it, terminate indexing immediately - press ^C." );
        if ( sitesToDrop.length() > 0 )
        {
          sitesToDrop += " ";
        }
        sitesToDrop += oldSitesR.get( host );
        int sleepTime = SEC_TO_SLEEP ;
        Thread.sleep( MILLS * sleepTime );
      } else if ( interim.containsKey( host ) )
      {
        interim.put( host, oldSitesR.get( host ) );
      }
    }
    setSitesToDrop( sitesToDrop );

    // Report newly found interim and configured hosts
    for ( String host : configuredSites.values() )
    {  
      if ( !oldSitesR.containsKey( host ) )
      {
        Indexer.LOG.info( "New site to crawl found: " + configuredSitesR.get( host ) + " (" + host + ")." );
      }
    }
    
    for ( String host : interim.keySet() )
    {
      if ( !oldSitesR.containsKey( host ) )
      {
        Indexer.LOG.info( "New interim host to crawl found: " + host + "." );
      }
    }

    interim = makeNames( interim, oldSites, configuredSites );

    // Now add configured to interim
    for ( String name : configuredSites.keySet() )
    {
      interim.put( configuredSites.get( name ), name );
    }

    return interim;
  }

  /**
   * Make names for interim sites: Cut off "www." Try leaving only the word following "www". If failed, add another
   * word. If there are still collisions, try adding numbers to conflicting names.
   * 
   * @param interim
   *          a map host -> name of interim (defined by crawling seeds) sites to crawl
   * @param old
   *          a map name -> host of existing sites (crawled previously)
   * @param configured
   *          a map name -> host of sites configured in site config folders
   * @return a merged (interim + configured) map host -> site of sites to crawl
   */
  static Map<String, String> makeNames( Map<String, String> interim, Map<String, String> old,
      Map<String, String> configured )
  {
    // Prepare a set of existing names to avoid conflicts
    Map<String, String> existing = new HashMap<String, String>();
    // Set of hosts to generate names for
    Set<String> hosts = new HashSet<String>();

    for ( String host : interim.keySet() )
    {
      String name = interim.get( host );
      if ( name.length() > 0 )
      {
        existing.put( name, host );
      }
      else
      {
        hosts.add( host.toLowerCase() );
      }
    }

    if ( hosts.size() == 0 )
    {
      return interim; // all have names already
    }
    // cut off "www." - it is usually very common
    for ( String name : hosts )
    {
      String name0 = name ;
      if ( name.startsWith( "www." ) )
      {
        name0 = name.substring( 4 );
      }
      name0 = name0.replace( '-', '_' );
      int i1 = name0.indexOf( '.' );
      int i2 = i1 >= 0 ? name0.indexOf( '.', i1 + 1 ) : -1;
      String name1 = i1 > 0 ? name0.substring( 0, i1 ) : name0;
      if ( Character.isDigit( name1.charAt( 0 ) ) )
      {
        name1 = "N" + name1;
      }
      if ( !conflict( name1, existing, old, configured ) )
      {
        existing.put( name1, name );
        continue;
      }
      String name2 = i2 > 0 ? name0.substring( 0, i2 ).replace( ".", "_" ) : name0.replace( ".", "_" );
      if ( Character.isDigit( name2.charAt( 0 ) ) )
      {
        name2 = "N" + name1;
      }
      if ( !conflict( name2, existing, old, configured ) )
      {
        existing.put( name2, name );
        continue;
      }
      String name3 = name1 + 1;
      int i = 2;
      while ( conflict( name3, existing, old, configured ) )
      {
        name3 = name1 + i++;
      }
      existing.put( name3, name );
    }

    for ( String name : existing.keySet() )
    {
      interim.put( existing.get( name ), name );
    }

    return interim;
  }

  static boolean conflict( String name, Map<String, String> existing, Map<String, String> old,
      Map<String, String> configured )
  {
    if ( existing.containsKey( name ) || old.containsKey( name ) || configured.containsKey( name ) )
    {
      return true;
    }
    return false;
  }

  static Map<String, String> parseNames( String list )
  {
    HashMap<String, String> map = new HashMap<String, String>();
    if ( list == null )
    {
      return map;
    }
    String[] pairs = list.split( "\\|" );
    for ( String pair : pairs )
    {
      String[] parts = pair.trim().split( " " );
      map.put( parts[ 0 ], parts[ 1 ] );
    }
    return map;
  }

  static Map<String, String> extractDomains( Collection<String> list ) throws Exception
  {
    HashMap<String, String> map = new HashMap<String, String>();
    for ( String str : list )
    {
      URL url = new URL( str );
      String host = url.getHost();
      map.put( host, "" );
    }
    return map;
  }

  static ArrayList<String> getCrawlingSeed( ArrayList<String> urls, String[] lines ) throws Exception
  {
    if ( urls == null )
    {
      urls = new ArrayList<String>();
    }
    if ( lines == null )
    {
      String param = System.getenv( "CRAWLING_SEED" );
      if ( param == null )
      {
        return urls;
      }
      lines = param.split( "\\|" );
    }

    for ( String line : lines )
    {
      line = line.trim();
      try
      {
        new URL( line ); // is this a valid URL?
        urls.add( line ); // if so, add it to the list
      } catch ( Exception e ) // no, it is not a valid URL, may be a file name?
      {
        String content = null;
        try
        {
          content = new Scanner( new File( line ) ).useDelimiter( "\\Z" ).next();
        } catch ( Exception ee )
        {
          LOG.warn( "IGNORED seed line " + line + " does not appear a valid URL or readable file name." );
          continue;
        }
        content = content.replace( "\r", "" );
        String[] contentLines = content.trim().split( "\n" );
        ArrayList<String> urls2 = getCrawlingSeed( null, contentLines );
        urls.addAll( urls2 );
      }
    }
    return urls;
  }

  static Map<String, String> readSites( File[] sites ) throws Exception
  {
    HashMap<String, String> map = new HashMap<String, String>();
    if ( sites != null && sites.length != 0 )
    {
      for ( File site : sites )
      {
        if ( site == null )
          continue;
        String config = site.getCanonicalPath() + "/config.txt";
        ConfigList cfg = ConfigList.newConfigList( config );
        String siteUrl = cfg.get( "url", null );
        URL url = new URL( siteUrl );
        String host = url.getHost();
        map.put( site.getName(), host );
      }
    }
    return map;
  }

  static <V, K> Map<V, K> invert( Map<K, V> map )
  {
    Map<V, K> inv = new HashMap<V, K>();
    for ( Entry<K, V> entry : map.entrySet() )
    {
      inv.put( entry.getValue(), entry.getKey() );
    }
    return inv;
  }

  // Getters and setters

  public BufferedWriter getCrawlRoots()
  {
    return crawlRoots;
  }

  /**
   * A helper class, for directory filtering
   */
  public static class SiteFilter implements java.io.FileFilter
  {
    boolean includeTemplate = false;

    public SiteFilter( boolean includeTemplate )
    {
      this.includeTemplate = includeTemplate;
    }

    public SiteFilter()
    {
      this( false );
    }

    public boolean accept( File f )
    {
      if ( f.isDirectory() && ( !f.getName().equals( "template" ) || includeTemplate ) )
      {
        return true;
      }
      else
      {
        return false;
      }
    }
  }

  public String getNutchConfigPath()
  {
    return nutchHome + "/conf";
  }

  public String getDataDir()
  {
    return dataDir;
  }

  /**
   * @return the db
   */
  public IndexRootDB getDb()
  {
    return db;
  }

  /**
   * @param db
   *          the db to set
   */
  public void setDb( IndexRootDB db )
  {
    this.db = db;
  }

  String getSitesToDrop()
  {
    return sitesToDrop;
  }

  void setSitesToDrop( String sitesToDrop )
  {
    this.sitesToDrop = sitesToDrop;
  }

  public File getUsed2()
  {
    return used2;
  }

  public void setUsed2( File used2 )
  {
    this.used2 = used2;
  }

  public boolean isNewBuild()
  {
    return isNewBuild;
  }

  public void setNewBuild( boolean isNewBuild )
  {
    this.isNewBuild = isNewBuild;
  }

  public boolean isFork()
  {
    return fork;
  }

  public void setFork( boolean fork )
  {
    this.fork = fork;
  }

  public boolean isParallelIndexing()
  {
    return parallelIndexing;
  }

  public void setParallelIndexing( boolean parallelIndexing )
  {
    this.parallelIndexing = parallelIndexing;
  }

  public FileSystem getFs()
  {
    return fs;
  }

  public void setFs( FileSystem fs )
  {
    this.fs = fs;
  }

  public Date getIndexingStart()
  {
    return indexingStart;
  }

  public void setIndexingStart( Date indexingStart )
  {
    this.indexingStart = indexingStart;
  }

  public ConfigList getCfg()
  {
    return cfg;
  }

  public void setCfg( ConfigList cfg )
  {
    this.cfg = cfg;
  }

  public boolean isWatchMode()
  {
    return watchMode;
  }

  public void setWatchMode( boolean watchMode )
  {
    this.watchMode = watchMode;
  }

  public Map<String, String> getSites()
  {
    return sites;
  }

  public void setSites( Map<String, String> sites )
  {
    this.sites = sites;
  }

  public int getBuildNumber()
  {
    return buildNumber;
  }

  public void setBuildNumber( int buildNumber )
  {
    this.buildNumber = buildNumber;
  }

}
