package com.hexagonkt.logging

import com.hexagonkt.logging.LoggingLevel.*
import com.hexagonkt.logging.jul.JulLoggingAdapter
import org.slf4j.LoggerFactory
import org.slf4j.Logger as Slf4jLogger

object Slf4jJulLoggingAdapter : LoggingPort {

    override fun createLogger(name: String): LoggerPort =
        object : LoggerPort {
            val log: Slf4jLogger = LoggerFactory.getLogger(name)

            override fun log(level: LoggingLevel, message: () -> Any?) {
                when (level) {
                    TRACE -> if (log.isTraceEnabled) log.trace(message().toString())
                    DEBUG -> if (log.isDebugEnabled) log.debug(message().toString())
                    INFO -> if (log.isInfoEnabled) log.info(message().toString())
                    WARN -> if (log.isWarnEnabled) log.warn(message().toString())
                    ERROR -> if (log.isErrorEnabled) log.error(message().toString())
                    OFF -> { /* Ignored */ }
                }
            }

            override fun <E : Throwable> log(
                level: LoggingLevel,
                exception: E,
                message: (E) -> Any?,
            ) {
                when (level) {
                    TRACE ->
                        if (log.isTraceEnabled) log.trace(message(exception).toString(), exception)
                    DEBUG ->
                        if (log.isDebugEnabled) log.debug(message(exception).toString(), exception)
                    INFO ->
                        if (log.isInfoEnabled) log.info(message(exception).toString(), exception)
                    WARN ->
                        if (log.isWarnEnabled) log.warn(message(exception).toString(), exception)
                    ERROR ->
                        if (log.isErrorEnabled) log.error(message(exception).toString(), exception)
                    OFF -> { /* Ignored */ }
                }
            }
        }

    override fun setLoggerLevel(name: String, level: LoggingLevel) {
        val loggerName = name.ifEmpty { Slf4jLogger.ROOT_LOGGER_NAME }
        JulLoggingAdapter.setLoggerLevel(loggerName, level)
    }
}
