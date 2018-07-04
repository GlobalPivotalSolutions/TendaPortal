/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */

package au.csiro.cass.arch.solr;

import java.util.ArrayList;

public class Facet
{
  String                               name ; // Facet name
  ArrayList<FacetConstraint>    constraints ; // Facet constraints
  
  
  /**
  * @param name
  */
  public Facet( String name )
  {
	this.name = name ;
	constraints = new ArrayList<FacetConstraint>() ;
  }
  
  
  /**
  * @param constraint
  */
  public void add( FacetConstraint constraint )
  {
	constraints.add( constraint ) ; 
  }
  
  
  /**
  * @return name of the facet field
  */
  public String getName() {
	return name;
  }
  
  /**
  * @param name of the facet
  */
  public void setName(String name) {
	this.name = name;
  }
 
  /**
  * @return array list of FacetConstraint objects
  */
  public ArrayList<FacetConstraint> getConstraints() {
	return constraints;
  }
  
  /**
  * @param constraints
  */
  public void setConstraints(ArrayList<FacetConstraint> constraints) {
	this.constraints = constraints;
  }

}
