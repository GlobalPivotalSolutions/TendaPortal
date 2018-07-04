/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.servlet.HttpSolrCall;

import au.csiro.cass.arch.auth.SearchProfile;
import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

public class AdminAuthenticator extends Authenticator
{

  Pattern[]           adminPatterns ; // compiled patterns of IP addresses that are allowed
  boolean                  security ; // true if security is enabled
	
  @Override
  public void init0( NamedList args ) // init replacement, called on first use, better for debugging
  {
	try {
	      String solrHome = SolrResourceLoader.locateSolrHome().toString();
	      String archDir = solrHome + "/conf/arch" ;
	      ConfigList rootCfg = ConfigList.newConfigList( archDir + "/config.txt" ) ;
          security = rootCfg.get( "access.control.enabled", false ) ;
	      configs = new HashMap<String, ConfigList>() ;
	      configs.put( "", rootCfg ) ;
	      profiles = new HashMap<String, SearchProfile>() ;
	      adminPatterns = getPatterns( rootCfg, "admin.ip.addresses" ) ;
		} catch( Exception e )
		{
		  throw new RuntimeException( e ) ;
		}
  }

  
  @Override
  public void prepare( ResponseBuilder rb ) throws IOException
  {
	if ( !initDone )
	   { init0( args ) ; initDone = true ; args = null ; }
	SolrQueryRequest req = rb.req;
	HttpSolrCall call = req.getHttpSolrCall() ;
    HttpServletRequest request = call != null ? call.getReq() : null ;
    if ( request == null ) return ; // Wrong interface, can do nothing
    if ( !security ) return ;
    // Check if the remote IP address is an exception
	String ip1 = request.getHeader( "X-Forwarded-For" ) ;
	String ip2 = ip1 == null ? request.getRemoteAddr() : null ;
	
	if ( adminPatterns != null ) // admin protection is set
	{
	   if ( ip1 != null && !match( adminPatterns, ip1 ) ) // the address is not on the list
	          throw new IOException( "Access denied to " + ip1 ) ;
	   if ( ip2 != null && !match( adminPatterns, ip2 ) ) // the address is not on the list
	          throw new IOException( "Access denied to " + ip2 ) ;
	}
  }
  
}
