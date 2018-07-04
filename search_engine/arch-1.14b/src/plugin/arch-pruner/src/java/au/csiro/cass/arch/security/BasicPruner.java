/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * A reference blocker implementation
 * 
 */
package au.csiro.cass.arch.security;

import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.csiro.cass.arch.utils.Utils;


public class BasicPruner implements Pruner
{
  public static final Logger LOG = LoggerFactory.getLogger( BasicPruner.class ) ;
  Configuration        conf ;
  
  /**
   * This function is being called before parsing
   * Removes unwanted fragments from fetched pages.
   *  
   */
  @Override
  public String preParsePrune( ScanContext context, String content ) throws Exception
  {
    // cut out bits to ignore
    for ( String[] pair : context.config.ignoredBits )
    {
      if ( pair.length < 4 ) throw new Exception( "Incomplete range definition " + pair[0] ) ;
      boolean removeStart = pair[ 0 ].charAt( 0 ) == '[' ;
      boolean removeEnd = pair[ 3 ].charAt( 0 ) == ']' ;
      content = Utils.cutOut( content, pair[ 1 ], pair[ 2 ], true, removeStart, removeEnd ) ;
    }
    return content ;
  }
  
  /**
   * This function is being called after parsing
   * Removes unwanted fragments from fetched pages.
   *  
   */
  @Override
  public String postParsePrune( ScanContext context, String content ) throws Exception
  {
    if ( LOG.isTraceEnabled() )
      LOG.trace( "Content before after parsing prunning: " + content );
    // cut out bits to ignore
    for ( String[] pair : context.config.ignoredBitsAfter )
    {
      if ( pair.length < 4 ) throw new Exception( "Incomplete range definition " + pair[0] ) ;
      boolean removeStart = pair[ 0 ].charAt( 0 ) == '[' ;
      boolean removeEnd = pair[ 3 ].charAt( 0 ) == ']' ;
      content = Utils.cutOut( content, pair[ 1 ], pair[ 2 ], true, removeStart, removeEnd ) ;
    }
    if ( LOG.isTraceEnabled() )
      LOG.trace( "Content after after parsing prunning: " + content );
    return content ;
  }
  
  @Override
  public void setConf( Configuration conf )
  {
    this.conf = conf ;
  }

  @Override
  public Configuration getConf()
  { return conf ; }

}
