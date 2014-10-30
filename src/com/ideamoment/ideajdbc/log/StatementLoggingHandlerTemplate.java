package com.ideamoment.ideajdbc.log;

import static com.ideamoment.ideajdbc.log.LogUtils.*;
import static com.ideamoment.ideajdbc.log.ProxyUtils.*;
import static com.ideamoment.ideajdbc.log.Loggers.*;

import java.lang.reflect.Method;

import org.slf4j.Logger;

/**
 * Template for statement logging handler, which includes slow query handling, and
 * before/after statement logging
 *
 * @author a511990
 */
public abstract class StatementLoggingHandlerTemplate extends LoggingHandlerSupport {

    protected StringBuilder batchStatements = new StringBuilder();

    public StatementLoggingHandlerTemplate(Object target) {
        super(target);
    }


    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        try {
            boolean needsLog = needsLogging(proxy, method, args);
            boolean isAddBatch = isAddBatch(proxy, method, args);
            boolean isExecuteBatch = isExecuteBatch(proxy, method, args);
            long startTimeInNano = 0;
            StringBuilder sb= null;

            if (needsLog) {
                startTimeInNano = System.nanoTime();

                sb = new StringBuilder("START: ");      // Reserve space for START: and END:
                sb.append(method.getDeclaringClass().getName()).append(".").append(method.getName()).append(": ");

                if (isExecuteBatch) {
                    if (IdeaJdbcLogConfiguration.logExecuteBatchDetail) {
                        appendBatchStatements(sb);
                    }
                    this.batchStatements = new StringBuilder();
                } else if (isAddBatch) {
                    if (IdeaJdbcLogConfiguration.logAddBatchDetail) {
                        appendStatement(sb, proxy, method, args);
                    }
                    if (IdeaJdbcLogConfiguration.logExecuteBatchDetail) {
                        doAddBatch(proxy, method, args);
                    }
                }  else {
                    appendStatement(sb, proxy, method, args);
                }

                appendStackTrace(sb);

                logBeforeInvoke(proxy, method, args, sb);
            }

            Object result = method.invoke(target, args);

            result = doAfterInvoke(proxy, method, args, result);

            if (needsLog) {
                long elapsedTimeInNano = System.nanoTime() - startTimeInNano;
                sb.setCharAt(0, 'E');
                sb.setCharAt(1, 'N');
                sb.setCharAt(2, 'D');
                sb.setCharAt(3, ':');
                sb.setCharAt(4, ' ');
                sb.setCharAt(5, ' ');
                sb.setCharAt(6, ' ');

                appendElapsedTime(sb, elapsedTimeInNano);

                logAfterInvoke(proxy, method, args, result, elapsedTimeInNano, sb);
            }
            return result;

        } catch (Throwable t) {
            handleException(t, proxy, method, args);
        }
        return null;
    }

    protected abstract void doAddBatch(Object proxy, Method method, Object[] args);

    protected abstract void appendBatchStatements(StringBuilder sb);

    protected boolean isExecuteBatch(Object proxy, Method method, Object[] args) {
        return method.getName().equals("executeBatch");
    }


    protected boolean isAddBatch(Object proxy, Method method, Object[] args) {
        return method.getName().equals("addBatch");
    }


    protected abstract void appendStatement(StringBuilder sb, Object proxy, Method method, Object[] args) ;

    protected boolean needsLogging(Object proxy, Method method, Object[] args) {
        return false;
    }

    protected void logBeforeInvoke(Object proxy, Method method, Object[] args, StringBuilder sb) {
        if (IdeaJdbcLogConfiguration.logBeforeStatement) {
            getLogger().info(sb.toString());
        }
    }

    protected Object doAfterInvoke(Object proxy, Method method, Object[] args, Object result) {
        return wrap(result);
    }

    protected void logAfterInvoke(Object proxy, Method method, Object[] args, Object result, long elapsedTimeInNano, StringBuilder message) {

        StringBuilder endMessage = message;
        if ( ! IdeaJdbcLogConfiguration.logDetailAfterStatement) {
            // replace the log message to a simple message

            endMessage = new StringBuilder("END:    ")
                        .append(method.getDeclaringClass().getName()).append(".").append(method.getName())
                        .append(": ");
            appendStackTrace(endMessage);
            appendElapsedTime(endMessage, elapsedTimeInNano);

        }

        getLogger().info(endMessage.toString());

        if (elapsedTimeInNano/1000000 >= IdeaJdbcLogConfiguration.slowQueryThreshold) {
            getSlowQueryLogger().info(message.toString());       // log the original message
        }

    }

    protected boolean needsSlowOperationLogging(Object proxy, Method method, Object[] args, Object result, long elapsedTimeInNano) {
        return true;
    }

    protected void handleException(Throwable t, Object proxy, Method method, Object[] args) throws Throwable {
        LogUtils.handleException(t, getLogger(), LogUtils.createLogEntry(method, null, null, null));
    }

    protected Logger getLogger() {
        return statementLogger;
    }
    protected Logger getSlowQueryLogger() {
        return slowQueryLogger;
    }

}
