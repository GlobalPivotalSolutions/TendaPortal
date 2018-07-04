/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.auth.Permissions;
import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.index.LogLinks;
import au.csiro.cass.arch.logProcessing.ScoreFile;
import au.csiro.cass.arch.security.ScanResult;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.URLSplit;
import au.csiro.cass.arch.utils.Utils;

/**
 * A set of db related functions needed by IndexSite and LogSite objects
 * 
 */
public class IndexSiteDBBaseImpl extends IndexDBBaseImpl implements IndexSiteDB
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexSiteDBBaseImpl.class ) ;

 // minutes before cached permissions values become invalid
 public final static long CACHE_LASTS = 10 ;  
 
 public PreparedStatement    sqlUpdateScore ; // update log score
 public PreparedStatement   sqlUpdateWeight ; // update weight by id
 public PreparedStatement  sqlUpdateWeight2 ; // update weight by path and name
 public PreparedStatement      sqlGetWeight ; // get weight
 public PreparedStatement sqlGetWeightByUrl ; // get weight by url
 public PreparedStatement        sqlPath2Id ; // get id by path and name
 public PreparedStatement     sqlUpdateNode ; // update node
 public PreparedStatement     sqlInsertNode ; // insert a new node
 public PreparedStatement        sqlGetById ; // get node by id
 public PreparedStatement      sqlGetByPath ; // get node by path
 public PreparedStatement           sqlLink ; // update links on the level to add a new node
 public PreparedStatement         sqlLastId ; // retrieve id of just inserted node
 public PreparedStatement     sqlCountLevel ; // count number of nodes on the level
 public PreparedStatement         sqlRelink ; // change ref to the next element on the level when deleting
 public PreparedStatement         sqlRemove ; // remove node
 public PreparedStatement       sqlGetLevel ; // read a level of nodes
 public String                sqlListScores ; // list counted scores
 public PreparedStatement      sqlSumScores ; // sum aliases scores
 public PreparedStatement      sqlSetStatus ; // set url status
 public PreparedStatement        sqlAddLink ; // add a link to links table
 public PreparedStatement     sqlRemoveLink ; // remove a link from the links table
 public PreparedStatement      sqlReadLinks ; // read links from this url
 public String                         site ; // site name
 public String                        table ; // site table name

 public String                          url ; // site base url
 
 public boolean servletMode ;
 
 /**
  * Constructor
  * 
  * @param cfg configuration parameters
  * 
  */
 public IndexSiteDBBaseImpl( ConfigList cfg )
 { super( cfg ) ; } ;
 
 /**
  * Connect to the db, prepare statements
  * 
  */ 
 public void connect() throws Exception 
 {
  super.connect() ;
  init() ;
 }

 /**
  * Create site table and root node, if needed
  * 
  */ 
 public void init() throws Exception
 {
  if ( !servletMode )
   {  
	     String sql = "CREATE TABLE IF NOT EXISTS site_" + site +  " ( " +
	       "id       int unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY, " + // URL id
	       "name     varchar(1000) NOT NULL, " +     // folder or file name, '/' for empty name
	       "label    text DEFAULT NULL, " + // if defined, shown in the tree instead of the name
	       "title    text DEFAULT NULL, " + // shown as a tip in the browser
	       "path     varchar(2000) NOT NULL, " + // path to this folder or file from the site root
	       "type     char(2) NOT NULL, "  +   
	       "access   char(1) NOT NULL, "  + 
	       "groupr   text DEFAULT NULL, " + // list of groups having read access
	       "groupw   text DEFAULT NULL, " + // list of groups having write access
	       "userr    text DEFAULT NULL, " + // list of users having read access
	       "userw    text DEFAULT NULL, " + // list of users having write access
	       "owners   text DEFAULT NULL, " + // list of users having admin access
	       "parentid int NOT NULL, "      + // id of the parent node
	       "nextid   int  DEFAULT 0, "    + // id of the next node on the level
	       "fetched  int DEFAULT 0, "     +
	       "errors   int DEFAULT 0, "     + 
	       "cached   bigint DEFAULT 0, "  +
	       "weight   float DEFAULT -1,"   +
	       "score    int DEFAULT 0, "     +        
	       "status   char(1) DEFAULT 'l',"+ // 'l' - found in logs, i - indexed        
	       "hasform  tinyint DEFAULT 0,"  + // 1 if the page has a form in it        
	       "hasscript tinyint DEFAULT 0," + // 1 if the page has a script in it        
	       "crc      bigint DEFAULT 0,"   + // CRC32 code of the page output        
	       "srccrc   bigint DEFAULT 0,"   + // CRC32 code of the page source 
           "aliasof     int DEFAULT 0, "  + // reference to canonical URL
	       "index " + site + "_idxparentid(parentid)," +
	       "index " + site + "_idxnextid(nextid), "    +
	       "index " + site + "_idxpathname(path, name)," +
           "index " + site + "_idxaliasof(aliasof)," +
	       "index " + site + "_idxparentidname(parentid, name))" ;
	    statement.execute( sql ) ; 
	         
	    sql = "CREATE TABLE IF NOT EXISTS links_" + site +  " ( " +
	       "id       int unsigned NOT NULL, " + // source URL id in the site table
	       "link     text NOT NULL, "         + // link target
	       "index " + site + "_idxlinkid( id )) " ;
	    statement.execute( sql ) ; 
	         
    sqlUpdateScore = db.prepareStatement( "UPDATE " + table + " SET score=score+? WHERE path=? AND name=? AND type='f'" ) ;
    sqlUpdateWeight = db.prepareStatement( "UPDATE " + table + " SET weight=? WHERE id=?" ) ;
    sqlUpdateWeight2 = db.prepareStatement( "UPDATE " + table + " SET weight=? WHERE path=? AND name=?" ) ;
    sqlGetWeight = db.prepareStatement( "SELECT weight FROM " + table + " WHERE id=?" ) ;
    sqlGetWeightByUrl = db.prepareStatement( "SELECT weight FROM " + table + " WHERE path=? and name=?" ) ;
    sqlPath2Id = db.prepareStatement( "SELECT id, status FROM " + table + " WHERE path=? AND name=?" ) ;
    sqlGetByPath = db.prepareStatement( "SELECT * FROM " + table + " WHERE path=? AND name=? AND type=?" ) ;
    sqlListScores = "SELECT id, path, name, weight, score, aliasof FROM " + table + " where type='f' order by id" ;
    sqlSumScores = db.prepareStatement( "SELECT SUM( score ), MAX( weight ) FROM " + table + " where aliasof=?" ) ;
    sqlSetStatus = db.prepareStatement( "UPDATE " + table + " SET status = ? WHERE id=?" ) ;
    sqlAddLink = db.prepareStatement( "INSERT into links_" + site + " values( ?, ? ) " ) ;
    sqlRemoveLink = db.prepareStatement( "DELETE from links_" + site + " where id = ? and link = ? " ) ;
    sqlReadLinks = db.prepareStatement( "SELECT link from links_" + site + " where id = ? " ) ;
   }
  
   sqlUpdateNode = db.prepareStatement( "UPDATE " + table + " SET  " +
	        " name=?, label=?, title=?, path=?, type=?, access=?, groupr=?, groupw=?, userr=?, userw=?," +
	        " owners=?, parentid=?, nextid=?, fetched=?, errors=?, cached=?, weight=?, score=?, status=?, " +
	        " hasform=?, hasscript=?, crc=?, srccrc=?, aliasof=? where id=? " ) ;
   sqlInsertNode = db.prepareStatement(
       "INSERT INTO " + table + "(name, label, title, path, type, access, groupr, groupw, userr, userw," +
                             "owners, parentid, nextid, fetched, errors, cached, weight, score, status, hasform," +
                             "hasscript, crc, srccrc, aliasof) values " +
                             "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" ) ;
   sqlLink = db.prepareStatement(
	           "UPDATE " + table + " SET nextid=? WHERE parentid=? AND nextid = 0 AND id != ? " ) ;
   sqlLastId = db.prepareStatement( "SELECT LAST_INSERT_ID() FROM  " + table + " " ) ;
   sqlGetById = db.prepareStatement( "SELECT * FROM " + table + " where id = ? " ) ;
   sqlCountLevel = db.prepareStatement( "SELECT count(*) FROM " + table + " WHERE parentid=? " ) ;
   sqlRelink = db.prepareStatement( "UPDATE " + table + " SET nextid=? where nextid=? " ) ;
   sqlRemove = db.prepareStatement( "DELETE FROM " + table + " where id=? " ) ;
   sqlGetLevel = db.prepareStatement( "SELECT * FROM " + table + " WHERE parentid=? " ) ;
   statement = db.createStatement() ;
   if ( !servletMode )
   {
	 // make sure the root node exists
	 IndexNode n = getNode( 1, "", "/", "d" ) ;
	 if ( n == null )
	   {
	     n = IndexNode.newRootIndexNode() ;
	     updateNode0( n ) ;
	   }
   }
  }
 
 /**
  * Close connection, release resources
  * 
  */ 
 public void close() throws Exception 
 {
  if ( sqlUpdateScore != null ) sqlUpdateScore.close() ;
  if ( sqlUpdateWeight != null ) sqlUpdateWeight.close() ;
  if ( sqlGetWeight != null ) sqlGetWeight.close() ;
  if ( sqlGetWeightByUrl != null ) sqlGetWeightByUrl.close() ;
  if ( sqlPath2Id != null ) sqlPath2Id.close() ;
  if ( sqlUpdateNode != null ) sqlUpdateNode.close() ;
  if ( sqlInsertNode != null ) sqlInsertNode.close() ;
  if ( sqlLink != null ) sqlLink.close() ;
  if ( sqlLastId != null ) sqlLastId.close() ;
  if ( sqlGetById != null ) sqlGetById.close() ;
  if ( sqlGetByPath != null ) sqlGetByPath.close() ;
  if ( sqlCountLevel != null ) sqlCountLevel.close() ;
  if ( sqlGetLevel != null ) sqlGetLevel.close() ;
  if ( sqlRelink != null ) sqlRelink.close() ;
  if ( sqlRemove != null ) sqlRemove.close() ;
  if ( sqlSetStatus != null ) sqlSetStatus.close() ;
  if ( sqlSumScores != null ) sqlSumScores.close() ;
  if ( statement != null ) statement.close() ;
  super.close() ;   
 }
 
 /**
  * Drop site table
  * 
  */ 
 public void resetAll() throws Exception  
 {
   notServlet( "resetAll()" ) ;
   execute( "DROP TABLE IF EXISTS " + table ) ;
   init() ;
 }

 /**
  * Read <url, score> pairs and export them to a file
  * 
  * @param file  output file
  */
 public void scores2file( ScoreFile file ) throws Exception  
 {
  notServlet( "scores2file()" ) ;
  ResultSet rs = listScores() ;
  while( rs.next() )
  {
   String path = rs.getString( 2 ) ;
   String name = rs.getString( 3 ) ;
   int score = rs.getInt( 5 ) ;
   if ( name.equals( "/" ) ) name = "" ;
   path += "/" + name ;
   file.put( path, score ) ;
  }
  rs.close() ;
 }

 /**
  * Init reading document scores from the db
  * 
  * @return ResultSet object to obtain scores from 
  */
 public ResultSet listScores() throws Exception  
 { 
  notServlet( "listScores()" ) ;
  ResultSet rs = read( sqlListScores ) ;
  return rs ;
 }
 
 /**
  * Read <url, score> pairs from a file and save them to the db
  * 
  * @param file  output file
  */
 public void file2scores( ScoreFile file ) throws Exception
 {
   notServlet( "file2scores()" ) ;
   String ln="" ;
   URLSplit split = null ;
   while( file.next() )
    {
     try {
           int score = file.getScore() ;
           String url = file.getURL() ;
           split = URLSplit.newURLSplit( url, split, "f" ) ;
           writeScore( split.path, split.name, score, true ) ;
         } catch( Exception e )
         {
           System.out.println( "Bad line: " + ln + " : " + e.getMessage() ) ;
         }
    }
 }

 /**
  * Read <url, score> pairs and export them to a file
  * 
  * @param file  output file
  */
 public void exportSiteMap( OutputStream o, String baseURL, float norm ) throws Exception  
 {
  notServlet( "exportSiteMap()" ) ;
  ResultSet rs = listScores() ;
  StringBuilder b = new StringBuilder() ;
  if ( baseURL.charAt( baseURL.length() - 1 ) == '/' ) baseURL = baseURL.substring( 0, baseURL.length() - 1 ) ;
  baseURL = Utils.htmlEncode( baseURL ) ;
  while( rs.next() )
  {
   String path = rs.getString( 2 ) ;
   String name = rs.getString( 3 ) ;
   float weight = rs.getFloat( 4 ) ;
   if ( weight < 0 ) weight = 0 ;
   if ( name.equals( "/" ) ) name = "" ;
   b.append( "  <url>\n    <loc>" ) ; b.append( baseURL ) ;
   b.append( Utils.htmlEncode( path ) ) ;
   b.append( '/' ) ; b.append( Utils.htmlEncode( name ) ) ; b.append( "</loc>\n"  ) ;
   b.append( "    <priority>" ) ; b.append( weight / norm ) ; b.append( "</priority>\n  </url>\n") ;
   o.write( b.toString().getBytes() ) ; b.setLength( 0 ) ;
  }
  rs.close() ;
 }

 /**
  * Read <url, score> pairs from a file and save them to the db
  * 
  * @param file  output file
  */
 public void importSiteMap( BufferedReader rd, String baseURL, float norm ) throws Exception
 {
   notServlet( "importSiteMap()" ) ;
   String ln="" ;
   String url = null ;
   String weight = null ;
   float w = 0 ;
   URLSplit split = null ;
   while( (ln = rd.readLine()) != null )
    {
	  ln = ln.trim() ;
	  if ( !ln.startsWith( "<loc>" ) && !ln.startsWith( "<priority>" ) ) continue ;
      try {
    	    if ( ln.startsWith( "<loc>" ) )
    	       { 
    	    	 if ( url != null )
    	    		 throw new Exception( "a location entry without matching priority entry." ) ;
    	    	 ln = ln.replaceAll( "<loc>", "" ) ;
                 ln = ln.replaceAll( "</loc>", "" ) ;
                 url = Utils.htmlDecode( ln ) ;
                 url = "/" + url.replace( baseURL, "" ) ;
                 split = URLSplit.newURLSplit( url, split, "f" ) ;
    	       } else
    	       {
      	    	 if ( weight != null )
    	    		 throw new Exception( "a priority entry without matching location entry." ) ;
    	    	 ln = ln.replaceAll( "<priority>", "" ) ;
                 weight = ln.replaceAll( "</priority>", "" ) ;
                 w = Float.parseFloat( weight ) ;
    	       }
    	    if ( url != null && weight != null )
    	    {
              writeWeight( split.path, split.name, w * norm, true ) ;
              url = weight = null ;
    	    }
          } catch( Exception e )
          {
            LOG.error( "Bad line: " + ln + " : " + e.getMessage() ) ;
          }
    }
   rd.close() ;
 }

 /**
  * Update or insert a document weight, re-connect if needed
  * 
  * @param path path to the document
  * @param name document name
  * @param weight    document weight
  * @param insert if true, a record will be created if no such document
  */
 public void writeWeight( String path, String name, float weight, boolean insert ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   writeWeight0( path, name, weight, insert ) ; return ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 }
 
 /**
  * Update or insert a document score
  * 
  * @param path path to the document
  * @param name document name
  * @param score    document score
  * @param insert if true, a record will be created if no such document
  */
 public void writeWeight0( String path, String name, float weight, boolean insert ) throws Exception 
 {
  //if ( url.indexOf( "http://" ) != 0 ) url = urlsBase + url ;
  notServlet( "writeWeight()" ) ;
  sqlUpdateWeight2.setString( 3, name ) ;
  sqlUpdateWeight2.setString( 2, path ) ;
  sqlUpdateWeight2.setFloat( 1, weight ) ;

  int num = sqlUpdateWeight2.executeUpdate() ; // try to update the score
  if ( num == 0 && insert ) // it does not exist yet, have to insert
   {
     makeSureExists( "f", path, name, false ) ; // create the url entry first
     sqlUpdateWeight2.executeUpdate() ; 
   }
 }
 

 /**
  * Update or insert a document score, re-connect if needed
  * 
  * @param path path to the document
  * @param name document name
  * @param score    document score
  * @param insert if true, a record will be created if no such document
  */
 public void writeScore( String path, String name, float score, boolean insert ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   writeScore0( path, name, score, insert ) ; return ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 }
 
 /**
  * Update or insert a document score
  * 
  * @param path path to the document
  * @param name document name
  * @param score    document score
  * @param insert if true, a record will be created if no such document
  */
 public void writeScore0( String path, String name, float score, boolean insert ) throws Exception 
 {
  //if ( url.indexOf( "http://" ) != 0 ) url = urlsBase + url ;
  notServlet( "writeScore()" ) ;
  sqlUpdateScore.setString( 3, name ) ;
  sqlUpdateScore.setString( 2, path ) ;
  sqlUpdateScore.setFloat( 1, score ) ;

  int num = sqlUpdateScore.executeUpdate() ; // try to update the score
  if ( num == 0 && insert ) // it does not exist yet, have to insert
   {
     makeSureExists( "f", path, name, false ) ; // create the url entry first
     sqlUpdateScore.setString( 3, name ) ;
     sqlUpdateScore.setString( 2, path ) ;
     sqlUpdateScore.setFloat( 1, score ) ;
     try {
     sqlUpdateScore.executeUpdate() ;
     }
     catch( Exception e )
     {
       name = null ;
     }
   }
 }
 
 /**
  * Update weight of a document, re-connect if needed
  * 
  * @param id   document id in the database
  * @param weight   weight value to set
  */
 public void updateWeight( int id, float weight ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { updateWeight0( id, weight ) ; return ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 }

 /**
  * Update weight of a document
  * 
  * @param id   document id in the database
  * @param weight   weight value to set
  */
 public void updateWeight0( int id, float weight ) throws Exception
 {
  notServlet( "updateWeight()" ) ;
  sqlUpdateWeight.setFloat( 1, weight ) ;
  sqlUpdateWeight.setInt( 2, id ) ;
  sqlUpdateWeight.executeUpdate() ;  
 }

 public void updateScore( String path, String name, int score ) throws Exception
 {
  
 }
 
 /**
  * Multiply scores in the db by a given koefficient
  * 
  * @param koeff    koefficient
  */
 public void normaliseWeights( float koeff ) throws Exception
 {
  notServlet( "normaliseWeights()" ) ;
  String sql = "UPDATE " + table + " SET weight=weight*" + koeff ; 
  execute( sql ) ;
 }

 /**
  * Reset document weights
  * 
  */
 public void resetWeights() throws Exception
 {
  notServlet( "resetWeights()" ) ;
  String sql = "UPDATE " + table + " SET weight=-1 " ;
  execute( sql ) ;
 }
 
 /**
  * Reset document scores
  * 
  */
 public void resetScores() throws Exception
 {
   notServlet( "resetScores()" ) ;
   String sql = "UPDATE " + table + " SET score=0 " ;
   execute( sql ) ;
 }
 
 /**
  * Obtain max document weight value from the db
  * 
  * @return max weight 
  */
 public float getMaxWeight() throws Exception
 {
  notServlet( "getMaxWeight()" ) ;
  String sql = "SELECT max(weight) FROM " + table ;
  ResultSet rs = read( sql ) ;
  float maxWeight = -1f ;
  if ( rs.next() ) maxWeight = rs.getFloat( 1 ) ;
  rs.close();
  return maxWeight ;
 }
 
 /**
  * Obtain max document score value from the db
  * 
  * @return max weight 
  */
 public int getMaxScore() throws Exception
 {
  notServlet( "getMaxScore()" ) ;
  String sql = "SELECT max(score) FROM " + table ; 
  ResultSet rs = read( sql ) ;
  int maxScore = -1 ;
  if ( rs.next() ) maxScore= rs.getInt( 1 ) ;
  rs.close();
  return maxScore ;
 }
 
 synchronized public float getWeight( int id ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { return getWeight0( id ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return 0.1f ;
 }
 
 /**
  * Read weight of a document
  * 
  * @param id db id of the document
  * @return document weight
  */  
 public float getWeight0( int id ) throws Exception
 {
   float weight = 0.1f ;
   notServlet( "getWeight()" ) ;

   try { 
         sqlGetWeight.setInt( 1, id ) ;
         ResultSet rs = sqlGetWeight.executeQuery() ;
         if ( rs.next() ) weight = rs.getFloat( 1 ) ;
         rs.close();
       } catch ( Exception e )
       {
      	 LOG.error("Failed to get weight for id " + id + ": " + e.getMessage() ) ;
       }
       
   return weight ;
 }
 
 
 /**
  * Read weight of a document
  * 
  * @param url url of the document
  * @return document weight
  */  
 synchronized public float getWeight( String url ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { return getWeight0( url ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return 0.1f ;
 }

 
 /**
  * Read weight of a document
  * 
  * @param url url of the document
  * @return document weight
  */  
 synchronized public float getWeight0( String url ) throws Exception
 {
   float weight = 0.1f ;
   notServlet( "getWeight()" ) ;

   try { 
         URLSplit split = URLSplit.newURLSplit( url, null, "f" ) ;
         sqlGetWeightByUrl.setString( 1, split.path ) ;
         sqlGetWeightByUrl.setString( 2, split.name ) ;
	     if ( LOG.isTraceEnabled() )
			   LOG.trace( "SQL: " + sqlGetWeightByUrl.toString() ) ;

         ResultSet rs = sqlGetWeightByUrl.executeQuery() ;
         if ( rs.next() ) weight = rs.getFloat( 1 ) ;
         rs.close();
       } catch ( Exception e )
       {
    	 LOG.error("Failed to get weight for " + url + ": " + e.getMessage() ) ;
       }
       
   return weight ;
 }
 
 /**
  * Create a document record + all ancestors, if it does not exist
  * 
  * @param url url of the document
  * @param markIndexed if true, mark this document as indexed
  * @return document IndexNode
  */   
 synchronized public IndexNode makeSureExists( String url, boolean markIndexed ) throws Exception
 {
  URLSplit split = URLSplit.newURLSplit( url, null, "f" ) ;
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  return makeSureExists0( "f", split.path, split.name, markIndexed ) ; }
	 catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ;
 }
 
 /**
  * Create a document record + all ancestors, if it does not exist, re-connect if needed
  * 
  * @param type type of the node
  * @param path path to the node
  * @param name name of the node
  * @param markIndexed if true, mark this document as indexed
  * @return document IndexNode
  */   
 public IndexNode makeSureExists( String type, String path, String name, boolean markIndexed )
 throws Exception
 {
  int aa = 1 ;   
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  return makeSureExists0( type, path, name, markIndexed ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ; 
 }

 /**
  * Create a document or folder record + all ancestors, if it does not exist 
  * 
  * @param type type of the node
  * @param path path to the node
  * @param name name of the node
  * @param markIndexed if true, mark this document as indexed
  * @return document IndexNode
  */   
 public IndexNode makeSureExists0( String type, String path, String name, boolean markIndexed )
 throws Exception 
 {
  notServlet( "makeSureExists()" ) ;
  IndexNode node = getNode( 0, path, name, type ) ;
  if ( node == null )
     node = insertCascade( path, name, type ) ;
  if ( markIndexed && !node.status.equals( "i" ) )
  {
    sqlSetStatus.setString( 1, "i" ) ;
    sqlSetStatus.setInt( 2, node.id );
    sqlSetStatus.execute();
    node.status = "i" ;
  }
  return node ;
 }
 
 
 public IndexNode registerAlias( String url1, String url2, boolean markIndexed )
 		throws Exception
 {
	 IndexNode node1 = this.makeSureExists( url1, markIndexed ) ;
	 IndexNode node2 = this.makeSureExists( url2, markIndexed ) ;
	 if ( node1.aliasof == 0 && node2.aliasof == 0 )
	 {
	   node1.aliasof = node2.aliasof = Math.min( node1.id, node2.id ) ;
	   this.updateNode( node1 ) ;
	   this.updateNode( node2 ) ;
	 } else if ( node1.aliasof == 0 )
	 {
	   addAlias( node2, node1 ) ;
	 } else if ( node2.aliasof == 0 )
	 {
	   addAlias( node1, node2 ) ;	 
	 } else if ( node1.aliasof != node2.aliasof ) // are aliases but belong to different chains
	 { // merge chains
	   // this may happen if merging signature equivalent chain with canonical equivalent chain
	   int minId = Math.min( node1.aliasof, node2.aliasof ) ;
	   String sql = "UPDATE site_" + site + " SET aliasof=" + minId +
           " WHERE aliasof=" + node1.aliasof + " OR aliasof=" + node2.aliasof  ;
       statement.execute( sql ) ;
	 }
	 
	 return node1 ;
 }
 
 
 void addAlias( IndexNode chain, IndexNode newAlias ) throws Exception
 {
   newAlias.aliasof = chain.aliasof ;
   updateNode( newAlias ) ;
   
   if ( chain.aliasof > newAlias.id )
	{
	   String sql = "UPDATE site_" + site + " SET aliasof=" + newAlias.id +
		            " WHERE aliasof=" + chain.aliasof ;
	   statement.execute( sql ) ;
	} 	 
 }


 void removeAlias( IndexNode chain, boolean nodeToo ) throws Exception
 {
	ResultSet res = statement.executeQuery( "SELECT MIN( id ), count(*) FROM site_" + site +
			" WHERE aliasof=" + chain.aliasof + " AND id!=" + chain.id ) ;
	int min, count ;
	if ( res.next() )
	{
	  min = res.getInt( 1 ) ;
	  count = res.getInt( 2 ) ;
	} else throw new Exception( "No result." ) ;
	
	if ( count <= 1 ) min = 0 ;
	statement.execute( "UPDATE site_" + site + " SET aliasof="
			+ min + " WHERE aliasof=" + chain.aliasof ) ;
	if ( nodeToo )
	{
		chain.aliasof = 0 ;
		updateNode( chain ) ;
	}
 }

 
 
 /**
  * Recursively insert a chain of document nodes, re-connect if needed 
  * 
  * @param path path to the document
  * @param name name of the document
  * @param type 'd' - directory, 'f' - file
  * @return document IndexNode
  */   
 public IndexNode insertCascade( String path, String name, String type ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  return insertCascade0( path, name, type ) ; }
	 catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ; 
 }

 /**
  * Recursively insert a chain of document nodes, re-connect if needed 
  * 
  * @param path path to the document
  * @param name name of the document
  * @param type 'd' - directory, 'f' - file
  * @return document IndexNode
  */   
 public IndexNode insertCascade0( String path, String name, String type ) throws Exception 
 {
  notServlet( "insertCascade()" ) ;
  IndexNode n = getNode( 0, path, name, type ) ;
  if ( n != null ) return n ;
  // this node does not exist yet, get it's parent id and insert it
  URLSplit split = URLSplit.newURLSplit( path, null, "d" ) ;
  // If this is the root node, it must exist already
  if ( path.equals( "" ) && name.equals( "" ) ) return getNode( 1, null, null, "d" ) ;     
  IndexNode parent = insertCascade( split.path, split.name, "d" ) ;
  if ( parent == null )
	               return null ;
  // now we have the parent's id and can insert this node
  n = IndexNode.newIndexNode( path, name, type ) ;
  n.setParentId( parent.id ) ;
  updateNode( n ) ;
  setPermissions( n, parent ) ;
  return n ;
 }
 
 /**
  * Recursively delete a chain of document nodes, re-connect if needed.
  * The terminal document is identified by either the id, or path and name. 
  * 
  * @param id id of the terminal document
  * @param path path to the document
  * @param name name of the document
  * @param type of the node: "d" - directory, "f" - file
  * @return document id
  */   
 public void deleteCascade( int id, String path, String name, String type ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  deleteCascade0( id, path, name, type ) ; return ; }
	 catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 } 
 
 /**
  * Recursively delete a chain of document nodes.
  * The terminal document is identified by either the id, or path and name. 
  * 
  * @param id id of the terminal document
  * @param path path to the document
  * @param name name of the document
  * @param type type of the node: "d" - directory, "f" - file
  * @return document id
  */   
 public void deleteCascade0( int id, String path, String name, String type ) throws Exception 
 {
  notServlet( "deleteCascade()" ) ;
  IndexNode n = getNode( id, path, name, type ) ;
  if ( n == null || n.getId() == 1 ) return ; // don't delete the root
  // How many nodes on the level?
  int num = countLevel0( n.parentId ) ;
  sqlRemove.setInt( 1, n.id ) ;
  if ( LOG.isTraceEnabled() )
	   LOG.trace( "SQL: " + sqlRemove.toString() ) ;

  sqlRemove.execute() ;
  // If the level is not empty, delete only this node and change link to next
  if ( num > 1 ) relinkLevel( n.id, n.nextId ) ; // Else delete the parent too
     else deleteCascade0( n.getParentId(), null, null, "d" ) ;
 }
 
 
 public void relinkLevel( int id, int nextId ) throws Exception
 {
   sqlRelink.setInt( 1, nextId ) ;
   sqlRelink.setInt( 2, id ) ;
   if ( LOG.isTraceEnabled() )
           LOG.trace( "SQL: " + sqlRelink.toString() ) ;
   sqlRelink.execute() ;
 }
 
 /**
  * Read document node info from the database, re-connect if needed
  * The document is identified by either the id, or path and name. 
  * 
  * @param id id of the document
  * @param path path to the document
  * @param name name of the document
  * @param type of the node: "d" - directory, "f" - file
  * @return document id
  */   
 public IndexNode getNode( int id, String path, String name, String type ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  return getNode0( id, path, name, type ) ; }
	 catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ;
 } 

 /**
  * Read document node info from the database.
  * The document is identified by either the id, or path and name. 
  * 
  * @param id id of the document
  * @param path path to the document
  * @param name name of the document
  * @param type of the node: "d" - directory, "f" - file
  * @return document id
  */   
 public IndexNode getNode0( int id, String path, String name, String type ) throws Exception 
 {
  // if we know the id, use it, should be faster
  if ( path != null && path.equals( "" ) && name.equals( "" )) id = 1 ; // the root 
  IndexNode n = null ;
  ResultSet rs = null ;
  if ( id != 0 )
   {
    sqlGetById.setInt( 1, id ) ;
    if ( LOG.isTraceEnabled() )
		   LOG.trace( "SQL: " + sqlGetById.toString() ) ;

    rs = sqlGetById.executeQuery() ;
   } else if ( path != null && name != null && type != null )
   {
    sqlGetByPath.setString( 1, path ) ;
    sqlGetByPath.setString( 2, name ) ;
    sqlGetByPath.setString( 3, type ) ;
    if ( LOG.isTraceEnabled() )
		   LOG.trace( "SQL: " + sqlGetByPath.toString() ) ;

    rs = sqlGetByPath.executeQuery() ;
   } else return null ;
  if ( rs.next() )
   { 
    n = readNode( rs, null ) ;
   } else n = null ;
  rs.close() ;
  rs = null ;
  if ( n != null ) setPermissions( n, null ) ; // make sure that current permissions are set
  return n ;
 }
 
 /**
  *  Make sure that current permissions are set
  * 
  */   
 public void setPermissions( IndexNode n, IndexNode parent ) throws Exception
 {
   Date now = new Date() ;
   if ( n.access.charAt( 0 ) == 'i' ) // inherited permissions, check cached value
    {
     Date timeToUpdate = new Date( n.cached + CACHE_LASTS * 60 * 1000 ) ;
     if ( timeToUpdate.after( now ) ) return ;
    
     if ( parent == null ) parent = getNode0( n.parentId, null, null, "d" ) ;
     setPermissions( parent, null ) ; // make sure that parent node has current permissions
     if ( parent.access.charAt( 0 ) == 'i' ) n.cached = parent.cached ;
                                        else n.cached = now.getTime() ;
     n.groupr = parent.groupr ;
     n.groupw = parent.groupw ;
     n.userr  = parent.userr ;
     n.userw  = parent.userw ;
     n.owners = parent.owners ;
     updateNode0( n ) ;
   }
 }
 
 /**
  * Apply permissions set via configuration file
  * 
  * @param permissions array of Permissions objects
  */    
 public void applyPermissions( au.csiro.cass.arch.auth.Permissions[] permissions ) throws Exception
 {
   if ( permissions == null ) return ;
   for ( int i = 0 ; i < permissions.length ; i++ )
   {
	Permissions p = permissions[ i ] ;
	IndexNode node = makeSureExists( p.getType(), p.getPath(), p.getName(), false ) ;
    if ( !( node.getUserr().equals( p.getUserr() ) ) ||
         !( node.getUserw().equals( p.getUserw() ) ) ||  
         !( node.getGroupr().equals( p.getGroupr() ) ) ||  
         !( node.getGroupw().equals( p.getGroupw() ) ) ||  
         !( node.getOwners().equals( p.getOwners() ) ) || 
         !( node.getAccess().equals( p.getAccessType() ) ) )
    {
      node.setUserr( p.getUserr() ) ;
      node.setUserw( p.getUserw() ) ;  
      node.setGroupr( p.getGroupr() ) ;
      node.setGroupw( p.getGroupw() ) ;  
      node.setOwners( p.getOwners() ) ;
      node.setAccess( p.getAccessType() ) ;
      updateNode0( node ) ;
    }
   }
 }


 
   
 /**
  * Write/update document node info, re-connect if needed
  * 
  * @return document id
  */   
 public int updateNode( IndexNode n ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { return updateNode0( n ) ; }
	 catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return -1 ;
 } 
 
 /**
  * Write/update document node info
  * 
  * @return document id
  */   
 public int updateNode0( IndexNode n ) throws Exception 
 {
 // id name label title path type access groupr groupw userr userw owners ;
 // parentId nextId fecthed errors cached weight score ;
    
  PreparedStatement st = sqlInsertNode ;
  if ( n.getId() != 0 ) st = sqlUpdateNode ; // update existing folder node
  setParam( st, 1, n.getName() ) ; setParam( st, 2, n.getLabel() ) ;
  setParam( st, 3, n.getTitle() ) ; setParam( st, 4, n.getPath() ) ;
  setParam( st, 5, n.getType() ) ; setParam( st, 6, n.getAccess() ) ;
  setParam( st, 7, n.getGroupr() ) ; setParam( st, 8, n.getGroupw() ) ;
  setParam( st, 9, n.getUserr() ) ; setParam( st, 10, n.getUserw() ) ;
  setParam( st, 11, n.getOwners() ) ; 
  st.setInt( 12, n.getParentId() ) ;
  st.setInt( 13, n.getNextId() ) ;
  st.setInt( 14, n.getFetched() ) ;
  st.setInt( 15, n.getErrors() ) ;
  st.setLong( 16, n.getCached() ) ;
  st.setFloat( 17, n.getWeight() ) ;
  st.setInt( 18, n.getScore() ) ;
  st.setString( 19, n.getStatus() ) ;
  st.setInt( 20, n.getHasForm() ) ;
  st.setInt( 21, n.getHasScript() ) ;
  st.setLong( 22, n.getCRC() ) ;
  st.setLong( 23, n.getSrcCRC() ) ;
  st.setLong( 24, n.getAliasof() ) ;
  if ( n.getId() != 0 )
      st.setInt( 25, n.getId() ) ;
  if ( LOG.isTraceEnabled() )
	   LOG.trace( "SQL: " + st.toString() ) ;
  st.executeUpdate() ;
  if ( n.getId() == 0 )
     {
	   if ( LOG.isTraceEnabled() )
		   LOG.trace( "SQL: " + sqlLastId.toString() ) ;

       ResultSet rs = sqlLastId.executeQuery() ;
       int newId = 0 ;
        if ( rs.next() )
           newId = rs.getInt( 1 ) ;
        else throw new Exception( "Can't retrieve last inserted after " + st.toString() ) ;
        rs.close();
        rs = null ;
                 
        // insert it as the last node on the level
        // SET nextid=? WHERE parentid=? AND nextid = 0 AND id != ? " 
        n.setId( newId ) ;
        sqlLink.setInt( 1, newId ) ;
        sqlLink.setInt( 2, n.getParentId() ) ;
        sqlLink.setInt( 3, newId ) ;
	     if ( LOG.isTraceEnabled() )
			   LOG.trace( "SQL: " + sqlLink.toString() ) ;

        int res = sqlLink.executeUpdate() ;
        if ( res != 1 && res != 0 ) 
         throw new Exception( "Folder tree link failed: " + sqlLink.toString() ) ;                
     }
  return n.getId() ;
 }

 /**
  * Set a PreparedStatement String parameter or NULL value
  * 
  * @return document id
  */   
 public void setParam( PreparedStatement st, int num, String param ) throws Exception
 {
    if ( param == null ) st.setNull( num, java.sql.Types.VARCHAR );
                                  else st.setString( num, param ) ; 
 }
 
 /**
  * Create IndexNode object and fill it with data from ResultSet
  * 
  * @param usedNode existing node object, if available, to avoid memory allocation
  * @param rs   ResultSet with data to copy to IndexNode
  * 
  * @return IndexNode
  */   
 public IndexNode readNode( ResultSet rs, IndexNode usedNode ) throws Exception
 {
   // id name label title path type access groupr groupw userr userw owners ;
   // parentId nextId fecthed errors cached weight score ;
   IndexNode n = usedNode ;
   if ( n == null ) n = new IndexNode() ;
   n.setId( rs.getInt( 1 ) )  ; 
   n.setName( rs.getString( 2 ) )  ; 
   n.setLabel( rs.getString( 3 ) ) ;
   n.setTitle( rs.getString( 4 ) ) ; 
   n.setPath( rs.getString( 5 ) ) ;
   n.setType( rs.getString( 6 ) ) ; 
   n.setAccess( rs.getString( 7 ) ) ;
   n.setGroupr( rs.getString( 8 ) ) ;
   n.setGroupw( rs.getString( 9 ) ) ;
   n.setUserr( rs.getString( 10 ) ) ;
   n.setUserw( rs.getString( 11 ) ) ;
   n.setOwners( rs.getString( 12 ) ) ; 
   n.setParentId( rs.getInt( 13 ) ) ;
   n.setNextId( rs.getInt( 14 ) ) ;
   n.setFetched( rs.getInt( 15 ) ) ;
   n.setErrors( rs.getInt( 16 ) ) ;
   n.setCached( rs.getLong( 17 ) ) ;
   n.setWeight( rs.getFloat( 18 ) ) ;
   n.setScore( rs.getInt( 19 )  ) ;
   n.setStatus( rs.getString( 20 )  ) ;
   n.setHasForm( rs.getInt( 21 )  ) ;
   n.setHasScript( rs.getInt( 22 )  ) ;
   n.setCRC( rs.getLong( 23 )  ) ;
   n.setSrcCRC( rs.getLong( 24 )  ) ;
   n.setAliasof( rs.getInt( 25 )  ) ;
   n.setSite( site ) ;
   n.setBase( url ) ;
   
   return n ;
 }
 
 
 /**
  * Read urls existing in the db, not found by the crawler, to injector input file
  * 
  * @param area LogLinks area for which to do this
  * @return a number of the urls output
  */     
 public int readUnindexedURLs( LogLinks area ) throws Exception
 {
   notServlet( "readUnindexedURLs()" ) ;
   String sql = "SELECT path, name FROM " + table + " WHERE status=\'l\' " +
                  "AND type=\'f\' " ;
   String url = area.getSite().getUrl() ;
   if ( url.endsWith("/") ) url = url.substring( 0, url.length() - 1 ) ;
   ResultSet rs = read( sql ) ;
   BufferedWriter out = area.getSite().getIndexer().getCrawlRoots() ;
   int count = 0 ;
   while ( rs.next() )
   {
     String path = rs.getString( 1 ) ;
     String name = rs.getString( 2 ) ;
     String u ;
     if ( !name.equals("/") ) u = url + path + "/" + name ;
                         else u = url + path + "/" ;
     out.write( u + "\n" ) ; count++ ;
   }
   rs.close() ;
   return count ;
 }

 
 /**
  * Mark all site URLs as indexed or not, depending on the parameter
  * 
  * @param boolean indexed - mark as indexed if true
  */     
 public void markIndexed( boolean indexed ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  markIndexed0( indexed ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 } 
 
 
 public void markIndexed0( boolean indexed ) throws Exception
 {
   notServlet( "markIndxed()" ) ;
   String status = indexed ? "'i'" : "'l'" ;
   String sql = "UPDATE " + table + " set status=" + status +
                  " WHERE type=\'f\' " ;
   execute( sql ) ;
 }

 
 /**
  * Read arrays of inclusions, exclusions and roots for this area
  * 
  * @param area area for which to do this
  */    
 public void readParams( IndexArea area ) throws Exception
 {
   notServlet( "readParams()" ) ;
   area.setExclusions( readStrings(  
           "select path from roots" + area.getAreaClause() + " and type='X'" ) );
   area.setInclusions( readStrings(  
           "select path from roots" + area.getAreaClause() + " and type='I'" ) );
   area.setRoots( readStrings(  
           "select path from roots" + area.getAreaClause() + " and type='R'" ) );
 }
 
 /**
  * Write arrays of inclusions, exclusions and roots for this area
  * 
  * @param area area for which to do this
  */    
 public void writeParams( IndexArea area ) throws Exception 
 {
   notServlet( "writeParams()" ) ;
   execute( "delete from roots" + area.getAreaClause() + 
                                                 " and type in ( 'X', 'I', 'R' ) " ) ;
   write( area.getInclusions(),
        "insert into roots ( site, area, type, path ) values " +
        "( '" + area.getSite().getName() + "', '" + area.getName() + "', 'I', ",")" ) ;
   write( area.getExclusions(),
        "insert into roots ( site, area, type, path ) values " +
        "( '" + area.getSite().getName() + "', '" + area.getName() + "', 'X', ",")" ) ;
   write( area.getRoots(),
        "insert into roots ( site, area, type, path ) values " +
        "( '" + area.getSite().getName() + "', '" + area.getName() + "', 'R', ",")" ) ;
 }
 
 /**
  * Read all nodes on a level defined by parent id
  * 
  * @param parentId id of the parent
  *
  * @return IndexNode[] array of nodes on the level
  */
 public IndexNode[] readLevel( int parentId, int exceptionId, boolean order ) throws Exception 
 {
  // How many nodes on the level?
  int num = countLevel0( parentId ) ;
  HashSet ids = new HashSet() ;
  HashMap nodes = new HashMap() ;
  IndexNode[] level = new IndexNode[ num ] ;
  sqlGetLevel.setInt( 1, parentId ) ;
  if ( LOG.isTraceEnabled() )
       LOG.trace( "SQL: " + sqlGetLevel.toString() ) ;

  ResultSet rs = sqlGetLevel.executeQuery() ;
  int i = 0 ;
  while( rs.next() )
   { 
     IndexNode node = readNode( rs, null ) ;
     if ( node.id == exceptionId ) num-- ;
      else { level[ i++ ] = node ;
             ids.add( Integer.valueOf( node.nextId ) ) ;
             nodes.put( Integer.valueOf( node.id ), node ) ;
           }
   }
  rs.close() ;
  // make sure cached permissions are still valid
  IndexNode parent = getNode( level[0].parentId, null, null, "d" ) ;
  for ( i = 0 ; i < num ; i++ )
	  this.setPermissions( level[i], parent ) ;
  
  
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
 
 /**
  * Count nodes on a level defined by parent id
  * 
  * @param parentId id of the parent
  *
  * @return int number of nodes on the level 
  */
 public int countLevel( int parentId ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {  return countLevel0( parentId ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return -1 ;
 } 
 
 public int countLevel0( int parentId ) throws Exception 
 {
  sqlCountLevel.setInt( 1, parentId ) ;
  if ( LOG.isTraceEnabled() )
       LOG.trace( "SQL: " + sqlCountLevel.toString() ) ;

  ResultSet rs = sqlCountLevel.executeQuery() ;
  rs.next() ;
  int num = rs.getInt( 1 ) ;
  rs.close() ;
  return num ;
 }
 
 public void notServlet( String function ) throws Exception
 {
  if ( servletMode )
     throw new Exception( "Function " + function + " of " + this.getClass().getName() + 
                          " is not intended to be used in servlet mode." ) ; 
 }

 //=============================================================================
 //                       Functions used by servlets
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
        IndexNode node = this.getNode( id, null, null, null ) ;
        IndexNode parent = this.getNode( node.parentId, null, null, null ) ;
        if ( !parent.canWrite( user, groups ) ) return false ;
        begin() ;
        if ( before > 0 ) // include inside level, else insert as the first one
                 // SET nextid=? WHERE parentid=? AND nextid = 0 AND id != ? "
         {
           nodeBefore = this.getNode( before, null, null, null ) ;
           if ( nodeBefore.parentId != node.parentId || id == before )
            { execute( "ROLLBACK" ) ; return false ; } // ignore attempts to move to another level
         }
        relinkLevel( node.id, node.nextId ) ;
        execute( "update " + table + " set nextid=" + id + " where nextid = " + before + " and id !=" + id  ) ;
        execute( "update " + table + " set nextid=" + before + " where id = " + id ) ;
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
         IndexNode node = getNode( id, null, null, null ) ;
         IndexNode parent = getNode( node.parentId, null, null, null ) ;
         if ( !parent.canWrite( user, groups ) ) return false ;
         IndexNode[] level = readLevel( id, -1, false ) ;
         for ( int i = 0 ; i < level.length ; i++ )
             deleteNode( level[ i ].id, user, groups ) ;
         relinkLevel( node.id, node.nextId ) ;
         execute( "delete from " + table + " where id=" + id ) ;
   return true ;
 }
  
 /**
  *  Read level of nodes defined either by parent id or id of one of the nodes.
  *  
  *  @param nodeId int id of one of the nodes on the level or -1
  *  @param parentId int id level parent or -1
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return array of nodes where any element may be null if reading is not permitted
  */
 public IndexNode[] readLevel( int nodeId, int parentId, String user, String groups ) throws Exception
 {
  IndexNode[] level ;
  String[] groupsA = groups.split( " " ) ;
  if ( parentId > 0 ) level = readLevel( parentId, -1, true ) ;
    else { 
          IndexNode node = getNode( nodeId, null, null, null ) ;
          level = readLevel( node.parentId, -1, true ) ;
         }
  for ( int i = 0 ; i < level.length ; i++ )
    if ( level[i] != null && !level[ i ].canRead( user, groupsA ) ) level[ i ] = null ;
  return level ;
 } 
 
 /**
  *  Read node info.
  *  
  *  @param id int id of the node
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *
  *  @return IndexNode node or null if reading is not permited
  */
 public IndexNode readNode( int id, String user, String groups ) throws Exception 
 {
  IndexNode node = getNode( id, null, null, null ) ;
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
   IndexNode old = getNode( node.getId(), null, null, null ) ;
   if ( !old.canWrite( user, groups ) ) return false ;
   if ( !old.isAdmin( user, groups ) ) // have to be admin to change permissions
     if ( !Utils.sameText( node.groupr, old.groupr ) ||
    	  !Utils.sameText( node.groupw, old.groupw ) ||
    	  !Utils.sameText( node.userr, old.userr ) ||
    	  !Utils.sameText( node.userw, old.userw ) ||
    	  !Utils.sameText( node.access, old.access ) ||
    	  !Utils.sameText( node.owners, old.owners ) ) return false ;
   // Copy info that can't be updated
   node.cached   = old.cached ;
   node.fetched  = old.fetched ;
   node.nextId   = old.nextId ;
   node.parentId = old.parentId ;
   node.score    = old.score ;
   node.status   = old.status ;
   node.type     = old.type ;
   node.weight   = old.weight ;
   node.path     = old.path ;
   node.name     = old.name ;
   updateNode0( node ) ;
   return true ;
 } 
 
 /**
  *  Insert a new node.
  *  
  *  
  *  @param parentId int id of level parent node
  *  @param info serialized node info
  *  @param user String login name of user requesting the action
  *  @param groups String space delimited list of user groups of the user
  *  
  *  @return int id of the new node or -1 if operation is not permitted
  */
 public int insertNode( int parentId, IndexNode node, String user, String groups ) throws Exception
 {
   // can only insert a node of can write to parent node
   IndexNode parent = getNode( parentId, null, null, null ) ;
   if ( !parent.canWrite( user, groups ) ) return -1 ; // can't write to this level
   node.parentId = parentId ;
   node.path = parent.path + "/" + parent.name ; 
   if ( node.path.charAt( 0 ) != '/' ) node.path = "/" + node.path ;
   node.id = 0 ;
   node.nextId = 0 ;
   updateNode0( node ) ;
   // update level links
   execute( "update " + table + " set nextid = " + node.id +
		              " where nextid = 0 and id != " + node.id ) ;
  
   return node.id ;  
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
   return url ;	 
 }

 /**
  * @return the table
  */
 public String getTable() {
 	return table;
 }

 /**
  * Write a scan result, re-connect if needed
  * 
  * @param result scan result to save
  */
 synchronized public void writeScanResult( ScanResult result ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   writeScanResult0( result ) ; return ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 }
 
 /**
  * Write a scan result
  * 
  * @param result scan result to save
  */
 public void writeScanResult0( ScanResult result ) throws Exception 
 {
  notServlet( "writeScanResult()" ) ;
  IndexNode node = makeSureExists( result.getUrl(), false ) ;
  node.setHasForm( result.isFormFound() ? 1 : 0 ) ;
  node.setHasScript( result.isScriptFound() ? 1 : 0 ) ;
  node.setCRC( result.getOutCRC() ) ;
  node.setSrcCRC( result.getSrcCRC() ) ;
  updateNode( node ) ;
 }
 

 /**
  * Read a scan result, re-connect if needed
  * 
  * @param url url of the page
  * @return ScanResult scan result 
  */
 synchronized public ScanResult readScanResult( String url ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   return readScanResult0( url ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ;
 }
 
 /**
  * Read a scan result
  * 
  * @param url url of the page
  * @return ScanResult scan result 
  */
 public ScanResult readScanResult0( String url ) throws Exception 
 {
  ScanResult result = new ScanResult() ;	 
  notServlet( "readScanResult()" ) ;
  IndexNode node = makeSureExists( url, false ) ;
  result.setFormFound( node.getHasForm() != 0 ) ;
  result.setScriptFound( node.getHasScript() != 0 ) ;
  result.setOutCRC( node.getCRC() ) ;
  result.setSrcCRC( node.getSrcCRC() ) ;
  result.setUrl( url ) ;
  return result ;
 }
 
 /**
  * Add a link, re-connect if needed
  * 
  * @param src url of link source
  * @param srcId id of link source if available
  * @param target url of link target
  * @return id of link source
  */
 synchronized public int addLink( String src, int srcId, String target ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   return addLink0( src, srcId, target ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return -1 ;
 }

 /**
  * Add a link
  * 
  * @param src url of link source
  * @param srcId id of link source if available
  * @param target url of link target
  * @return id of link source
  */
 public int addLink0( String src, int srcId, String target ) throws Exception 
 {
   if ( srcId < 0 ) srcId = getId( src ) ; // get id first
   if ( srcId < 0 ) return srcId ; // can do nothing
   sqlAddLink.setInt( 1, srcId ) ;
   sqlAddLink.setString( 2, target ) ;
   sqlAddLink.execute() ;
   return srcId ;
 }
 
 /**
  * Remove a link, re-connect if needed
  * 
  * @param src url of link source
  * @param srcId id of link source if available
  * @param target url of link target
  * @return id of link source
  */
 synchronized public int removeLink( String src, int srcId, String target ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   return removeLink0( src, srcId, target ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return -1 ;
 }

 /**
  * Remove a link
  * 
  * @param src url of link source
  * @param srcId id of link source if available
  * @param target url of link target
  * @return id of link source
  */
 public int removeLink0( String src, int srcId, String target ) throws Exception 
 {
   if ( srcId < 0 ) srcId = getId( src ) ; // get id first
   if ( srcId < 0 ) return srcId ; // can do nothing
   sqlRemoveLink.setInt( 1, srcId ) ;
   sqlRemoveLink.setString( 2, target ) ;
   sqlRemoveLink.execute() ;
   return srcId ;
 }

 /**
  * Read links, re-connect if needed
  * 
  * @param src url of link source
  * @param srcId id of link source if available
  * @return result set containing links
  */
 synchronized public Set readLinks( String src, int srcId ) throws Exception 
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try {   return readLinks0( src, srcId ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ;
 }

 /**
  * Read links
  * 
  * @param src url of link source
  * @param srcId id of link source if available
  * @return result set containing links
  */
 public Set readLinks0( String src, int srcId ) throws Exception 
 {
   if ( srcId < 0 ) srcId = getId( src ) ; // get id first
   if ( srcId < 0 ) return null ; // can do nothing
   sqlReadLinks.setInt( 1, srcId ) ;
   ResultSet rs = sqlReadLinks.executeQuery() ;
   HashSet links = new HashSet() ;
   while( rs.next() )
	  links.add( rs.getString( 1 ) ) ;
   rs.close() ;
   return links ;
 }
 
 synchronized int getId( String url ) throws Exception
 {
   int id = -1 ;
   URLSplit split = URLSplit.newURLSplit( url, null, "f" ) ;
   sqlPath2Id.setString( 1, split.path ) ; // "SELECT id, status FROM " + table + " WHERE path=? AND name=? " ) ;
   sqlPath2Id.setString( 2, split.name ) ;
   if ( LOG.isTraceEnabled() ) LOG.trace( "SQL: " + sqlPath2Id.toString() ) ;
   ResultSet rs = sqlPath2Id.executeQuery() ;
   if ( rs.next() ) id = rs.getInt( 1 ) ;
   rs.close() ;
   return id ;
 }
 
 public int getSumScores( int aliasOf, float[] paramContainer ) throws Exception
 {
   this.sqlSumScores.setInt( 1, aliasOf ) ; 
   if ( LOG.isTraceEnabled() ) LOG.trace( "SQL: " + sqlSumScores.toString() ) ;
   ResultSet rs = sqlSumScores.executeQuery() ;
   int sum = 0 ; 
   if ( rs.next() )
	   { 
	     sum = rs.getInt( 1 ) ;
	     paramContainer[ 0 ] = rs.getFloat( 2 ) ;
	   }
   rs.close() ;
   return sum ;
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
	  
	IndexSiteDB db = intrf.newIndexSiteDB( cfg, "narrabri", false ) ;
	 
   String user = "person", groups = "other" ;
// These should fail because of blocked access
	IndexNode node = db.readNode( 52, user, groups ) ;
	IndexNode[] level1 = db.readLevel( 52, -1, user, groups ) ;
	IndexNode[] level2 = db.readLevel( -1, 51, user, groups ) ;
	node.setName( "This is my new name" ) ;
	boolean r1 = false, r2 = false, r3 = false, r4 = false ;
	if ( node != null ) r1 = db.updateNode( node, user, groups ) ;
	r2 = db.moveNode( 52, 87, user, groups ) ;
	r3 = db.moveNode( 52, 257, user, groups ) ;
	int newid = db.insertNode( 51, node, user, groups ) ;
	if ( newid > 0 ) r4 = db.deleteNode( newid, user, groups ) ;
	
	if ( node != null || level1 != null || level2 != null || r1 || r2 || r3 || r4 || newid > 0 )
		throw new Exception( "Some actions succeeded, access control is broken." ) ;
		
// These should succeed
	user = "root" ; groups = "public" ;
	node = db.readNode( 52, user, groups ) ;
	level1 = db.readLevel( 52, -1, user, groups ) ;
	level2 = db.readLevel( -1, 51, user, groups ) ;
    String oldName = node.getName() ;
	node.setName( "This is my new name" ) ;
	boolean r = db.updateNode( node, user, groups ) ;
	node.setName( oldName ) ;
	r1 = db.updateNode( node, user, groups ) ;
	r2 = db.moveNode( 52, 87, user, groups ) ;
	r3 = db.moveNode( 52, 257, user, groups ) ;
	newid = db.insertNode( 51, node, user, groups ) ;
	r4 = db.deleteNode( newid, user, groups ) ;
	if ( !r || !r1 || !r2 || !r3 || !r4 || newid < 1 )
		throw new Exception( "Test failed." ) ;
	
  } catch( Exception e )
  {
    System.out.print( e.getMessage() ) ;
    e.printStackTrace() ;
  }
 }

/* (non-Javadoc)
 * @see au.csiro.cass.arch.sql.IndexSiteDB#resetAliasof(java.lang.String)
 */
@Override
public void resetAliasof() throws SQLException
{
  statement.execute( "update " + table + " set aliasof = 0 "  ) ;
  
}

 
}
