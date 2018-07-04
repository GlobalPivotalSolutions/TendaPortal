/*
 * 
 * Copyright (c) CSIRO Astronomy and Space Science
 * Author Arkadi.Kosmynin@csiro.au
 * 
 */
package au.csiro.cass.arch.sql;

public interface ServletDB 
{
	/**
	 *  Move node on the level.
	 *  
	 *  @param id int id of the node
	 *  @param before int id of the node to place this node before or 0 if to place last 
	 *  @param user String login name of user requesting the action
	 *  @param groups String space delimited list of user groups of the user
	 *
	 *  @return true if success, false if moving is not permitted
	 */
	public boolean moveNode( int id, int before, String user, String groups ) throws Exception ;

	/**
	 *  Delete node with its subtree.
	 *  
	 *  @param id int id of the node
	 *  @param user String login name of user requesting the action
	 *  @param groups String space delimited list of user groups of the user
	 *
	 *  @return true if success, false if deletion is not permitted
	 */
	public boolean deleteNode( int id, String user, String groups ) throws Exception ;

	/**
	 *  Read level of nodes defined either by parent id or id of one of the nodes.
	 *  
	 *  @param nodeId int id of one of the nodes on the level or -1
	 *  @param parentId ignored
	 *  @param user String login name of user requesting the action
	 *  @param groups String space delimited list of user groups of the user
	 *
	 *  @return array of index nodes where elements may be null if reading is not permitted
	 */
	public IndexNode[] readLevel( int nodeId, int parentId, String user, String groups ) throws Exception ;

	/**
	 *  Read node info.
	 *  
	 *  @param id int id of the node
	 *  @param user String login name of user requesting the action
	 *  @param groups String space delimited list of user groups of the user
	 *
	 *  @return IndexNode node or null if reading is not permited
	 */
	public IndexNode readNode( int id, String user, String groups ) throws Exception ;

	/**
	 *  Update node info.
	 *  
	 *  @param id int id of the node
	 *  @param info serialized node info
	 *  @param user String login name of user requesting the action
	 *  @param groups String space delimited list of user groups of the user
	 *
	 *  @return true if success, false if operation is not permitted
	 */
	public boolean updateNode( IndexNode node, String user, String groups ) throws Exception ;

	/**
	 *  Insert a new node.
	 *  
	 *  
	 *  @param parentId int id of level parent node
	 *  @param info serialized node info
	 *  @param user String login name of user requesting the action
	 *  @param groups String space delimited list of user groups of the user
	 *  
	 *  @return int id of the new node or -1 if operation is not permitted
	 */
	public int insertNode( int parentId, IndexNode node, String user, String groups ) throws Exception ;

	/**
	 *  Return base URL of the site.
     *
	 *  @param String site name
	 *  
	 *  @return String site base URL
	 */
	public String getURL( String site ) throws Exception ;

}
