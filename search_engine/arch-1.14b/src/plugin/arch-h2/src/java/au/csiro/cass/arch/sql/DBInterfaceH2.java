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

public class DBInterfaceH2 implements DBInterface
{
 Configuration conf ;
 
 public DBInterfaceH2() {} ;
  
/**
 * Returns a new object implementing IndexRootDB
 * 
 * @param cfg   configuration parameters
 */
 public IndexRootDBH2 newIndexRootDB( ConfigList cfg, boolean servletMode ) throws Exception
 { 
  return IndexRootDBH2.newIndexRootDBH2( cfg, servletMode ) ;    
 } 
 
/**
 * Returns a new object implementing IndexSiteDB
 * 
 * @param cfg   configuration parameters
 */
 public IndexSiteDBH2 newIndexSiteDB( ConfigList cfg, String site, boolean servletMode ) throws Exception
 { 
  return IndexSiteDBH2.newIndexSiteDBH2( cfg, site, servletMode ) ;    
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
