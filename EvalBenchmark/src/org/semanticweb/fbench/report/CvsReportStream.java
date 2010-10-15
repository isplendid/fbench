package org.semanticweb.fbench.report;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.semanticweb.fbench.Config;
import org.semanticweb.fbench.query.Query;
import org.semanticweb.fbench.query.QueryType;



/**
 * Report evaluation to file at %baseDir%\result\result.csv
 * Report loadTimes to file at %baseDir%\result\loadtimes.csv
 * 
 * Format:
 * 
 * <code>
 * eval: run;query-id;dataConfig;queryTime;results;
 * loadTimes: name;location;type;duration;
 * </code>
 * 
 * @author as
 *
 */
public class CvsReportStream implements ReportStream {

	
	private String dataConfig;
	private BufferedWriter evalOut;
	private BufferedWriter loadOut;
	
	public CvsReportStream() {
		;
	}
	
	@Override
	public void beginEvaluation(String dataConfig, List<QueryType> querySet,
			int numberOfQueries, int numberOfRuns) {
		this.dataConfig = dataConfig;	
	}

	@Override
	public void beginQueryEvaluation(Query query, int run) {
		;		
	}

	@Override
	public void beginRun(int run, int totalNumberOfRuns) {
		;		
	}

	
	@Override
	public void endEvaluation(long duration) {
		;		
	}

	@Override
	public void endQueryEvaluation(Query query, int run, long duration,
			int numberOfResults) {
		try {
			// run;query-id;dataConfig;queryTime;results;
			evalOut.append(run+";"+query.getIdentifier()+";"+dataConfig+";"+duration+";"+numberOfResults+";\n");
		} catch (IOException e) {
			throw new RuntimeException("IOError: " + e.getMessage(), e);
		}
	}

	@Override
	public void endRun(int run, int totalNumberOfRuns, long duration) {
		;		
	}

	@Override
	public void error(String errorMsg, Exception ex) {
		System.out.println("[ERROR] " + errorMsg);
		ex.printStackTrace();		
	}

	@Override
	public void open() throws Exception {
		
		// evaluation file
		String file = Config.getConfig().getBaseDir() + "result\\result.csv"; 
		evalOut = new BufferedWriter( new FileWriter(file));
		evalOut.append("run;query-id;dataConfig;queryTime;results;\n");
		
		String file2 = Config.getConfig().getBaseDir() + "result\\loadTimes.csv"; 
		loadOut = new BufferedWriter( new FileWriter(file2));
		loadOut.append("name;location;type;duration;\n");
	}

	@Override
	public void close() throws Exception {
		evalOut.flush();
		evalOut.close();
		loadOut.flush();
		loadOut.close();
	}

	@Override
	public void initializationBegin() {
		;		
	}

	@Override
	public void initializationEnd(long duration) {
		;		
	}

	@Override
	public void reportDatasetLoadTime(String name, String location, String type, long duration) {
		try {
			// name;type;duration;
			loadOut.append(name+";"+location+";"+type+";"+duration+";\n");
		} catch (IOException e) {
			throw new RuntimeException("IOError: " + e.getMessage(), e);
		}	
	}
}
