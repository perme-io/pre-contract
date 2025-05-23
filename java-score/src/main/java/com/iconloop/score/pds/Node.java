package com.iconloop.score.pds;

import score.Address;
import score.annotation.EventLog;
import score.annotation.Optional;

import java.math.BigInteger;

public interface Node {
    /**
     * Adds a new node with the given attributes.
     *
     * @param peer_id The unique identifier for the node.
     * @param name The name of the node.
     * @param endpoint The network endpoint of the peer node.
     * @param owner The owner of the node.
     *
     * @implNote This method should be payable to accept the staking tokens.
     */
    void add_node(String peer_id,
                  String name,
                  String endpoint,
                  @Optional Address owner);

    /**
     * Updates the attributes of an existing node.
     *
     * @param peer_id The ID of the node to be updated.
     * @param owner (Optional) The updated owner of the node.
     * @param name (Optional) The updated name of the node.
     * @param endpoint (Optional) The updated network endpoint of the node.
     *
     * @implNote This method should be payable to accept the additional staking tokens.
     */
    void update_node(String peer_id,
                     @Optional Address owner,
                     @Optional String name,
                     @Optional String endpoint);

    /**
     * Removes an existing node.
     *
     * @param peer_id The ID of the node to be removed.
     */
    void remove_node(String peer_id);

    /**
     * Retrieves the details of a node.
     *
     * @param peer_id The ID of the node to retrieve.
     *
     * @return A map containing the node's details as key-value pairs.
     *         Returns an empty map if the node is not found.
     */
    NodeInfo get_node(String peer_id);

    /**
     * Retrieves all registered nodes.
     *
     * @return A list of all nodes. Each node is represented as a NodeInfo within the list.
     */
    NodeInfo[] all_nodes();

    /**
     * Sets the minimum stake value required for serving as a node.
     *
     * @param min_stake_for_serve The minimum stake value to be set.
     */
    void set_min_stake_value(BigInteger min_stake_for_serve);

    /**
     * Returns the current minimum stake value required for serving as a node.
     *
     * @return The minimum stake value.
     */
    BigInteger get_min_stake_value();

    /**
     * Sets the system threshold value.
     *
     * @param threshold The new threshold value to be set for the system.
     */
    void set_system_threshold(BigInteger threshold);

    /**
     * Retrieves the current system threshold value.
     *
     * @return The system threshold value as a BigInteger.
     */
    BigInteger get_system_threshold();

    /**
     * Notifies when a new node is added to the system.
     *
     * @param peer_id The unique identifier of the node that was added.
     * @param owner The owner address of the newly added node.
     * @param endpoint The network endpoint of the newly added node.
     */
    @EventLog(indexed=1)
    void NodeAdded(String peer_id, Address owner, String endpoint);

    /**
     * Notifies when the attributes of an existing node have been updated.
     *
     * @param peer_id The unique identifier of the node that was updated.
     * @param owner The updated owner of the node.
     * @param endpoint The updated network endpoint of the node.
     */
    @EventLog(indexed=1)
    void NodeUpdated(String peer_id, Address owner, String endpoint);

    /**
     * Notifies when an existing node is removed from the system.
     *
     * @param peer_id The unique identifier of the node that was removed.
     */
    @EventLog(indexed=1)
    void NodeRemoved(String peer_id);
}
