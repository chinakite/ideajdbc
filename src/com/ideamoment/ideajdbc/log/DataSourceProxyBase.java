package com.ideamoment.ideajdbc.log;

import static com.ideamoment.ideajdbc.log.Loggers.connectionLogger;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.PooledConnection;
import javax.sql.XAConnection;
import javax.sql.XADataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataSourceProxyBase implements Serializable {

    private static final long serialVersionUID = -1209576641924549514L;

    protected static Logger logger = LoggerFactory.getLogger(DataSourceProxyBase.class);

    protected static final String TARGET_DS_PARAMETER = "targetDS";

    protected Object targetDs = null;

    protected Map<String, Object> props = new HashMap<String, Object>();

    protected Map<String, Class<?>> propClasses = new HashMap<String, Class<?>>();

    public DataSourceProxyBase() throws IdeaJdbcLogException {
    }

    public Connection getConnection() throws SQLException {

        if (targetDs == null) {
            throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
        }
        if (targetDs instanceof DataSource) {
            Connection con = ((DataSource) targetDs).getConnection();
            if (connectionLogger.isInfoEnabled()) {
                String message = LogUtils.appendStackTrace("connect to URL {} for user {}");
                connectionLogger.info(message, con.getMetaData().getURL(), con.getMetaData().getUserName());
            }
            return ProxyUtils.wrapByConnectionProxy(con);
        } else {
            throw new SQLException("targetDS doesn't implement DataSource interface.");
        }
    }

    public Connection getConnection(String username, String password) throws SQLException {
        if (targetDs == null) {
            throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
        }
        if (targetDs instanceof DataSource) {
            Connection con = ((DataSource) targetDs).getConnection(username, password);

            if (connectionLogger.isInfoEnabled()) {
                String message = LogUtils.appendStackTrace("connect to URL {} for user {}");
                connectionLogger.info(message, con.getMetaData().getURL(), con.getMetaData().getUserName());
            }
            return ProxyUtils.wrapByConnectionProxy(con);
        } else {
            throw new SQLException("targetDS doesn't implement DataSource interface.");
        }
    }

    public PrintWriter getLogWriter() throws SQLException {
        if (targetDs instanceof DataSource)
            return ((DataSource) targetDs).getLogWriter();
        if (targetDs instanceof XADataSource)
            return ((XADataSource) targetDs).getLogWriter();
        if (targetDs instanceof ConnectionPoolDataSource)
            return ((ConnectionPoolDataSource) targetDs).getLogWriter();
        throw new SQLException("targetDS doesn't have getLogWriter() method");
    }

    public int getLoginTimeout() throws SQLException {
        if (targetDs instanceof DataSource)
            return ((DataSource) targetDs).getLoginTimeout();
        if (targetDs instanceof XADataSource)
            return ((XADataSource) targetDs).getLoginTimeout();
        if (targetDs instanceof ConnectionPoolDataSource)
            return ((ConnectionPoolDataSource) targetDs).getLoginTimeout();
        throw new SQLException("targetDS doesn't have getLogTimeout() method");
    }

    public void setLogWriter(PrintWriter out) throws SQLException {
        if (targetDs instanceof DataSource)
            ((DataSource) targetDs).setLogWriter(out);
        if (targetDs instanceof XADataSource)
            ((XADataSource) targetDs).setLogWriter(out);
        if (targetDs instanceof ConnectionPoolDataSource)
            ((ConnectionPoolDataSource) targetDs).setLogWriter(out);
        throw new SQLException("targetDS doesn't have setLogWriter() method");
    }

    public void setLoginTimeout(int seconds) throws SQLException {
        if (targetDs instanceof DataSource)
            ((DataSource) targetDs).setLoginTimeout(seconds);
        if (targetDs instanceof XADataSource)
            ((XADataSource) targetDs).setLoginTimeout(seconds);
        if (targetDs instanceof ConnectionPoolDataSource)
            ((ConnectionPoolDataSource) targetDs).setLoginTimeout(seconds);
        throw new SQLException("targetDS doesn't have setLogWriter() method");
    }

    public XAConnection getXAConnection() throws SQLException {
        if (targetDs == null) {
            throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
        }
        if (targetDs instanceof XADataSource) {
            XAConnection con = ((XADataSource) targetDs).getXAConnection();
            return ProxyUtils.wrapByXaConnection(con);
        } else {
            throw new SQLException("targetDS doesn't implement XADataSource interface.");
        }
    }

    public XAConnection getXAConnection(String user, String password) throws SQLException {
        if (targetDs == null)
            throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
        if (targetDs instanceof XADataSource)
            return ProxyUtils.wrapByXaConnection(((XADataSource) targetDs).getXAConnection(user, password));
        else
            throw new SQLException("targetDS doesn't implement XADataSource interface.");
    }

    public PooledConnection getPooledConnection() throws SQLException {
        if (targetDs == null)
            throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
        if (targetDs instanceof ConnectionPoolDataSource)
            return ProxyUtils.wrapByPooledConnection(((ConnectionPoolDataSource) targetDs).getPooledConnection());
        else
            throw new SQLException("targetDS doesn't implement ConnectionPoolDataSource interface.");
    }

    public PooledConnection getPooledConnection(String user, String password) throws SQLException {
        if (targetDs == null)
            throw new SQLException("targetDS parameter has not been passed to Database or URL property.");
        if (targetDs instanceof ConnectionPoolDataSource)
            return ProxyUtils.wrapByPooledConnection(((ConnectionPoolDataSource) targetDs).getPooledConnection(user, password));
        else
            throw new SQLException("targetDS doesn't implement ConnectionPoolDataSource interface.");
    }

    protected void invokeTargetSetMethod(String methodName, Object param, Class<?> paramClass) {
        if (targetDs == null) {
            props.put(methodName, param);
            propClasses.put(methodName, paramClass);
            return;
        }
        logger.debug("invokeTargetSetMethod: {} ({})", methodName, param);
        try {
            Method me = targetDs.getClass().getMethod(methodName, paramClass);
            if (me != null)
                me.invoke(targetDs, param);
        } catch (Exception e) {
            connectionLogger.error("Error in invoking setter method {}({})", methodName, param);
        }
    }

    public void setURL(String url) throws IdeaJdbcLogException {
        url = initTargetDS(url);
        invokeTargetSetMethod("setURL", url, String.class);
    }

    private String initTargetDS(String url) throws IdeaJdbcLogException {
        logger.debug("initTargetDS: url={}, targetDs={}", url, targetDs);
        try {
            if (url == null || targetDs != null)
                return url;
            logger.debug("Parse url. {}", url);
            StringTokenizer ts = new StringTokenizer(url, ":/;=&?", false);
            String targetDSName = null;
            while (ts.hasMoreTokens()) {
                String s = ts.nextToken();
                logger.debug("s = {}", s);
                if (TARGET_DS_PARAMETER.equals(s) && ts.hasMoreTokens()) {
                    targetDSName = ts.nextToken();
                    break;
                }
            }
            if (targetDSName == null)
                return url;
            url = url.substring(0, url.length() - targetDSName.length() - TARGET_DS_PARAMETER.length() - 2);
            setTargetDS(targetDSName);
            return url;
        } catch (Throwable t) {
            connectionLogger.error("Error in initTargetDS() url={}", url, t);
            throw new IdeaJdbcLogException(IdeaJdbcLogExceptionCode.LOAD_TARGET_CLASS_ERR, "Error in initTargetDS() url=" + url, t);
        }
    }

    public void setTargetDSDirect(Object dataSource) {
        targetDs = dataSource;
        logger.debug("setTargetDSDirect(): targetDS initialized.");
    }

    public void setTargetDS(String targetDSName) throws IdeaJdbcLogException, InstantiationException, IllegalAccessException {
        try {
            Class<?> cl = Class.forName(targetDSName);
            if (cl == null)
                throw new IdeaJdbcLogException("Can't load class of targetDS.");
            Object targetObj = cl.newInstance();
            targetDs = targetObj;
            logger.debug("setTargetDS(): targetDS initialized.");
            setPropertiesForTargetDS();
        } catch (Throwable t) {
            connectionLogger.error("Error in setTargetDS(): targetDSName={}", targetDSName, t);
            throw new IdeaJdbcLogException(IdeaJdbcLogExceptionCode.LOAD_TARGET_CLASS_ERR, "Error in setTargetDS(): targetDSName=" + targetDSName, t);
        }
    }

    private void setPropertiesForTargetDS() {
        for (String m : props.keySet()) {
            invokeTargetSetMethod(m, props.get(m), propClasses.get(m));
        }
    }

    public void setDatabaseName(String p) {
        invokeTargetSetMethod("setDatabaseName", p, String.class);
    }

    public void setDescription(String p) {
        invokeTargetSetMethod("setDescription", p, String.class);
    }

    public void setDataSourceName(String p) {
        invokeTargetSetMethod("setDataSourceName", p, String.class);
    }

    public void setDriverType(String p) {
        invokeTargetSetMethod("setDriverType", p, String.class);
    }

    public void setNetworkProtocol(String p) {
        invokeTargetSetMethod("setNetworkProtocol", p, String.class);
    }

    public void setPassword(String p) {
        invokeTargetSetMethod("setPassword", p, String.class);
    }

    public void setPortNumber(int p) {
        invokeTargetSetMethod("setPortNumber", new Integer(p), int.class);
    }

    public void setServerName(String p) {
        invokeTargetSetMethod("setServerName", p, String.class);
    }

    public void setServiceName(String p) {
        invokeTargetSetMethod("setServiceName", p, String.class);
    }

    public void setTNSEntryName(String p) {
        invokeTargetSetMethod("setTNSEntryName", p, String.class);
    }

    public void setUser(String p) {
        invokeTargetSetMethod("setUser", p, String.class);
    }

    public void setDatabase(String p) throws IdeaJdbcLogException {
        p = initTargetDS(p);
        invokeTargetSetMethod("setDatabase", p, String.class);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return false;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return null;
    }

    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }
}
