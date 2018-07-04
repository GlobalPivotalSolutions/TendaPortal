/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.security;

import org.apache.hadoop.conf.Configurable;

/** A pruner for content generated by a {@link org.apache.nutch.protocol.Protocol}
 *  implementation. This interface is implemented by extensions. 
 */
public interface Pruner extends Configurable
{
  /** The name of the extension point. */
  public final static String X_POINT_ID = Pruner.class.getName();

  /** 
   * This function is being called before parsing
   * Prunes the given content: cuts out bits to ignore
   * 
   * @param context scanning context
   */
  String preParsePrune( ScanContext context, String content ) throws Exception ;

  /** 
   * Prunes the given content: cuts out bits to ignore
   * 
   * @param context scanning context
   */
  String postParsePrune( ScanContext context, String content ) throws Exception ;
}
