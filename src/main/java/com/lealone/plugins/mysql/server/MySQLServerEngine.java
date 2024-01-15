/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.mysql.server;

import com.lealone.plugins.mysql.MySQLPlugin;
import com.lealone.server.ProtocolServer;
import com.lealone.server.ProtocolServerEngineBase;

public class MySQLServerEngine extends ProtocolServerEngineBase {

    public MySQLServerEngine() {
        super(MySQLPlugin.NAME);
    }

    @Override
    protected ProtocolServer createProtocolServer() {
        return new MySQLServer();
    }
}
