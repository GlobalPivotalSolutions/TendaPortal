<?php
 // functions for serving ajax requests
 require_once( "localname.php" ) ;  // functions substituting names of files with that of local copies if exist
 require_once( local( "config.php" ) ) ; // configuration parameters
 require_once( local( "IndexNode.php" ) ) ; 
 require_once( local( "utils.php" ) ) ; 
 require_once( local( "out.php" ) ) ; 
 require_once( $authenticator ) ; 
 require_once( local( "language/$language/messages.php" ) ) ; 

 $msg = "" ;
 $failed = false ;
 $area = $query = $site = $action = $nodeId = null ;
 $start = 0 ;
 $hitsPerPage = 10 ;
 $language = "en" ;
 $encoding = "html" ;
 if ( isset( $_REQUEST[ 'q' ] ) ) $query = urldecode( $_REQUEST[ 'q' ] ) ;
 if ( isset( $_REQUEST[ 'start' ] ) ) $start = $_REQUEST[ 'start' ] ;
 if ( isset( $_REQUEST[ 'rows' ] ) ) $hitsPerPage = $_REQUEST[ 'rows' ] ;
 if ( isset( $_REQUEST[ 'ar_site' ] ) ) $site = $_REQUEST[ 'ar_site' ] ;
 if ( isset( $_REQUEST[ 'ar_area' ] ) ) $area = $_REQUEST[ 'ar_area' ] ;
 if ( isset( $_REQUEST[ 'lang' ] ) ) $language = $_REQUEST[ 'lang' ] ;
 if ( isset( $_REQUEST[ 'ar_action' ] ) ) $action = $_REQUEST[ 'ar_action' ] ;
 if ( isset( $_REQUEST[ 'ar_node' ] ) ) $nodeId = $_REQUEST[ 'ar_node' ] ;
 if ( isset( $_REQUEST[ 'enc' ] ) ) $encoding = $_REQUEST[ 'enc' ] ;
 
 try
 {
   $isRootLevel = isset( $_REQUEST[ 'ar_isRoot' ] ) ;
   if ( $site == null && isset( $_REQUEST[ 'fsite' ] ) ) $site = $_REQUEST[ 'fsite' ] ;  // May be a login request
   if ( $site == null && isset( $_REQUEST[ 'ar_site' ] ) ) $site = $_REQUEST[ 'ar_site' ] ; 

   if ( $action == null ) throw new Exception( $ms->action_required ) ;
   
   if ( !authenticate() ) throw new Exception( $ms->auth_failed ) ;
   $user = getUserName() ;
   $groups = getUserGroups() ;
      
   if ( $action == "readNode" )
      { 
        $ret = Utils::getJsp( "&ar_node=$nodeId&ar_site=$site&ar_enc=text", $user, $groups, "readNode", $isRootLevel ) ;
        if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
        $nodeStr = $ret[ 1 ] ;  
        $node = IndexNode::newIndexNode( substr( $nodeStr, 5 ) ) ;
        $msg = sendNode( $encoding, $node, $isRootLevel, $user, $groups ) ;  
        if ( $msg == null ) throw new Exception( $ms->error001 ) ;  
      }
     else if ( $action == "getProperties" ) // html code for properties dialog
      {
        $ret = Utils::getJsp( "&ar_node=$nodeId&ar_site=$site&ar_enc=text", $user, $groups, "getProperties", $isRootLevel ) ;
        if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
        $nodeStr = $ret[ 1 ] ;  
        $node = IndexNode::newIndexNode( $nodeStr ) ;
        if ( $node == null ) throw new Exception( $ms->error002 ) ;  
        $msg = sendPropertiesForm( $node, $isRootLevel ) ;
      }
     else if ( $action == "getLogin" ) // html code for login dialog
      {
        $msg = sendLoginForm() ;
      }
     else if ( $action == "login" ) // process login request
      {  // done in authenticate() 
      }   
     else if ( $action == "logout" )
      {
        logout() ; // must be implemented by authenticator      
      }   
     else if ( $action == "moveNode" )
      {
        $beforeId = $_REQUEST[ "before" ] ;
        $ret = Utils::getJsp( "&ar_node=$nodeId&ar_before=$beforeId&ar_site=$site", $user, $groups, "moveNode", $isRootLevel ) ;
        if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
      }
     else if ( $action == "deleteNode" )
      {
        $ret = Utils::getJsp( "&ar_node=$nodeId&ar_site=$site", $user, $groups, "deleteNode", $isRootLevel ) ;     
        if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
      }
     else if ( $action == "insertNode" )
      {
         throw new Exception( "Inserting a new node is not implemented" ) ;
      }
     else if ( $action == "updateNode" )
      {
         $args = "&ar_node=$nodeId&ar_site=$site" ;
         $access = isset( $_REQUEST[ "ar_access" ] ) ? "&ar_access=i" : "" ;
         $args .= "$access&ar_fname=".urlencode( $_REQUEST[ "ar_fname" ] ) ;
         $args .= "&ar_flabel=".urlencode( $_REQUEST[ "ar_flabel" ] ) ;
         $args .= "&ar_ftitle=".urlencode( $_REQUEST[ "ar_ftitle" ] ) ;
         $args .= "&ar_groupr=".urlencode( $_REQUEST[ "ar_groupr" ] ) ;
         $args .= "&ar_groupw=".urlencode( $_REQUEST[ "ar_groupw" ] ) ;
         $args .= "&ar_userr=".urlencode( $_REQUEST[ "ar_userr" ] ) ;
         $args .= "&ar_userw=".urlencode( $_REQUEST[ "ar_userw" ] ) ;
         $args .= "&ar_owners=".urlencode( $_REQUEST[ "ar_owners" ] ) ;
         $ret = Utils::getJsp( $args, $user, $groups, "updateNode", $isRootLevel ) ;
         if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
      }
     else if ( $action == "readLevel" )
      {
        $ret = Utils::getJsp( "&ar_node=$nodeId&ar_site=$site&ar_enc=text", $user, $groups, "readLevel", $isRootLevel ) ;
        if ( !isset( $ret ) || $ret[ 0 ] == "--Failed" ) throw new Exception( "Operation failed. ".$ret[ 1 ] ) ;
        $levelStr = $ret[ 1 ] ;  
        if ( trim( $levelStr ) != "" ) 
           {   
             $nodes = IndexNode::newIndexNodeArray( $levelStr ) ;
             if ( $nodes == null ) throw new Exception( $ms->error007 ) ;  
             $msg = sendNodes( $encoding, $nodes, $nodeId == 0, $user, $groups ) ;
           } else $msg = "" ;
      }
   $msg = "++OK " . $msg ;
 } catch ( Exception $e ) 
 {
   $msg = $e->getMessage() ;  
   if ( !(strpos( $msg, "-Failed" ) > 0) ) 
            $msg = "  --Failed: " . $msg . "\n" ;
   echo $msg ;
   var_dump( $e->getTrace() ) ;
   $failed = true ;
 }
  if ( !$failed ) echo( $msg ) ;
?>
