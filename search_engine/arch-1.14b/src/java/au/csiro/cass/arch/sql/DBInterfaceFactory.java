/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.plugin.Extension;
import org.apache.nutch.plugin.ExtensionPoint;
import org.apache.nutch.plugin.PluginRepository;
import org.apache.nutch.plugin.PluginRuntimeException;

public class DBInterfaceFactory
{
 /**
 * Creates and caches {@link DBInterface} plugins.
 *
 * @author Arkadi Kosmynin
 */
  
  private static DBInterfaceFactory factory ;
  
  private HashMap cache ; 
  
  private ExtensionPoint extensionPoint ;
  private Configuration conf ;

  public DBInterfaceFactory( Configuration conf )
  {
      this.conf = conf;
      cache = new HashMap() ;
      this.extensionPoint = PluginRepository.get(conf).getExtensionPoint(DBInterface.X_POINT_ID);
      if ( this.extensionPoint == null )
      {
          throw new RuntimeException("x point " + DBInterface.X_POINT_ID +
          " not found.");
      }
  }

  
  /**
   * Get or create a DB interface factory given a configuration object.
   * @param conf - configuration object.
   * @return DB interface factory.
   */
  public static DBInterfaceFactory get( Configuration conf )
  {
    if ( factory == null )
      factory = new DBInterfaceFactory( conf ) ;
    return factory;
  }
  
  /**
   * Returns the appropriate {@link DBInterface interface} implementation
   * given a database name (Oracle, MySQL etc.).
   *
   * <p>DBInterface extensions should define the attribute "database". The first
   * plugin found whose "database" attribute equals the specified database parameter is
   * used. If none match, then the {@link DBInterfaceMySQL} is used.
   */
  public DBInterface get( String database ) throws Exception
  {

    DBInterface intr = null ;
    Extension extension = getExtension( database ) ;
    if ( extension != null )
    {
      intr = (DBInterface) extension.getExtensionInstance() ;
    } else throw new Exception( "Can't find DBInterface for " + database ) ;
    return intr ;
  }

  /**
   * Returns the appropriate Nutch extension given a database name (Oracle, MySQL etc.).
   * @return extension 
   */  
  private Extension getExtension( String database )
  {
    if ( database == null ) { return null ; }
    Extension extension = (Extension) cache.get( database ) ;
    if ( extension == null )
     {
      extension = findExtension( database ) ;
      if ( extension != null )
      {
        cache.put( database, extension ) ;
      }
  }
    return extension;
  }

  /**
   * Returns the appropriate Nutch extension given a database name (Oracle, MySQL etc.).
   * @return extension 
   */  
  private Extension findExtension( String database )
  {

    if ( database != null )
    {
      Extension[] extensions = this.extensionPoint.getExtensions() ;
      for ( int i = 0 ; i < extensions.length ; i++ )
      {
        if ( database.equals( extensions[ i ].getAttribute( "database" ) ) )
                                                         return extensions[ i ] ;
      }
    }
    return null;
  }
}
