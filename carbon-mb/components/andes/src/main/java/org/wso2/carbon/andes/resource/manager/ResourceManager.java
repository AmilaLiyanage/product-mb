package org.wso2.carbon.andes.resource.manager;

import com.gs.collections.api.iterator.MutableLongIterator;
import com.gs.collections.impl.list.mutable.primitive.LongArrayList;
import com.gs.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.wso2.andes.amqp.AMQPUtils;
import org.wso2.andes.kernel.Andes;
import org.wso2.andes.kernel.AndesChannel;
import org.wso2.andes.kernel.AndesException;
import org.wso2.andes.kernel.AndesMessage;
import org.wso2.andes.kernel.AndesMessageMetadata;
import org.wso2.andes.kernel.AndesMessagePart;
import org.wso2.andes.kernel.AndesUtils;
import org.wso2.andes.kernel.DestinationType;
import org.wso2.andes.kernel.DisablePubAckImpl;
import org.wso2.andes.kernel.FlowControlListener;
import org.wso2.andes.server.ClusterResourceHolder;
import org.wso2.carbon.andes.resource.manager.types.Destination;
import org.wso2.carbon.andes.resource.manager.types.Message;
import org.wso2.carbon.andes.resource.manager.types.Subscription;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public abstract class ResourceManager {
    /**
     * AndesChannel for this dead letter channel restore which implements flow control.
     */
    AndesChannel andesChannel;

    /**
     * Publisher Acknowledgements are disabled for this MBean hence using DisablePubAckImpl to drop any pub ack request
     * by Andes.
     */
    private final DisablePubAckImpl disablePubAck;

    /**
     * The message restore flowcontrol blocking state. If true message restore will be interrupted from dead letter
     * channel.
     */
    boolean restoreBlockedByFlowControl = false;

    public ResourceManager() throws AndesException {
        andesChannel = Andes.getInstance().createChannel(new FlowControlListener() {
            @Override
            public void block() {
                restoreBlockedByFlowControl = true;
            }

            @Override
            public void unblock() {
                restoreBlockedByFlowControl = false;
            }

            @Override
            public void disconnect(){
                // Do nothing. since its not applicable.
            }
        });

        disablePubAck = new DisablePubAckImpl();
    }

    /**
     * Gets the collection of destinations(queues/topics)
     *
     * @param keyword Search keyword for destination name. "*" will return all destinations. Destinations that
     *                <strong>contains</strong> the keyword will be returned.
     * @param offset  The offset value for the collection of destination.
     * @param limit   The number of records to return from the collection of destinations.
     * @return A list of destinations.
     */
    public abstract List<Destination> getDestinations(String keyword, int offset, int limit) throws AndesException;

    /**
     * Deletes all the destinations.
     */
    public abstract void deleteDestinations() throws AndesException;

    /**
     * Gets a destination.
     *
     * @param destinationName The name of the destination.
     * @return A destination object.
     */
    public abstract Destination getDestination(String destinationName) throws AndesException;

    /**
     * Creates a new destination.
     *
     * @param destinationName The name of the destination.
     * @param currentUsername The username of the user who creates the destination.
     * @return A newly created destination object.
     */
    public abstract Destination createDestination(String destinationName, String currentUsername) throws AndesException;

    /**
     * Deletes a destination.
     *
     * @param destinationName The name of the destination to be deleted.
     */
    public abstract void deleteDestination(String destinationName) throws AndesException;

    /**
     * Gets subscriptions belonging to a specific protocol type and destination type. The subscriptions can be filtered
     * by subscription name, destination name and whether they are active or not.
     *
     * @param subscriptionName The name of the subscription. If "*", all subscriptions are included. Else subscriptions
     *                         that <strong>contains</strong> the value are included.
     * @param destinationName  The name of the destination name. If "*", all destinations are included. Else
     *                         destinations that <strong>equals</strong> the value are included.
     * @param active           Filtering the subscriptions that are active or inactive.
     * @param offset           The starting index to return.
     * @param limit            The number of subscriptions to return.
     * @return A list subscriptions.
     */
    public abstract List<Subscription> getSubscriptions(String subscriptionName, String destinationName, boolean active,
                                                  int offset, int limit) throws AndesException;

    /**
     * Close/unsubscribe subscriptions forcefully belonging to a specific protocol type, destination type.
     *
     * @param destinationName The name of the destination to close/unsubscribe. If "*", all destinations are included.
     *                        Else destinations that <strong>contains</strong> the value are included.
     */
    public abstract void removeSubscriptions(String destinationName) throws AndesException;

    /**
     * Close/Remove/Unsubscribe subscriptions forcefully belonging to a specific protocol type, destination type.
     *
     * @param destinationName The name of the destination to close/unsubscribe. If "*", all destinations are included.
     *                        Else destinations that <strong>equals</strong> the value are included.
     */
    public abstract void removeSubscription(String destinationName, String subscriptionId) throws AndesException;

    /**
     * Browse message of a destination using message ID.
     *
     * @param destinationName The name of the destination
     * @param content         Whether to return message content or not.
     * @param nextMessageID   The starting message ID to return from.
     * @param limit           The number of messages to return.
     * @return A list of messages.
     */
    public abstract List<Message> browseDestinationWithMessageID(String destinationName, boolean content, long
            nextMessageID, int limit) throws AndesException;

    /**
     * Browse message of a destination. Please note this is time costly.
     *
     * @param destinationName The name of the destination
     * @param content         Whether to return message content or not.
     * @param offset          Starting index of the messages to return.
     * @param limit           The number of messages to return.
     * @return A list of messages.
     */
    public abstract List<Message> browseDestinationWithOffset(String destinationName, boolean content, int offset, int
            limit) throws AndesException;

    /**
     * Gets a message by message ID belonging to a particular protocol, destination type and destination name.
     *
     * @param destinationName The name of the destination to which the message belongs to.
     * @param andesMessageID  The message ID. This message is the andes metadata message ID.
     * @param content         Whether to return content or not.
     * @return A message.
     */
    public abstract Message getMessage(String destinationName, long andesMessageID, boolean content) throws
            AndesException;

    /**
     * Purge all messages belonging to a destination.
     *
     * @param destinationName The name of the destination to purge messages.
     */
    public abstract void deleteMessages(String destinationName) throws AndesException;

    /**
     * Delete a selected message list from a given Dead Letter Queue of a tenant.
     *
     * @param andesMetadataIDs     The browser message Ids
     * @param destinationQueueName The Dead Letter Queue Name for the tenant
     */
    void deleteMessagesFromDeadLetterQueue(long[] andesMetadataIDs, String destinationQueueName) {
        List<AndesMessageMetadata> messageMetadataList = new ArrayList<>(andesMetadataIDs.length);

        for (long andesMetadataID : andesMetadataIDs) {
            AndesMessageMetadata messageToRemove = new AndesMessageMetadata(andesMetadataID, null, false);
            messageToRemove.setStorageQueueName(destinationQueueName);
            messageToRemove.setDestination(destinationQueueName);
            messageMetadataList.add(messageToRemove);
        }

        // Deleting messages which are in the list.
        try {
            Andes.getInstance().deleteMessagesFromDLC(messageMetadataList);
        } catch (AndesException e) {
            throw new RuntimeException("Error deleting messages from Dead Letter Channel", e);
        }
    }

    /**
     * Restore a given browser message Id list from the Dead Letter Queue to the same queue it was previous in before
     * moving to the Dead Letter Queue and remove them from the Dead Letter Queue.
     *
     * @param andesMetadataIDs     The browser message Ids
     * @param destinationQueueName The Dead Letter Queue Name for the tenant
     */
    void restoreMessagesFromDeadLetterQueue(long[] andesMetadataIDs, String destinationQueueName) {
        if (null != andesMetadataIDs) {
            LongArrayList andesMessageIdList = new LongArrayList(andesMetadataIDs.length);
            andesMessageIdList.addAll(andesMetadataIDs);
            List<AndesMessageMetadata> messagesToRemove = new ArrayList<>(andesMessageIdList.size());

            try {
                LongObjectHashMap<List<AndesMessagePart>> messageContent = Andes.getInstance()
                        .getContent(andesMessageIdList);

                boolean interruptedByFlowControl = false;

                MutableLongIterator iterator = andesMessageIdList.longIterator();

                while (iterator.hasNext()) {
                    long messageId = iterator.next();
                    if (restoreBlockedByFlowControl) {
                        interruptedByFlowControl = true;
                        break;
                    }
                    AndesMessageMetadata metadata = Andes.getInstance().getMessageMetaData(messageId);
                    String destination = metadata.getDestination();

                    metadata.setStorageQueueName(AndesUtils.getStorageQueueForDestination(destination,
                            ClusterResourceHolder.getInstance().getClusterManager().getMyNodeID(),
                            DestinationType.QUEUE));

                    messagesToRemove.add(metadata);

                    AndesMessageMetadata clonedMetadata = metadata.shallowCopy(metadata.getMessageID());
                    AndesMessage andesMessage = new AndesMessage(clonedMetadata);

                    // Update Andes message with all the chunk details
                    List<AndesMessagePart> messageParts = messageContent.get(messageId);
                    messageParts.forEach(andesMessage::addMessagePart);

                    // Handover message to Andes. This will generate a new message ID and store it
                    Andes.getInstance().messageReceived(andesMessage, andesChannel, disablePubAck);
                }

                // Delete old messages
                Andes.getInstance().deleteMessagesFromDLC(messagesToRemove);

                if (interruptedByFlowControl) {
                    // Throw this out so UI will show this to the user as an error message.
                    throw new RuntimeException("Message restore from dead letter queue has been interrupted by flow "
                                               + "control. Please try again later.");
                }

            } catch (AndesException e) {
                throw new RuntimeException("Error restoring messages from " + destinationQueueName, e);
            }
        }
    }

    /**
     * Restore a given browser message Id list from the Dead Letter Queue to a different given queue in the same tenant
     * and remove them from the Dead Letter Queue.
     *
     * @param destinationQueueName    The Dead Letter Queue Name for the tenant
     * @param andesMetadataIDs        The browser message Ids
     * @param newDestinationQueueName The new destination
     */
    void restoreMessagesFromDeadLetterQueue(long[] andesMetadataIDs, String newDestinationQueueName, String
            destinationQueueName) {
        if (null != andesMetadataIDs) {

            LongArrayList andesMessageIdList = new LongArrayList(andesMetadataIDs.length);
            andesMessageIdList.addAll(andesMetadataIDs);
            List<AndesMessageMetadata> messagesToRemove = new ArrayList<>(andesMessageIdList.size());

            try {
                LongObjectHashMap<List<AndesMessagePart>> messageContent = Andes.getInstance()
                        .getContent(andesMessageIdList);

                boolean interruptedByFlowControl = false;

                MutableLongIterator iterator = andesMessageIdList.longIterator();
                while (iterator.hasNext()) {

                    long messageId = iterator.next();
                    if (restoreBlockedByFlowControl) {
                        interruptedByFlowControl = true;
                        break;
                    }

                    AndesMessageMetadata metadata = Andes.getInstance().getMessageMetaData(messageId);

                    // Set the new destination queue
                    metadata.setDestination(newDestinationQueueName);
                    metadata.setStorageQueueName(AndesUtils.getStorageQueueForDestination(newDestinationQueueName,
                            ClusterResourceHolder.getInstance().getClusterManager().getMyNodeID(),
                            DestinationType.QUEUE));

                    metadata.updateMetadata(newDestinationQueueName, AMQPUtils.DIRECT_EXCHANGE_NAME);
                    AndesMessageMetadata clonedMetadata = metadata.shallowCopy(metadata.getMessageID());
                    AndesMessage andesMessage = new AndesMessage(clonedMetadata);

                    messagesToRemove.add(metadata);

                    // Update Andes message with all the chunk details
                    List<AndesMessagePart> messageParts = messageContent.get(messageId);
                    messageParts.forEach(andesMessage::addMessagePart);

                    // Handover message to Andes. This will generate a new message ID and store it
                    Andes.getInstance().messageReceived(andesMessage, andesChannel, disablePubAck);
                }

                // Delete old messages
                Andes.getInstance().deleteMessagesFromDLC(messagesToRemove);

                if (interruptedByFlowControl) {
                    // Throw this out so UI will show this to the user as an error message.
                    throw new RuntimeException("Message restore from dead letter queue has been interrupted by flow "
                                               + "control. Please try again later.");
                }

            } catch (AndesException e) {
                throw new RuntimeException("Error restoring messages from " + destinationQueueName, e);
            }
        }
    }
}
