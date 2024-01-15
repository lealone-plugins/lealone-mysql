/*
 * Copyright Lealone Database Group.
 * Licensed under the Server Side Public License, v 1.
 * Initial Developer: zhh
 */
package com.lealone.plugins.mysql;

import com.lealone.main.Lealone;
import com.lealone.test.TestBase;

public class MySQLStart {

    static {
        // 测试代码中有依赖mysql jdbc驱动，禁用掉流氓的abandoned-connection-cleanup线程
        TestBase.disableAbandonedConnectionCleanup();
    }

    public static void main(String[] args) {
        Lealone.main(args);
    }
}
