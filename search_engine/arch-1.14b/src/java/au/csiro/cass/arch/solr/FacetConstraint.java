package au.csiro.cass.arch.solr;


public class FacetConstraint implements Comparable
{
  String                    value ; // field value or query
  long                      count ; // hit count

  /**
  * @param value - field value
  * @param count - count of hits with this value
  */
  public FacetConstraint( String value, long count )
  {
	this.value = value ;
	this.count = count ;
  }
  
  /**
  * @return field value
  */
  public String getValue()
  {
	return value;
  }

  /**
  * @param value - field value
  */
  public void setValue( String value )
  {
	this.value = value;
  }

  /**
  * @return count of hits with this value
  */
  public long getCount()
  {
	return count;
  }

  /**
  * @param count - count of hits with this value
  */
  public void setCount( long count )
  {
	this.count = count;
  }

@Override
  public int compareTo( Object arg0 )
  {
	FacetConstraint fc = (FacetConstraint)arg0 ;
	return this.value.compareTo( fc.value ) ;
  }

}
