/*
 * RESTHeart - the data REST API server
 * Copyright (C) 2014 SoftInstigate Srl
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.softinstigate.restheart.db;

import com.mongodb.BasicDBObject;
import com.mongodb.CommandFailureException;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.softinstigate.restheart.handlers.RequestContext;
import java.time.Instant;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare
 */
public class PropsFixer {
    private static final MongoClient client = MongoDBClientSingleton.getInstance().getClient();

    private static final Logger logger = LoggerFactory.getLogger(PropsFixer.class);

    /**
     *
     * @param dbName
     * @param collName
     * @return
     * @throws MongoException
     */
    public static boolean addCollectionProps(String dbName, String collName) throws MongoException {
        DBObject dbmd = DBDAO.getDbProps(dbName);

        if (dbmd == null) {
            // db must exists with properties
            return false;
        }

        DBObject md = CollectionDAO.getCollectionProps(dbName, collName);

        if (md != null) // properties exists
        {
            return false;
        }

        // check if collection has data
        DB db = DBDAO.getDB(dbName);

        if (!db.collectionExists(collName)) {
            return false;
        }

        // ok, create the properties
        DBObject properties = new BasicDBObject();

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        properties.put("_id", "_properties");
        properties.put("_created_on", now.toString());
        properties.put("_etag", timestamp);

        DBCollection coll = CollectionDAO.getCollection(dbName, collName);

        coll.insert(properties);

        logger.info("properties added to {}/{}", dbName, collName);
        return true;
    }

    /**
     *
     * @param dbName
     * @return
     */
    public static boolean addDbProps(String dbName) {
        if (!DBDAO.doesDbExists(dbName)) {
            return false;
        }

        DBObject dbmd = DBDAO.getDbProps(dbName);

        if (dbmd != null) // properties exists
        {
            return false;
        }

        // ok, create the properties
        DBObject properties = new BasicDBObject();

        ObjectId timestamp = new ObjectId();
        Instant now = Instant.ofEpochSecond(timestamp.getTimestamp());

        properties.put("_id", "_properties");
        properties.put("_created_on", now.toString());
        properties.put("_etag", timestamp);

        DBCollection coll = CollectionDAO.getCollection(dbName, "_properties");

        coll.insert(properties);
        logger.info("properties added to {}", dbName);
        return true;
    }

    /**
     *
     */
    public static void fixAllMissingProps() {
        try {
            client.getDatabaseNames().stream().filter(dbName -> !RequestContext.isReservedResourceDb(dbName)).map(dbName -> {
                try {
                    addDbProps(dbName);
                } catch (Throwable t) {
                    logger.error("error fixing _properties of db {}", dbName, t);
                }
                return dbName;
            }).forEach(dbName -> {
                DB db = DBDAO.getDB(dbName);

                DBDAO.getDbCollections(db).stream().filter(collectionName -> !RequestContext.isReservedResourceCollection(collectionName)).forEach(collectionName -> {
                    try {
                        addCollectionProps(dbName, collectionName);
                    } catch (Throwable t) {
                        logger.error("error checking the collection {}/{} for valid _properties. note that a request to it will result on NOT_FOUND", dbName, collectionName, t);
                    }
                }
                );
            });
        } catch (CommandFailureException cfe) {
            Object errmsg = cfe.getCommandResult().get("errmsg");

            if (errmsg != null && errmsg instanceof String && ("unauthorized".equals(errmsg) || ((String) errmsg).contains("not authorized"))) {
                logger.error("error looking for dbs and collections with missing _properties due to insuffient mongo user privileges. note that requests to dbs and collections with no _properties result on NOT_FOUND", cfe);
            } else {
                logger.error("eorro looking for dbs and collections with missing _properties. note that requests to dbs and collections with no _properties result on NOT_FOUND", cfe);
            }
        } catch (MongoException mex) {
            logger.error("eorro looking for dbs and collections with missing _properties. note that requests to dbs and collections with no _properties result on NOT_FOUND", mex);
        }
    }
}
