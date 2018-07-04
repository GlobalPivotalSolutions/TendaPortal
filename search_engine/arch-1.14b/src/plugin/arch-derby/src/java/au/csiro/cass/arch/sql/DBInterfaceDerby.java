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

public class DBInterfaceDerby implements DBInterface
{
 Configuration conf ;
 
 public DBInterfaceDerby() {} ;
  
/**
 * Returns a new object implementing IndexRootDB
 * 
 * @param cfg   configuration parameters
 */
 public IndexRootDBDerby newIndexRootDB( ConfigList cfg, boolean servletMode ) throws Exception
 { 
  return IndexRootDBDerby.newIndexRootDBDerby( cfg, servletMode ) ;    
 } 
 
/**
 * Returns a new object implementing IndexSiteDB
 * 
 * @param cfg   configuration parameters
 */
 public IndexSiteDBDerby newIndexSiteDB( ConfigList cfg, String site, boolean servletMode ) throws Exception
 { 
  return IndexSiteDBDerby.newIndexSiteDBDerby( cfg, site, servletMode ) ;    
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
