<?php
 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( "IndexNode.php" ) ) ;
 require_once( local( "language/$language/messages.php" ) ) ; 

function initSmarty()
{
  global $language, $ArchAddress ;
  require_once( local( './Smarty/libs/Smarty.class.php' ) ) ;
  $smarty = new Smarty ; // templating object
  $smarty->template_dir = "./language/$language";
  $smarty->compile_dir = './Smarty/templates_c';
  $smarty->cache_dir = './Smarty/cache';
  $smarty->config_dir = './Smarty/configs';
  $smarty->assign( 'ArchAddress', $ArchAddress ) ;
  $theme = Utils::getTheme() ;
  $smarty->assign( "theme", $theme ) ;
  return $smarty ;
}

function showQueryPage( $selectSite, $selectArea, $language, $hitsPerPage )
{
  global $ms ;   
  $smarty = initSmarty() ;
  $loggedIn = isset( $_SESSION[ "loggedin" ] ) && $_SESSION[ "loggedin" ] == "Y" ;
  $login = "inline" ; 
  $logout = "inline" ;
  if ( $loggedIn ) $login = "none" ; else $logout = "none" ;
  $smarty->assign( "styleLogin", $login ) ;
  $smarty->assign( "phrase_search", $ms->phrase_search ) ;
  $smarty->assign( "query_prompt", $ms->query_prompt ) ;
  $smarty->assign( "styleLogout", $logout ) ;
  $smarty->assign( 'selectSite', $selectSite ) ;
  $smarty->assign( 'selectArea', $selectArea ) ;
  $smarty->assign( 'language', $language ) ;
  $smarty->assign( 'rows', $hitsPerPage ) ;
  $smarty->display( local_template( "query.tpl" ) ) ;
}

function showResults( $query, $items, $total, $url, $pages, $start, $end,
                                              $search, $language, $facets, $global, $format )
{
  global $ms ;
  $smarty = initSmarty() ;
  $smarty->assign( 'items', $items ) ;
  $smarty->assign( 'total', $total ) ;
  $smarty->assign( 'url', $url ) ;
  $smarty->assign( 'pages', $pages ) ;
  $smarty->assign( 'start', $start ) ;
  $smarty->assign( 'search', $search ) ;
  $smarty->assign( 'language', $language ) ;
  $smarty->assign( 'end', $end ) ;
  // If it is not advanced query, show it
  if ( strpos($query, 'ar_fnot') === false && strpos($query, 'ar_fall') === false )
         $smarty->assign( 'query', $query ) ;
  $smarty->assign( 'facets', $facets ) ;
  $typeOn = false ;
  foreach ($global as $key => $value)
    if ( strpos( $key, "type:", 0 ) === 0 ) { $typeOn = true ; break ; }
  if ( !$global || count( $global ) == 0 )  $global = false ;
  $smarty->assign( 'allfacets', $global ) ;
  $smarty->assign( 'typeOn', $typeOn ) ;
  $smarty->assign( "phrase_search", $ms->phrase_search ) ;
  $smarty->assign( "query_prompt", $ms->query_prompt ) ;
  $smarty->display( local_template( "$format.tpl" ) ) ;
}


function sendNode( $encoding, $node, $isRootLevel, $user, $groups )
{
 global $ArchAddress ;
 $images = "images" ;
 if ( $encoding != "html" ) return $node->toString() ; // just return serialised node
 // else send html presentation of the node
 // As there is nothing to customise here, and this is performance sensitive place,
 // therefore will not use templates.
 if ( $node == null ) return "" ;
 $site = $isRootLevel ? "_root_" : $node->site ;
 $rw = $node->canWrite( $user, $groups ) ? "rw='rw' " : "rw='ro' " ;
 $url = addWithDelim( $node->base, $node->path ) ;
 if ( !$isRootLevel ) $url = addWithDelim( $url, $node->name ) ;
 $title = $node->title != null ? $node->title : "" ;
 $label = $node->label != null && $node->label != "" ? $node->label : $node->name ;
 $buf = "" ;
 $buf .= "<LI id='node.".$site.".".$node->id."' " ;
 $buf .= " site='".$node->site."' " ;
 $buf .= $rw ; 
 if ( strpos( $node->type, 'f' ) !== false ) // this is a file 
      { $buf .= " class='file' noChildren='true'>\n" ;
        $buf .= "<img style='visibility: hidden;' src='$images/plus.gif'>" ;
        $buf .= "<img src='$images/file.gif' title='" . $title ;
        $buf .= "'>\n<A target='_blank' style='font-size:1em;' href='" . $url . "'>" ; 
        $buf .= $label . "</A>\n</LI>\n" ;
      } else // this is a directory
      {
        $buf .= " class='folder'>\n" ;
        $buf .= "<img src='$images/plus.gif'>" ;
        $buf .= "<img src='$images/folder.gif' title='" . $title ;
        $buf .= "'>\n<A target='_blank' style='font-size:1em;' href='" . $url . "'>" ; 
        $buf .= $label . "</A>\n<UL></UL>\n</LI>\n" ;  
      }
 return $buf ;
}

function  addWithDelim( $str1, $str2 ) // assuming str1 never null 
{
  if ( $str2 == null || $str2 == "" ) return $str1 ;
  if ( !(substr( $str1, strlen( $str1 ) -1 ) === "/") && !( substr( $str2, 0, 1 ) === "\\") ) return "$str1/$str2" ;
  if ( !(substr( $str1, strlen( $str1 ) -1 ) === "/") && !( substr( $str2, 0, 1 ) === "\\") ) return $str1.substr( $str2, 1 ) ;
  return $str1.$str2 ;
}

function sendNodes( $encoding, $nodes, $isRoot, $user, $groups )
{
  $html = $encoding == "html" ;
  $buf = "" ;   
  foreach( $nodes as $node )
  {
    if ( $node != null ) 
         if ( $html )
                  $buf .= sendNode( $encoding, $node, $isRoot, $user, $groups ) ;
             else {
                    $nn = sendNode( $encoding, $node, $isRoot, $user, $groups ) ;
                    if ( $nn != "" && $buf != "" ) $buf .= '<' ;
                    $buf .= $nn ;
                  }
  }
  return $buf ;  
}

function sendPropertiesForm( $node, $isRoot )
{
  if ( $node == null ) $node = new IndexNode() ;
  $smarty = initSmarty() ;
  
  $smarty->assign( 'inherited',  substr( $node->access, 0, 1 ) === 'i' ? "CHECKED" : "" ) ;
  $smarty->assign( 'name', $node->name != null ? $node->name : "" ) ; 
  $smarty->assign( 'label', $node->label != null ? $node->label : "" ) ;  
  $smarty->assign( 'title', $node->title != null ? $node->title : "" ) ; 
  $smarty->assign( 'groupr', $node->groupr != null ? $node->groupr : "" ) ; 
  $smarty->assign( 'groupw', $node->groupw != null ? $node->groupw : "" ) ;
  $smarty->assign( 'userr', $node->userr != null ? $node->userr : "" ) ;
  $smarty->assign( 'userw', $node->userw != null ? $node->userw : "" ) ;   
  $smarty->assign( 'owners', $node->owners != null ? $node->owners : "" ) ;   
  $smarty->assign( 'site', $isRoot ? "_root_" : $node->site ) ;
  $smarty->assign( 'nodeSite', $node->site ) ;
  $smarty->assign( 'titleDisabled', $isRoot ? "disabled='disabled'" : "" ) ;
  $smarty->assign( 'nodeId', $node->id ) ;
  
  ob_start() ;
  $smarty->display( local_template( "properties.tpl" ) ) ;
  $msg = ob_get_contents() ;
  ob_end_clean() ;
  return $msg ;
}

function sendLoginForm()
{
  $smarty = initSmarty() ;  
  ob_start() ;
  $smarty->display( local_template( "login.tpl" ) ) ;
  $msg = ob_get_contents() ;
  ob_end_clean() ;
  return $msg ;
}

function showDirectory()
{
  global $ArchAddress ;
  $images = "images" ;
  $smarty = initSmarty() ;  
  ob_start() ;
  if ( isset( $_SESSION[ "loggedin" ] ) ) $loggedIn = $_SESSION[ "loggedin" ] == "Y" ;
   else $loggedIn = false ;
  $login = "inline" ; 
  $logout = "inline" ;
  if ( $loggedIn ) $login = "none" ; else $logout = "none" ;
  $smarty->assign( "styleLogin", $login ) ;
  $smarty->assign( "styleLogout", $logout ) ;
  $smarty->assign( "images", $images ) ;
  $smarty->display( local_template( "dir.tpl" ) ) ;
  $msg = ob_get_contents() ;
  ob_end_clean() ;
  return $msg ;
}

?>
