/************************************************************************************************************
Drag and drop folder tree
Copyright (C) 2006  DTHMLGoodies.com, Alf Magne Kalleland

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA

Dhtmlgoodies.com., hereby disclaims all copyright interest in this script
written by Alf Magne Kalleland.

Alf Magne Kalleland, 2006
Owner of DHTMLgoodies.com


************************************************************************************************************/	
		
	var JSTreeObj;
	var treeUlCounter = 0;
	var nodeId = 1;
		
	/* Constructor */
	function JSDragDropTree()
	{
		var idOfTree;
		var imageFolder;
		var folderImage;
		var plusImage;
		var minusImage;
		var maximumDepth;
		var dragNode_source;
		var dragNode_parent;
		var dragNode_sourceNextSib;
		var dragNode_noSiblings;
		var ajaxObjects;
		
		var dragNode_destination;
		var floatingContainer;
		var dragDropTimer;
		var dropTargetIndicator;
		var insertAsSub;
		var indicator_offsetX;
		var indicator_offsetX_sub;
		var indicator_offsetY;
		
		this.imageFolder = 'images/';
		this.folderImage = 'folder.gif';
		this.plusImage = 'plus.gif';
		this.minusImage = 'minus.gif';
		this.maximumDepth = 6;
		var messageMaximumDepthReached;
		var filePathRenameItem;
		var filePathDeleteItem;
		var additionalRenameRequestParameters = {};
		var additionalDeleteRequestParameters = {};

		var renameAllowed;
		var deleteAllowed;
		var currentlyActiveItem;
		var contextMenu;
		var currentItemToEdit;		// Reference to item currently being edited(example: renamed)
		var helpObj;
		var Q_DELETE ; // delete question text
		
		this.contextMenu = false;
		this.floatingContainer = document.createElement('UL');
		this.floatingContainer.style.position = 'absolute';
		this.floatingContainer.style.display='none';
		this.floatingContainer.id = 'floatingContainer';
		this.insertAsSub = false;
		document.body.appendChild(this.floatingContainer);
		this.dragDropTimer = -1;
		this.dragNode_noSiblings = false;
		this.currentItemToEdit = false;
		
		if(document.all){
			this.indicator_offsetX = 2;	// Offset position of small black lines indicating where nodes would be dropped.
			this.indicator_offsetX_sub = 4;
			this.indicator_offsetY = 2;
		}else{
			this.indicator_offsetX = 1;	// Offset position of small black lines indicating where nodes would be dropped.
			this.indicator_offsetX_sub = 3;
			this.indicator_offsetY = 2;			
		}
		if(navigator.userAgent.indexOf('Opera')>=0){
			this.indicator_offsetX = 2;	// Offset position of small black lines indicating where nodes would be dropped.
			this.indicator_offsetX_sub = 3;
			this.indicator_offsetY = -7;				
		}

		this.messageMaximumDepthReached = ''; // Use '' if you don't want to display a message 
		
		this.renameAllowed = true;
		this.deleteAllowed = true;
		this.currentlyActiveItem = false;
		this.filePathRenameItem = 'folderTree_updateItem.php';
		this.filePathDeleteItem = 'folderTree_updateItem.php';
		this.ajaxObjects = new Array();
		this.helpObj = false;
		
		this.RENAME_STATE_BEGIN = 1;
		this.RENAME_STATE_CANCELED = 2;
		this.RENAME_STATE_REQUEST_SENDED = 3;
		this.renameState = null;
	}
	
	
	/* JSDragDropTree class */
	JSDragDropTree.prototype = {
		// {{{ addEvent()
	    /**
	     *
	     *  This function adds an event listener to an element on the page.
	     *
	     *	@param Object whichObject = Reference to HTML element(Which object to assigne the event)
	     *	@param String eventType = Which type of event, example "mousemove" or "mouseup"
	     *	@param functionName = Name of function to execute. 
	     * 
	     * @public
	     */	
		addEvent : function(whichObject,eventType,functionName)
		{ 
		  if(whichObject.attachEvent){ 
		    whichObject['e'+eventType+functionName] = functionName; 
		    whichObject[eventType+functionName] = function(){whichObject['e'+eventType+functionName]( window.event );} 
		    whichObject.attachEvent( 'on'+eventType, whichObject[eventType+functionName] ); 
		  } else 
		    whichObject.addEventListener(eventType,functionName,false); 	    
		} 
		// }}}	
		,	
		// {{{ removeEvent()
	    /**
	     *
	     *  This function removes an event listener from an element on the page.
	     *
	     *	@param Object whichObject = Reference to HTML element(Which object to assigne the event)
	     *	@param String eventType = Which type of event, example "mousemove" or "mouseup"
	     *	@param functionName = Name of function to execute. 
	     * 
	     * @public
	     */		
		removeEvent : function(whichObject,eventType,functionName)
		{ 
		  if(whichObject.detachEvent){ 
		    whichObject.detachEvent('on'+eventType, whichObject[eventType+functionName]); 
		    whichObject[eventType+functionName] = null; 
		  } else 
		    whichObject.removeEventListener(eventType,functionName,false); 
		} 
		,	
		Get_Cookie : function(name) { 
		   var start = document.cookie.indexOf(name+"="); 
		   var len = start+name.length+1; 
		   if ((!start) && (name != document.cookie.substring(0,name.length))) return null; 
		   if (start == -1) return null; 
		   var end = document.cookie.indexOf(";",len); 
		   if (end == -1) end = document.cookie.length; 
		   return unescape(document.cookie.substring(len,end)); 
		} 
		,
		// This function has been slightly modified
		Set_Cookie : function(name,value,expires,path,domain,secure) { 
			expires = expires * 60*60*24*1000;
			var today = new Date();
			var expires_date = new Date( today.getTime() + (expires) );
		    var cookieString = name + "=" +escape(value) + 
		       ( (expires) ? ";expires=" + expires_date.toGMTString() : "") + 
		       ( (path) ? ";path=" + path : "") + 
		       ( (domain) ? ";domain=" + domain : "") + 
		       ( (secure) ? ";secure" : ""); 
		    document.cookie = cookieString; 
		} 
		,
		setFileNameRename : function(newFileName)
		{
			this.filePathRenameItem = newFileName;
		}
		,
		setFileNameDelete : function(newFileName)
		{
			this.filePathDeleteItem = newFileName;
		}
		,
		setAdditionalRenameRequestParameters : function(requestParameters)
		{
			this.additionalRenameRequestParameters = requestParameters;
		}
		,
		setAdditionalDeleteRequestParameters : function(requestParameters)
		{
			this.additionalDeleteRequestParameters = requestParameters;
		}
		,setRenameAllowed : function(renameAllowed)
		{
			this.renameAllowed = renameAllowed;			
		}
		,
		setDeleteAllowed : function(deleteAllowed)
		{
			this.deleteAllowed = deleteAllowed;	
		}
		,setMaximumDepth : function(maxDepth)
		{
			this.maximumDepth = maxDepth;	
		}
		,setMessageMaximumDepthReached : function(newMessage)
		{
			this.messageMaximumDepthReached = newMessage;
		}
		,	
		setImageFolder : function(path)
		{
			this.imageFolder = path;	
		}
		,
		setFolderImage : function(imagePath)
		{
			this.folderImage = imagePath;			
		}
		,
		setPlusImage : function(imagePath)
		{
			this.plusImage = imagePath;				
		}
		,
		setMinusImage : function(imagePath)
		{
			this.minusImage = imagePath;			
		}
		,		
		setTreeId : function(idOfTree)
		{
			this.idOfTree = idOfTree;			
		}	
		,
		expandAll : function()
		{
			var menuItems = document.getElementById(this.idOfTree).getElementsByTagName('LI');
			for(var no=0;no<menuItems.length;no++){
				var subItems = menuItems[no].getElementsByTagName('UL');
				if(subItems.length>0 && subItems[0].style.display!='block'){
					JSTreeObj.showHideNode(false,menuItems[no].id);
				}			
			}
		}	
		,
		collapseAll : function()
		{
			var menuItems = document.getElementById(this.idOfTree).getElementsByTagName('LI');
			for(var no=0;no<menuItems.length;no++){
				var subItems = menuItems[no].getElementsByTagName('UL');
				if(subItems.length>0 && subItems[0].style.display=='block'){
					JSTreeObj.showHideNode(false,menuItems[no].id);
				}			
			}		
		}	
		,
		/*
		Find top pos of a tree node
		*/
		getTopPos : function(obj){
			var top = obj.offsetTop/1;
			while((obj = obj.offsetParent) != null){
				if(obj.tagName!='HTML')top += obj.offsetTop;
			}			
			if(document.all)top = top/1 + 13; else top = top/1 + 4;		
			return top;
		}
		,	
		/*
		Find left pos of a tree node
		*/
		getLeftPos : function(obj){
			var left = obj.offsetLeft/1 + 1;
			while((obj = obj.offsetParent) != null){
				if(obj.tagName!='HTML')left += obj.offsetLeft;
			}
	  			
			if(document.all)left = left/1 - 2;
			return left;
		}	
			
		,
		showHideNode : function(e,inputId)
		{
			if( inputId )
            {
				if( !document.getElementById( inputId ) ) return ;
				thisNode = document.getElementById( inputId ).getElementsByTagName( 'IMG' )[0] ; 
			} else
            {
				thisNode = this;
				if( this.tagName == 'A' ) thisNode = this.parentNode.getElementsByTagName( 'IMG' )[ 0 ] ;	
			}
            
			if( thisNode.style.visibility=='hidden' ) return ;		
			var parentNode = thisNode.parentNode ;
			// inputId = parentNode.id.replace(/[^0-9]/g,''); moved away from numerical ids
			inputId = parentNode.id ;
			if( thisNode.src.indexOf( JSTreeObj.plusImage ) >= 0 )
            {
				thisNode.src = thisNode.src.replace( JSTreeObj.plusImage, JSTreeObj.minusImage ) ;
				var ul = parentNode.getElementsByTagName( 'UL' )[ 0 ] ;
				ul.style.display='block';
				/*
				if( !initExpandedNodes ) initExpandedNodes = ',' ;
				if( initExpandedNodes.indexOf( ',' + inputId + ',' ) < 0 )
                    initExpandedNodes = initExpandedNodes + inputId + ',' ;
                */
                // check if any children are there AAA!!!
                var li = ul.getElementsByTagName( 'LI' ) ;
                if ( li == null || li.length == 0 )
                                    JSTreeObj.readLevel( inputId ) ;
			} else
            {
				thisNode.src = thisNode.src.replace( JSTreeObj.minusImage, JSTreeObj.plusImage ) ;
				parentNode.getElementsByTagName( 'UL' )[ 0 ].style.display = 'none' ;
				// initExpandedNodes = initExpandedNodes.replace( ',' + inputId, '' ) ;
			}	
			// AAA!!! switched off JSTreeObj.Set_Cookie( 'expandedNodes', initExpandedNodes, 500 ) ;			
			return false;						
		}
		,
		/* Initialize drag */
		initDrag : function(e)
		{
			return false ; // AAA!!! disables drag'n'drop
            var node2drag = this.parentNode ; // will not drag R/O nodes
            if ( node2drag.getAttribute( 'rw' ) != 'rw' ) return false ; 
            
            if(document.all)e = event;	
			
			var subs = JSTreeObj.floatingContainer.getElementsByTagName('LI');
			if(subs.length>0){
				if(JSTreeObj.dragNode_sourceNextSib){
					JSTreeObj.dragNode_parent.insertBefore(JSTreeObj.dragNode_source,JSTreeObj.dragNode_sourceNextSib);
				}else{
					JSTreeObj.dragNode_parent.appendChild(JSTreeObj.dragNode_source);
				}					
			}
			
			JSTreeObj.dragNode_source = this.parentNode;
			JSTreeObj.dragNode_parent = this.parentNode.parentNode;
			JSTreeObj.dragNode_sourceNextSib = false;

			
			if(JSTreeObj.dragNode_source.nextSibling)JSTreeObj.dragNode_sourceNextSib = JSTreeObj.dragNode_source.nextSibling;
			JSTreeObj.dragNode_destination = false;
			JSTreeObj.dragDropTimer = 0;
			JSTreeObj.timerDrag();
			return false;
		}
		,
		timerDrag : function()
		{	
			if(this.dragDropTimer>=0 && this.dragDropTimer<10){
				this.dragDropTimer = this.dragDropTimer + 1;
				setTimeout('JSTreeObj.timerDrag()',20);
				return;
			}
			if(this.dragDropTimer==10)
			{
				JSTreeObj.floatingContainer.style.display='block';
				JSTreeObj.floatingContainer.appendChild(JSTreeObj.dragNode_source);	
			}
		}
		,
		moveDragableNodes : function(e)
		{
			if(JSTreeObj.dragDropTimer<10)return;
			if(document.all)e = event;
			dragDrop_x = e.clientX/1 + 5 + document.body.scrollLeft;
			dragDrop_y = e.clientY/1 + 5 + document.documentElement.scrollTop;	
					
			JSTreeObj.floatingContainer.style.left = dragDrop_x + 'px';
			JSTreeObj.floatingContainer.style.top = dragDrop_y + 'px';
			
			var thisObj = this;
			if(thisObj.tagName=='A' || thisObj.tagName=='IMG')thisObj = thisObj.parentNode;

			JSTreeObj.dragNode_noSiblings = false;
			var tmpVar = thisObj.getAttribute('noSiblings');
			if(!tmpVar)tmpVar = thisObj.noSiblings;
			if(tmpVar=='true')JSTreeObj.dragNode_noSiblings=true;
					
			if(thisObj && thisObj.id)
			{
				JSTreeObj.dragNode_destination = thisObj;
				var img = thisObj.getElementsByTagName('IMG')[1];
				var tmpObj= JSTreeObj.dropTargetIndicator;
				tmpObj.style.display='block';
				
				var eventSourceObj = this;
				if(JSTreeObj.dragNode_noSiblings && eventSourceObj.tagName=='IMG')eventSourceObj = eventSourceObj.nextSibling;
				
				var tmpImg = tmpObj.getElementsByTagName('IMG')[0];
				if(this.tagName=='A' || JSTreeObj.dragNode_noSiblings){
					tmpImg.src = tmpImg.src.replace('ind1','ind2');	
					JSTreeObj.insertAsSub = true;
					tmpObj.style.left = (JSTreeObj.getLeftPos(eventSourceObj) + JSTreeObj.indicator_offsetX_sub) + 'px';
				}else{
					tmpImg.src = tmpImg.src.replace('ind2','ind1');
					JSTreeObj.insertAsSub = false;
					tmpObj.style.left = (JSTreeObj.getLeftPos(eventSourceObj) + JSTreeObj.indicator_offsetX) + 'px';
				}
				
				
				tmpObj.style.top = (JSTreeObj.getTopPos(thisObj) + JSTreeObj.indicator_offsetY) + 'px';
			}
			
			return false;
			
		}
		,
		dropDragableNodes:function()
		{
			if(JSTreeObj.dragDropTimer<10){				
				JSTreeObj.dragDropTimer = -1;
				return;
			}
			var showMessage = false;
			if(JSTreeObj.dragNode_destination){	// Check depth
				var countUp = JSTreeObj.dragDropCountLevels(JSTreeObj.dragNode_destination,'up');
				var countDown = JSTreeObj.dragDropCountLevels(JSTreeObj.dragNode_source,'down');
				var countLevels = countUp/1 + countDown/1 + (JSTreeObj.insertAsSub?1:0);		
				
				if(countLevels>JSTreeObj.maximumDepth){
					JSTreeObj.dragNode_destination = false;
					showMessage = true; 	// Used later down in this function
				}
			}
			//AAA!!! begin changes
			var dest = JSTreeObj.dragNode_destination ;
            var parent = JSTreeObj.getParentFolder( dest ) ;
            var destRW = dest && dest.getAttribute( 'rw' ) == 'rw' ;
            var parentRW = parent && parent.getAttribute( 'rw' ) == 'rw' ;
            var validMove = false ;
            var sourceParentNode = JSTreeObj.dragNode_parent.parentNode ;
            var destParentNode = JSTreeObj.dragNode_destination.parentNode.parentNode ;
            
            // check validity of the move:
            // 1. the destination must be not null and on the same level
            if ( dest && sourceParentNode == destParentNode ) 
            // 2. in case of insertion as a subnode
              if ( JSTreeObj.insertAsSub ) 
            //   2a. the destination must be a folder 
            //   2b. it must be a writable folder
                { if ( destRW && dest.className == 'folder' )  validMove = 'true' ; }
            // 3. in case of insertion "next to"
            //   3a. the destination's parent must be writable
                  else if ( parentRW ) validMove = 'true' ;
            
			if ( validMove ) {			
				if( JSTreeObj.insertAsSub && JSTreeObj.dragNode_destination.className == 'folder' ){
					//AAA!!! end changes
					var uls = JSTreeObj.dragNode_destination.getElementsByTagName('UL');
					if(uls.length>0){
						ul = uls[0];
						ul.style.display='block';
						
						var lis = ul.getElementsByTagName('LI');
                        var parentId = ul.parentNode.id.replace(/[^0-9]/g,'' ) ;  //AAA!!!
                        var moved = JSTreeObj.dragNode_source ; //AAA!!!
                        var sourceParent = JSTreeObj.dragNode_parent.parentNode.id.replace( /[^0-9]/g,'' ) ; //AAA!!!
                        // requestMove( movedId, "inside", parentId, sourceParent, parentId ) ; //AAA!!!
                        var nextId = 0 ; //

						if(lis.length>0){	// Sub elements exists - drop dragable node before the first one
							ul.insertBefore(JSTreeObj.dragNode_source,lis[0]);
							nextId = lis[0].id.replace( /[^0-9]/g,'' ) ; // AAA!!!
						}else {	// No sub exists - use the appendChild method - This line should not be executed unless there's something wrong in the HTML, i.e empty <ul>
							    ul.appendChild(JSTreeObj.dragNode_source);	
						}
						JSTreeObj.moveNode( moved, nextId ) ; //AAA!!!
					}else{
                        alert( "Empty level - no UL element - this can't happen." ) ; //AAA!!!
						var ul = document.createElement('UL');
						ul.style.display='block';
						JSTreeObj.dragNode_destination.appendChild(ul);
						ul.appendChild(JSTreeObj.dragNode_source);
					}
					var img = JSTreeObj.dragNode_destination.getElementsByTagName('IMG')[0];					
					img.style.visibility='visible';
					img.src = img.src.replace(JSTreeObj.plusImage,JSTreeObj.minusImage);					
					
					
				}else{
                       var next = 0 ; //AAA!!!
					if (JSTreeObj.dragNode_destination.nextSibling){
						var nextSib = JSTreeObj.dragNode_destination.nextSibling ;
                        next = nextSib.id.replace(/[^0-9]/g,'');  //AAA!!!
						nextSib.parentNode.insertBefore(JSTreeObj.dragNode_source,nextSib);
					}else{
						JSTreeObj.dragNode_destination.parentNode.appendChild(JSTreeObj.dragNode_source);
					} //AAA!!! 4 lines inserted
                    var moved = JSTreeObj.dragNode_source ;
                    var sourceParent = JSTreeObj.dragNode_parent.parentNode.id.replace( /[^0-9]/g,'' ) ;
                    var destParent = JSTreeObj.dragNode_destination.parentNode.parentNode.id.replace( /[^0-9]/g,'' ) ;
//                    requestMove( JSTreeObj, movedId, "before", next, sourceParent, destParent ) ;  
                    JSTreeObj.moveNode( moved, next ) ;  
				}	
				/* Clear parent object */
				var tmpObj = JSTreeObj.dragNode_parent;
				var lis = tmpObj.getElementsByTagName('LI');
				if(lis.length==0){
					var img = tmpObj.parentNode.getElementsByTagName('IMG')[0];
					img.style.visibility='hidden';	// Hide [+],[-] icon
					tmpObj.parentNode.removeChild(tmpObj);						
				}
				
			}else{
				// Putting the item back to it's original location
				
				if(JSTreeObj.dragNode_sourceNextSib){
					JSTreeObj.dragNode_parent.insertBefore(JSTreeObj.dragNode_source,JSTreeObj.dragNode_sourceNextSib);
				}else{
					JSTreeObj.dragNode_parent.appendChild(JSTreeObj.dragNode_source);
				}			
					
			}
			JSTreeObj.dropTargetIndicator.style.display='none';		
			JSTreeObj.dragDropTimer = -1;	
			if(showMessage && JSTreeObj.messageMaximumDepthReached)alert(JSTreeObj.messageMaximumDepthReached);
		}
		,
		createDropIndicator : function()
		{
			this.dropTargetIndicator = document.createElement('DIV');
			this.dropTargetIndicator.style.position = 'absolute';
			this.dropTargetIndicator.style.display='none';			
			var img = document.createElement('IMG');
			img.src = this.imageFolder + 'dragDrop_ind1.gif';
			img.id = 'dragDropIndicatorImage';
			this.dropTargetIndicator.appendChild(img);
			document.body.appendChild(this.dropTargetIndicator);
			
		}
		,
		dragDropCountLevels : function(obj,direction,stopAtObject){
			var countLevels = 0;
			if(direction=='up'){
				while(obj.parentNode && obj.parentNode!=stopAtObject){
					obj = obj.parentNode;
					if(obj.tagName=='UL')countLevels = countLevels/1 +1;
				}		
				return countLevels;
			}	
			
			if(direction=='down'){ 
				var subObjects = obj.getElementsByTagName('LI');
				for(var no=0;no<subObjects.length;no++){
					countLevels = Math.max(countLevels,JSTreeObj.dragDropCountLevels(subObjects[no],"up",obj));
				}
				return countLevels;
			}	
		}		
		,
		cancelEvent : function()
		{
			return false;	
		}
		,
		cancelSelectionEvent : function()
		{
			
			if(JSTreeObj.dragDropTimer<10)return true;
			return false;	
		}
		,getNodeOrders : function(initObj,saveString)
		{
			
			if(!saveString)var saveString = '';
			if(!initObj){
				initObj = document.getElementById(this.idOfTree);

			}
			var lis = initObj.getElementsByTagName('LI');

			if(lis.length>0){
				var li = lis[0];
				while(li){
					if(li.id){
						if(saveString.length>0)saveString = saveString + ',';
						var numericID = li.id.replace(/[^0-9]/gi,'');
						if(numericID.length==0)numericID='A';
						var numericParentID = li.parentNode.parentNode.id.replace(/[^0-9]/gi,'');
						if(numericID!='0'){
							saveString = saveString + numericID;
							saveString = saveString + '-';
							
							
							if(li.parentNode.id!=this.idOfTree)saveString = saveString + numericParentID; else saveString = saveString + '0';
						}
						var ul = li.getElementsByTagName('UL');
						if(ul.length>0){
							saveString = this.getNodeOrders(ul[0],saveString);	
						}	
					}			
					li = li.nextSibling;
				}
			}

			if(initObj.id == this.idOfTree){
				return saveString;
							
			}
			return saveString;
		}
		,highlightItem : function(inputObj,e)
		{
			if(JSTreeObj.currentlyActiveItem)JSTreeObj.currentlyActiveItem.className = '';
			this.className = 'highlightedNodeItem';
			JSTreeObj.currentlyActiveItem = this;
		}
		,
		removeHighlight : function()
		{
			if(JSTreeObj.currentlyActiveItem)JSTreeObj.currentlyActiveItem.className = '';
			JSTreeObj.currentlyActiveItem = false;
		}
		,
		hasSubNodes : function(obj)
		{
			var subs = obj.getElementsByTagName('LI');
			if(subs.length>0)return true;
			return false;	
		}
		,
		deleteItem : function(obj1,obj2)
		{
			var message = 'Click OK to delete item ' + obj2.innerHTML;
			if(this.hasSubNodes(obj2.parentNode)) message = message + ' and it\'s sub nodes';
			if(confirm(message)){
				this.__deleteItem_step2(obj2.parentNode);	// Sending <LI> tag to the __deleteItem_step2 method	
			}
			
		}
		,
		__refreshDisplay : function(obj)
		{
			if(this.hasSubNodes(obj))return;

			var img = obj.getElementsByTagName('IMG')[0];
			img.style.visibility = 'hidden';	
		}
		,
		__deleteItem_step2 : function(obj)
		{

			var saveString = obj.id.replace(/[^0-9]/gi,'');
			
			var lis = obj.getElementsByTagName('LI');
			for(var no=0;no<lis.length;no++){
				saveString = saveString + ',' + lis[no].id.replace(/[^0-9]/gi,'');
			}
			
			// Creating ajax object and send items
			var ajaxIndex = JSTreeObj.ajaxObjects.length;
			JSTreeObj.ajaxObjects[ajaxIndex] = new sack();
			JSTreeObj.ajaxObjects[ajaxIndex].method = "GET";
			JSTreeObj.ajaxObjects[ajaxIndex].setVar("deleteIds", saveString);
			JSTreeObj.__addAdditionalRequestParameters(JSTreeObj.ajaxObjects[ajaxIndex], JSTreeObj.additionalDeleteRequestParameters);
			JSTreeObj.ajaxObjects[ajaxIndex].requestFile = JSTreeObj.filePathDeleteItem;	// Specifying which file to get
			JSTreeObj.ajaxObjects[ajaxIndex].onCompletion = function() { JSTreeObj.__deleteComplete(ajaxIndex,obj); } ;	// Specify function that will be executed after file has been found
			JSTreeObj.ajaxObjects[ajaxIndex].runAJAX();		// Execute AJAX function				
			
			
		}
		,
		__deleteComplete : function(ajaxIndex,obj)
		{
			if(this.ajaxObjects[ajaxIndex].response!='OK'){
				alert('ERROR WHEN TRYING TO DELETE NODE: ' + this.ajaxObjects[ajaxIndex].response); 	// Rename failed
			}else{
				var parentRef = obj.parentNode.parentNode;
				obj.parentNode.removeChild(obj);
				this.__refreshDisplay(parentRef);
				
			}			
			
		}
		,
		__renameComplete : function(ajaxIndex)
		{
			if(this.ajaxObjects[ajaxIndex].response!='OK'){
				alert('ERROR WHEN TRYING TO RENAME NODE: ' + this.ajaxObjects[ajaxIndex].response); 	// Rename failed
			}
		}
		,
		__saveTextBoxChanges : function(e,inputObj)
		{
			if(!inputObj && this)inputObj = this;
			if(document.all)e = event;
			if(e.keyCode && e.keyCode==27){
				JSTreeObj.__cancelRename(e,inputObj);
				return;
			}
			inputObj.style.display='none';
			inputObj.nextSibling.style.visibility='visible';
			if(inputObj.value.length>0){
				inputObj.nextSibling.innerHTML = inputObj.value;	
				// Send changes to the server.
				if (JSTreeObj.renameState != JSTreeObj.RENAME_STATE_BEGIN) {
					return;
				}
				JSTreeObj.renameState = JSTreeObj.RENAME_STATE_REQUEST_SENDED;

				var ajaxIndex = JSTreeObj.ajaxObjects.length;
				JSTreeObj.ajaxObjects[ajaxIndex] = new sack();
				JSTreeObj.ajaxObjects[ajaxIndex].method = "GET";
				JSTreeObj.ajaxObjects[ajaxIndex].setVar("renameId", inputObj.parentNode.id.replace(/[^0-9]/gi,''));
				JSTreeObj.ajaxObjects[ajaxIndex].setVar("newName", inputObj.value);
				JSTreeObj.__addAdditionalRequestParameters(JSTreeObj.ajaxObjects[ajaxIndex], JSTreeObj.additionalRenameRequestParameters);
				JSTreeObj.ajaxObjects[ajaxIndex].requestFile = JSTreeObj.filePathRenameItem;	// Specifying which file to get
				JSTreeObj.ajaxObjects[ajaxIndex].onCompletion = function() { JSTreeObj.__renameComplete(ajaxIndex); } ;	// Specify function that will be executed after file has been found
				JSTreeObj.ajaxObjects[ajaxIndex].runAJAX();		// Execute AJAX function		
							
				
				
			}
		}
		,
		__cancelRename : function(e,inputObj)
		{
			JSTreeObj.renameState = JSTreeObj.RENAME_STATE_CANCELD;
			if(!inputObj && this)inputObj = this;
			inputObj.value = JSTreeObj.helpObj.innerHTML;
			inputObj.nextSibling.innerHTML = JSTreeObj.helpObj.innerHTML;
			inputObj.style.display = 'none';
			inputObj.nextSibling.style.visibility = 'visible';
		}
		,
		__renameCheckKeyCode : function(e)
		{
			if(document.all)e = event;
			if(e.keyCode==13){	// Enter pressed
				JSTreeObj.__saveTextBoxChanges(false,this);	
			}	
			if(e.keyCode==27){	// ESC pressed
				JSTreeObj.__cancelRename(false,this);
			}
		}
		,
		__createTextBox : function(obj)
		{
			var textBox = document.createElement('INPUT');
			textBox.className = 'folderTreeTextBox';
			textBox.value = obj.innerHTML;
			obj.parentNode.insertBefore(textBox,obj);	
			textBox.id = 'textBox' + obj.parentNode.id.replace(/[^0-9]/gi,'');
			textBox.onblur = this.__saveTextBoxChanges;	
			textBox.onkeydown = this.__renameCheckKeyCode;
			this.__renameEnableTextBox(obj);
		}
		,
		__renameEnableTextBox : function(obj)
		{
			JSTreeObj.renameState = JSTreeObj.RENAME_STATE_BEGIN;
			obj.style.visibility = 'hidden';
			obj.previousSibling.value = obj.innerHTML;
			obj.previousSibling.style.display = 'inline';	
			obj.previousSibling.select();
		}
		,
		initTree : function()
		{
			JSTreeObj = this;
			JSTreeObj.createDropIndicator();
			document.documentElement.onselectstart = JSTreeObj.cancelSelectionEvent;
			document.documentElement.ondragstart = JSTreeObj.cancelEvent;
			document.documentElement.onmousedown = JSTreeObj.removeHighlight;
			
			/* Creating help object for storage of values */
			this.helpObj = document.createElement('DIV');
			this.helpObj.style.display = 'none';
			document.body.appendChild(this.helpObj);
			
			/* Create context menu */
			try{ //AAA!!!
				 /* Creating menu model for the context menu, i.e. the datasource */
                 initMenu( this ) ; // call to a function placed in JSP code				 
				 window.refToDragDropTree = this;
					
				 this.contextMenu = new DHTMLGoodies_contextMenu();
				 this.contextMenu.setWidth(120);
				 referenceToDHTMLSuiteContextMenu = this.contextMenu;
				}catch(e){
					
				}
					
			var nodeId = 0;
			var dhtmlgoodies_tree = document.getElementById(this.idOfTree);
			var menuItems = dhtmlgoodies_tree.getElementsByTagName('LI');	// Get an array of all menu items
			for( var no=0 ; no < menuItems.length ; no++ )
                   this.initNode( menuItems[ no ] ) ;
			
			/* initExpandedNodes = this.Get_Cookie( 'expandedNodes' ) ; //AAA!!!
			if(initExpandedNodes){
				var nodes = initExpandedNodes.split(',');
				for(var no=0;no<nodes.length;no++){
					if(nodes[no])this.showHideNode(false,nodes[no]);	
				}			
			}			
			*/
			
			document.documentElement.onmousemove = JSTreeObj.moveDragableNodes;	
			document.documentElement.onmouseup = JSTreeObj.dropDragableNodes;
            
            // this.readLevel( "node._root_.0" ) ; // this is id of the root node            
		}
		,
        
        initNode : function( node ) 
        {
                          // No children var set ?
          var noChildren = false;
          var tmpVar = node.getAttribute( 'noChildren' ) ;
          if ( !tmpVar ) tmpVar = node.noChildren ;
          if( tmpVar=='true' ) noChildren = true ;
                // No drag var set ?
          var noDrag = false ;
          var tmpVar = node.getAttribute( 'noDrag' ) ;
          if( !tmpVar ) tmpVar = node.noDrag ;
          if( tmpVar == 'true' ) noDrag = true ;
                         
//          var aTag = node.getElementsByTagName( 'A' )[0] ;
//          if( !noDrag ) aTag.onmousedown = JSTreeObj.initDrag ;
//          aTag.onmousemove = JSTreeObj.moveDragableNodes ;

          var subItems = node.getElementsByTagName( 'IMG' ) ;
          var img = subItems[ 0 ] ;
          img.onclick = JSTreeObj.showHideNode ;

          var folderImg = subItems[ 1 ] ;
          if( !noDrag ) folderImg.onmousedown = JSTreeObj.initDrag ;
          folderImg.onmousemove = JSTreeObj.moveDragableNodes ;
                
          if( this.contextMenu )
          {
             this.contextMenu.attachToElement( folderImg, false, this.menuModel ) ;
          }
          this.addEvent( folderImg, 'contextmenu', this.highlightItem ) ;
        },
        
        addChildren : function( parentNum, html )
        {
          if ( findError( html ) ) return ; 
          pos = html.indexOf( "++OK" ) ;
          html = html.substring( pos + 5 ) ;
        	
          // find parent object
          var parent = null, node = null ;
          var tree = document.getElementById( this.idOfTree ) ;
          var menuItems = tree.getElementsByTagName('LI');    // Get an array of all menu items

          if ( parentNum == 0 ) // add to the root
            parent = document.getElementById( "tree0" ) ;
            else { node = this.findNodeByNum( parentNum ) ;
                   parent = node.getElementsByTagName( 'UL' )[ 0 ] ;
                 }

          // add inner HTML
          parent.innerHTML = html ;
          // re-init new nodes
          var nodes = parent.getElementsByTagName( 'LI' ) ;
            for ( var no = 0 ; no < nodes.length ; no++ )
                                  this.initNode( nodes[ no ] ) ;
        },

        findNodeByNum : function ( nodeId )
        {
         /* This should not be needed as moved from numbers to full ids	
          var tree = document.getElementById( this.idOfTree ) ;
          var nodes = tree.getElementsByTagName( 'LI' ) ;
          for ( var no = 0 ; no < nodes.length ; no++ )  
             if ( nodes[ no ].id.replace(/[^0-9]/gi,'') == nodeNum )
                                  return nodes[ no ] ;
          return null ;
         */
         return document.getElementById( nodeId ) ;	
        },
        
        readParentLevel : function( nodeId )
        {
          var node = JSTreeObj.findNodeByNum( nodeId ) ;
          var parent = JSTreeObj.getParentNodeId( node ) ;
          JSTreeObj.readLevel( parent ) ;
        },

        readLevel : function( nodeId )
        {
         var node = document.getElementById( nodeId ) ;
         var site = node.getAttribute( "site" ) ;
         var nodeNum = nodeId.replace(/[^0-9]/gi,'') ;
       
         var request = new sack() ;
         request.requestFile = ajaxAddress + "?ar_action=readLevel&ar_site=" + site + "&ar_node=" + nodeNum ;
         if ( nodeId.indexOf( "_root_" ) > 0 || nodeNum == 0 ) request.requestFile += "&ar_isRoot=y" ;
         var tree = this ; 
         request.onCompletion = function() { tree.addChildren( nodeId, request.response ) ; } ;
         request.runAJAX() ;  
        },
        
		__addAdditionalRequestParameters : function(ajax, parameters)
		{
			for (var parameter in parameters) {
				ajax.setVar(parameter, parameters[parameter]);
			}
		},
    
    //==========================================================================
    //            Added menu functions
    //==========================================================================    
                
        newFolder : function( obj1, obj2 )
        {
          var parentNode = obj2.parentNode ;    // Reference to the <li> tag.
          var inputId = JSTreeObj.getNodeId( obj2 ) ;
          if ( parentNode.className != 'folder' ) 
               inputId = JSTreeObj.getParentNodeId( obj2 ) ;
          JSTreeObj.requestPropertiesDialog( inputId, "newfolder" )          
        },
        
        refreshItem : function( obj1, obj2 )
        {
          var parentNode = obj2.parentNode ;    // Reference to the <li> tag.
          var inputId = JSTreeObj.getNodeId( obj2 ) ;
          if ( parentNode.className != 'folder' ) 
               inputId = JSTreeObj.getParentNodeId( obj2 ) ;
          JSTreeObj.readLevel( inputId ) ;
        },
        
        deleteItem : function( obj1, obj2 )
        {
          var inputId = JSTreeObj.getNodeId( obj2 ) ;
          var parentId = JSTreeObj.getParentNodeId( obj2 ) ;
                    
          if ( confirm( JSTreeObj.Q_DELETE ) ) 
          {
            var request = new sack() ;
            request.requestFile = ajaxAddress + "?ar_node=" + inputId + "&ar_action=deleteNode" ;
            request.onCompletion = function()
                      { 
                        var str = request.response ;
                        if ( findError( str ) ) return ; 
                        JSTreeObj.readLevel( parentId ) ;
                      } ;
            request.runAJAX() ;  
          }
        },
                
        readProperties : function( obj1, obj2 )
        {
          var inputId = JSTreeObj.getNodeId( obj2 ) ;
          JSTreeObj.requestPropertiesDialog( inputId, "read" )          
        },

        requestPropertiesDialog : function( nodeId, action )
        {
          var node = document.getElementById( nodeId ) ;
          var site = node.getAttribute( "site" ) ;
          var nodeNum = nodeId.replace(/[^0-9]/gi,'') ;
          if ( nodeNum == 0 ) return ;
          
          messageObj.nodeObject = node ;
          var request = new sack() ;
          request.requestFile = ajaxAddress + "?ar_action=getProperties&ar_site=" + site + "&ar_node=" + nodeNum ;
          if ( nodeId.indexOf( "_root_" ) > 0 || nodeNum == 0 ) request.requestFile += "&ar_isRoot=y" ;
          request.onCompletion = function()
                    { 
                      var str = request.response ;
                      if ( findError( str ) ) return ; 
                      else str = str.substr( str.indexOf( "++OK" ) + 5 ) ;
                      messageObj.setHtmlContent( str ) ;
                      messageObj.setCssClassMessageBox( false ) ;
                      messageObj.setSize( 650, 410 ) ;
                      messageObj.setShadowDivVisible( true ) ; // Enable shadow for these boxes
                      messageObj.display() ;
                  	  accessChanged() ; // Enable/disable access fields, depending on access type
                    } ;
          request.runAJAX() ;  
        },
        
        moveNode : function( node, before )
        {
          var site = node.getAttribute( "site" ) ;
          var nodeNum = node.id.replace(/[^0-9]/gi,'') ;
          if ( nodeNum == 0 ) return ;
            
          messageObj.nodeObject = node ;
          var request = new sack() ;
          request.requestFile = ajaxAddress + "?ar_action=moveNode&ar_site=" + site + "&ar_node=" + nodeNum + "&ar_before=" + before;
          if ( node.id.indexOf( "_root_" ) > 0 || nodeNum == 0 ) request.requestFile += "&ar_isRoot=y" ;
          request.onCompletion = function()
                      { 
                        var str = request.response ;
                        if ( findError( str ) ) return ; 
                        JSTreeObj.refreshItem( null, messageObj.nodeObject ) ;
                      } ;
          request.runAJAX() ;  
        },
        
        getNodeId : function( obj )
        { 
          if ( obj.tagName != 'LI' )
           if ( obj.id == 'tree0' ) return "node._root_.0" ; 
              else obj = obj.parentNode ;
          // return obj.id.replace( /[^0-9]/g, '' ) ; moved away from using node numbers
          return obj.id ;
        },
        
        getParentNodeId : function( obj )
        {
          if ( obj.tagName != 'LI' )
           if ( obj.id == 'tree0' ) return 0 ; 
             else obj = obj.parentNode ;
          obj = obj.parentNode ;
          return JSTreeObj.getNodeId( obj ) ;
        },

        getParentFolder : function( obj )
        {
          obj = obj.parentNode ;
          if ( obj.tagName != 'LI' )
           if ( obj.id == 'tree0' ) return null ; 
             else obj = obj.parentNode ;
           if ( obj.id == 'tree0' ) return null ; 
           return obj ;
        }
        
	}
  
    // A couple of functions related to the properties dialog
    
    function onPropertiesCloseClick()
    {
      messageObj.close();
    }
	
	function onPropertiesOKClick()
	{
	  var nodeNum = $( "ar_propNodeId" ).value.replace(/[^0-9]/gi,'') ;
	  var isRoot = $( "ar_propNodeId" ).value.indexOf( "_root_" ) > 0 ;
	  var site = $( "ar_propNodeSite" ).value ;
	  var str = "ar_node=" + nodeNum + "&ar_action=updateNode" + "&ar_site=" + site ;
	  if ( isRoot ) str += "&ar_isRoot=y"  ;
          if ( $( "ar_access" ).checked ) str += "&ar_access=on" ;
	  str += packText( $( "ar_fname" ) ) ;
	  str += packText( $( "ar_flabel" ) ) ;
	  str += packText( $( "ar_ftitle" ) ) ;
	  str += packText( $( "ar_userr" ) ) ;
	  str += packText( $( "ar_userw" ) ) ;
	  str += packText( $( "ar_owners" ) ) ;
	  str += packText( $( "ar_groupr" ) ) ;
	  str += packText( $( "ar_groupw" ) ) ;
	  messageObj.close();
      var request = new sack() ;
      request.requestFile = ajaxAddress + "?" + str ;
      request.onCompletion = function()
                { 
                  var str = request.response ;
                  if ( findError( str ) ) return ; 
                  else JSTreeObj.refreshItem( null, messageObj.nodeObject ) ;
                } ;
      request.runAJAX() ;  
	}
	
	// packs values of a text input object
	function packText( obj )
	{
	  var str = "" ;
	  if ( obj != null && obj.value != null && obj.value != "" ) 
	    str = "&" + encodeURIComponent( obj.name ) + "=" + encodeURIComponent( obj.value ) ;
	  return str ;
	}
	
	function accessChanged()
	{ 
	  var aa = $( "ar_access" ).checked ; 
	  $( "ar_groupsr" ).disabled = aa ;
	  $( "ar_groupsw" ).disabled = aa ;
	  $( "ar_userr" ).disabled = aa ;
	  $( "ar_userw" ).disabled = aa ;
	  $( "ar_owners" ).disabled = aa ;
	} 

	function $( str )
	{
	  var aa = document.getElementById( str ) ;
	  return aa ;
	}



        
    