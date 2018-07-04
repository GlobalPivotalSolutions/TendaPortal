/*
 * A reference authentication plugin. Provided as an example for building authentication
 * modules. Can be used to perform Apache file based authentication.
 * 
 * Denies access if client's IP address matches an address from the black list.
 * 
 * If the client is not logged in (this information is stored in the session), allows
 * access if the clients's IP address matches an address in the white list. In this case,
 * preconfigured user names and/or groups are assigned to the user session and used to
 * filter queries.
 * 
 * If the client requests authentication, performs authentication and either rejects
 * access or sets authenticated user name and groups to the session object. 
 * 
 * Performs authentication of requests coming from frontends. Frontend id, password and, 
 * optionally, domain must be included in the request as ar_frontid, ar_password and
 * ar_domain. If ar_domain is present, authentication configuration parameters are taken
 * from the respective site configuration file and search is limited to that site documents.
 * Authentication configuration is cached. Object reinit cleans the caches.
 * 
 * Takes user name and groups from the session object, where they can be put by other
 * authentication modules. 
 * 
 * Can perform user authentication, if ar_action == "login" and user name and password
 * are supplied in ar_user and ar_password request parameters.
 * 
 * Constructs site, area user and groups filters based on search profile of the frontend
 * or established user name and groups and site and area names sent in ar_site and ar_area.
 * 
 * See Arch White Paper for more information about authentication. 
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.solr;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.servlet.HttpSolrCall;

import au.csiro.cass.arch.auth.SearchProfile;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.UnixCrypt;
import au.csiro.cass.arch.utils.Utils;

public class Authenticator extends SearchComponent
{
  Map<String, SearchProfile> profiles ; // cached frontend search profile objects
  Map<String, ConfigList>     configs ; // cached Arch config objects
  Pattern[]           allowedPatterns ; // compiled patterns of IP addresses that are allowed
  Pattern[]           blockedPatterns ; // compiled patterns of IP addresses that are blocked
  Set<String>            allowedUsers ; // users for allowed IP addresses
  String              allowedUsersStr ; // users for allowed IP addresses
  Set<String>           allowedGroups ; // groups for allowed IP addresses
  String             allowedGroupsStr ; // groups for allowed IP addresses
  Set<String>            allowedSites ; // groups for allowed IP addresses
  Set<String>            allowedAreas ; // groups for allowed IP addresses  
  Set<String>            defaultUsers ; // users for IP addresses that do not match white or black lists
  String              defaultUsersStr ; // users for IP addresses that do not match white or black lists
  Set<String>           defaultGroups ; // groups for IP addresses that do not match white or black lists
  String             defaultGroupsStr ; // groups for IP addresses that do not match white or black lists
  Set<String>            defaultSites ; // groups for IP addresses that do not match white or black lists
  Set<String>            defaultAreas ; // groups for IP addresses that do not match white or black lists
  boolean                    initDone ;
  NamedList                      args ;
  boolean                    security ; // true if security is ON
  
  @Override
  public void init( NamedList args )
  {
	this.args = args ;
  }
	  
  public void init0( NamedList args ) // init replacement, called on first use, better for debugging
  {
	try {
          String solrHome = SolrResourceLoader.locateSolrHome().toString() ;
          String archDir = solrHome + "/conf/arch" ;
          ConfigList rootCfg = ConfigList.newConfigList( archDir + "/config.txt" ) ;
          security = rootCfg.get( "access.control.enabled", false ) ;
          configs = new HashMap<String, ConfigList>() ;
          configs.put( "", rootCfg ) ;
          profiles = new HashMap<String, SearchProfile>() ;
          allowedPatterns = getPatterns( rootCfg, "allowed.ip.addresses" ) ;
          blockedPatterns = getPatterns( rootCfg, "blocked.ip.addresses" ) ;
	      allowedUsers = getSet( rootCfg, "allowed.users" ) ;	
	      allowedUsersStr = rootCfg.get( "allowed.users", null ) ;	
	      allowedGroups = getSet( rootCfg, "allowed.groups" ) ;	
	      allowedGroupsStr = rootCfg.get( "allowed.groups", null ) ;	
	      allowedSites = getSet( rootCfg, "allowed.sites" ) ;	
	      allowedAreas = getSet( rootCfg, "allowed.areas" ) ;
	      defaultUsers = getSet( rootCfg, "default.users" ) ;	
	      defaultUsersStr = rootCfg.get( "default.users", null ) ;	
	      defaultGroupsStr = rootCfg.get( "default.groups", null ) ;	
	      defaultSites = getSet( rootCfg, "default.sites" ) ;	
	      defaultAreas = getSet( rootCfg, "default.areas" ) ;
	    } catch( Exception e )
	    {
	      throw new RuntimeException( e ) ;
	    }
  }
  
    
  /**
   * Get IP address patterns and precompile them.
   * @param param - configuration parameter name
   * @return and array of compiled IP address patterns.
   */
  Pattern[] getPatterns( ConfigList cfg, String param )
  {
	String lines[] = cfg.getAll( param ) ;
	if ( lines == null ) return null ;
	StringBuffer buf = new StringBuffer() ;
	for ( String line : lines )
	{ if ( buf.length() > 0 ) buf.append( ' ' ) ; buf.append( line ) ; }
	String str = buf.toString() ;
	String[] expressions = str.trim().split( "[ \t\n\r]" ) ;
	if ( expressions == null || expressions.length == 0 ) return null ;
	int count = 0, j = 0 ;
	for ( int i = 0 ; i < expressions.length ; i++ ) 
	   if ( expressions[ i ] != null && expressions[ i ].length() > 0 ) count++ ;
	Pattern[] patterns = new Pattern[ count ] ;
	for ( int i = 0 ; i < expressions.length ; i++ )
	{
	  String ex = expressions[ i ] ;
	  if ( ex == null || ex.length() == 0 ) continue ;
	  patterns[ j ] = Pattern.compile( ex ) ; j++ ;
	}
	return patterns ;  
  }
 
  
  /**
   * Get parameter value and split it to a set.
   * @param cfg - configuration object
   * @param param - configuration parameter name
   * @return a set of strings.
   */
  Set<String> getSet( ConfigList cfg, String param )
  {
	Set<String> set = null ;
    String line = cfg.get( param, null ) ;
    if ( line != null && !line.equalsIgnoreCase( "all" ) ) set = Utils.splitToSet( line ) ;
    return set ;
  }
  
  
  /**
   * Match an IP address against an array of patterns
   * @param patterns - patterns to match.
   * @param address - address to match.
   * @return true if the address matches any of the patterns.
   */
  boolean match( Pattern[] patterns, String address )
  {
	if ( patterns == null || patterns.length == 0 ) return false ;
	for ( int i = 0 ; i < patterns.length ; i++ )
	{
      if ( patterns[ i ] == null ) continue ;
      Matcher matcher = patterns[ i ].matcher( address ) ;
      if ( matcher.matches() ) return true ;
	}
	return false ;
  }

  
  @Override
  public void prepare( ResponseBuilder rb ) throws IOException
  {
	if ( !initDone )
	{ init0( args ) ; initDone = true ; args = null ; }
	SolrQueryRequest req = rb.req;
    if ( security ) req.getContext().put( "security", "on" ) ; else return ;
    HttpSolrCall call = req.getHttpSolrCall() ;
    HttpServletRequest request = call != null ? call.getReq() : null ;

    if ( request == null ) return ; // Wrong interface, can do nothing
    // Check if the remote IP address is an exception
	String ip1 = request.getHeader( "X-Forwarded-For" ) ;
    String ip2 = request.getRemoteAddr() ;
    if ( ip1 == null ) ip1 = ip2 ;
	
	String loggedIn = (String)request.getSession().getAttribute( "ar_loggedin" ) ;
	String action = (String)request.getParameter( "ar_action" ) ;

	String frontid = (String)request.getParameter( "ar_frontid" ) ;
    if ( frontid != null ) // this is a request from a front end 
       { authenticateFrontEnd( req, request ) ;  return ; } 
	
    if ( !security ) return ;
    if ( match( blockedPatterns, ip1 ) ) // the address is on the black list
        throw new IOException( "Access denied to " + ip1 ) ;
    if ( match( blockedPatterns, ip2 ) ) // the address is on the black list
        throw new IOException( "Access denied to " + ip2 ) ;

    if ( ( loggedIn == null || !loggedIn.equals( "Y" ) ) &&
	     ( action == null || ( !action.equals( "login" ) && !action.equals( "logout" ) ) ) ) 
	 {
      // Sort out site overriden facets if any
      if ( match( allowedPatterns, ip1 ) && match( allowedPatterns, ip2 ) ) // the address is on the white list
       {
    	 String filter = Utils.makeFilter( request, allowedSites, allowedAreas, allowedUsers, allowedGroups ) ;
         req.getContext().put( "ar_filter", filter ) ;
         req.getContext().put( "ar_user", allowedUsersStr ) ;
  	     req.getContext().put( "ar_groups", allowedGroupsStr ) ;
  	     return ;
       } else // the address does not match neither back nor white lists
       {
      	 String filter = Utils.makeFilter( request, defaultSites, defaultAreas, defaultUsers, defaultGroups ) ;
         req.getContext().put( "ar_filter", filter ) ;
         req.getContext().put( "ar_user", defaultUsersStr ) ;
  	     req.getContext().put( "ar_groups", defaultGroupsStr ) ; 
  	     return ;
       }
	 }
    // else have to authenticate
    authenticateUser( req, request ) ;
  }
  

  /**
   * Authenticate a front-end and form a filter query filtering the result by users,
   * groups, sites and areas.
   * 
   * @param req - SolrQueryRequest object.
   * @param request - HttpServletRequest object.
   * @throws IOException
   */ 
  protected void authenticateFrontEnd( SolrQueryRequest req, HttpServletRequest request )
  throws IOException
  { 
    String id = Utils.notEmpty( (String)request.getParameter( "ar_frontid" ) ) ; 
    String password = Utils.notEmpty( (String)request.getParameter( "ar_password" ) );
    String domain = Utils.notEmpty( (String)request.getParameter( "ar_domain" ) ) ;
    SearchProfile profile = getSearchProfile( req, request, id, password ) ;
    if ( profile == null && security )  // authentication failed
      throw new IOException( "No search profile found for domain=" + 
    		                 domain + ", domain=" + domain + ", password=" + password ) ;
    String sites = Utils.notEmpty( (String)request.getParameter( "ar_site" ) ) ;
    String areas = Utils.notEmpty( (String)request.getParameter( "ar_area" ) ) ;
    String users = Utils.notEmpty( (String)request.getParameter( "ar_user" ) ) ;
    String groups = Utils.notEmpty( (String)request.getParameter( "ar_groups" ) ) ;
    StringBuilder b = new StringBuilder() ;
    if ( domain != null ) sites = domain ; // limit search to this site only
    String s = profile.getSites( sites ) ;
    if ( s != null && s.length() == 0 )
      {  String m = "Access denied: can't search sites " + sites ; 
         if ( domain != null ) m += " of domain " + domain ;
    	 throw new IOException( m ) ;
      }
    
    String a = profile.getAreas( areas ) ;
    if ( a != null && a.length() == 0 )
    	 throw new IOException( "Access denied: can't search areas " + areas ) ;
      
    String u = profile.getUsers( users ) ;
    String g = profile.getGroups( groups ) ;
    String fq = Utils.makeFilter( request, profile.getSiteS(), profile.getAreaS(),
    		                               profile.getUserS(), profile.getGroupS() ) ;
    // the result will always contain at least "ar_user:guest OR ar_groups:public" 
    req.getContext().put( "ar_filter", fq ) ;
    req.getContext().put( "ar_user", u ) ;
    req.getContext().put( "ar_groups", g ) ;

  }
  
    
  /**
   * Authenticate a user or process a login request. If success, set user name and groups
   * to request and session objects. If failure, throw exception. Prepare a query filter
   * based on user name and groups. 
   * 
   * @param req - Solr request.
   * @param request - a HttpServletRequest object.
   * @throws IOException
   */ 
  protected void authenticateUser( SolrQueryRequest req, HttpServletRequest request )
  throws IOException
  {
   String action = (String)request.getParameter( "ar_action" ) ;
   HttpSession session = request.getSession() ;
   String user = "" ;
   String groups = "" ;
   String areas ;
   String sites ;
   String loggedIn = "N" ;
   if ( action != null && action.equals( "login" ) )
      {
	    ConfigList cfg = getArchCfg( req, request ) ;
        String passFile = cfg.get( "auth.passwords.file", "" ) ;
        String groupsFile = cfg.get( "auth.groups.file", "" ) ;
        if ( passFile.length() == 0 || groupsFile.length() == 0 )
 	        throw new IOException( "Passwords file or groups file is not configured." ) ;
        user = (String)request.getParameter( "ar_fname" ) ;
        String password = (String)request.getParameter( "ar_fpass" ) ;
        String cryptedPassword = getPassword( passFile, user ) ;
        if ( cryptedPassword == null || !UnixCrypt.matches( cryptedPassword, password ) )
        	throw new IOException( "Authentication failed for user " + user ) ; // unsuccessful
        session.setAttribute( "ar_user", user ) ;
        groups = getGroups( groupsFile, user ) ;
        if ( groups == null || groups.length() == 0 ) groups = "public" ;
        session.setAttribute( "ar_groups", groups ) ;
        session.setAttribute( "ar_loggedin", "Y" ) ;
        loggedIn = "Y" ;
      } else if ( action != null && action.equals( "logout" ) )
      {
        session.setAttribute( "ar_user", "guest" ) ;
        session.setAttribute( "ar_groups", "public" ) ;
        session.setAttribute( "ar_loggedin", "N" ) ;
        user = "guest" ;
        groups = "public" ;
      } else
      {
        user = (String)session.getAttribute( "ar_user" ) ;
        groups = (String)session.getAttribute( "ar_groups" ) ;
        loggedIn = (String)session.getAttribute( "ar_loggedin" ) ;
        if ( user == null ) user = "guest" ;
        if ( groups == null ) groups = "public" ;
        if ( loggedIn == null ) loggedIn = "N" ;
      }
   sites = Utils.notEmpty( (String)request.getParameter( "ar_site" ) ) ;
   areas = Utils.notEmpty((String)request.getParameter( "ar_area" ) ) ;
   String fq = Utils.makeFilter( sites, areas, user, groups ) ;
   req.getContext().put( "ar_filter", fq ) ;
   req.getContext().put( "ar_user", user ) ;
   req.getContext().put( "ar_groups", groups ) ;
   req.getContext().put( "ar_loggedin", loggedIn ) ;
  }
  
  
  /**
   * Read a front-end search profile from Arch configuration file.
   * @param req - Solr query request.
   * @param request - HTTP servlet request.
   * @param id - front-end id.
   * @param password - front-end password.
   * @return front-end SearchProfile object.
   * @throws IOException
   */
  protected synchronized SearchProfile getSearchProfile(  SolrQueryRequest req, 
		                           HttpServletRequest request, String id, String password )
  throws IOException
  {
	String domain = Utils.notEmpty( (String)request.getParameter( "ar_domain" ) ) ;
	String key = id + "/" + password ;
	if ( domain != null ) key = domain + "/" + key ;
	if ( profiles == null ) profiles = new HashMap() ;
	SearchProfile profile = (SearchProfile)profiles.get( key ) ;
	if ( profile == null )
	{
	   ConfigList cfg = getArchCfg( req, request ) ;  
	   profile = cfg.getSearchProfile( id, password ) ;
       if ( profile != null ) profiles.put( key, profile ) ;
	}
    return profile ;
  }
  
  /**
   * Get Arch configuration object.
   * @param req - Solr query request.
   * @param request - HTTP servlet request.
   * @return front-end ConfigList object.
   * @throws IOException
   */  
  protected ConfigList getArchCfg( SolrQueryRequest req, HttpServletRequest request )
  throws IOException
  {
	    ConfigList cfg ;
	    String domain = Utils.notEmpty( (String)request.getParameter( "ar_frealm" ) ) ;
	    if ( domain == null ) domain = "" ;
        cfg = configs.get( domain ) ;
        if ( cfg != null ) return cfg ;
	    SolrParams params = req.getParams() ;
	    String solrHome = SolrResourceLoader.locateSolrHome().toString() ;
	    String archDir = solrHome + "/conf/arch" ;
	    String archCfgFile = archDir + "/config.txt" ;
	    if ( domain.length() > 0 ) archCfgFile = archDir + "/sites/" + domain + "/config.txt" ;
        // This implements simple Apache file based authentication
	    cfg = ConfigList.newConfigList( archCfgFile ) ;
	    configs.put( domain, cfg ) ;
	    return cfg ;
  }
  
  

    
  /**
   * Get encrypted password for this user.
   * 
   * @param file String name of file containing groups lists in Apache format
   * @param user user name
   * 
   * @return String - a string of user groups separated by spaces
   * @throws IOException
   */
  protected String getPassword( String file, String user ) throws IOException
  {
   try
   {
     FileInputStream fstream = new FileInputStream( file ) ;
     DataInputStream in = new DataInputStream( fstream ) ;
 	BufferedReader br = new BufferedReader( new InputStreamReader( in ) ) ;
     String line, password = null, start = user + ":" ;
 	while ( (line = br.readLine()) != null )
 	{
      if ( line.startsWith( start ) ) break ; 
 	}
 	in.close();
 	if ( line != null )
 		return line.substring( line.indexOf( ':' ) + 1 ) ;
   } catch ( IOException e ) 
   {
 	throw new IOException( "Can't read passwords file " + file ) ;
   }
   return null ;
  }
  
  /**
   * Get a string of user groups separated by spaces.
   * 
   * @param file String name of file containing groups lists in Apache format
   * @param user user name
   * 
   * @return String - a string of user groups separated by spaces
   * @throws IOException
   */
  protected String getGroups( String file, String user ) throws IOException
  {
   try
   {
     FileInputStream fstream = new FileInputStream( file ) ;
     DataInputStream in = new DataInputStream( fstream ) ;
 	BufferedReader br = new BufferedReader( new InputStreamReader( in ) ) ;
     String line, groups = "" ;
 	while ( (line = br.readLine()) != null )
 	{
 	  line = line.trim() ;
 	  int pos = 0, len = user.length(), linlen = line.length() ;
 	  do {
 		   pos = line.indexOf( user, pos + 1 ) ;
 		   if ( pos > 1 && (
 				( line.charAt( pos - 1 ) == ':' || line.charAt( pos - 1 ) == ' ' ) &&
 				( pos + len == linlen || line.charAt( pos + len ) == ' ' ) )
 			  )
 		   { 
 			 if ( groups.length() > 0 ) groups += " " ;
 		     groups += line.substring( 0, line.indexOf( ':' ) ) ;
 		     break ;
 		   }
 	     } while( pos != -1 ) ;
 	}
 	in.close();
 	if ( groups.length() > 0 ) groups += " " ; groups += "public" ;
 	return groups ;
   } catch ( IOException e ) 
   {
 	throw new IOException( "Can't read user groups file " + file ) ;
   }
  }

  
  

	@Override
  public void process( ResponseBuilder rb ) throws IOException
  {
	// do nothing
  }

  @Override
  public String getDescription() { return "Arch reference authenticator" ; }

}
