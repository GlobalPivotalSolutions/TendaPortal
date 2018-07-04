<?php

 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( "utils.php" ) ) ;
// require_once( local( 'OpenSearch/OpenSearch.php' ) ) ;
 require_once( local( 'out.php' ) ) ; 
 require_once( $authenticator ) ; 

 $query = "" ;
 $start = 0 ;
 $hitsPerPage = 10 ;
 $site = "" ;
 $area = "" ;
 $search = "" ;
 $language = "en" ;
 $searchUrl = "$ArchAddress/search?q={searchTerms}&ar_search={search}&start={startIndex}" .
  "&rows=10&ar_format=rss{authentication}{domain}{user}{groups}{site}{area}" ;
 $format = "results" ;
 $fq = "" ;
     
 authenticate() ;
 $user = getUserName() ;
 $groups = getUserGroups() ;
 
 // get current query parameters
 if ( isset( $_REQUEST[ 'q' ] ) ) $query = stripslashes( $_REQUEST[ 'q' ] ) ;
 if ( isset( $_REQUEST[ 'start' ] ) ) $start = $_REQUEST[ 'start' ] ;
 if ( isset( $_REQUEST[ 'rows' ] ) ) $hitsPerPage = $_REQUEST[ 'rows' ] ;
 if ( isset( $_REQUEST[ 'ar_site' ] ) ) $site = stripslashes( $_REQUEST[ 'ar_site' ] ) ;
 if ( isset( $_REQUEST[ 'ar_area' ] ) ) $area = stripslashes( $_REQUEST[ 'ar_area' ] ) ;
 if ( isset( $_REQUEST[ 'lang' ] ) ) $language = $_REQUEST[ 'lang' ] ;
 if ( isset( $_REQUEST[ 'ar_search' ] ) ) $search = $_REQUEST[ 'ar_search' ] ;
 if ( isset( $_REQUEST[ 'ar_format' ] ) ) $format = $_REQUEST[ 'ar_format' ] ;
 if ( isset( $_REQUEST[ 'fq' ] ) ) $fq = $_REQUEST[ 'fq' ] ;

 if ( $query == "" && !isset( $_REQUEST[ 'ar_fall' ]  ) ) // this is a request for query form
 {
   // get texts of selects
   $ret = Utils::getJsp( "", $user, $groups, "readSelects", "isRoot=y" ) ; 
   if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
   $selects = $ret[ 1 ] ;
   list( $selectSite, $selectArea ) = explode(  "<>", $selects ) ; 
   showQueryPage( $selectSite, $selectArea, $language, $hitsPerPage ) ;
   die ;
 } 

 if ( isset( $_REQUEST[ 'ar_fall' ]  ) )
 {
  // construct query of advanced search fields
  $query = Utils::makeQuery() ;
  $search = "simple" ; 
 }

 // add filters to the search description
 if ( $domain != "" && $domain != null ) $domain = "&ar_domain=".urlencode( $domain ) ;  else $domain="" ;
 $searchUrl = str_replace( "{domain}", $domain, $searchUrl ) ;
 if ( $site != "" && $site != "all" && $site != null ) $site = "&ar_site=".urlencode( $site ) ;  else $site="" ;
 $searchUrl = str_replace( "{site}", $site, $searchUrl ) ;
 if ( $area != "" && $area != "all" && $area != null ) $area = "&ar_area=".urlencode( $area ) ;  else $area="" ;
 $searchUrl = str_replace( "{area}", $area, $searchUrl ) ;
 $user = "&ar_user=".urlencode( $user ) ;
 $searchUrl = str_replace( "{user}", $user, $searchUrl ) ;
 $groups = "&ar_groups=".urlencode( $groups ) ;
 $searchUrl = str_replace( "{groups}", $groups, $searchUrl ) ;
 $auth = "&ar_frontid=".$id."&ar_password=".$password ; 
 $searchUrl = str_replace( "{authentication}", $auth, $searchUrl ) ;
 $searchUrl = str_replace( "{search}", $search, $searchUrl ) ;

 $searchUrl = str_replace( "rows=10", "rows=".$hitsPerPage, $searchUrl ) ;
 $searchUrl = str_replace( "start={startIndex}", "start=".$start, $searchUrl ) ;
 if ( !isset( $_REQUEST[ 'ar_fall' ]  ) ) // simple search
      $searchUrl = str_replace( "q={searchTerms}", "q=".urlencode( $query ), $searchUrl ) ;
      else $searchUrl = str_replace( "q={searchTerms}", $query, $searchUrl ) ;
 if ( $fq != "" ) $searchUrl .= "&fq=".urlencode( $fq ) ;
 
 // search

 $contents = file_get_contents( $searchUrl ) ;
 $results= array() ;
 if ( $contents != null ) 
 {
   $p = xml_parser_create();
   xml_parse_into_struct( $p, $contents, $vals, $index ) ;
   xml_parser_free( $p ) ;
   $count = count( $index[ "DESCRIPTION" ] ) ;
   $first = true ;
   $nx = $index[ "OPENSEARCH:TOTALRESULTS" ][ 0 ] ;
   $total = $vals[ $nx ][ "value" ] ;
   for( $n = 1 ; $n < $count && $n <= $hitsPerPage ; $n++ )
   {
     if ( !isset( $index[ "TITLE"][ $n ] ) || !isset( $index[ "LINK"][ $n ] ) ) break ;
     $item = array() ;
     $item[ "link" ] = $vals[ $index[ "LINK"][ $n ] ][ "value" ] ;
     $url = "goto.php?url=".urlencode( $item[ 'link' ] ) ; 
     $item[ 'url' ] = $url ;
     if ( isset( $vals[ $index[ "TITLE"][ $n ] ][ "value"] ) )
           $item[ "title" ] = $vals[ $index[ "TITLE"][ $n ] ][ "value" ] ;
     else $item[ "title" ] = $url ;
     if ( isset( $vals[ $index[ "DESCRIPTION"][ $n ] ][ "value" ] ) )
        $item[ "description" ] = $vals[ $index[ "DESCRIPTION"][ $n ] ][ "value" ] ;
        else $item[ "description" ] = "" ;
     array_push( $results, $item ) ;
   }
   
   // Let's form facet arrays
   $fc = count( $index[ "CONSTRAINT" ] ) ;
   $facets = array() ;
   $global = array() ;
   for (  $n = 0 ; $n < $fc ; $n++ )
   {
     $constraint =  $vals[ $index[ "CONSTRAINT" ][ $n ] ] ;
     $value = $constraint[ "attributes"][ "CVALUE" ] ;
     $count = $constraint[ "attributes"][ "CCOUNT" ] ;
     if ( isset( $constraint[ "attributes"][ "FIELD" ] ) )
     {
       $field = $constraint[ "attributes"][ "FIELD" ] ;
       if ( !isset( $facets[ $field ] ) ) $facets[ $field ] = array() ;
       $facets[ $field ][ $value ] = $count ;
     } else $global[ $value ] = $count ;
   }
   ksort( $global ) ;
   ksort( $facets ) ;                    
   foreach( $facets as $constraints ) { ksort( $constraints ) ; } 
 }
 // $total = $os->getTotalResults() ;

 $url = "arch.php?q=".urlencode($query)."&start=##&lang=$language&seach=$search".$site.$area ;
 $pages = printPages( $query, $total, $start, $hitsPerPage, $url ) ;

 showResults( $query, $results, $total, $url, $pages, $start + 1,
              $start +  ( $total ? count( $results ) : 0 ), $search, $language, $facets, $global, $format ) ;

 function printPages( $query, $total, $start, $hits, $url )
 {
  global $ms ;
  $out = "" ;
  $next = "" ;
  if ( $total <= 0 ) return "" ;
  $pages = (int)( $total / $hits ) ;
  if ( $hits * $pages < $total ) $pages++ ;
  $current = (int)( $start / $hits ) + 1 ;
  if ( $current > 5 ) $out .= "<span class='ellipsis'> ... </span>&nbsp;\n"   ;
  
  $i = $current - 5 ;
  if ( $i < 1 ) $i = 1 ;
  $last = $i + 9 ;
  
  for ( ; $i <= $last && $i <= $pages ; $i++ )
   {
     if ( $i == $current )  $out .= "<span class='thispage'>$i</span>" ;
      else { $u = str_replace( "&start=##", "&start=".(($i-1) * $hits), $url ) ;
             $u = Utils::htmlEncode( $u ) ;
             $out .= "<a href='$u' class='page' onclick='return linkClick( event );'>$i</a>\n" ;
             if ( $current ==  $i - 1 ) $next = "<a href='$u' class='next' onclick='return linkClick( event );'>$ms->next</a>\n" ;
           }
   } 
  if ( $i < $pages ) $out .= "<span class='ellipsis'> ... </span>\n"   ;
  $out .= $next ; 
  return $out ;  
 }
?>
