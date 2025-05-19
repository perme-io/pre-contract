package com.iconloop.score.pds;

import score.annotation.EventLog;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

public interface Policy {
    /**
     * Adds a new policy with the given attributes.
     *
     * @param policy_id The unique ID for the policy.
     * @param label_id The label ID associated with the policy.
     * @param name The name of the policy.
     * @param consumer The consumer associated with the policy.
     * @param threshold The threshold of the policy.
     * @param owner_sign The owner's signature authorizing the policy creation.
     * @param expire_at (Optional) The expiration timestamp of the policy in microseconds.
     *                  If null, the policy expiration follows the label expiration timestamp.
     *
     * @implNote Must trigger the PolicyAdded event when the policy is added successfully.
     * @see #PolicyAdded(String, String, String)
     */
    void add_policy(String policy_id,
                    String label_id,
                    String name,
                    String consumer,
                    BigInteger threshold,
                    String owner_sign,
                    @Optional BigInteger expire_at);

    /**
     * Updates the expiration timestamp of an existing policy.
     *
     * @param policy_id The ID of the policy to be updated.
     * @param expire_at The updated expiration timestamp of the policy in microseconds.
     * @param owner_sign The owner's signature authorizing the policy update.
     *
     * @implNote Must trigger the PolicyUpdated event when the policy is updated successfully.
     * @see #PolicyUpdated(String)
     */
    void update_policy(String policy_id,
                       BigInteger expire_at,
                       String owner_sign);

    /**
     * Retrieves the details of a policy.
     *
     * @param policy_id The ID of the policy to retrieve.
     *
     * @return A map containing the policy's details as key-value pairs.
     *         Returns an empty map if the policy is not found.
     */
    PolicyInfo get_policy(String policy_id);

    /**
     * Checks if a policy with the given ID exists and is valid.
     *
     * @param policy_id The ID of the policy to check.
     *
     * @return A map containing the policy's details as key-value pairs.
     */
    Map<String, Object> check_policy(String policy_id);

    /**
     * Retrieves a page of policies associated with the given label ID.
     *
     * @param label_id The label ID associated with the policies.
     * @param offset The starting position (can be negative for end-relative indexing).
     * @param limit (Optional) The maximum number of items to return.
     *              If null, a default size or all remaining items may be returned.
     *
     * @return A paginated result containing the list of policies.
     */
    PageOfPolicy get_policies(String label_id,
                              int offset,
                              @Optional int limit);

    /**
     * Retrieves the total count of policies that have been added.
     *
     * @return The total number of policies.
     */
    BigInteger get_policy_count();

    /**
     * Notifies when a new policy is added.
     *
     * @param policy_id The ID of the added policy.
     * @param label_id The label ID associated with the policy.
     * @param consumer The consumer associated with the policy.
     */
    @EventLog(indexed=3)
    void PolicyAdded(String policy_id, String label_id, String consumer);

    /**
     * Notifies when a policy has been updated.
     *
     * @param policy_id The ID of the policy that has been updated.
     */
    @EventLog(indexed=1)
    void PolicyUpdated(String policy_id);
}
