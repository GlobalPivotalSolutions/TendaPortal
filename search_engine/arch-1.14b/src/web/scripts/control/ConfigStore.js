/**
 *
 * Author: Arkadi Kosmynin
 * Date: 3/03/12
 * Time: 1:23 PM
 */

define(["dojo/store/util/QueryResults", "dojo/_base/declare", "dojo/store/util/SimpleQueryEngine",
        "dojo/_base/lang", "dojo/store/JsonRest" ],
    function( QueryResults, declare, SimpleQueryEngine, lang, JsonRest ){

        //  Declare the initial store
        return declare(JsonRest, {

        constructor: function( options )
            {
                lang.mixin(this, options || {});
            },

            mayHaveChildren: function(object)
            { // see if it has a children property
              //  return "children" in object;
                return object.hasChildren ;
            },

            getChildren: function(object, onComplete, onError)
            {    // retrieve the full copy of the object
                this.get(object.id).then( function( fullObject )
                {    // copy to the original object so it has the children array as well.
                    object.children = fullObject.children;
                    if ( object.id == "root" )
                    {
                      controller.rootChildrenLoaded = true ;
                      for ( var i = 0 ; i < object.children.length ; i++ )
                      {
                        if ( object.children[ i ].id == "root_template" )
                            controller.mySite = true ;
                      }
                    }
                    // now that full object, we should have an array of children
                    onComplete(fullObject.children);
                }, function(error){
                    // an error occurred, log it, and indicate no children
                    console.error(error);
                    onComplete([]);
                });
            },

            getRoot: function( onItem, onError ) {
                // get the root object, we will do a get() and callback the result
                this.get("root").then( onItem, onError ) ;
            },

            getLabel: function( object ) {
                // just get the name
                return object.name ;
            }        });
    });