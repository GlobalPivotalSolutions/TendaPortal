/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.filters.scoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.Inlinks;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseData;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.scoring.ScoringFilter;
import org.apache.nutch.scoring.ScoringFilterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.SiteAreas;
import au.csiro.cass.arch.index.SiteMatcher;
import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.sql.PluginConnectionCache;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
* This plugin replaces the standard Nutch scoring filter based on OPIC.
* Instead, scores are taken from an external database.
*
* @author Arkadi Kosmynin
*/
public class Scoring implements ScoringFilter, DBConnected 
{
 public static final Logger LOG = LoggerFactory.getLogger( Scoring.class ) ;

 private Configuration conf;
 private String area ;
 private String site ;
 static private boolean connected = false ;
 static private IndexSiteDB db ;
 static private IndexRootDB rootDb ;
 float       scorePower ;
 boolean      bookmarks ;
 boolean       loglinks ;
 SiteAreas        known ;
 static SiteMatcher    matcher ;
 private static ConnectionCache connections ;


 
 public Scoring() { super() ; } ;
 
 public Configuration getConf()
  { return conf ; }

 public void setConf( Configuration conf )
  {
    this.conf = conf ;
    scorePower = conf.getFloat( "indexer.score.power", 0.5f ) ;
  }

  public synchronized void connect() throws Exception
  {
	String trace = "" ;
	try
	{ 
  	  if ( connected ) return ;
	  connections = PluginConnectionCache.newPluginConnectionCache( conf, false ) ;  
      rootDb = connections.getRootConnection() ;
	  String site = conf.get( "arch.site" ) ;
	  String area = conf.get( "arch.area" ) ;
	  loglinks = area != null && area.equalsIgnoreCase( "loglinks" ) ;
	  bookmarks = area != null && area.equalsIgnoreCase( "bookmarks" ) ;
      known = new SiteAreas( null ) ;
	  if ( site != null && site.length() > 0 ) known.setSite( site ) ;
	  if ( area != null && area.length() > 0 && !loglinks ) known.addArea( area ) ;
	
      // If crawling log links or doing parallel indexing, need a matcher
      if ( loglinks || known.getSite() == null )
       {
    	 Map<String, ArrayList<String>[]> roots = rootDb.readRoots() ;
    	 matcher = SiteMatcher.newSiteMatcher( roots ) ;
       }
      if ( known.getSite() != null  )
      {
        ConfigList cfg = rootDb.readConfig( site ) ;
        db = connections.getSiteConnection( site ) ;
      }
    
      connected = true ;
      Utils.scoringFilter = this ;
	} catch( Exception e )
	{
      LOG.error( "Exception in Scoring.connect" + e.getMessage(), e ) ;
      throw e ;
	}
  }
 
 public void injectedScore( Text url, CrawlDatum datum )
  throws ScoringFilterException
  { datum.setScore( getBoost( url ) ) ; }


 public void initialScore( Text url, CrawlDatum datum )
  throws ScoringFilterException
  { datum.setScore( getBoost( url ) ) ; }

/** Use {@link CrawlDatum#getScore()}. */
public float generatorSortValue( Text url, CrawlDatum datum, float initSort)
 throws ScoringFilterException { return datum.getScore() * initSort ; }

/** Store a float value of CrawlDatum.getScore() under Fetcher.SCORE_KEY. */
public void passScoreBeforeParsing( Text url, CrawlDatum datum, Content content )
 { content.getMetadata().set(Nutch.SCORE_KEY, "" + datum.getScore()); }

/** Copy the value from Content metadata under Fetcher.SCORE_KEY to parseData. */
public void passScoreAfterParsing(Text url, Content content, Parse parse)
 { 
   parse.getData().getContentMeta().set(Nutch.SCORE_KEY, 
                                        content.getMetadata().get(Nutch.SCORE_KEY));
 }

/** Increase the score by a sum of inlinked scores. */
public void updateDbScore(Text url, CrawlDatum old, CrawlDatum datum, List inlinked)
 throws ScoringFilterException
 {
 }

  /** This left unchanged from OPIC, but should not have any effects */
  public CrawlDatum distributeScoreToOutlinks(Text fromUrl, ParseData parseData,
		  Collection<Entry<Text, CrawlDatum>> targets, CrawlDatum adjust, int allCount)
  throws ScoringFilterException
  {
    return adjust;
  }

/** Get boost value from the db */
public float indexerScore(Text url, NutchDocument doc, CrawlDatum dbDatum,
  CrawlDatum fetchDatum, Parse parse, Inlinks inlinks, float initScore )
  throws ScoringFilterException
  { 
	return getBoost( url ) ;
  }


public synchronized void disconnect() throws Exception
  {
   if ( connected ) connections.destroy() ;
   connected = false ;
   db = null ;
   rootDb = null ;
   connections = null ;
   matcher = null ;
  }

  public Logger getLOG()
   {
    return LOG;
   }
  
  synchronized float getBoost( Text url ) throws ScoringFilterException
  {
    float boost = 1f ;
	String u = null ;
	String trace = "" ;
	try { 
		  if ( LOG.isDebugEnabled() ) trace += "1." ;
		  if ( !connected ) connect() ;
		  if ( LOG.isDebugEnabled() ) trace += "2." ;
		  if ( bookmarks ) return boost * 1.5f ;
		  if ( LOG.isDebugEnabled() ) trace += "3." ;
		  u = url.toString() ;
	  	  if ( db != null ) 
	  		  {  if ( LOG.isDebugEnabled() ) trace += "3.5: got boost " + boost + " " ;
	  		     boost = db.getWeight( u ) ;
	  		  }
	  	  else {
			      if ( LOG.isDebugEnabled() ) trace += "4." ;
	  		     String site = matcher.getSite( u ) ;
	  			  if ( LOG.isDebugEnabled() ) trace += "5." ;
	             if ( site == null ) return boost ;
	   		      if ( LOG.isDebugEnabled() ) trace += "6." ;
		         IndexSiteDB con = connections.getSiteConnection( site ) ;
				  if ( LOG.isDebugEnabled() ) trace += "7." ;
		         boost = con.getWeight( u ) ;
				  if ( LOG.isDebugEnabled() ) trace += "8: got boost " + boost + " " ;
	  	        }
		  if ( LOG.isDebugEnabled() ) trace += "9." ;
		  if ( boost < 0 ) boost = 0 ; // a url that never occurred in logs
		  if ( LOG.isDebugEnabled() ) trace += "10." ;
		  boost += 1f ;
		  if ( LOG.isDebugEnabled() )
		               LOG.debug( "Boost in ArchScoring: " + boost + "  " + u + " trace: " + trace ) ;
		} catch( Exception e )
		{ 
		  LOG.error( "IndexerScore " + u + " exception: " + e.getMessage() + " trace: " + trace ) ;
		  throw new ScoringFilterException( e.getMessage() ) ;
		}
	return boost ;
  }

}

