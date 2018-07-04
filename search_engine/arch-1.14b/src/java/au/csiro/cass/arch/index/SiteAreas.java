/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.index;

import java.util.ArrayList;

/**
 * A container for <site, area> pair C
 * 
 * Copyright 2015, CSIRO Australia All rights reserved.
 * 
 */
public class SiteAreas
{
  /** site name */
  private String site;
  /** set of areas */
  private ArrayList<String> areas;

  /**
   * Constructor
   * 
   * @param site
   *          site to set
   * @param area
   *          area to add
   */
  public SiteAreas( String site, String area )
  {
    this( site );
    if ( area != null )
    {
      getAreas().add( area );
    }
  }

  /**
   * Constructor
   * 
   * @param site
   *          site to set
   * @param areas
   *          areas to set
   */
  public SiteAreas( String site, ArrayList<String> areas )
  {
    this.setSite( site );
    this.setAreas( areas );
  }

  /**
   * Constructor
   * 
   * @param site
   *          site to set
   */
  public SiteAreas( String site )
  {
    this.setSite( site );
    this.setAreas( new ArrayList<String>() );
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  public String toString()
  {
    String s = "SITEAREAS site: " + site + " areas:";
    if ( areas != null )
      for ( String area : areas )
        s += " " + area ;
    return s ;
  }
  
  /**
   * Adds an area to the set of areas
   * 
   * @param area
   */
  public void addArea( String area )
  {
    if ( area != null )
      getAreas().add( area );
  }

  /**
   * Checks if there are area names at all
   * 
   * @return true if there are 
   */
  public boolean hasAreas()
  {
    return getAreas() != null && getAreas().size() > 0;
  }

  /**
   * Checks if the given area name is in the set
   * 
   * @param area
   *          area name to check
   * @return true if it is
   */
  public boolean hasArea( String area )
  {
    if ( getAreas() == null || getAreas().size() == 0 )
      return false;
    for ( String myArea : getAreas() )
      if ( myArea.equalsIgnoreCase( area ) )
        return true;
    return false;
  }

  /**
  /**
   * Checks if any of the given area names are in the set
   * 
   * @param areas
   * @return true if they are
   */
  public boolean hasArea( ArrayList<String> areas )
  {
    if ( this.getAreas() == null || this.getAreas().size() == 0 )
    {
      return false;
    }
    if ( areas == null || areas.size() == 0 )
    {
      return false;
    }
    for ( String myArea : this.getAreas() )
    {
      for ( String theirArea : areas )
      {
        if ( myArea.equalsIgnoreCase( theirArea ) )
        {
          return true;
        }
      }
    }
    return false;
  }

  public String getSite()
  {
    return site;
  }

  public void setSite( String site )
  {
    this.site = site;
  }

  public ArrayList<String> getAreas()
  {
    return areas;
  }

  public void setAreas( ArrayList<String> areas )
  {
    this.areas = areas;
  }

}
