/**
 * Author: Arkadi Kosmynin
 * Date: 5/03/12
 * Time: 2:40 PM
 * To change this template use File | Settings | File Templates.
 */
define([ "dojo/_base/declare", "dojo/_base/lang", "dojo/store/JsonRest", "dojo/query", "dojo/_base/array",
         "dojo/on", "dijit/Dialog", "dojo/_base/lang", "dojox/validate/web", "dijit/form/_FormWidget",
         "dojo/NodeList-traverse", "dijit/layout/TabContainer", "dojox/timing",
         "dijit/layout/ContentPane", "dijit/InlineEditBox", "dijit/form/Textarea", "dijit/form/Select",
         "dijit/form/CheckBox", "dijit/form/NumberSpinner" ],
 function( declare, lang, JsonRest, query, array, on, dialog, lang, validate, _FormWidget, list )
 {
   var controller = null ;
   var tree = null ;
   var store = null ;
   return declare( null,
   {
       model: {},           // array of configuration objects, with site names as keys
       changes: null,       // names of changed sites
       currentSite: "",     // current site name
       currentArea: "",     // current area name
       siteCfg: null,       // configuration object of the current site
       rootCfg: null,       // root configuration object
       nutchCfg: null,      // nutch-site configuration object
       areaTab: null,       // a place holder for the area tab
       crawlingTab: null,   // a place holder for the crawling tab
       siteFilled: false,   // is set to false before filling and to true when filling starts
       isRoot: true,        // is authenticated as admin user
       isRootConfig: false,
         // These params are visible only in the root configuration in sets that are shared with site params.
         // Do not include in this list params that are located in sets that are hidden entirely. Place them
         // in otherParams instead.
       rootOnlyParams: [ "solr.url", "temp.dir", "parallel.indexing", "auth.passwords.file", "auth.groups.file",
                         "log.length", "history.size", "max.hits.day", "max.hits.ip.day", "max.score",
                         "allowed.ip.addresses", "blocked.ip.addresses", "admin.ip.addresses", "allowed.users",
                         "allowed.groups", "allowed.sites", "allowed.areas", "default.users", "default.groups",
                         "default.sites", "default.areas", "remove.duplicates", "max.url.cache", "depth.loglinks",
                         "depth", "threads", "access.control.enabled", "watch.mode" ],

       nutchParams: [ "http.agent.name", "http.agent.description", "http.agent.url",
                      "http.agent.email", "http.agent.version", "http.robots.agents", "http.agent.host" ],

       siteOnlyParams: [ "url", "permissions", "sitemap.url", "logs" ],

       areaOnlyParams: [ "enabled", "area", "interval", "area.depth", "area.max.urls", "area.threads", "root",
                         "include", "exclude", "file.bookmarks", "usersread.bookmarks", "groupsread.bookmarks" ],

       otherParams: [ "database", "target.db", "db.driver", "mail.level", "mail.transport.protocol", "mail.host",
                      "mail.user", "mail.pass", "mail.subject", "mail.recipient", "log.format", "ignore.in.logs",
                      "frontend.profile", "ip.filter", "max.hits.norm", "hits.threshold", "capture.interval",
                      "max.ip.cache", "admin.user", "index.file.names", "max.urls", "delete.logs",
                      "facet.areas", "facet.sites", "facet.formats",
                      "scan.file.types", "scan.src.file.types", "scan.content.types", "scan.src.content.types",
                      "scan.script.edges", "scan.ignore.bits", "scan.ignore.scripts", "scan.ignore.links",
                      "scan.alert", "scan.alert.level", "scan.source.access.url", "scan.index.file.type",
                      "scan.min.script.size", "scan.normalize.urls", "scan.report.link.changes",
                      "scan.report.new.forms","scan.report.new.scripts","scan.report.new.pages",
                      "scan.report.changed.forms","scan.report.changed.scripts","scan.report.changed.pages",
                      "prune.file.types", "prune.content.types" ],

       rootOnlySets: [ "ip.filter.parameters", "ip.based.access.control" ],
       siteOnlySets: [ "document.access.permissions" ],
       allParams: null,       // concatenated otherParams, rootOnlyParams, siteOnlyParams
       allPlusNutch: null,    // concatenated allParams and nutchParams
       allPlusArea: null,     // concatenated allParams and areaParams
       validationEnabled: false,
       path: null, // Path to preserve when refreshing the tree

       constructor: function( options )
       {
         lang.mixin( this, options || {} ) ;
         controller = this ;
         this.allParams = this.otherParams.concat( this.siteOnlyParams, this.rootOnlyParams ) ;
         this.allPlusArea = this.allParams.concat( this.areaOnlyParams ) ;
         this.allPlusNutch = this.allParams.concat( this.nutchParams ) ;
         this.loadRoot() ;
       },

       ready: function()
       {
         var allParams = controller.allPlusArea.concat( controller.nutchParams ) ;
         array.forEach( allParams, function( entry )
          {
            var node = dijit.byId( entry ) ;
            if ( node ) on( node, "change", function( event ) { controller.onChange( event, node ) ; } ) ;
            if ( node ) on( node, "focus", function( event ) { controller.onFocus( event ) ; } ) ;
          } ) ;
         var t = new dojox.timing.Timer( 1000 ) ;
         t.onTick = function()
         {
           console.info("One second elapsed");
           if ( !controller.rootChildrenLoaded ) return ;
           t.stop() ;
           if ( controller.mySite )
                       controller.startWizard( "root" ) ;
         }
         t.onStart = function() { console.info("Starting timer") ; }
         t.start();
       },


       onChange: function( event, target )
       {
         if ( !controller.validationEnabled ) return ;
         if ( !controller.changes )
         { controller.changes = [] ; dojo.byId( "unsaved" ).style.display = "" ; }
         if ( array.indexOf( controller.changes, controller.currentSite ) == -1 )
             controller.changes.push( controller.currentSite ) ;
         controller.validate( event, target ) ;
       },

       onFocus: function( event )
       {
         controller.validationEnabled = true ;
       },

       treeClick: function( item )
       {
         // If this is an after refresh event
         if ( controller.path != null )
         { controller.navigationTree.set( "path", controller.path ) ;
           controller.path = null ;
           return ;
         }
         // Fields will be programatically changed, block validation
         // onChange events should not fire on programmatic changes, but they do!
         controller.validationEnabled = false ;
         if ( !item ) return ;
         var arr = item.id.split( "_", 3 ) ;
         if ( arr.length == 1 ) controller.fillSite( "root", null ) ;
           else if ( arr.length == 2 ) controller.loadSite( arr[ 1 ], null ) ;
             else { // clicked on an area node => the site is there already
                    controller.openArea( arr[ 1 ], arr[ 2 ] ) ;
                    var areas = dijit.byId( "areas" ) ;
                    dijit.byId( "tabs" ).selectChild( areas ) ;
                  }
         return true ;
       },

       exit: function()
       {
         if ( controller.changes )
         {
           controller.prompt( "Unsaved_Changes", "YesNoCancel", function( response )
           {
             dijit.byId( "promptDialog" ).hide() ;
             if ( response.target.id.indexOf( "Yes" ) ) { if ( !controller.saveChanges() ) return ; }
               else if ( response.target.id.indexOf( "No" ) ) window.close() ;
                  else return ; // cancel - do nothing
           }) ;
         } else  window.close() ;
       },

       prompt: function( message, buttons, funct )
       {
         message = controller.msg( message ) ;
         dojo.byId( "promptMessage" ).innerHTML = message ;
         controller.setupButton( buttons, "Yes", funct ) ;
         controller.setupButton( buttons, "No", funct ) ;
         controller.setupButton( buttons, "Cancel", funct ) ;
         dijit.byId( "promptDialog" ).show() ;
       },

       setupButton: function( buttons, button, funct )
       {
         var style = buttons.indexOf( button ) !== false ? "" : "none" ;
         dojo.style( dojo.byId( "td" + button ), style ) ;
         var bt = dojo.byId( "button" + button ).onclick = funct ;
       },

       loadRoot: function()
       {
         var args1 = { url: "control?ar_action=sendConfig&ar_site=/nutch-site.xml",
                       handleAs: "json", preventCache: true, error: this.errorHandler,
                       load: function( object )
                       { controller.nutchCfg = object ;
                         if ( controller.rootCfg != null ) controller.fillSite( "root" ) ;
                       }
                     } ;
         dojo.xhrGet( args1 ) ;
         var args2 = { url: "control?ar_action=sendConfig&id=",
                       handleAs: "json", preventCache: true, error: this.errorHandler,
                       load: function( object )
                       {
                         if ( object[ "solr.url" ] == null || object[ "solr.url" ] == "" )
                               object[ "solr.url" ] = controller.getSolrURL() ;
                         controller.model[ "root" ] = lang.clone( object ) ;
                         controller.rootCfg = object ;
                         if ( controller.nutchCfg != null ) controller.fillSite( "root" ) ;
                       }
                     } ;
         dojo.xhrGet( args2 ) ;
       },

       loadSite: function( siteName, areaName )
       {
         if ( !siteName || this.currentSite == siteName ) return ;
         this.siteCfg = null ;
         this.currentSite = siteName ;
         this.currentArea = areaName ;
         this.siteFilled = false ;
         this.clear() ; // clear all parameters
         var args = { url: "control?ar_action=sendConfig&ar_site=" + siteName,
                      handleAs: "json", preventCache: true, load: function( object )
                       { controller.siteCfg = object ;
                         controller.fillSite() ;
                         controller.model[ siteName ] = lang.clone( object ) ;
                       },
                      error: this.errorHandler } ;
         dojo.xhrGet( args ) ;
       },

       errorHandler: function( error, ioargs )
       {
         if ( ioargs.xhr.responseText && ioargs.xhr.responseText.indexOf( "--Failed" ) == 0 )
         {
           var a = ioargs.xhr.responseText.substring( 8 ) ;
           var pos = a.indexOf( "\n" ) ;
           var msg = a.substr( 0, pos ) ;
           var details = a.substr( pos ) ;
           controller.showError( msg, details ) ;
         } else controller.showError( error ) ;
       },

       showError: function( error, details )
       {
         var d = dijit.byId( "errMessage" ) ;
         var msgDiv = dojo.byId( "msgDiv" ) ;
         var detailsDiv = dojo.byId( "detailsDiv" ) ;
         if ( error ) msgDiv.innerHTML = error ; else msgDiv.innerHTML = "" ;
         if ( details ) detailsDiv.innerHTML = details ; else detailsDiv.innerHTML = "" ;
         d.show() ;
       },

       openArea: function( siteName, areaName )
       {
         if ( !siteName || !areaName ||
            ( this.currentSite == siteName && this.currentArea == areaName ) ) return ;
         if ( this.currentSite != siteName ) this.loadSite( siteName, areaName ) ;
          else this.fillArea( areaName ) ;
       },

       fillSite: function( siteName )
       {
         if ( !siteName ) siteName = controller.currentSite ;
           else
            {
              if ( controller.siteCfg )
                 if ( !controller.packSite() ) return ; // packing failed - can't proceed
              controller.model[ controller.currentSite ] = lang.clone( controller.siteCfg ) ;
              controller.currentSite = siteName ;
            }
         if ( controller.siteFilled ) return ;
         if ( siteName != "root" ) controller.isRootConfig = false ;
            else { controller.isRootConfig = true ; controller.siteCfg = controller.rootCfg ; }
         controller.siteFilled = true ; // to avoid simultaneous filling
         controller.clear() ;

         if ( controller.isRootConfig )
         {
           if ( controller.areaTab == null )
           { controller.areaTab = dijit.byId( "areas" ) ;
             dijit.byId( "tabs" ).removeChild( dijit.byId( "areas" ) ) ;
           }
           if ( controller.crawlingTab != null )
           { dijit.byId( "tabs" ).addChild( controller.crawlingTab ) ;
             controller.crawlingTab = null ;
           }
         } else
         {
           if ( controller.crawlingTab == null )
           { controller.crawlingTab = dijit.byId( "crawling" ) ;
             dijit.byId( "tabs" ).removeChild( dijit.byId( "crawling" ) ) ;
           }
           if ( controller.areaTab != null )
           { dijit.byId( "tabs" ).addChild( this.areaTab ) ;
             controller.areaTab = null ;
           }
         }
         controller.changeView() ;

         // Do the best shot at automatic filling, then fix uncommon cases
         array.forEach( controller.allParams, function( entry )
         { controller.fillParam( entry ) ; } ) ;
         if ( this.isRootConfig ) // have to fill the nutch config as well
         {
           array.forEach( controller.nutchParams, function( entry )
             { controller.fillParam( entry, entry, controller.nutchCfg ) ; } ) ;
             controller.siteFilled = false ; return ;
         }
         // Else fill one of the areas
         var area = this.getDataField( "area", this.siteCfg ) ;
         if ( area ) this.fillArea( area ) ;
         dijit.byId( "tabs" ).selectChild( dijit.byId( "generalTab" ) ) ;
         controller.siteFilled = false ;
       },

       fillArea: function( areaName )
       {
         if ( controller.siteCfg == null || controller.siteCfg == controller.rootCfg ) return ;
         // clean current area
         array.forEach( controller.areaOnlyParams, function( entry )
           {
               var item = dijit.byId( entry ) ;
               if ( item ) item.set( "value", "" ) ;
           } ) ;
         var areaKey = "ar_area_" + areaName ;
         var areaData = controller.siteCfg[ areaKey ] ;
         if ( !areaData ) return ;
         // Hide everything optional
         controller.makeVisible( "area.depth", false ) ;  // does not apply to bookmarks
         controller.makeVisible( "area.contents", false, true ) ; // does not apply to bookmarks and loglinks
         controller.makeVisible( "bookmarks", false, true ) ; // only applies to bookmarks
         // Now show what is relevant
         if ( areaName == 'loglinks' )
           controller.makeVisible( "area.depth", true ) ;
          else if ( areaName == "bookmarks" )
           controller.makeVisible( "bookmarks", true, true ) ;
          else { controller.makeVisible( "area.depth", true ) ;
                 controller.makeVisible( "area.contents", true, true ) ;
               }
         dijit.byId( "area" ).set( "value", areaName ) ;
         var disabled = areaName == "bookmarks" || areaName == "logLinks" ? true : false ;
         dijit.byId( "area" ).set( "disabled", true ) ; // can't change area name
           // Now fill parameters
         controller.fillParam( "enabled", "enabled", areaData ) ;
         var interval = this.getDataField( "interval", areaData ) ;
         var weekdays = [ "sun", "mon", "tue", "wed", "thu", "fri", "sat" ] ;
         dijit.byId( "interval" ).set( "value", "1" ) ;
         array.forEach( weekdays, function( entry )
         { dijit.byId( entry ).set( "value", false ) } ) ;
         if ( interval )
         {
          var a = interval.split( " " ) ;
          var b = a.join( "" ) ;
          var c = b.split( "," ) ;
          array.forEach( c, function( entry, i )
          {
            if ( i == 0 )  dijit.byId( "interval" ).set( "value", entry ) ;
              else dijit.byId( entry ).set( "value", true ) ;
          } ) ;
          if ( c.length == 1 ) // no limitations
          {
            array.forEach( weekdays, function( entry )
            { dijit.byId( entry ).set( "value", true ) } ) ;
          }
         }
         controller.fillParam( "depth", "area.depth", areaData ) ;
         controller.fillParam( "threads", "area.threads", areaData ) ;
         controller.fillParam( "root", "root", areaData ) ;
         controller.fillParam( "include", "include", areaData ) ;
         controller.fillParam( "exclude", "exclude", areaData ) ;
         controller.fillParam( "file", "file.bookmarks", areaData ) ;
         controller.fillParam( "usersread", "usersread.bookmarks", areaData ) ;
         controller.fillParam(  "groupsread", "groupsread.bookmarks", areaData ) ;
         var areaTab = dijit.byId( "areas" ) ;
         dijit.byId( "tabs" ).selectChild( areaTab ) ;
         this.currentArea = areaName ;

       },

       fillParam: function( param, itemName, model )
       {
         if ( !itemName ) itemName = param ;
         var item = dijit.byId( itemName ) ;
         if ( !item ) return ;
         var val = null ;
         if ( !model )
         {
           if ( !this.isRootConfig ) // take from site config if possible
           {
             if ( this.siteCfg.hasOwnProperty( param ) ) val = this.siteCfg[ param ] ;
               else if ( this.rootCfg.hasOwnProperty( param ) ) val = this.rootCfg[ param ] ;
                else if ( this.nutchCfg.hasOwnProperty( param ) ) val = this.nutchCfg[ param ] ;
           } else // Root configuration
                 if ( this.rootCfg.hasOwnProperty( param ) ) val = this.rootCfg[ param ] ;
                   else if ( this.nutchCfg.hasOwnProperty( param ) ) val = this.nutchCfg[ param ] ;
         } else val = model[ param ] ;

         var aval = "" ;
         if ( val != null )
           if ( val instanceof Array ) aval = val.join( "\n" ) ;
              else aval = val ;
         if ( aval == "false" ) aval = false ;
         // Make default value for checkboxes false
         if ( item.declaredClass == "dijit.form.CheckBox" && val == null )
              item.set( "value", false ) ;
           else item.set( "value", aval ) ;
         val = null ; // just to stop debugger here
       },

       save: function( item )
       {
         var model = null ;
         var problems = false ;
         if ( item != "root" && item != "/nutch-site.xml" ) model = controller.model[ item ] ;
           else
         { if ( item == "/nutch-site.xml" ) model = controller.nutchCfg ;
           else {
                  if ( !controller.save( "/nutch-site.xml" ) ) return false ;
                  model = controller.rootCfg ;
                }
         }
         var params = { ar_action: "saveConfig", ar_json: dojo.toJson( model ), ar_site: item }
         dojo.xhrPost( {
                 url: "control",
                 handleAs: 'text',
                 content: params,
                 sync: true,
                 preventCache: true,
                 error: this.errorHandler,
                 load: function( result )
                 {
                   var err = null ;
                   if ( result.indexOf( "--Failed" ) == 0 )
                   { err = result.substr( 9 ) ;
                     controller.showError( controller.msg( "Failed_To_Save" ), err ) ;
                     problems = true ;
                   }
                 } } ) ;
         return !problems ;
       },

       saveChanges: function()
       {
         controller.packSite() ;
         var problems = false ;
         if ( controller.changes )
         for ( var i = 0 ; i < controller.changes.length ; i++ )
         {
           problems |= !controller.save( controller.changes[ i ] ) ;
         }
         if ( !problems )
           { controller.changes = null ; dojo.byId( "unsaved" ).style.display = "none" ; }
         return !problems ;
       },

       // Packs current area and site dialog fields to the model.
       pack: function()
       {
         var problems = !controller.packSite() ;
         if ( !problems ) problems = !controller.packArea() ;
         if ( !problems ) controller.model[ controller.currentSite ] = controller.siteCfg ;
         return !problems ;
       },

       packSite: function()
       {
         if ( controller.siteCfg == controller.rootCfg )
         {
           array.forEach( controller.nutchParams, function( entry )
           { controller.packParam( entry, null, controller.nutchCfg ) ; } ) ;
           array.forEach( controller.rootOnlyParams, function( entry )
           { controller.packParam( entry, null, controller.rootCfg ) ; } ) ;
         } else
         {
           array.forEach( controller.siteOnlyParams, function( entry )
           { controller.packParam( entry, null, controller.siteCfg ) ; } ) ;
           controller.packArea() ;
         }
         array.forEach( controller.otherParams, function( entry )
         { controller.packParam( entry, null, controller.siteCfg ) ; } ) ;
         // Re-pack multiline parameters into arrays
         controller.rePackParam( "frontend.profile", controller.siteCfg, true ) ;
         controller.rePackParam( "permissions", controller.siteCfg, true ) ;
         controller.rePackParam( "logs", controller.siteCfg, true ) ;
         controller.rePackParam( "ignore.in.logs", controller.siteCfg, false ) ;
         controller.rePackParam( "index.file.names", controller.siteCfg, false ) ;

         controller.rePackParam( "scan.content.types", controller.siteCfg, false ) ;
         controller.rePackParam( "scan.src.content.types", controller.siteCfg, false ) ;
         controller.rePackParam( "scan.file.types", controller.siteCfg, false ) ;
         controller.rePackParam( "prune.src.content.types", controller.siteCfg, false ) ;
         controller.rePackParam( "prune.file.types", controller.siteCfg, false ) ;
         controller.rePackParam( "scan.src.file.types", controller.siteCfg, false ) ;
         controller.rePackParam( "scan.script.edges", controller.siteCfg, true ) ;
         controller.rePackParam( "scan.ignore.bits", controller.siteCfg, true ) ;
         controller.rePackParam( "scan.ignore.scripts", controller.siteCfg, true ) ;
         controller.rePackParam( "scan.ignore.links", controller.siteCfg, true ) ;
         controller.rePackParam( "scan.alert", controller.siteCfg, true ) ;

         controller.model[ controller.currentSite ] = lang.clone( controller.siteCfg ) ;
         return true ; // just packing, nothing to go wrong
       },

       packArea: function()
       {
         if ( controller.siteCfg == null || controller.siteCfg == controller.rootCfg ) return ;
         if ( controller.currentArea == null || controller.currentArea == "" ) return ;
         var areaKey = "ar_area_" + controller.currentArea ;
         var areaData = controller.siteCfg[ areaKey ] ;
         if ( !areaData ) areaData = {} ;
         // pack all area parameters
         var map = { enabled : "enabled", interval: "interval",
                     "area.depth" : "depth", "area.threads" : "threads", root: "root",
                     include: "include", exclude: "exclude", "file.bookmarks" : "file",
                     "usersread.bookmarks" : "usersread", "groupsread.bookmarks" : "groupsread" } ;
         for ( var key in map )
         { controller.packParam( map[ key ], key, areaData ) ; }
         // re-pack interval
         var interval = this.getDataField( "interval", areaData ) ;
         var weekdays = [ "sun", "mon", "tue", "wed", "thu", "fri", "sat" ] ;
         array.forEach( weekdays, function( entry )
           {
              var val = dijit.byId( entry ).checked ;
               if ( val ) interval += "," + entry ;
           } ) ;
         areaData[ "interval" ] = [ interval ] ;
         // Re-pack multilines
         controller.rePackParam( "root", areaData, true ) ;
         controller.rePackParam( "include", areaData, true ) ;
         controller.rePackParam( "exclude", areaData, true ) ;
         controller.rePackParam( "file", areaData, true ) ;
         if ( !controller.siteCfg.area ) controller.siteCfg.area = [] ;
         if ( array.indexOf( controller.siteCfg.area, controller.currentArea ) == -1 )
             controller.siteCfg.area.push( controller.currentArea ) ;
         // Clean
         if ( controller.currentArea == "bookmarks" || controller.currentArea == "loglinks" )
         { delete areaData[ "root" ] ; delete areaData[ "include" ] ; delete areaData[ "exclude" ] ; }
         if ( controller.currentArea != "bookmarks" )
         { delete areaData[ "usersread" ] ; delete areaData[ "groupsread" ] ; delete areaData[ "file" ] ;  }
         controller.siteCfg[ areaKey ] = areaData ;
         return true ;
       },

       // Packs parameter to the model. Returns false if the field is marked as invalid.
       packParam: function( param, itemName, model )
       {
         if ( !itemName ) itemName = param ;
         var item = dijit.byId( itemName ) ;
         if ( !item || dojo.style( item, "display" ) == "none" )  return true ;
         var val = item.get( "value" ) ;
         if ( val == "default" || !val || val == "" ) delete model[ param ] ;
                                   else model[ param ] = [ "" + val ] ;
         if ( item.type == "checkbox" )
            { val = item.checked ; model[ param ] = [ "" + val ] ; }
         return true ;
       },

       // Re-packs multiline parameter as an array of strings
       rePackParam: function( param, model, multiline )
       {
         if ( !model[ param ] ) return ;
         var val = model[ param ][0] ;
         if ( !val || val == "" || val == "default" ) return ;
         var arr = multiline ?  val.split( "\n" ) : [ val.replace( "\n", " " ) ] ;
         var brr = [] ;
         for ( var i = 0 ; i < arr.length ; i++ )
         {
           var c = arr[ i ].trim() ;
           if ( c != "" ) brr.push( c ) ;
         }
         model[ param ] = brr ;
       },

       // Moves focus to invalid field and shows error in the status bar.
       statusBarError: function( item )
       {
         controller.statusBar( null, "Invalid" ) ;
         var tab = dojo.NodeList( dojo.byId( item ) ).parents( ".archTab" ) ;
         dijit.byId( "tabs" ).selectChild( tab ) ;
         dijit.focus(  dojo.byId( item ) ) ;
       },

       getDataField: function( param, model, original )
       {
         if ( !model || !param || !model[ param ] ) return null ;
         var v = model[ param ] ;
         if ( original || !( v instanceof Array ) ) return v ;
         if ( v.length == 0 ) return null ;
         return v[ 0 ] ;
       },

       clear: function()
       {
         array.forEach( controller.allPlusArea, function( entry )
         {
           var item = dijit.byId( entry ) ;
           if ( item ) item.set( "value", "" ) ;
           // TODO: take off error styles
         } ) ;
       },

       changeView: function()
       {
         array.forEach( this.rootOnlyParams, function( entry )
           { controller.toggleVis( entry, true, true  ) ; } ) ;
         array.forEach( this.siteOnlyParams, function( entry )
           { controller.toggleVis( entry, true, false ) ; } ) ;
         array.forEach( this.rootOnlySets, function( entry )
           { controller.toggleVis( entry, false, true  ) ; } ) ;
         array.forEach( this.siteOnlySets, function( entry )
           { controller.toggleVis( entry, false, false ) ; } ) ;
       },

       toggleVis: function( entry, parent, root )
       {
         var style = "" ;
         if ( controller.isRootConfig && !root ) style = "none" ;
         if ( !controller.isRootConfig && root ) style = "none" ;
         var node = parent ? dojo.byId( "tr.of." + entry ) : dojo.byId( entry ) ;
         if ( node ) dojo.style( node, "display", style ) ;
       },

       //===============================================================================================================
       //  Shows or hides (depending on "visible") element or its row (depending on itself)
       makeVisible: function( name, visible, itself ) //================================================================
       {
         if ( !itself ) name = "tr.of." + name ;
         var element = dojo.byId( name ) ;
         if ( !element ) return ;
         var style = visible ? "" : "none" ;
         element.style.display = style ;
       },

       //===============================================================================================================
       //         Shows or hides help of visible elements in a field set where the "i" has been clicked on
       toggleHelp: function( element ) //===============================================================================
       {
         var table = element.parentNode.parentNode.parentNode ;
         var helpRows = query(".helpRow", table ) ;
         var isShown = false ;
         array.forEach( helpRows, function( entry )
         { if ( entry.style.display != "none" ) isShown = true ; } ) ;
         var display = isShown ? "none" : "" ;
         array.forEach( helpRows, function( entry )
         {
           var helped = entry.id.substr( 9 ) ; // skip "help.for."
           var tr = dojo.byId( "tr.of." + helped ) ;
           if ( tr && tr.style.display != "none" )
                                entry.style.display = display ;
         } ) ;
       },
       //===============================================================================================================
       //                                           Wizards related stuff
       //===============================================================================================================
//       rootWizardParams: [ "http.agent.name", "admin.user" ],
       rootWizardParams: [ "http.agent.name", "http.agent.description", "http.agent.url",
          "http.agent.email", "http.agent.version", "http.robots.agents", "http.agent.host",
          "solr.url", "temp.dir", "database", "target.db", "db.driver",
          "ignore.in.logs", "depth", "depth.loglinks", "threads", "mail.level",
          "mail.transport.protocol", "mail.host", "mail.user", "mail.pass", "mail.subject",
          "mail.recipient","frontend.profile","admin.user" ],
       siteWizardParams: [ "url", "permissions", "admin.user" ],
       areaWizardParams: [ "interval", "root", "include", "exclude" ],
       bookmarksWizardParams: [ "interval", "file.bookmarks", "usersread.bookmarks", "groupsread.bookmarks" ],
       loglinksWizardParams: [ "interval" ],
//       siteWizardParams: [ "url", "admin.user" ],
//       areaWizardParams: [ "interval", "groupsread.bookmarks" ],
       currentParams: null,
       currentWizard: null,
       currentWizardType: null,
       currentField: null,
       wizLabelWidth: null,

       prepareWizards: function()
       {
         // TODO: save changes if any
         // controller.clear() ;
         // add back crawling and area tabs
        if ( controller.crawlingTab )
               dijit.byId( "tabs" ).addChild( controller.crawlingTab ) ;
        if ( controller.areaTab )
               dijit.byId( "tabs" ).addChild( controller.areaTab ) ;
        controller.crawlingTab = null ;
        controller.areaTab = null ;
        dojo.byId( "wiz.quit" ).src = "images/nogo.png" ;
       },

       startWizard: function( wiz )
       {
         var current = this.currentWizard ;
         if ( this.currentWizard == "siteWizardStart" || this.currentWizard == "rootWizardStart" )
                                                                           controller.packSite() ;
         if ( this.currentWizard == "siteWizardStart" ) // validate new site name first
         {
           if ( !controller[ "validate.new.site.name" ]
                 ( dijit.byId( "new.site.name" ), controller[ "val.new.site.name" ] ) ) return ;
           controller.currentWizardType = "site" ;
           this.currentParams = this.siteWizardParams ;
         }
         if ( this.currentWizard == "areaWizardStart" ) // validate new area name first
         {
           var name = dijit.byId( "new.area.name" ).value ;
           if ( !controller[ "validate.new.area.name" ]
                  ( dijit.byId( "new.area.name" ), controller[ "val.new.area.name" ] ) ) return ;
           controller.currentWizardType = "area" ;
           this.currentParams = this.areaWizardParams ;
           if ( name == 'loglinks' ) this.currentParams = this.loglinksWizardParams ;
           if ( name == 'bookmarks' ) this.currentParams = this.bookmarksWizardParams ;
         }
         if ( this.currentWizard == "rootWizardStart" )
            { controller.currentWizardType = "root" ; this.currentParams = this.rootWizardParams ;
              controller.moveToRoot() ;
            }

         if ( current ) dijit.byId( current ).hide() ;
         if (  wiz != "common" )
         {
           this.currentWizard =  wiz + "WizardStart" ;
           dijit.byId( "startWizard" ).hide() ;
         } else
         {
           this.currentWizard = "commonWizard" ;
           controller.prepareWizards() ;
         }
         var wizard = dijit.byId( this.currentWizard ) ;
         on( wizard, "onCancel", this.quitWizard ) ;
         var style =  controller.mySite ? "" : "none" ;
         dojo.style( dojo.byId( "MySiteWarning" ), "display", style ) ;
         wizard.show() ;
         if ( wiz == "common" ) this.nextWizard( 0 ) ;
       },

       quitWizard: function( noswap )
       {
         controller.validationEnabled = false ;
         if ( !noswap ) this.swapTD() ;
         if ( this.currentWizard ) dijit.byId( this.currentWizard ).hide() ;
         // if the current wizard has been finished OK, offer to continue
         if ( this.currentField == "admin.user" ) // Root or site wizard is finishing
           if ( this.currentParams == this.rootWizardParams && dojo.byId( "toSiteWizard" ).checked )
                                                           { this.startWizard( "site" ) ; return ; }
            else if ( this.currentParams == this.siteWizardParams && dojo.byId( "toAreaWizard" ).checked  )
                                                           { this.startWizard( "area" ) ; return ; }
        this.currentParams = null ;
        this.currentWizard = null ;
        if ( !noswap ) controller.moveToRoot() ;
       },

       nextWizard: function( step )
       {
         var wizLabel = dojo.byId( "wiz.param.label" ) ;
         if ( !this.wizLabelWidth ) this.wizLabelWidth = wizLabel.style.width ;

         var tBody = dojo.byId( "wiztable" ) ;
         if ( step != 0 )
         { if ( step > 0 ) // wants to go forward
             { var item = dijit.byId( this.currentField ) ;
               if ( !controller.validate( "", item ) ) return ; // Invalid input, do not move
             }
           this.swapTD() ; // there are some params
         } else this.currentField = null ;

         if ( this.lastStep() ) // A successful completion of a wizard
         { this.quitWizard( true ) ;
           controller.validationEnabled = false ;
           if ( this.currentWizardType == "site" ) controller.createNewSite() ;
           if ( this.currentWizardType == "area" )
           {
             controller.createNewArea() ;
             controller.moveToRoot() ;
           }
           if ( this.currentWizardType == "root" )
                           controller.moveToRoot() ;
           return ;
         }

         // show or hide Back
         this.currentField = this.findParam( step ) ;
         var backStyle = "" ;
         if ( this.firstStep() ) backStyle = "none" ;
         dojo.style( dojo.byId( "back" ), "display", backStyle ) ;

         // show or hide next
         var nextStyle = "", nextSite = "none", nextArea = "none" ;
         if ( this.lastStep() )
         {
           nextStyle = "none" ;
           if ( this.currentField == "admin.user" )
               if ( this.currentParams == this.rootWizardParams ) nextSite = "" ;
               else if ( this.currentParams == this.siteWizardParams ) nextArea = "" ;
         }
//         dojo.style( dojo.byId( "next" ), "display", nextStyle ) ;
         dojo.style( dojo.byId( "toSiteWizardTd" ), "display", nextSite ) ;
         dojo.style( dojo.byId( "toAreaWizardTd" ), "display", nextArea ) ;

         // replace label text
         var trData = dojo.byId( "tr.of." + this.currentField ) ;
         var tdLabel = query(".paramLabel", trData )[0] ;
         if ( !tdLabel ) { wizLabel.innerHTML= "" ; wizLabel.style.width = "10px" ; }
          else { wizLabel.innerHTML = tdLabel.innerHTML ; wizLabel.style.width = this.wizLabelWidth ; }
         // replace the widget dt
         this.swapTD() ;
         // replace help text
         var tdWizHelp = dojo.byId( "wiz.help.for.param"  ) ;
         var trHelp = dojo.byId( "help.for." + this.currentField ) ;
         var tdHelp = query("td", trHelp )[0] ;
         tdWizHelp.innerHTML = tdHelp.innerHTML ;
       },

       moveToRoot: function()
       {
         controller.navigationTree.set( "path", [ "root" ] ) ;
         controller.fillSite( "root" ) ;
         controller.currentSite = "root" ;
         controller.siteCfg = controller.rootCfg ;
         controller.currentArea = null ;
       },


       //===============================================================================================================
       /**
        * Creates a new site model based on the current parameters and saves it to the server
        */
       createNewSite: function()
       {
         // Transfer parameters from forms fields to the model
         controller.currentSite = dijit.byId( "new.site.name" ).value ;
         controller.siteCfg = {} ;
         controller.currentArea = null ; // does not exist
         controller.packSite() ;
         controller.model[ controller.currentSite ] = lang.clone( controller.siteCfg ) ;
         // Now save the model(s)
         controller.save( controller.currentSite ) ;
         // Refresh the site tree
         controller.refreshTree() ;
       },

       //===============================================================================================================
       /**
        * Creates a new area, adds it to the model, saves to the server
        */
       createNewArea: function()
       {
         controller.currentArea = dijit.byId( "new.area.name" ).value ;
         controller.packSite() ;
         controller.model[ controller.currentSite ] = lang.clone( controller.siteCfg ) ;
         controller.save( controller.currentSite ) ;
         controller.refreshTree() ;
       },

       deleteSite: function( name )
       {
         if ( name == null ) name = controller.currentSite ;
         if ( name == "root" ) return ;
         if ( !confirm( controller.msg( "Delete_Site" ) + name + "?" ) ) return ;
         var params = { ar_action: "deleteSite", ar_site: name } ;
         var problems = false ;
         dojo.xhrGet( { url: "control", handleAs: 'text', content: params, sync: true,
                        preventCache: true, error: this.errorHandler,
                 load: function( result )
                 {
                     var err = null ;
                     if ( result.indexOf( "--Failed" ) == 0 )
                     { err = result.substr( 9 ) ;
                         controller.showError( controller.msg( "Could not delete site" ), err ) ;
                         problems = true ;
                     }
                 } } ) ;
         if ( !problems ) controller.deleteOnSolr( "ar_site:" + name ) ;
         if ( !problems ) { delete controller.model[ name ] ; controller.refreshTree() ; }
         return problems ;
       },

       deleteArea: function( site, area )
       {
         if ( !site ) site = controller.currentSite ;
         if ( !area ) area = controller.currentArea ;
         if ( site == "root" ) return ;
         if ( !confirm( controller.msg( "Delete_Area" ) + site + "/" + area + "?" ) ) return ;
         controller.packSite() ;
         var model = controller.model[ site ] ;
         delete model[ "ar_area_" + area ] ;
         controller.deleteElement( model.area, area ) ;
         if ( site == controller.currentSite )
         {
           delete controller.siteCfg[ "ar_area_" + area ] ;
           controller.deleteElement( controller.siteCfg.area, area ) ;
         }
         controller.save( site ) ;
         controller.deleteOnSolr( "(ar_site:" + site + ")AND(ar_area:" + area + ")" ) ;
         controller.refreshTree() ;
       },

       deleteElement: function( arr, elem )
       {
         if ( !arr ) return ;
         var ix = array.indexOf( arr, elem ) ;
         if ( ix != -1 ) arr.splice( ix, 1 ) ;
       },

       deleteOnSolr: function( filter )
       {
         var solrURL = controller.getSolrURL() + "/update" ;
         var params = { "stream.body": "<delete><query>" + filter + "</query></delete>" } ;
         dojo.xhrGet( { url: solrURL, handleAs: 'text', content: params, sync: false,
               preventCache: true, error: this.errorHandler } ) ;
       },

       getSolrURL: function()
       {
         var url = window.location.href ;
         var solrURL = controller.rootCfg[ "solr.url" ] ;
         if ( solrURL == null || solrURL == "" )
         {
            var pos = url.indexOf( "/control" ) ;
            solrURL = url.substr( 0, pos ) ;
         }
         return solrURL ;
       },

       lastStep: function()
       {
         var params = this.currentParams ;
         var field = this.currentField ;
         if ( params.indexOf( field ) == params.length - 1 ) return true ;
         return false ;
       },

       firstStep: function()
       {
         return this.currentField == "url" || this.currentField == "interval" ||
                this.currentField == "http.agent.name" ;
       },

       // Swap param td in the wozard and on the pannels
       swapTD: function()
       {
         var trWiz = dojo.byId( "wiz.tr.of.param" ) ;
         var id = "tr.of." + this.currentField ;
         var trPan =  dojo.byId( id ) ;
         var trPan = document.getElementById( id ) ;
         var tdWiz =  query(".paramText", trWiz )[0] ;
         var tdPan =  query(".paramText", trPan )[0] ;
         var tdLabWiz =  query(".paramLabel", trWiz )[0] ;
         var tdLabPan =  query(".paramLabel", trPan )[0] ;
         trPan.removeChild( tdPan ) ;
         trWiz.removeChild( tdWiz ) ;
         dojo.place( tdPan, tdLabWiz, "after" ) ;
         if ( tdLabPan ) dojo.place( tdWiz, tdLabPan, "after" ) ;
              else trPan.appendChild( tdWiz ) ;
       },

       findParam: function( step )
       {
        if ( step == 0 ) return this.currentParams[ 0 ] ;
        var lastParam = null ;
        for ( var i = 0 ; i < this.currentParams.length ; i++ )
        {
          if ( this.currentField == this.currentParams[ i ] )
          {
            if ( step == -1 ) return lastParam ;
            if ( i == this.currentParams.length - 1 ) return null ;
            return this.currentParams[ i + 1 ] ;
          } else lastParam = this.currentParams[ i ] ;
        }
       },

       //===============================================================================================================
       //                                           Validation related stuff
       //===============================================================================================================
       "val.solr.url" : [ "solr.url" ],
       "val.temp.dir" : [ "temp.dir" ],

       "val.database" : [ "database", "target.db", "db.driver" ],
       "val.target.db" : "val.database",
       "val.db.driver" : "val.database",

       "val.mail.level" : [ "mail.level", "mail.transport.protocol", "mail.host", "mail.user", "mail.pass",
                            "mail.subject", "mail.recipient" ],
       "val.mail.transport.protocol" : "val.mail.level",
       "val.mail.host" : "val.mail.level",
       "val.mail.user" : "val.mail.level",
       "val.mail.pass" : "val.mail.level",
       "val.mail.subject" : "val.mail.level",
       "val.mail.recipient" : "val.mail.level",

       "val.url" : [ "url" ],
       "val.sitemap.url" : [ "sitemap.url" ],
       "val.permissions" : [ "url", "permissions" ],
       "val.root" : [ "url", "root" ],
       "val.include" : [ "url", "include" ],
       "val.exclude" : [ "url", "exclude" ],
       "val.new.site.name" : [ "new.site.name" ],
       "val.new.area.name" : [ "new.area.name" ],
       "val.area" : [ "area" ],
       "val.file.bookmarks" : [ "file.bookmarks" ],

       validate : function( event, target )
       {
        // First, find out what field to validate
        var field = target.id ;
        // A simple test on required parameter
        var redirection = this[ "val." + field ] ;
        if ( !redirection ) // no custom validation defined
        {
          var err = target.required && ( target.value == null || target.value == "" ) ? "Required" : null ;
          if ( !err ) err = "Error" != target.get( "state" ) ? null : "Invalid" ;
          controller.mark( err, [ field ] ) ;
          return err == null ;
        }
        if ( typeof( redirection ) == 'string' ) // there is a redirection
                                   field = redirection.substr( 4 ) ;
        // Is any of the listed fields still having focus? If yes - don't bother yet
        var haveToValidate = true ;
        var fieldGroup = this[ "val." + field ] ;
        array.forEach( fieldGroup, function( entry )
           {
              var item = dijit.byId( entry ) ;
              if ( item._focused ) haveToValidate = false ;
           } ) ;
        // If a wizard is running and this field is not last in the group
        if ( this.currentWizard != null &&
             this.currentField != fieldGroup[ fieldGroup.length - 1 ] ) haveToValidate = false ;
        if ( !haveToValidate ) return true ;
        // At this point it is clear that we have to validate
        // Is there a custom function?
        if ( typeof controller[ "validate." + field ] === 'function' )
                return controller[ "validate." + field ]( target, fieldGroup ) ;
        // If not, this is server side validation
           else return controller.serverValidate( target, fieldGroup ) ;
       },


       serverValidate : function( target, fieldGroup )
       {
         var field = target.id ;
         var params = { ar_action: "validate" } ;
         array.forEach( fieldGroup, function( entry )
           { params[ entry ] = dijit.byId( entry ).value ; } ) ;
         controller.mark( "Server-side validation", fieldGroup ) ;
         var error = null ;
         dojo.xhrGet({
               url: "control",
               handleAs: 'text',
               content: params,
               sync: true,
               preventCache: true,
               load: function( result )
               {
                 if ( result.indexOf( "--Failed" ) == 0 ) error = result.substr( 9 ) ;
                 controller.mark( error, fieldGroup ) ;
               }
         }) ;
         return error === null ;
       },


       mark : function( error, fieldGroup, messageExtra )
       {
         var state = error ? "Error" : "" ;
         if ( !messageExtra ) messageExtra = "" ;
         var message = error ?
                       controller.msg( error ) + messageExtra : messageExtra ;
         if ( error && !controller.msg( error ) ) message = "Unidentified error." ;
         array.forEach( fieldGroup, function( entry )
           { var item = dijit.byId( entry ) ;
             // item.set( "state", state ) ;
             if ( error ) dojo.addClass( item.domNode, 'archError' ) ;
                     else dojo.removeClass( item.domNode, 'archError' ) ;
           } ) ;
         // Display error message in the status bar if needed
         controller.statusBar( message, error ) ;
       },


       statusBar: function( message, error )
       {
         if ( message == null && error ) message = controller.msg( error ) ;
         dojo.byId( "message" ).innerHTML = message ;
         var alarmStyle = error ? "" : "none" ;
         dojo.byId( "alarm" ).style.display = alarmStyle ;
         if ( error ) dojo.byId( "message" ).setAttribute( "class", "errorMessage" ) ;
            else dojo.byId( "message" ).setAttribute( "class", "emptyMessage" ) ;
       },


       "validate.solr.url" : function( target, fieldGroup )
       {
        // TODO: show a warning if the URL does not match the control URL
         var error = !validate.isUrl( target.value ) ? error = "Invalid_URL" : null ;
         controller.mark( error, fieldGroup ) ;
         return !error ;
       },


       "validate.url" : function( target, fieldGroup )
       {
         return this[ "validate.solr.url" ]( target, fieldGroup ) ;
       },


       "validate.sitemap.url" : function( target, fieldGroup )
       {
         return this[ "validate.solr.url" ]( target, fieldGroup ) ;
       },


       "validate.permissions" : function( target, fieldGroup )
       {
         // Every record has to start with 'd' or 'f' and match the base URL.
         // It has to include 8 fields and the last one has to be 's' or 'i'
         var val = target.value ;
         if ( val == "" || val.trim() == "" ) return true ;
         // Split by lines
         var lines = val.split( "\n" ) ;
         var url = dijit.byId( "url" ).value.trim() ;
         var aBug = null ; var error = "Invalid_Permissions" ;
         for ( var i = 0 ; i < lines.length ; i++ )
         {
           if ( lines[i].trim() == "" ) continue ;
           var fields = lines[ i ].split( "|" ) ;
           if ( ( fields.length != 8 ) ||
                ( fields[ 0 ].trim() != 'd' && fields[ 0 ].trim() != 'f' ) ||
                ( fields[ 1 ].trim().indexOf( url ) != 0 ) ||
                ( fields[ 7 ].trim() != 's' && fields[ 7 ].trim() != 'i' ) )
                    if ( aBug === null ) aBug = ": " + lines[ i ] ;
         }
         if ( aBug === null ) error = null ;
         controller.mark( error, fieldGroup, aBug ) ;
         return aBug === null ;
       },


       "validate.root" : function( target, fieldGroup )
       {
         var val = target.value ;
         // Every record has to start with the base URL
         if ( val == "" || val.trim() == "" )
          { controller.mark( "Empty_roots", fieldGroup ) ; return false ; }
         return this.validateURLs( target, fieldGroup, "Invalid_root_record" ) ;
       },


       "validate.include" : function( target, fieldGroup )
       {
         var val = target.value ;
         // Every record has to start with the base URL
         if ( val == "" || val.trim() == "" )
           { controller.mark( "Empty_includes", fieldGroup ) ; return false ; }
         return this.validateURLs( target, fieldGroup, "Invalid_include_record" ) ;
       },

       "validate.exclude" : function( target, fieldGroup )
       { return this.validateURLs( target, fieldGroup, "Invalid_exclude_record" ) ; },

       validateURLs : function( target, fieldGroup, error )
       {
           // Split by lines
         var val = target.value ;
         var lines = val.split( "\n" ) ;
         var url = dijit.byId( "url" ).value.trim() ;
         var aBug = null ;
         for ( var i = 0 ; i < lines.length ; i++ )
           {
               if ( lines[ i ].trim() == "" ) continue ;
               if ( lines[ i ].trim().indexOf( url ) != 0 )
                 if ( !aBug ) aBug = ": " + lines[ i ] ;
           }
         if ( !aBug ) error = null ;
         controller.mark( error, fieldGroup, aBug ) ;
         return aBug === null ;
       },

       "validate.new.area.name" : function( target, fieldGroup )
       {
         var val = target.value ;
         var error = val == null || val == "" || val.length > 30 || !val.match( /^[a-z0-9]+$/i ) ?
               "Invalid_Area_Name" : null ;
         if ( !error )
            if ( controller.currentSite == "root" ) error = "No_Areas_In_Root" ;
         if ( !error ) // check if this area is unique
         { // by this time, the new site model must have been created and saved
           var model = controller.model[ controller.currentSite ] ;
           if ( model[ "ar_area_" + val ] ) // an area with this name already exists
                               error = "Area_Exists" ;
         }
         controller.mark( error, fieldGroup ) ;
         return error === null ;
       },

       "validate.new.site.name" : function( target, fieldGroup )
       {
         var val = target.value ;
         var error = val == null || val == "" || val.length > 30 || !val.match( /^[a-z0-9]+$/i ) ?
                 "Invalid_Site_Name" : null ;
         if ( !error ) return controller.serverValidate( target, fieldGroup ) ;
       },

       "validate.area" : function( target, fieldGroup )
       { return controller[ "validate.new.area.name" ]( target, fieldGroup ) ; },

       msg: function( message )
       {
             if ( archMsgTable[ message ] ) message = archMsgTable[ message ] ;
             return message ;
       },

       refreshTree: function()
       {
         var tree = controller.navigationTree ;
         tree.dndController.selectNone() ;
         if ( tree.model.store )
           {
             tree.model.store.clearOnClose = true ;
             tree.model.store.close() ;
           }
         tree._itemNodesMap = {} ;
           tree.rootNode.state = "UNCHECKED" ;
         if ( tree.model.root )
               tree.model.root.children = null ;
           tree.rootNode.destroyRecursive() ;
           tree.model.constructor(dijit.byId("tree").model) ;
           tree.postMixInProperties() ;
           tree._load() ;
         controller.path = [ "root" ] ;
         if ( controller.currentSite != "root" ) controller.path.push( controller.currentSite ) ;
         if ( controller.currentArea != null ) controller.path.push( controller.currentArea ) ;
       }

     });
 });