/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * Implements security scanning related functions
 * 
 */


package au.csiro.cass.arch.security;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseImpl;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.protocol.Content;
import org.apache.nutch.protocol.Protocol;
import org.apache.nutch.protocol.ProtocolFactory;
import org.apache.nutch.protocol.ProtocolOutput;
import org.apache.nutch.protocol.ProtocolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.SiteMatcher;
import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.PluginConnectionCache;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

public class ScanUtil implements DBConnected
{
  public static final Logger LOG = LoggerFactory.getLogger( ScanUtil.class ) ;
  private static ScanUtil          util ; // util instance
  private boolean             connected ; // true if has been connected to the database
  Configuration                    conf ; // Nutch configuration object
  Map<String,ScanConfig> configurations ; // a cache of scanning configurations
  private IndexRootDB            rootDb ; // root db connection
  private ConnectionCache   connections ; // a pool of site connections
  SiteMatcher                   matcher ; // url -> <site, area> matcher
  String                      knownSite ; // site, if known
  String                      knownArea ; // area, if known
  boolean           loglinks, bookmarks ; // true if the area is loglinks or bookmarks
  ArrayList<Scanner>           scanners ; // scanner objects
  ArrayList<Pruner>             pruners ; // pruner objects
  ProtocolFactory       protocolFactory ; // protocols to use for source retrieving
  
  
  public ScanUtil() { configurations = new HashMap<String,ScanConfig>() ; } ;
   
  public static ScanUtil getScanUtil( Configuration conf ) throws Exception
  {
    if ( util != null )
    	{ util.conf = conf ; if ( !util.connected ) util.connect() ; return util ; }
	util = new ScanUtil() ;
	util.conf = conf ;
	// create scanners
	util.scanners = ScannerFactory.getScanners( conf ) ;
	util.pruners = PrunerFactory.getPruners( conf ) ;
	util.protocolFactory = new ProtocolFactory( conf ) ;
	util.connect() ;
	return util ;
  }
  
  public static void preParsePrune( Configuration conf, Content content )
  throws Exception
  {
    // make sure context exists
    getScanUtil( conf ) ;
    String url = content.getUrl() ;

    String site = util.getSite( url ) ; 
    if ( site == null ) return ; // the document does not belong to any sites in processing
    ScanConfig sc =  util.getConfiguration( site ) ;
    ScanContext context = util.getContext( content, null, site, sc ) ;
    if ( !context.pruning ) return ;
    long m = 1024 * 1024 ;
    long free = Runtime.getRuntime().freeMemory() / m ;
    long max = Runtime.getRuntime().maxMemory() / m ;
    LOG.debug( "PRUNNING " + url + ", free mem: " + free + " of " + max + "\n" ) ;

    // prune downloaded content
    context.originalContent = Text.decode( content.getContent() ) ;
    for ( Pruner pruner : util.pruners )
        context.originalContent = pruner.preParsePrune( context, context.originalContent ) ;    
    content.setContent( context.originalContent.getBytes() ) ;
  }

  
  public static void postParsePrune( Configuration conf, Content content, ParseResult parseResult )
  throws Exception
  {
    // make sure context exists
    getScanUtil( conf ) ;
    String url = content.getUrl() ;

    String site = util.getSite( url ) ; 
    if ( site == null ) return ; // the document does not belong to any sites in processing
    ScanConfig sc =  util.getConfiguration( site ) ;
    ScanContext context = util.getContext( content, null, site, sc ) ;
    if ( !context.pruningAfter ) return ;

    // prune extracted content
    Iterator<Entry<Text, Parse>> it = parseResult.iterator() ;
    while( it.hasNext() )
    {
      Map.Entry<Text, Parse> entry = it.next();
      Parse parse = entry.getValue();
      String text = parse.getText() ;
      if ( text != null && text.length() > 0 )
      {
        for ( Pruner pruner : util.pruners )
            text = pruner.postParsePrune( context, text ) ;    
        if ( !text.equals( parse.getText() ) ) // any changes made by pruners?
        {
          ParseImpl newValue = new ParseImpl( text, parse.getData() ) ;
          entry.setValue( newValue ) ;
        }
      }
    }
  }

  
  
  public static void scan( Configuration conf, Content content, ParseResult parse )
  throws Exception
  {
	// make sure context exists
    postParsePrune( conf, content, parse ) ;
	if ( util.bookmarks ) return ; // not scanning bookmarks
	String url = content.getUrl() ;
    String site = util.getSite( url ) ; 
    if ( site == null ) return ; // the document does not belong to any sites in processing
    ScanConfig sc =  util.getConfiguration( site ) ;
    if ( !sc.scanEnabled ) return ;

    ScanContext context = util.getContext( content, parse, site, sc ) ;
    if ( !context.scanningSrc && !context.scanningOut ) return ;
    // get source
    if ( !context.scanningSrc ) context.originalSource = "" ;
    else // download the source
    {
      context.originalSource = util.downloadSource( context, context.url ) ; 
      if ( context.originalSource == null || context.originalSource.length() == 0 )
    	    throw new Exception( " Could not download source for scanning: " + context.url ) ;
    }

	// normalise content and source
    context.originalContent = Text.decode( content.getContent() ) ;
    context.lowcaseContent = context.originalContent.toLowerCase() ;
    for ( Pruner pruner : util.pruners )
    	context.originalSource = pruner.preParsePrune( context, context.originalSource ) ;
    context.lowcaseSource = context.originalSource.toLowerCase() ;
	// put content and source through scanners
    ScanResult scanResult = new ScanResult() ;
    scanResult.url = context.url ;
//BasicScanner scanner0 = new BasicScanner() ;
//scanner0.scan( context, scanResult ) ;
    for ( Scanner scanner : util.scanners )
       scanner.scan( context, scanResult ) ;	
	// write results
    context.siteDB.writeScanResult( scanResult ) ;
    for ( ScanAlert alert : scanResult.alerts )
                            context.rootDB.addAlert( alert ) ;
    int srcId = -1 ;
    if ( scanResult.newLinks != null )
        for ( String link : scanResult.newLinks )
            {
              srcId = context.siteDB.addLink( context.url, srcId, link ) ;
              context.rootDB.addAlert( new ScanAlert( context.site, context.url, ScanAlert.UNSURE, "New link: " + link ) ) ;
            }

    if ( scanResult.removedLinks != null )
        for ( String link : scanResult.removedLinks )
            {
              srcId = context.siteDB.removeLink( context.url, srcId, link ) ;
              context.rootDB.addAlert( new ScanAlert( context.site, context.url, ScanAlert.UNSURE, "Removed link: " + link ) ) ;
            }
  }

  
  ScanContext getContext( Content content, ParseResult parse, String site, ScanConfig sc )
  throws Exception
  {
    String url = content.getUrl() ;
	// establish site and area, unless known
	ScanContext context = new ScanContext() ;
	context.util = util ;
	context.site = site ;
	context.rootDB = util.rootDb ;
	context.siteDB = util.connections.getSiteConnection( context.site ) ;
	context.url = url ;
	context.parseResult = parse ;
	context.content = content ;
	context.site = site ;

	// get site configuration object
	context.config = sc ;
	// get content type
	context.contentType = content.getContentType() ;    
	// get file type
	context.path = url ;
	int end = url.indexOf( '?' ) ;
	if ( end > 0 ) context.path = url.substring( 0, end ) ;
	int dot = context.path.lastIndexOf( '.' ) ;
	context.fileType = "" ;
	if ( dot > 0 ) context.fileType = context.path.substring( dot + 1 ).toLowerCase() ;
	if( context.path.endsWith( "/" ) ) context.fileType = "html" ;
	// do we want to scan this page?
	context.scanningOut = context.config.contentTypes.contains( context.contentType ) ||
	                      context.config.fileTypes.contains( context.fileType ) ;
	// do we want to scan source of this page?
	context.scanningSrc = context.config.srcContentTypes.contains( context.contentType ) ||
                          context.config.srcFileTypes.contains( context.fileType ) ;
	// do we want to prune this page?
    context.pruning = context.config.pruneContentTypes.contains( context.contentType ) ||
                            context.config.pruneFileTypes.contains( context.fileType ) ;
    context.pruningAfter = context.config.pruneContentTypesAfter.contains( context.contentType ) ||
                            context.config.pruneFileTypesAfter.contains( context.fileType ) ;
	return context ;  
  }

  
  String downloadSource( ScanContext context, String url ) throws Exception
  {
    Text u = new Text( context.config.sourceAccess + URLEncoder.encode( url, "UTF-8" ) ) ;
    Protocol protocol = util.protocolFactory.getProtocol( u.toString() ) ;
    CrawlDatum datum = new CrawlDatum() ;
    ProtocolOutput output = protocol.getProtocolOutput( u, datum ) ;
    ProtocolStatus status = output.getStatus();
    if ( status.getCode() != ProtocolStatus.SUCCESS ) return null ;
    Content srcContent = output.getContent();
    String content = Text.decode( srcContent.getContent() ) ;
    if ( content.length() < 100 && content.indexOf( "Error:" ) >= 0 ) return null ;
    return content ;
  }
  
  
  public synchronized void connect() throws Exception
  {
	if ( connected ) return ;
 	connections = PluginConnectionCache.newPluginConnectionCache( conf, false ) ;  
    rootDb = connections.getRootConnection() ;
    knownSite = conf.get( "arch.site" ) ;
    knownArea = conf.get( "arch.area" ) ;
    loglinks = ( knownArea != null && knownArea.equalsIgnoreCase( "loglinks" ) ) ;
    bookmarks = ( knownArea != null && knownArea.equalsIgnoreCase( "bookmarks" ) )  ;
    
    // If crawling log links or doing parallel indexing, need a matcher
    if ( loglinks || knownSite == null || knownSite.length() == 0 )
       {
    	 Map<String, ArrayList<String>[]> roots = rootDb.readRoots() ;
    	 matcher = SiteMatcher.newSiteMatcher( roots ) ;
       }
    connected = true ;
    Utils.scanUtil = this ;
  }
   
  public synchronized void disconnect() throws Exception
  {
    if ( connected ) connections.destroy() ;
    connections = null ;
    rootDb = null ;
    connected = false ;
    Utils.scanUtil = null ;
  }
  
  String getSite( String url ) throws Exception
  {
	return matcher != null ? matcher.getSite( url ) : knownSite ;
  }
  
  ScanConfig getConfiguration( String site ) throws Exception 
  {
	ScanConfig scanConfig = configurations.get( site ) ;
	if ( scanConfig != null ) return scanConfig ;
	ConfigList cfg = rootDb.readConfig( site ) ;
	scanConfig = new ScanConfig( cfg ) ;
	configurations.put( site, scanConfig ) ;
	return scanConfig ;
  }
    
  
}
