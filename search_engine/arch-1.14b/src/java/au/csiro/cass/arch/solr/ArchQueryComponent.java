/*
 * Takes care of Arch related filters that can be prepared by the authenticator
 * or submitted as request fields. Also, creates a query in Solr syntax from 
 * advanced query form fields.
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */


package au.csiro.cass.arch.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.CursorMarkParams;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrResourceLoader;
import org.apache.solr.handler.component.MergeStrategy;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.CursorMark;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.RankQuery;
import org.apache.solr.search.ReturnFields;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SolrReturnFields;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.servlet.HttpSolrCall;

import au.csiro.cass.arch.utils.ConfigList;
import au.csiro.cass.arch.utils.Utils;

public class ArchQueryComponent extends QueryComponent
{

  Map<String,ConfigList> configCache = new HashMap<String,ConfigList>() ;
  /*
   * 	(non-Javadoc)
   * @see org.apache.solr.handler.component.QueryComponent#prepare(org.apache.solr.handler.component.ResponseBuilder)
   */
  @Override
  public void prepare(ResponseBuilder rb) throws IOException
  {
    SolrQueryRequest req = rb.req ;
	SolrParams params = req.getParams() ;
	HttpSolrCall call = req.getHttpSolrCall() ;
	HttpServletRequest request = call != null ? call.getReq() : null ;
	// First, filter prepared by the Authenticator (if any) 
	String filter = (String)req.getContext().get( "ar_filter" ) ;
	// Then, extra Arch filters coming in request parameters
	if ( request != null && filter == null ) // else this is done by the Authenticator
	{
	  String sites = Utils.notEmpty( (String)request.getParameter( "ar_site" ) ) ;
	  String areas = Utils.notEmpty((String)request.getParameter( "ar_area" ) ) ;
	  String users = Utils.notEmpty((String)request.getParameter( "ar_user" ) ) ;
	  String groups = Utils.notEmpty((String)request.getParameter( "ar_groups" ) ) ;
	  filter =  Utils.makeFilter( sites, areas, users, groups ) ;
	  req.getContext().put( "ar_filter", filter ) ;
	}

	if (!params.getBool(COMPONENT_NAME, true)) {
      return;
    }
	
	handleFacets( req ) ;
    
    SolrQueryResponse rsp = rb.rsp ;

    // Set field flags
    ReturnFields returnFields = new SolrReturnFields( req ) ;
    rsp.setReturnFields( returnFields );
    int fieldFlags = 0;
    if ( returnFields.wantsScore() ) {
      fieldFlags |= SolrIndexSearcher.GET_SCORES ;
    }
    rb.setFieldFlags( fieldFlags ) ;

    String defType = params.get(QueryParsing.DEFTYPE,QParserPlugin.DEFAULT_QTYPE);

    // get it from the response builder to give a different component a chance
    // to set it.
    String queryString = rb.getQueryString();
    if ( queryString == null )
    {
       String query = params.get( CommonParams.Q ) ;
       if ( query == null )
       { 
    	 query = Utils.makeQuery( params ) ; // could be advanced search
    	 if ( query == null || query.length() == 0 ) // still no query - send the query form
    	 {
    	   query = "ar_site:sendQueryForm" ; // this will be fast and won't find anything
           req.getContext().put( "ar_action", "sendQueryForm" ) ;
    	 }
       } else query = Utils.parseField( query, null, "", "", false ) ;
       rb.setQueryString( query ) ; 
    }

    try
    {
      QParser parser = QParser.getParser( rb.getQueryString(), defType, req ) ;
      Query q = parser.getQuery() ;
      if ( q == null )
        q = new MatchNoDocsQuery() ;
      
      rb.setQuery( q ) ;
      
      String rankQueryString = rb.req.getParams().get( CommonParams.RQ ) ;
      if( rankQueryString != null )
      {
        QParser rqparser = QParser.getParser( rankQueryString, req ) ;
        Query rq = rqparser.getQuery() ;
        if( rq instanceof RankQuery )
        {
          RankQuery rankQuery = (RankQuery)rq ;
          rb.setRankQuery( rankQuery ) ;
          MergeStrategy mergeStrategy = rankQuery.getMergeStrategy() ;
          if( mergeStrategy != null )
          {
            rb.addMergeStrategy( mergeStrategy ) ;
            if( mergeStrategy.handlesMergeFields() )
            {
              rb.mergeFieldHandler = mergeStrategy;
            }
          }
        } else throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,"rq parameter must be a RankQuery");
      }      
      
      rb.setSortSpec( parser.getSortSpec( true ) ) ;
      rb.setQparser( parser ) ;

      final String cursorStr = rb.req.getParams().get(CursorMarkParams.CURSOR_MARK_PARAM);
      if (null != cursorStr) {
        final CursorMark cursorMark = new CursorMark(rb.req.getSchema(),
                                                     rb.getSortSpec());
        cursorMark.parseSerializedTotem(cursorStr);
        rb.setCursorMark(cursorMark);
      }      
      
      String[] fqs0 = req.getParams().getParams( CommonParams.FQ ) ;
      ArrayList<String> fqs = new ArrayList<String>() ;
      if ( fqs0 != null && fqs0.length != 0 ) Collections.addAll( fqs, fqs0 ) ;
      if ( filter != null && filter.length() > 0 ) fqs.add( filter ) ;
      if ( fqs != null && fqs.size() != 0 )
      {
        List<Query> filters = rb.getFilters() ;
        if ( filters==null ) filters = new ArrayList<Query>( fqs.size() ) ;
        for ( String fq : fqs ) 
          if ( fq != null && fq.trim().length()!=0 )
          {
            QParser fqp = QParser.getParser(fq, null, req);
            filters.add(fqp.getQuery());
          }
 
        // only set the filters if they are not empty otherwise
        // fq=&someotherParam= will trigger all docs filter for every request 
        // if filter cache is disabled
        if (!filters.isEmpty()) {
          rb.setFilters( filters );
        }
      }
    } catch ( SyntaxError e)
    {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, e);
    }

    if (params.getBool(GroupParams.GROUP, false))
    {
      prepareGrouping(rb);
    } else {
      //Validate only in case of non-grouping search.
      if(rb.getSortSpec().getCount() < 0) {
        throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "'rows' parameter cannot be negative");
      }
    }

    //Input validation.
    if (rb.getSortSpec().getOffset() < 0) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "'start' parameter cannot be negative");
    }
  }
  
  public void handleFacets( SolrQueryRequest req )
  {
	SolrParams params = req.getParams() ;
	String param = params.get( "facet" ) ;
	// If done already here, or by gateway or by Solr configuration
	String baseURL = (String)req.getContext().get( "baseURL" ) ;
	if ( param == null ) // check configuraton file and set up faceting 
	           setupFaceting( req ) ;
		
	// If done by the Authenticator for a site (domain) query already
	if ( baseURL != null ) return ;
	
	baseURL = "?" + req.getParamString() ;
	baseURL = Utils.removeParam( baseURL, "ar_fc" ) ;
	String[] ar_fc = params.getParams( "ar_fc" ) ;
	if ( ar_fc != null ) // clean the base URL of the facet clause
	 {
	   String clause = Utils.makeFQ( ar_fc ) ;
	   baseURL = Utils.removeClause( baseURL, clause ) ;
	 }
	req.getContext().put( "ar_fc", ar_fc ) ;
	req.getContext().put( "baseURL", baseURL ) ;
  }

  @Override
  public String getDescription() { return "Arch QueryFilter" ; }
    
  
  protected void setupFaceting( SolrQueryRequest sreq )
  {
    
    ConfigList cfg = null ;
    String site = sreq.getParams().get( "ar_domain" ) ;
    try
    {
      cfg = getConfig( site );
    } catch ( IOException e )
    {
      // must be a bogus domain, ignore it
    }
    if ( cfg == null ) return ;
        
    boolean facetSites = cfg.getInherited( "facet.sites", true ) ;
    boolean facetAreas = cfg.getInherited( "facet.areas", true ) ;
    boolean facetSizes = cfg.getInherited( "facet.sizes", false ) ;
    boolean facetDates = cfg.getInherited( "facet.dates", false ) ;
    boolean facetTypes = cfg.getInherited( "facet.types", true ) ;
    boolean faceting = cfg.getInherited( "facet", true ) ;
    faceting &= facetSites || facetAreas || facetSizes || facetDates || facetTypes ;
    
    ModifiableSolrParams params = new ModifiableSolrParams( sreq.getParams() ) ;
    if ( faceting ) params.add( "facet", "on" ) ; else params.add( "facet", "off" ) ;
    if ( facetSites && faceting ) params.add( "facet.field", "ar_site" ) ;
    if ( facetAreas && faceting ) params.add( "facet.field", "ar_area" ) ;
    if ( facetTypes && faceting ) params.add( "facet.field", "type" ) ;
    if ( facetSizes && faceting ) // could be done with "standard" facet.range params, but want non-linear scale
    {
        params.add( "facet.query", "contentLength:[0 TO 1023]" ) ;  // < 1K
        params.add( "facet.query", "contentLength:[1024 TO 10239]" ) ;  // < 10K
        params.add( "facet.query", "contentLength:[10240 TO 51199]" ) ; // < 50K
        params.add( "facet.query", "contentLength:[51200 TO 102399]" ) ; // < 100K
        params.add( "facet.query", "contentLength:[102400 TO 511999]" ) ; // < 500K
        params.add( "facet.query", "contentLength:[512000 TO 1048575]" ) ; // < 1M
        params.add( "facet.query", "contentLength:[1048576 TO 5242879]" ) ; // < 5M
        params.add( "facet.query", "contentLength:[5242880 TO 10485759]" ) ; // < 10M
        params.add( "facet.query", "contentLength:[10485760 TO 52428799]" ) ; // < 50M
        params.add( "facet.query", "contentLength:[52428800 TO 104857599]" ) ; // < 100M
        params.add( "facet.query", "contentLength:[104857600 TO 524287999]" ) ; // < 500M
        params.add( "facet.query", "contentLength:[524288000 TO *]" ) ; // >= 500M
    }
    if ( facetDates && faceting ) // could be done with "standard" facet.date params, but want non-linear scale
    {
        params.add( "facet.query", "lastModified:[NOW/DAY-7DAYS TO NOW/DAY+1DAY]" ) ;  
        params.add( "facet.query", "lastModified:[NOW/DAY-1MONTH TO NOW/DAY-7DAYS]" ) ; 
        params.add( "facet.query", "lastModified:[NOW/DAY-3MONTHS TO NOW/DAY-1MONTH]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-6MONTHS TO NOW/DAY-3MONTHS]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-1YEAR TO NOW/DAY-6MONTHS]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-2YEARS TO NOW/DAY-1YEAR]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-3YEARS TO NOW/DAY-2YEARS]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-4YEARS TO NOW/DAY-3YEARS]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-5YEARS TO NOW/DAY-4YEARS]" ) ;
        params.add( "facet.query", "lastModified:[NOW/DAY-10YEARS TO NOW/DAY-5YEARS]" ) ;
        params.add( "facet.query", "lastModified:[* TO NOW/DAY-10YEARS]" ) ;
    }
    sreq.setParams( params ) ;
  }
  
  
  protected ConfigList getConfig( String site ) throws IOException
  {
    String solrHome = SolrResourceLoader.locateSolrHome().toString();
    String archDir = solrHome + "/conf/arch" ;
    String archConfig = archDir + "/config.txt" ;
    ConfigList rootCfg = null ;
    ConfigList cfg = null ;
    synchronized( configCache )
    {
      if ( "clearConfigCache".equalsIgnoreCase(  site ) )
                               { configCache.clear() ; return null ; }
      rootCfg = configCache.get( archConfig ) ;
      if ( rootCfg == null )
      { 
        rootCfg = ConfigList.newConfigList( archConfig ) ;
        configCache.put( archConfig, rootCfg ) ;
      }
      cfg = rootCfg ;
//    String site = req.getParameter( "ar_domain" ) ;
      if ( site != null )
      {
        String siteConfig = archDir + "/sites/" + site + "/config.txt" ; 
        cfg = configCache.get( siteConfig ) ;
        if ( cfg == null )
        { 
          cfg = ConfigList.newConfigList( siteConfig, rootCfg ) ;
          configCache.put( siteConfig, cfg ) ;
        }
      }
    }
    return cfg ;
  }
    


}
