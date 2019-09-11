The repository consists of multiple folders, initially of the following three folders: scijava, KNIME, C++ .

scijava: Here's the main Java implementation of the image transfer protocol, and Fiji plugins.

KNIME: Here's a KNIME source and sink nodes that call the scijava back-end to do the transfer.

C++: Here's a C++ mini-library that is compatible with the transfer protocol in scijava.

The protocol documentation, including a bit of rationale/motivation and example code, can be found as PDFs in the `release/doc` folder.
