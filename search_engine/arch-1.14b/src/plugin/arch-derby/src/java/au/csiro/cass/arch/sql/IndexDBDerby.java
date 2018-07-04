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
public class IndexDBDerby extends IndexDBBaseImpl
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexDBDerby.class ) ;
 
/**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexDBDerby( ConfigList cfg )
 { super( cfg ) ; }

  
 /**
 * IndexDBImpl factory
 * 
 * @param cfg   configuration parameters
 * @return  conected IndexDBImpl object
 */
 public static IndexDBDerby newIndexDBDerby( ConfigList cfg ) throws Exception
 {
   IndexDBDerby idb = new IndexDBDerby( cfg ) ;
   idb.connect() ;
   return idb ;
 }
 
 /**
 * Close the db connection
 */
 @Override
 public void close() throws Exception
 { if ( db != null )
      { db.commit() ; db.close() ; db = null ; }
 }


 @Override
 public void begin() throws Exception
 {
   db.commit() ;
   db.setAutoCommit( false ) ;
 }

 
 @Override
 public void rollback() throws Exception
 {
   db.rollback() ;
   db.setAutoCommit( true ) ;
 }

 
 @Override
 public void commit() throws Exception
 {
   db.commit() ;
   db.setAutoCommit( true ) ;
 }

 
 
}
