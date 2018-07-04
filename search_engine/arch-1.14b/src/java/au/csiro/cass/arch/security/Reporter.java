/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.security;

import org.apache.hadoop.conf.Configurable;

import au.csiro.cass.arch.index.IndexSite;
import au.csiro.cass.arch.index.Indexer;

/** A reporter interface. Reporter handles alerts found by scanners.
 * This interface is implemented by extensions. 
 */
public interface Reporter extends Configurable
{
  /** The name of the extension point. */
  public final static String X_POINT_ID = Reporter.class.getName();

  /** 
   * Scans the given content for threats and vulnerabilities
   * 
   * @param context scanning context
   * @param scanResult results of scanning
   */
  void report( Indexer indexer, IndexSite site ) throws Exception ;
}
