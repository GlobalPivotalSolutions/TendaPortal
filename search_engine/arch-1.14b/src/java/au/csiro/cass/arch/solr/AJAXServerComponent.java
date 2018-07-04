/*
 * Handles AJAX requests coming from clients and/or relayed by frontends.
 * Has very little to do with Solr. Could be designed to be deployed separately,
 * but is merged with Solr for the sake of compactness.
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.solr;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.velocity.VelocityContext;

import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.IndexNode;
import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.ServletDB;
import au.csiro.cass.arch.sql.SolrConnectionCache;
import au.csiro.cass.arch.utils.Utils;


public class AJAXServerComponent extends SearchComponent
{
  public static Log LOG = LogFactory.getLog(AJAXServerComponent.class) ; 
	
  @Override
  public void prepare( ResponseBuilder rb ) throws IOException
  {}

  /**
   * Serves AJAX requests and prepares responses for response writer.
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
	  ConnectionCache connections = SolrConnectionCache.newSolrConnectionCache(core, params, true ) ;
	  Utils.dumpRequest( LOG, req ) ;

	  ServletDB db = null ;
	  String site = params.get( "ar_site" ) ;
	  if ( site != null && site.equals( "_root_" ) ) site = null ;
	  boolean isRootLevel = params.get( "ar_isRoot" ) != null ;
	  if ( isRootLevel ) db = connections.getRootConnection() ;
	     else if ( site != null ) db = connections.getSiteConnection( site ) ;
	   
	  // Requests may come from clients directly or via PHP front-ends. If request comes from a client,
	  // the client is considered to be a 'guest' and member of 'public' group until logged in,
	  // but this depends in the logic implemented in the authentication plugin.
	   
	  // In the provided Authenticator plugin, login procedure establishes real user name and groups
	  // and sets them to the context object. In case if request comes via a front-end, the front-end's
	  // SearchProfile object is used as a filter to filter user names and groups that this front-end
	  // is authorised to  present. This filter may also limit access via this front-end to a given site only.
	  
	  // Authentication is expected to be done before 
      String user = (String)req.getContext().get( "ar_user" ) ;
      if ( user == null ) user = "guest" ; if ( user.indexOf( "guest" ) < 0 ) user += " guest" ;
      String groups = (String)req.getContext().get( "ar_groups" ) ;
      if ( groups == null ) groups = "public" ; if ( groups.indexOf( "public" ) < 0 ) groups += " public" ;
      
      //==============================================================================================
      // Performing requested action
      
      String action = params.get( "ar_action" ) ;
      if ( action == null ) throw new Exception( "Action is a required parameter." ) ;
      String nodeIdStr = params.get( "ar_node" ) ;
      int nodeId = -1 ; 
      if ( nodeIdStr != null ) nodeId = Integer.parseInt( nodeIdStr ) ;
      String encoding = params.get( "ar_enc" ) ;
      if ( encoding == null ) encoding = "html" ;
      String msg = "" ;
      String code = "++OK" ;
      
      if ( action.equals( "sendQueryForm" ) )
         {
 	       sendQueryForm( context, params, connections ) ;
         }
         else if ( action.equals( "sendDir" ) )
         {
 	       context.put( "template", "dir" ) ;
 	       String theme = params.get( "theme" ) ; if ( theme == null ) theme = "blue" ;
 	       context.put( "theme", theme ) ;
         }
         else if ( action.equals( "readSelects" ) )
         { 
           // context.put( "message", ((IndexRootDB)db).getSiteSelect( null ) + "<>" + 
           //             ((IndexRootDB)db).getAreaSelect( null ) ) ;
           // Read them from configuration directory instead
           context.put( "message", getSelect( (IndexRootDB)db, "site" ) + "<>" + 
        	                       getSelect( (IndexRootDB)db, "area" ) ) ;	 
         }
         else if ( action.equals( "readNode" ) )
         { 
           IndexNode node = db.readNode( nodeId, user, groups ) ;
           sendNode( context, encoding, node, isRootLevel, user, groups ) ;
         }
         else if ( action.equals( "getProperties" ) ) // html code for properties dialog
         {
           IndexNode node = db.readNode( nodeId, user, groups ) ;
                // you can only see node's properties if you have writing permissions
           if ( node == null || !node.canWrite( user, groups ) )
           	                    throw new Exception( "Access denied" ) ;  
           if ( encoding.equals( "html" ) ) sendPropertiesForm( context, node, isRootLevel ) ;
                         else sendNode( context, encoding, node, isRootLevel, user, groups ) ; 
         }
        else if ( action.equals( "getLogin" ) ) // html code for login dialog
         {
           context.put( "template", "login" ) ;
         }
        else if ( action.equals( "login" ) ) // process login request
         {
           String loggedIn = (String)req.getContext().get( "ar_loggedin" ) ;
           if ( loggedIn == null || !loggedIn.equals( "Y" ) ) context.put( "code", "--Failed" ) ;
         }   
        else if ( action.equals( "logout" ) )
         {
            // do nothing, must have been done above by the Authenticator
         }   
        else if ( action.equals( "moveNode" ) )
         {
           throw new Exception( "Moving nodes is not implemented." ) ;
         /*
           String nodeBeforeStr = (String)request.getParameter( "before" ) ;
           int beforeId = Integer.parseInt( nodeBeforeStr ) ;
           if ( !db.moveNode( nodeId, beforeId, user, groups ) )
            throw new Exception( bundle.getString( "error003" ) ) ;
         */
         }
        else if ( action.equals( "deleteNode" ) )
         {
           if ( !db.deleteNode( nodeId, user, groups ) ) 
              throw new Exception( "Could not delete node." ) ;
         }
        else if ( action.equals( "insertNode" ) )
         {
            throw new Exception( "Inserting a new node is not implemented." ) ;
         }
        else if ( action.equals( "updateNode" ) )
         {
       	   IndexNode node = IndexNode.newRootIndexNode() ;
       	   node.setAccess( params.get( "ar_access" ) != null ? "i" : "s" ) ;
       	   // node.setName( (String)request.getParameter( "fname" ) ) ; ignore, names don't change
       	   node.setLabel( params.get( "ar_flabel" ) ) ;
       	   node.setTitle( params.get( "ar_ftitle" ) ) ;
       	   node.setGroupr( params.get( "ar_groupr" ) ) ;
       	   node.setGroupw( params.get( "ar_groupw" ) ) ;
       	   node.setUserr( params.get( "ar_userr" ) ) ;
       	   node.setUserw( params.get( "ar_userw" ) ) ;
       	   node.setOwners( params.get( "ar_owners" ) ) ;
       	   node.setId( nodeId ) ; 
           if ( !db.updateNode( node, user, groups ) )
                        throw new Exception( "Ipdate node failed." ) ;
         }
        else if ( action.equals( "readLevel" ) )
         {
       	   IndexNode[] nodes = null ;
           if ( isRootLevel && nodeId != 0 ) // must get real site name first
           { 
             IndexNode node = db.readNode( nodeId, user, groups ) ;
             if ( node == null ) throw new Exception( "Could not read node" ) ;
   		     ServletDB sitedb = connections.getSiteConnection( site  ) ;
             // now read children of that site's root node
             nodes = sitedb.readLevel( -1, 1, user, groups ) ;
           } else // this is either level 0 or below 1
           {
             nodes = db.readLevel( -1, nodeId, user, groups ) ;
             if ( isRootLevel && site != null ) 
           	 for ( int t = 0 ; t < nodes.length ; t++ )
           		if ( nodes[ t ] != null && !nodes[ t ].getSite().equals( site ) )
           			 nodes[ t ] = null ;
           }
           if ( nodes == null ) throw new Exception(  "Could not read level." ) ;  
           sendNodes( context, encoding, nodes, nodeId == 0, user, groups ) ;
         }
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
  
  
  
  boolean notNull( ConnectionCache connections, Object db ) throws Exception
  {
    if ( db != null ) return true ;
	if ( connections.msg != null ) throw new Exception( connections.msg ) ;
	else throw new Exception( "Can't connect to the database" ) ;
  }

  
  /**
   * Sets up Velocity context information for node properties form.
   * @param context - Velocity context object.
   * @param node - index/site map node.
   * @param isRoot - true if this is the root node.
   * 
   */
  void sendPropertiesForm( VelocityContext context, IndexNode node, boolean isRoot )
  throws Exception // Node properties HTML
  {
    if ( node == null ) node = new IndexNode() ;
    String inherited = node.getAccess().charAt(0) == 'i' ? "CHECKED" : "" ;
    String name   = node.getName() ;   if ( name == null )   name = "" ;
    String label  = node.getLabel() ;  if ( label == null )  label = "" ;
    String title  = node.getTitle() ;  if ( title == null )  title = "" ;
    String groupr = node.getGroupr() ; if ( groupr == null ) groupr = "" ;
    String groupw = node.getGroupw() ; if ( groupw == null ) groupw = "" ;
    String userr  = node.getUserr() ;  if ( userr == null )  userr = "" ;
    String userw  = node.getUserw() ;  if ( userw == null )  userw = "" ;
    String owners = node.getOwners() ; if ( owners == null ) owners = "" ;
    String site = isRoot ? "_root_" : node.site ;
    String titleDisabled = isRoot ? "disabled='disabled'" : "" ;
    
    context.put( "name", name ) ;
    context.put( "nodeId", node.id + "" ) ;
    context.put( "inherited", inherited ) ;
    context.put( "label", label ) ;
    context.put( "title", title ) ;
    context.put( "groupr", groupr ) ;
    context.put( "groupw", groupw ) ;
    context.put( "userr", userr ) ;
    context.put( "userw", userw ) ;
    context.put( "owners", owners ) ;
    context.put( "site", site ) ;
    context.put( "titleDisabled", titleDisabled ) ;
    context.put( "template", "properties" ) ;
  }

  
  /**
   * Sets up Velocity context information for the query form.
   * @param context - Velocity context object.
   * @param params - Solr parameters object.
   * @param connections - DB connections cache to use to read information from the DB.
   * 
   */
  static void sendQueryForm( VelocityContext context, SolrParams params, ConnectionCache connections )
  throws Exception
  {
    IndexRootDB db = connections.getRootConnection() ;
	context.put( "template", "query" ) ;
    String siteSelect = getSelect( db, "site" ) ;
    String areaSelect = getSelect( db, "area" ) ;
    String rows = params.get( "rows" ) ; if ( rows == null ) rows = "10" ;
    String lang = params.get( "lang" ) ; if ( lang == null ) lang = "en" ;
    context.put( "selectSite", siteSelect ) ;
    context.put( "selectArea", areaSelect ) ;
    context.put( "hitsPerPage", rows ) ;
    context.put( "language", lang ) ;
    String loggedIn = params.get( "rows" ) ; if ( loggedIn == null ) loggedIn = "N" ;
    if ( loggedIn.equals( "Y" ) )
          { context.put( "styleLogin", "none" ) ; context.put( "styleLogout", "block" ) ; }
    else  { context.put( "styleLogin", "block" ) ; context.put( "styleLogout", "none" ) ; }
  }  

  static String getSelect( IndexRootDB db, String select ) throws Exception
  {
    String solrHome = SolrResourceLoader.locateSolrHome().toString() ;
	if ( db == null )  
		return Utils.readFileAsString( solrHome + "/conf/arch/" + select + "Select.txt" ) ;
    try
    {
      if ( select.equals( "site" ) ) return db.getSiteSelect( null ) ;
                         else return db.getAreaSelect( null ) ;
    } catch ( Exception e )
    {
	  return Utils.readFileAsString( solrHome + "/conf/arch/" + select + "Select.txt" ) ;
    }
  }

  /**
   * Send serialised node (for PHP processing).
   * @param context - Velocity context object.
   * @param node - index/site map node.
   * @param user - user name of the user seeking access.
   * @param groups - group names of the user seeking access.
   * 
   */
  void sendNodeText( VelocityContext context, IndexNode node, String user, String groups )
  throws Exception
  {
	String message = "" ;
    if ( node != null && node.canRead( user, groups ) ) message = node.toString() ;
    context.put( "message", message ) ;
  }  

  
  /**
   * Send index/site map node either in text or in HTML
   * @param context - Velocity context object.
   * @param encoding - "html" if send in HTML, else send serialised node in text.
   * @param node - index/site map node.
   * @param isRoot - true if this is the root node.
   * @param user - user name of the user seeking access.
   * @param groups - group names of the user seeking access.
   * 
   */  
  void sendNode( VelocityContext context, String encoding, IndexNode node, boolean isRoot,
                                             String user, String groups ) throws Exception
  {
    boolean html = encoding.equalsIgnoreCase( "html" ) ;
    if ( html ) sendNodeHTML( context, node, isRoot, user, groups ) ;
 	  else sendNodeText( context, node, user, groups ) ;
  }

  
  /**
   * Send index/site map node either in text or in HTML
   * @param context - Velocity context object.
   * @param encoding - "html" if send in HTML, else send serialised node in text.
   * @param node - index/site map node.
   * @param isRoot - true if this is the root node.
   * @param user - user name of the user seeking access.
   * @param groups - group names of the user seeking access.
   * 
   */  
  void sendNodeHTML( VelocityContext context, IndexNode node, boolean isRoot, String user,
		                                                  String groups ) throws Exception
  { 
    if ( node == null ) { context.put( "message", "" ) ; return ; }
    context.put( "node", node ) ;
    context.put( "isRoot", isRoot ) ;
    context.put( "user", user ) ;
    context.put( "groups", groups ) ;
    context.put( "template", "node" ) ;    
  }  

  
  /**
   * Send a set of index/site map nodes either in text or in HTML
   * @param context - Velocity context object.
   * @param encoding - "html" if send in HTML, else send serialised nodes in text.
   * @param nodes - an array of index/site map nodes.
   * @param isRoot - true if this is the root node.
   * @param user - user name of the user seeking access.
   * @param groups - group names of the user seeking access.
   * 
   */    
  void sendNodes( VelocityContext context, String encoding, IndexNode[] nodes, boolean isRoot,
                                                 String user, String groups ) throws Exception
  {
    if ( !encoding.equalsIgnoreCase( "html" ) )
     {
       StringBuffer buf = new StringBuffer() ;   
       for ( int i = 0 ; i < nodes.length ; i++ )
          if ( nodes[ i ] != null && nodes[ i ].canRead( user, groups ) )
           {
             if ( buf.length() > 0 ) buf.append( "<" ) ;
        	 buf.append( nodes[ i ].toString() ) ;
           }
       context.put( "message", buf.toString() ) ;
     } else // output HTML code for the nodes
     {
       int count = 0 ;
       for ( int i = 0 ; i < nodes.length ; i++ )
         if ( nodes[ i ] != null && nodes[ i ].canRead( user, groups ) ) count++ ;
         else nodes[ i ] = null ;
       if ( count > 0 )
       {
         context.put( "nodes", nodes ) ;
         context.put( "isRoot", isRoot ) ;
         context.put( "user", user ) ;
         context.put( "groups", groups ) ;
         context.put( "template", "nodes" ) ;
       } else context.put( "message", "" ) ;
     }
  }

  
  @Override
  public String getDescription() { return "Arch AJAX server component" ; }

  public String getSourceId() { return null ; }
 
  public String getSource() { return null ; }

  public String getVersion() { return null ; }

}
