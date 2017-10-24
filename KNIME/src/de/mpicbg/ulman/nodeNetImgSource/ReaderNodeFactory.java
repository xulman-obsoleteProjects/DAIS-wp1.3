package de.mpicbg.ulman.nodeNetImgSource;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "Image Receiver" Node.
 * It as an adopted code from the MyExampleNode found in the KNIME SDK.
 *
 * @author Vladimir Ulman, MPI-CBG.de
 */
public class ReaderNodeFactory 
        extends NodeFactory<ReaderNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ReaderNodeModel createNodeModel() {
        return new ReaderNodeModel();
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
    public NodeView<ReaderNodeModel> createNodeView(final int viewIndex,
            final ReaderNodeModel nodeModel) {
        return new ReaderNodeView(nodeModel);
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
        return new ReaderNodeDialog();
    }

}

