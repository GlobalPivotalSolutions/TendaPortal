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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.logProcessing.LogFile;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Implements a set of db functions for access to tables shared by all sites and areas
 * 
 * @author Arkadi Kosmynin
 *
 */
public class IndexRootDBDerby extends IndexRootDBBaseImpl
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexRootDBDerby.class ) ;

 /**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexRootDBDerby(  ConfigList cfg )
 { super( cfg ) ; }
 
 
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
 * IndexRootDBImpl factory
 * 
 * @param cfg   configuration parameters
 * @return  connected IndexRootDBImpl object
 */
 public static IndexRootDBDerby newIndexRootDBDerby( ConfigList cfg, boolean servletMode ) throws Exception
 {
  String targetDB = cfg.get( "target.db", null ) ;
  if ( servletMode && targetDB != null && targetDB.contains( ":embedded" ) )
	                                                     return null ;
  IndexRootDBDerby dd = new IndexRootDBDerby( cfg ) ;
  dd.servletMode = servletMode ;
  dd.connect() ;
//  dd.init() ; this is being called from connect
  return dd ;
 }
 
 /**
  * Ensures that all required tables exist
  */
 public void init() throws Exception
 {
   begin() ;
   if ( !servletMode )
   { 
     try
     { 
       // The tables must not exist 
	   // info about known log files for each site
         statement.execute( " CREATE TABLE logs ( " +
                 " timeStart       varchar( 255 ), " + // timestamp in the first record of the log
                 " timeEnd         varchar( 255 ), " + // timestamp in the last record of the log
                 " timeModified    varchar( 255 ), " + // timestamp of last file change 
                 " usedForIPs  smallint DEFAULT 0, " + // 1 if used in blocked IPs list making 
                 " fileName        varchar( 255 ), " + // file name
                 " site            varchar( 255 ), " + // site name 
                 " size                  BIGINT )  " ); // file size 
     } catch( Exception e ) // The table must have existed 
     {
       String aaa = e.getMessage() ;
       String bbb = aaa ;
     } 

     try
     { 
        statement.execute( " CREATE INDEX idxlogs ON logs( site, fileName )" ) ;
     } catch( Exception e ) 
     {
       String aaa = e.getMessage() ;
       String bbb = aaa ;
     } 
 
     try
     { 
       // maintains central information about each site
       statement.execute( " CREATE TABLE sites ( " +
 	                  " id bigint NOT NULL GENERATED ALWAYS AS IDENTITY, " + // site id
                       " weightsStart varchar( 255 ) DEFAULT NULL, " + // start of the interval covered by weights
                       " weightsEnd   varchar( 255 ) DEFAULT NULL, " + // end of the interval covered by weights
                       " scoresStart  varchar( 255 ) DEFAULT NULL, " + // start of the interval covered by scores
                       " scoresEnd    varchar( 255 ) DEFAULT NULL, " + // end of of the interval covered by scores
                       " site                      varchar( 255 ), " + // site name
                       " label       varchar( 2550 ) DEFAULT NULL, " + // if defined, shown in the tree instead of name
                       " title       varchar( 2550 ) DEFAULT NULL, " + // shown as a tip in the browser
                       " groupr      varchar( 2550 ) DEFAULT NULL, " + // list of groups having read access
                       " groupw      varchar( 2550 ) DEFAULT NULL, " + // list of groups having write access
                       " userr       varchar( 2550 ) DEFAULT NULL, " + // list of users having read access
                       " userw       varchar( 2550 ) DEFAULT NULL, " + // list of users having write access
                       " owners      varchar( 2550 ) DEFAULT NULL, " + // list of users having admin access
                       " nextid                     int DEFAULT 0, " + // id of the next node on the level
                       " config                 blob DEFAULT NULL, " + // serialised site configuration file
                       " PRIMARY KEY( id ) ) " ) ;
       statement.execute( " CREATE INDEX idxsites ON sites( site )" ) ;
     } catch( Exception e ) 
     {
        String aaa = e.getMessage() ;
        String bbb = aaa ;
     } 
     try
     { 
      // a set of misc name-value pairs
      statement.execute( "CREATE TABLE system ( " +
                      " name    varchar( 250 ) NOT NULL, " +
                      " value   varchar( 2500 )NOT NULL, " +
                      " PRIMARY KEY( name ) ) " ) ;
     } catch( Exception e ) // The tables must have existed 
     {
       String aaa = e.getMessage() ;
       String bbb = aaa ;
     } 

     try
     { 
      // maintains list of index areas for each site and area
      statement.execute( " CREATE TABLE areas ( "  +
                      " site           varchar(256) NOT NULL, "  + // site name
                      " area           varchar( 30 ) NOT NULL, " + // area name
                      " lastIndexed    varchar( 255 ), "       + // last time this area was indexed
                      " status         int DEFAULT 0, "          + // completion status
                      " build          int default 0, "          + // build number
                      " PRIMARY KEY( site, area ) )  " ) ;
     } catch( Exception e ) // The tables must have existed 
     {
        String aaa = e.getMessage() ;
        String bbb = aaa ;
     } 
     try
     { 
      // maintains list of index seed urls and exclusions for each site and area
       statement.execute( " CREATE TABLE roots ( "  +
                          " site           varchar( 256 ), "         + // site name
                          " area           varchar( 30 ) NOT NULL, " + // area tag
                          " type           char(1), "                + // r - root, x - exclude, i - include
                          " path           varchar( 5550 ) ) " ) ;     // path where result is stored
       statement.execute( " CREATE INDEX idxroots ON roots( site, area )" ) ;
     } catch( Exception e ) // The tables must have existed 
     {
       String aaa = e.getMessage() ;
       String bbb = aaa ;
     } 
     try
     { 
      // maintains security scan alerts
      statement.execute( " CREATE TABLE alerts ( "     +
                        " site           varchar( 256 )  NOT NULL, " + // site name
                        " url            varchar( 5550 ) NOT NULL, " + // page url
                        " code           int, "                      + // alert code
                        " message        varchar( 10000 ) )  " ) ;       // alert message
      statement.execute( " CREATE INDEX idxalerts ON alerts( site )" ) ;
     } catch( Exception e ) // The tables must have existed 
     {
        String aaa = e.getMessage() ;
        String bbb = aaa ;
     }

    sqlInsertLogFile = db.prepareStatement(
 	        "INSERT INTO logs(timeStart, timeEnd, timeModified, usedForIPs, fileName, site, size) " +
 	                                                        " values (?, ?, ?, ?, ?, ?, ?) " ) ;
 	sqlReadLogFile = db.prepareStatement(
 	        "SELECT timeStart, timeEnd, timeModified, usedForIPs, size " +
 	                        " FROM logs WHERE fileName=? AND site=? " ) ;
   }
   commit() ;
 }
   
}


