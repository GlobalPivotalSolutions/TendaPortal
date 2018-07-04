/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.sql.IndexDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Holds db connection. Implements a set of generic utility functions.
 * 
 * @author Arkadi Kosmynin
 *
 */
public class IndexDBBaseImpl implements IndexDB
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexDBBaseImpl.class ) ;

 public ConfigList         cfg ; // configuration parameters 
 public Statement    statement ; // a reusable statement 
 public Connection          db ; // connection to the database 
 
 
/**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexDBBaseImpl( ConfigList cfg )
 { this.cfg = cfg ; }
  
 /**
 * Close the db connection
 */
 public void close() throws Exception
 { if ( db != null ) { db.close() ; db = null ; } }
 
 /**
 * Connect to the database 
 */
 public void connect() throws Exception
 {
  String targetDB = cfg.getInherited( "target.db", "" ) ;
  if ( targetDB.contains( ":embedded" ) )
  {
	String home = cfg.getInherited( "db.home", "db", "DB_HOME" ) ;
	targetDB = targetDB.replace( "embedded", home ) ;
  }
  if ( targetDB.length() == 0 ) Utils.missingParam( "target.db" ) ;
  String dbDriver = cfg.getInherited( "db.driver", "" ) ; 
  if ( dbDriver.length() == 0 ) Utils.missingParam( "db.driver" ) ;
     
  Class.forName( dbDriver ) ;
  if ( db != null ) db.close() ; // close before reconnecting
  db = DriverManager.getConnection( targetDB ) ;
  db.setAutoCommit( true ) ;
  statement = db.createStatement() ;
 }
 
 /**
 * Execute an sql query
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return  ResultSet object containing results of the query. Must be closed.
 */
 public ResultSet read( String sql ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { return read0( sql ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return null ;
 } 

 /**
 * Execute an sql query
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return  ResultSet object containing results of the query. Must be closed.
 */
 public ResultSet read0( String sql ) throws Exception
 { 
   if ( LOG.isTraceEnabled() )
       LOG.trace( "SQL: " + sql ) ;
   return statement.executeQuery( sql ) ;
 }

 /**
 * Read an array of Strings
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return  array of Strings returned by the query 
 */
 public String[] readStrings( String sql ) throws Exception
 {
   String[] values = null ;
   int count, i = 0 ;
   Statement stmt = db.createStatement( ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE ) ;
   stmt.setFetchSize(25);
   
   ResultSet result = stmt.executeQuery( sql ) ;
   for ( count = 0 ; result.next() ; count++ ) ;
   if ( count > 0 )
    {
     result.beforeFirst() ;
     values = new String[ count ] ;
     while( result.next() )
      {
       values[ i ] = result.getString( 1 ) ;
       i++ ;
      }
     }
   result.close() ;
   stmt.close() ;
   return values ;
 }

 /**
 * Read an integer value
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return integer value
 */
 public int readInt( String sql ) throws Exception
 {
  int value = -1 ;
  ResultSet result = read( sql ) ;
  if ( result.next() ) value = result.getInt( 1 ) ;
  result.close() ;
  return value ;
 }

 /**
 * Read a String value
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return String value
 */
 public String readString( String sql ) throws Exception
 {
  String value = null ;
  ResultSet result = read( sql ) ;
  if ( result.next() ) value = result.getString( 1 ) ;
  result.close() ;
  return value ;
 }

 /**
 * Read a long value
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return long value
 */
 public long readLong( String sql ) throws Exception
 {
  long value = -1 ;
  ResultSet result = read( sql ) ;
  if ( result.next() ) value = result.getLong( 1 ) ;
  result.close() ;
  return value ;
 }
 
 /**
 * Update a set of records in the db, insert them if needed
 * 
 * @param update   String sql statement that updates value(s) in the db
 * @param insert   String sql statement that creates new records, or null
 */
 public int write( String update, String insert ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { return write0( update, insert ) ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
  return -1 ; // can't happen
 } 

 /**
 * Update a set of records in the db, insert them if needed
 * 
 * @param update   String sql statement that updates value(s) in the db
 * @param insert   String sql statement that creates new records, or null
 */
 public int write0( String update, String insert )
 throws Exception
 {
  if ( LOG.isTraceEnabled() )
		   LOG.trace( "SQL: " + update ) ;
  int count = statement.executeUpdate( update ) ;
  if ( count == 0 && insert != null ) 
	  { 
	    if ( LOG.isTraceEnabled() )
		   LOG.trace( "SQL: " + update ) ;
	    count = statement.executeUpdate( insert ) ;
	  }
  return count ;
 }
 
 /**
 * Write an array of objects
 * 
 * @param arr   array of objects to write
 * @param sqlStart  start of the sql statement, before an object 
 * @param sqlEnd    second part of the sql statement, after the object 
 */
 public void write( Object[] arr, String sqlStart, String sqlEnd ) throws Exception
 {
   if ( arr != null && arr.length != 0 )
    for ( int i = 0 ; i < arr.length ; i++ )
     {
      String ap = "\'" ; String cl = arr[i].getClass().getName() ;
      if ( !cl.equals( "java.lang.String" ) ) ap = "" ;
      String sql = sqlStart + ap + arr[i] + ap + sqlEnd ;
      write( sql, null ) ;
     }
 }
 
 /**
 * Write an array of Strings
 * 
 * @param arr   array of Strings to write
 * @param sqlStart  start of the sql statement, before a String
 * @param sqlEnd    second part of the sql statement, after the String
 */
 public void write( String[] arr, String sqlStart, String sqlEnd ) throws Exception
 {
  for ( int i = 0 ; i <= 1 ; i++ )
  { try { write0( arr, sqlStart, sqlEnd ) ; return ; }
     catch ( Exception e ) { if ( i > 0 ) throw e ; else connect() ; }
  }
 } 
 
 /**
 * Write an array of Strings
 * 
 * @param arr   array of Strings to write
 * @param sqlStart  start of the sql statement, before a String
 * @param sqlEnd    second part of the sql statement, after the String
 */
 public void write0( String[] arr, String sqlStart, String sqlEnd ) throws Exception
 {
  // have to use prepared statement to ensure proper string encoding
  PreparedStatement statement = db.prepareStatement( sqlStart + "?" + sqlEnd ) ;
  if ( arr != null && arr.length != 0 )
   for ( int i = 0 ; i < arr.length ; i++ )
    {
     statement.setString( 1, arr[i] ) ;
     if ( LOG.isTraceEnabled() )
          LOG.trace( "SQL: " + statement.toString() ) ;
     statement.executeUpdate() ;
    }
  statement.close() ;
 }
 
 /**
 * Execute an sql statement that does not produce a result set
 * 
 * @param sql   String sql statement 
 */
 public int execute( String sql ) throws Exception
 {  return write( sql, null ) ; }
 
 
 /**
  * Get Arch configuration object
  *
  * @return ConfigList cfg 
  */ 
 public ConfigList getCfg()
 { return cfg ; }

 
 @Override
 public void begin() throws Exception
 {
   statement.execute( "BEGIN" ) ;
	
 }

 
 @Override
 public void rollback() throws Exception
 {
   statement.execute( "ROLLBACK" ) ;
	
 }

 
 @Override
 public void commit() throws Exception
 {
   statement.execute( "COMMIT" ) ;
	
 }


}
