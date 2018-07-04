/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * Scanning context object containing necessary references for scanning.
 * When implementing a scanner, look what is available in the context.
 * 
 */

package au.csiro.cass.arch.security;

import org.apache.nutch.parse.ParseResult;
import org.apache.nutch.protocol.Content;

import au.csiro.cass.arch.sql.IndexRootDB;
import au.csiro.cass.arch.sql.IndexSiteDB;

public class ScanContext
{
   public IndexRootDB         rootDB ; // root database interface
   public IndexSiteDB         siteDB ; // site database interface
   public String                site ; // site name
   public ScanConfig          config ; // scan configuration
   public String                 url ; // URL of the page
   public String     originalContent ; // original page content string
   public String      lowcaseContent ; // lower case content string
   public String      originalSource ; // original page content string
   public String       lowcaseSource ; // lower case content string
   public String         contentType ; // content type
   public String            fileType ; // file type
   public ParseResult    parseResult ; // parsing result
   public Content            content ; // Nutch content object
   public ScanUtil              util ; // ScanUtil object
   public boolean        scanningSrc ; // true if to scan source of the page
   public boolean        scanningOut ; // true if to scan output of the page
   public boolean            pruning ; // true if the page to be pruned before parsing
   public boolean        pruningAfter; // true if the page to be pruned after parsing
   public String                path ; // URL path part

}
