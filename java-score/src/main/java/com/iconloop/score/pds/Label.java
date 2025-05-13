package com.iconloop.score.pds;

import score.annotation.EventLog;
import score.annotation.Optional;

import java.math.BigInteger;

public interface Label {
    /**
     * Adds a new label with the given attributes.
     *
     * @param label_id The unique ID for the label.
     * @param name The name of the label.
     * @param public_key The public key associated with the label.
     * @param expire_at The expiration timestamp of the label in microseconds.
     * @param owner_sign The owner's signature authorizing the label creation.
     * @param category (Optional) The category of the label.
     * @param producer (Optional) The producer associated with the label.
     * @param producer_expire_at (Optional) The expiration timestamp for the producer in microseconds.
     * @param data (Optional) The cid of the content associated with the label.
     * @param data_size (Optional) The size of the data in bytes associated with the label.
     *
     * @implNote Must trigger the LabelAdded event when the label is added successfully.
     * @see #LabelAdded(String, String, String)
     */
    void add_label(String label_id,
                   String name,
                   String public_key,
                   BigInteger expire_at,
                   String owner_sign,
                   @Optional String category,
                   @Optional String producer,
                   @Optional BigInteger producer_expire_at,
                   @Optional String data,
                   @Optional BigInteger data_size);

    /**
     * Removes an existing label.
     *
     * @param label_id The ID of the label to be removed.
     * @param owner_sign The owner's signature authorizing the label removal.
     *
     * @implNote Must trigger the LabelRemoved event when the label is removed successfully.
     * @see #LabelRemoved(String)
     */
    void remove_label(String label_id,
                      String owner_sign);

    /**
     * Updates the attributes of an existing label.
     *
     * @param label_id The ID of the label to be updated.
     * @param owner_sign The owner's signature authorizing the update.
     * @param name (Optional) The new name of the label.
     * @param expire_at (Optional) The updated expiration timestamp of the label in microseconds.
     * @param category (Optional) The new category of the label.
     * @param producer (Optional) The new producer associated with the label.
     * @param producer_expire_at (Optional) The updated expiration timestamp for the producer in microseconds.
     *
     * @implNote Must trigger the LabelUpdated event when the label is updated successfully.
     * @see #LabelUpdated(String)
     */
    void update_label(String label_id,
                      String owner_sign,
                      @Optional String name,
                      @Optional BigInteger expire_at,
                      @Optional String category,
                      @Optional String producer,
                      @Optional BigInteger producer_expire_at);

    /**
     * Retrieves the details of a label.
     *
     * @param label_id The ID of the label to retrieve.
     *
     * @return A map containing the label's details as key-value pairs.
     *         Returns an empty map if the label is not found.
     */
    LabelInfo get_label(String label_id);

    /**
     * Retrieves the total count of labels that have been added.
     *
     * @return The total number of labels.
     */
    BigInteger get_label_count();

    /**
     * Adds data associated with a given label.
     *
     * @param label_id The ID of the label associated with the data.
     * @param data The cid of the content.
     * @param name The arbitrary name for the data.
     * @param size The size of the data in bytes.
     * @param producer_sign The producer's signature authorizing the data addition.
     *
     * @implNote Must trigger the LabelData event when the data is added successfully.
     * @see #LabelData(String, String)
     */
    void add_data(String label_id,
                  String data,
                  String name,
                  BigInteger size,
                  String producer_sign);

    /**
     * Retrieves a paginated list of data associated with the given label ID.
     *
     * @param label_id The label ID associated with the data.
     * @param offset The starting position of the data to retrieve.
     * @param limit (Optional) The maximum number of data to retrieve.
     *              If null, a default value or all remaining policies may be returned.
     *
     * @return A paginated result containing the list of data.
     */
    Page<DataInfo> get_data(String label_id,
                            BigInteger offset,
                            @Optional BigInteger limit);

    /**
     * Notifies when a new label is added.
     *
     * @param label_id The ID of the label.
     * @param owner The owner of the label.
     * @param producer The producer associated with the label.
     */
    @EventLog(indexed=3)
    void LabelAdded(String label_id, String owner, String producer);

    /**
     * Notifies when a label has been removed.
     *
     * @param label_id The ID of the label that was removed.
     */
    @EventLog(indexed=1)
    void LabelRemoved(String label_id);

    /**
     * Notifies when a label has been updated.
     *
     * @param label_id The ID of the label that was updated.
     */
    @EventLog(indexed=1)
    void LabelUpdated(String label_id);

    /**
     * Notifies when data is associated with a specific label.
     *
     * @param label_id The ID of the label associated with the data.
     * @param data The cid of the content.
     */
    @EventLog(indexed=2)
    void LabelData(String label_id, String data);
}
