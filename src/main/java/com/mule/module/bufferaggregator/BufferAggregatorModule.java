/**
* Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
* The software in this package is published under the terms of the CPAL v1.0
* license, a copy of which has been included with this distribution in the
* LICENSE.txt file.
**/

/**
 * This file was automatically generated by the Mule Development Kit
 */
package com.mule.module.bufferaggregator;

import org.mule.api.MuleContext;
import org.mule.api.annotations.Module;
import org.mule.api.annotations.Configurable;
import org.mule.api.annotations.Processor;
import org.mule.api.annotations.param.Payload;
import org.mule.api.callback.SourceCallback;
import org.mule.api.store.ListableObjectStore;
import org.mule.api.store.ObjectDoesNotExistException;
import org.mule.api.store.ObjectStoreException;
import org.mule.api.store.ObjectStoreManager;
import org.mule.api.transaction.Transaction;
import org.mule.transaction.TransactionCoordination;
import org.omg.CORBA.ValueBaseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * A module to implement the buffer pattern
 *
 * @author Simone Avossa
 */
@Module(friendlyName="Buffer Aggregator", name="bufferaggregator", schemaVersion="1.0.0-SNAPSHOT")
public class BufferAggregatorModule
{
    public static final String BUFFER_GROUPS_STORE = "groups";

    private static Logger logger = LoggerFactory.getLogger(BufferAggregatorModule.class);

    /**
     * The maximum buffer size before being flushed
     */
    @Configurable
    private int bufferSize;

    /**
     * The time to live for the buffer before being flushed (milliseconds)
     */
    @Configurable
    private long bufferTimeToLive;

    /**
     * The object store prefix
     */
    @Configurable
    private String storePrefix;

    @Inject
    private ObjectStoreManager objectStoreManager;

    @Inject
    private MuleContext muleContext;

    public void setBufferSize(int bufferSize)
    {
        this.bufferSize = bufferSize;
    }

    public int getBufferSize()
    {
        return bufferSize;
    }

    public void setBufferTimeToLive(long bufferTimeToLive)
    {
        this.bufferTimeToLive = bufferTimeToLive;
    }

    public long getBufferTimeToLive()
    {
        return bufferTimeToLive;
    }

    public void setStorePrefix(String storePrefix)
    {
        this.storePrefix = storePrefix;
    }

    public String getStorePrefix()
    {
        return storePrefix;
    }

    public void setObjectStoreManager(ObjectStoreManager objectStoreManager)
    {
        this.objectStoreManager = objectStoreManager;
    }

    public ObjectStoreManager getObjectStoreManager()
    {
        return objectStoreManager;
    }

    public void setMuleContext(MuleContext muleContext)
    {
        this.muleContext = muleContext;
    }

    public MuleContext getMuleContext()
    {
        return muleContext;
    }

    /**
     * Buffer the payload of the incoming message
     *
     * {@sample.xml ../../../doc/BufferAggregator-connector.xml.sample bufferaggregator:buffer}
     *
     * @param group Identifies the group under which buffer the message
     * @param key The key used to store the message in the buffer
     */
    @Processor(intercepting = true)
    public void buffer(SourceCallback afterChain, String group, String key, @Payload Serializable payload) throws BufferException
    {
        Lock lock = muleContext.getLockFactory().createLock(group);
        lock.lock();

        String actualKey = null;
        ListableObjectStore<Serializable> buffer = null;

        try
        {
            ListableObjectStore<Long> groups = (ListableObjectStore<Long>) objectStoreManager.getObjectStore(storePrefix + "." + BUFFER_GROUPS_STORE);

            if(!groups.contains(group))
            {
                groups.store(group, System.currentTimeMillis());
            }

            actualKey = key + "-" + System.currentTimeMillis();
            buffer = (ListableObjectStore<Serializable>) objectStoreManager.getObjectStore(storePrefix + "." + group);
            buffer.store(actualKey, payload);

            /*
            // This is to minimise duplicates in case of unexpected shutdown
            Transaction tx = TransactionCoordination.getInstance().getTransaction();
            if (tx != null && tx.isBegun())
            {
                // Commit current transaction as the payload is stored into the object store
                tx.commit();
            }
            */

            List<Serializable> allKeys = buffer.allKeys();

            // Aggregate all the payloads and flush the buffer
            if (allKeys.size() >= bufferSize)
            {
                List<String> sortedKeys = sortKeys(allKeys);
                List<Serializable> aggregatedPayloads = aggregatePayloads(buffer, sortedKeys);

                if (!aggregatedPayloads.isEmpty())
                {
                    // Process aggregated payload
                    afterChain.process(aggregatedPayloads);
                }

                // Clear the buffer
                cleanBuffer(buffer, sortedKeys);

                try
                {
                    groups.remove(group);
                }
                catch (ObjectDoesNotExistException e)
                {
                    // Do nothing as the key does not exist
                }

            }
        }
        catch (Exception e)
        {
            try
            {
                if (buffer != null && actualKey != null) {
                    buffer.remove(actualKey);
                }
            }
            catch (ObjectStoreException ose)
            {
                // Do nothing as we are already in an exception scope
            }

            throw new BufferException("Unable to buffer message", e);
        }
        finally
        {
            lock.unlock();
        }
    }

    /**
     * Flushes the buffer
     *
     * {@sample.xml ../../../doc/BufferAggregator-connector.xml.sample bufferaggregator:flush-buffer}
     *
     */
    @Processor(intercepting = true)
    public void flushBuffer(SourceCallback afterChain) throws BufferException
    {
        try {
            ListableObjectStore<Long> groups = (ListableObjectStore<Long>) objectStoreManager.getObjectStore(storePrefix + "." + BUFFER_GROUPS_STORE);
            List<Serializable> allKeys = groups.allKeys();

            for (int i = 0; i < allKeys.size(); i++) {
                long time = groups.retrieve(allKeys.get(i));
                // Aggregate all the payloads and flush the buffer
                if (time + bufferTimeToLive < System.currentTimeMillis()) {
                    String group = (String) allKeys.get(i);

                    Lock lock = muleContext.getLockFactory().createLock(group);
                    lock.lock();

                    try {
                        ListableObjectStore<Serializable> buffer = (ListableObjectStore<Serializable>) objectStoreManager.getObjectStore(storePrefix + "." + group);
                        List<Serializable> bufferAllKeys = buffer.allKeys();

                        List<String> sortedKeys = sortKeys(bufferAllKeys);
                        List<?> aggregatedPayloads = aggregatePayloads(buffer, sortedKeys);

                        if (!aggregatedPayloads.isEmpty())
                        {
                            // Process aggregated payload
                            afterChain.process(aggregatedPayloads);
                        }

                        // Clear the buffer
                        cleanBuffer(buffer, sortedKeys);

                        try
                        {
                            groups.remove(allKeys.get(i));
                        }
                        catch (ObjectDoesNotExistException e)
                        {
                            // Do nothing as the key does not exist
                        }
                    }
                    catch (Exception e)
                    {
                        logger.error("Unable to flush expired buffer for group: " + group, e);
                    }
                    finally
                    {
                        lock.unlock();
                    }
                }

            }
        }
        catch (Exception e)
        {
            throw new BufferException("Unable to flush expired buffer", e);
        }
    }

    private List<Serializable> aggregatePayloads(ListableObjectStore<Serializable> buffer, List<String> keys) throws ObjectStoreException
    {
        List<Serializable> aggregatedPayloads = new ArrayList<Serializable>();
        for (int i = 0; i < keys.size(); i++)
        {
            try
            {
                Serializable value = (Serializable) buffer.retrieve(keys.get(i));
                aggregatedPayloads.add(value);
            }
            catch (ObjectDoesNotExistException e)
            {
                // Do nothing as the key does not exist
            }
        }

        return aggregatedPayloads;
    }

    private void cleanBuffer(ListableObjectStore<Serializable> buffer, List<String> keys) throws ObjectStoreException
    {
        for (int i = 0; i < keys.size(); i++)
        {
            try
            {
                buffer.remove(keys.get(i));
            }
            catch (ObjectDoesNotExistException e)
            {
                // Do nothing as the key does not exist
            }
        }
    }

    private List<String> sortKeys(List<Serializable> keys)
    {
        List<String> sortedKeys= new ArrayList<String>();
        for (int i = 0; i < keys.size(); i++)
        {
            sortedKeys.add((String) keys.get(i));
        }

        Collections.sort(sortedKeys);

        return sortedKeys;
    }
}
