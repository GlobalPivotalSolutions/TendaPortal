/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.solr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Properties;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrParamResourceLoader;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.response.SolrVelocityResourceLoader;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

public class AJAXResponseWriter implements QueryResponseWriter
{
  String contentType = "default" ;	
 /*
  * (non-Javadoc)
  * @see org.apache.solr.response.QueryResponseWriter#write(java.io.Writer, org.apache.solr.request.SolrQueryRequest, org.apache.solr.response.SolrQueryResponse)
  */
 public void write( Writer writer, SolrQueryRequest request, SolrQueryResponse response )
 throws IOException
 {
   VelocityEngine engine = getEngine( request ) ;
   Template template ;
   VelocityContext context = (VelocityContext)( request.getContext().get( "ar_AJAXResponse" ) ) ;
   String name = (String)context.get( "template" ) ;
   contentType = (String)context.get( "contentType" ) ;
   if ( contentType == null ) contentType = "default" ;
   template = getTemplate( engine, request, name ) ;
   if ( context.get("code" ) == null ) context.put( "code", " ++Failed " ) ;
   template.merge( context, writer ) ;
 }

 
 /**
  * Get Velocity engine based on parameters passed in Solr request.
  * @param request - Solr request object.
  * @return configured Velocity engine.
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
   String template_name = name ;
   String qt = request.getParams().get( "qt" ) ;
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
   String contentType = (String)request.getContext().get( "contentType" ) ;	
   if ( contentType == null || contentType.equals( "default" ) )	 
      return request.getParams().get( "v.contentType", "text/html;charset=UTF-8" ) ;
   else return contentType ;
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
