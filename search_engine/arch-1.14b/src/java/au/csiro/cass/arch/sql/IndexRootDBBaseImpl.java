/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.logProcessing.LogFile;
import au.csiro.cass.arch.security.ScanAlert;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Implements a set of db functions for access to tables shared by all sites and areas
 * 
 * @author Arkadi Kosmynin
 *
 */
public class IndexRootDBBaseImpl extends IndexDBBaseImpl implements IndexRootDB
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexRootDBBaseImpl.class ) ;
 public PreparedStatement  sqlInsertLogFile ;
 public PreparedStatement    sqlReadLogFile ;
 public PreparedStatement       sqlAddAlert ;
 public boolean                 servletMode ; // true if this object is used from a servlet
 public IndexInfo                      info ; // cached index info
 /**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexRootDBBaseImpl(  ConfigList cfg )
 { super( cfg ) ; }
 
 /**
 * Ensures that all required tables exist
 */
 public void init() throws Exception
 {
  
  if ( !servletMode )
  { 
   // info about known log files for each site
   statement.execute( " CREATE TABLE IF NOT EXISTS logs ( " +
                      " timeStart       varchar( 255 ), " + // timestamp in the first record of the log
                      " timeEnd         varchar( 255 ), " + // timestamp in the last record of the log
                      " timeModified    varchar( 255 ), " + // timestamp of last file change 
                      " usedForIPs   tinyint DEFAULT 0, " + // 1 if used in blocked IPs list making 
                      " fileName        varchar( 255 ), " + // file name
                      " site            varchar( 255 ), " + // site name
                      " size                    BIGINT, " + // file size
                      " index idxlogs( site, fileName ) )  " ) ;

   // maintains central information about each site
   statement.execute( " CREATE TABLE IF NOT EXISTS sites ( " +
	                  " id int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY, " + // site id
                      " weightsStart varchar( 255 ) DEFAULT NULL, " + // start of the interval covered by weights
                      " weightsEnd   varchar( 255 ) DEFAULT NULL, " + // end of the interval covered by weights
                      " scoresStart  varchar( 255 ) DEFAULT NULL, " + // start of the interval covered by scores
                      " scoresEnd    varchar( 255 ) DEFAULT NULL, " + // end of of the interval covered by scores
                      " site                      varchar( 255 ), " + // site name
                      " label                  text DEFAULT NULL, " + // if defined, shown in the tree instead of name
                      " title                  text DEFAULT NULL, " + // shown as a tip in the browser
                      " groupr                 text DEFAULT NULL, " + // list of groups having read access
                      " groupw                 text DEFAULT NULL, " + // list of groups having write access
                      " userr                  text DEFAULT NULL, " + // list of users having read access
                      " userw                  text DEFAULT NULL, " + // list of users having write access
                      " owners                 text DEFAULT NULL, " + // list of users having admin access
                      " nextid                     int DEFAULT 0, " + // id of the next node on the level
                      " config                 blob DEFAULT NULL, " + // serialised site configuration file
                      " index            idxsites( site ) )  " ) ;

   // a set of misc name-value pairs
   statement.execute( "CREATE TABLE IF NOT EXISTS system ( " +
                     " name    varchar( 255 ) NOT NULL, " +
                     " value   text NOT NULL, " +
                     " index   idxname ( name ) ) " ) ;

   // maintains list of index areas for each site and area
   statement.execute( " CREATE TABLE IF NOT EXISTS areas ( "    +
                     " site           varchar(256) NOT NULL, "  + // site name
                     " area           varchar( 30 ) NOT NULL, " + // area name
                     " lastIndexed    varchar( 255 ), "         + // last time this area was indexed
                     " status         int DEFAULT 0, "          + // completion status
                     " build          int default 0, "          + // build number
                     " index          idxareas( site, area ) )  " ) ;
  
   // maintains list of index seed urls and exclusions for each site and area
   statement.execute( " CREATE TABLE IF NOT EXISTS roots ( "    +
                     " site           varchar( 256 ), "         + // site name
                     " area           varchar( 30 ) NOT NULL, " + // area tag
                     " type           char(1), "                + // r - root, x - exclude, i - include
                     " path           text, "                   + // path where result is stored
                     " index          idxroots( site, area ) )  " ) ;

   // maintains security scan alerts
   statement.execute( " CREATE TABLE IF NOT EXISTS alerts ( "   +
                     " site           varchar( 256 ) NOT NULL, "+ // site name
                     " url            text NOT NULL, "          + // page url
                     " code           int, "                    + // alert code
                     " message        text, "                   + // alert message
                     " index          idxalerts( site ) )  " ) ;

   sqlInsertLogFile = db.prepareStatement(
	        "INSERT INTO logs(timeStart, timeEnd, timeModified, usedForIPs, fileName, site, size) " +
	                                                        " VALUES (?, ?, ?, ?, ?, ?, ?) " ) ;
   sqlReadLogFile = db.prepareStatement(
	        "SELECT timeStart, timeEnd, timeModified, usedForIPs, size " +
	                        " FROM logs WHERE fileName=? AND site=? " ) ;

   sqlAddAlert = db.prepareStatement(
	        "INSERT INTO alerts VALUES ( ?, ?, ?, ? )" ) ;
  }
 }

 /**
 * Connect to the database, prepare statements 
 */
 public void connect() throws Exception
 {
   super.connect() ;
   init() ;
 }

 /**
 * Register (a new) site
 * 
 * @param site  name of the site
 */
 public void regSite( String site, String baseURL ) throws Exception
 {
  String sql = "SELECT site FROM sites WHERE site = \'" + site + "\'"  ;
  ResultSet rs = read( sql ) ;
  if ( !rs.next() )
   {
	sql = "INSERT INTO sites (site, title, groupr, userr, owners) values( ?, ?, " +
			" 'public', 'guest', 'admin' )" ;
	PreparedStatement st = db.prepareStatement( sql ) ;
	st.setString( 1, site ) ;
	st.setString( 2, baseURL ) ;
	st.execute() ;
	st.close() ;
	int newId = readInt( " SELECT max(id) FROM sites " ) ;
	execute( "update sites set nextid = " + newId + " where nextid = 0 and id != " + newId ) ;
   }
  rs.close() ;
 }
 
 
 /**
  * Saves site configuration to the db
  * 
  * @param site  name of the site
  * @param cfg configuration object
  */
  public void writeConfig( String site, ConfigList cfg ) throws Exception
  {
	for ( int i = 0 ; i <= 1 ; i++ )
	{ try { writeConfig0( site, cfg ) ; return ; }
	  catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
	}
  }

	  
  /**
  * Saves site configuration to the db
  * 
  * @param site  name of the site
  * @param cfg configuration object
  */
  public void writeConfig0( String site, ConfigList cfg ) throws Exception
  {
    ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
    DataOutputStream out = new DataOutputStream( bos ) ;
    cfg.write( out ) ;
	String sql = "UPDATE sites set config = ? where site = ? " ;
    PreparedStatement st = db.prepareStatement( sql ) ;
    st.setString( 2, site ) ;
    ByteArrayInputStream bis = new ByteArrayInputStream( bos.toByteArray() ) ;
    st.setBinaryStream( 1, bis, bis.available() );
    st.executeUpdate() ;
    st.close() ;
    bos.close() ;
    out.close() ;
    bis.close() ;
  }

  
  /**
  * Reads site configuration from the db
  * 
  * @param site  name of the site
  * @return site configuration object
  */
  synchronized public ConfigList readConfig( String site ) throws Exception
  {
    for ( int i = 0 ; i <= 1 ; i++ )
	  { try { return readConfig0( site ) ; }
		  catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
	  }
    return null ;
  }
  
  /**
  * Reads site configuration from the db
  * 
  * @param site  name of the site
  * @return site configuration object
  */
  public ConfigList readConfig0( String site ) throws Exception
  {
    String sql = "select config from sites where site = ? " ;
	PreparedStatement st = db.prepareStatement( sql ) ;
	st.setString( 1, site ) ;
	ResultSet rs = st.executeQuery() ;
	if ( !rs.next() )
		throw new Exception( "Configuration is not found for site " + site ) ;
	InputStream bis = rs.getBinaryStream( 1 ) ;
    DataInputStream in = new DataInputStream( bis ) ;
    ConfigList cfg = ConfigList.newConfigList( in ) ;
    in.close() ;
    rs.close() ;
    st.close() ;
    return cfg ;
  }


 /**
 * Get date of a log processing point 
 * 
 * @param point   log processing point
 * @param site    site name
 * @return  Date of the log processing point
 */
 public Date getProcessed( String point, String site ) throws Exception
 {
    notServlet( "getProcessed()" ) ;
    String timestamp = readString( "select " + point + 
                " from sites where site=\'" + site + "\'" ) ;
    if ( timestamp == null ) return null ;
    else return DateFormat.getDateTimeInstance().parse( timestamp ) ;
  }

 /**
 * Write date of a log processing point 
 * 
 * @param point   log processing point
 * @param site    site name
 * @param value   date to write
 */ 
 public void putProcessed( String point, String site, Date value )
 throws Exception
 {
  notServlet( "putProcessed()" ) ;
  String dt = DateFormat.getDateTimeInstance().format( value ) ;
  String sql = "UPDATE sites SET " + point + "='" + dt +
                                      "' WHERE site=\'" + site + "\'" ; 
  execute( sql ) ;
 }
 
 /**
 * Delete info on log files for a given site
 * 
 * @param site    site name
 */ 
 public void deleteLogFiles( String site )
 throws Exception
 {
  notServlet( "deleteLogFiles()" ) ;
  execute( "DELETE FROM logs WHERE site='" + site + "'" ) ;
 }

 
 /**
 * Save a log file info. Re-connect if needed.
 * 
 * @param logFile log file object
 * @param site    site name
 */ 
 public void saveLogFile( LogFile logFile, String site )
 throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { saveLogFile0( logFile, site ) ; return ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 } 
 
 /**
 * Save a log file info
 * 
 * @param logFile log file object
 * @param site    site name
 */ 
 public void saveLogFile0( LogFile logFile, String site )
 throws Exception
 {
   notServlet( "saveLogFile()" ) ;
   String     fileName = logFile.getFileName() ;
   String    timeStart = DateFormat.getDateTimeInstance().format( logFile.getTimeStart() ) ;
   String      timeEnd = DateFormat.getDateTimeInstance().format( logFile.getTimeEnd() ) ;
   String timeModified = DateFormat.getDateTimeInstance().format( logFile.getTimeModified() ) ;
   int usedForIPs = logFile.isUsedForIPs() ? 1 : 0 ; 
   long size = logFile.getSize() ; 
   //timeStart, timeEnd, timeModified, usedForIPs, fileName, site
   sqlInsertLogFile.setString( 1, timeStart ) ;
   sqlInsertLogFile.setString( 2, timeEnd ) ;
   sqlInsertLogFile.setString( 3, timeModified ) ;
   sqlInsertLogFile.setInt( 4, usedForIPs ) ;
   sqlInsertLogFile.setString( 5, fileName ) ;
   sqlInsertLogFile.setString( 6, site ) ;
   sqlInsertLogFile.setLong( 7, size ) ;
   sqlInsertLogFile.execute() ;
 }
 
 public boolean readLogFile( LogFile logFile, String site )
 throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { return readLogFile0( logFile, site ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return false ;
 } 
 /**
 * Reads log file info 
 * 
 * @param logFile logFile object, must contain log file name
 * @param site    site name
 * @return  true if log file was found, else false
 */
 public boolean readLogFile0( LogFile logFile, String site )
 throws Exception
 {
   notServlet( "readLogFile()" ) ;
   String fileName   = logFile.getFileName() ;
   // timeStart, timeEnd, timeModified, usedForIPs, fileName, site 
   sqlReadLogFile.setString( 1, fileName ) ;
   sqlReadLogFile.setString( 2, site ) ;
   ResultSet rs = sqlReadLogFile.executeQuery() ;
   if ( rs.next() )
    {
      logFile.setTimeStart( DateFormat.getDateTimeInstance().parse( rs.getString( 1 ) ) ) ;
      logFile.setTimeEnd( DateFormat.getDateTimeInstance().parse( rs.getString( 2 ) ) ) ;
      logFile.setTimeModified( DateFormat.getDateTimeInstance().parse( rs.getString( 3 ) ) ) ;
      logFile.setUsedForIPs( rs.getInt( 4 ) == 1 ) ;
      logFile.setSize( rs.getLong( 5 ) ) ;
      return true ;
    }
   rs.close() ;
   return false ;
 }
 
 /**
 * Deletes info on log processing points for a given site
 * 
 * @param site    site name
 */
 public void resetWeights( String site )
 throws Exception
 {
   notServlet( "resetWeights()" ) ;
   execute( "DELETE FROM sites WHERE site='" + site + "'" ) ;
 }
 
 /**
 * For a given site, for each log file, deletes info on whether the log file
 * was used to construct the list of blocked IPs
 * 
 * @param site    site name
 */
 public void resetIPs( String site ) throws Exception
 {
   notServlet( "resetIPs()" ) ;
   execute( "UPDATE logs set usedForIPs=0 WHERE site='" + site + "'" ) ;
 }
 
 /**
 * Drops all central tables 
 */
 public void resetAll()
 throws Exception
 {
   notServlet( "resetAll()" ) ;
   execute( "DROP TABLE IF EXISTS sites" ) ;
   execute( "DROP TABLE IF EXISTS areas" ) ;
   execute( "DROP TABLE IF EXISTS logs" ) ;
   execute( "DROP TABLE IF EXISTS system" ) ;
   execute( "DROP TABLE IF EXISTS roots" ) ;
   init() ;
 }
 
 public void notServlet( String function ) throws Exception
 {
  if ( servletMode )
     throw new Exception( "Function " + function + " of " + this.getClass().getName() + 
                          " is not intended to be used in servlet mode." ) ; 
 }

 
  /**
  *  Closes the database connection, if it is open.
  */
 public void close() throws Exception { if ( db != null ) db.close() ; db = null ; }  

 //=============================================================================
 //                         Functions used from servlets
 //=============================================================================
 
 
 public IndexInfo getIndexInfo() throws Exception
 {
   if ( info != null ) return info ;
   info = new IndexInfo() ;
   ResultSet rs = read( "select site, area from areas" ) ;
   while( rs.next() )
   {
     String site = rs.getString( 1 ) ;  
     String area = rs.getString( 2 ) ;
     if ( !area.equalsIgnoreCase( "LOGLINKS" ) )
                                   info.addArea( site, area ) ;
   }
   return info ;
 }

 /**
  *  Resets the cached IndexInfo object.
  */
 public void resetIndexInfo() { info = null ; }

 
 /**
  *  Constructs a html select element for site selection.
  *  
  *  @param selected   name of selected site that will be marked as SELECTED
  *  @return    text of select element ready to insert in a html file
  */
 public String getSiteSelect( String selected ) throws Exception
 {
  info = getIndexInfo() ;
  return getSelect( "ar_site", selected, info.getSites() ) ;
 }
  

 /**
  *  Constructs a html select element for area selection.
  *  
  *  @param selected   name of selected area that will be marked as SELECTED
  *  @return    text of select element ready to insert in a html file
  */
 public String getAreaSelect( String selected ) throws Exception
 {
  info = getIndexInfo() ;
  return getSelect( "ar_area", selected, info.getAllAreas() ) ;
 }
  
 /**
  *  Constructs a generic html select element based on a set of options.
  *  
  *  @param name       html name attribute of the select element
  *  @param selected   value of selected option that will be marked as SELECTED
  *  @param options    options (Strings)
  *  @return    text of select element ready to insert in a html file
  */
 public String getSelect( String name, String selected, ArrayList options ) throws Exception
 {
  StringBuilder buf = new StringBuilder( "<select name=\"" + name + "\" id=\"" + name + "\">\n" ) ;
  if ( selected == null )
            buf.append( "<option value=\"all\" SELECTED>All</option>\n" ) ;
       else buf.append( "<option value=\"all\">All</option>\n" ) ;
  buf.append( getOptions( selected, options ) ) ;
  buf.append( "</select>\n" ) ;
  return buf.toString() ;
 }
 
 /**
  *  Converts a set of strings into text containing html select options.
  *  
  *  @param selected   value of selected option that will be marked as SELECTED
  *  @param options    options (Strings)
  *  @return    text of select element ready to insert in a html file
  */
 public String getOptions( String selected, ArrayList options ) throws Exception
 {
  StringBuilder buf = new StringBuilder( "" ) ;
  for ( int i = 0 ; i < options.size() ; i++ )
   {
     String option = (String)options.get( i ) ;
     String Option = Utils.up( option ) ;
     if ( selected != null && selected.equalsIgnoreCase( option ) )
            buf.append( "<option value=\"" + option + "\" SELECTED>" + Option + "</option>\n" ) ;
       else buf.append( "<option value=\"" + option + "\">" + Option + "</option>\n" ) ;
   }
  return buf.toString() ;
 }
 
 /**
  *  Constructs a group select html select element where areas are groupped by site.
  *  
  *  @param selectedSite   name of site containing selected area, or null
  *  @param selectedArea   name if selected area, if selectedSite is not null
  *  @return    text of group select element ready to insert in a html file
  */
 public String getGroupSelect( String selectedSite, String selectedArea ) throws Exception
 {
  info = getIndexInfo() ;
  StringBuilder buf = new StringBuilder( "<select name=\"sitearea\">\n" ) ;
  if ( selectedSite == null )
            buf.append( "<option value=\"all:all\" SELECTED>All</option>\n" ) ;
       else buf.append( "<option value=\"all:all\">All</option>\n" ) ;
  Map sites = info.getSiteAreas() ;
  Iterator iterator = sites.keySet().iterator() ;
  while( iterator.hasNext() )
  {
    String site = (String)iterator.next() ;
    ArrayList areas = (ArrayList)sites.get( site ) ;
    buf.append( "<optgroup label=\"" + site + "\">\n" ) ;
    if ( selectedSite != null && selectedSite.equalsIgnoreCase( site ) )
       buf.append( getOptions( selectedArea, areas ) ) ;
       else buf.append( getOptions( null, areas ) ) ;
    buf.append( "</optgroup>\n" ) ;
  }
  buf.append( "</select>\n" ) ;
  return buf.toString() ;
 }
 
//==============================================================================
// Analogues of SiteDB functions for working with sites lists, emulating nodes
// Looks a bit of duplication here, but they are needed to allow storing of
// sites info in separate tables, which is good for efficiency and potential
// replication and parallel site processing (may or may not be implemented.)
//==============================================================================
 
 public IndexSiteDB getSiteDB( String site ) throws Exception
 {
  String rootDir = cfg.get( "arch.data.dir", "" ) ;
  String siteDir = rootDir + "/sites/" + site ;
  File dir = new File( siteDir ) ;
  ConfigList cfg = null ;
  if ( dir.exists() ) cfg = ConfigList.newConfigList( dir.getCanonicalPath() + 
                                                      "/config.txt", this.cfg ) ;
     else cfg = this.cfg ; // else this is a virtual site, use root database
  Configuration nutchConf = NutchConfiguration.create() ;
  DBInterfaceFactory factory = DBInterfaceFactory.get( nutchConf ) ;
  String database = cfg.getInherited( "database", "MySQL" ) ;
  DBInterface intrf = factory.get( database ) ;
  return intrf.newIndexSiteDB( cfg, site, false ) ;	 
 }
 
 public IndexNode getNode( String site ) throws Exception
 {
   int id = readInt( "select id from sites where site='" + site + "'" ) ;
   return getNode( id ) ;
 }
 
 public IndexNode getNode( int id ) throws Exception 
 {
  ResultSet rs = read( "select id, site, label, title, groupr, groupw, userr, " +
		  " userw, owners, nextid from sites where id = " + id ) ;
  if ( !rs.next() ) return null ;
  IndexNode node = readNode( rs ) ;
  rs.close() ;
  return node ;
 }

 public IndexNode readNode( ResultSet rs ) throws Exception
 {
  IndexNode node = IndexNode.newRootIndexNode() ;
  node.id = rs.getInt( 1 ) ; 
  node.name = rs.getString( 2 ) ;
  node.label = rs.getString( 3 ) ; 
  node.title = rs.getString( 4 ) ; 
  node.groupr = rs.getString( 5 ) ; 
  node.groupw = rs.getString( 6 ) ; 
  node.userr = rs.getString( 7 ) ; 
  node.userw = rs.getString( 8 ) ; 
  node.owners = rs.getString( 9 ) ; 
  node.nextId = rs.getInt( 10 ) ; 
  node.parentId = 0 ;
  node.site = node.name ;
  node.base = node.title ;
  return node ;
 }

 /**
  * Read all nodes on a level defined by parent id
  * 
  * @param parentId id of the parent
  *
  * @return IndexNode[] array of nodes on the level
  */
 public IndexNode[] readLevel( int exceptionId, boolean order ) throws Exception 
 {
  // How many nodes on the level?
  int num = readInt( "select count(*) from sites" ) ;
  HashSet ids = new HashSet() ;
  HashMap nodes = new HashMap() ;
  IndexNode[] level = new IndexNode[ num ] ;
  ResultSet rs = read( "select id, site, label, title, groupr, groupw, userr, " +
		                                     " userw, owners, nextid from sites" ) ;
  int i = 0 ;
  while( rs.next() )
   { 
     IndexNode node = readNode( rs ) ;
     if ( node.id == exceptionId ) num-- ;
      else { level[ i++ ] = node ;
             ids.add( Integer.valueOf( node.nextId ) ) ;
             nodes.put( Integer.valueOf( node.id ), node ) ;
           }
   }
  rs.close() ;
  
  IndexNode[] result = level ;
  if ( order )  // order nodes in the order of links
  {
	result = new IndexNode[ num ] ;
    for ( i = 0 ; i < num ; i++ )
     if ( !ids.contains( Integer.valueOf( level[ i ].id ) ) ) break ;
    if ( i == num )
     throw new Exception( "Can't find first node on the level." ) ;
    // Now we got the starting point
    int j = 0 ; IndexNode node = level[ i ] ;
    do
      { result[ j++ ] = node ;
	    node = (IndexNode)nodes.get( Integer.valueOf( node.nextId ) ) ;  
      } while( node != null ) ;
  }

  return result ;
 }

 
 public void updateNode( IndexNode node ) throws Exception
 {
  PreparedStatement st = db.prepareStatement( " update sites set label=?, " +
  "title=?, groupr=?, groupw=?, userr=?, userw=?, owners=?, nextid=? where id=? " ) ;
    
  st.setString( 1, node.label ) ; 
  st.setString( 2, node.title ) ; 
  st.setString( 3, node.groupr ) ; 
  st.setString( 4, node.groupw ) ; 
  st.setString( 5, node.userr ) ; 
  st.setString( 6, node.userw ) ; 
  st.setString( 7, node.owners ) ; 
  st.setInt( 8, node.nextId ) ; 
  st.setInt( 9, node.id ) ; 
  st.execute() ;
  
  // replicate changes to site table
  IndexSiteDB siteDB = getSiteDB( node.name ) ;
  IndexNode rootNode = siteDB.getNode( 1, null, null, null ) ;
  rootNode.name = node.name ;
  rootNode.label = node.label ;
  rootNode.title = node.title ;
  rootNode.groupr = node.groupr ;
  rootNode.groupw = node.groupw ;
  rootNode.userr = node.userr ;
  rootNode.userw = node.userw ;
  rootNode.owners = node.owners ;
  siteDB.updateNode( rootNode ) ;
  siteDB.close() ;  
 }
 
 public int insertNode( IndexNode node ) throws Exception
 {
   // check if exists	
   if ( readInt( "select id from sites where site='" + node.name + "'" ) > 0 )
	  throw new Exception( "This site already exists." ) ;
   if ( node.name.length() == 0 || !Utils.isProperName( node.name ) )
	  throw new Exception( "Invalid site name: " + node.name ) ;
	   
   regSite( node.label, node.name ) ;
   updateNode( node ) ;
   // Now have to create root node in site table 
   IndexSiteDB siteDB = getSiteDB( node.name ) ;
   IndexNode rootNode = siteDB.getNode( 1, null, null, null ) ;
   rootNode.name = node.name ;
   rootNode.label = node.label ;
   rootNode.title = node.title ;
   rootNode.groupr = node.groupr ;
   rootNode.groupw = node.groupw ;
   rootNode.userr = node.userr ;
   rootNode.userw = node.userw ;
   rootNode.owners = node.owners ;
   siteDB.updateNode( rootNode ) ;
   siteDB.close() ;
   return readInt( "select id from sites where site='" + node.name + "'" ) ;
 }
 
 public void unlinkNode( int id ) throws Exception
 {
   IndexNode node = getNode( id ) ;
   execute( "update sites set nextid=" + node.nextId + " where nextid = " + node.id ) ;
 }
 
 public void linkNode( int id, int before ) throws Exception
 {
   execute( "update sites set nextid=" + id + " where nextid = " + before + " and id !=" + id  ) ;
   execute( "update sites set nextid=" + before + " where id = " + id ) ;
 }
 
 public void deleteNode( int id ) throws Exception 
 {
   IndexNode node = getNode( id ) ;
   unlinkNode( id ) ;
   execute( "delete from sites where id=" + id ) ; 
   execute( "delete from logs where site='" + node.name + "'" ) ;
   execute( "delete from roots where site='" + node.name + "'" ) ;
   execute( "delete from areas where site='" + node.name + "'" ) ;
   // now delete this site completely
   IndexSiteDB siteDB = getSiteDB( node.name ) ;
   siteDB.execute( "DROP TABLE IF EXISTS " + node.name ) ;
   String siteDir = cfg.get( "arch.data.dir",  "" ) + "/sites/" + node.name ;
   File dir = new File( siteDir ) ;
   if ( dir.exists() ) Utils.rmdir( siteDir, null ) ;
 }

 //=============================================================================
 //      Functions used by servlets: ServletDB interface implementation
 //=============================================================================
  
 /**
  *  Move node on the level.
  *  
  *  @param id int id of the node
  *  @param before int id of the node to place this node before or 0 if to place last 
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return true if success, false if moving is not permitted
  */
 public boolean moveNode( int id, int before, String user, String groups ) throws Exception
 {
  // several operations, need a transaction
  try { 
        IndexNode nodeBefore = null ; 
        IndexNode node = getNode( id ) ;
        if ( !node.canWrite( user, groups ) ) return false ;
        begin() ;
        if ( before > 0 ) // include inside level, else insert as the first one
                 // SET nextid=? WHERE parentid=? AND nextid = 0 AND id != ? "
         {
           nodeBefore = getNode( before ) ;
           if ( nodeBefore.parentId != node.parentId || id == before )
            { rollback() ; return false ; } // ignore attempts to move to another level
         }
        unlinkNode( id ) ;
        linkNode( id, before ) ;
        commit() ;
      } catch( Exception e )
      { rollback() ;
        throw e ;
      }
   return true ;
 }
 
 /**
  *  Delete node with its subtree.
  *  
  *  @param id int id of the node
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return true if success, false if deletion is not permitted
  */
 public boolean deleteNode( int id, String user, String groups ) throws Exception
 {
   try { execute( "BEGIN" ) ;
         if ( !deleteNodeR( id, user, groups ) )
           { execute( "ROLLBACK" ) ; return false ; }
         execute( "COMMIT" ) ;
       } catch( Exception e ) 
       { execute( "ROLLBACK" ) ; throw e ; }
   return true ;
 }
 
 
 public boolean deleteNodeR( int id, String user, String groups ) throws Exception
 {
   IndexNode node = getNode( id ) ;
   if ( !node.canWrite( user, groups ) ) return false ;
   deleteNode( id ) ;
   return true ;
 }
  
 /**
  *  Read level of nodes defined either by parent id or id of one of the nodes.
  *  
  *  @param id1 ignored (is there to comply with DBServlet interface)
  *  @param id2 ignored
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return serialized in String level or null if reading is not permited
  */
 public IndexNode[] readLevel( int id1, int id2, String user, String groups ) throws Exception
 {
  String[] groupsA = groups.split( " " ) ;
  IndexNode[] level = readLevel( -1, true ) ;
  for ( int i = 0 ; i < level.length ; i++ )
    if ( level[ i ] != null && !level[ i ].canRead( user, groupsA ) ) level[ i ] = null ;
  return level ;
 } 
 
 /**
  *  Read node info.
  *  
  *  @param id int id of the node
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return serialized in String node or null if reading is not permited
  */
 public IndexNode readNode( int id, String user, String groups ) throws Exception 
 {
  IndexNode node = getNode( id ) ;
  if ( node.canRead( user, groups ) ) return node ;
  return null ;
 } 
 
 /**
  *  Update node info.
  *  
  *  @param id int id of the node
  *  @param info serialized node info
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return true if success, false if operation is not permitted
  */
 public boolean updateNode( IndexNode node, String user, String groups ) throws Exception
 {
   // can only update a node if it is writeable, change permissions if admin
   IndexNode old = getNode( node.getId() ) ;
   if ( !old.canWrite( user, groups ) ) return false ;
   if ( !old.isAdmin( user, groups ) ) // have to be admin to change permissions
      if ( !Utils.sameText( node.groupr, old.groupr ) ||
	       !Utils.sameText( node.groupw, old.groupw ) ||
	       !Utils.sameText( node.userr, old.userr ) ||
	       !Utils.sameText( node.userw, old.userw ) ||
	       !Utils.sameText( node.access, old.access ) ||
	       !Utils.sameText( node.owners, old.owners ) ) return false ;
   node.nextId   = old.nextId ;
   node.parentId = old.parentId ;
   node.name = old.name ;
   updateNode( node ) ;
   return true ;
 } 
 
 /**
  *  Insert a new node.
  *  
  *  
  *  @param parentId ignored
  *  @param info serialized node info
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *  
  *  @return int id of the new node or -1 if operation is not permitted
  */
 public int insertNode( int parentId, IndexNode node, String user, String groups ) throws Exception
 {
   String[] groupsA = groups.split( " " ) ;
   IndexNode[] level = readLevel( -1, true ) ;
   int i = 0 ;
   for ( i = 0 ; i < level.length ; i++ )
     if ( level[ i ].isAdmin( user, groupsA ) ) break ;
   if ( i == level.length ) return -1 ; // has to be admin of at least one node
   return insertNode( node ) ;
 }
  
 /**
  *  Return base URL of the site.
  *
  *  @param String site name
  *  
  *  @return String site base URL
  */
 public String getURL( String site ) throws Exception
 {
   return readString( "select label from sites where site ='" + site + "'" ) ;	 
 }
 
 public static void main( String args[] )
 {
  try	 
  { 
    String configPath = args[0] ;
    ConfigList cfg = ConfigList.newConfigList( configPath ) ;
	Configuration nutchConf = NutchConfiguration.create() ;
	DBInterfaceFactory factory = DBInterfaceFactory.get( nutchConf ) ;
	String database = cfg.getInherited( "database", "MySQL" ) ;
    DBInterface intrf = factory.get( database ) ;
	  
	IndexRootDB db = intrf.newIndexRootDB( cfg, false ) ;
	 
    String user = "person", groups = "other" ;
// These should fail because of blocked access
	boolean r = false, r1 = false, r2 = false, r3 = false, r4 = false ;
	IndexNode node = db.readNode( 1, user, groups ) ;
	if ( node != null ) 
		{ node.setName( "This is my new name" ) ;
	      r = db.updateNode( node, user, groups ) ;
		}
	r2 = db.moveNode( 1, 2, user, groups ) ;
	r3 = db.moveNode( 2, 1, user, groups ) ;
	int newid = db.insertNode( -1, node, user, groups ) ;
	if ( newid > 0 ) r4 = db.deleteNode( newid, user, groups ) ;
	
	if ( node != null || r || r1 || r2 || r3 || r4 || newid > 0 )
		throw new Exception( "Some actions succeeded, access control is broken." ) ;
		
// These should succeed
	user = "admin" ; groups = "public" ;
	node = db.readNode( 1, user, groups ) ;
	IndexNode[] level1 = db.readLevel( 52, -1, user, groups ) ;
	String oldTitle = node.getTitle() ;
	node.setTitle( "This is my new title" ) ;
	r = db.updateNode( node, user, groups ) ;
	node.setTitle( oldTitle ) ;
	r1 = db.updateNode( node, user, groups ) ;
	r2 = db.moveNode( 1, 2, user, groups ) ;
	r3 = db.moveNode( 2, 1, user, groups ) ;
	node.setName( "newSite" ) ; node.setTitle( "newSite title" ) ;
	newid = db.insertNode( -1, node, user, groups ) ;
	r4 = db.deleteNode( newid, user, groups ) ;
	r2 = db.deleteNode( 1, user, groups ) ;
	if ( !r || !r1 || !r2 || !r3 || !r4 || newid < 1 )
		throw new Exception( "Test failed." ) ;
	
  } catch( Exception e )
  {
    System.out.print( e.getMessage() ) ;
    e.printStackTrace() ;
  }
 }
 
 /**
  * Reads values related to this area from the database
  *
  * @param area area object to fill
  * 
  * @throws Exception
  */
 public void readDbValues( IndexArea area ) throws Exception 
 {
   notServlet( "readDbValues()" ) ;
   String sql = "select lastIndexed from areas " + area.getAreaClause() ;
   Statement st = db.createStatement() ;
   if ( LOG.isTraceEnabled() )
	   LOG.trace( "SQL: " +sql ) ;

   ResultSet res = st.executeQuery( sql ) ;
   
   if ( res.next() )
    {
      area.setLastIndexed( DateFormat.getDateTimeInstance().parse( res.getString( 1 ) ) ) ;
    } else // default values
      area.setLastIndexed( new Date( new Date().getTime() - 1000 * 3600 * 24 * 365 * 100 ) ) ;
   
   area.setOldExclusions( readStrings(  
           "select path from roots" + area.getAreaClause() + " and type='x'" ) );
   area.setOldInclusions( readStrings(  
           "select path from roots" + area.getAreaClause() + " and type='i'" ) );
   area.setOldRoots( readStrings(  
           "select path from roots" + area.getAreaClause() + " and type='r'" ) );
   area.setBuildNumber( readInt( "select build from areas " + area.getAreaClause() ) ) ;
   area.setMarkedForCrawling( readInt( "select status from areas " + area.getAreaClause() ) ) ;
 }
 

  /**
  * Writes area data to the database
  *
  * @param area area object to save
  * @param arraysToo    if true, write roots, exclusions and inclusions
  * 
  * @throws Exception
  */
 public void writeDbValues( IndexArea area, boolean arraysToo ) throws Exception 
 {
   notServlet( "writeDbValues()" ) ;
   write( 
     "update areas set lastIndexed='" +
         DateFormat.getDateTimeInstance().format( area.getLastIndexed() ) + 
                    "', build=" + area.getBuildNumber() + ", status=" + 
                    area.getMarkedForCrawling() + area.getAreaClause(),
     "insert into areas ( lastIndexed, build, site, area, status ) values ('" + 
     DateFormat.getDateTimeInstance().format( area.getLastIndexed() ) + 
     "', " + area.getBuildNumber()
     + ", '" + area.getSite().getName() + "', '" + area.getName() + "', "
     + area.getMarkedForCrawling() + ") " ) ;
   
   if ( arraysToo )
    {
      execute( "delete from roots" + area.getAreaClause() ) ;
      write( area.getOldInclusions(),
        "insert into roots ( site, area, type, path ) values " +
        "( '" + area.getSite().getName() + "', '" + area.getName() + "', 'i', ",")" ) ;
      write( area.getOldExclusions(),
        "insert into roots ( site, area, type, path ) values " +
        "( '" + area.getSite().getName() + "', '" + area.getName() + "', 'x', ",")" ) ;
      write( area.getOldRoots(),
        "insert into roots ( site, area, type, path ) values " +
        "( '" + area.getSite().getName() + "', '" + area.getName() + "', 'r', ",")" ) ;
    }   
 }
 
 
 /**
  * Marks all area marked for crawling as indexed. Sets index time and build number.
  *
  * @param buildNumber - build number to set
  * 
  * @throws Exception
  */ 
 public void markIndexed( int buildNumber ) throws Exception
 {
   for ( int i = 0 ; i <= 1 ; i++ )
   { try { markIndexed0( buildNumber ) ; return ; }
      catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
   }
 }
 

 /**
  * Marks all area marked for crawling as indexed. Sets index time and build number.
  *
  * @param buildNumber - build number to set
  * 
  * @throws Exception
  */ 
 public void markIndexed0( int buildNumber ) throws Exception
 {
   notServlet( "markIndexed()" ) ;
   String sql = "update areas set lastIndexed='" +
                  DateFormat.getDateTimeInstance().format( new Date() ) + 
                   "', build=" + buildNumber + ", status=0 where status=1 " ;
   execute( sql ) ;
 }
  

 /**
  * Marks given area for crawling. This mark is used in filters.
  *
  * @param site site name
  * @param area area name
  * 
  * @throws Exception
  */ 
 public void markForCrawl( String site, String area, int value ) throws Exception
 {
   for ( int i = 0 ; i <= 1 ; i++ )
	 { try { markForCrawl0( site, area, value ) ; return ; }
	      catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
	 }
 }
 
 
 /**
  * Marks given area for crawling. This mark is used in filters.
  *
  * @param site site name
  * @param area area name
  * 
  * @throws Exception
  */ 
 public void markForCrawl0( String site, String area, int value ) throws Exception
 {
   notServlet( "markForCrawl()" ) ;
   String clause = "" ;
   if ( site != null ) clause = "site='" + site + "'" ;
   if ( area != null ) if ( site != null ) clause += " and area='" + area + "'";
                                      else clause = " area='" + area + "'";
   if ( clause.length() > 0 ) clause = " where " + clause ;
   execute( "update areas set status=" + value + clause ) ;
 }
 

 /**
  * Reads roots, includes and excludes of marked areas to a Map
  *
  * @return a map of root, include and exclude paths for all areas marked for crawling
  * @throws Exception
  */ 
 synchronized public Map readRoots() throws Exception
 {
   for ( int i = 0 ; i <= 1 ; i++ )
       { try { return readRoots0() ; }
          catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
       }
   return null;
 }
 
 /**
  * Reads roots, includes and excludes of marked areas to a Map
  *
  * @return a map of root, include and exclude paths for all areas marked for crawling
  * @throws Exception
  */ 
 synchronized public Map readRoots0() throws Exception
 {
   notServlet( "readRoots()" ) ;
   String sql = "select r.site, r.area, r.path, r.type from roots r, areas a where" +
	                      " r.area=a.area and r.site=a.site and a.status=1 " ;
   Statement st = db.createStatement() ;
   if ( LOG.isTraceEnabled() )
		                    LOG.trace( "SQL: " +sql ) ;
   HashMap<String, ArrayList<String>[]> map = new HashMap<String, ArrayList<String>[]>() ;
   ResultSet res = st.executeQuery( sql ) ;
   while( res.next() )
   {
	 String site = res.getString( 1 ) ;
	 String area = res.getString( 2 ) ;
	 String path = res.getString( 3 ) ;
	 String type = res.getString( 4 ) ;
	 String key = site + '\n' + area ;
	 ArrayList<String>[] paths = map.get( key ) ;
	 if ( paths == null )
	 {
	   paths = (ArrayList<String>[])(new ArrayList[ 3 ]) ;
	   paths[ 0 ] = new ArrayList<String>() ;
	   paths[ 1 ] = new ArrayList<String>() ;
	   paths[ 2 ] = new ArrayList<String>() ;
	   map.put( key, paths ) ;
	 }
	 switch( type.charAt( 0 ) )
	   {
	     case 'x' : paths[ 0 ].add( path ) ; break ;
	     case 'i' : paths[ 1 ].add( path ) ; break ;
	     case 'r' : paths[ 2 ].add( path ) ; break ;
	     default: throw new Exception( "Undefined path type in readRoots: " + type ) ;
	   }
   }
   res.close() ;
   return map ;
 }
 
 // Scan alerts related functions
 
 /**
  * Erases alerts table, reconnects if needed
  *
  * @throws Exception
  */ 
 public void eraseAlerts() throws Exception
 {
   notServlet( "eraseAlerts()" ) ;
   execute( "delete from alerts" ) ;
 }
  

 /**
  * Adds a scan alert to the alerts table, reconnects if needed
  *
  * @param alert to add
  *  
  * @throws Exception
  */ 
 public synchronized void addAlert( ScanAlert alert ) throws Exception
 {
   for ( int i = 0 ; i <= 1 ; i++ )
   { try { addAlert0( alert ) ; return ; }
      catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
   }
 }
 

 /**
  * Adds a scan alert to the alerts table
  *
  * @param alert to add
  *  
  * @throws Exception
  */ 
 public void addAlert0( ScanAlert alert ) throws Exception
 {
   notServlet( "addAlert()" ) ;
   sqlAddAlert.setString( 1, alert.getSite() ) ;
   sqlAddAlert.setString( 2, alert.getUrl() ) ;
   sqlAddAlert.setInt( 3, alert.getCode() ) ;
   sqlAddAlert.setString( 4, alert.getMessage() ) ;
   sqlAddAlert.execute() ;
 }
  

 /**
  * Reads scan alerts for a site ordered by site, then url, then code.
  * Reconnects if needed.
  *
  * @param site name or null if read for all sites
  * @return ResultSet for iteration using nextAlert 
  *  
  * @throws Exception
  */ 
 public synchronized ResultSet readAlerts( String site ) throws Exception
 {
   String sql = "SELECT * from alerts" ;
   if ( site != null ) sql += " WHERE site = '" + site + "'" ;
   sql += " ORDER BY site, url, code desc " ;
   return read( sql ) ;
 }

 
 /**
  * Returns next scan alert.
  *
  * @param ResultSet for iteration using nextAlert
  * @return scan alert 
  *  
  * @throws Exception
  */ 
 public synchronized ScanAlert nextAlert( ResultSet alerts ) throws Exception
 {
   if ( alerts.next() )
   {
	 String site = alerts.getString( 1 ) ;  
	 String url = alerts.getString( 2 ) ;  
	 int code = alerts.getInt( 3 ) ;  
	 String message = alerts.getString( 4 ) ;
     return new ScanAlert( site, url, ScanAlert.codes[ code ], message ) ;
   } else { alerts.close() ; return null ; }
 }
  

 
}


