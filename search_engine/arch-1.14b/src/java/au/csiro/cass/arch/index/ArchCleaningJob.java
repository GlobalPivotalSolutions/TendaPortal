package au.csiro.cass.arch.index;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.lib.NullOutputFormat;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.CrawlDb;
import org.apache.nutch.indexer.CleaningJob;
import org.apache.nutch.indexer.IndexWriters;
import org.apache.nutch.indexer.IndexerMapReduce;
import org.apache.nutch.indexer.CleaningJob.DBFilter;
import org.apache.nutch.indexer.CleaningJob.DeleterReducer;
import org.apache.nutch.net.URLFilterException;
import org.apache.nutch.net.URLFilters;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.NutchJob;
import org.apache.nutch.util.TimingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.sql.PluginConnectionCache;
import au.csiro.cass.arch.utils.URLSplit;
import au.csiro.cass.arch.utils.Utils;

/**
 * 
 * Adds deletion of gone nodes from the DB to actions performed by Nutch CleaningJob
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class ArchCleaningJob extends CleaningJob implements Tool
{
  public static final Logger LOG = LoggerFactory.getLogger( ArchCleaningJob.class );

  @Override
  public int run( String[] args ) throws IOException
  {
    if ( args.length < 1 )
    {
      String usage = "Usage: CleaningJob <crawldb> [-noCommit]";
      LOG.error( "Missing crawldb. " + usage );
      System.err.println( usage );
      IndexWriters writers = new IndexWriters( getConf() );
      System.err.println( writers.describe() );
      return 1;
    }

    boolean noCommit = false;
    if ( args.length == 2 && args[ 1 ].equals( "-noCommit" ) )
    {
      noCommit = true;
    }

    try
    {
      delete( args[ 0 ], noCommit );
    } catch ( final Exception e )
    {
      LOG.error( "CleaningJob: " + StringUtils.stringifyException( e ) );
      System.err.println( "ERROR CleaningJob: " + StringUtils.stringifyException( e ) );
      return -1;
    }
    return 0;
  }

  @Override
  public void delete( String crawldb, boolean noCommit ) throws IOException
  {
    SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
    long start = System.currentTimeMillis();
    LOG.info( "CleaningJob: starting at " + sdf.format( start ) );

    JobConf job = new NutchJob( getConf() );

    FileInputFormat.addInputPath( job, new Path( crawldb, CrawlDb.CURRENT_NAME ) );
    job.setBoolean( "noCommit", noCommit );
    job.setInputFormat( SequenceFileInputFormat.class );
    job.setOutputFormat( NullOutputFormat.class );
    job.setMapOutputKeyClass( ByteWritable.class );
    job.setMapOutputValueClass( Text.class );
    job.setMapperClass( DBFilter.class );
    job.setReducerClass( DeleterReducer.class );

    job.setJobName( "CleaningJob" );

    // need to expicitely allow deletions
    job.setBoolean( IndexerMapReduce.INDEXER_DELETE, true );

    JobClient.runJob( job );

    long end = System.currentTimeMillis();
    LOG.info( "CleaningJob: finished at " + sdf.format( end ) + ", elapsed: " + TimingUtil.elapsedTime( start, end ) );
  }

  public static class DBFilter implements Mapper<Text, CrawlDatum, ByteWritable, Text>, DBConnected
  {
    private ByteWritable OUT = new ByteWritable( CrawlDatum.STATUS_DB_GONE );

    private Configuration conf;
    private static boolean connected = false;
    private static IndexSiteDB db;
    private static IndexRootDB rootDb;
    private static ConnectionCache connections;
    SiteMatcher matcher;
    SiteAreas known;
    boolean loglinks, bookmarks;
    private static URLFilters filters; // URL filters to use

    @Override
    public void configure( JobConf arg0 )
    {
      conf = arg0;
    }

    @Override
    public void close() throws IOException
    {}

    @Override
    public void map( Text key, CrawlDatum value, OutputCollector<ByteWritable, Text> output, Reporter reporter )
        throws IOException
    {

      if ( value.getStatus() == CrawlDatum.STATUS_DB_GONE || value.getStatus() == CrawlDatum.STATUS_DB_DUPLICATE )
      {
        String url = null, siteName = null;
        try
        {
          connect();
          url = filters.filter( key.toString() );
          LOG.info( "Deleting " + url + " status: " + value.getStatus()  );

          try
          {
            siteName = matcher != null ? matcher.getSite( url ) : known.getSite();
          } catch ( Throwable th )
          {
            LOG.warn( "Can't match " + url + ", ignored. Matcher:\n" + matcher + "\nknown:\n" + known  );
            return ;
          }

          if ( value.getStatus() == CrawlDatum.STATUS_DB_GONE ) // delete from the database
          {
            URLSplit split = URLSplit.newURLSplit( url, null, "f" );
            if ( db != null )
            {
              db.deleteCascade( 0, split.getPath(), split.getName(), "f" );
            } else
            {
              IndexSiteDB con = connections.getSiteConnection( siteName );
              con.deleteCascade( 0, split.getPath(), split.getName(), "f" );
            }
          }
        } catch ( URLFilterException e )
        {
          LOG.warn( "Unexpected filter exception while filterinng " + url + ": " + e.getMessage(), e );
          return ;
        } catch ( Exception e )
        {
          LOG.warn( "Exception when matching URL " + url, e );
          return ;
        }
        output.collect( OUT, new Text( siteName + ":" + url ) );
      }
    }

    public synchronized void connect() throws Exception
    {
      if ( connected )
      {
        if ( known == null && matcher == null )
        {
          LOG.warn( "Connected, but both matcher and known are null, reconnecting." );
          disconnect() ;
        }
        else return;
      }
      LOG.info( "Connecting"  );
      if ( filters == null )
      {
        filters = new URLFilters( conf );
      }
      connections = PluginConnectionCache.newPluginConnectionCache( conf, false );
      rootDb = connections.getRootConnection();
      String site = conf.get( "arch.site" );
      String area = conf.get( "arch.area" );
      known = new SiteAreas( null );
      loglinks = ( area != null && area.equalsIgnoreCase( "loglinks" ) );
      bookmarks = ( area != null && area.equalsIgnoreCase( "bookmarks" ) );
      if ( site != null && site.length() > 0 )
      {
        known.setSite( site );
      }
      if ( area != null && area.length() > 0 && !loglinks )
      {
        known.addArea( area );
      }
      // If crawling log links or doing parallel indexing, need a matcher
      if ( loglinks || site == null || site.length() == 0 )
      {
        Map<String, ArrayList<String>[]> roots = rootDb.readRoots();
        matcher = SiteMatcher.newSiteMatcher( roots );
        LOG.debug( "Created matcher: " + matcher  );
      }
      else
      {
        LOG.debug( "Did not create matcher: loglinks =" + loglinks + ", site=" + site  );
      }
      if ( site != null && site.length() > 0 )
      {
        db = connections.getSiteConnection( site );
      }
      Utils.dbFilter = this;
      connected = true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see au.csiro.cass.arch.sql.DBConnected#disconnect()
     */
    @Override
    public void disconnect() throws Exception
    {
      LOG.info( "Disconnecting" );
      if ( connected )
      {
        connections.destroy();
      }
      connections = null;
      db = null;
      rootDb = null;
      connected = false;
    }

  }
}
