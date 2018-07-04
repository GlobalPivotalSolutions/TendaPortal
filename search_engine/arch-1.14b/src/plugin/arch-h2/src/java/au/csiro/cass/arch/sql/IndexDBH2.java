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
public class IndexDBH2 extends IndexDBBaseImpl
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexDBH2.class ) ;
 
/**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexDBH2( ConfigList cfg )
 { super( cfg ) ; }

  
 /**
 * IndexDBImpl factory
 * 
 * @param cfg   configuration parameters
 * @return  conected IndexDBImpl object
 */
 public static IndexDBH2 newIndexDBH2( ConfigList cfg ) throws Exception
 {
   IndexDBH2 idb = new IndexDBH2( cfg ) ;
   idb.connect() ;
   return idb ;
 } 
 
}
