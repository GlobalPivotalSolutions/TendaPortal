  var MSIE = navigator.userAgent.indexOf('MSIE') != -1 ? true : false ;
  var hasInnerText ; 

  var eventTarget = null ;
  var subjList = null ;
  var postList = null ;
  var trList = null ;
  var dateList = null ;
 
 // Default values. Have effect only if the cookie does not exist
  var standard = "y" ;
  var advanced = "y" ;
  var precision = "y" ;
  var hits = "10" ;
  var columns = "4" ;
  var theme = "blue" ;
  var site = "all" ;
  
  var savedQueries = null ;
  var firstVisit = 1 ;
  var options = null ;
  var doNotApplyOptions = false ;
  var facetCheckboxes ;
  var facetLabels ;
  var facetQueries ;
  var checks ;
  var baseURL ;


//==============================================================================
//            Does whatever has to be done after the page is loaded
function initPage() //==========================================================
{
  cookie2options() ;
  // apply options
  if ( !doNotApplyOptions ) applyOptions() ;
  prepareFacets() ;
}


//==============================================================================
//                Forms facetCheckoxes and facetQueries arrays
function prepareFacets() //=====================================================
{
  baseURL = location.href ;
  var divs = document.getElementsByTagName( "div" ) ;
  var labels = document.getElementsByTagName( "label" ) ;

  facetCheckboxes = [] ;
  facetQueries = [] ;
  facetLabels = [] ;
  checks = [] ;
  for ( var j = 0  ; j < divs.length ; j++ )
    for ( var i = 0 ; i < divs[ j ].childNodes.length ; i++ )
     {
       var el = divs[ j ].childNodes[ i ] ;
       if ( !el.tagName || el.tagName.toLowerCase() != "input" ) continue ;
       if ( el.type.toLowerCase() != "hidden" ) // this is a checkbox
         { 
           facetCheckboxes.push( el ) ;            
           if ( el.checked ) checks.push( true ) ; else checks.push( false ) ;
           for ( var k = 0 ; k < labels.length ; k++ )
            { 
              var la = labels[ k ] ;
              if ( la.htmlFor == el.id )
                   { facetLabels.push( la ) ; break ; }
            }
              
           if ( facetCheckboxes.length != facetLabels.length ) 
             alert( "Label for checlbox " + el.id + " must be provided for faceting to work." ) ;
         }
            else facetQueries.push(  el.value ) ;                              
     }
  for ( var i = 0 ; i < facetCheckboxes.length ; i++ )
    {
      facetCheckboxes[ i ].onclick = facetClick ;
      facetCheckboxes[ i ].onmouseover = changeClassOv ;
      facetCheckboxes[ i ].onmouseout = changeClassOu ;
      facetCheckboxes[ i ].onfocus = changeClassOv ;
      facetCheckboxes[ i ].onblur = changeClassOu ;
    }
}


//==============================================================================
// Hilight label of the <box, label> pair when the checkbox is getting attention
function changeClassOv() //=====================================================
{
    var el = null ;
    for ( var i = 0 ; i < facetCheckboxes.length ; i++ )
       if ( facetCheckboxes[ i ] == this ) { el = facetLabels[ i ] ; break ; }
    if ( el ) el.className = 'facetfocus' ;
}


//==============================================================================
//                Make label of the <box, label> pair normal
function changeClassOu() //=====================================================
{
    var el = null ;
    for ( var i = 0 ; i < facetCheckboxes.length ; i++ )
       if ( facetCheckboxes[ i ] == this ) { el = facetLabels[ i ] ; break ; }
    if ( el ) el.className = 'facet' ;
}


//==============================================================================
//                    Process clicking on a facet checkbox
function facetClick() //========================================================
{
  retrieve( baseURL, this ) ; scroll( 0, 0 ) ; 
}


//==============================================================================
//                    Process clicking on a facet link
function linkClick( e ) //=====================================================
{
  var box ;
  if ( !e ) var e = window.event ;
  if ( e.target ) box = e.target ;
    else if ( e.srcElement ) box = e.srcElement ;
  if ( box.nodeType == 3 ) // defeat Safari bug
		box = box.parentNode ;

  var u = box.href ;
  var start = getURLParam( u, "start" ) ;
  u = baseURL + "&start=" + start ;
  retrieve( u, this ) ;
  scroll( 0, 0 ) ; 
  return false ;
}

function retrieve( url, box ) 
{

  var queries = {} ;

  for ( var i = 0  ; i < facetCheckboxes.length ; i++ )
  {
    var el = facetCheckboxes[ i ] ;
    if ( el == box ) checks[ i ] = !checks[ i ] ;
    if ( checks[ i ] )
       {
         ss = facetQueries[ i ].split( ":", 2 ) ;
         if ( queries[ ss[ 0 ] ] ) queries[ ss[ 0 ] ] += " OR " + facetQueries[ i ] ;
                             else  queries[ ss[ 0 ] ] = facetQueries[ i ] ;
       }
  }
  
  var query = null ;
  for( q in queries )
  {
    if ( queries.hasOwnProperty( q ) )
     if ( query ) query += " AND (" + queries[ q ] + ")" ;
              else query =  "(" + queries[ q ] + ")" ; 
  }
  if ( query ) query = "&fq=" + encodeURIComponent( query ) ; else query = "" ;

  var request = new sack() ;
  request.requestFile = url + "&ar_format=resultsBody" + query ;
                                                
  request.onCompletion = function()
   { 
     document.getElementById( "results" ).innerHTML = request.response ;
   } ;
  request.runAJAX() ;  
}


//==============================================================================
//                               Translates cookie
function cookie2options() //====================================================
{
 var nameEQ = "options=" ;
 var cc = document.cookie ;
 var body = null ;
 if ( cc != null )
  {
    var ca = cc.split( ';' ) ;
    for ( var i=0 ; i < ca.length ; i++ )
     {
       var c = ca[ i ];
       while ( c.charAt( 0 ) == ' ' ) c = c.substring( 1, c.length ) ;
       if ( c.indexOf( nameEQ ) == 0 )
          { body = c.substring( nameEQ.length, c.length ) ; break ; }
     }
    if ( body != null ) 
    body = unescape( body ) ;
  }
 if ( body == null ) // there was no cookie, create one
    {
      doNotApplyOptions = true ;
      body = "standard=" + standard + "\tadvanced=" + advanced + "\tprecision=" 
                + precision + "\thits=" + hits + "\ttheme=" + theme + 
                "\tsite=" + site + "\tcolumns=" + columns ;
      setCookie( body ) ;
    }
 options = new Array() ;
 var sp = body.split( "\t" ) ;
 for ( var i = 0 ; i < sp.length ; i++ )
  {
    var pair = parsePair( sp[ i ] ) ;
    if ( pair ) options[ pair.key ] = pair.value ;
  } 
}


//==============================================================================
//                               Packs options to cookie
function options2cookie() //====================================================
{
  var cookie = "", t = "" ;
  for ( var key in options )
  {
    cookie += t + key + "=" + options[ key ] ;
    t = "\t" ;
  }
  return cookie ;  
}


//==============================================================================
//            
function applyOptions() //======================================================
{
  switchCSS( options[ "theme" ] ) ;
  
  var standardPanel = document.getElementById( "standardPanel" ) ;
  if ( standardPanel == null ) return ; // this is not a search form page
  standardPanel.style.display  =
      options[ "standard" ] == null || options[ "standard" ] == "y" ? "" : "none" ;
  var advancedPanel = document.getElementById( "advancedPanel" ) ;
  advancedPanel.style.display  = options[ "advanced" ] == "y" ? "" : "none" ;

  var base = window.location.href ;
  var q = base.indexOf( '?' ) ;
  if ( q > 0 ) base = base.substring( 0, q ) ; 
  
  
  // set default site
  // setSelect( $( "search1" ).elements[ "ar_site" ], options[ "site" ] ) ;
  setSelect( $( "search2" ).elements[ "ar_site" ], options[ "site" ] ) ;
  setField( "rows", options[ "hits" ] ) ;
  
  // display saved queries
  if ( !options[ 'query0' ] ) return ; // else have at least 1 query
  var td = document.getElementById( "savedQueriesPlace" ) ;
  if ( td.firstChild ) td.removeChild( td.firstChild ) ;
  td.innerHTML = '<fieldset><legend onclick="togglePanel( \'savedQueries\' );">Saved Queries</legend>'
               + '<div id="savedQueriesPanel"><table width="100%" id="savedQueriesTable"></table></div></fileset>' ;
  var table = document.getElementById( "savedQueriesTable" ) ;
  
  // form a table with 'columns' number of columns
  var count = 0, rowCount = 0 ;
  for( var count = 0 ; options[ "query" + count ] ; ) 
  {
    var tr = table.insertRow( rowCount ) ;
    rowCount++ ;
    var html = "" ;
    for ( var i = 0 ; i < columns ; i++ ) 
      {
//        var td = tr.insertCell( i ) ;
        var td = document.createElement( "TD" ) ;
        if ( options[ "query" + count ] )
          { 
            var pair = parsePair( options[ "query" + count ] ) ;
            td.innerHTML = "<a href=\"\" class=\"queryLink\">" + pair.key + "</a>\n" ;
            td.firstChild.setAttribute( "href", base + pair.value ) ;
            count++ ;
          } else td.innerHTML = "&nbsp;\n" ;
        tr.appendChild( td ) ;
      }
  }
}


//==============================================================================
//                        Sets options cookie to the document
function setCookie( cookie ) //=================================================
{
  var date = new Date();
  // first, clean the doc if there is an old cookie
  date.setTime( date.getTime() - ( 365 * 24 * 60 * 60 * 1000 ) );
  document.cookie = "options=; expires=" + date.toGMTString() ; 
  date.setTime( date.getTime() + ( 2*365 * 24 * 60 * 60 * 1000 ) );
  var expires = "; expires="+date.toGMTString() ;
                 
  document.cookie = "options="+ escape( cookie ) + expires +"; path=/";
  return document.cookie ;
}


//==============================================================================
//                     Splits a string into a key, value pair
function parsePair( str ) //====================================================
{
  var key, value, eq ;
  if ( str == null || str == "" ) return { key : "", value : "" } ;
  eq = str.indexOf( '=' ) ;
  if ( eq < 0 ) return { key : str, value : "" } ;
  key = str.substring( 0, eq ) ;
  if ( eq == str.length - 1 ) return { key : key, value : "" } ;
  value = str.substring( eq + 1 ) ;
  return { key : key, value : value } ;
}

String.prototype.trim = function()
{ return ( this.replace( /^[\s\xA0]+/, "" ).replace( /[\s\xA0]+$/, "" ) ) ; }

String.prototype.startsWith = function( str )
{ return ( this.match("^"+str) == str ) ; }

//==============================================================================
//                        Shows options panel
function showOptions() //=======================================================
{
  // if options are visible, hide them, else show them
  if ( !togglePanel( "options" ) ) return ; // hid  

  setSelectById( "hits", options[ "hits" ] ) ;
  setSelectById( "columns", options[ "columns" ] ) ;
  setSelectById( "theme", options[ "theme" ] ) ;
  document.getElementById( "defaultSite" ).value = options[ "site" ] ;
  unpackSavedQueries() ;
}


//==============================================================================
//                Unpacks all savedQueries into the savedQueries table
function unpackSavedQueries() //==================================================
{
  var table = document.getElementById( "savedQueriesOptions" ) ;
  for ( ; table.rows.length != 0 ; )
  {
    pNode( table.rows[ 0 ] ).removeChild( table.rows[ 0 ] ) ;
  } 

  for ( var i = 0 ; ; i++ )
  {
      var key = "query" + i ;
      if ( !options[ key ] ) break ;
      var pair = parsePair( options[ key ] ) ;
      var tr = table.insertRow( i ) ;
      var td1 = document.createElement( "TD" ) ;
      td1.setAttribute( "class", "queryName" ) ;
      td1.innerHTML = '<input type="text" size=20 value="">' ;
      td1.firstChild.setAttribute( "value", pair.key ) ;
      var td2 = document.createElement( "TD" ) ;
      td2.setAttribute( "class", "queryString" ) ;
      td2.innerHTML = '<input type="text" size=80 value="">' ;
      td2.firstChild.setAttribute( "value", pair.value ) ;
      var td3 = document.createElement( "TD" ) ;
      td3.setAttribute( "class", "queryButton" ) ;
      td3.innerHTML = '<button onclick="deleteSavedQuery( event );">X</button>' ;
      tr.appendChild( td1 ) ;
      tr.appendChild( td2 ) ;
      tr.appendChild( td3 ) ;
  }
}


//==============================================================================
//        Deletes or cleans the current row in the table of savedQueries 
function deleteSavedQuery( e ) //===============================================
{
  if ( document.all ) e = event ;
  var button = e.target != null ? e.target : e.srcElement ;

  // Check: if this is the first row then clean it, else delete it
  var tr = pNode( pNode( button ) ) ;
  pNode( tr ).removeChild( tr ) ;
}


//==============================================================================
//                 Switches CSS when selected theme is changed
function onThemeChanged( select ) //============================================
{
  var theme = select.options[ select.selectedIndex ].value ;
  switchCSS( theme ) ;
}


//==============================================================================
//                 Hides options panel and restores the theme
function onOptionsCancelClick() //==============================================
{
  togglePanel( "options" ) ;
  switchCSS( options[ "theme" ] ) ;
}


//==============================================================================
//            Reads values from the options form to cookie
function onOptionsOKClick() //==================================================
{
  var s = null ;
  
  options[ "standard" ] = document.getElementById( "standardPanel" ).style.display != "none" ? "y" : "no" ;
  options[ "advanced" ] = document.getElementById( "advancedPanel" ).style.display != "none" ? "y" : "no" ;
  s = document.getElementById( "hits" ) ;
  options[ "hits" ] = s.options[ s.selectedIndex ].value ;
  s = document.getElementById( "theme" ) ;
  options[ "theme" ] = s.options[ s.selectedIndex ].value ;
  s = document.getElementById( "columns" ) ;
  options[ "columns" ] = s.options[ s.selectedIndex ].value ;
  s = document.getElementById( "site" ) ;
  options[ "site" ] = document.getElementById( "defaultSite" ).value ;
  var table = document.getElementById( "savedQueriesOptions" ) ;
  
  // clean options first 
  for ( var i = 0 ; ; i++ )
  { 
    if ( !options[ "query" + i ] ) break ;
    delete options[ "query" + i ] ;
  } 

  var queryFound, count = 0 ;
  if ( table && table.rows.length > 0 )
   do { 
        var firstName = null, firstRow = null, firstQuery ;
        queryFound = false ;
        for ( var i = 0 ; i < table.rows.length ; i++ )
         { 
           var tr = table.rows[ i ] ;
           var name = getChild( tr.cells[ 0 ], "INPUT", null, null ).value ;
           var query = getChild( tr.cells[ 1 ], "INPUT", null, null ).value ;
           if ( name != "" && query != "" )
              {
               if ( firstName == null || firstName.toLowerCase() > name.toLowerCase() )
                  { firstName = name ; firstRow = tr ; queryFound = true ; firstQuery = query ; } 
              }
         }
        if ( queryFound )
         {
           options[ "query" + count ] = firstName + "=" + firstQuery ; 
           count++ ;
           pNode( firstRow ).removeChild( firstRow ) ;
         }  
      } while( queryFound && table.rows.length > 0 ) ;
  var cookie = options2cookie() ;
  setCookie( cookie ) ;
  applyOptions() ;
  togglePanel( "options" ) ;
}

//==============================================================================
//                   Saves a query. Called from the results page.
function saveQuery() //=========================================================
{
  var queryName ;
  do { 
       queryName = prompt( query_prompt ) ;
     } while( queryName == "" ||
              ( queryName != null && queryName.indexOf( '=' ) >= 0 ) ) ;
  if ( !queryName ) return ;
  var i = 0 ;
  for ( ; ; i++ )
   if ( options[ "query" + i ] == null ) break ;
  options[ "query" + i ] = queryName + "=" + window.location.search ;  
  var cookie = options2cookie() ;
  setCookie( cookie ) ;
}


//==============================================================================
//              Sets a select with the given id to given value
function setSelectById( id, value ) //==========================================
{
  var sel = document.getElementById( id ) ;
  if ( sel ) setSelect( sel, value ) ;
}

//==============================================================================
//              Sets the given select to given value
function setSelect( select, value ) //==========================================
{
  for ( var i = 0 ; i < select.options.length ; i++ ) 
   {
     if ( select.options[i].value == value ) select.options[i].selected = true;
//        else select.options[ i ].selected = false ; 
   }
}


//==============================================================================
//         Extracts argument with the given name from the query string
function getArg( name ) //======================================================
{  
 name = name.replace( /[\[]/,"\\\[" ).replace( /[\]]/, "\\\]" ) ;
 var regexS = "[\\?&]"+name+"=([^&#]*)" ;
 var regex = new RegExp( regexS ) ;
 var results = regex.exec( window.location.href ) ;
 if ( results == null ) return "" ;
   else return results[ 1 ] ;
}


//==============================================================================
//         Returns url parameter value 
function getURLParam( strHref, strParamName ) //================================
{
  var strReturn = "" ;
  if ( strHref == null ) strHref = window.location.href ;
  if ( strHref.indexOf("?") > -1 )
  {
    var strQueryString = strHref.substr(strHref.indexOf("?")).toLowerCase();
    var aQueryString = strQueryString.split("&");
    for ( var iParam = 0 ; iParam < aQueryString.length ; iParam++ )
    {
      if ( aQueryString[iParam].indexOf(strParamName.toLowerCase() + "=") > -1 )
      {
        var aParam = aQueryString[iParam].split("=");
        strReturn = aParam[1];
        break;
      }
    }
  }
  strReturn = strReturn.replace( "+", " " ) ;
  return unescape(strReturn);
} 

//==============================================================================
//            replacement for parentNode & parentElement
function pNode( aa ) //=========================================================
{
  if ( MSIE ) return aa.parentElement ; else return aa.parentNode ;
}

//==============================================================================
//              Shows or hides search forms, returns true if shows
function togglePanel( name ) //=================================================
{  
  var tab = document.getElementById( name + "Panel" ) ;
  var y = "n" ;
  var d = "none" ; 
  if ( tab.style.display == "none" ) 
     { y = "y" ; d = "" ; } else { y = "n" ; d = "none" ; }
  tab.style.display = d ; 
  options[ name ] = y ;
  var cookie = options2cookie() ;
  setCookie( cookie ) ;
  return d == "" ; 
}


//==============================================================================
//                            Sets preferred site select values
function setField( name, value ) //=============================================
{
  if ( value === undefined ) return ;
  var e = document.getElementsByName( name ) ;
  for( var i = 0 ; i < e.length ; i++ )
      if ( e[ i ].tagName == "SELECT" ) setSelect( e[ i ], value ) ;
                                           else e[i].value = value ;
}


//==============================================================================
//                            Sets item value
function setFieldItem( item, value ) //=========================================
{
  if ( value === undefined ) return ;
  if ( item.tagName == "SELECT" ) setSelect( item, value ) ;
                                     else item.value = value ;
}


//==============================================================================
//                           Generates a CSS link in document
function switchCSS( theme ) //==================================================
{
  var el = document.getElementsByTagName( "link" ) ;
  for( var i = 0 ; i < el.length ; i++ )
  {
    var a = el[ i ] ;
    if( a.getAttribute( "rel" ).indexOf( "style" ) != -1
                                                  && a.getAttribute( "title" ) )
        a.disabled = a.getAttribute( "title" ) != theme ;
  }
}


//==============================================================================
// Login processing
//==============================================================================
function login()
{
  var request = new sack() ;
  request.requestFile = ajaxAddress + "?ar_action=getLogin" ;
  request.onCompletion = function()
            { 
              var str = request.response ;
              if ( findError( str ) ) return ; 
              else str = str.substr( str.indexOf( "++OK" ) + 5 ) ;
              messageObj.setHtmlContent( str ) ;
              messageObj.setCssClassMessageBox( false ) ;
              var vsize = 150 ; 
              if ( str.indexOf( "fsite" ) > 0 ) vsize += 25 ;
              if ( str.indexOf( "frealm" ) > 0 ) vsize += 25 ;
              messageObj.setSize( 300, vsize ) ;
              messageObj.setShadowDivVisible( true ) ; // Enable shadow for these boxes
              messageObj.display() ;
            } ;
  request.runAJAX() ;  
}

function logout()
{
  var request = new sack() ;
  request.requestFile = ajaxAddress + "?ar_action=logout" ;
  request.onCompletion = function()
	{ 
	  var str = request.response ;
	  if ( !findError( str ) ) // something wrong, show error message
	                          replaceLoginDiv() ; 
	} ;
  request.runAJAX() ;  
}
        
function onLoginOKClick()
{
  var str = packText( $( "ar_fname" ) ) ;
  str += packText( $( "ar_fpass" ) ) ;
  str += packText( $( "ar_frealm" ) ) ;
  str += packText( $( "ar_fsite" ) ) ;
  messageObj.close();
  
  var request = new sack() ;
  request.requestFile = ajaxAddress + "?ar_action=login" + str ;
  request.onCompletion = function()
	{ 
	  var str = request.response ;
	  if ( str.indexOf( "--Failed" ) >= 0 ) // something is wrong
                       { login() ; return ; } // display login form again
	     else replaceLoginDiv() ; 
	} ;
  request.runAJAX() ;  
}

function onLoginCloseClick()
{
  messageObj.close();
}

function replaceLoginDiv()
{
  var divIn = $( "login" ) ;
  var divOut = $( "logout" ) ;
  if ( divIn.style.display != 'none' ) 
           { divIn.style.display = 'none' ; divOut.style.display = 'block' ; }
      else { divIn.style.display = 'block' ; divOut.style.display = 'none' ; }
}

function findError( str )
{
  if ( str.indexOf( "--Failed " ) >= 0 )
  {
	var i = str.indexOf( "--Failed " ) ;
	var j = str.indexOf( "\n", i ) ;
	var msg = str.substring( i + 8, j ) ;
	alert( msg ) ;
	return true ;
  } else return false ;
	
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
//                        Emulates click on a link          
function navigate( url ) //=====================================================
{
  if ( !MSIE ) { window.location = url ; return ; }
  var div = document.getElementById( "goto" ) ; 
  if ( div == null ) 
     { div = document.createElement( "div" ) ;
       div.id = "goto" ;
     }
  div.innerHTML = "<a href=\"" + url + "\"></a>" ;
  document.body.appendChild( div ) ;
  var a = div.firstChild ;
  a.click() ; 
}

	// packs values of a text input object
	function packText( obj )
	{
	  var str = "" ;
	  if ( obj != null && obj.value != null && obj.value != "" ) 
	    str = "&" + encodeURIComponent( obj.name ) + "=" + encodeURIComponent( obj.value ) ;
	  return str ;
	}


	function $( str )
	{
	  var aa = document.getElementById( str ) ;
	  return aa ;
	}
