package de.mpicbg.ulman.nodeNetImgSource;

import java.io.File;
import java.io.IOException;

import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.RowKey;
import org.knime.core.data.def.DefaultRow;
import org.knime.core.node.BufferedDataContainer;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.knip.base.data.img.ImgPlusCell;
import org.knime.knip.base.data.img.ImgPlusCellFactory;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;

import net.imagej.ImgPlus;
import de.mpicbg.ulman.imgtransfer.ImgTransfer;
import de.mpicbg.ulman.imgtransfer.ProgressCallback;


/**
 * This is the implementation of DAIS wp1.3 "Image Receiver" Node for KNIME.com.
 * It as an adopted code from the MyExampleNode found in the KNIME SDK.
 *
 * @author Vladimir Ulman, MPI-CBG.de
 */
public class ReaderNodeModel extends NodeModel
{
	/// the logger instance
	private static
	final NodeLogger logger = NodeLogger.getLogger("Image Receiver");

	/*
	 * A helper class to provide the same variable model for Port number.
	 * This class is called from ReaderNodeDialog's constructor.
	 */
	static
	SettingsModelIntegerBounded createSettingsModel_Port()
	{
		return new SettingsModelIntegerBounded(ReaderNodeModel.CFG_PORTIN,54545,1024,65535);
	}

	/*
	 * A helper class to provide the same variable model for TimeOut value.
	 * This class is called from ReaderNodeDialog's constructor.
	 */
	static
	SettingsModelIntegerBounded createSettingsModel_TimeOut()
	{
		return new SettingsModelIntegerBounded(ReaderNodeModel.CFG_TIMEOUT,60,0,Integer.MAX_VALUE);
	}

	static final String CFG_PORTIN = "ReceivingPort";
	static final String CFG_TIMEOUT = "ReceivingTimeOut";

	/// port to listen at
	private final SettingsModelIntegerBounded m_portNo
		= ReaderNodeModel.createSettingsModel_Port();

	/// timeOut to wait for the initial connection
	private final SettingsModelIntegerBounded m_timeOut
		= ReaderNodeModel.createSettingsModel_TimeOut();

	/// the fixed table specification, created once and for all
	final DataTableSpec outTableSpec;

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
	protected ReaderNodeModel()
	{
		// no incoming port and one outgoing port
		super(0, 1);

		//complete the out table specification/initialization
		outTableSpec = new DataTableSpec(new String[] { "Image" },
		                                 new DataType[] { ImgPlusCell.TYPE });
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

        return new DataTableSpec[] { outTableSpec };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected BufferedDataTable[] execute(final BufferedDataTable[] inData,
                                          final ExecutionContext exec)
    throws Exception
    {
		// the execution context will provide us with storage capacity, in this
		// case a data container to which we will add rows sequentially
		// Note, this container can also handle arbitrary big data tables, it
		// will buffer to disc if necessary.
		BufferedDataContainer container = exec.createDataContainer(outTableSpec);

		//helper class to create new table cells with received images
		final ImgPlusCellFactory imgPlusCellFactory = new ImgPlusCellFactory(exec);

		//create receiver instance
		MyLogger myLogger = new MyLogger();
		ImgTransfer Receiver = new ImgTransfer(m_portNo.getIntValue(), m_timeOut.getIntValue(), myLogger);

		//counters of received images, and expected no. of images to be received
		int cnt = 0, cntE = 0;

		while (Receiver.isThereNextImage())
		{
			// Check if execution got canceled.
			exec.checkCanceled();
			//TODO close the receiving interface after abort!

			//get next image
			final ImgPlus<?> i = Receiver.receiveImage();

			//first image?
			if (cnt == 0)
			{
				cntE = Receiver.getExpectedNumberOfImages();
				if (cntE == 0) cntE = 1; //make sure we don't divide by zero later

				logger.info("ReceiveImages node: going to receive "+cntE+" images");
			}

			++cnt;
			logger.info("ReceiveImages node: received "+cnt+"/"+cntE+": "+i.getName());

			//turn the image into a table cell
			@SuppressWarnings("unchecked")
			ImgPlusCell<?> ic = imgPlusCellFactory.createCell((ImgPlus)i);

			//add the image as another row into the output table
			final RowKey key = new RowKey( i.getName() );
			container.addRowToTable(new DefaultRow(key, ic));

			// Update progress indicator.
			exec.setProgress(cnt / cntE);
		}

		container.close();
		return new BufferedDataTable[] { container.getTable() };
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

