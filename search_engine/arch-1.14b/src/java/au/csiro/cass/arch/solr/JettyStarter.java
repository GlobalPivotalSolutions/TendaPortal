/**
 * 
 */
package au.csiro.cass.arch.solr;

import au.csiro.cass.arch.index.Indexer;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

/**
 * Starts Jetty server in background process, if it is not running 
 * @author Arkadi Kosmynin
 * Copyright CSIRO Australia
 *
 */
public class JettyStarter
{

  /**
   * @param args
   */
  public static void main( String[] args ) throws Exception
  {
	String nutchHome = System.getenv( "NUTCH_HOME" ) ;
	if ( nutchHome == null )
		throw new Exception( "Stopped: environment variable NUTCH_HOME is not set." ) ;
	String configFile = nutchHome + "/conf/arch/config.txt" ;
    ConfigList cfg = ConfigList.newConfigList( configFile ) ;
	Utils.startJetty( nutchHome, cfg ) ; // fork Jetty process if needed
  }

}
