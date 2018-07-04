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
public class IndexDBMySQL extends IndexDBBaseImpl
{
 public static final Logger LOG = LoggerFactory.getLogger( IndexDBMySQL.class ) ;
 
/**
 * Constructor
 * 
 * @param cfg   configuration parameters
 */
 public IndexDBMySQL( ConfigList cfg )
 { super( cfg ) ; }
 
 /**
 * IndexDBImpl factory
 * 
 * @param cfg   configuration parameters
 * @return  conected IndexDBImpl object
 */
 public static IndexDBMySQL newIndexDBMySQL( ConfigList cfg ) throws Exception
 {
   IndexDBMySQL idb = new IndexDBMySQL( cfg ) ;
   idb.connect() ;
   return idb ;
 }
}
