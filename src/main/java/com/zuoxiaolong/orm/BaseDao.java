package com.zuoxiaolong.orm;

/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zuoxiaolong.model.ViewMode;
import org.apache.log4j.Logger;

import com.zuoxiaolong.jdbc.ConnectionFactory;

/**
 * @author 左潇龙
 * @since 5/7/2015 11:44 AM
 */
public abstract class BaseDao {

    private static final Logger logger = Logger.getLogger(BaseDao.class);

    protected static void info(String message) {
        if (logger.isInfoEnabled()) {
            logger.info(message);
        }
    }

    protected static void warn(String message) {
        warn(message, null);
    }

    protected static void warn(String message, Exception e) {
        if (e != null) {
            logger.warn(message, e);
        } else {
            logger.warn(message);
        }
    }

    protected static void error(String message) {
        error(message, null);
    }

    protected static void error(String message, Exception e) {
        if (e != null) {
            logger.error(message, e);
        } else {
            logger.error(message);
        }
    }

    protected static <T> T execute(Operation<T> operation) {
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setReadOnly(true);
            connection.createStatement().execute("SET NAMES 'utf8mb4'");
            return operation.doInConnection(connection);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected static <T> T execute(TransactionalOperation<T> transactionalOperation) {
        Connection connection = null;
        try {
            connection = ConnectionFactory.getConnection();
            connection.setAutoCommit(false);
            connection.createStatement().execute("SET NAMES 'utf8mb4'");
            T result = transactionalOperation.doInConnection(connection);
            connection.commit();
            return result;
        } catch (Exception e) {
            if (connection != null) {
                try {
                	error("execute update failed!", e);
                    connection.rollback();
                } catch (SQLException e1) {
                    throw new RuntimeException(e1);
                }
            }
            throw new RuntimeException(e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected static boolean updateTimesCount(final String table, final String timesColumnName, final String refTableName, String refColumnName, final int id) {
        return execute(new TransactionalOperation<Boolean>() {
            @Override
            public Boolean doInConnection(Connection connection) {
                try {
                    PreparedStatement preparedStatement = connection.prepareStatement("update " + table + " set " + timesColumnName + "=(select count(id) from " + refTableName + " where " + refColumnName + "=?) where id = ?");
                    preparedStatement.setInt(1, id);
                    preparedStatement.setInt(2, id);
                    int number = preparedStatement.executeUpdate();
                    return number > 0;
                } catch (SQLException e) {
                    error("update " + table + " failed ..." , e);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    protected static boolean updateCount(final int id, final String table, final String column) {
        return updateCount(id, table, column, 1);
    }

    protected static boolean updateCount(final int id, final String table, final String column, final int count) {
        return execute(new TransactionalOperation<Boolean>() {
            @Override
            public Boolean doInConnection(Connection connection) {
                try {
                    PreparedStatement preparedStatement = connection.prepareStatement("update " + table + " set " + column + " = " + column + " + " + count + " where id = ?");
                    preparedStatement.setInt(1, id);
                    int number = preparedStatement.executeUpdate();
                    return number > 0;
                } catch (SQLException e) {
                    error("update " + table + " failed ..." , e);
                    throw new RuntimeException(e);
                }
            }
        });
    }

    protected List<Map<String, String>> getPager(final Map<String, Integer> pager, final String table) {
        return getPager(pager, table, "create_date");
    }

    protected List<Map<String, String>> getPager(final Map<String, Integer> pager, final String table, final String orderColumn) {
        return execute(new Operation<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> doInConnection(Connection connection) {
                String sql = "select * from " + table + " order by " + orderColumn + " desc limit ?,10";
                List<Map<String, String>> result = new ArrayList<Map<String, String>>();
                try {
                    PreparedStatement statement = connection.prepareStatement(sql);
                    statement.setInt(1 , (pager.get("current") - 1) * 10);
                    ResultSet resultSet = statement.executeQuery();
                    while (resultSet.next()) {
                        result.add(transfer(resultSet));
                    }
                } catch (SQLException e) {
                    error("query " + table + " failed ..." , e);
                    throw new RuntimeException(e);
                }
                return result;
            }
        });
    }

    public List<Map<String, String>> getAll(final String table) {
        return execute(new Operation<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> doInConnection(Connection connection) {
                String sql = "select * from " + table;
                List<Map<String, String>> result = new ArrayList<Map<String, String>>();
                try {
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(sql);
                    while (resultSet.next()) {
                        result.add(transfer(resultSet));
                    }
                } catch (SQLException e) {
                    error("query " + table + " failed ..." , e);
                    throw new RuntimeException(e);
                }
                return result;
            }
        });
    }

    public List<Map<String, String>> getAll(final String table, final String order) {
        return execute(new Operation<List<Map<String, String>>>() {
            @Override
            public List<Map<String, String>> doInConnection(Connection connection) {
                String sql = "select * from " + table + " order by " + order + " desc";
                List<Map<String, String>> result = new ArrayList<Map<String, String>>();
                try {
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(sql);
                    while (resultSet.next()) {
                        result.add(transfer(resultSet));
                    }
                } catch (SQLException e) {
                    error("query " + table + " failed ..." , e);
                    throw new RuntimeException(e);
                }
                return result;
            }
        });
    }

    public Map<String, String> getById(String table, final int id) {
        return execute(new Operation<Map<String, String>>() {
            @Override
            public Map<String, String> doInConnection(Connection connection) {
                String sql = "select * from " + table + " where id = ?";
                Map<String, String> result = new HashMap<String, String>();
                try {
                    PreparedStatement statement = connection.prepareStatement(sql);
                    statement.setInt(1, id);
                    ResultSet resultSet = statement.executeQuery(sql);
                    if (resultSet.next()) {
                        result = transfer(resultSet);
                    }
                } catch (SQLException e) {
                    error("query " + table + " failed ..." , e);
                    throw new RuntimeException(e);
                }
                return result;
            }
        });
    }


    public abstract Map<String, String> transfer(ResultSet resultSet);

}
