/**
 * 
 */
package org.rhq.plugins.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.core.pluginapi.operation.OperationFacet;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.core.util.jdbc.JDBCUtil;
import org.rhq.plugins.database.AbstractDatabaseComponent;

/**
 * @author isaac
 * 
 */
public class OracleSchemaComponent extends AbstractDatabaseComponent
		implements MeasurementFacet, OperationFacet {
    private static final String SQL_QUERY_AVAILABILITY = "SELECT owner FROM dba_tables WHERE owner = ?";
	
    /*
	 * (non-Javadoc)
	 * @see org.rhq.core.pluginapi.availability.AvailabilityFacet#getAvailability()
	 */
	public AvailabilityType getAvailability() {
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		try {
			statement = getConnection().prepareStatement(SQL_QUERY_AVAILABILITY);
			statement.setString(1, this.resourceContext.getResourceKey());
			resultSet = statement.executeQuery();
			if (resultSet.next()) {
				return AvailabilityType.UP;
			}
		} catch (SQLException e) {
			// Problems ? Mark the resource as down
		} finally {
			JDBCUtil.safeClose(statement, resultSet);
		}

		return AvailabilityType.DOWN;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.rhq.core.pluginapi.measurement.MeasurementFacet#getValues(org.rhq
	 * .core.domain.measurement.MeasurementReport, java.util.Set)
	 */
	public void getValues(MeasurementReport arg0,
			Set<MeasurementScheduleRequest> arg1) throws Exception {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * @see org.rhq.core.pluginapi.operation.OperationFacet#invokeOperation(java.lang.String, org.rhq.core.domain.configuration.Configuration)
	 */
	public OperationResult invokeOperation(String name, Configuration parameters)
			throws InterruptedException, Exception {
		if ("invokeSql".equals(name)) {
            Statement stmt = null;
            ResultSet rs = null;
            try {
                stmt = getConnection().createStatement();
                String sql = parameters.getSimple("sql").getStringValue();
                OperationResult result = new OperationResult();

                if (parameters.getSimple("type").getStringValue().equals("update")) {
                    int updateCount = stmt.executeUpdate(sql);
                    result.getComplexResults().put(new PropertySimple("result", "Query updated " + updateCount + " rows"));

                } else {
                    rs = stmt.executeQuery(parameters.getSimple("sql").getStringValue());

                    ResultSetMetaData md = rs.getMetaData();
                    StringBuilder buf = new StringBuilder();
                    int rowCount = 0;

                    buf.append("<table>");
                    buf.append("<th>");
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        buf.append("<td>");
                        buf.append(md.getColumnName(i) + " (" + md.getColumnTypeName(i) + ")");
                        buf.append("</td>");
                    }
                    buf.append("</th>");


                    while (rs.next()) {
                        rowCount++;
                        buf.append("<tr>");
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            buf.append("<td>");
                            buf.append(rs.getString(i));
                            buf.append("</td>");
                        }
                        buf.append("</tr>");
                    }

                    buf.append("</table>");
                    result.getComplexResults().put(new PropertySimple("result", "Query returned " + rowCount + " rows"));
                    result.getComplexResults().put(new PropertySimple("contents", buf.toString()));
                }
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
