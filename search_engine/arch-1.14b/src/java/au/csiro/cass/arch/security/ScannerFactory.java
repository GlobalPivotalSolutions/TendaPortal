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


/** Creates and caches {@link Scanner} plugins.*/
public final class ScannerFactory {
  
  public static final Logger LOG = LoggerFactory.getLogger( ScannerFactory.class ) ;
    
  private Configuration conf ;
  private ExtensionPoint extensionPoint ;
  ArrayList<Scanner> scanners ;
  private static ScannerFactory factory ;

  ScannerFactory( Configuration conf ) throws Exception
  {
    this.conf = conf;
    extensionPoint = PluginRepository.get( conf ).getExtensionPoint( Scanner.X_POINT_ID ) ;
    if ( extensionPoint == null )
        throw new RuntimeException( "x point " + Scanner.X_POINT_ID + " not found." ) ;
    Extension[] extensions = this.extensionPoint.getExtensions() ;
    scanners = new ArrayList<Scanner>() ;
    for ( int i = 0 ; i < extensions.length ; i++ )
    	scanners.add( (Scanner)extensions[ i ].getExtensionInstance() ) ;
  }
  
  public static ArrayList<Scanner> getScanners( Configuration conf ) throws Exception  
  { if ( factory == null ) factory = new ScannerFactory( conf ) ; return factory.scanners ;  }
  
}
