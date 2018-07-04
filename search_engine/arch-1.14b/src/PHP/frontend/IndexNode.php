<?php

require_once( "localname.php" ) ;
require_once( local( "utils.php" ) ) ;

class IndexNode
{
 // A PHP translation of parts of au.csiro.atnf.arch.sql.IndexNode   
    
 var       $id ; // node id
 var     $name ; // node name: a directory or file name
 var    $label ; // text shown in the directory, normally, the name
 var    $title ; // html title
 var     $path ; // path from the site root to this node
 var     $type ; // 'd' - directory, 'f' - file (terminal node)
 var   $access ; // 'i' - inherited from parent node, 's' - standard
 var   $groupr ; // names of groups having r/o access 
 var   $groupw ; // names of groups having r/w access 
 var    $userr ; // names of users having r/o access 
 var    $userw ; // names of users having r/w access 
 var   $owners ; // names of users having admin access
 var $parentId ; // id of the parent node
 var   $nextId ; // id of the next node on the level
 var  $fetched ; // reserved
 var   $errors ; // reserved
 var   $cached ; // reserved
 var   $weight ; // current file weight
 var    $score ; // file hit score
 var   $status ; // reserved
 
 var     $site ; // r/o name of site this node belongs to
 var     $base ; // base url
 var     $read ; // set to true if this node is visible to user
 var    $write ; // set to true if user can change the node
 var    $admin ; // set to true if user has administrator access
 var    $isSet ; // set to true if read/write/admin fields are set
 var  $grouprA ; // names of groups having r/o access 
 var  $groupwA ; // names of groups having r/w access 
 var   $userrA ; // names of users having r/o access 
 var   $userwA ; // names of users having r/w access 
 var  $ownersA ; // names of users having admin access
 
 /**
 * Default constructor 
 */
 function IndexNode() { $this->weight = -1 ; } 
     
 /**
  * IndexNode factory. Sets default values as for a site root node.
  * 
  */
 static function newRootIndexNode()
 {
  $n = new IndexNode() ;
  $n->id = 0 ;
  $n->name = "" ;
  $n->label = "/" ;
  $n->title = "Root node" ;
  $n->path = "" ;
  $n->type = "d" ; // a directory
  $n->access = "s" ; // standard
  $n->groupr = "public" ; // open to public by default
  $n->groupw = null ;
  $n->userr = "guest" ;
  $n->userw = null ;
  $n->owners = "admin" ;
  $n->parentId = 0 ;
  $n->nextId = 0 ;
  $n->fetched = 0 ;
  $n->errors = 0 ;
  $n->cached = 0 ;
  $n->weight = -1.0 ;
  $n->score = 0 ;
  $n->status = "l" ;
  return $n ;
 }
 
  /**
  * Serialises to String. Fields are separated by '>'.
  * 
  * @return String result of serialization
  */
 function toString()
 {
  $buf = "" ;
  $buf .= $this->id ; 
  $buf .= '>'.Utils::htmlEncode( $this->name ) ; 
  $buf .= '>'.Utils::htmlEncode( $this->label ) ; 
  $buf .= '>'.Utils::htmlEncode( $this->title ) ; 
  $buf .= '>'.Utils::htmlEncode( $this->path ) ; 
  $buf .= '>'.$this->type ; 
  $buf .= '>'.$this->access ; 
  $buf .= '>' ;
  if ( !$this->isSet || $this->write || $this->admin )
   {
     $buf .= Utils::htmlEncode( $this->groupr ).'>'.Utils.htmlEncode( $this->groupw ).'>' ;
     $buf .= Utils::htmlEncode( $this->userr ).'>'.Utils.htmlEncode( $this->userw ).'>' ;
     $buf .= Utils::htmlEncode( $this->owners ) ;
   } else $buf .= '->->->->-' ;
  $buf .= '>'.$this->parentId ;
  $buf .= '>'.$this->nextId ;
  $buf .= '>'.$this->fetched ;
  $buf .= '>'.$this->errors ; 
  $buf .= '>'.$this->cached ;
  $buf .= '>'.$this->weight ;
  $buf .= '>'.$this->score ;
  $buf .= '>'.$this->status ;
  $buf .= '>'.Utils::htmlEncode( $this->site ) ; 
  $buf .= '>'.Utils::htmlEncode( $this->base ) ; 
  $buf .= '>'.( $this->read ? "t" : "f" ) ;
  $buf .= '>'.( $this->write ? "t" : "f" ) ;
  $buf .= '>'.( $this->admin ? "t" : "f" ) ;
  $buf .= '>'.( $this->isSet ? "t" : "f" ) ;  
  
  return $buf ; 
 }
 
 /**
  * Deserialises from String.
  * 
  * @param str - serialised object
  * 
  * @return IndexNode object
  */
 static function newIndexNode( $str )
 {
  $n = IndexNode::newRootIndexNode() ;
  list
  ( $n->id, $n->name, $n->label, $n->title, $n->path, $n->type, $n->access,
    $n->groupr, $n->groupw, $n->userr, $n->userw, $n->owners, $n->parentId, 
    $n->nextId, $n->fetched, $n->errors, $n->cached, $n->weight, $n->score, 
    $n->status, $n->site, $n->base, $n->read, $n->write, $n->admin, $n->isSet
  ) = explode( ">", $str ) ;
  $n->name = trim( Utils::htmlDecode( $n->name ) ) ; 
  $n->label = trim( Utils::htmlDecode( $n->label ) ) ; 
  if ( $n->label == "" ) $n->label = null ; 
  $n->title = trim( Utils::htmlDecode( $n->title ) ) ; 
  if ( $n->title == "" ) $n->title = null ; 
  $n->path = trim( Utils::htmlDecode( $n->path ) ) ; 
  $n->access = trim( $n->access ) ; 
  $n->groupr = trim( Utils::htmlDecode( $n->groupr ) ) ; 
  if ( $n->groupr == "" ) $n->groupr = null ; 
  $n->groupw = trim( Utils::htmlDecode( $n->groupw ) ) ; 
  if ( $n->groupw == "" ) $n->groupw = null ; 
  $n->userr = trim( Utils::htmlDecode( $n->userr ) ) ; 
  if ( $n->userr == "" ) $n->userr = null ; 
  $n->userw = trim( Utils::htmlDecode( $n->userw ) ) ; 
  if ( $n->userw == "" ) $n->userw = null ; 
  $n->owners = trim( Utils::htmlDecode( $n->owners ) ) ; 
  if ( $n->owners == "" ) $n->owners = null ; 
  $n->site = trim( Utils::htmlDecode( $n->site ) ) ; 
  $n->base = trim( Utils::htmlDecode( $n->base ) ) ; 
  $n->read = $n->read == 't' ;
  $n->write = $n->write == 't' ;
  $n->admin = $n->admin == 't' ;
  $n->isSet = $n->isSet == 't' ;  
  return $n ;
 }

 /**
  * Deserialises arry of IndexNode objects from String.
  * 
  * @param str - serialised array
  * 
  * @return array of IndexNode objects
  */
 static function newIndexNodeArray( $str )
 {
  $arr = explode( "<", $str ) ;
  $nodes = array() ;
  for ( $i = 0 ; $i < count( $arr ) ; $i++ )
   { 
     $nodes[ $i ] = IndexNode::newIndexNode( $arr[ $i ] ) ;
   }
  return $nodes ;
 }
 
 function setPermissions( $user, $groups0 )
 {
   $this->groups = explode( " ", $groups0 ) ;
   $this->grouprA = explode( " ", $this->groupr ) ;
   $this->groupwA = explode( " ", $this->groupw ) ;
   $this->userrA = explode( " ", $this->userr ) ;
   $this->userwA = explode( " ", $this->userw ) ;
   $this->ownersA = explode( " ", $this->owners ) ;
   $this->admin = Utils::isIn( $user, $ownersA ) ;
   $this->write = $this->admin || Utils::isIn( $user, $this->userwA ) || Utils::intersect( $this->groupwA, $groups ) ;
   $this->read = $this->admin || $this->write || Utils::isIn( $user, $this->userrA ) || Utils::intersect( $this->grouprA, $this->groups ) ;
 }
 
 function canWrite( $user, $groups0 )
 {
   if ( !$this->groupwA || !$this->userwA ) $this->setPermissions( $user, $groups0 ) ;
   return $this->write ;
 }
 
} 
?>
