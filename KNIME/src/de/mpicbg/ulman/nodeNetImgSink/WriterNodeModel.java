package de.mpicbg.ulman.nodeNetImgSink;

import java.io.File;
import java.io.IOException;

import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

import org.knime.knip.base.data.img.ImgPlusCell;
import org.knime.knip.base.data.img.ImgPlusValue;
import org.knime.knip.base.node.NodeUtils;

import net.imagej.ImgPlus;
import de.mpicbg.ulman.imgtransfer.ImgTransfer;
import de.mpicbg.ulman.imgtransfer.ProgressCallback;


/**
 * This is the implementation of DAIS wp1.3 "Image Server" Node for KNIME.com.
 * It as an adopted code from the MyExampleNode found in the KNIME SDK.
 *
 * @author Vladimir Ulman, MPI-CBG.de
 */
public class WriterNodeModel extends NodeModel
{
	/// the logger instance
	private static
	final NodeLogger logger = NodeLogger.getLogger("Image Server");

	/*
	 * A helper class to provide the same variable model for Port number.
	 * This class is called from ReaderNodeDialog's constructor.
	 */
	static
	SettingsModelIntegerBounded createSettingsModel_Port()
	{
		return new SettingsModelIntegerBounded(WriterNodeModel.CFG_PORTOUT,54546,1024,65535);
	}

	/*
	 * A helper class to provide the same variable model for TimeOut value.
	 * This class is called from ReaderNodeDialog's constructor.
	 */
	static
	SettingsModelIntegerBounded createSettingsModel_TimeOut()
	{
		return new SettingsModelIntegerBounded(WriterNodeModel.CFG_TIMEOUT,60,0,Integer.MAX_VALUE);
	}

	static
	SettingsModelString createSettingsModel_ImgColumn()
	{
		return new SettingsModelString(WriterNodeModel.CFG_IMGCOL,"");
	}

	static final String CFG_PORTOUT = "ServingPort";
	static final String CFG_TIMEOUT = "ServingTimeOut";
	static final String CFG_IMGCOL  = "ServingColumn";

	/// port to listen at
	private final SettingsModelIntegerBounded m_portNo
		= WriterNodeModel.createSettingsModel_Port();

	/// timeOut to wait for the initial connection
	private final SettingsModelIntegerBounded m_timeOut
		= WriterNodeModel.createSettingsModel_TimeOut();

	/// image column to be served
	private final SettingsModelString m_selectedImgColumn
		= WriterNodeModel.createSettingsModel_ImgColumn();

	private class MyLogger implements ProgressCallback
	{
		@Override
		public void info(String msg) { logger.info(msg); }
		@Override
		public void setProgress(float arg0) { /* empty */ }
	}

	/**
	 * Constructor for the node model.
	 */
	protected WriterNodeModel()
	{
		// one incoming port and no outgoing port
		super(1, 0);
	}

    /**
     * {@inheritDoc}
     */
    @Override
    protected DataTableSpec[] configure(final DataTableSpec[] inSpecs)
    throws InvalidSettingsException
    {
        // tODO: check if user settings are available, fit to the incoming
        // table structure, and the incoming types are feasible for the node
        // to execute. If the node can execute in its current state return
        // the spec of its output data table(s) (if you can, otherwise an array
        // with null elements), or throw an exception with a useful user message

		  // the input settings should be OK: they are bounded integers
		  // and the GUI should not leave them out of their bounds...

		  //we only need to check that inSpecs contains a column with images:
		  // Check table spec if column is available.
		  NodeUtils.autoColumnSelection(inSpecs[0], m_selectedImgColumn,
		                                ImgPlusValue.class, this.getClass());

		  return new DataTableSpec[] { };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
                                          final ExecutionContext exec)
    throws Exception
    {
		//shortcut to the input data
		final BufferedDataTable data = inData[0];
		final int colSpec = data.getSpec().findColumnIndex(m_selectedImgColumn.getStringValue());

		//counters of received images, and expected no. of images to be sent
		int cnt = 1, cntE = (int)data.size();
		logger.info("SendImages node: going to serve "+cntE+" images");

		//create server instance
		MyLogger myLogger = new MyLogger();
		ImgTransfer Server = new ImgTransfer(m_portNo.getIntValue(), cntE,
		                                     m_timeOut.getIntValue(), myLogger);

		for (final DataRow row : data)
		{
			// Check if execution got canceled.
			exec.checkCanceled();
			//TODO close the receiving interface after abort!

			//get next image
			final ImgPlusCell<?> cell = (ImgPlusCell<?>)row.getCell(colSpec);
			if (!cell.isMissing())
			{
				final ImgPlus<?> i = cell.getImgPlus();
				//TODO: row key or truly the image name, currently the later

				logger.info("SendImages node: serving "+cnt+"/"+cntE+": "+i.getName());
				Server.serveImage((ImgPlus)i);
			}
			//NB: if cell is missing, we just skip it

			//count every processed row, not images...
			++cnt;

			// Update progress indicator.
			exec.setProgress(cnt / cntE);
		}

		Server.hangUpAndClose();
		//return new BufferedDataTable[] { null };
		return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset()
    {
        // tODO Code executed on reset.
        // Models build during execute are cleared here.
        // Also data handled in load/saveInternals will be erased here.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings)
    {
        // tODO save user settings to the config object.
        m_portNo.saveSettingsTo(settings);
        m_timeOut.saveSettingsTo(settings);
        m_selectedImgColumn.saveSettingsTo(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
    throws InvalidSettingsException
    {
        // tODO load (valid) settings from the config object.
        // It can be safely assumed that the settings are valided by the 
        // method below.
        m_portNo.loadSettingsFrom(settings);
        m_timeOut.loadSettingsFrom(settings);
        m_selectedImgColumn.loadSettingsFrom(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
    throws InvalidSettingsException
    {
        // tODO check if the settings could be applied to our model
        // e.g. if the count is in a certain range (which is ensured by the
        // SettingsModel).
        // Do not actually set any values of any member variables.
        m_portNo.validateSettings(settings);
        m_timeOut.validateSettings(settings);
        m_selectedImgColumn.validateSettings(settings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File internDir,
            final ExecutionMonitor exec)
    throws IOException, CanceledExecutionException
    {
        // tODO load internal data.
        // Everything handed to output ports is loaded automatically (data
        // returned by the execute method, models loaded in loadModelContent,
        // and user settings set through loadSettingsFrom - is all taken care 
        // of). Load here only the other internals that need to be restored
        // (e.g. data used by the views).
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File internDir,
            final ExecutionMonitor exec)
    throws IOException, CanceledExecutionException
    {
        // tODO save internal models.
        // Everything written to output ports is saved automatically (data
        // returned by the execute method, models saved in the saveModelContent,
        // and user settings saved through saveSettingsTo - is all taken care 
        // of). Save here only the other internals that need to be preserved
        // (e.g. data used by the views).
    }
}

