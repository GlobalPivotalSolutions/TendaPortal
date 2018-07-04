package au.csiro.cass.arch.index;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.nutch.crawl.CrawlDb;
import org.apache.nutch.crawl.Generator;
import org.apache.nutch.crawl.Injector;
import org.apache.nutch.crawl.LinkDb;
import org.apache.nutch.fetcher.Fetcher;
import org.apache.nutch.indexer.CleaningJob;
import org.apache.nutch.indexer.IndexingJob;
import org.apache.nutch.parse.ParseSegment;
import org.apache.nutch.util.HadoopFSUtil;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.NutchJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.utils.Utils;

/**
 * Implements crawling process
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class ArchCrawl extends Configured implements Tool
{
  public static final Logger LOG = LoggerFactory.getLogger( ArchCrawl.class );

  public static final int DEFAULT_THREADS = 10;
  public static final int DEFAULT_DEPTH = 5;

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.hadoop.util.Tool#run(java.lang.String[])
   */
  @Override
  public int run( String[] args ) throws Exception
  {
    if ( args.length < 1 )
    {
      System.out.println( "Usage: Crawl <urlDir> -solr <solrURL> [-dir d] [-threads n] [-depth i] [-topN N]" );
      return -1;
    }
    Path rootUrlDir = null;
    Path dir = new Path( "crawl-" + getDate() );
    int threads = getConf().getInt( "fetcher.threads.fetch", DEFAULT_THREADS );
    int depth = DEFAULT_DEPTH;
    long topN = Long.MAX_VALUE;
    String solrUrl = null;

    for ( int i = 0; i < args.length; i++ )
    {
      if ( "-dir".equals( args[ i ] ) )
      {
        dir = new Path( args[ i + 1 ] );
        i++;
      } else if ( "-threads".equals( args[ i ] ) )
      {
        threads = Integer.parseInt( args[ i + 1 ] );
        i++;
      } else if ( "-depth".equals( args[ i ] ) )
      {
        depth = Integer.parseInt( args[ i + 1 ] );
        i++;
      } else if ( "-topN".equals( args[ i ] ) )
      {
        topN = Integer.parseInt( args[ i + 1 ] );
        i++;
      } else if ( "-solr".equals( args[ i ] ) )
      {
        solrUrl = StringUtils.lowerCase( args[ i + 1 ] );
        i++;
      } else if ( args[ i ] != null )
      {
        rootUrlDir = new Path( args[ i ] );
      }
    }

    JobConf job = new NutchJob( getConf() );

    if ( solrUrl == null )
    {
      LOG.warn( "solrUrl is not set, indexing will be skipped..." );
    }

    FileSystem fs = FileSystem.get( job );

    if ( LOG.isInfoEnabled() )
    {
      LOG.info( "crawl started in: " + dir );
      LOG.info( "rootUrlDir = " + rootUrlDir );
      LOG.info( "threads = " + threads );
      LOG.info( "depth = " + depth );
      LOG.info( "solrUrl=" + solrUrl );
      if ( topN != Long.MAX_VALUE )
      {
        LOG.info( "topN = " + topN );
      }
    }

    Path crawlDb = new Path( dir + "/crawldb" );
    Path linkDb = new Path( dir + "/linkdb" );
    Path segments = new Path( dir + "/segments" );

    Configuration c0 = getConf();
    c0.set( "solr.server.url", solrUrl );
    Injector injector = new Injector( getConf() );
    Fetcher fetcher = new Fetcher( getConf() );
    ParseSegment parseSegment = new ParseSegment( getConf() );
    CrawlDb crawlDbTool = new CrawlDb( getConf() );
    LinkDb linkDbTool = new LinkDb( getConf() );

    Configuration conf = NutchConfiguration.create();
    conf.set( "arch.action", "Generate" );
    copy( conf, c0, "db.update.additions.allowed" );
    copy( conf, c0, "arch.site" );
    copy( conf, c0, "arch.area" );
    copy( conf, c0, "arch.database" );
    copy( conf, c0, "arch.target.db" );
    copy( conf, c0, "arch.db.driver" );
    copy( conf, c0, "hadoop.tmp.dir" );
    copy( conf, c0, "solr.server.url" );
    copy( conf, c0, "remove.duplicates" );
    copy( conf, c0, "max.url.length" );
    String site = conf.get( "arch.site" );
    String area = conf.get( "arch.area" );
    if ( site == null )
    {
      site = "all sites";
    } else
    {
      site = "site " + site;
    }
    if ( area == null )
    {
      area = "all areas";
    } else
    {
      area = "area " + area;
    }
    Generator generator = new Generator( conf );
    Utils.disconnectFilters(); // just in case
    // initialize crawlDb
    injector.inject( crawlDb, rootUrlDir );
    Utils.disconnectFilters(); // just in case
    int i;
    for ( i = 0; i < depth; i++ )
    { // generate new segment

      if ( LOG.isInfoEnabled() )
      {
        LOG.info( "Crawling " + site + ", " + area + " iteration " + i );
      }
      Utils.disconnectFilters();
      Path[] segs = generator.generate( crawlDb, segments, -1, topN, System.currentTimeMillis() );
      Utils.disconnectFilters();
      Utils.gc();
      if ( segs == null )
      {
        LOG.info( "Crawling " + site + ", " + area + ", stopping at depth=" + i + " - no more URLs to fetch." );
        break;
      }
      fetcher.fetch( segs[ 0 ], threads ); // fetch it
      Utils.disconnectFilters();
      Utils.gc();
      if ( !Fetcher.isParsing( job ) )
      {
        parseSegment.parse( segs[ 0 ] ); // parse it, if needed
      }
      Utils.disconnectFilters();
      Utils.gc();
      crawlDbTool.update( crawlDb, segs, true, true ); // update crawldb
      Utils.disconnectFilters();
      Utils.gc();
    }
    if ( i > 0 )
    {
      linkDbTool.invert( linkDb, segments, true, true, false ); // invert links
      Utils.disconnectFilters();
      if ( solrUrl != null )
      { // index
        FileStatus[] fstats = fs.listStatus( segments, HadoopFSUtil.getPassDirectoriesFilter( fs ) );
        IndexingJob indexingJob = new IndexingJob( conf );
        // SolrIndexer indexer = new SolrIndexer( getConf() ) ;
        // indexer.indexSolr( solrUrl, crawlDb, linkDb, Arrays.asList( HadoopFSUtil.getPaths( fstats ) ) ) ;
        indexingJob.index( crawlDb, linkDb, Arrays.asList( HadoopFSUtil.getPaths( fstats ) ), false );
        Utils.disconnectFilters();
        
        String[] cleaningArgs = { crawlDb.toString() } ;
        if ( conf.getBoolean( "remove.duplicates", false ))
        {
          ToolRunner.run( conf, new ArchDeduplicationJob(), cleaningArgs );
        }
        ToolRunner.run( conf, new ArchCleaningJob(), cleaningArgs );
        Utils.gc();
      }
    } else
      LOG.warn( "No URLs to fetch - check your seed list and URL filters." );

    if ( LOG.isInfoEnabled() )
    {
      LOG.info( "crawl finished: " + dir );
    }
    return 0;
  }

  /**
   * Gets current date in required format
   * 
   * @return current date
   */
  private static String getDate()
  {
    return new SimpleDateFormat( "yyyyMMddHHmmss" ).format( new Date( System.currentTimeMillis() ) );
  }

  /**
   * Copies required parameter from one configuration to another
   * 
   * @param dst
   *          destination configuration
   * @param src
   *          source configuration
   * @param name
   *          name of the parameter to copy
   */
  public static void copy( Configuration dst, Configuration src, String name )
  {
    String val = src.get( name );
    if ( val != null )
    {
      dst.set( name, val );
    }
  }

}
