package org.semanticweb.fbench.evaluation;

import org.apache.log4j.Logger;
import org.semanticweb.fbench.Config;
import org.semanticweb.fbench.query.Query;
import org.semanticweb.fbench.query.QueryManager;
import org.semanticweb.fbench.report.ReportStream;



/**
 * Base class for the actual evaluation runtime. <p>
 * 
 * Can be configured dynamicall using "evaluationClass" property. 
 * 
 * @see SesameEvaluation
 * @author as
 *
 */
public abstract class Evaluation {

	public static Logger log = Logger.getLogger(Evaluation.class);
	
	protected ReportStream report;
	protected Object monitor;
	
	public Evaluation() {
		
	}
	
	public final void run() throws Exception  {
		
		// intialize the report stream, default is SimpleReportStream
		try {
			report = (ReportStream)Class.forName(Config.getConfig().getReportStream()).newInstance();
			report.open();
		} catch (Exception e) {
			log.error("Error while configuring the report output stream [" + Config.getConfig().getReportStream() + "]: " + e.getMessage());
			log.debug("Exception details:", e);
			report.close();
			System.exit(1);
		}
		
		// perform any initialization, e.g. in Sesame load repositories
		try {
			report.initializationBegin();
			long initializationStart = System.currentTimeMillis();
			initialize();
			long initializationDuration = System.currentTimeMillis() - initializationStart;
			report.initializationEnd(initializationDuration);
		} catch (Exception e) {
			log.error("Error during initialization in " + this.getClass().getCanonicalName() + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
			log.debug("Exception details:", e);
			report.close();
			System.exit(1);
		}
		
		if (Config.getConfig().isFill()) {
			log.info("Fill mode activated. No query evalutation. Done.");
			report.endEvaluation(0);
			report.close();
			System.exit(0);
		}
		
		// depending on config run the evaluation with different settings
		// debugMode -> run evaluation once
		// timeout -> use extra thread and stop query execution after timeout
		// otherwise -> n runs
		
		if (Config.getConfig().isDebugMode()) {
			runEval();
		}
		
		else if (Config.getConfig().getTimeout()>0) {
			runMultiEvalTimeout();
		}
		
		else {
			runMultiEval();
		}
		
		// perform any clean up, e.g. in Sesame close repositories
		try {
			finish();
		} catch (Exception e) {
			log.error("Error during clean up in " + this.getClass().getCanonicalName() + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
			log.debug("Exception details:", e);
		}
		
		report.close();
	}
	
	
	
	protected void runEval() {
		
		log.info("Evaluation of queries in debug mode (one run per query)...");
		boolean showResult = Config.getConfig().isShowResults();
		report.beginEvaluation(Config.getConfig().getDataConfig(), Config.getConfig().getQuerySet(), QueryManager.getQueryManager().getQueries().size(), 1);
		long evalStart = System.currentTimeMillis();
		
		for (Query q : QueryManager.getQueryManager().getQueries()) {
			try {
				log.info("Executing query " + q.getIdentifier() + ", run 1");
				report.beginQueryEvaluation(q, 1);
				long start = System.currentTimeMillis();
				int numberOfResults = runQueryDebug(q, showResult);
				long duration = System.currentTimeMillis() - start;
				report.endQueryEvaluation(q, 1, duration, numberOfResults);
			} catch (Exception e) {
				report.endQueryEvaluation(q, 1, -1, -1);
				log.error("Error executing query " + q.getIdentifier()+ " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
				log.debug("Exception details:", e);
			}
		}
		
		long overallDuration = System.currentTimeMillis() - evalStart;
		
		report.endEvaluation(overallDuration);
		log.info("Evaluation of queries done. Overall duration: " + overallDuration + "ms");
	}
	
	
	protected void runMultiEval() {
		log.info("Evaluation of queries in multiple runs...");
		
		int evalRuns = Config.getConfig().getEvalRuns();
		report.beginEvaluation(Config.getConfig().getDataConfig(), Config.getConfig().getQuerySet(), QueryManager.getQueryManager().getQueries().size(), evalRuns);
		long evalStart = System.currentTimeMillis();
		
		for (int run = 1; run <= evalRuns; run++){
			report.beginRun(run, evalRuns);
			long runStart = System.currentTimeMillis();
			for (Query q : QueryManager.getQueryManager().getQueries()){
				try {
					log.info("Executing query " + q.getIdentifier() + ", run " + run);
					report.beginQueryEvaluation(q, run);
					long start = System.currentTimeMillis();
					int numberOfResults = runQuery(q);
					long duration = System.currentTimeMillis() - start;
					report.endQueryEvaluation(q, run, duration, numberOfResults);
				} catch (Exception e) {
					report.endQueryEvaluation(q, run, -1, -1);
					log.error("Error executing query " + q.getIdentifier()+ " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
					log.debug("Exception details:", e);
				}
			}
			long runDuration = System.currentTimeMillis() - runStart;
			report.endRun(run, evalRuns, runDuration);
		}
		
		long overallDuration = System.currentTimeMillis() - evalStart;
		
		report.endEvaluation(overallDuration);
		log.info("Evaluation of queries done.");
	}
	
	
	
	protected synchronized void runMultiEvalTimeout() {
		
		log.info("Evaluation of queries in multiple runs (using timeouts) ...");
		
		int evalRuns = Config.getConfig().getEvalRuns();
		long timeout = Config.getConfig().getTimeout();
		
		monitor = new Object();
		
		report.beginEvaluation(Config.getConfig().getDataConfig(), Config.getConfig().getQuerySet(), QueryManager.getQueryManager().getQueries().size(), evalRuns);
		long evalStart = System.currentTimeMillis();
		
		for (int run = 1; run <= evalRuns; run++){
			report.beginRun(run, evalRuns);
			long runStart = System.currentTimeMillis();
			for (Query q : QueryManager.getQueryManager().getQueries()) {
				try {
					log.info("Executing query " + q.getIdentifier() + ", run " + run);
					EvaluationThread eval = new EvaluationThread(this, q, report, run);
					eval.start();
					
					synchronized (Evaluation.class) {
						Evaluation.class.wait(timeout);
					}
					
					eval.stop();	// TODO check if this is really safe in this scenario, we have shared objects
					if (!eval.isFinished()) {
						log.info("Execution of query " + q.getIdentifier() + " resulted in timeout.");
						report.endQueryEvaluation(q, run, -1, -1);
					}
				} catch (InterruptedException e) {
					log.info("Execution of query " + q.getIdentifier() + " resulted in timeout.");
					report.endQueryEvaluation(q, run, -1, -1);
				}
			}
			long runDuration = System.currentTimeMillis() - runStart;
			report.endRun(run, evalRuns, runDuration);
		}
		
		long overallDuration = System.currentTimeMillis() - evalStart;
		
		report.endEvaluation(overallDuration);
		log.info("Evaluation of queries done.");			
	}
	
	
	/**
	 * Perform any initializations here, i.e. load repositories, open streams, etc.
	 * 
	 * @throws Exception
	 */
	public abstract void initialize() throws Exception;
	
	/**
	 * Clean up after all queries are run, i.e. close streams etc
	 * @throws Exception
	 */
	public abstract void finish() throws Exception;
	
	
	/**
	 * Run the specified query. Avoid printing debug information.
	 * 
	 * Note: you can use the class internal reportStream to print messages
	 *  
	 * @param query
	 * 			the query to be executed
	 * @return
	 * 		the number of results
	 * @throws Exception
	 */
	public abstract int runQuery(Query query) throws Exception;
	
	/**
	 * run the query in debug mode, i.e. printing debug information is ok and not
	 * relevant for any timings
	 * 
	 * @param query
	 * @param showResult
	 * @return
	 * @throws Exception
	 */
	public abstract int runQueryDebug(Query query, boolean showResult) throws Exception;
	
	
	
}