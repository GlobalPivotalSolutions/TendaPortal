/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.IndexSiteDBBaseImpl;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * A set of db related functions needed by IndexSite and LogSite objects
 * 
 */
public class IndexSiteDBDerby extends IndexSiteDBBaseImpl
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexSiteDBDerby.class ) ;
 
 /**
  * Constructor
  * 
  * @param cfg configuration parameters
  * 
  */
 IndexSiteDBDerby( ConfigList cfg )
 { super( cfg ) ; } ;
 
 
 /**
 * Close the db connection
 */
 @Override
 public void close() throws Exception
 { if ( db != null )
      { db.commit() ; super.close() ; }
 }

 @Override
 public void begin() throws Exception
 {
   if ( db == null ) return ;
   db.commit() ;
   db.setAutoCommit( false ) ;
 }

 
 @Override
 public void rollback() throws Exception
 {
   if ( db == null ) return ;
   db.rollback() ;
   db.setAutoCommit( true ) ;
 }

 
 @Override
 public void commit() throws Exception
 {
   if ( db == null ) return ;
   db.commit() ;
   db.setAutoCommit( true ) ;
 }
 
 /**
  * Factory, returns initialised and connected object.
  * 
  * @param cfg configuration parameters
  * @param site site name
  * 
  */
 public static IndexSiteDBDerby newIndexSiteDBDerby( ConfigList cfg, String site, boolean servletMode ) throws Exception
 {
  String targetDB = cfg.get( "target.db", null ) ;
  if ( servletMode && targetDB != null && targetDB.contains( "derby:embedded" ) )
          return null ;
  IndexSiteDBDerby dd = new IndexSiteDBDerby( cfg ) ;
  dd.site = site ;
  dd.table = "site_" + site ;
  dd.servletMode = servletMode ;
  dd.connect();
//  dd.init() ; This is being called from connect() now
  dd.url = cfg.get( "url", "" ) ;
  if ( dd.url.length() == 0 )
    LOG.warn( "URL is missing for site " + site + ", ignore if the site is being dropped." );
  return dd ;
 }
 
 /**
  * Create site table and root node, if needed
  * 
  */ 
 public void init() throws Exception
 {
   begin() ;
   statement = db.createStatement() ;
  if ( !servletMode )
   {
	 try
	 {
       String sql = "CREATE TABLE site_" + site +  " ( " +
       "id bigint NOT NULL GENERATED ALWAYS AS IDENTITY, " + // URL id
       "name   varchar( 2550 ) DEFAULT NULL, " +     // folder or file name, '/' for empty name
       "label  varchar( 2550 ) DEFAULT NULL, " + // if defined, shown in the tree instead of the name
       "title  varchar( 2550 ) DEFAULT NULL, " + // shown as a tip in the browser
       "path   varchar( 2550 ) DEFAULT NULL, " + // path to this folder or file from the site root
       "type               char(2) NOT NULL, " +   
       "access             char(1) NOT NULL, " + 
       "groupr varchar( 2550 ) DEFAULT NULL, " + // list of groups having read access
       "groupw varchar( 2550 ) DEFAULT NULL, " + // list of groups having write access
       "userr  varchar( 2550 ) DEFAULT NULL, " + // list of users having read access
       "userw  varchar( 2550 ) DEFAULT NULL, " + // list of users having write access
       "owners varchar( 2550 ) DEFAULT NULL, " + // list of users having admin access
       "parentid            bigint NOT NULL, " + // id of the parent node
       "nextid            bigint  DEFAULT 0, " + // id of the next node on the level
       "fetched               int DEFAULT 0, " +
       "errors                int DEFAULT 0, " + 
       "cached             bigint DEFAULT 0, " +
       "weight             float DEFAULT -1, " +
       "score                 int DEFAULT 0, " +        
       "status          char(1) DEFAULT 'l', " +
       "hasform          smallint DEFAULT 0, " + // 1 if the page has a form in it        
       "hasscript        smallint DEFAULT 0, " + // 1 if the page has a script in it        
       "crc                bigint DEFAULT 0, " + // CRC32 code of the page output        
       "srccrc             bigint DEFAULT 0, " +
       "aliasof               int DEFAULT 0, " +
       "primary key ( id ) ) " ;                 // CRC32 code of the page source        
       statement.execute( sql ) ;
       statement.execute( " CREATE INDEX idxparentid ON site_" + site + " ( parentid )" ) ;
       statement.execute( " CREATE INDEX idxnextid ON site_" + site + " ( nextid )" ) ;
       statement.execute( " CREATE INDEX idxpathname ON site_" + site + " ( path, name )" ) ;
       statement.execute( " CREATE INDEX idxparentidname ON site_" + site + " ( parentid, name )" ) ;
       statement.execute( " CREATE INDEX idxaliasof ON site_" + site + " ( aliasof )" ) ;
	 } catch( Exception e ) // ignore, this table must have existed
	 {
	      String aaa = e.getMessage() ;
	      String bbb = aaa ;
	 } ;
     try
     {
	   String sql = "CREATE TABLE links_" + site +  " ( " +
	                "id     bigint NOT NULL, " + // source URL id in the site table
	                "link varchar(2500) NOT NULL )" ; // link target
	   statement.execute( sql ) ; 
       statement.execute( " CREATE INDEX idxlinkid ON links_" + site + " ( id )" ) ;
     } catch( Exception e ) // ignore, this table must have existed
     {
        String aaa = e.getMessage() ;
        String bbb = aaa ;
     } ;
         
    sqlUpdateScore = db.prepareStatement( "UPDATE " + table + " SET score=score+? WHERE path=? AND name=?" ) ;
    sqlUpdateWeight = db.prepareStatement( "UPDATE " + table + " SET weight=? WHERE id=?" ) ;
    sqlGetWeight = db.prepareStatement( "SELECT weight FROM " + table + " WHERE id=?" ) ;
    sqlGetWeightByUrl = db.prepareStatement( "SELECT weight FROM " + table + " WHERE path=? and name=?" ) ;
    sqlPath2Id = db.prepareStatement( "SELECT id, status FROM " + table + " WHERE path=? AND name=? " ) ;
    sqlGetByPath = db.prepareStatement( "SELECT * FROM " + table + " WHERE path=? AND name=? AND type=?" ) ;
    sqlListScores = "SELECT id, path, name, weight, score, aliasof FROM " + table + " where type='f'" ;
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
	  		                "owners, parentid, nextid, fetched, errors, cached, weight, score, status," +
	  		                "hasform, hasscript, crc, srccrc, aliasof) values " +
	                        "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)" ) ;
	  sqlLink = db.prepareStatement(
	           "UPDATE " + table + " SET nextid=? WHERE parentid=? AND nextid = 0 AND id != ? " ) ;
   sqlLastId = db.prepareStatement( "SELECT IDENTITY_VAL_LOCAL() FROM  " + table + " " ) ;
   sqlGetById = db.prepareStatement( "SELECT * FROM " + table + " where id = ? " ) ;
   sqlCountLevel = db.prepareStatement( "SELECT count(*) FROM " + table + " WHERE parentid=? " ) ;
   sqlRelink = db.prepareStatement( "UPDATE " + table + " SET nextid=? where nextid=? " ) ;
   sqlRemove = db.prepareStatement( "DELETE FROM " + table + " where id=? " ) ;
   sqlGetLevel = db.prepareStatement( "SELECT * FROM " + table + " WHERE parentid=? " ) ;
   // make sure the root node exists
   IndexNode n = getNode( 1, "", "/", "d" ) ;
   if ( n == null )
     {
       n = IndexNode.newRootIndexNode() ;
       updateNode( n ) ;
     }
   commit() ;
 }

 
}
