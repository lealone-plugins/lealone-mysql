/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.mysql.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Properties;

import com.lealone.common.exceptions.DbException;
import com.lealone.common.logging.Logger;
import com.lealone.common.logging.LoggerFactory;
import com.lealone.common.util.StringUtils;
import com.lealone.db.ConnectionInfo;
import com.lealone.db.Constants;
import com.lealone.db.ManualCloseable;
import com.lealone.db.PluginManager;
import com.lealone.db.result.Result;
import com.lealone.db.scheduler.Scheduler;
import com.lealone.db.session.ServerSession;
import com.lealone.db.value.Value;
import com.lealone.db.value.ValueNull;
import com.lealone.net.NetBuffer;
import com.lealone.net.NetBufferOutputStream;
import com.lealone.net.WritableChannel;
import com.lealone.plugins.mysql.server.handler.AuthPacketHandler;
import com.lealone.plugins.mysql.server.handler.CommandPacketHandler;
import com.lealone.plugins.mysql.server.handler.PacketHandler;
import com.lealone.plugins.mysql.server.protocol.AuthPacket;
import com.lealone.plugins.mysql.server.protocol.EOFPacket;
import com.lealone.plugins.mysql.server.protocol.ErrorPacket;
import com.lealone.plugins.mysql.server.protocol.ExecutePacket;
import com.lealone.plugins.mysql.server.protocol.FieldPacket;
import com.lealone.plugins.mysql.server.protocol.Fields;
import com.lealone.plugins.mysql.server.protocol.HandshakePacket;
import com.lealone.plugins.mysql.server.protocol.OkPacket;
import com.lealone.plugins.mysql.server.protocol.Packet;
import com.lealone.plugins.mysql.server.protocol.PacketInput;
import com.lealone.plugins.mysql.server.protocol.PacketOutput;
import com.lealone.plugins.mysql.server.protocol.PreparedOkPacket;
import com.lealone.plugins.mysql.server.protocol.ResultSetHeaderPacket;
import com.lealone.plugins.mysql.server.protocol.RowDataPacket;
import com.lealone.server.AsyncServerConnection;
import com.lealone.server.scheduler.SessionInfo;
import com.lealone.sql.PreparedSQLStatement;
import com.lealone.sql.SQLEngine;
import com.lealone.sql.SQLStatement;
import com.lealone.sql.ddl.CreateDatabase;

public class MySQLServerConnection extends AsyncServerConnection {

    private static final Logger logger = LoggerFactory.getLogger(MySQLServerConnection.class);
    private static final byte[] AUTH_OK = new byte[] { 7, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0 };
    private static final byte[] EMPTY = new byte[0];

    private final Calendar calendar = Calendar.getInstance();

    private final MySQLServer server;
    private final Scheduler scheduler;
    private ServerSession session;
    private SessionInfo si;

    private PacketHandler packetHandler;
    private AuthPacket authPacket;
    private int nextStatementId;

    private byte[] salt;

    protected MySQLServerConnection(MySQLServer server, WritableChannel channel, Scheduler scheduler) {
        super(channel, true);
        this.server = server;
        this.scheduler = scheduler;
    }

    public Calendar getCalendar() {
        return calendar;
    }

    public ServerSession getSession() {
        return session;
    }

    @Override
    public void closeSession(SessionInfo si) {
    }

    @Override
    public int getSessionCount() {
        return 1;
    }

    // 客户端连上来后，数据库先发回一个握手包
    void handshake(int threadId) {
        // 创建一个AuthPacketHandler用来鉴别是否是合法的用户
        packetHandler = new AuthPacketHandler(this);
        HandshakePacket p = new HandshakePacket(threadId);
        salt = p.getSalt();
        sendPacket(p);
    }

    public void authenticate(AuthPacket authPacket) {
        this.authPacket = authPacket;
        try {
            session = createSession(authPacket, authPacket.database);
            session.setSQLEngine(PluginManager.getPlugin(SQLEngine.class, MySQLServerEngine.NAME));
        } catch (Throwable e) {
            logAndSendErrorMessage("Failed to create session", e);
            close();
            server.removeConnection(this);
            return;
        }
        // 鉴别成功后创建CommandPacketHandler用来处理各种命令(包括SQL)
        packetHandler = new CommandPacketHandler(this);
        sendMessage(AUTH_OK);
    }

    private ServerSession createSession(AuthPacket authPacket, String schemaName) {
        String dbName = MySQLServer.DATABASE_NAME;
        if (schemaName != null) {
            int pos = schemaName.indexOf('.');
            if (pos > 0) {
                dbName = schemaName.substring(0, pos);
                schemaName = schemaName.substring(pos + 1);
            }
        }
        if (schemaName == null)
            schemaName = Constants.SCHEMA_MAIN;

        if (session == null) {
            Properties info = new Properties();
            info.put("MODE", MySQLServerEngine.NAME);
            info.put("USER", authPacket.user);
            info.put("PASSWORD", StringUtils.convertBytesToHex(getPassword(authPacket)));
            info.put("PASSWORD_HASH", "true");
            String url = Constants.URL_PREFIX + Constants.URL_TCP + server.getHost() + ":"
                    + server.getPort() + "/" + dbName;
            ConnectionInfo ci = new ConnectionInfo(url, info);
            ci.setSalt(salt);
            ci.setRemote(false);
            session = (ServerSession) ci.createSession();
            si = new SessionInfo(scheduler, this, session, -1, -1);
            scheduler.addSessionInfo(si);
            session.setScheduler(scheduler);
            session.setVersion(MySQLServer.SERVER_VERSION);
        }
        session.setCurrentSchema(session.getDatabase().getSchema(session, schemaName));
        return session;
    }

    private static byte[] getPassword(AuthPacket authPacket) {
        if (authPacket.password == null || authPacket.password.length == 0)
            return EMPTY;
        return authPacket.password;
    }

    public void initDatabase(String dbName) {
        session = createSession(authPacket, dbName);
    }

    public void closeStatement(int statementId) {
        ManualCloseable stmt = session.removeCache(statementId, true);
        if (stmt != null) {
            stmt.close();
        }
    }

    public void prepareStatement(String sql) {
        PreparedSQLStatement stmt = prepareStatement(sql, true);
        if (stmt != null) {
            PreparedOkPacket packet = new PreparedOkPacket();
            packet.packetId = 1;
            packet.statementId = stmt.getId();
            packet.columnsNumber = stmt.getMetaData().get().getVisibleColumnCount();
            packet.parametersNumber = stmt.getParameters().size();
            sendPacket(packet);
        }
    }

    private PreparedSQLStatement prepareStatement(String sql, boolean cache) {
        if (logger.isDebugEnabled())
            logger.debug("prepare statement: " + sql);
        try {
            PreparedSQLStatement stmt = session.prepareStatement(sql, -1);
            if (cache) {
                int statementId = ++nextStatementId;
                stmt.setId(statementId);
                session.addCache(statementId, stmt);
            }
            return stmt;
        } catch (Throwable e) {
            logAndSendErrorMessage("Failed to prepare statement: " + sql, e);
            return null;
        }
    }

    public void executeStatement(ExecutePacket packet) {
        PreparedSQLStatement stmt = (PreparedSQLStatement) session.getCache((int) packet.statementId);
        executeStatement(stmt);
    }

    public void executeStatement(String sql) {
        PreparedSQLStatement stmt = prepareStatement(sql, false);
        if (stmt != null) {
            executeStatement(stmt);
        }
    }

    private void executeStatement(PreparedSQLStatement stmt) {
        if (logger.isDebugEnabled())
            logger.debug("execute statement: " + stmt.getSQL());
        try {
            submitYieldableCommand(stmt);
        } catch (Throwable e) {
            logAndSendErrorMessage("Failed to submit statement: " + stmt.getSQL(), e);
        }
    }

    // 异步执行SQL语句
    private void submitYieldableCommand(PreparedSQLStatement stmt) {
        PreparedSQLStatement.Yieldable<?> yieldable;
        if (stmt.isQuery()) {
            yieldable = stmt.createYieldableQuery(-1, false, ar -> {
                if (ar.isSucceeded()) {
                    writeQueryResult(ar.getResult());
                } else {
                    writeFailedResult(stmt, ar.getCause());
                }
            });
        } else {
            yieldable = stmt.createYieldableUpdate(ar -> {
                if (ar.isSucceeded()) {
                    writeUpdateResult(ar.getResult());
                    if (stmt.getType() == SQLStatement.CREATE_DATABASE) {
                        MySQLServer.createBuiltInSchemas(((CreateDatabase) stmt).getDatabaseName());
                    }
                } else {
                    writeFailedResult(stmt, ar.getCause());
                }
            });
        }
        si.submitYieldableCommand(0, yieldable);
    }

    private void writeFailedResult(PreparedSQLStatement stmt, Throwable cause) {
        logAndSendErrorMessage("Failed to execute statement: " + stmt.getSQL(), cause);
    }

    private void writeQueryResult(Result result) {
        int fieldCount = result.getVisibleColumnCount();
        ResultSetHeaderPacket header = Packet.getHeader(fieldCount);
        FieldPacket[] fields = new FieldPacket[fieldCount];
        EOFPacket eof = new EOFPacket();
        byte packetId = 0;
        header.packetId = ++packetId;
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = Packet.getField(result.getAlias(i).toLowerCase(),
                    Fields.toMySQLType(result.getColumnType(i)));
            fields[i].packetId = ++packetId;
        }
        eof.packetId = ++packetId;

        PacketOutput out = getPacketOutput();

        // write header
        header.write(out);

        // write fields
        for (FieldPacket field : fields) {
            field.write(out);
        }

        // write eof
        eof.write(out);

        // write rows
        packetId = eof.packetId;
        for (int i = 0; i < result.getRowCount(); i++) {
            RowDataPacket row = new RowDataPacket(fieldCount);
            if (result.next()) {
                Value[] values = result.currentRow();
                for (int j = 0; j < fieldCount; j++) {
                    if (values[j] == ValueNull.INSTANCE) {
                        row.add(new byte[0]);
                    } else {
                        row.add(values[j].getString().getBytes());
                    }
                }
                row.packetId = ++packetId;
                row.write(out);
            }
        }

        // write last eof
        EOFPacket lastEof = new EOFPacket();
        lastEof.packetId = ++packetId;
        lastEof.write(out);
    }

    private void writeUpdateResult(int updateCount) {
        writeOkPacket(updateCount);
    }

    public void writeOkPacket() {
        writeOkPacket(0);
    }

    private void writeOkPacket(int updateCount) {
        OkPacket packet = new OkPacket();
        packet.packetId = 1;
        packet.affectedRows = updateCount;
        packet.serverStatus = 2;
        sendPacket(packet);
    }

    private void logAndSendErrorMessage(String message, Throwable e) {
        logger.error(message, e);
        sendErrorMessage(e);
    }

    private void sendErrorMessage(Throwable e) {
        if (e instanceof DbException) {
            DbException dbe = (DbException) e;
            sendErrorMessage(dbe.getErrorCode(), dbe.getMessage());
        } else {
            sendErrorMessage(DbException.convert(e));
        }
    }

    public void sendErrorMessage(int errno, String msg) {
        ErrorPacket err = new ErrorPacket();
        err.packetId = 0;
        err.errno = errno;
        err.message = encodeString(msg, "utf-8");
        sendPacket(err);
    }

    private static byte[] encodeString(String src, String charset) {
        if (src == null) {
            return null;
        }
        if (charset == null) {
            return src.getBytes();
        }
        try {
            return src.getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            return src.getBytes();
        }
    }

    private void sendMessage(byte[] data) {
        try (NetBufferOutputStream out = new NetBufferOutputStream(writableChannel, data.length,
                scheduler.getDataBufferFactory())) {
            out.write(data);
            out.flush(false);
        } catch (IOException e) {
            logger.error("Failed to send message", e);
        }
    }

    private void sendPacket(Packet packet) {
        PacketOutput out = getPacketOutput();
        packet.write(out);
    }

    private PacketOutput getPacketOutput() {
        return new PacketOutput(writableChannel, scheduler.getDataBufferFactory());
    }

    @Override
    public int getPacketLength() {
        int length = (packetLengthByteBuffer.get() & 0xff);
        length |= (packetLengthByteBuffer.get() & 0xff) << 8;
        length |= (packetLengthByteBuffer.get() & 0xff) << 16;
        return length;
    }

    @Override
    public void handle(NetBuffer buffer) {
        if (!buffer.isOnlyOnePacket()) {
            DbException.throwInternalError("NetBuffer must be OnlyOnePacket");
        }
        try {
            PacketInput input = new PacketInput(this, packetLengthByteBuffer.get(3), buffer);
            packetHandler.handle(input);
        } catch (Throwable e) {
            logAndSendErrorMessage("Failed to handle packet", e);
        } finally {
            buffer.recycle();
        }
    }
}
