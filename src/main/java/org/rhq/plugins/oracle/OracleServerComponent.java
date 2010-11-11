/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.plugins.oracle;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertyList;
import org.rhq.core.domain.configuration.PropertyMap;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.pluginapi.inventory.InvalidPluginConfigurationException;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.core.pluginapi.operation.OperationFacet;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.plugins.database.DatabaseComponent;
import org.rhq.plugins.database.DatabaseQueryUtility;

/**
 * @author Greg Hinkle
 */
/**
 * @author isaac
 *
 */
/**
 * @author isaac
 *
 */
public class OracleServerComponent implements DatabaseComponent,
		MeasurementFacet, OperationFacet {
	private static final Log LOG = LogFactory
			.getLog(OracleServerComponent.class);

	private static final String PROPERTY_SIZE_DB = "totalSize";

	private static final String SQL_RETRIEVE_METRICS = 
		"SELECT " +
		"   name" +
		"   , value " +
		"FROM " +
		"   V$SYSSTAT";
	private static final String SQL_RETRIEVE_TRAITS = 
		"SELECT " +
		"   name " +
		"   , value " +
		"FROM " +
		"   V$PARAMETER";
	private static final String SQL_RETRIEVE_SIZE_DB = 
		"SELECT " +
		"   SUM(bytes) " +
		"FROM " +
		"   SYS.DBA_DATA_FILES";
	private static final String SQL_OPERATION_OPEN_CURSORS_BY_SESSION = 
		"SELECT " +
		"   s.sid " +
		"   , s.username " +
		"   , s.serial# " +
		"   , a.value " +
		"  FROM " +
		"   v$sesstat a " +
		"   , v$statname b " +
		"   , v$session s " +
		" WHERE     " +
		"   a.statistic# = b.statistic# " +
		"   AND s.sid=a.sid " +
		"   AND b.name = 'opened cursors current'";
    private static final String SQL_OPERATION_OPEN_CURSORS_BY_USER_BY_MACHINE =
    	"SELECT" + 
        "   s.username " +
        "   , s.machine" +
        "   , sum(a.value) total_cur " +
        "   , avg(a.value) avg_cur " +
        "   , max(a.value) max_cur " +
        "  FROM " +
        "   v$sesstat a " +
        "   , v$statname b " +
        "   , v$session s " +
        " WHERE " +
        "   a.statistic# = b.statistic#  " +
        "   AND s.sid=a.sid " +
        "   AND b.name = 'opened cursors current' " +
        " GROUP BY " +
        "   s.username, " +
        "   s.machine" +
        " ORDER BY " +
        "   1 desc";
    private static final String SQL_OPERATION_CACHED_CURSORS_PER_SESSION =
    	"SELECT" +
    	"   s.username" +
    	"   , s.sid" +
    	"   , s.serial#" +
    	"   , a.value" +
    	"  FROM " +
    	"   v$sesstat a" +
    	"   , v$statname b" +
    	"   , v$session s" +
    	" WHERE " +
    	"   a.statistic# = b.statistic#  " +
    	"   AND s.sid=a.sid" +
    	"   AND b.name = 'session cursor cache count'";
    private static final String SQL_OPERATION_VIEW_SESSSION_CURSORS_CACHE = 
    	"SELECT " +
    	"   c.user_name" +
    	"   , c.sid" +
    	"   , sql.sql_text" +
    	"  FROM " +
    	"   v$open_cursor c" +
    	"   , v$sql sql" +
    	" WHERE " +
    	"   c.sql_id=sql.sql_id" +
    	"   AND c.sid= ?";   


	private Connection connection;

	private ResourceContext resourceContext;

	/**
	 * Builds the JDBC Connection URL String
	 * @param configuration The Agent Oracle Plugin Configuration
	 * @return the String URL for the Configuration
	 */
	private static String buildUrl(Configuration configuration) {
		return "jdbc:oracle:thin:@"
				+ configuration.getSimpleValue("host", "localhost") + ":"
				+ configuration.getSimpleValue("port", "1521") + ":"
				+ configuration.getSimpleValue("sid", "XE");
	}

	/**
	 * 
	 * @param report
	 * @param metrics
	 * @param values
	 */
	private void populateRequestedMetrics(MeasurementReport report,
			Set<MeasurementScheduleRequest> metrics, Map<String, Double> values) {
		for (MeasurementScheduleRequest request : metrics) {
			if (request.getName().equals(PROPERTY_SIZE_DB)) {
				Double val = DatabaseQueryUtility.getSingleNumericQueryValue(
						this, SQL_RETRIEVE_SIZE_DB);
				report.addData(new MeasurementDataNumeric(request, val));
			} else {
				Double value = values.get(request.getName());
				if (value != null) {
					report.addData(new MeasurementDataNumeric(request, value));
				}
			}
		}
	}

	/**
	 * 
	 * @return
	 */
	private Map<String, Double> retrieveMetricValues() {
		Map<String, Double> values = DatabaseQueryUtility
				.getNumericQueryValueMap(this, SQL_RETRIEVE_METRICS);
		values.putAll(DatabaseQueryUtility
				.getNumericQueryValueMap(this, SQL_RETRIEVE_TRAITS));
		return values;
	}

	/* (non-Javadoc)
	 * @see org.rhq.core.pluginapi.inventory.ResourceComponent#start(org.rhq.core.pluginapi.inventory.ResourceContext)
	 */
	public void start(ResourceContext resourceContext)
			throws InvalidPluginConfigurationException, Exception {
		this.resourceContext = resourceContext;
		this.connection = buildConnection(resourceContext
				.getPluginConfiguration());
	}

	/* (non-Javadoc)
	 * @see org.rhq.core.pluginapi.inventory.ResourceComponent#stop()
	 */
	public void stop() {
		if (this.connection != null) {
			try {
				this.connection.close();
			} catch (SQLException e) {
				LOG.debug("Unable to close oracle connection", e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see org.rhq.core.pluginapi.availability.AvailabilityFacet#getAvailability()
	 */
	public AvailabilityType getAvailability() {
		if (getConnection() != null) {
			return AvailabilityType.UP;
		} else {
			return AvailabilityType.DOWN;
		}
	}

	/* (non-Javadoc)
	 * @see org.rhq.plugins.database.DatabaseComponent#getConnection()
	 */
	public synchronized Connection getConnection() {
		try {
			if ((this.connection == null) || connection.isClosed()) {
			    this.connection = 
			       buildConnection(this.resourceContext.getPluginConfiguration());
			}
		} catch (SQLException e) {
			LOG.info("Unable to create oracle connection", e);
		}

		return this.connection;				
	}

	/* (non-Javadoc)
	 * @see org.rhq.plugins.database.DatabaseComponent#removeConnection()
	 */
	public void removeConnection() {
		this.connection = null;
	}

	/**
	 * @param configuration
	 * @return
	 * @throws SQLException
	 */
	public static Connection buildConnection(Configuration configuration)
			throws SQLException {
		String driverClass = configuration.getSimple("driverClass")
				.getStringValue();
		try {
			Class.forName(driverClass);
		} catch (ClassNotFoundException e) {
			throw new InvalidPluginConfigurationException(
					"Specified JDBC driver class (" + driverClass
							+ ") not found.");
		}

		String url = buildUrl(configuration);
		LOG.debug("Attempting JDBC connection to [" + url + "]");

		String principal = configuration.getSimple("principal")
				.getStringValue();
		String credentials = configuration.getSimple("credentials")
				.getStringValue();

		Properties props = new Properties();
		props.put("user", principal);
		props.put("password", credentials);
		if (principal.equalsIgnoreCase("SYS")) {
			props.put("internal_logon", "sysdba");
		}

		return DriverManager.getConnection(url, props);
	}

	/* (non-Javadoc)
	 * @see org.rhq.core.pluginapi.measurement.MeasurementFacet#getValues(org.rhq.core.domain.measurement.MeasurementReport, java.util.Set)
	 */
	public void getValues(MeasurementReport report,
			Set<MeasurementScheduleRequest> metrics) throws Exception {
		Map<String, Double> values = retrieveMetricValues();

		populateRequestedMetrics(report, metrics, values);
	}

	/* (non-Javadoc)
	 * @see org.rhq.core.pluginapi.operation.OperationFacet#invokeOperation(java.lang.String, org.rhq.core.domain.configuration.Configuration)
	 */
	public OperationResult invokeOperation(String name, Configuration config)
			throws InterruptedException, Exception {
		
        if (name.equals("listOpenCursorsBySession")) {
            Statement stmt = null;
            ResultSet rs = null;
            try {
                stmt = getConnection().createStatement();
                rs = stmt.executeQuery(SQL_OPERATION_OPEN_CURSORS_BY_SESSION);

                PropertyList cursorList = new PropertyList("openCursorList");
                while (rs.next()) {
                    PropertyMap pm = new PropertyMap("process");
                    pm.put(new PropertySimple("sid", rs.getInt("sid")));
                    pm.put(new PropertySimple("userName", rs.getString("username")));
                    pm.put(new PropertySimple("serialNum", rs.getString("serial#")));
                    pm.put(new PropertySimple("numCursors", rs.getString("value")));

                    cursorList.add(pm);
                }

                OperationResult result = new OperationResult();
                result.getComplexResults().put(cursorList);
                return result;
            } finally {
                if (rs != null) {
                    rs.close();
                }

                if (stmt != null) {
                    stmt.close();
                }
            }
        } else if (name.equals("listOpenCursorsByUserByMachine")) {
            Statement stmt = null;
            ResultSet rs = null;
            try {
            	stmt = getConnection().createStatement();
            	rs = stmt.executeQuery(SQL_OPERATION_OPEN_CURSORS_BY_USER_BY_MACHINE);

            	PropertyList cursorList = new PropertyList("openCursorByUserList");
            	while (rs.next()) {
            		PropertyMap pm = new PropertyMap("openCursorsByUser");
            		pm.put(new PropertySimple("userName", rs.getString("username")));
            		pm.put(new PropertySimple("connectingServer", rs.getString("machine")));
            		pm.put(new PropertySimple("numCursors", rs.getString("total_cur")));
            		pm.put(new PropertySimple("avgCursors", rs.getString("avg_cur")));
            		pm.put(new PropertySimple("maxCursors", rs.getString("max_cur")));

            		cursorList.add(pm);
            	}

            	OperationResult result = new OperationResult();
            	result.getComplexResults().put(cursorList);
            	return result;
            } finally {
            	if (rs != null) {
            		rs.close();
            	}

            	if (stmt != null) {
            		stmt.close();
            	}
            }
        } else if(name.equals("listCachedCursorsBySession")) {
            Statement stmt = null;
            ResultSet rs = null;
            try {
            	stmt = getConnection().createStatement();
            	rs = stmt.executeQuery(SQL_OPERATION_CACHED_CURSORS_PER_SESSION);

            	PropertyList cursorList = new PropertyList("cachedCursorByUserList");
            	while (rs.next()) {
            		PropertyMap pm = new PropertyMap("cachedCursorsByUser");
                    pm.put(new PropertySimple("userName", rs.getString("username")));
                    pm.put(new PropertySimple("sid", rs.getInt("sid")));
                    pm.put(new PropertySimple("serialNum", rs.getString("serial#")));
                    pm.put(new PropertySimple("numCursors", rs.getString("value")));

            		cursorList.add(pm);
            	}

            	OperationResult result = new OperationResult();
            	result.getComplexResults().put(cursorList);
            	return result;
            } finally {
            	if (rs != null) {
            		rs.close();
            	}

            	if (stmt != null) {
            		stmt.close();
            	}
            }
        } else if (name.equals("viewSessionCursorsCache")) {
        	   PreparedStatement stmt = null;
               ResultSet rs = null;
               try {
            	String sid = config.getSimple("sid").getStringValue();   
               	stmt = getConnection().prepareStatement(SQL_OPERATION_VIEW_SESSSION_CURSORS_CACHE);
               	stmt.setString(1,sid);
               	rs = stmt.executeQuery();

               	PropertyList cursorList = new PropertyList("sessionCursorCacheList");
               	while (rs.next()) {
               	   PropertyMap pm = new PropertyMap("cachedCursorsBySession");
                   pm.put(new PropertySimple("userName", rs.getString("user_name")));
                   pm.put(new PropertySimple("sid", rs.getInt("sid")));
                   pm.put(new PropertySimple("sqlText", rs.getString("sql_text")));

               	   cursorList.add(pm);
               	}

               	OperationResult result = new OperationResult();
               	result.getComplexResults().put(cursorList);
               	return result;
               } finally {
               	if (rs != null) {
               		rs.close();
               	}

               	if (stmt != null) {
               		stmt.close();
               	}
               }
        } else {
            throw new UnsupportedOperationException("Operation [" + name + "] is not supported yet.");
        }
	}

}