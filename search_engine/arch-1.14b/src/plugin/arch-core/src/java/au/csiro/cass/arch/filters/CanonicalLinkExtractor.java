/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.filters;

import org.apache.nutch.parse.HtmlParseFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.protocol.Content;
import org.apache.hadoop.conf.Configuration;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class CanonicalLinkExtractor implements HtmlParseFilter
{
  public static final Logger LOG = LoggerFactory.getLogger(CanonicalLinkExtractor.class);
  private Configuration conf;
	  
  public ParseResult filter( Content content, ParseResult parseResult,
	                          HTMLMetaTags metaTags, DocumentFragment doc )
  {

	Parse parse = parseResult.get(content.getUrl());
	walk( doc, parse, metaTags, false, false ) ;
	return parseResult;
  }

  private void walk( Node n, Parse parse, HTMLMetaTags metaTags, boolean inCategory, boolean inAccess )
  {
	if (n instanceof Element)
	{
	  String name = n.getNodeName();
	  if ( name.equalsIgnoreCase( "link" ) )
	  {
        NamedNodeMap attrs = n.getAttributes();
		Node rel = attrs.getNamedItem( "rel" ) ;
		if ( rel != null && "canonical".equalsIgnoreCase( rel.getNodeValue() ) ) 
		{
		  try {
		        String content = attrs.getNamedItem("href").getNodeValue();
		        parse.getData().getParseMeta().set( "canonical", content ) ;
                return ;
		      } catch( Exception e ) {} ;
		}	    
	  } else if ( name.equalsIgnoreCase( "meta" ) && parse.getData().getParseMeta().get( "canonical" ) == null ) 
	  {
        NamedNodeMap attrs = n.getAttributes();
		Node rel = attrs.getNamedItem( "name" ) ;
		if ( rel != null && "DC.Identifier".equalsIgnoreCase( rel.getNodeValue() ) ) 
		{
		  try {
		        String href = attrs.getNamedItem("content").getNodeValue();
		        parse.getData().getParseMeta().set( "canonical", href ) ;
                return ;
		      } catch( Exception e ) {} ;
		}	    
	  } else if ( name.equalsIgnoreCase( "div" ) )
	  // The code here is extracting category and access information from Drupal pages that
	  // contain this information in fields named "field-category" and "field-access".
	  // It depends on the way Drupal formats its pages and assigns classes to DIV elements to
	  // find and extract category and access data.
	  {
	    NamedNodeMap attrs = n.getAttributes();
	    NodeList nl = n.getChildNodes() ;
	    Node child = nl.item( 0 ) ;
	    
	    Node cssclass = attrs.getNamedItem( "class" ) ;
	    if ( cssclass != null )
	    {
	      String classes = cssclass.getNodeValue() ;
	      if ( classes.indexOf( "field-item even" ) >= 0 )
	        {
	    	  if ( inCategory )
	            {
	              String category = child.getNodeValue() ;
	              parse.getData().getParseMeta().set( "category", category ) ;
	            }
	          else if ( inAccess )
	            {
	              String access = child.getNodeValue() ;
	              parse.getData().getParseMeta().set( "access", access ) ;
	            }
	        }
	      else if ( classes.indexOf( "field-name-field-category" ) >= 0 ) inCategory = true ;
	      else if ( classes.indexOf( "field-name-field-access" ) >= 0 ) inAccess = true ;
	    }
	  }
	}
    NodeList nl = n.getChildNodes();
    for (int i = 0; i < nl.getLength(); i++) 
      walk(nl.item(i), parse, metaTags, inCategory, inAccess ) ;
  }
	  
  public void setConf( Configuration conf )
  {
	 this.conf = conf;
  }

  public Configuration getConf()
  {
    return this.conf;
  }
	
}
