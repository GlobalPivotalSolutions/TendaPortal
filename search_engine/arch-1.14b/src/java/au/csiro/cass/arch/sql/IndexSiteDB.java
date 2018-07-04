/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import au.csiro.cass.arch.auth.Permissions;
import au.csiro.cass.arch.index.IndexArea;
import au.csiro.cass.arch.index.LogLinks;
import au.csiro.cass.arch.logProcessing.ScoreFile;
import au.csiro.cass.arch.security.ScanResult;

/**
 * A set of db related functions needed by IndexSite and LogSite objects
 * 
 * @author Arkadi Kosmynin
 *
 */
public interface IndexSiteDB extends ServletDB, IndexDB
{
  /**
   * Read <url, weight> pairs and export them to a stream in SiteMap xml format
   * 
   * @param file
   *          output file
   */

  void exportSiteMap( OutputStream o, String baseURL, float norm ) throws Exception;

  /**
   * Read <url, weight> pairs from a stream and save them to the db
   * 
   * @param file
   *          output file
   */
  void importSiteMap( BufferedReader rd, String baseURL, float norm ) throws Exception;

  /**
   * Read <url, score> pairs and export them to a file
   * 
   * @param file
   *          output file
   */

  void scores2file( ScoreFile file ) throws Exception;

  /**
   * Read <url, score> pairs from a file and save them to the db
   * 
   * @param file
   *          output file
   */
  void file2scores( ScoreFile file ) throws Exception;

  /**
   * Init reading document scores from the db
   * 
   * @return ResultSet object to obtain scores from
   */

  ResultSet listScores() throws Exception;

  /**
   * Obtain max document weight value from the db
   * 
   * @return max weight
   */
  float getMaxWeight() throws Exception;

  /**
   * Obtain max document score value from the db
   * 
   * @return max score
   */
  int getMaxScore() throws Exception;

  /**
   * Reset document scores
   * 
   */
  void resetScores() throws Exception;

  /**
   * Update weight of a document
   * 
   * @param id
   *          document id in the database
   * @param weight
   *          weight value to set
   */
  void updateWeight( int id, float weight ) throws Exception;

  /**
   * Multiply scores in the db by a given koefficient
   * 
   * @param koeff
   *          koefficient
   */
  void normaliseWeights( float koeff ) throws Exception;

  /**
   * Update or insert a document score
   * 
   * @param path
   *          path to the document
   * @param name
   *          document name
   * @param score
   *          document score
   * @param insert
   *          if true, a record will be created if no such document
   */
  void writeScore( String path, String name, float score, boolean insert ) throws Exception;

  /**
   * Read weight of a document
   * 
   * @param id
   *          db id of the document
   * @return document weight
   */
  float getWeight( int id ) throws Exception;

  /**
   * Read weight of a document
   * 
   * @param url
   *          url of the document
   * @return document weight
   */
  float getWeight( String url ) throws Exception;

  /**
   * Create a document record + all ancestors, if it does not exist
   * 
   * @param url
   *          url of the document
   * @param markIndexed
   *          if true, mark this document as indexed
   * @return document IndexNode
   */
  IndexNode makeSureExists( String url, boolean markIndexed ) throws Exception;

  /**
   * Apply permissions set via configuration file
   * 
   * @param permissions
   *          array of Permissions objects
   */
  void applyPermissions( Permissions[] permissions ) throws Exception;

  /**
   * Read urls existing in the db, not found by the crawler, to injector input file
   * 
   * @param area
   *          area for which to do this
   * @return int count of output URLs
   */
  int readUnindexedURLs( LogLinks area ) throws Exception;

  /**
   * Mark all site URLs as indexed or not, depending on the parameter
   * 
   * @param boolean indexed - mark as indexed if true
   */
  void markIndexed( boolean indexed ) throws Exception;

  /**
   * Read arrays of inclusions, exclusions and roots for this area
   * 
   * @param area
   *          area for which to do this
   */
  void readParams( IndexArea area ) throws Exception;

  /**
   * Write inclusions, exclusions and roots of this area to the db
   * 
   * @param area
   *          area for which to do this
   */
  void writeParams( IndexArea area ) throws Exception;

  /**
   * Close db connection, release resources
   */
  void close() throws Exception;

  /**
   * Connect to the db, init statements
   */
  void connect() throws Exception;

  /**
   * Reset document weights
   */
  void resetWeights() throws Exception;

  /**
   * Drop site db table
   */
  void resetAll() throws Exception;

  // These are needed by IndexRootDB, so, have to publish them

  /**
   * Read document node info from the database, re-connect if needed The document is identified by either the id, or
   * path and name.
   * 
   * @param id
   *          id of the document
   * @param path
   *          path to the document
   * @param name
   *          name of the document
   * @param type
   *          "d" - directory, "f" - file
   * @return document id
   */
  IndexNode getNode( int id, String path, String name, String type ) throws Exception;

  /**
   * Write/update document node info, re-connect if needed
   * 
   * @return document id
   */
  int updateNode( IndexNode n ) throws Exception;

  /**
   * @return the site table name
   */
  String getTable();

  /**
   * Write a scan result, re-connect if needed
   * 
   * @param result
   *          scan result to save
   */
  void writeScanResult( ScanResult result ) throws Exception;

  /**
   * Read a scan result, re-connect if needed
   * 
   * @param url
   *          url of the page
   * @return ScanResult scan result
   */
  ScanResult readScanResult( String url ) throws Exception;

  /**
   * Add a link, re-connect if needed
   * 
   * @param src
   *          url of link source
   * @param srcId
   *          id of link source if available
   * @param target
   *          url of link target
   * @return id of link source
   */
  int addLink( String src, int srcId, String target ) throws Exception;

  /**
   * Remove a link, re-connect if needed
   * 
   * @param src
   *          url of link source
   * @param srcId
   *          id of link source if available
   * @param target
   *          url of link target
   * @return id of link source
   */
  int removeLink( String src, int srcId, String target ) throws Exception;

  /**
   * Read links, re-connect if needed
   * 
   * @param src
   *          url of link source
   * @param srcId
   *          id of link source if available
   * @return result set containing links
   */
  Set readLinks( String src, int srcId ) throws Exception;

  /**
   * Get sum of scores and max weight in a group of aliases
   * 
   * @param aliasOf
   *          id of the main alias
   * @param paramContainer
   *          container to return weight in
   * @return sum of scores
   */
  int getSumScores( int aliasOf, float[] paramContainer ) throws Exception;

  /**
   * Register the fact that two URLs are aliases of the same page
   * 
   * @param url1
   *          first alias
   * @param url2
   *          second alias
   * @param markIndexed
   *          mark both URLs as indexed
   * @return IndexNode object corresponding to the first URL
   */
  IndexNode registerAlias( String url1, String url2, boolean markIndexed ) throws Exception;

  /**
   * Recursively delete a chain of document nodes, re-connect if needed. The terminal document is identified by either
   * the id, or path and name.
   * 
   * @param id
   *          id of the terminal document
   * @param path
   *          path to the document
   * @param name
   *          name of the document
   * @param type
   *          type of the node: "d" - directory, "f" - file
   * @return document id
   */
  public void deleteCascade( int id, String path, String name, String type ) throws Exception;

  /**
   * Resets aliases information for this site
   * 
   * @throws SQLException
   *           in case of DB problems
   * 
   */
  void resetAliasof() throws SQLException;

}