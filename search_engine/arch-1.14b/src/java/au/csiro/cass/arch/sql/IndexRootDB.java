/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.logProcessing.LogFile;
import au.csiro.cass.arch.security.ScanAlert;
import au.csiro.cass.arch.utils.ConfigList;

/**
 * A set of db functions concerning tables shared by all sites and areas
 * 
 * @author Arkadi Kosmynin
 *
 */
public interface IndexRootDB extends ServletDB, IndexDB
{

 /**
 * Register (a new) site
 * 
 * @param site  name of the site
 * @param baseURL base url of the site, e.g. http://www.mysite.com
 */
 void regSite( String site, String baseURL ) throws Exception ;

 /**
 * Saves site configuration to the db
 * 
 * @param site  name of the site
 * @param cfg configuration object
 */
 void writeConfig( String site, ConfigList cfg ) throws Exception ;

 /**
 * Reads site configuration from the db
 * 
 * @param site  name of the site
 * @return site configuration object
 */
 ConfigList readConfig( String site ) throws Exception ;

 /**
 * Get date of a log processing point 
 * 
 * @param point   log processing point
 * @param site    site name
 * @return Date of the log processing point
 */
 Date getProcessed( String point, String site ) throws Exception ;

 /**
 * Write date of a log processing point 
 * 
 * @param point   log processing point
 * @param site    site name
 * @param value   date to write
 */
 void putProcessed( String point, String site, Date value )
                                                       throws Exception ;

 /**
 * Saves log file info 
 * 
 * @param logFile logFile object
 * @param site    site name
 */
 void saveLogFile( LogFile logFile, String site ) throws Exception ;

 /**
 * Reads log file info 
 * 
 * @param logFile logFile object, must contain log file name
 * @param site    site name
 * @return  true if log file was found, else false
 */
 boolean readLogFile( LogFile logFile, String site ) throws Exception ;

 /**
 * Deletes info on all log files for a given site
 * 
 * @param site    site name
 */
 void deleteLogFiles( String site ) throws Exception ;

 
 /**
 * Deletes info on log processing points for a given site
 * 
 * @param site    site name
 */
 void resetWeights( String site ) throws Exception ;
 
 
 /**
 * For a given site, for each log file, deletes info on whether the log file
 * was used to construct the list of blocked IPs
 * 
 * @param site    site name
 */
 void resetIPs( String site ) throws Exception ;
 
 
 /**
 * Drops all central tables 
 */
 void resetAll() throws Exception ;
 
  /**
  *  Closes database connection if it is open.
  */
 void close() throws Exception ;
 
  
 /**
  *  Constructs a html select element for site selection.
  *  
  *  @param selected   name of selected site that will be marked as SELECTED
  *  @return    text of select element ready to insert in a html file
  */
 String getSiteSelect( String selected ) throws Exception ;
  

 /**
  *  Constructs a html select element for area selection.
  *  
  *  @param selected   name of selected area that will be marked as SELECTED
  *  @return    text of select element ready to insert in a html file
  */
 String getAreaSelect( String selected ) throws Exception ;
  
 
 /**
  *  Constructs a group select html select element where areas are groupped by site.
  *  
  *  @param selectedSite   name of site containing selected area, or null
  *  @param selectedArea   name if selected area, if selectedSite is not null
  *  @return    text of group select element ready to insert in a html file
  */
 String getGroupSelect( String selectedSite, String selectedArea ) throws Exception ;
 
 /**
  * Read db stored information on this area
  * 
  * @param area IndexArea object, must have name and site set
  */
 void readDbValues( IndexArea area ) throws Exception ;

  /**
  * Write db stored information on this area
  * 
  * @param area IndexArea object
  * @param arraysToo    if true, then db stored arrays will be saved too
  */
 void writeDbValues( IndexArea area, boolean arraysToo ) throws Exception ;
  
  /**
   * Marks given area for crawling. This mark is used in filters.
   *
   * @param site site name
   * @param area area name
   * @param value 0 - not to crawl, 1 - to crawl
   * 
   * @throws Exception
   */ 
 void markForCrawl( String site, String area, int value ) throws Exception ;
  
  /**
   * Marks areas marked for crawling as indexed. Sets indexing time and build number.
   *
   * @param buildNumber - latest build number
   * 
   * @throws Exception
   */ 
 void markIndexed( int buildNumber ) throws Exception ;
  
  
  /**
   * Reads roots, includes and excludes of marked areas to a Map
   *
   * @return a map of root, include and exclude paths for all areas marked for crawling
   * @throws Exception
   */ 
  Map readRoots() throws Exception ;
  
  
  /**
   * Erases alerts table, reconnects if needed
   *
   * @throws Exception
   */ 
  void eraseAlerts() throws Exception ;
   

  /**
   * Adds a scan alert to the alerts table, reconnects if needed
   *
   * @param alert to add
   *  
   * @throws Exception
   */ 
  void addAlert( ScanAlert alert ) throws Exception ;
     

  /**
   * Reads scan alerts for a site ordered by site, then url, then code.
   * Reconnects if needed.
   *
   * @param site name or null if read for all sites
   * @return ResultSet for iteration using nextAlert 
   *  
   * @throws Exception
   */ 
  ResultSet readAlerts( String site ) throws Exception ;

  
  /**
   * Returns next scan alert.
   *
   * @param ResultSet for iteration using nextAlert
   * @return scan alert 
   *  
   * @throws Exception
   */ 
  ScanAlert nextAlert( ResultSet alerts ) throws Exception ;


}
