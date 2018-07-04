/*
 * Responsible for reading/writing Arch and Nutch configuration files.
 * Has very little to do with Solr. Could be designed to be deployed separately,
 * but is merged with Solr for the sake of compactness.
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.solr;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.velocity.VelocityContext;

import au.csiro.cass.arch.index.Indexer;
import au.csiro.cass.arch.sql.DBInterface;
import au.csiro.cass.arch.sql.DBInterfaceFactory;
import au.csiro.cass.arch.sql.IndexSiteDB;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;


public class ArchControlComponent extends SearchComponent
{
  public static Log LOG = LogFactory.getLog(AJAXServerComponent.class) ;
  public static String solrConfDir ;
	
  @Override
  public void prepare( ResponseBuilder rb ) throws IOException
  {}

  /**
   * Reads and writes Arch and Nutch configuration files and prepares
   * responses for response writer.
   * 
   */
  @Override
  public void process( ResponseBuilder rb ) throws IOException
  {
   VelocityContext context = new VelocityContext();
   try
	{
	  SolrQueryRequest req = rb.req ;
	  req.getContext().put( "ar_AJAXResponse", context ) ;
	  SolrCore core = req.getCore() ;
	  SolrParams params = req.getParams() ;
	  Utils.dumpRequest( LOG, req ) ;
	  if ( solrConfDir == null ) solrConfDir = SolrResourceLoader.locateSolrHome() + "/conf" ;
		  
	  String site = params.get( "ar_site" ) ;
	  // Authentication is expected to be done before and the user name must
	  // match admin.user in the configuration file.
      String user = (String)req.getContext().get( "ar_user" ) ;
      boolean isRoot = (String)req.getContext().get( "ar_isRoot" ) != null ;
        
      //==============================================================================================
      // Performing requested action
      
      String action = params.get( "ar_action" ) ;
      if ( action == null ) // send admin home page
          context.put( "template", "control" ) ;
         else if ( action.equals( "sendNode" ) )
 	       sendNode( req, context, params, isRoot ) ;
         else if ( action.equals( "sendConfig" ) )
   	       sendConfig( req, context, params, user, site, isRoot ) ;
         else if ( action.equals( "saveConfig" ) )
     	   saveConfig( context, params, user, site, isRoot ) ;
         else if ( action.equals( "sendTemplate" ) )
             context.put( "template", params.get( "ar_template" ) ) ;
         else if ( action.equals( "deleteSite" ) )
             deleteSite( context, params, user, site, isRoot ) ;
         else if ( action.equals( "validate" ) )
           validate( context, params ) ;
      
      if ( context.get( "template" ) == null ) context.put( "template", "ajax" ) ;
      if ( context.get( "code" ) == null ) context.put( "code", "++OK" ) ;

	} catch( Exception e )
	{
	  String msg =  e.getMessage() + "\n" ;
	  StringWriter stringWriter = new StringWriter() ;
	  PrintWriter printWriter = new PrintWriter( stringWriter );
	  e.printStackTrace( printWriter ) ;
	  msg += "\n" + stringWriter.getBuffer().toString() ;
      context.put( "code", "--Failed" ) ;
      context.put( "message", msg ) ;
      context.put( "template", "ajax" ) ;
	}
  }
  
  /**
   * Sends a root node, site node or area node
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param isRoot - true if the user is root admin.
   * 
   */
  void sendNode( SolrQueryRequest req, VelocityContext context, SolrParams params,
		         boolean isRoot ) throws Exception
  {
 //   if ( !isRoot ) throw new Exception( "SendNode Action requires root priveleges." ) ;
    File f = new File( this.solrConfDir + "/arch/sites" ) ;
    String id = params.get( "ar_id" ) ;
    if ( id == null ) throw new Exception( "ar_id is required for sendNode action." ) ;
    String[] aa = id.split( "_" ) ;
    String json ;
    if ( aa.length == 1 )      json = rootJson( f ) ;
    else if ( aa.length == 2 ) json = siteJson( f, aa[1] ) ;
                          else json = areaJson( f, aa[1], aa[2] ) ;
    context.put( "template", "json" ) ;
    context.put( "message", json ) ;
    req.getContext().put( "contentType", "application/json;charset=UTF-8" ) ;
  }

  /**
   * JSON encodes the root node
   * @param rootDir - root directory object.
   * @return JSON encoded root and list of sites
   */
  String rootJson( File rootDir ) throws Exception
  {
    File[] sites = rootDir.listFiles( new Indexer.SiteFilter( true ) ) ;
	StringBuffer json = new StringBuffer( "{ id:\"root\" , name: \"Arch\" , hasChildren : true , children: [ " ) ;
	boolean first = true ;
	for ( File file : sites )
	      { if ( !first ) json.append( ", " ) ; first = false ; 
	        json.append( "{ id:\"root_" ) ; json.append( file.getName() ) ;
	        json.append( "\" , name: \"" ) ; json.append( file.getName() ) ;
	        json.append( "\" , hasChildren : true }" ) ;
	      }	
	json.append( " ] } " ) ;
	return json.toString() ;
  }
  
  
  /**
   * JSON encodes a site node
   * @param rootDir - root directory object.
   * @param siteName - site name
   * @return JSON encoded root and list of sites
   */
  String siteJson( File rootDir, String siteName ) throws Exception
  {
	StringBuffer json = new StringBuffer( "{ id:\"root_" + siteName + "\" , name :\"" + siteName + 
			                                 "\" , hasChildren : true , children: [ " ) ;
	boolean first = true ;
	ConfigList cfg = ConfigList.newConfigList( rootDir.getCanonicalPath() + "/" + siteName + "/config.txt" ) ;
	String[] areas = cfg.getAll( "area" ) ;
	for ( String area : areas )
	      { if ( !first ) json.append( ", " ) ; first = false ;
	        json.append( "{ id:\"root_" + siteName + "_" + area ) ;
	        json.append( "\" , name: \"" ) ; json.append( area ) ;
	        json.append( "\" , hasChildren : false }" ) ;
	      }	
	json.append( " ] } " ) ;
	return json.toString() ;
  }
  
  
  /**
   * JSON encodes an area node
   * @param rootDir - root directory object.
   * @param siteName - site name
   * @param areaName - area name
   * @return JSON encoded root and list of sites
   */
  String areaJson( File rootDir, String siteName, String areaName ) throws Exception
  {
	// For now, just send the area node, without any children  
	String json = "{ id:\"root_" + siteName + "_" + areaName + 
	                 "\" , name : \"" + areaName + "\" , hasChildren : false } " ;
	return json ;
  }
  
  
  /**
   * Sends a configuration file in JSON encoding.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param site - site name or "/<file name>" for other configuration files
   * @param user - authenticated user name 
   * @param isRoot - true if the user is root admin.
   * 
   */
  void sendConfig( SolrQueryRequest req, VelocityContext context, SolrParams params,
		           String user, String site, boolean isRoot ) throws IOException 
  {
	boolean isAuthorised = checkAuthorised( user, site, isRoot ) ; // Make sure that the user has access
	if ( site == null || site.equals( "root" ) ) site = "/arch/config.txt" ;
	if ( !site.startsWith( "/" ) ) site = "/arch/sites/" + site + "/config.txt" ;
	String json ;
	String fileName = solrConfDir + site ;
	if ( !fileName.endsWith( ".xml" ) ) json = ConfigList.properties2JSON( fileName, !isAuthorised ) ;
	                               else json = ConfigList.XML2JSON( fileName, !isAuthorised ) ;
    context.put( "message", json ) ;
    context.put( "template", "json" ) ;
    req.getContext().put( "contentType", "application/json;charset=UTF-8" ) ;
  }

  
  /**
   * Saves a configuration file in JSON encoding.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param site - site name or "/<file name>" for other configuration files
   * @param user - authenticated user name 
   * @param isRoot - true if the user is root admin.
   * 
   */
  void saveConfig( VelocityContext context, SolrParams params, String user, String site, boolean isRoot )
  throws IOException 
  {
	if ( !checkAuthorised( user, site, isRoot ) ) // Make sure that the user has access
		throw new IOException( "You are not authorised to save configuration of site " + site ) ;
	if ( site == null || site.equalsIgnoreCase( "root" ) ) site = "/arch/config.txt" ;
	String json = params.get( "ar_json" ) ;
	// If site name has a dot in it, it must be an explicit file name
	if ( site.indexOf( "." ) < 0 ) 
		{ 
		  String dir = solrConfDir + "/arch/sites/" + site ;
		  File f = new File( dir ) ;
		  site = "/arch/sites/" + site + "/config.txt" ;
		  if ( !f.exists() )
			{ 
			  f.mkdirs() ;
			  String src = solrConfDir + "/arch/site.tmpl" ;
			  String dst = solrConfDir + site ;
			  // Utils.copyFile( dst, src ) ;
			}
		}
	String fileName = solrConfDir + site ;
	if ( fileName.indexOf( "/arch/" ) > 0 ) ConfigList.JSON2Properties( fileName, json ) ;
	                                   else ConfigList.JSON2XML( fileName, json ) ;
  }

  
  /**
   * Deletes a site directory
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param site - site name or "/<file name>" for other configuration files
   * @param user - authenticated user name 
   * @param isRoot - true if the user is root admin.
   * 
   */
  void deleteSite( VelocityContext context, SolrParams params, String user, String site, boolean isRoot )
  throws IOException 
  {
	if ( !checkAuthorised( user, site, isRoot ) ) // Make sure that the user has access
		throw new IOException( "You are not authorised to delete site " + site ) ;
	if ( site == null || site.equalsIgnoreCase( "root" ) || site.indexOf( "." ) >= 0 ) 
		throw new IOException( "Can't delete " + site ) ; // can't delete root config files
	String fileName = solrConfDir + "/arch/sites/" + site ;
    File f = new File( fileName ) ;
    if ( !f.exists() ) throw new IOException( "Does not exist: " + fileName ) ;
    try { Utils.rmdir( fileName, null ) ; }
     catch( Exception e ) { throw new IOException( e.getMessage() ) ; }
    if ( f.exists() ) throw new IOException( "Could not delete " + fileName ) ;
  }

  
  /**
   * Checks if the user has access to this configuration file.
   * @param site - site name or file name for other configuration files
   * @param user - authenticated user name 
   * @param isRoot - true if the user is root admin.
   * 
   */
  boolean checkAuthorised( String user, String site, boolean isRoot )
  throws IOException
  {
    if ( isRoot ) return true ; // take any file
    ConfigList cfg = ConfigList.newConfigList( solrConfDir + "/arch/config.txt" ) ;
    String admin = cfg.get( "admin.user", null ) ;
    if ( admin == null || 
         ( user != null && admin.equals( user ) ) ) return true ; // not secured, everyone is root

    if ( site == null || site.startsWith( "/" ) || site.endsWith( ".xml" ) ||
         site.equals( "root" ) ) return false ; // not root user, root config files
    // Else is trying to access a site config file. This is allowed, provided that the user is
    // authorised in the file, or no protection is set up.
    String fileName = solrConfDir + "/arch/sites/" + site + "/config.txt" ;
	cfg = ConfigList.newConfigList( fileName ) ;
    admin = cfg.get( "admin.user", null ) ;
    if ( admin == null || admin.equals( user ) ) return true ;
       throw new IOException( "You are not authorised to access configuration of site " + site ) ; 
  }

  /**
   * Does server-side validation of parameters being input to the GUI controller.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   */
  void validate( VelocityContext context, SolrParams params )
  throws IOException 
  {
    String fileName = solrConfDir + "/arch/config.txt" ;
    ConfigList rootCfg = ConfigList.newConfigList( fileName ) ;
    String msg = null ;
    if ( params.get( "temp.dir" ) != null ) msg = validateTempDir( context, params, rootCfg ) ;
    else if ( params.get( "database" ) != null ) msg = validateDatabase( context, params, rootCfg ) ;
    else if ( params.get( "mail.level" ) != null ) msg = validateMail( context, params, rootCfg ) ;
    else if ( params.get( "file.bookmarks" ) != null ) msg = validateBookmarks( context, params, rootCfg ) ;
    if ( msg != null ) { context.put( "code", "--Failed" ) ; context.put( "message", msg ) ; }
  }

  
  /**
   * Does server-side validation of working directory parameter.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param rootCfg - root configuration.
   */
  String validateTempDir( VelocityContext context, SolrParams params, ConfigList rootCfg )
  throws IOException 
  {
	String fileName = params.get( "temp.dir" ) ;
	File file = new File( fileName ) ;
	if ( ( !file.exists() && !file.mkdirs() ) || !file.isDirectory()  ) return "No temp directory" ;
	if ( !file.canWrite() ) return "No writing to temp" ;
	return null ;
  }

  
  /**
   * Does server-side validation of database related parameters.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param rootCfg - root configuration.
   */
  String validateDatabase( VelocityContext context, SolrParams params, ConfigList rootCfg )
  throws IOException 
  {
	Configuration nutchConf = NutchConfiguration.create() ;
	String solrHome = SolrResourceLoader.locateSolrHome().toString() ;
	nutchConf.set( "plugin.folders", solrHome + "/plugins" ) ;
	DBInterfaceFactory factory = null ;
	try { factory = DBInterfaceFactory.get( nutchConf ) ; }
	 catch ( Exception e ) { return "Nutch plugins configuration error" ; } 
	String database = getParam( params, rootCfg, "database" ) ;
	DBInterface intrf = null ;
	String message = null ;
	try { intrf = factory.get( database ) ; } catch( Exception e ) { message = "No plugin" ; } ;
	if ( message == null && intrf != null )
	{
      String target = getParam( params, rootCfg, "target.db" ) ;
      if ( target.contains( ":embedded" ) ) // can't connect to embedded db from a servlet
    	                                   return message ;
      try {
	        rootCfg.set( "target.db", target ) ;
	        rootCfg.set( "db.driver", getParam( params, rootCfg, "db.driver" ) ) ;
	        rootCfg.set( "url", "aa" ) ;
	        rootCfg.finalise() ;
	        IndexSiteDB db = intrf.newIndexSiteDB( rootCfg, "__", false ) ;
	        try { db.execute( "DROP TABLE IF EXISTS site___" ) ; } catch( Exception e ) {} ;
	        db.close() ;
          } catch ( Exception e ) { message = "Database test failed" ; }
	}
	return message ;
  }

  
  /**
   * Does server-side validation of email related parameters.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param rootCfg - root configuration.
   */
  String validateMail( VelocityContext context, SolrParams params, ConfigList rootCfg )
  throws IOException 
  {
	String message = null ;
    try {
	      rootCfg.set( "mail.level", getParam( params, rootCfg, "mail.level" ) ) ;
	      rootCfg.set( "mail.transport.protocol", getParam( params, rootCfg, "mail.transport.protocol" ) ) ;
	      rootCfg.set( "mail.host", getParam( params, rootCfg, "mail.host" ) ) ;
	      rootCfg.set( "mail.user", getParam( params, rootCfg, "mail.user" ) ) ;
	      rootCfg.set( "mail.password", getParam( params, rootCfg, "mail.password" ) ) ;
	      rootCfg.set( "mail.subject", getParam( params, rootCfg, "mail.subject" ) ) ;
	      rootCfg.set( "mail.recipient", getParam( params, rootCfg, "mail.recipient" ) ) ;
	      rootCfg.finalise() ;
          Indexer.sendEmail( "This is email test, please ignore.", "Arch email test", rootCfg ) ;
        } catch ( Exception e ) { message = "Mail test failed" ; }
    return message ;
	  
  }
  
  
  /**
   * Does server-side validation of bookmarks file parameter.
   * @param context - Velocity context object.
   * @param params - request SolrParams.
   * @param rootCfg - root configuration.
   */
  String validateBookmarks( VelocityContext context, SolrParams params, ConfigList rootCfg )
  throws IOException 
  {
	String fileNames = getParam( params, rootCfg, "file.bookmarks" ) ;
    String[] names = fileNames.trim().split( "\n" ) ;
    for ( String name : names )
    {
      File file = null ;
	  try { file = new File( name ) ; }
	    catch ( Exception e ) { return "No bookmarks file" ; }
	  if ( !file.exists() ) return "No bookmarks file" ;
	  if ( !file.canRead() ) return "Unreadable bookmarks file" ;
    }
	return null ;
  }
  
  
  String getParam( SolrParams params, ConfigList rootCfg, String name )
  {
    String val = params.get( name ) ;
    if ( val == null || val.length() == 0 ) val = rootCfg.get( name, "" ) ;
    return val ;
  }
  
  @Override
  public String getDescription() { return "Arch AJAX server component" ; }

}
