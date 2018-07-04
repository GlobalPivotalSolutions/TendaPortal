/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.cass.arch.index;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.RunningJob;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.Counters.Group;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.CrawlDb;
import org.apache.nutch.crawl.DeduplicationJob;
import org.apache.nutch.crawl.DeduplicationJob.DBFilter;
import org.apache.nutch.crawl.DeduplicationJob.DedupReducer;
import org.apache.nutch.crawl.DeduplicationJob.StatusUpdateReducer;
import org.apache.nutch.net.URLFilterException;
import org.apache.nutch.net.URLFilters;
import org.apache.nutch.util.NutchJob;
import org.apache.nutch.util.TimingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.IndexNode;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.sql.PluginConnectionCache;
import au.csiro.cass.arch.utils.Utils;

/**
 * Modifies Nutch DeduplicationJob to save information on equivalent URLs in the DB to use this information in counting
 * URLs hit scores.
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class ArchDeduplicationJob extends DeduplicationJob implements Tool
{

  public static final Logger LOG = LoggerFactory.getLogger( ArchDeduplicationJob.class );

  public final static Text URL_KEY = new Text( "_URLTEMPKEY_" );

  @Override
  public int run( String[] args ) throws IOException
  {
    if ( args.length < 1 )
    {
      System.err.println( "Usage: DeduplicationJob <crawldb>" );
      return 1;
    }

    String crawldb = args[ 0 ];

    SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd HH:mm:ss" );
    long start = System.currentTimeMillis();
    LOG.info( "ArchDeduplicationJob: starting at " + sdf.format( start ) );

    Path tempDir = new Path( getConf().get( "mapred.temp.dir", "." ) + "/dedup-temp-"
        + Integer.toString( new Random().nextInt( Integer.MAX_VALUE ) ) );

    JobConf job = new NutchJob( getConf() );

    job.setJobName( "Deduplication on " + crawldb );

    FileInputFormat.addInputPath( job, new Path( crawldb, CrawlDb.CURRENT_NAME ) );
    job.setInputFormat( SequenceFileInputFormat.class );

    FileOutputFormat.setOutputPath( job, tempDir );
    job.setOutputFormat( SequenceFileOutputFormat.class );

    job.setMapOutputKeyClass( BytesWritable.class );
    job.setMapOutputValueClass( CrawlDatum.class );

    job.setOutputKeyClass( Text.class );
    job.setOutputValueClass( CrawlDatum.class );

    job.setMapperClass( DBFilter.class );
    job.setReducerClass( DedupReducer.class );

    try
    {
      RunningJob rj = JobClient.runJob( job );
      Group g = rj.getCounters().getGroup( "DeduplicationJobStatus" );
      if ( g != null )
      {
        long dups = g.getCounter( "Documents marked as duplicate" );
        LOG.info( "Deduplication: " + (int)dups + " documents marked as duplicates" );
      }
    } catch ( final Exception e )
    {
      LOG.error( "DeduplicationJob: " + StringUtils.stringifyException( e ) );
      return -1;
    }

    // merge with existing crawl db
    if ( LOG.isInfoEnabled() )
    {
      LOG.info( "Deduplication: Updating status of duplicate urls into crawl db." );
    }

    Path dbPath = new Path( crawldb );
    JobConf mergeJob = CrawlDb.createJob( getConf(), dbPath );
    FileInputFormat.addInputPath( mergeJob, tempDir );
    mergeJob.setReducerClass( StatusUpdateReducer.class );

    try
    {
      JobClient.runJob( mergeJob );
    } catch ( final Exception e )
    {
      LOG.error( "DeduplicationMergeJob: " + StringUtils.stringifyException( e ) );
      return -1;
    }

    CrawlDb.install( mergeJob, dbPath );

    // clean up
    FileSystem fs = FileSystem.get( getConf() );
    fs.delete( tempDir, true );

    long end = System.currentTimeMillis();
    LOG.info( "Deduplication finished at " + sdf.format( end ) + ", elapsed: " + TimingUtil.elapsedTime( start, end ) );

    return 0;
  }

  public static class DBFilter implements Mapper<Text, CrawlDatum, BytesWritable, CrawlDatum>
  {

    @Override
    public void configure( JobConf arg0 )
    {}

    @Override
    public void close() throws IOException
    {}

    @Override
    public void map( Text key, CrawlDatum value, OutputCollector<BytesWritable, CrawlDatum> output, Reporter reporter )
        throws IOException
    {

      if ( value.getStatus() == CrawlDatum.STATUS_DB_FETCHED || value.getStatus() == CrawlDatum.STATUS_DB_NOTMODIFIED )
      {
        // || value.getStatus() ==CrawlDatum.STATUS_DB_GONE){
        byte[] signature = value.getSignature();
        if ( signature == null )
          return;
        BytesWritable sig = new BytesWritable( signature );
        // add the URL as a temporary MD
        value.getMetaData().put( URL_KEY, key );
        // reduce on the signature
        output.collect( sig, value );
      }
    }
  }

  public static class DedupReducer extends DeduplicationJob.DedupReducer implements
      Reducer<BytesWritable, CrawlDatum, Text, CrawlDatum>, DBConnected
  {
    private Configuration conf;
    private static boolean connected = false;
    private static IndexSiteDB db;
    private static IndexRootDB rootDb;
    private static ConnectionCache connections;
    SiteMatcher matcher;
    SiteAreas known;
    boolean loglinks, bookmarks;
    private static URLFilters filters; // URL filters to use

    private void writeOutAsDuplicate( CrawlDatum datum, OutputCollector<Text, CrawlDatum> output, Reporter reporter )
        throws IOException
    {
      datum.setStatus( CrawlDatum.STATUS_DB_DUPLICATE );
      Text key = (Text)datum.getMetaData().remove( URL_KEY );
      reporter.incrCounter( "DeduplicationJobStatus", "Documents marked as duplicate", 1 );
      output.collect( key, datum );
    }

    @Override
    public void reduce( BytesWritable key, Iterator<CrawlDatum> values, OutputCollector<Text, CrawlDatum> output,
        Reporter reporter ) throws IOException
    {
      CrawlDatum existingDoc = null;
      String urlOld = null;
      try
      {
        connect() ;
      } catch ( Exception e1 )
      {
        throw new IOException( e1 ) ;
      }
      String siteName = known.getSite();

      while ( values.hasNext() )
      {
        if ( existingDoc == null )
        {
          existingDoc = new CrawlDatum();
          existingDoc.set( values.next() );
          urlOld = filter( existingDoc.getMetaData().get( URL_KEY ).toString() );
          if ( siteName == null )
          {
            try
            {
              siteName = matcher.getSite( urlOld );
            } catch ( Exception e )
            {
              throw new IOException( "Exception when matching URL " + urlOld );
            }
          }
          continue;
        }
        CrawlDatum newDoc = values.next();
        // Keep the URL that has a filename extension or does not have a query string
        String urlNew = filter( newDoc.getMetaData().get( URL_KEY ).toString() );
        boolean keepOld = true;
        if ( urlNew.lastIndexOf( '.' ) >= urlNew.length() - 4 && urlOld.lastIndexOf( '.' ) < urlOld.length() - 4 )
          keepOld = false;
        if ( urlOld.endsWith( "/" ) )
          keepOld = false;
        if ( urlNew.lastIndexOf( '?' ) > 0 )
          keepOld = true;
        if ( urlOld.lastIndexOf( '?' ) > 0 )
          keepOld = false;
        // Register these two as duplicates, anyway
        try
        {
          registerAlias( urlOld, urlNew, siteName, false );
        } catch ( Exception e )
        {
          throw new IOException( "Exception when registering aliases: " + urlOld + " and " + urlNew );
        }
        if ( keepOld == true )
        {
          // mark new one as duplicate
          writeOutAsDuplicate( newDoc, output, reporter );
          continue;
        } else
        {
          // mark existing one as duplicate
          writeOutAsDuplicate( existingDoc, output, reporter );
          existingDoc = new CrawlDatum();
          existingDoc.set( newDoc );
          urlOld = urlNew;
          continue;
        }
      }
    }

    private String filter( String url ) throws IOException
    {
      try
      {
        return filters.filter( url );
      } catch ( URLFilterException e )
      {
        throw new IOException( "Unexpected filter exception while filterinng " + url + ": " + e.getMessage() );
      }
    }

    public synchronized void connect() throws Exception
    {
      if ( connected )
        return;
      if ( filters == null )
        filters = new URLFilters( conf );
      connections = PluginConnectionCache.newPluginConnectionCache( conf, false );
      rootDb = connections.getRootConnection();
      String site = conf.get( "arch.site" );
      String area = conf.get( "arch.area" );
      known = new SiteAreas( null );
      loglinks = ( area != null && area.equalsIgnoreCase( "loglinks" ) );
      bookmarks = ( area != null && area.equalsIgnoreCase( "bookmarks" ) );
      if ( site != null && site.length() > 0 )
        known.setSite( site );
      if ( area != null && area.length() > 0 && !loglinks )
        known.addArea( area );

      // If crawling log links or doing parallel indexing, need a matcher
      if ( loglinks || site == null || site.length() == 0 )
      {
        Map<String, ArrayList<String>[]> roots = rootDb.readRoots();
        matcher = SiteMatcher.newSiteMatcher( roots );
      }
      if ( site != null && site.length() > 0 )
      {
        db = connections.getSiteConnection( site );
      }
      Utils.dedupReducer = this;
      connected = true;
    }

    IndexNode registerAlias( String url, String alias, String siteName, boolean markIndexed ) throws Exception
    {
      if ( db != null )
        return db.registerAlias( url, alias, markIndexed );
      IndexSiteDB con = connections.getSiteConnection( siteName );
      return con.registerAlias( url, alias, markIndexed );
    }

    @Override
    public void configure( JobConf arg0 )
    {
      conf = arg0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see au.csiro.cass.arch.sql.DBConnected#disconnect()
     */
    @Override
    public synchronized void disconnect() throws Exception
    {
      if ( connected )
        connections.destroy();
      connections = null;
      db = null;
      rootDb = null;
      connected = false;
    }

  }

  /** Combine multiple new entries for a url. */
  public static class StatusUpdateReducer implements Reducer<Text, CrawlDatum, Text, CrawlDatum>
  {

    public void configure( JobConf job )
    {}

    public void close()
    {}

    private CrawlDatum old = new CrawlDatum();
    private CrawlDatum duplicate = new CrawlDatum();

    public void reduce( Text key, Iterator<CrawlDatum> values, OutputCollector<Text, CrawlDatum> output,
        Reporter reporter ) throws IOException
    {
      boolean duplicateSet = false;

      while ( values.hasNext() )
      {
        CrawlDatum val = values.next();
        if ( val.getStatus() == CrawlDatum.STATUS_DB_DUPLICATE )
        {
          duplicate.set( val );
          duplicateSet = true;
        } else
        {
          old.set( val );
        }
      }

      // keep the duplicate if there is one
      if ( duplicateSet )
      {
        output.collect( key, duplicate );
        return;
      }

      // no duplicate? keep old one then
      output.collect( key, old );
    }
  }
}
