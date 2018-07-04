/*
 * A container for search results.
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */


package au.csiro.cass.arch.solr;

public class ResultsItem
{
  public String        title ;
  public String  description ;
  public String         link ;

  public ResultsItem( String title, String description, String link )
  {
		this.title = title ;
		this.description = description ;
		this.link = link ;
  }
  
  public String getTitle() {
	return title;
  }

  public void setTitle(String title) {
	this.title = title;
  }

  public String getDescription() {
	return description;
  }

  public void setDescription(String description) {
	this.description = description;
  }

  public String getLink() {
	return link;
  }

  public void setLink(String link) {
	this.link = link;
  }

}
