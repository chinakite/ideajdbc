package com.ideamoment.ideajdbc.log;

import static com.ideamoment.ideajdbc.log.Loggers.connectionLogger;
import static com.ideamoment.ideajdbc.log.ProxyUtils.*;

import java.lang.reflect.Method;
import java.sql.Connection;

public class ConnectionLoggingHandler extends LoggingHandlerSupport {
    public ConnectionLoggingHandler(Object target) {
        super(target);
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        try {
            Object r = method.invoke(target, args);
            if (UNWRAP_METHOD_NAME.equals(method.getName())) {
                Class<?> unwrapClass = (Class<?>) args[0];
                if (r == target && unwrapClass.isInstance(proxy)) {
                    r = proxy;      // returning original proxy if it is enough to represent the unwrapped obj
                } else if (unwrapClass.isInterface() && Connection.class.isAssignableFrom(unwrapClass)) {
                    r = wrapByConnectionProxy(r);
                }
            } else if (method.getName().equals("createStatement")) {
                r = wrapByStatementProxy(r);
            } else if (method.getName().equals("prepareCall")) {
                r = wrapByCallableStatementProxy(r, (String) args[0]);
            } else if (method.getName().equals("prepareStatement")) {
                r = wrapByPreparedStatementProxy(r, (String) args[0]);
            } else {
                r = wrap(r);
            }
            return r;
        } catch (Throwable t) {
            LogUtils.handleException(t, connectionLogger, LogUtils.createLogEntry(method, null, null, null));
        }
        return null;
    }
}
