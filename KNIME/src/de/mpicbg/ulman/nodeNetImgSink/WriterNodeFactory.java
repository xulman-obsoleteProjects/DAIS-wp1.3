package de.mpicbg.ulman.nodeNetImgSink;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "Image Receiver" Node.
 * It as an adopted code from the MyExampleNode found in the KNIME SDK.
 *
 * @author Vladimir Ulman, MPI-CBG.de
 */
public class WriterNodeFactory 
        extends NodeFactory<WriterNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public WriterNodeModel createNodeModel() {
        return new WriterNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<WriterNodeModel> createNodeView(final int viewIndex,
            final WriterNodeModel nodeModel) {
        return new WriterNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new WriterNodeDialog();
    }

}

