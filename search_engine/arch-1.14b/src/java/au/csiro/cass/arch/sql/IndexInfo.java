/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * A container for index structure
 * 
 */
public class IndexInfo
{
  ArrayList       sites ; // names of all sites
  ArrayList       areas ; // names of all ares 
  Map         siteAreas ; // areas for each site, where site name is the key
  
  public IndexInfo()
  { siteAreas = new HashMap() ; sites = new ArrayList() ; areas = new ArrayList() ; }

  public ArrayList getAllAreas()
   {
    return areas;
   }

  public void setAllAreas( ArrayList areas )
   {
    this.areas = areas;
   }

  public ArrayList getSites()
   {
    return sites;
   }

  public void setSites( ArrayList sites )
   {
    this.sites = sites;
   }

  public Map getSiteAreas()
   {
    return siteAreas;
   }

  public void setSiteAreas( Map siteAreas )
   {
    this.siteAreas = siteAreas;
   }
  
  public void setAreas( ArrayList areas, String site )
   {
    this.siteAreas.put( site, areas ) ;
   }
  
  public ArrayList getAreas( String site )
   {
     return (ArrayList)siteAreas.get( site ) ;
   }
  
  public void addArea( String site, String area )
   {
     ArrayList arr = (ArrayList)siteAreas.get( site ) ;
     if ( arr == null )
        { arr = new ArrayList() ; siteAreas.put( site, arr ) ; sites.add( site ) ; }
     arr.add( area ) ;
     if ( !areas.contains( area ) ) areas.add( area ) ;
   }
  
}
