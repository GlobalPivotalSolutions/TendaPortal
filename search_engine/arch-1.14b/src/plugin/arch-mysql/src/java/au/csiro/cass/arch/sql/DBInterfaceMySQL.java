/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import org.apache.hadoop.conf.Configuration ;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.utils.ConfigList;

public class DBInterfaceMySQL implements DBInterface
{
 Configuration conf ;
 
 public DBInterfaceMySQL() {} ;
  
/**
 * Returns a new object implementing IndexRootDB
 * 
 * @param cfg   configuration parameters
 */
 public IndexRootDBMySQL newIndexRootDB( ConfigList cfg, boolean servletMode ) throws Exception
 { 
  return IndexRootDBMySQL.newIndexRootDBMySQL( cfg, servletMode ) ;    
 } 
 
/**
 * Returns a new object implementing IndexSiteDB
 * 
 * @param cfg   configuration parameters
 */
 public IndexSiteDBMySQL newIndexSiteDB( ConfigList cfg, String site, boolean servletMode ) throws Exception
 { 
  return IndexSiteDBMySQL.newIndexSiteDBMySQL( cfg, site, servletMode ) ;    
 }
 
 public Configuration getConf()
 {
   return conf;
 }

 public void setConf( Configuration conf )
 {
   this.conf = conf;
 }
 
}
