/**
 * 
 */
package au.csiro.cass.arch.index;

import java.net.URL;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;

import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;

/**
 * Models an index site created "on the fly" by defining a crawling seed URL in the environment variable. These sites
 * take their configuration from environment variables and shared configuration file interim-site.cfg. NOTE: -
 * parameters provided in the environment variables OVERRIDE parameters provided in configuration files; - names of
 * interim sites are generated automatically by removing common domain prefixes (like "www.") and suffixes (like
 * ".com"), upper casing first letter in the remaining part and replacing dots with underscores; - if a permanent site
 * is created to replace an interim site (same host name) and its name is different, THE INDEX OF THE REPLACED SITE WILL
 * BE AUTOMATICALLY DELETED after the replacing site has been indexed.
 *
 * This class also contains a few static methods servicing the interim index sites feature.
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 */
public class InterimIndexSite extends IndexSite
{

  /**
   * Parameterless constructor
   */
  public InterimIndexSite()
  {}

  /**
   * creates a new IndexSite instance, sets dates of the first and last records
   * 
   * @param dir
   *          directory containing site data
   * @param boss
   *          the root Indexer process
   * @return
   * @throws Exception
   */
  static InterimIndexSite newInterimIndexSite( String name, Indexer indexer ) throws Exception
  {

    InterimIndexSite site = new InterimIndexSite();
    site.indexer = indexer;
    site.email = new StringBuffer();
    site.name = name;
    site.setCfg( indexer.getCfg().clone( true ) );
    String emailRecipient = site.getCfg().get( "mail.recipient", "" );
    if ( emailRecipient.trim().length() == 0 )
    {
      site.emailViaIndexer = true;
    }
    else
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

    // sort out the base URL and crawling root(s)
    ArrayList<String> roots = new ArrayList<String>();
    if ( indexer.crawlingSeed != null ) // it can be null if this site is to be dropped
    {
      for ( String str : indexer.crawlingSeed )
      {
        URL url = new URL( str );
        String host = url.getHost();
        String siteName = indexer.getSites().get( host );
        if ( siteName != null && siteName.equals( name ) )
        {
          roots.add( str );
        }
      }
    }

    // make base URL
    if ( roots.size() > 0 )
    {
      String root = roots.get( 0 );
      int i = root.indexOf( '/', 10 );
      if ( i < 0 )
      {
        site.url = root + "/";
      }
      else
      {
        site.url = root.substring( 0, root.indexOf( '/', 10 ) );
      }
      if ( !site.url.endsWith( "/" ) )
      {
        site.url += "/";
      }
      site.getCfg().add( "url", site.url );
      site.setDepth( site.getCfg().getInherited( "depth", 30, "CRAWLING_DEPTH" ) );
      indexer.getDb().regSite( site.name, site.url );
      indexer.getDb().writeConfig( site.name, site.getCfg() );
    }
    site.areas = IndexArea.newInterimIndexArea( site, roots );

    // LOG.trace( "New index site: " + site.name ) ;
    Configuration nutchConf = NutchConfiguration.create();
    DBInterfaceFactory factory = DBInterfaceFactory.get( nutchConf );
    String database = site.getCfg().getInherited( "database", "MySQL" );
    DBInterface intrf = factory.get( database );
    site.db = intrf.newIndexSiteDB( site.getCfg(), site.name, false );
    indexer.getDb().writeConfig( site.name, site.getCfg() );

    return site;
  }


//    public static void main(String[] args) throws Exception
//    {  
//      test( "aaa bbb ccc",
//    "aaa www.aaa.com | bbb www.bbb.org | eee www.eee.com.au | xxx www.dropthis.com", //
//    " http://www.ddd.com/qq | http://www.bbb.com.au/qq | http://www.eee.com.au", //
//    "aaa www.aaa.com | bbb www.bbb.com | ccc www.ccc.com.au" ) ; // case when there is a conflict between new interim
//    and old conf site - covered above // case when there is a conflict between new and old interim sites (eee) test(
//    "aaa bbb ccc", "aaa www.aaa.com | bbb www.bbb.org | eee www.eee.com.au | xxx www.dropthis.com",
//    " http://www.ddd.com/qq | http://www.bbb.com.au/qq | http://www.eee.com.au | http://www.eee.com | http://www.eee.com.cn"
//    , "aaa www.aaa.com | bbb www.bbb.com | ccc www.ccc.com.au" ) ; // case when there is a conflict between old interim
//    and new conf site (bbb) // test( "aaa bbb ccc",
//    "aaa www.aaa.com | bbb www.bbb.org | eee www.eee.com.au | xxx www.dropthis.com", //
//    " http://www.ddd.com/qq | http://www.bbb.org/qq | http://www.eee.com.au | http://www.eee.com ", //
//    "aaa www.aaa.com | bbb www.bbb.org | ccc www.ccc.com.au" ) ;
//    
//    }
//    
//    // static void test( String existing, String old, String seed, String folders ) // throws Exception // { // Indexer
//    indexer = new Indexer() ; // sitelist = old ; // crawlingseed = seed ; // testing = true ; // onDisk = folders ; //
//    Map<String,String> result = getSites( indexer, existing.split( " " ) ) ; // for ( String host : result.keySet() )
//    System.out.println( host + " -> " + result.get( host ) ) ; //
//    }
   

}
