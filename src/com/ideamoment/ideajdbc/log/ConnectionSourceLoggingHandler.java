/*
 *  ConnectionSourceLoggingHandler.java
 *
 *  $id$
 *
 * Copyright (C) FIL Limited. All rights reserved
 *
 * This software is the confidential and proprietary information of
 * FIL Limited You shall not disclose such Confidential Information
 * and shall use it only in accordance with the terms of the license
 * agreement you entered into with FIL Limited.
 */

package com.ideamoment.ideajdbc.log;

import static com.ideamoment.ideajdbc.log.Loggers.*;
import static com.ideamoment.ideajdbc.log.ProxyUtils.*;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Logging handler for objects that can directly or indirectly create Connection from.  For example,
 * DataSource, PooledConnection.
 *
 * @author a511990
 */
public class ConnectionSourceLoggingHandler extends LoggingHandlerSupport {
    public ConnectionSourceLoggingHandler(Object target) {
        super(target);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        try {
            Object r = method.invoke(target, args);

            if (r instanceof Connection) {
                DatabaseMetaData connMetaData = ((Connection) r).getMetaData();
                if (connectionLogger.isInfoEnabled()) {
                    String message = LogUtils.appendStackTrace("connect to URL {} for user {}");
                    connectionLogger.info(message, connMetaData.getURL(), connMetaData.getUserName());
                }
            }

            return wrap(r);

        } catch (Throwable t) {
            LogUtils.handleException(t, connectionLogger, LogUtils.createLogEntry(method, null, null, null));
        }
        return null;
    }

}
