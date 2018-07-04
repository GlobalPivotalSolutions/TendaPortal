/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.sql;

import java.sql.ResultSet;

import au.csiro.cass.arch.utils.ConfigList;

/**
 * A set of generic utility functions useful for all db interacting objects
 * 
 * @author Arkadi Kosmynin
 *
 */
public interface IndexDB
{
 /**
 * Execute an sql query
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return  ResultSet object containing results of the query. Must be closed.
 */
 ResultSet read( String sql ) throws Exception ;

 /**
 * Read an array of Strings
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return  array of Strings returned by the query 
 */
 String[] readStrings( String sql ) throws Exception ;

 /**
 * Read an integer value
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return integer value
 */
 int readInt( String sql ) throws Exception ;

 /**
 * Read a String value
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return String value
 */
 String readString( String sql ) throws Exception ;
 
 /**
 * Read a long value
 * 
 * @param sql   String sql statement that is expected to produce a result set
 * @return long value
 */
 long readLong( String sql ) throws Exception ;

 /**
 * Update a set of records in the db, insert them if needed
 * 
 * @param update   String sql statement that updates value(s) in the db
 * @param insert   String sql statement that creates new records, or null
 */
 int write( String update, String insert ) throws Exception ;

 /**
 * Write an array of objects
 * 
 * @param arr   array of objects to write
 * @param sqlStart  start of the sql statement, before an object 
 * @param sqlEnd    second part of the sql statement, after the object 
 */
 void write( Object[] arr, String sqlStart, String sqlEnd ) throws Exception ;

 /**
 * Write an array of Strings
 * 
 * @param arr   array of Strings to write
 * @param sqlStart  start of the sql statement, before a String
 * @param sqlEnd    second part of the sql statement, after the String
 */
 void write( String[] arr, String sqlStart, String sqlEnd ) throws Exception ;

 /**
 * Execute an sql statement that does not produce a result set
 * 
 * @param sql   String sql statement 
 */
 int execute( String sql ) throws Exception ;

 /**
 * Close db connection 
 * 
 */
 void close() throws Exception ;
 
 /**
  * Get Arch configuration object
  *
  * @return ConfigList cfg 
  */ 
 public ConfigList getCfg() ;
 
 /**
  * Begin transaction
  *
  */ 
 public void begin() throws Exception ;
 
 /**
  * Commit transaction
  *
  */ 
 public void commit()  throws Exception  ;
 

 /**
  * Roll back transaction
  *
  */ 
 public void rollback()  throws Exception  ;
 

}

