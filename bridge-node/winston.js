/**
 * Winston logger configuration
 *
 * @changeHistory
 * - 2025-08-24: Updated logger to send logs to both file and console; fixed createLogger usage; unified levels and formats (Neer Patel)
 */

const appRoot = require("app-root-path");
const { createLogger, format, transports } = require("winston");
const { combine, timestamp, printf } = format;

const myFormat = printf(({ level, message, timestamp }) => {
    return `${timestamp} ${level}: ${message}`;
});

const options = {
    file: {
        level: "debug", // capture debug and above (adjust as needed)
        filename: `${appRoot}/logs/app.log`,
        handleExceptions: true,
        json: false, // use the same human-readable format in the file
        maxsize: 5242880, // 5MB
        maxFiles: 5,
        colorize: false,
        format: combine(timestamp(), myFormat),
    },
    console: {
        level: "debug", // capture debug and above (adjust as needed)
        handleExceptions: true,
        json: false,
        colorize: true,
        format: combine(timestamp(), myFormat),
    },
};

const logger = createLogger({
    transports: [
        new transports.File(options.file),
        new transports.Console(options.console),
    ],
    exitOnError: false, // do not exit on handled exceptions
});

logger.stream = {
    /**
     * @changeHistory
     * - 2025-08-24: Added stream.write helper to pipe other loggers (Neer Patel)
     */
    write: function (message) {
        logger.info(message.trim());
    },
};

module.exports = logger;
