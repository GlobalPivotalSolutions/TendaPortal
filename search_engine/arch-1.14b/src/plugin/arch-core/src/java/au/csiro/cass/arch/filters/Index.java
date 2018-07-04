/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.filters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.Inlinks;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.net.URLFilters;
import org.apache.nutch.net.protocols.Response;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseData;
import org.apache.nutch.util.MimeUtil;
import org.apache.tika.mime.MimeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.SiteAreas;
import au.csiro.cass.arch.index.SiteMatcher;
import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.DBConnected;
import au.csiro.cass.arch.sql.IndexNode;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.sql.PluginConnectionCache;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

public class Index implements IndexingFilter, DBConnected
 {
   public static final Logger LOG = LoggerFactory.getLogger( Index.class ) ;
   private Configuration conf ;
   private MimeUtil MIME; 
   private static boolean connected = false ;
   private static IndexSiteDB db ;
   private static IndexRootDB rootDb ;
   private static ConnectionCache connections ;
   SiteMatcher matcher ;
   SiteAreas known ;
   boolean loglinks, bookmarks ;
   private String bookmarksUsers, bookmarksGroups ;
   private static URLFilters              filters ; // URL filters to use 

   
   private Set<String> types = new HashSet<String>() ;
   private Map<String, String> typeMap = new HashMap<String, String>() ;
   
   public Index() {} ;
   
   public static void main( String[] args )
   {
    System.exit( 0 ) ;
   }   
   
   public synchronized NutchDocument filter( NutchDocument doc, Parse parse, Text url,
		                                                   CrawlDatum datum, Inlinks inlinks )
    throws IndexingException 
    {
	  String u = null, passedURL = null ;
	  String usersRead = null, usersWrite = null, owners = null,
	         groupsRead = null, groupsWrite = null ;

      try {
            if ( !connected ) connect() ;
            String canonical = parse.getData().getParseMeta().get( "canonical" ) ;
        	Text reprUrl = (Text) datum.getMetaData().get( Nutch.WRITABLE_REPR_URL_KEY ) ;  
            if ( reprUrl != null ) url = reprUrl ;
            u = passedURL = url.toString() ;
        	 // People may put anything in metadata, make sure it at least looks like a url
            IndexNode node = null ;
            if ( canonical == null || canonical.indexOf( "://" ) < 0 ) canonical = "null" ; 
            	else
            	{ 
            	  // If using canonical URL, it must be normalised to match URLs in the database
                  if ( filters == null ) filters = new URLFilters( conf ) ;
                  u = filters.filter( canonical ) ;
                  if ( u == null ) return null ; // blocked by filters
                }            	 
            LOG.info( "URL:\t" + url.toString() + "\tcanonical:\t" + canonical + "\tindexing:\t" + u ) ;
            // This is only needed for creation of a directory, and even then, may not
            // mark node as indexed if not log links being indexed, else keep it newly found
            SiteAreas sa = known ;
            if ( sa.getSite() == null || !sa.hasAreas() ) sa = matcher.getSiteAreas( u ) ; 
            String category = parse.getData().getParseMeta().get( "category" ) ;
            if ( category == null && !sa.hasAreas() ) return null ; // the document does not belong to any areas
            String siteName = sa.getSite() ;
            addType(doc, parse.getData(), u, datum ) ;
            doc.add( "ar_site", siteName ) ;
            doc.add( "ar_boost", Float.toString( doc.getWeight() ) );
            for ( String areaName : sa.getAreas() )
                        doc.add( "ar_area", areaName ) ;
            if ( category != null ) doc.add( "ar_area", category ) ;
            doc.removeField( "id" ) ;
            doc.add( "ar_id", siteName + ":" + u ) ;
            if ( !bookmarks )
               { 
                 if ( !passedURL.equalsIgnoreCase( u ) && !bookmarks ) // has an alias
             	                 node = registerAlias( u, passedURL, siteName, !loglinks ) ; 
                            else node =  makeSureExists( u, siteName, !loglinks ) ;

                 usersRead = node.getUserr() ; usersWrite = node.getUserw() ; owners = node.getOwners() ;
                 groupsRead = node.getGroupr() ; groupsWrite = node.getGroupw() ;
                 String access = parse.getData().getParseMeta().get( "access" ) ;
                 if ( access != null ) groupsRead = access ;
               } else // set permissions for bookmarks here
               {
            	 usersRead = bookmarksUsers ; groupsRead = bookmarksGroups ;
            	 usersWrite = groupsWrite = owners = "" ;
               }
            addPermissions( doc, "ar_user", usersRead, usersWrite, owners ) ;
            addPermissions( doc, "ar_groups", groupsRead, groupsWrite, null ) ;
            if ( LOG.isDebugEnabled() )
                 LOG.debug( "URL in Arch index filter: " + u ) ;
          } catch( Exception e )
          {
            LOG.error( "Document filter " + u + " exception: " + e.getMessage(), e ) ;
        	e.printStackTrace() ;
            throw new IndexingException( e.getMessage() ) ;
          }
      return doc ;
    }

  // User and group names must be separated, but not changed in any other way.
  // Can do it using an analizer plugin, but this seems to be simpler.
  void addPermissions( NutchDocument doc, String field, String p1, String p2, String p3 )
  {
	StringBuffer buf = new StringBuffer() ;
	if ( p1 != null && p1.length() > 0 ) buf.append( p1 ) ;
	if ( p2 != null && p2.length() > 0 ) { buf.append( " " ) ; buf.append( p2 ) ; }
	if ( p3 != null && p3.length() > 0 ) { buf.append( " " ) ; buf.append( p3 ) ; }
	String[] tokens = buf.toString().toLowerCase().split( " " ) ;
	if ( tokens != null )
	   for ( int i = 0 ; i < tokens.length ; i++ )
	      if ( tokens[ i ].length() > 0 ) doc.add( field, tokens[ i ] ) ;
  }
  
  
  /**
   * Based on a similar method in MoreIndexingFilter, except that type codes are not
   * amended with strings based on their parts. Instead, they are grouped into user
   * friendly groups, like "HTML", "Plain text", "Source code", "PDF", "Office document", etc.
   * @param doc
   * @param data
   * @param url
   * @return
   */
  private void addType(NutchDocument doc, ParseData data, String url, CrawlDatum datum)
  {
   // MimeType mimeType = null ;
   // String contentType = data.getMeta( Response.CONTENT_TYPE ) ;
   // if ( contentType == null ) mimeType = MIME.getMimeType( url ) ;
   //   else mimeType = MIME.forName( MimeUtil.cleanMimeType( contentType ) ) ;
    
    String mimeType = null;
    String contentType = null;

    Writable tcontentType = datum.getMetaData().get( new Text(Response.CONTENT_TYPE) ) ;
    if ( tcontentType != null ) contentType = tcontentType.toString();
      else contentType = data.getMeta( Response.CONTENT_TYPE ) ;
    if ( contentType == null ) mimeType = MIME.getMimeType( url ) ;
      else mimeType = MIME.forName( MimeUtil.cleanMimeType( contentType ) ) ;

    if ( mimeType == null ) return ;
    String type = typeMap.get( mimeType.trim() ) ;
    if ( type == null )
       { 
         if ( contentType == null ) type = "Other" ;
         else if ( contentType.startsWith( "audio" ) ) type = "Audio" ;
   	     else if ( contentType.startsWith( "video" ) ) type = "Video" ;
   	     else if ( contentType.startsWith( "image" ) ) type = "Image" ;
   	     else type = "Other" ;
       }
    
    if ( types.add( contentType ) )
    	LOG.warn( "Unknown content type: " + contentType + " at " + url ) ;
    
    doc.add( "type", type ) ;
    return ;
  }

   
  public void setConf(Configuration conf)
  {
    this.conf = conf ;
    this.MIME = new MimeUtil(conf) ;
    // Create map of most widely occurring content types
    typeMap.put( "text/rtf", "Document" ) ;
    typeMap.put( "text/x-rtf", "Document" ) ;
    typeMap.put( "application/x-tika-msoffice", "Document" ) ;
    typeMap.put( "application/pdf", "PDF" ) ;
    typeMap.put( "application/x-pdf", "PDF" ) ;
    typeMap.put( "application/vnd.ms-powerpoint", "Presentation" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.text", "Document" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.text-template", "Document" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.text-master", "Document" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.text-web", "Document" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.presentation", "Presentation" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.presentation-template", "Presentation" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.spreadsheet", "Spreadsheet" ) ;
    typeMap.put( "application/vnd.oasis.opendocument.spreadsheet-template", "Spreadsheet" ) ;
    typeMap.put( "application/vnd.sun.xml.calc", "Spreadsheet" ) ;
    typeMap.put( "application/vnd.sun.xml.calc.template", "Spreadsheet" ) ;
    typeMap.put( "application/vnd.sun.xml.impress", "Presentation" ) ;
    typeMap.put( "application/vnd.sun.xml.impress.template", "Presentation" ) ;
    typeMap.put( "application/vnd.sun.xml.writer", "Document" ) ;
    typeMap.put( "application/vnd.sun.xml.writer.template", "Document" ) ;
    typeMap.put( "application/vnd.ms-excel", "Spreadsheet" ) ;
    typeMap.put( "text/sgml", "Document" ) ;
    typeMap.put( "text/tab-separated-values", "Spreadsheet" ) ;
    typeMap.put( "application/x-csh", "Code" ) ;
    typeMap.put( "application/x-kword", "Document" ) ;
    typeMap.put( "application/x-kspread", "Spreadsheet" ) ;
    typeMap.put( "text/html", "HTML" ) ;
    typeMap.put( "text/plain", "Text" ) ;
    typeMap.put( "application/msword", "Document" ) ;
    typeMap.put( "application/postscript", "Document" ) ;
    typeMap.put( "application/xhtml+xml", "XML" ) ;
    typeMap.put( "application/rss+xml", "XML" ) ;
//    typeMap.put( "application/x-bzip2", "Compressed" ) ;
//    typeMap.put( "application/x-gzip", "Compressed" ) ;
    typeMap.put( "application/x-javascript", "Code" ) ;
    typeMap.put( "application/javascript", "Code" ) ;
    typeMap.put( "text/javascript", "Code" ) ;
    typeMap.put( "application/x-shockwave-flash", "Video" ) ;
//    typeMap.put( "application/zip", "Compressed" ) ;
    typeMap.put( "text/xml", "XML" ) ;
    typeMap.put( "application/xml", "XML" ) ;
    typeMap.put( "text/x-c", "Code" ) ;
    typeMap.put( "text/x-fortran", "Code" ) ;
    typeMap.put( "text/x-h", "Code" ) ;
    typeMap.put( "text/x-script", "Code" ) ;
    typeMap.put( "text/x-java-source", "Code" ) ;
    typeMap.put( "text/richtext", "Document" ) ;
    
    types.addAll( typeMap.keySet() ) ;
  }

  public synchronized void connect() throws Exception
  {
	if ( connected ) return ;
	connections = PluginConnectionCache.newPluginConnectionCache( conf, false ) ;  
    rootDb = connections.getRootConnection() ;
    String site = conf.get( "arch.site" ) ;
    String area = conf.get( "arch.area" ) ;
    known = new SiteAreas( null ) ;
    loglinks = ( area != null && area.equalsIgnoreCase( "loglinks" ) ) ;
    bookmarks = ( area != null && area.equalsIgnoreCase( "bookmarks" ) )  ;
    if ( site != null && site.length() > 0 ) known.setSite( site ) ;
    if ( area != null && area.length() > 0 && !loglinks ) known.addArea( area ) ;
    
    // If crawling log links or doing parallel indexing, need a matcher
    if ( loglinks || site == null || site.length() == 0 )
       {
    	 Map<String, ArrayList<String>[]> roots = rootDb.readRoots() ;
    	 matcher = SiteMatcher.newSiteMatcher( roots ) ;
    	 if ( LOG.isDebugEnabled() )
    	   LOG.debug( "Connecting, matcher:\n" + matcher + "known:\n" + known.toString() );
       }
    if ( site != null && site.length() > 0 )
    {
      ConfigList cfg = rootDb.readConfig( site ) ;
      if ( bookmarks )
        { bookmarksUsers = cfg.get( "usersread.bookmarks", "guest" ) ;
          bookmarksGroups = cfg.get( "groupsread.bookmarks", "public" ) ;
        } else db = connections.getSiteConnection( site ) ;
    }
    Utils.indexFilter = this ;
    connected = true ;
  }
  
  IndexNode makeSureExists( String url, String siteName, boolean markIndexed ) throws Exception
  {
	if ( db != null ) return db.makeSureExists( url, markIndexed ) ;
	IndexSiteDB con = connections.getSiteConnection( siteName ) ;
	return con.makeSureExists( url, markIndexed ) ;
  }
  
  
  IndexNode registerAlias( String url, String alias, String siteName, boolean markIndexed )
		  throws Exception
  {
	if ( db != null ) return db.registerAlias( url, alias, markIndexed ) ;
	IndexSiteDB con = connections.getSiteConnection( siteName ) ;
	return con.registerAlias( url, alias, markIndexed ) ;
  }
    

  public Configuration getConf() {
    return this.conf;
  }    
 
  public synchronized void disconnect() throws Exception
  {
    if ( connected ) connections.destroy() ;
    connections = null ;
    db = null ;
    rootDb = null ;
    connected = false ;
  }

  public Logger getLOG()
  {
    return LOG;
  }

}
  
