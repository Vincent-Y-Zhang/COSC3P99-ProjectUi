package projectui;

import java.sql.Date;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.swtchart.Chart;
import org.eclipse.swtchart.ISeries.SeriesType;
import org.eclipse.swtchart.Range;
import org.eclipse.tracecompass.tmf.core.event.*;
import org.eclipse.tracecompass.tmf.core.request.*;
import org.eclipse.tracecompass.tmf.core.signal.*;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestampFormat;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

public class ProjectView extends TmfView {
	
	private static final String SERIES_NAME = "Series";
    private static final String Y_AXIS_TITLE = "Signal";
    private static final String X_AXIS_TITLE = "Time";
    // CPU Usage sched_switch events
    private static final String FIELD = "next_tid"; // The name of the field that we want to display on the Y axis
    private static final String VIEW_ID = "projectui.view";
    private Chart chart;
    private ITmfTrace currentTrace;

	public ProjectView() {
		super(VIEW_ID);
	}

    @Override
    public void createPartControl(Composite parent) {
        chart = new Chart(parent, SWT.BORDER);
        chart.getTitle().setVisible(false);
        chart.getAxisSet().getXAxis(0).getTitle().setText(X_AXIS_TITLE);
        chart.getAxisSet().getYAxis(0).getTitle().setText(Y_AXIS_TITLE);
        chart.getSeriesSet().createSeries(SeriesType.LINE, SERIES_NAME);
        chart.getAxisSet().getXAxis(0).getTick().setFormat(new TmfChartTimeStampFormat());
        chart.getLegend().setVisible(false);
        
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if(trace != null) {
        	traceSelected(new TmfTraceSelectedSignal(this, trace));
        }
    }
    
    public class TmfChartTimeStampFormat extends SimpleDateFormat {
    	private static final long serialVersionUID = 1L;
    	public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
    		long time = date.getTime();
    		toAppendTo.append(TmfTimestampFormat.getDefaulTimeFormat().format(time));
    		return toAppendTo;
    	}
    }
    
    @TmfSignalHandler
    public void timestampFormatUpdated(TmfTimestampFormatUpdateSignal signal) {
    	chart.getAxisSet().getXAxis(0).getTick().setFormat(new TmfChartTimeStampFormat());
    	chart.redraw();
    }

    @Override
    public void setFocus() {
        chart.setFocus();
    }
    
    @TmfSignalHandler
    public void traceSelected(final TmfTraceSelectedSignal signal) {
    	System.out.println("traceSelected called with trace: " + signal.getTrace().getName());
    	if (currentTrace == signal.getTrace()) {
    		return;
    	}
    	currentTrace = signal.getTrace();
    	
    	TmfEventRequest req = new TmfEventRequest(TmfEvent.class, TmfTimeRange.ETERNITY, 0, ITmfEventRequest.ALL_DATA, ITmfEventRequest.ExecutionType.BACKGROUND) {
    		ArrayList<Double> xValues = new ArrayList<Double>();
            ArrayList<Double> yValues = new ArrayList<Double>();
            private double maxY = -Double.MAX_VALUE;
            private double minY = Double.MAX_VALUE;
            private double maxX = -Double.MAX_VALUE;
            private double minX = Double.MAX_VALUE;
            
    		@Override
    		public void handleData(ITmfEvent data) {
    			super.handleData(data);
    			ITmfEventField field = data.getContent().getField(FIELD);
    			if (field != null) {
                    Double yValue = Double.valueOf((Long) field.getValue());
                    yValues.add(yValue);
                    minY = Math.min(minY, yValue);
                    maxY = Math.max(maxY, yValue);

                    double xValue = (double) data.getTimestamp().getValue();
                    xValues.add(xValue);
                    minX = Math.min(minX, xValue);
                    maxX = Math.max(maxX, xValue);
                }
    		}
    		
    		@Override
    		public void handleSuccess() {
    	    	System.out.println("Success Handler Begins");
    	    	
    			// Request successful, no more data available
    			super.handleSuccess();
    			
    			final double x[] = toArray(xValues);
                final double y[] = toArray(yValues);

                Display.getDefault().asyncExec(new Runnable() {

                    @Override
                    public void run() {
                        chart.getSeriesSet().getSeries()[0].setXSeries(x);
                        chart.getSeriesSet().getSeries()[0].setYSeries(y);
                        
                        // Modify Range of View
                        if (!xValues.isEmpty() && !yValues.isEmpty()) {
                            chart.getAxisSet().getXAxis(0).setRange(new Range(0, x[x.length - 1]));
                            chart.getAxisSet().getYAxis(0).setRange(new Range(minY, maxY));
                        } else {
                            chart.getAxisSet().getXAxis(0).setRange(new Range(0, 1));
                            chart.getAxisSet().getYAxis(0).setRange(new Range(0, 1));
                        }
                        chart.getAxisSet().adjustRange();

                        chart.redraw();
                    }

                });
    		}
    		
    		// Convert List<Double> to double[]
    		private double[] toArray(List<Double> list) {
                double[] d = new double[list.size()];
                for (int i = 0; i < list.size(); ++i) {
                    d[i] = list.get(i);
                }

                return d;
            }
    		
    		@Override
    		public void handleFailure() {
    	    	System.out.println("Failure Handler Begins");
    			super.handleFailure();
    		}
    	};
    	ITmfTrace trace = signal.getTrace();
    	trace.sendRequest(req);
    }
}
