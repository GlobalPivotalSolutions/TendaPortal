/**
 
 */
package au.csiro.cass.arch.security;

import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.plugin.Extension;
import org.apache.nutch.plugin.ExtensionPoint;
import org.apache.nutch.plugin.PluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/** Creates and caches {@link Reporter} plugins.*/
public final class ReporterFactory {
  
  public static final Logger LOG = LoggerFactory.getLogger( ReporterFactory.class ) ;
    
  private Configuration conf ;
  private ExtensionPoint extensionPoint ;
  ArrayList<Reporter> reporters ;
  private static ReporterFactory factory ;

  ReporterFactory( Configuration conf ) throws Exception
  {
    this.conf = conf;
    extensionPoint = PluginRepository.get( conf ).getExtensionPoint( Reporter.X_POINT_ID ) ;
    if ( extensionPoint == null )
        throw new RuntimeException( "x point " + Reporter.X_POINT_ID + " not found." ) ;
    Extension[] extensions = this.extensionPoint.getExtensions() ;
    reporters = new ArrayList<Reporter>() ;
    for ( int i = 0 ; i < extensions.length ; i++ )
    	reporters.add( (Reporter)extensions[ i ].getExtensionInstance() ) ;
  }
  
  public static ArrayList<Reporter> getReporters( Configuration conf ) throws Exception  
  { if ( factory == null ) factory = new ReporterFactory( conf ) ; return factory.reporters ;  }
  
}
