/*
 *    Copyright 2010-2012 The MyBatis Team
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.spring.boot.mybatis.rw.starter.transaction;

import static org.springframework.jdbc.datasource.DataSourceUtils.isConnectionTransactional;
import static org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection;
import static org.springframework.util.Assert.notNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.transaction.Transaction;
import org.spring.boot.mybatis.rw.starter.datasource.AbstractRWRoutingDataSourceProxy;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@code SpringManagedTransaction} handles the lifecycle of a JDBC connection.
 * It retrieves a connection from Spring's transaction manager and returns it
 * back to it when it is no longer needed.
 * <p>
 * If Spring's transaction handling is active it will no-op all
 * commit/rollback/close calls assuming that the Spring transaction manager will
 * do the job.
 * <p>
 * If it is not it will behave like {@code JdbcTransaction}.
 *
 * @version $Id$
 */
public class RWManagedTransaction implements Transaction {


	private final Log logger = LogFactory.getLog(getClass());

	private final DataSource dataSource;

	private Connection connection;

	private boolean isConnectionTransactional;

	private boolean autoCommit;

	public RWManagedTransaction(DataSource dataSource) {
		notNull(dataSource, "No DataSource specified");
		this.dataSource = dataSource;
	}

	/**
	 * {@inheritDoc}
	 */
	public Connection getConnection() throws SQLException {
		if (this.connection == null) {
			openConnection();
		}
		return this.connection;
	}

	/**
	 * Gets a connection from Spring transaction manager and discovers if this
	 * {@code Transaction} should manage connection or let it to Spring.
	 * <p>
	 * It also reads autocommit setting because when using Spring Transaction
	 * MyBatis thinks that autocommit is always false and will always call
	 * commit/rollback so we need to no-op that calls.
	 */
	private void openConnection() throws SQLException {
		this.connection = DataSourceUtils.getConnection(this.dataSource);
		this.autoCommit = this.connection.getAutoCommit();
		this.isConnectionTransactional = isConnectionTransactional(this.connection, this.dataSource);

		if (this.logger.isDebugEnabled()) {
			this.logger.debug("JDBC Connection [" + this.connection + "] will"
					+ (this.isConnectionTransactional ? " " : " not ") + "be managed by Spring");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void commit() throws SQLException {
		if (this.connection != null && !this.isConnectionTransactional && !this.autoCommit) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Committing JDBC Connection [" + this.connection + "]");
			}
			this.connection.commit();
		}
		Map<String, Connection> connectionMap = AbstractRWRoutingDataSourceProxy.ConnectionContext.get();
		if(connectionMap !=null){
			for (Connection c : connectionMap.values()) {
				if(!c.isClosed() && !c.getAutoCommit()){
					c.commit();
				}
			}
		}	
	}

	/**
	 * {@inheritDoc}
	 */
	public void rollback() throws SQLException {
		if (this.connection != null && !this.isConnectionTransactional && !this.autoCommit) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Rolling back JDBC Connection [" + this.connection + "]");
			}
			this.connection.rollback();
		}	
		Map<String, Connection> connectionMap = AbstractRWRoutingDataSourceProxy.ConnectionContext.get();
		if(connectionMap !=null){
			for (Connection c : connectionMap.values()) {
				if(!c.isClosed() && !c.getAutoCommit()){
					c.rollback();
				}
			}
		}		
	}

	/**
	 * {@inheritDoc}
	 */
	public void close() throws SQLException {
//		releaseConnection(this.connection, this.dataSource);
		AbstractRWRoutingDataSourceProxy.currentDataSource.remove();
		AbstractRWRoutingDataSourceProxy.currentDataSource.set(AbstractRWRoutingDataSourceProxy.WRITE);
	}

	@Override
	public Integer getTimeout() throws SQLException {
	    ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
	    if (holder != null && holder.hasTimeout()) {
	      return holder.getTimeToLiveInSeconds();
	    } 
	    return null;
	}

}
