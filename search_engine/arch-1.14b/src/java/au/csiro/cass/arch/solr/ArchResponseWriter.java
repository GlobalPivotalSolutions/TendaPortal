/*
 * Based on code of org.apache.solr.response.VelocityResponceWriter.
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.solr ;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrResponseBase;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.BinaryResponseWriter;
import org.apache.solr.response.PageTool;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrParamResourceLoader;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.SolrVelocityResourceLoader;
import org.apache.solr.response.XMLWriter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.ComparisonDateTool;
import org.apache.velocity.tools.generic.EscapeTool;
import org.apache.velocity.tools.generic.ListTool;
import org.apache.velocity.tools.generic.MathTool;
import org.apache.velocity.tools.generic.NumberTool;
import org.apache.velocity.tools.generic.SortTool;

import au.csiro.cass.arch.sql.ConnectionCache;
import au.csiro.cass.arch.sql.SolrConnectionCache;
import au.csiro.cass.arch.utils.Utils;


public class ArchResponseWriter implements QueryResponseWriter
{

 /*
  * (non-Javadoc)
  * @see org.apache.solr.response.QueryResponseWriter#write(java.io.Writer, org.apache.solr.request.SolrQueryRequest, org.apache.solr.response.SolrQueryResponse)
  */
  public void write( Writer writer, SolrQueryRequest request, SolrQueryResponse response )
  throws IOException
  {
	String outputFormat = request.getParams().get( "ar_format" ) ;
	if ( outputFormat != null && outputFormat.equals( "xml" ) )
	                           { XMLWriter.writeResponse( writer, request, response ) ; return ; }
	  
	VelocityEngine engine = getEngine( request ) ;
    VelocityContext context = new VelocityContext();
    context.put( "request", request ) ;
    HttpServletRequest htr = (HttpServletRequest)request.getContext().get( "HttpServletRequest" ) ;
    String theme = null ;
    if ( htr != null ) theme = Utils.getTheme( htr ) ;
    if ( theme == null ) theme = request.getParams().get( "ar_theme", "blue" ) ;
    context.put( "theme", theme ) ;
    String rows = request.getParams().get( "rows", "10" ) ;
    context.put( "rows", rows ) ;
    
    // Turn the SolrQueryResponse into a SolrResponse.
    SolrResponse rsp = new QueryResponse();
    NamedList<Object> parsedResponse = BinaryResponseWriter.getParsedResponse( request, response ) ;
    PageTool page = new PageTool( request, response ) ;
    try
    {
      rsp.setResponse( parsedResponse ) ; // page only injected if QueryResponse works
      context.put( "page", page ) ;
    } catch ( ClassCastException e )
    {
      e.printStackTrace() ;
      rsp = new SolrResponseBase() ;
      rsp.setResponse( parsedResponse ) ;
    }
    
    // Is this a request for query form?
    String aa = (String)request.getContext().get( "ar_action" ) ;
    if ( aa != null && aa.equals( "sendQueryForm" ) )
    {
      try {
  	        SolrCore core = request.getCore() ;
	        SolrParams params = request.getParams() ;
	        ConnectionCache connections = SolrConnectionCache.newSolrConnectionCache(core, params, true ) ;
	        AJAXServerComponent.sendQueryForm( context, params, connections ) ;
	        Template template = getTemplate( engine, request, "query" ) ;
	        template.merge( context, writer ) ;
	        return ;
          } catch( Exception e )
          { IOException ie = new IOException( e.getMessage() ) ;
            ie.setStackTrace( e.getStackTrace() ) ;
            throw ie ;
          }
    }

    EscapeTool esc = new EscapeTool() ;
    ArrayList<ResultsItem> items = new ArrayList<ResultsItem>() ;
    SolrDocumentList docs = (SolrDocumentList)parsedResponse.get( "response") ;
    SimpleOrderedMap highlighting = (SimpleOrderedMap)parsedResponse.get( "highlighting" ) ;
    if ( docs != null && docs.size() > 0 )
       for ( SolrDocument doc : docs )
         {
    	   String docId = (String)doc.getFieldValue( "id" ) ;
    	   String link = (String)doc.getFieldValue( "url" ) ;
    	   String title = (String)doc.getFieldValue( "title" ) ;
    	   if ( title != null && title.length() > 0 ) title = esc.html( title ) ; else title = link ;
    	   String description = "" ;
    	   if ( highlighting != null )
    	   {
    		 SimpleOrderedMap h = (SimpleOrderedMap)highlighting.get( docId ) ;
    		 if ( h != null )
    		 {
    		   ArrayList<String> fragments = (ArrayList<String>)h.get( "content" ) ;
    		   if ( fragments != null )
    			  for ( String str : fragments )
    			  {
    				str = esc.html( str ) ;
      				str = str.replaceAll( "&lt;em&gt;", "<span class=\"highlight\">" ) ;  
    				str = str.replaceAll( "&lt;/em&gt;", "</span>" ) ;
    				if ( description.length() > 0 ) description += "<span class='ellipsis'> ... </span>" ;
    				description += str ;
    			  }
    		   if ( description.length() != 0 ) description += "<span class='ellipsis'> ... </span>" ;
    		 }
    	   }
    	   ResultsItem item = new ResultsItem( title, description, link ) ;
    	   items.add( item ) ;
         }
    // Deal with facets
    // get a list of facet fields
    NamedList<Object> fcs= (NamedList<Object>)parsedResponse.get( "facet_counts" ) ;
	HashMap<String, Facet> facets = new HashMap<String, Facet>() ;
	HashMap<String, FacetConstraint> global = new HashMap<String, FacetConstraint>() ;
    String baseURL = "?" + request.getParamString() ;
    
    if ( fcs != null ) formFacets( fcs, facets, global ) ;
    if ( facets.size() == 0 ) facets = null ;
    if ( global.size() == 0 ) global = null ;
    
    context.put( "response", rsp ) ;
    context.put( "facets", facets ) ;
    context.put( "allfacets", global ) ;
    context.put( "items", items ) ;
    context.put( "esc", new EscapeTool() ) ;
    context.put( "date", new ComparisonDateTool() ) ;
    context.put( "list", new ListTool() ) ;
    context.put( "math", new MathTool() ) ;
    context.put( "number", new NumberTool() ) ;
    context.put( "sort", new SortTool() ) ;
    context.put( "engine", engine ) ;
    context.put( "language", getParam( request, "lang", "en" ) ) ;
    context.put( "search", getParam( request, "ar_search", "simple" ) ) ;
    long total = page.getResults_found() ;
    context.put( "total", total ) ;
    int start = ( page.getCurrent_page_number() - 1 ) * page.getResults_per_page() + 1 ;
    context.put( "start", start ) ;
    long end = page.getCurrent_page_number() * page.getResults_per_page() ;
    if ( end > page.getResults_found() ) end = page.getResults_found() ;
    context.put( "end", end ) ;
    long rowsNum = page.getResults_per_page() ;
    String pathToRoot = "." ;
    String coreName = request.getCore().getName() ;
    if ( coreName != null && coreName.length() != 0 ) pathToRoot = "../" ;
    String pages = Utils.printPages( total, start, rowsNum, baseURL, "NNNNN", false ) ;
    context.put( "pages", pages ) ;
    context.put( "pathToRoot", pathToRoot ) ;

    String templateName = "results" ;
    if ( outputFormat != null )
    	if ( outputFormat.equals( "rss" ) )
          { sendRSS( writer, request, response, rsp, engine, context ) ; return ; }
    	   else if ( outputFormat.equals( "resultsBody" ) ) templateName = "resultsBody" ;
    
    Template template = getTemplate( engine, request, templateName ) ;
    template.merge( context, writer ) ;
  }

  
  void formFacets( NamedList<Object> fcs, HashMap<String, Facet> facets, HashMap<String, FacetConstraint> global )
  {
    NamedList<NamedList<Number>> ff = (NamedList<NamedList<Number>>) fcs.get( "facet_fields" ) ;
    if ( ff != null )
        for( Map.Entry<String,NamedList<Number>> facet : ff )  // for each facet field
          {
  	        Facet f = new Facet( facet.getKey() ) ;
            for( Map.Entry<String, Number> entry : facet.getValue() )  // for each field value 
              { 
        	    long val = entry.getValue().longValue() ;
        	    if (  val > 0 )
        	    {
          	      f.add( new FacetConstraint( entry.getKey(), val ) ) ;
                  global.put( f.name + ":" + entry.getKey(), new FacetConstraint(  f.name + ":" + entry.getKey(), val ) ) ;
        	    }
              }
            Collections.sort( f.constraints ) ;
            if ( f.constraints.size() > 0 ) facets.put( f.name, f ) ;
          }
    NamedList<Number> fq = (NamedList<Number>) fcs.get( "facet_queries" ) ;
    if ( fq != null )
    {
      Set<String> fields = new HashSet<String>() ;
      for( Map.Entry<String,Number> facet : fq )  // for each facet field, takes only the first one!
        {
	      String query = facet.getKey() ;
	      String[] qq = query.split( ":" ) ;
	      if ( qq.length == 1 ) fields.add( "" ) ; else fields.add( qq[ 0 ] ) ; 
        }
      for ( String field : fields )
        {
    	  Facet f = new Facet( field ) ;
          for ( Map.Entry<String,Number> entry : fq )  // for each facet field
          {
            String query = entry.getKey() ;
            String fl = "" ;
   	        String[] qq = query.split( ":" ) ;
            if ( qq.length == 1 && field.length() > 0 || !qq[0].equals( field ) ) continue ; 
   	        long val = entry.getValue().longValue() ;
   	        if ( val != 0 )
   	    	  { FacetConstraint fc = new FacetConstraint( query, val ) ;
   	    	    f.add( fc ) ;
   	    	    global.put( query, fc ) ;
   	    	  }
          }
          Collections.sort( f.constraints ) ;
          if ( f.constraints.size() > 0 ) facets.put( f.name, f ) ;
        }
    }
  }
  
  
  /**
   * Send response in OpenSearch format.
   * @param writer - output writer object.
   * @param request - Solr request object.
   * @param response - Solr query response object.
   * @param rsp - Solr response object.
   * @param engine - Velocity engine.
   * @param context - Velocity context.
   * @throws IOException
   * 
   */
  private void sendRSS( Writer writer, SolrQueryRequest request, SolrQueryResponse response,
		           SolrResponse rsp, VelocityEngine engine, VelocityContext context ) throws IOException
  {
	SolrParams params = request.getParams() ;  
	context.put( "title", "Arch: " + params.get( "q") ) ;
	context.put( "description", "Arch search results for query: " + params.get( "q") ) ;
    String baseURL = "?" + request.getParamString() ;
    HttpServletRequest http = (HttpServletRequest)request.getContext().get( "HttpServletRequest" ) ;
    if ( http != null ) baseURL = http.getRequestURI() ; 
	context.put( "link", baseURL ) ;  
    Template template = getTemplate( engine, request, "rss" ) ;
    template.merge( context, writer ) ;	  
  }
  

  /**
   * Get a pre-configured Velocity engine.
   * @param request - Solr request object.
   * @return Velocity engine
   * 
   */
  private VelocityEngine getEngine( SolrQueryRequest request )
  {
    VelocityEngine engine = new VelocityEngine();
    String template_root = request.getParams().get( "v.base_dir" ) ;
    String lang = request.getParams().get( "lang" ) ;
    if ( lang == null || lang.length() == 0 ) lang = "en" ;
    if ( template_root != null ) template_root += "/lang/" + lang ;
    File baseDir = new File( request.getCore().getResourceLoader().getConfigDir() + "/lang/" + lang ) ;
    if ( template_root != null ) baseDir = new File(template_root);
    
    engine.setProperty( VelocityEngine.FILE_RESOURCE_LOADER_PATH, baseDir.getAbsolutePath() ) ;
    engine.setProperty( "params.resource.loader.instance", new SolrParamResourceLoader( request ) ) ;
    SolrVelocityResourceLoader resourceLoader =
            new SolrVelocityResourceLoader( request.getCore().getSolrConfig().getResourceLoader() ) ;
    engine.setProperty( "solr.resource.loader.instance", resourceLoader ) ;
    engine.setProperty( VelocityEngine.RESOURCE_LOADER, "params,file,solr" ) ;
    String propFile = request.getParams().get( "v.properties" ) ;
    try {
    	  if ( propFile == null ) engine.init() ;
            else {
                   InputStream is = null ;
                   try {
                         is = resourceLoader.getResourceStream( propFile ) ;
                         Properties props = new Properties() ;
                         props.load( is ) ;
                         engine.init( props ) ;
                       }
                  finally { if (is != null) is.close(); }
                 }
        } catch ( Exception e ) { throw new RuntimeException( e ) ; }
    return engine;
  }

  
  /**
   * Get Velocity template based on parameters passed in Solr request.
   * @param engine - Velocity engine.
   * @param request - Solr request object.
   * @param name - template name.
   * @return Velocity template.
   * @throws IOException
   */
  private Template getTemplate( VelocityEngine engine, SolrQueryRequest request, String name )
  throws IOException
  {
    Template template;
    String template_name = request.getParams().get( "v.template" ) ;
    String qt = request.getParams().get( "qt" ) ;
    if ( template_name == null && name != null ) template_name = name ;
    String path = (String)request.getContext().get( "path" ) ;
    if ( template_name == null && path != null ) template_name = path ;
    if ( template_name == null && qt != null ) template_name = qt ;
    if ( template_name == null ) template_name = "index";
    try { template = engine.getTemplate(template_name + ".vm") ; }
    catch ( Exception e ) { throw new IOException( e.getMessage() ) ; }
    return template ;
  }

  
  /**
   * Get content type based on parameters passed in Solr request.
   * @param request - Solr request object.
   * @param response - Solr response object.
   * @return String content type.
   */
  public String getContentType( SolrQueryRequest request, SolrQueryResponse response )
  {
    return request.getParams().get( "v.contentType", "text/html;charset=UTF-8" ) ;
  }

  
  /*
   * (non-Javadoc)
   * @see org.apache.solr.response.QueryResponseWriter#init(org.apache.solr.common.util.NamedList)
   */
  public void init(NamedList args) {}
  
  
  /** 
   * Extract a parameter from Solr request.
   * @param reques - Solr request object.
   * @param key - parameter name.
   * @param def - parameter default value.
   * @return parameter value.
   */
  String getParam( SolrQueryRequest request, String key, String def )
  {
	String value = request.getParams().get( key ) ;
	if ( value == null ) value = def ;
	return value ;
  }
}
