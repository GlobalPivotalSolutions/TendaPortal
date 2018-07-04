  var MSIE = navigator.userAgent.indexOf('MSIE') != -1 ? true : false ;
  var hasInnerText ;

String.prototype.trim = function()
{ return ( this.replace( /^[\s\xA0]+/, "" ).replace( /[\s\xA0]+$/, "" ) ) ; }

String.prototype.startsWith = function( str )
{ return ( this.match("^"+str) == str ) ; }

//==============================================================================
//            replacement for parentNode & parentElement
function pNode( aa ) //=========================================================
{
  if ( MSIE ) return aa.parentElement ; else return aa.parentNode ;
}

//==============================================================================
//     returns first found child of the el with given tag, name or class
function getChild( el, tag, name, classN ) //===================================
{
  for ( var i=0 ; i < el.childNodes.length ; i++ )
  {
   var ch = el.childNodes[ i ] ;
   if ( tag != null && tag != ch.tagName ||
        name != null && name != ch.name ||
        classN != null && ch.className.indexOf( classN ) < 0 ) continue ;
   return ch ; 
  }  
   return null ;
}

//==============================================================================
//     change relevancy indicator on the document and identical documents
function relevant( el ) //======================================================
{
  var count = 0 ;
  
  while( el && el.tagName != "TD" ) el = pNode( el ) ;
  if ( !el ) return ; // ignore, should not happen
  
  var num = el.id.substr( 2 ) ;
  var cb = document.getElementById( "cb" + num ) ;   
  var a = document.getElementById( "a" + num ) ;   
  var url = a.href ;
  
  var state = el.className != "relevant" ;
  setRelevant( pNode( a ), state, num ) ;
  
  for ( count = 0 ; count < 20 ; count++ )
  {
    if ( count == num ) continue ; 
    var el =  document.getElementById( "a" + count ) ;
    if ( el == null ) break ;
    if ( equal( el.href, a.href ) ) setRelevant( pNode( el ), state, count ) ;    
  }
  return false ;
}


//==============================================================================
//                   change relevancy state of the document
function setRelevant( el, state, num ) //=======================================
{  
  var cb = getChild( el , "INPUT", null, null ) ; 
  if ( !state ) { el.className = "restitle" ; cb.value = "" ; }
     else { el.className = "relevant" ;
            cb.value = "y" ;
//            var radio = document.getElementById( "rate" + num ) ;   
//            radio.checked = true ;
          }
          
}


//==============================================================================
//                               compare 2 urls
function equal( url1, url2 ) //=================================================
{
   if ( url1 == url2 ) return true ;
   var len1 = url1.length ;
   var len2 = url2.length ;
   if ( len1 < len2 ) { if ( url2.substring( 0, len1 ) != url1 ) return false ; }
                 else { if ( url1.substring( 0, len2 ) != url2 ) return false ; }
   // else one is part of another, may refer to an index/home page   
   url1 = normalise( url1 ) ; 
   url2 = normalise( url2 ) ;
   return url1 == url2 ;  
}
 
//==============================================================================
//                       Home/index page normalisation
function normalise( url ) //====================================================
{
  if ( homePages == null ) 
      { 
        homePages = home.split( " " ) ;
        for( var i = 0 ; i < homePages.length ; i++ ) 
         { 
           if ( homePages[ i ] == "" ) continue ;
           homePages[ i ] = "/" + homePages[ i ].trim() ;
//           homePages[ i ] = "/" + homePages[ i ] ;
         }
      }
  for( var j = 0 ; j < homePages.length ; j++ ) 
   {
     if ( homePages[ j ] == "" ) continue ;  
     if ( endsWith( url, homePages[ j ] ) )
      { url = url.substring( 0, url.length - homePages[ j ].length ) ; }
   }
  if ( endsWith( url, "/" ) )
        url = url.substring( 0, url.length - 1 ) ;
  return url ;
 }
 
 function endsWith( str, end )
 {
   var pos = str.lastIndexOf( end ) ;
   var ppp = ( str.length - end.length ) ;
   return pos === ppp ;
 }   

