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


/** Creates and caches {@link Pruner} plugins.*/
public final class PrunerFactory
{
  
  public static final Logger LOG = LoggerFactory.getLogger( PrunerFactory.class ) ;
    
  private Configuration conf ;
  private ExtensionPoint extensionPoint ;
  ArrayList<Pruner> pruners ;
  private static PrunerFactory factory ;

  PrunerFactory( Configuration conf ) throws Exception
  {
    this.conf = conf;
    extensionPoint = PluginRepository.get( conf ).getExtensionPoint( Pruner.X_POINT_ID ) ;
    if ( extensionPoint == null )
        throw new RuntimeException( "x point " + Pruner.X_POINT_ID + " not found." ) ;
    Extension[] extensions = this.extensionPoint.getExtensions() ;
    pruners = new ArrayList<Pruner>() ;
    // First add blockers, then pruners
    for ( int i = 0 ; i < extensions.length ; i++ )
    	if ( extensions[ i ].getExtensionInstance() instanceof Blocker ) 
    		pruners.add( (Pruner)extensions[ i ].getExtensionInstance() ) ;
    for ( int i = 0 ; i < extensions.length ; i++ )
    	if ( !( extensions[ i ].getExtensionInstance() instanceof Blocker ) ) 
    	    pruners.add( (Pruner)extensions[ i ].getExtensionInstance() ) ;
  }
  
  public static ArrayList<Pruner> getPruners( Configuration conf ) throws Exception  
  { if ( factory == null ) factory = new PrunerFactory( conf ) ; return factory.pruners ;  }
  
}
