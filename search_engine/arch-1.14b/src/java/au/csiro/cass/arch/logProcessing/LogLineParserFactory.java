/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.logProcessing;

import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.plugin.Extension;
import org.apache.nutch.plugin.ExtensionPoint;
import org.apache.nutch.plugin.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogLineParserFactory
{
/**
 * Creates and caches {@link LogLine} plugins.
 *
 * @author Arkadi Kosmynin
 */

  private final static String KEY = LogLineParserFactory.class.getName();
  
  public static final Logger LOG = LoggerFactory.getLogger( LogLineParserFactory.class ) ;

   
  private static LogLineParserFactory factory ;
  
  private HashMap cache ; 
  
  private ExtensionPoint extensionPoint ;
  private Configuration conf ;

  public LogLineParserFactory( Configuration conf, LogProcessor logProcessor )
  {
      this.conf = conf;
      cache = new HashMap() ;
      this.extensionPoint = PluginRepository.get(conf).getExtensionPoint(LogLineParser.X_POINT_ID);
      if ( this.extensionPoint == null )
      {
          throw new RuntimeException("x point " + LogLineParser.X_POINT_ID +
          " not found.");
      }
  }

  public static LogLineParserFactory get( Configuration conf, LogProcessor logProcessor )
  {
    if ( factory == null )
      factory = new LogLineParserFactory( conf, logProcessor ) ;
    return factory;
  }
  
  /**
   * Returns the appropriate {@link LogLineParser} implementation
   * given a log format.
   *
   * <p>LogLineParser extensions should define the attribute "logformat". The first
   * plugin found whose "logformat" attribute equals the specified logFormat parameter is
   * used. If none match, then the {@link LogLineParserCombined} is used.
   */
  public LogLineParser get( String logFormat ) throws Exception
  {

    LogLineParser parser = null ;
    Extension extension = getExtension( logFormat ) ;
    if ( extension != null )
    {
      parser = (LogLineParser) extension.getExtensionInstance() ;
    }
    return parser ;
  }

  private Extension getExtension( String logFormat )
  {
    if ( logFormat == null ) { return null ; }
    Extension extension = (Extension) cache.get( logFormat ) ;
    if ( extension == null )
     {
      extension = findExtension( logFormat ) ;
      if ( extension != null )
      {
        cache.put( logFormat, extension ) ;
      }
  }
    return extension;
  }

  private Extension findExtension( String logFormat )
  {

    if ( logFormat != null )
    {
      Extension[] extensions = this.extensionPoint.getExtensions() ;
      for ( int i = 0 ; i < extensions.length ; i++ )
      {
        if ( logFormat.equals( extensions[ i ].getAttribute( "format" ) ) )
                                                         return extensions[ i ] ;
      }
    }
    return null;
  }

}
