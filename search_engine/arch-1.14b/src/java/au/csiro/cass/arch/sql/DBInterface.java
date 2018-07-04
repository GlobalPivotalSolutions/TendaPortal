/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import org.apache.hadoop.conf.Configurable;
import org.apache.nutch.plugin.Pluggable;

import au.csiro.cass.arch.logProcessing.LogLineParser;
import au.csiro.cass.arch.utils.ConfigList;


/**
 * A set of db connection factories
 * 
 * @author Arkadi Kosmynin
 *
 */
public interface DBInterface extends Pluggable, Configurable
{
 public static final String X_POINT_ID = DBInterface.class.getName();
 /** Max allowed length of path part of a URL */
 public static final int MAX_PATH_LENGTH = 2000 ;
 /** Max allowed length of name part of a URL */
 public static final int MAX_NAME_LENGTH = 2000 ;
 
  
 /**
 * Returns a new object implementing IndexRootDB
 * 
 * @param cfg   configuration parameters
 */
 IndexRootDB newIndexRootDB( ConfigList cfg, boolean servletMode ) throws Exception ;

 /**
 * Returns a new object implementing IndexSiteDB
 * 
 * @param cfg   configuration parameters
 */
 IndexSiteDB newIndexSiteDB( ConfigList cfg, String site, boolean servletMode ) throws Exception ;
 
}
